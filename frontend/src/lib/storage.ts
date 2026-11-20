/**
 * Purge les données Leadrive stockées dans le localStorage au logout.
 *
 * Sans ça, sur un poste partagé en établissement (cas fréquent : un PC commun aux
 * vendeurs), l'historique du chat d'aide (qui peut contenir des noms de prospects),
 * le badge "nouveaux leads" et les préférences de la bulle d'aide restent visibles
 * pour le compte suivant qui se connecte → fuite PII inter-comptes.
 *
 * On préserve volontairement `leadrive_dark` (préférence d'affichage, non sensible).
 */
const CLES_A_PURGER = [
  "leadrive_help_messages",
  "leadrive_help_open",
  "leadrive_help_mode",
  "leadrive.nouveauxLeads",
];

export function clearLeadriveStorage(): void {
  if (typeof window === "undefined") return;
  for (const cle of CLES_A_PURGER) {
    try {
      localStorage.removeItem(cle);
    } catch {
      /* mode privé Safari / quota : on ignore, best-effort */
    }
  }
}
