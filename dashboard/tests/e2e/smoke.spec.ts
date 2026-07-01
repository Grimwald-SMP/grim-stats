import { expect, test } from '@playwright/test';

// Smoke tests for the built SPA shell. These run without a backend: the app renders its navigation
// and theming regardless of API availability, then shows a "retrying" notice for data.
test('renders the app shell and brand', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('link', { name: /GrimStats/i })).toBeVisible();
  // The default theme is applied to the document.
  await expect(page.locator('html')).toHaveAttribute('data-theme', /.+/);
});

test('can navigate to admin sign-in', async ({ page }) => {
  await page.goto('/#/admin');
  await expect(page.getByText(/Admin sign in/i)).toBeVisible();
  await expect(page.getByRole('button', { name: /Sign in/i })).toBeVisible();
});

test('theme menu switches palette', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: /Choose theme/i }).click();
  await page.getByRole('button', { name: 'nord', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'nord');
});
