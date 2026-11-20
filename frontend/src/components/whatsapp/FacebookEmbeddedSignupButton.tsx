"use client";

import { useEffect, useRef, useState } from "react";
import { useApiClient } from "@/lib/auth";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

// Variables d'environnement requises en mode reel. Si absentes, le bouton
// se met en mode "simulation" : il appelle directement le backend avec des
// identifiants factices (utile pour le QA local sans compte Meta valide).
const FB_APP_ID = process.env.NEXT_PUBLIC_FACEBOOK_APP_ID;
const FB_CONFIG_ID = process.env.NEXT_PUBLIC_FACEBOOK_CONFIG_ID;
const FB_SOLUTION_ID = process.env.NEXT_PUBLIC_FACEBOOK_SOLUTION_ID;

// Type minimal du SDK Facebook injecte par leur loader. On declare uniquement
// les surfaces qu'on utilise.
declare global {
  interface Window {
    FB?: {
      init: (opts: { appId: string; cookie: boolean; xfbml: boolean; version: string }) => void;
      login: (
        callback: (response: FacebookLoginResponse) => void,
        opts: { config_id: string; response_type: string; override_default_response_type: boolean; auth_type?: string; extras?: Record<string, unknown> }
      ) => void;
    };
    fbAsyncInit?: () => void;
  }
}

interface FacebookLoginResponse {
  status?: string;
  authResponse?: {
    code?: string;
    accessToken?: string;
    userID?: string;
  };
}

// Donnees retournees par le flow Embedded Signup que Meta transmet via
// {@code message} (postMessage) en parallele du callback FB.login.
interface EmbeddedSignupData {
  type: string;
  event?: string;
  data?: {
    waba_id?: string;
    business_id?: string;
    phone_number_id?: string;
    phone_number?: string;
  };
}

interface Props {
  /** Callback apres connexion Meta reussie cote backend. */
  onSuccess: () => void;
  /** Callback en cas d'erreur. */
  onError?: (message: string) => void;
  /**
   * Appele au lancement d'une nouvelle tentative, pour effacer une erreur
   * precedente (ex. "Connexion Facebook annulee") qui n'a plus lieu d'etre
   * des que l'utilisateur re-ouvre la popup.
   */
  onReset?: () => void;
  /** Classes additionnelles pour le bouton. */
  className?: string;
}

/**
 * Bouton qui declenche le flow Facebook Embedded Signup pour WhatsApp Business
 * (epic 25).
 *
 * <p>Charge le SDK Facebook au mount, ouvre la popup FB.login avec le scope
 * {@code whatsapp_business_management} via la config Embedded Signup, puis
 * appelle le backend {@code /api/parametres/whatsapp/self-signup/connecter-meta}
 * avec les identifiants Meta recus.</p>
 *
 * <p><strong>Configuration prod</strong> : necessite les env vars
 * {@code NEXT_PUBLIC_FACEBOOK_APP_ID} et {@code NEXT_PUBLIC_FACEBOOK_CONFIG_ID}
 * (id de la config Embedded Signup creee dans le Meta App Dashboard).</p>
 *
 * <p><strong>Mode simulation (dev)</strong> : si les env vars sont absentes,
 * le bouton appelle directement le backend avec des identifiants factices,
 * permettant de tester l'UI sans flow Facebook reel.</p>
 */
export default function FacebookEmbeddedSignupButton({ onSuccess, onError, onReset, className }: Props) {
  const apiFetch = useApiClient();
  const [sdkLoaded, setSdkLoaded] = useState(false);
  const [loading, setLoading] = useState(false);
  // Ref qui passe a true des qu'on reçoit un message Facebook acceptable (FINISH
  // ou FINISH_ONLY_WABA) — utilise comme safety net : si le callback FB.login
  // se termine sans message recu, on declenche un refresh statut + warning UX.
  const messageRecuRef = useRef(false);
  // Code OAuth renvoyé par FB.login (response_type=code). Conservé ici car il
  // arrive dans le callback FB.login, alors que les ids (waba/phone_number_id)
  // arrivent par postMessage : on les recombine au moment de l'envoi backend.
  const codeRef = useRef<string | null>(null);
  // Point de rendez-vous : le code (callback FB.login) et les ids WABA
  // (postMessage) arrivent par DEUX canaux asynchrones, dans un ordre non
  // garanti. On ne peut envoyer au backend qu'une fois les deux disponibles
  // (sinon le code part à null → pas de verified_name → numéro non récupéré).
  // codeResoluRef = le callback a fini (code présent ou définitivement absent).
  const codeResoluRef = useRef(false);
  // Données reçues par postMessage (ids WABA), en attente du code.
  const signupDataRef = useRef<{
    wabaId: string;
    businessId: string;
    phoneNumber: string | null;
    phoneNumberId: string | null;
  } | null>(null);
  // Garde anti double-envoi : les deux canaux appellent tentativeEnvoi(), un
  // seul doit déclencher l'appel backend.
  const envoyeRef = useRef(false);

  // Envoie au backend dès que le code ET les ids WABA sont tous deux résolus.
  // Appelée depuis les deux canaux (callback FB.login + postMessage) : la
  // dernière des deux arrivées déclenche réellement l'appel.
  function tentativeEnvoi() {
    if (envoyeRef.current) return;
    const data = signupDataRef.current;
    if (!data || !codeResoluRef.current) return;
    envoyeRef.current = true;
    void envoyerAuBackend(data.wabaId, data.businessId, data.phoneNumber, data.phoneNumberId);
  }

  const modeReel = !!FB_APP_ID && !!FB_CONFIG_ID;

  // Chargement asynchrone du SDK Facebook. On le fait au mount du bouton
  // (et pas au layout global) pour ne pas charger 200 Ko de JS sur toutes les
  // pages alors que ce bouton n'est present que sur la page d'activation.
  useEffect(() => {
    if (!modeReel) {
      // Mode simulation : pas besoin du SDK.
      setSdkLoaded(true);
      return;
    }
    if (window.FB) {
      setSdkLoaded(true);
      return;
    }
    window.fbAsyncInit = function () {
      window.FB?.init({
        appId: FB_APP_ID!,
        cookie: true,
        xfbml: true,
        version: "v21.0",
      });
      setSdkLoaded(true);
    };
    const existant = document.getElementById("facebook-jssdk") as HTMLScriptElement | null;
    if (existant) {
      setSdkLoaded(true);
      return;
    }
    const script = document.createElement("script");
    script.id = "facebook-jssdk";
    script.src = "https://connect.facebook.net/en_US/sdk.js";
    script.async = true;
    script.defer = true;
    script.crossOrigin = "anonymous";
    document.body.appendChild(script);
  }, [modeReel]);

  // Listener postMessage : pendant le flow Embedded Signup, Meta envoie via
  // postMessage les identifiants au moment ou l'utilisateur termine.
  // Le callback FB.login n'est pas suffisant car il ne contient que le code OAuth,
  // pas les ids WABA/business.
  //
  // Filtre d'origine permissif : Facebook envoie ces messages depuis plusieurs
  // sous-domaines (www.facebook.com, web.facebook.com, business.facebook.com,
  // m.facebook.com selon le contexte). On accepte tout *.facebook.com et on
  // log chaque event WA_EMBEDDED_SIGNUP recu pour faciliter le debug en cas
  // de format inattendu.
  useEffect(() => {
    if (!modeReel) return;
    function isFacebookOrigin(origin: string): boolean {
      try {
        const url = new URL(origin);
        return url.hostname === "facebook.com" || url.hostname.endsWith(".facebook.com");
      } catch {
        return false;
      }
    }
    function onMessage(event: MessageEvent) {
      if (!isFacebookOrigin(event.origin)) return;
      let parsed: EmbeddedSignupData | null = null;
      try {
        parsed = typeof event.data === "string" ? (JSON.parse(event.data) as EmbeddedSignupData) : (event.data as EmbeddedSignupData);
      } catch {
        return;
      }
      if (!parsed || parsed.type !== "WA_EMBEDDED_SIGNUP") return;
      // Log diagnostic : tous les events WA_EMBEDDED_SIGNUP recus, peu importe
      // l'event interne. Permet de detecter un format inattendu (event non
      // mappe, data manquante, etc.) sans avoir a remettre l'utilisateur dans
      // le flow Facebook.
      console.info("[WA Embedded Signup] message", { event: parsed.event, data: parsed.data, origin: event.origin });
      // FINISH = flow complet (WABA + numero), FINISH_ONLY_WABA = flow
      // sans ecran telephone (featureType: 'only_waba_sharing' pour numeros Twilio SMS).
      if (parsed.event !== "FINISH" && parsed.event !== "FINISH_ONLY_WABA") return;
      if (!parsed.data) {
        console.warn("[WA Embedded Signup] FINISH sans data — abandon");
        return;
      }
      const { waba_id, business_id, phone_number, phone_number_id } = parsed.data;
      if (!waba_id || !business_id) {
        console.warn("[WA Embedded Signup] FINISH sans waba_id/business_id", parsed.data);
        return;
      }
      messageRecuRef.current = true;
      // On ne part PAS tout de suite : on mémorise les ids et on attend le code
      // OAuth (callback FB.login), qui arrive sur un autre canal et parfois APRÈS
      // ce postMessage. tentativeEnvoi() partira dès que les deux sont là.
      signupDataRef.current = {
        wabaId: waba_id,
        businessId: business_id,
        phoneNumber: phone_number ?? null,
        phoneNumberId: phone_number_id ?? null,
      };
      tentativeEnvoi();
      // Filet : si le callback FB.login (et donc le code) n'arrive jamais après
      // ce FINISH, on envoie quand même après un court délai pour ne pas rester
      // coincé (le backend retombera sur le numéro du postMessage s'il existe).
      setTimeout(() => {
        if (!codeResoluRef.current) {
          console.warn("[WA Embedded Signup] FINISH reçu mais pas de code OAuth après délai — envoi sans code");
          codeResoluRef.current = true;
          tentativeEnvoi();
        }
      }, 3000);
    }
    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modeReel]);

  async function envoyerAuBackend(
    wabaId: string,
    businessId: string,
    phoneNumber: string | null,
    phoneNumberId: string | null
  ) {
    setLoading(true);
    try {
      const res = await apiFetch(`${API_BASE}/api/parametres/whatsapp/self-signup/connecter-meta`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          wabaId,
          metaBusinessId: businessId,
          phoneNumber,
          phoneNumberId,
          // Permet au backend de récupérer le verified_name (nom validé Meta).
          code: codeRef.current,
        }),
      });
      if (!res.ok) {
        const msg = await res.text();
        throw new Error(msg || "Échec de la connexion WhatsApp Business");
      }
      onSuccess();
    } catch (err: unknown) {
      onError?.(err instanceof Error ? err.message : "Erreur inconnue");
    } finally {
      setLoading(false);
    }
  }

  function lancer() {
    // Nouvelle tentative : on efface l'erreur de la tentative precedente
    // (ex. "Connexion Facebook annulee") des qu'on re-ouvre la popup, sinon
    // elle resterait affichee meme apres une connexion finalement reussie.
    onReset?.();
    if (!modeReel) {
      // Simulation : on appelle directement le backend avec des ids factices.
      // Utile pour tester la suite de la timeline sans flow Facebook.
      void envoyerAuBackend(
        `FAKE_WABA_${Date.now()}`,
        `FAKE_BIZ_${Date.now()}`,
        "+33611111111",
        null
      );
      return;
    }
    if (!window.FB) return;
    // Parametres conformes a la doc Twilio Tech Provider Integration Guide :
    // - sessionInfoVersion: 3 (requis pour obtenir le WABA ID)
    // - auth_type: "rerequest" (evite erreur "user is already logged" si re-clic)
    // - solutionID : Partner Solution ID fourni par Twilio (Phase 4 du rollout)
    const extras: Record<string, unknown> = {
      feature: "whatsapp_embedded_signup",
      sessionInfoVersion: 3,
    };
    if (FB_SOLUTION_ID) {
      extras.setup = { solutionID: FB_SOLUTION_ID };
    }
    // Réinitialise le point de rendez-vous : chaque tentative repart propre.
    messageRecuRef.current = false;
    codeRef.current = null;
    codeResoluRef.current = false;
    signupDataRef.current = null;
    envoyeRef.current = false;
    window.FB.login(
      (response) => {
        const code = response.authResponse?.code ?? null;
        console.info("[WA Embedded Signup] FB.login callback", { status: response.status, hasCode: !!code });
        // IMPORTANT : en flow response_type:'code', le SUCCÈS se mesure à la
        // PRÉSENCE DU CODE, PAS à status === 'connected'. Dans ce flow le token
        // n'est jamais exposé côté client, donc Meta renvoie très souvent
        // status:'unknown' AVEC un code valide. Gater sur 'connected' rejetait
        // une connexion réussie comme "annulée" et ne postait jamais au backend
        // (écran figé sur "Continuer sur Facebook").
        if (code) {
          codeRef.current = code;
          codeResoluRef.current = true;
          tentativeEnvoi();
          // Filet 2s : si le postMessage FINISH n'arrive pas, on envoie le code
          // SEUL, le backend résout le WABA via l'API Graph (durcissement).
          setTimeout(() => {
            if (messageRecuRef.current || envoyeRef.current) return;
            console.warn("[WA Embedded Signup] Pas de message FINISH — envoi du code seul (résolution WABA côté serveur)");
            envoyeRef.current = true;
            void envoyerAuBackend("", "", null, null);
          }, 2000);
          return;
        }
        // Callback sans code : Meta émet parfois un callback 'unknown' parasite
        // (avant/après le vrai). On ne déclare "annulé" qu'après un court délai,
        // et seulement si RIEN d'exploitable n'est arrivé entre-temps (ni FINISH,
        // ni code, ni envoi déjà parti) — sinon on écraserait une réussite.
        setTimeout(() => {
          if (messageRecuRef.current || codeRef.current || envoyeRef.current) return;
          console.warn("[WA Embedded Signup] FB.login sans code ni message FINISH — connexion annulée");
          onError?.("Connexion Facebook annulée.");
          onSuccess(); // refresh du statut cote parent
        }, 2500);
      },
      {
        config_id: FB_CONFIG_ID!,
        response_type: "code",
        override_default_response_type: true,
        auth_type: "rerequest",
        extras,
      }
    );
  }

  const disabled = !sdkLoaded || loading;
  return (
    <button
      onClick={lancer}
      disabled={disabled}
      className={`rounded-lg btn-primary px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-50 ${className || ""}`}
    >
      {loading
        ? "Connexion en cours…"
        : !sdkLoaded
          ? "Chargement…"
          : modeReel
            ? "Continuer sur Facebook"
            : "Continuer (mode simulation)"}
    </button>
  );
}
