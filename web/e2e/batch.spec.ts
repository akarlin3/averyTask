import { test, expect } from '@playwright/test';

/**
 * Smoke test for the NLP batch route. The E2E suite in this repo does not
 * log users in (same pattern as navigation.spec.ts), so we verify the
 * route is wired up and that unauthenticated hits land back on login —
 * which protects against a regression where a lazy-loaded route fails to
 * resolve and renders a white screen.
 */
test.describe('Batch preview route', () => {
  test('unauthenticated /batch/preview bounces to login', async ({ page }) => {
    await page.goto('/batch/preview');
    // ProtectedRoute redirects to /login (or /) when not authed; accept either.
    await expect(page).toHaveURL(/\/(login)?$/);
  });
});
