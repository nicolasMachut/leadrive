import { formatErreur } from "@/lib/auth";
import type { ToastTone } from "@/lib/realtime";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

type ShowToast = (opts: {
  titre: string;
  message?: string;
  tone?: ToastTone;
  cible?: string;
  duration?: number;
  action?: { label: string; onClick: () => void };
}) => number;

type ApiFetch = (url: string, init?: RequestInit) => Promise<Response>;

/**
 * Affiche un toast de succes avec un bouton "Annuler" (undo) apres une action RDV
 * (Effectue / Abandonner). L'undo POST /api/feedback/rdv/annuler-action : le backend
 * rouvre la tache (A_FAIRE + lead RDV_PRIS) et supprime le feedback cree.
 *
 * Si {@code rdvId} est absent (RDV sans tache liee), on affiche un toast simple sans
 * undo (rien a rouvrir cote tache).
 */
export function toastUndoRdv(params: {
  showToast: ShowToast;
  apiFetch: ApiFetch;
  leadId: number;
  rdvId: number | null | undefined;
  titre: string;
  onReverted: () => void;
}): void {
  const { showToast, apiFetch, leadId, rdvId, titre, onReverted } = params;

  if (rdvId == null) {
    showToast({ titre, tone: "success" });
    return;
  }

  showToast({
    titre,
    tone: "success",
    duration: 7000,
    action: {
      label: "Annuler",
      onClick: async () => {
        try {
          const res = await apiFetch(`${API_BASE}/api/feedback/rdv/annuler-action`, {
            method: "POST",
            body: JSON.stringify({ leadId, rdvId }),
          });
          if (!res.ok) {
            const msg = await res.text().catch(() => "");
            throw new Error(msg || `Erreur ${res.status}`);
          }
          onReverted();
        } catch (err) {
          showToast({
            titre: "Annulation impossible",
            message: formatErreur(err),
            tone: "danger",
          });
        }
      },
    },
  });
}

/**
 * Toast succes + "Annuler" apres un changement de statut de lead (dropdown Pilotage).
 * L'undo re-PATCH /api/leads/{id}/statut vers le statut PRECEDENT (source SELECTBOX,
 * non facturable). Sans ca, un statut change par erreur (clic mal place sur mobile)
 * n'avait aucun retour arriere, contrairement aux actions RDV.
 */
export function toastUndoStatut(params: {
  showToast: ShowToast;
  apiFetch: ApiFetch;
  leadId: number;
  statutPrecedent: string;
  labelStatut: string;
  onReverted: () => void;
}): void {
  const { showToast, apiFetch, leadId, statutPrecedent, labelStatut, onReverted } = params;
  showToast({
    titre: "Statut mis à jour",
    message: `Lead passé en ${labelStatut}.`,
    tone: "success",
    duration: 7000,
    action: {
      label: "Annuler",
      onClick: async () => {
        try {
          const res = await apiFetch(`${API_BASE}/api/leads/${leadId}/statut`, {
            method: "PATCH",
            headers: { "X-Source": "SELECTBOX" },
            body: JSON.stringify({ statut: statutPrecedent }),
          });
          if (!res.ok) {
            const msg = await res.text().catch(() => "");
            throw new Error(msg || `Erreur ${res.status}`);
          }
          onReverted();
        } catch (err) {
          showToast({ titre: "Annulation impossible", message: formatErreur(err), tone: "danger" });
        }
      },
    },
  });
}

/**
 * Formate une date ISO en libelle FR lisible pour le toast, ex. "au mardi 15 juillet à 14:00".
 * Pas d'heure "à minuit" trompeuse : on affiche toujours l'heure telle quelle.
 */
function formatDateReport(iso: string): string {
  const d = new Date(iso);
  const jour = d.toLocaleDateString("fr-FR", { weekday: "long", day: "numeric", month: "long" });
  const heure = d.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });
  return `au ${jour} à ${heure}`;
}

/**
 * Toast succes + "Annuler" apres un report de tache/RDV (PATCH /api/taches/{id}/reporter).
 * Sans ca, "Reporter" n'avait ni confirmation ni retour arriere, contrairement a ses actions
 * soeurs "Effectue" et "Abandonner". L'undo re-PATCH la MEME route avec l'ANCIENNE date
 * d'echeance (restauration). Si l'ancienne date est absente (surface sans la donnee en cache),
 * on affiche un toast de confirmation SANS bouton Annuler (graceful).
 */
export function toastUndoReporter(params: {
  showToast: ShowToast;
  apiFetch: ApiFetch;
  tacheId: number;
  ancienneDateEcheance: string | null | undefined;
  nouvelleDateEcheance: string;
  estRdv?: boolean;
  onReverted: () => void;
}): void {
  const { showToast, apiFetch, tacheId, ancienneDateEcheance, nouvelleDateEcheance, estRdv = true, onReverted } = params;
  const titre = estRdv ? "RDV reporté" : "Tâche reportée";
  const message = formatDateReport(nouvelleDateEcheance);

  if (ancienneDateEcheance == null) {
    showToast({ titre, message, tone: "success" });
    return;
  }

  showToast({
    titre,
    message,
    tone: "success",
    duration: 7000,
    action: {
      label: "Annuler",
      onClick: async () => {
        try {
          const res = await apiFetch(`${API_BASE}/api/taches/${tacheId}/reporter`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ dateEcheance: ancienneDateEcheance }),
          });
          if (!res.ok) {
            const msg = await res.text().catch(() => "");
            throw new Error(msg || `Erreur ${res.status}`);
          }
          onReverted();
        } catch (err) {
          showToast({ titre: "Annulation impossible", message: formatErreur(err), tone: "danger" });
        }
      },
    },
  });
}

/**
 * Toast succes + "Annuler" apres l'annulation d'un envoi auto du premier message
 * WhatsApp (action REVERSIBLE : on a juste efface un flag en base). L'undo RE-ARME
 * l'envoi via POST /api/leads/{leadId}/envoi-auto/reprogrammer avec {@code anciennePrevuLe}
 * (la valeur premierMessageAutoPrevuLe d'AVANT l'annulation), puis {@code onReverted}.
 *
 * Si {@code anciennePrevuLe} est absente (surface sans la donnee en cache), on affiche
 * un toast de confirmation SANS bouton Annuler (rien a re-armer, graceful).
 */
export function toastUndoEnvoiAutoAnnule(params: {
  showToast: ShowToast;
  apiFetch: ApiFetch;
  leadId: number;
  anciennePrevuLe: string | null | undefined;
  onReverted: () => void;
}): void {
  const { showToast, apiFetch, leadId, anciennePrevuLe, onReverted } = params;

  if (anciennePrevuLe == null) {
    showToast({ titre: "Envoi auto annulé", tone: "success" });
    return;
  }

  showToast({
    titre: "Envoi auto annulé",
    message: "L'envoi automatique a été annulé.",
    tone: "success",
    duration: 7000,
    action: {
      label: "Annuler",
      onClick: async () => {
        try {
          const res = await apiFetch(`${API_BASE}/api/leads/${leadId}/envoi-auto/reprogrammer`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ dateEcheance: anciennePrevuLe }),
          });
          if (!res.ok) {
            const msg = await res.text().catch(() => "");
            throw new Error(msg || `Erreur ${res.status}`);
          }
          onReverted();
        } catch (err) {
          showToast({ titre: "Annulation impossible", message: formatErreur(err), tone: "danger" });
        }
      },
    },
  });
}

/**
 * Pattern d'annulation pour une action IRREVERSIBLE (effet de bord externe non
 * rappelable : envoi WhatsApp/SMS/email). Contrairement a un undo classique, on ne
 * peut PAS revenir en arriere une fois le message parti. On offre donc l'annulation
 * en DIFFERANT l'effet de quelques secondes : rien n'est envoye tant que le delai
 * n'est pas ecoule.
 *
 * Comportement :
 * - demarre un setTimeout({@code delaiMs}) ; a son declenchement, appelle {@code onEnvoyer}
 *   (l'envoi reel). En cas d'erreur, appelle {@code onErreur}.
 * - affiche un toast "Envoi du message…" (sous-texte "Part dans quelques secondes")
 *   avec un bouton "Annuler" (duration = delaiMs) dont le onClick ANNULE le timeout
 *   (aucun envoi, aucun appel reseau) et affiche un toast "Envoi annulé".
 *
 * ROBUSTESSE : le flag premierMessageAutoPrevuLe n'est PAS touche pendant le delai
 * (c'est l'envoi reel via /whatsapp qui l'efface cote back). Donc si l'envoi differe
 * est annule, l'envoi AUTOMATIQUE normal reste programme et partira a son heure.
 *
 * ATTENTION : le timer vit dans le closure de ce toast. Si l'utilisateur navigue
 * ailleurs (demontage du composant) pendant le delai, le timer continue et l'envoi
 * part quand meme (comportement voulu : "Envoyer maintenant" = intention ferme).
 * Le {@code onEnvoyer}/{@code onErreur} doivent donc etre robustes a un composant
 * potentiellement demonte (ne pas supposer que le state React est encore monte).
 */
export function toastEnvoiDiffere(params: {
  showToast: ShowToast;
  delaiMs?: number;
  onEnvoyer: () => Promise<void>;
  onErreur: (err: unknown) => void;
  onAnnule?: () => void;
}): void {
  const { showToast, delaiMs = 7000, onEnvoyer, onErreur, onAnnule } = params;
  let annule = false;

  const timer = setTimeout(async () => {
    if (annule) return;
    try {
      await onEnvoyer();
    } catch (err) {
      onErreur(err);
    }
  }, delaiMs);

  showToast({
    titre: "Envoi du message…",
    message: "Part dans quelques secondes.",
    tone: "success",
    duration: delaiMs,
    action: {
      label: "Annuler",
      onClick: () => {
        annule = true;
        clearTimeout(timer);
        // onAnnule : reinitialise l'etat de chargement de l'appelant (le bouton
        // "Envoyer maintenant" etait desactive pendant le differe pour empecher
        // un double declenchement). Sans ca, le bouton resterait bloque.
        onAnnule?.();
        showToast({ titre: "Envoi annulé", tone: "success" });
      },
    },
  });
}

/**
 * Toast succes + "Annuler" apres l'archivage d'un prospect (action REVERSIBLE : on a juste
 * pose un flag {@code archive=true} en base). L'undo DESARCHIVE le prospect via
 * POST /api/leads/{leadId}/restaurer, puis {@code onReverted} (revenir sur la fiche /
 * recharger la liste). Echec de l'undo -> toast danger via formatErreur (jamais silencieux).
 */
export function toastUndoArchiveLead(params: {
  showToast: ShowToast;
  apiFetch: ApiFetch;
  leadId: number;
  onReverted: () => void;
}): void {
  const { showToast, apiFetch, leadId, onReverted } = params;
  showToast({
    titre: "Prospect archivé",
    message: "Il n'apparaît plus dans vos listes.",
    tone: "success",
    duration: 7000,
    action: {
      label: "Annuler",
      onClick: async () => {
        try {
          const res = await apiFetch(`${API_BASE}/api/leads/${leadId}/restaurer`, {
            method: "POST",
          });
          if (!res.ok) {
            const msg = await res.text().catch(() => "");
            throw new Error(msg || `Erreur ${res.status}`);
          }
          onReverted();
        } catch (err) {
          showToast({ titre: "Annulation impossible", message: formatErreur(err), tone: "danger" });
        }
      },
    },
  });
}

/**
 * Toast succes + "Annuler" apres le desarchivage d'un prospect (action REVERSIBLE :
 * on a juste efface le flag {@code archive} en base). Symetrique de {@code toastUndoArchiveLead}.
 * L'undo RE-ARCHIVE le prospect via DELETE /api/leads/{leadId}, puis {@code onReverted}
 * (revenir sur la fiche / recharger la liste). Echec de l'undo -> toast danger via
 * formatErreur (jamais silencieux).
 */
export function toastUndoDesarchiveLead(params: {
  showToast: ShowToast;
  apiFetch: ApiFetch;
  leadId: number;
  onReverted: () => void;
}): void {
  const { showToast, apiFetch, leadId, onReverted } = params;
  showToast({
    titre: "Prospect désarchivé",
    message: "Il réapparaît dans vos listes actives.",
    tone: "success",
    duration: 7000,
    action: {
      label: "Annuler",
      onClick: async () => {
        try {
          const res = await apiFetch(`${API_BASE}/api/leads/${leadId}`, {
            method: "DELETE",
          });
          if (!res.ok) {
            const msg = await res.text().catch(() => "");
            throw new Error(msg || `Erreur ${res.status}`);
          }
          onReverted();
        } catch (err) {
          showToast({ titre: "Annulation impossible", message: formatErreur(err), tone: "danger" });
        }
      },
    },
  });
}

/**
 * Variante GENERIQUE pour une tache (non-RDV, ou RDV sans lead) marquee "Effectuee"
 * directement (sans le slide-up de feedback) : toast succes + "Annuler" qui rouvre
 * la tache (POST /api/taches/{id}/rouvrir → statut A_FAIRE). Pas de feedback a supprimer.
 */
export function toastUndoTache(params: {
  showToast: ShowToast;
  apiFetch: ApiFetch;
  tacheId: number;
  titre: string;
  onReverted: () => void;
}): void {
  const { showToast, apiFetch, tacheId, titre, onReverted } = params;
  showToast({
    titre,
    tone: "success",
    duration: 7000,
    action: {
      label: "Annuler",
      onClick: async () => {
        try {
          const res = await apiFetch(`${API_BASE}/api/taches/${tacheId}/rouvrir`, {
            method: "POST",
          });
          if (!res.ok) {
            const msg = await res.text().catch(() => "");
            throw new Error(msg || `Erreur ${res.status}`);
          }
          onReverted();
        } catch (err) {
          showToast({ titre: "Annulation impossible", message: formatErreur(err), tone: "danger" });
        }
      },
    },
  });
}
