import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

/**
 * Règles "anti-régression" alignées sur bug-hunter.md section 10 (frontend).
 * Chaque entrée correspond à un pattern qu'on a fixé dans le code et qu'on
 * ne veut pas voir revenir (palette interdite, em-dashes, confirm() natif,
 * silent catches, framer-motion imposé). Sévérité "warn" partout sauf
 * `confirm()` (error) : permet l'évolution progressive sans bloquer un PR.
 */
const projectRules = {
  rules: {
    "no-restricted-syntax": [
      "warn",
      // Palette Tailwind hardcodée — interdit par DESIGN.md "No-Blue Rule"
      // et la "Warm Tinting Rule". Utiliser var(--*) ou tokens sémantiques
      // (bg-accent, bg-success/10, bg-warning/10, bg-danger/10).
      {
        selector:
          "Literal[value=/\\b(bg|text|border|ring|from|to|via|fill|stroke|outline|shadow|divide|placeholder)-(indigo|cyan|amber|emerald|red|blue|sky|violet|purple|fuchsia|pink|rose|orange|teal|lime|yellow)-\\d+\\b/]",
        message:
          "Palette Tailwind hardcodée interdite (DESIGN.md No-Blue Rule). Mapping : indigo/cyan/blue → bg-accent ; amber → bg-warning/10 ; emerald → bg-success/10 ; red → bg-danger/10. Variables CSS : var(--color-accent), var(--color-lr-*).",
      },
      {
        selector:
          "TemplateElement[value.raw=/\\b(bg|text|border|ring|from|to|via|fill|stroke|outline|shadow|divide|placeholder)-(indigo|cyan|amber|emerald|red|blue|sky|violet|purple|fuchsia|pink|rose|orange|teal|lime|yellow)-\\d+\\b/]",
        message:
          "Palette Tailwind hardcodée dans template string interdite. Utiliser tokens Atelier (var(--color-accent), bg-success/10, etc.).",
      },
      // Greys froids hors palette warm Atelier. Utiliser --color-lr-* ou --border.
      {
        selector:
          "Literal[value=/\\b(bg|text|border|ring)-(zinc|gray|slate|stone|neutral)-\\d+\\b/]",
        message:
          "Greys Tailwind hardcodés interdits. Utiliser var(--color-lr-text-secondary), var(--color-lr-text-tertiary), var(--border), var(--muted).",
      },
      // Confirm() natif : impossible à styler, casse l'UX, contre-modèle DMS 2010.
      {
        selector: "CallExpression[callee.name='confirm']",
        message:
          "confirm() natif interdit. Utiliser le composant <Modal> de @/components/common/Modal avec footer custom (cf. /equipe désactivation vendeur).",
      },
      // Em-dash dans les chaînes : signature classique d'IA en français.
      // Utiliser virgule / point / deux-points / parenthèses.
      {
        selector: "Literal[value=/—/]",
        message:
          "Em-dash (—) interdit dans les chaînes user-facing (signature IA). Utiliser virgule, point, deux-points, parenthèses. Exception : '—' placeholder de cellule vide dans un tableau (équivalent n/a) — dans ce cas, ajoute un commentaire eslint-disable.",
      },
      {
        selector: "TemplateElement[value.raw=/—/]",
        message:
          "Em-dash (—) interdit dans les template strings user-facing. Utiliser virgule, point, deux-points.",
      },
    ],
    "no-restricted-imports": [
      "warn",
      {
        paths: [
          {
            name: "framer-motion",
            message:
              "framer-motion (~50KB gzip) à éviter sauf besoin réel de motion complexe. Préférer @keyframes CSS + cubic-bezier(0.16, 1, 0.3, 1) (ease-cinema) ou scroll-driven animations natives. Cf. globals.css existant.",
          },
        ],
      },
    ],
    // Empêche les <img src=""> raw — utiliser next/image ou <LeadriveLogo>.
    // Règle déjà incluse dans eslint-config-next core-web-vitals (warning),
    // on la passe à error pour la rendre bloquante au prochain PR.
    "@next/next/no-img-element": "error",
  },
};

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  projectRules,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    // E2E tests : Playwright a son propre runner et patterns, on ne lui
    // applique pas les restrictions UI.
    "e2e/**",
  ]),
]);

export default eslintConfig;
