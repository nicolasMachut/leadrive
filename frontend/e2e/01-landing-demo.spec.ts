import { test, expect } from "@playwright/test";

/**
 * Parcours 1 — Landing page → Demande de démo
 */
test.describe("Landing page et demande de démo", () => {

  test("affiche le hero avec le slogan", async ({ page }) => {
    await page.goto("/");
    await expect(page.locator("h1")).toContainText("potentiel", { timeout: 10_000 });
    await expect(page).toHaveTitle(/Leadrive/);
  });

  test("affiche les sections clés", async ({ page }) => {
    await page.goto("/");
    await expect(page.locator("text=Votre meilleur vendeur")).toBeVisible({ timeout: 10_000 });
    await expect(page.locator("text=Simple, transparent")).toBeVisible();
    await expect(page.locator("text=Questions fréquentes")).toBeVisible();
  });

  test("le formulaire de démo est visible et fonctionnel", async ({ page }) => {
    await page.goto("/");
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
    await page.waitForTimeout(500);

    const form = page.locator("form").first();
    await expect(form).toBeVisible({ timeout: 10_000 });

    await form.getByPlaceholder(/nom/i).first().fill("Test E2E");
    await form.getByPlaceholder(/entreprise/i).first().fill("Concession Test");
    await form.getByPlaceholder(/email/i).first().fill("e2e-test@leadrive.fr");
  });

  test("les liens de navigation fonctionnent (mentions légales)", async ({ page }) => {
    await page.goto("/");
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
    await page.waitForTimeout(500);

    const mentionsLink = page.locator("a[href*='mentions-legales']").first();
    await expect(mentionsLink).toBeVisible({ timeout: 5000 });
    await mentionsLink.click();
    await expect(page).toHaveURL(/mentions-legales/);
  });

  test("le SEO est en place (title, structured data)", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveTitle(/Leadrive/, { timeout: 10_000 });
    const jsonLd = page.locator('script[type="application/ld+json"]').first();
    await expect(jsonLd).toBeAttached();
  });
});
