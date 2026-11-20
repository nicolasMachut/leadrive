import { Page } from "@playwright/test";

/**
 * Login via le formulaire custom Clerk de Leadrive.
 * Nécessite les variables d'env E2E_EMAIL et E2E_PASSWORD.
 * Retourne false si les credentials ne sont pas configurés (tests skippés).
 */
export async function loginAsManager(page: Page): Promise<boolean> {
  const email = process.env.E2E_EMAIL;
  const password = process.env.E2E_PASSWORD;

  if (!email || !password) {
    return false;
  }

  await page.goto("/login");

  // Formulaire custom Leadrive (labels: #email, #motDePasse)
  await page.locator("#email").fill(email);
  await page.locator("#motDePasse").fill(password);
  await page.locator("button[type='submit']").click();

  // Attendre la redirection vers le dashboard
  await page.waitForURL("**/dashboard", { timeout: 20_000 });
  return true;
}

export async function loginAsVendeur(page: Page): Promise<boolean> {
  const email = process.env.E2E_VENDEUR_EMAIL;
  const password = process.env.E2E_VENDEUR_PASSWORD;

  if (!email || !password) {
    return false;
  }

  await page.goto("/login");
  await page.locator("#email").fill(email);
  await page.locator("#motDePasse").fill(password);
  await page.locator("button[type='submit']").click();
  await page.waitForURL("**/dashboard", { timeout: 20_000 });
  return true;
}
