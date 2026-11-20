// Couleurs partagées pour les graphes Recharts.
//
// Recharts ne sait pas résoudre `var(--color-accent)` côté SVG (la spec SVG
// limite fill/stroke aux strings concrètes). On garde donc les hex centralisés
// ici, en miroir du @theme inline de globals.css. Si la palette ember change,
// c'est le seul fichier à mettre à jour côté data viz.

export const CHART_COLORS = {
  accent: "#D9603A",       // ember mid, --color-accent
  accentDeep: "#A8401F",   // ember deep, --color-accent-deep
  accentLight: "#E07B5A",  // ember light, --color-accent-light
  warning: "#D97706",      // --color-warning
  success: "#0E9F6E",      // --color-success
  danger: "#DC2626",       // --color-danger
  muted: "#8B8676",        // --color-lr-text-tertiary
  border: "#E5E2DA",       // --border (light)
} as const;

export type ChartColorKey = keyof typeof CHART_COLORS;
