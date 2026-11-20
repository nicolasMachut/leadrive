/**
 * Libellé STATIQUE (non rafraîchi en continu) pour l'échéance d'un envoi
 * automatique du premier message WhatsApp.
 *
 * Volontairement sans compte à rebours qui tic-tac : on affiche une estimation
 * lisible au moment du rendu. Le scheduler tourne toutes les ~2 min, une
 * précision à la minute près n'apporterait rien et créerait du bruit visuel.
 *
 *  - < 60 min    → "dans ~X min"
 *  - même jour   → "à HHhMM"
 *  - demain      → "demain à HHhMM"
 *  - au-delà     → date courte "le JJ/MM à HHhMM"
 *
 * IMPORTANT : on formate l'heure d'envoi EFFECTIVE (calculée côté backend en
 * tenant compte de la plage horaire d'envoi), pas l'échéance brute armée. Hors
 * plage, l'échéance brute afficherait "imminent" alors que le message ne partira
 * qu'à l'ouverture de la plage. On accepte donc l'échéance effective si fournie,
 * avec repli sur l'échéance brute pour rétro-compatibilité.
 */
export function formatEnvoiAutoLabel(
  effectifLeIso: string | null | undefined,
  prevuLeIso?: string | null | undefined
): string {
  const iso = effectifLeIso ?? prevuLeIso;
  if (!iso) return "";
  const prevu = new Date(iso);
  if (Number.isNaN(prevu.getTime())) return "";

  const maintenant = new Date();
  const diffMs = prevu.getTime() - maintenant.getTime();
  const diffMin = Math.round(diffMs / 60000);

  const heure = `${prevu.getHours()}h${String(prevu.getMinutes()).padStart(2, "0")}`;

  // Échéance déjà passée (le scheduler n'a pas encore tourné) : traité comme imminent.
  if (diffMin <= 0) return "imminent";

  if (diffMin < 60) return `dans ~${diffMin} min`;

  const memeJour = prevu.toDateString() === maintenant.toDateString();
  if (memeJour) return `à ${heure}`;

  const demain = new Date(maintenant);
  demain.setDate(demain.getDate() + 1);
  if (prevu.toDateString() === demain.toDateString()) return `demain à ${heure}`;

  const date = `${String(prevu.getDate()).padStart(2, "0")}/${String(prevu.getMonth() + 1).padStart(2, "0")}`;
  return `le ${date} à ${heure}`;
}
