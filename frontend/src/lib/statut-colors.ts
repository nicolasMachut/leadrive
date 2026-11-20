/**
 * Couleurs Tailwind centralisees pour les statuts de leads.
 * Source unique utilisee par les pages leads, la fiche lead, les stats, etc.
 *
 * Mapping Atelier (DESIGN.md) :
 * - NOUVEAU = ember (One Attention Rule, action requise du vendeur)
 * - CONTACTE / EN_COURS = warning subtle (en attente du prospect, dialogue en cours)
 * - RDV_PRIS = success (objectif atteint)
 * - TERMINE = muted (dossier clos, ambient)
 */
export const STATUT_COLORS: Record<string, string> = {
  NOUVEAU: "bg-accent/10 text-accent",
  CONTACTE: "bg-warning/10 text-warning",
  EN_COURS: "bg-warning/10 text-warning",
  RDV_PRIS: "bg-success/10 text-success",
  TERMINE: "bg-[var(--muted)] text-[var(--color-lr-text-secondary)]",
};

/**
 * Couleurs hex correspondantes pour les charts Recharts et les SVG.
 * Aligne avec la palette Atelier (ember / warning / success / ink-tertiary).
 */
export const STATUT_HEX: Record<string, string> = {
  NOUVEAU: "#D9603A",
  CONTACTE: "#D97706",
  EN_COURS: "#A8401F",
  RDV_PRIS: "#0E9F6E",
  TERMINE: "#8B8676",
};
