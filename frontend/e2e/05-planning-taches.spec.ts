import { test, expect } from "@playwright/test";
import { loginAsManager } from "./helpers/auth";

test.describe("Planning et gestion des tâches", () => {

  test.beforeEach(async ({ page }) => {
    const ok = await loginAsManager(page);
    test.skip(!ok, "E2E_EMAIL/E2E_PASSWORD non configurés");
  });

  test("la page planning affiche le toggle", async ({ page }) => {
    await page.goto("/planning");
    await expect(page.locator("text=Planning")).toBeVisible();
    await expect(page.getByRole("button", { name: /ma journée/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /calendrier/i })).toBeVisible();
  });

  test("le bouton nouvelle tâche ouvre la modale", async ({ page }) => {
    await page.goto("/planning");
    await page.locator("button", { hasText: /nouvelle tâche/i }).click();
    await expect(page.locator("input[placeholder*='Titre']")).toBeVisible();
  });

  test("basculer en vue calendrier", async ({ page }) => {
    await page.goto("/planning");
    await page.getByRole("button", { name: /calendrier/i }).click();
    await expect(page).toHaveURL(/vue=calendrier/);
    await expect(page.locator("text=Lun")).toBeVisible();
  });

  test("la page stats est accessible", async ({ page }) => {
    await page.goto("/stats");
    await expect(page.locator("text=Statistiques du pipeline")).toBeVisible();
    await expect(page.getByRole("button", { name: "30j" })).toBeVisible();
  });
});
