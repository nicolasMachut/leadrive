"use client";

import { useAuth as useClerkAuth, useUser, useOrganization } from "@clerk/nextjs";
import { useCallback, useEffect, useRef, useState } from "react";
import { clearLeadriveStorage } from "./storage";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export type Role = "SUPER_ADMIN" | "MANAGER" | "VENDEUR" | null;

export function useCurrentRole(): { role: Role; isLoaded: boolean } {
  const { isLoaded: userLoaded, user } = useUser();
  const { isLoaded: orgLoaded, membership } = useOrganization();

  if (!userLoaded) return { role: null, isLoaded: false };

  const publicMetadata = user?.publicMetadata as { isSuperAdmin?: boolean } | undefined;
  if (publicMetadata?.isSuperAdmin) return { role: "SUPER_ADMIN", isLoaded: true };

  if (!orgLoaded) return { role: null, isLoaded: false };

  const orgRole = membership?.role;
  if (orgRole === "org:manager" || orgRole === "admin" || orgRole === "org:admin") {
    return { role: "MANAGER", isLoaded: true };
  }
  if (orgRole === "org:vendeur" || orgRole === "basic_member" || orgRole === "org:member") {
    return { role: "VENDEUR", isLoaded: true };
  }

  return { role: null, isLoaded: true };
}

export function useIsSuperAdmin(): boolean {
  return useCurrentRole().role === "SUPER_ADMIN";
}

export function useIsManager(): boolean {
  return useCurrentRole().role === "MANAGER";
}

export function useIsVendeur(): boolean {
  return useCurrentRole().role === "VENDEUR";
}

export function useIsManagerOrSuperAdmin(): boolean {
  const role = useCurrentRole().role;
  return role === "SUPER_ADMIN" || role === "MANAGER";
}

export function useConcessionName(): string {
  const { isLoaded, organization } = useOrganization();
  if (!isLoaded) return "";
  return organization?.name ?? "";
}

interface MeData {
  prenom: string;
  nom: string;
  email: string;
  onboardingTermine: boolean;
  onboardingEtape: number | null;
}

// Cache module-level pour eviter le flash email→nom lors des navigations.
// Persiste tant que la page n'est pas rechargee (F5).
let meCache: MeData | null = null;
const meCacheListeners = new Set<(me: MeData | null) => void>();

// Mise a jour du cache + notification des composants useMe deja montes.
// Indispensable apres un PATCH /api/me/* sinon les redirections d'AppLayout
// (onboardingTermine) restent basees sur l'ancien snapshot.
export function setMeCache(me: MeData | null): void {
  meCache = me;
  meCacheListeners.forEach((l) => l(me));
}

/**
 * Deconnexion robuste. Purge le stockage local + le cache module-level, invalide
 * la session Clerk, puis FORCE une redirection dure vers /login.
 *
 * <p>Pourquoi le filet : appeler {@code signOut(...)} en fire-and-forget echoue
 * silencieusement quand Clerk n'est pas encore charge au moment du clic (le
 * bouton "ne fait rien", il faut recharger la page). On attend donc la fin du
 * signOut MAIS avec un timeout de 2s (si Clerk hang / pas pret), et on redirige
 * dans tous les cas via window.location pour garantir la sortie.</p>
 */
export async function deconnexionComplete(
  signOut: () => Promise<unknown>
): Promise<void> {
  clearLeadriveStorage();
  setMeCache(null);
  try {
    await Promise.race([
      Promise.resolve(signOut()).catch(() => undefined),
      new Promise((resolve) => setTimeout(resolve, 2000)),
    ]);
  } catch {
    /* on redirige quand meme */
  }
  if (typeof window !== "undefined") {
    window.location.assign("/login");
  }
}

export function useMe(): MeData | null {
  const apiFetch = useApiClient();
  const { isSignedIn } = useClerkAuth();
  const [me, setMe] = useState<MeData | null>(meCache);

  useEffect(() => {
    meCacheListeners.add(setMe);
    return () => { meCacheListeners.delete(setMe); };
  }, []);

  useEffect(() => {
    if (!isSignedIn) { setMeCache(null); return; }
    // Si deja en cache, pas de refetch
    if (meCache) { setMe(meCache); return; }
    let cancelled = false;
    apiFetch(`${API_BASE}/api/me`)
      .then((res) => res.ok ? res.json() : null)
      .then((data) => {
        if (!cancelled) setMeCache(data);
      })
      .catch(() => { if (!cancelled) setMe(null); });
    return () => { cancelled = true; };
  }, [isSignedIn, apiFetch]);

  return me;
}

export function useUserDisplayName(): string {
  const me = useMe();
  const { user } = useUser();
  if (me && (me.prenom || me.nom)) return `${me.prenom} ${me.nom}`.trim();
  if (!user) return "";
  const prenom = user.firstName ?? "";
  const nom = user.lastName ?? "";
  if (prenom || nom) return `${prenom} ${nom}`.trim();
  return user.primaryEmailAddress?.emailAddress ?? "Utilisateur";
}

export function useUserEmail(): string {
  const { user } = useUser();
  return user?.primaryEmailAddress?.emailAddress ?? "";
}

/**
 * Hook qui retourne une fonction `apiFetch(url, init)` ajoutant automatiquement
 * le JWT Clerk dans l'header Authorization.
 * Necessite un JWT Template "leadrevive-backend" configure dans le dashboard Clerk.
 */
export function useApiClient() {
  const { getToken, signOut } = useClerkAuth();
  // Les fonctions Clerk (getToken, signOut) peuvent etre recreees a chaque render
  // (objets non memoises cote librairie). Si elles entrent dans les deps du
  // useCallback, apiFetch change a chaque render => tous les useEffect en aval
  // qui ont [apiFetch] re-fetchent en boucle => UI gelee.
  // Fix : stabiliser via refs, useCallback sans dependances => identite stable.
  const getTokenRef = useRef(getToken);
  const signOutRef = useRef(signOut);
  useEffect(() => { getTokenRef.current = getToken; }, [getToken]);
  useEffect(() => { signOutRef.current = signOut; }, [signOut]);

  return useCallback(
    async (url: string, init?: RequestInit): Promise<Response> => {
      const token = await getTokenRef.current({ template: "leadrevive-backend" });
      const headers = new Headers(init?.headers);
      if (token) headers.set("Authorization", `Bearer ${token}`);
      if (!headers.has("Content-Type") && init?.body && typeof init.body === "string") {
        headers.set("Content-Type", "application/json");
      }
      const response = await fetch(url, { ...init, headers });
      // Si le backend rejette l'auth (JWT expire, user desactive cote backend, etc.),
      // on force un signOut + redirect login plutot que de laisser l'utilisateur faire
      // des actions qui echoueront silencieusement.
      // Le param reason=session-ended permet a la page login d'afficher un message
      // clair (sinon le user est juste "ejecte" sans comprendre pourquoi).
      if (response.status === 401 && typeof window !== "undefined") {
        clearLeadriveStorage(); // purge PII locale avant d'éjecter (poste partagé)
        setMeCache(null); // purge le cache module-level (prenom/nom/email du user sortant)
        try {
          if (signOutRef.current) await signOutRef.current();
        } catch { /* ignore */ }
        window.location.replace("/login?reason=session-ended");
      }
      return response;
    },
    []
  );
}

/**
 * Transforme une erreur technique en message lisible pour l'utilisateur.
 *
 * Strategie :
 *   1. Patterns techniques reconnus (network, 4xx, 5xx, timeout) -> message custom rassurant
 *   2. Fallback generique "Erreur NNN" sans contexte -> message generique
 *   3. Sinon -> on retourne le message tel quel : le backend rédige deja en francais
 *      propre (validation Bean Validation, WhatsAppOnboardingException, etc.). Ecraser
 *      ces messages par un generique masque les informations utiles a l'utilisateur.
 */
export function formatErreur(err: unknown): string {
  if (!(err instanceof Error)) return "Une erreur inattendue est survenue. Réessayez dans quelques instants.";
  const raw = err.message;
  const msg = raw.toLowerCase();
  if (msg.includes("failed to fetch") || msg.includes("networkerror") || msg.includes("network"))
    return "Impossible de contacter le serveur. Vérifiez votre connexion internet ou réessayez dans quelques instants.";
  if (msg.includes("401") || msg.includes("unauthorized"))
    return "Votre session a expiré. Veuillez vous reconnecter.";
  if (msg.includes("403") || msg.includes("forbidden"))
    return "Vous n'avez pas les droits pour effectuer cette action.";
  if (msg.includes("404"))
    return "La ressource demandée n'existe pas ou a été supprimée.";
  if (msg.includes("500") || msg.includes("internal server"))
    return "Une erreur serveur est survenue. L'équipe technique a été notifiée.";
  if (msg.includes("timeout") || msg.includes("aborted"))
    return "La requête a pris trop de temps. Réessayez dans quelques instants.";
  // Fallback "Erreur NNN" genere quand on n'a pas pu extraire de message metier : pas utile pour l'UI.
  if (/^erreur \d+/.test(msg)) return "Une erreur est survenue. Réessayez dans quelques instants.";
  // Si le message backend est exploitable (court, pas de stack trace, pas un
  // code technique brut), on le retourne tel quel : permet d'afficher les
  // messages metier ("Ce vendeur a 3 leads assignes...", "Email deja utilise",
  // etc.) au lieu du fallback generique.
  if (raw && raw.length > 0 && raw.length < 300 && !raw.startsWith("Error ") && !raw.includes("\n")) {
    return raw;
  }
  return "Une erreur est survenue. Réessayez dans quelques instants.";
}
