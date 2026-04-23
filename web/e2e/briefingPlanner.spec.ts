import { test, expect } from '@playwright/test';

test.describe('Briefing + Planner routes', () => {
  test('unauthenticated /briefing bounces to login', async ({ page }) => {
    await page.goto('/briefing');
    await expect(page).toHaveURL(/\/(login)?$/);
  });

  test('unauthenticated /planner bounces to login', async ({ page }) => {
    await page.goto('/planner');
    await expect(page).toHaveURL(/\/(login)?$/);
  });
});
