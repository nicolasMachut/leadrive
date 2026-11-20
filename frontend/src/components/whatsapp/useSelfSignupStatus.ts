"use client";

import { useEffect, useState, useCallback } from "react";
import { useApiClient } from "@/lib/auth";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export interface TemplateStatut {
  name: string;
  statut: "EN_ATTENTE_SOUMISSION" | "SOUMIS" | "APPROUVE" | "REJETE" | "DESACTIVE";
  metaRejectReason: string | null;
  nbTentatives: number;
}

export interface SelfSignupStatus {
  /** Sub-account Twilio cree (étape 1 du flow) */
  subAccountCree: boolean;
  wabaId: string | null;
  metaBusinessId: string | null;
  numeroAffiche: string | null;
  /** Nom affiché à vos prospects sur WhatsApp (verified name validé Meta). Peut différer du nom d'établissement saisi. */
  nomAffiche: string | null;
  /** Tous les templates Meta sont APPROUVE -> envoi possible */
  tousTemplatesApprouves: boolean;
  templates: TemplateStatut[];
  /** Texte court à afficher sur la page d'activation */
  messageGuidage: string | null;
  /** Raison de blocage (envoi indisponible). Null si OK. */
  messageBlocage: string | null;
  /**
   * État de santé runtime du compte (≠ progression onboarding) : "ACTIF",
   * "RESTREINT"... Peut régresser après activation (restriction WhatsApp Business).
   */
  etatCompte: string | null;
  /** Message user-facing quand le compte est restreint (sinon null). */
  messageEtat: string | null;
  /**
   * Numéro/sender WhatsApp indisponible : envois récents en échec déterministe alors
   * que le compte est ACTIF (≠ restriction Meta). Problème à corriger côté établissement.
   */
  numeroEnEchec: boolean;
  /** Message user-facing quand numeroEnEchec (sinon null). */
  messageEnvoi: string | null;
}

/**
 * Cache module-level partage entre toutes les instances du hook (et entre les
 * remounts d'AppLayout sur navigation). Evite que le banner WhatsApp ne flashe
 * a chaque changement de page : la valeur connue est utilisee comme etat initial,
 * et le fetch suivant la rafraichit silencieusement en tache de fond.
 */
let cachedStatus: SelfSignupStatus | null = null;

/**
 * Hook : recupere et rafraichit l'etat du flow Self Sign-up WhatsApp pour le
 * tenant courant. Utilise par :
 *  - WhatsAppSelfSignupBanner (banner cross-page)
 *  - Page d'activation /parametres/whatsapp/activation
 *  - Boutons "Contacter par WhatsApp" (disable si bloque)
 */
export function useSelfSignupStatus(autoRefreshMs = 0): {
  status: SelfSignupStatus | null;
  loading: boolean;
  refresh: () => Promise<void>;
} {
  const apiFetch = useApiClient();
  // Etat initial reprend le cache module-level => pas de flash de la banner
  // au remount (changement de page).
  const [status, setStatus] = useState<SelfSignupStatus | null>(cachedStatus);
  const [loading, setLoading] = useState(cachedStatus === null);

  const fetcher = useCallback(async () => {
    try {
      const res = await apiFetch(`${API_BASE}/api/parametres/whatsapp/self-signup/statut`);
      if (!res.ok) return;
      const data = (await res.json()) as SelfSignupStatus;
      cachedStatus = data;
      setStatus(data);
    } catch {
      // Si l'endpoint n'est pas joignable (offline / 5xx), on garde l'ancien
      // status pour ne pas faire flasher la banner. Un toast d'erreur serait
      // bruyant pour un fetch passif.
    } finally {
      setLoading(false);
    }
  }, [apiFetch]);

  useEffect(() => {
    fetcher();
    if (autoRefreshMs > 0) {
      const id = setInterval(fetcher, autoRefreshMs);
      return () => clearInterval(id);
    }
  }, [fetcher, autoRefreshMs]);

  return { status, loading, refresh: fetcher };
}
