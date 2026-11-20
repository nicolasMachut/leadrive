// Constantes partagées pour les statuts de lead.
// Utilisées dans la liste prospects (/leads), le détail (/leads/[id]) et
// partout où on affiche un statut user-facing. ProgressionBadge garde sa
// propre map avec metadata (color, step) — on ne factorise pas ici pour
// éviter un refactor cross-component, mais les labels DOIVENT rester en
// sync.

export const STATUTS_PIPELINE = ["NOUVEAU", "CONTACTE", "EN_COURS", "RDV_PRIS", "TERMINE"] as const;

export const STATUTS_ACTIFS_DEFAUT = ["NOUVEAU", "CONTACTE", "EN_COURS", "RDV_PRIS"] as const;

export type StatutLead = (typeof STATUTS_PIPELINE)[number];

// Label user-facing. JAMAIS exposer la constante backend brute en UI.
export const STATUT_LABELS: Record<string, string> = {
  NOUVEAU: "Nouveau",
  CONTACTE: "Contacté",
  EN_COURS: "En cours",
  RDV_PRIS: "RDV pris",
  TERMINE: "Terminé",
};

// Ordre du pipeline pour calcul de progression / saut anormal.
export const STATUT_ORDER: Record<string, number> = {
  NOUVEAU: 0,
  CONTACTE: 1,
  EN_COURS: 2,
  RDV_PRIS: 3,
  TERMINE: 4,
};

export function statutLabel(statut: string | null | undefined): string {
  if (!statut) return "—";
  return STATUT_LABELS[statut] ?? statut;
}

// Couleurs des statuts (pour graphes Recharts qui n'acceptent pas les CSS vars
// + bandeaux d'entonnoir / barres de progression). Une seule source de vérité,
// alignée sur l'identité ember + tons warning/success/muted du design system.
export const STATUT_COLORS: Record<string, string> = {
  NOUVEAU: "#D9603A",      // accent ember mid
  CONTACTE: "#D97706",     // warning
  EN_COURS: "#A8401F",     // accent ember deep
  RDV_PRIS: "#0E9F6E",     // success
  TERMINE: "#8B8676",      // muted
};

