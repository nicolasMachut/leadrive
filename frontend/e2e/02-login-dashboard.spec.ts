import { test, expect } from "@playwright/test";
import { loginAsManager } from "./helpers/auth";

test.describe("Authentification et dashboard", () => {

  test("la page login affiche le formulaire", async ({ page }) => {
    await page.goto("/login");
    await expect(page.locator("h1")).toContainText("Connexion", { timeout: 10_000 });
    await expect(page.locator("#email")).toBeVisible();
    await expect(page.locator("#motDePasse")).toBeVisible();
  });

  test("un manager se connecte et voit son dashboard", async ({ page }) => {
    const ok = await loginAsManager(page);
    test.skip(!ok, "E2E_EMAIL/E2E_PASSWORD non configurés");
    await expect(page.locator("[data-tour='dashboard']")).toBeVisible({ timeout: 10_000 });
  });

  test("la sidebar affiche les onglets", async ({ page }) => {
    const ok = await loginAsManager(page);
    test.skip(!ok, "E2E_EMAIL/E2E_PASSWORD non configurés");
    await expect(page.locator("nav a[href='/leads']")).toBeVisible();
    await expect(page.locator("nav a[href='/planning']")).toBeVisible();
  });
});
