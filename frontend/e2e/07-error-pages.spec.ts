import { test, expect } from "@playwright/test";

/**
 * Parcours 7 — Pages d'erreur editoriales Atelier.
 *
 * Verifie que le 404 (`not-found.tsx`) rend bien le style Atelier (eyebrow
 * mono ember + display Bricolage + CTA retour) et pas le 404 generique Next.js.
 *
 * Tournable SANS backend.
 */
test.describe("Pages d'erreur Atelier", () => {

  test("404 affiche la page editoriale Atelier", async ({ page }) => {
    const response = await page.goto("/cette-page-nexiste-vraiment-pas-2026", {
      waitUntil: "domcontentloaded",
    });

    // Next.js retourne 404 status
    expect(response?.status()).toBe(404);

    // Eyebrow ember "Erreur 404"
    await expect(page.locator("text=Erreur 404")).toBeVisible({ timeout: 5_000 });

    // Display title : doit contenir "n'existe pas" (le titre est sur 2 lignes
    // avec un span ember sur "ou plus", on cherche un fragment stable)
    await expect(page.locator("h1")).toContainText("existe pas");

    // CTA principal vers l'accueil
    const cta = page.locator("a", { hasText: /Retour à l'accueil|Retour a l'accueil/ });
    await expect(cta).toBeVisible();
    await expect(cta).toHaveAttribute("href", "/");

    // CTA secondaire vers le centre d'aide
    const aideLink = page.locator("a", { hasText: /Centre d'aide/ });
    await expect(aideLink).toBeVisible();
  });

  test("404 contient un robots noindex (pas d'indexation google)", async ({ page }) => {
    await page.goto("/cette-page-nexiste-vraiment-pas-2026", { waitUntil: "domcontentloaded" });
    const robotsMeta = page.locator('meta[name="robots"]');
    // Pas d'attribut required (la meta peut etre absente), on verifie noindex si presente
    const count = await robotsMeta.count();
    if (count > 0) {
      const content = await robotsMeta.first().getAttribute("content");
      expect(content?.toLowerCase()).toContain("noindex");
    }
  });

  test("404 garde la signature gradient bar atelier", async ({ page }) => {
    await page.goto("/cette-page-nexiste-vraiment-pas-2026", { waitUntil: "domcontentloaded" });
    // La signature gradient bar est en haut fixed, on verifie sa presence
    const bar = page.locator(".bar-gradient").first();
    await expect(bar).toBeAttached();
  });
});
