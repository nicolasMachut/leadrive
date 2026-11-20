import { test, expect } from "@playwright/test";
import { loginAsManager } from "./helpers/auth";

test.describe("Import de leads avec mapping intelligent", () => {

  test.beforeEach(async ({ page }) => {
    const ok = await loginAsManager(page);
    test.skip(!ok, "E2E_EMAIL/E2E_PASSWORD non configurés");
  });

  test("la page import affiche les deux modes", async ({ page }) => {
    await page.goto("/import");
    await expect(page.locator("text=Importer un fichier")).toBeVisible();
    await expect(page.locator("text=Coller vos données")).toBeVisible();
  });

  test("upload CSV redirige vers le mapping", async ({ page }) => {
    await page.goto("/import");
    const csvContent = "Nom du compte,Téléphone mobile,Contact : Adresse e-mail,Marque\nDupont Jean,0612345678,jean@dupont.fr,Peugeot";
    await page.locator("input[type='file']").setInputFiles({
      name: "leads.csv", mimeType: "text/csv", buffer: Buffer.from(csvContent),
    });
    await page.getByRole("button", { name: /analyser/i }).click();
    await page.waitForURL("**/import/*/mapping", { timeout: 15_000 });
    await expect(page.locator("text=Mapping des colonnes")).toBeVisible();
  });

  test("le copier-coller fonctionne", async ({ page }) => {
    await page.goto("/import");
    await page.getByRole("button", { name: /coller/i }).click();
    await page.locator("textarea").fill("Nom\tEmail\nDupont\tjean@test.fr");
    await page.getByRole("button", { name: /analyser/i }).click();
    await page.waitForURL("**/import/*/mapping", { timeout: 15_000 });
  });

  test("la page templates est accessible", async ({ page }) => {
    await page.goto("/import/templates");
    await expect(page.locator("text=Templates de mapping")).toBeVisible();
  });
});
