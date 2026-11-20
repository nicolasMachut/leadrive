import { test, expect } from "@playwright/test";
import { loginAsManager } from "./helpers/auth";

test.describe("Vue Kanban du pipeline", () => {

  test.beforeEach(async ({ page }) => {
    const ok = await loginAsManager(page);
    test.skip(!ok, "E2E_EMAIL/E2E_PASSWORD non configurés");
  });

  test("la page leads affiche le toggle", async ({ page }) => {
    await page.goto("/leads");
    await expect(page.locator("button[title='Vue kanban'], button[title='Vue liste']").first()).toBeVisible();
  });

  test("le kanban affiche les colonnes de statut", async ({ page }) => {
    await page.goto("/leads");
    await page.waitForTimeout(2000);
    const texte = await page.textContent("body");
    expect(texte).toContain("Nouveau");
    expect(texte).toContain("RDV pris");
    expect(texte).toContain("Terminé");
  });

  test("basculer en vue liste fonctionne", async ({ page }) => {
    await page.goto("/leads");
    const listeBtn = page.locator("button[title='Vue liste']");
    if (await listeBtn.isVisible()) {
      await listeBtn.click();
      await expect(page.locator("th", { hasText: "Nom" }).first()).toBeVisible({ timeout: 5000 });
      await expect(page).toHaveURL(/vue=liste/);
    }
  });

  test("cliquer sur un lead ouvre sa fiche", async ({ page }) => {
    await page.goto("/leads?vue=liste");
    await page.waitForTimeout(2000);
    const firstRow = page.locator("tbody tr").first();
    if (await firstRow.isVisible()) {
      await firstRow.click();
      await page.waitForURL("**/leads/*", { timeout: 5000 });
      await expect(page.locator("text=Informations")).toBeVisible();
    }
  });
});
