import { test, expect } from "@playwright/test";

/**
 * Parcours 6 — Garde-fou vocabulaire utilisateur final.
 *
 * Verifie que les pages publiques ne contiennent aucun terme technique interdit
 * par CLAUDE.md (Meta, OTP, Sender SID, display name, Self Sign-up, concession
 * exclusif Stellantis). Sert de filet contre les régressions du Package B.
 *
 * Tournables SANS backend (pages 100% statiques cote Next.js).
 */
test.describe("Vocabulaire user-facing propre", () => {

  // Liste des termes interdits par CLAUDE.md regle "Vocabulaire user-facing".
  // Cle = terme a chercher (case insensitive), valeur = explication courte.
  const TERMES_INTERDITS: Record<string, string> = {
    "Meta Business": "Devrait etre 'Facebook professionnel' ou 'WhatsApp Business'",
    "Meta Business Suite": "Devrait etre 'Facebook Business Suite'",
    "Self Sign-up": "Devrait etre 'activation guidee'",
    "OTP": "Devrait etre 'code de verification' (OTP en variable interne OK, pas en UI)",
    "Sender SID": "Devrait etre 'Identifiant technique'",
    "Quality rating": "Devrait etre 'Qualite des envois'",
    "Plafond envois": "Devrait etre 'Quantite quotidienne autorisee'",
    "display name": "Devrait etre 'nom affiche'",
    "Maj Meta": "Devrait etre 'Derniere synchronisation'",
  };

  // Pages publiques : aucun auth requis, surface visible par tous.
  const PAGES_PUBLIQUES = ["/", "/aide", "/mentions-legales", "/politique-confidentialite"];

  for (const path of PAGES_PUBLIQUES) {
    test(`${path} : aucun terme technique interdit dans le texte rendu`, async ({ page }) => {
      const errors: string[] = [];

      // On suit les erreurs reseau muettes (silent fails côté form demo notamment).
      page.on("requestfailed", (req) => {
        // Ignorer les requêtes Sentry / analytics non critiques
        const url = req.url();
        if (url.includes("sentry") || url.includes("clerk-telemetry")) return;
        errors.push(`${req.method()} ${url}, ${req.failure()?.errorText}`);
      });

      const response = await page.goto(path, { waitUntil: "domcontentloaded" });
      // Page doit charger (200 ou 404 acceptable pour /, on verifie juste pas de crash)
      expect(response?.status() ?? 500, `Page ${path} doit repondre`).toBeLessThan(500);

      // On recupere tout le texte visible (innerText : exclut le HTML cache)
      const visibleText = (await page.locator("body").innerText()).toLowerCase();

      for (const [terme, explication] of Object.entries(TERMES_INTERDITS)) {
        if (visibleText.includes(terme.toLowerCase())) {
          // En mode strict on echoue. Pour faciliter le debug, on cherche
          // l'extrait de contexte autour du terme.
          const idx = visibleText.indexOf(terme.toLowerCase());
          const context = visibleText.slice(Math.max(0, idx - 60), idx + terme.length + 60);
          throw new Error(
            `${path} : terme interdit '${terme}' trouve.\n  ${explication}\n  Contexte: "...${context}..."`,
          );
        }
      }

      // Pareil pour "concession" hors quelques contextes legitimes (cf. politique
      // de confidentialite ou il s'agit du terme legal "concession cliente").
      // Sur la landing et /aide, "concession" doit ceder a "etablissement".
      if (path === "/" || path === "/aide") {
        // On accepte les references aux "concessions auto" plurielles dans la
        // description SEO (inclusivite : concessions + garages + multimarques).
        // En revanche, "votre concession" ou "la concession" singulier = a fixer.
        const concessionPossessif = /votre concession|la concession |sa concession/gi;
        const matches = visibleText.match(concessionPossessif);
        expect(matches, `${path} : utilisation de "concession" au singulier possessif`).toBeNull();
      }
    });
  }

  test("aucun em-dash (—) dans les pages publiques sauf placeholders cellule", async ({ page }) => {
    for (const path of PAGES_PUBLIQUES) {
      await page.goto(path, { waitUntil: "domcontentloaded" });
      const text = await page.locator("body").innerText();

      // On scanne les em-dashes mais on accepte les exceptions :
      //  - "—" tout seul (placeholder cellule vide n/a)
      //  - " — " entoure de chiffres (intervalle, ex: "30 — 40")
      // L'idee : detecter " mot — mot " typique de la signature IA en francais.
      const aiSignaturePattern = /\b[a-zà-ÿ]{3,}\s—\s[a-zà-ÿ]{3,}\b/gi;
      const matches = text.match(aiSignaturePattern);
      if (matches && matches.length > 0) {
        throw new Error(
          `${path} : em-dash typique IA detecte dans le texte rendu (${matches.length}x).\n  Premier exemple: "${matches[0]}"\n  Remplacer par virgule, point ou parentheses.`,
        );
      }
    }
  });
});
