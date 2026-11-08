import { expect, test } from '@playwright/test'
import { attachEvidenceScreenshot } from './evidence-fixtures'

test('caixa do servidor ocupa a largura disponível sem overflow após login', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/servidor/')
  await page.getByRole('combobox', { name: 'Organização' }).click()
  await page.getByRole('option', { name: 'Município Alfa (demonstração)', exact: true }).click()
  await page.getByLabel('Usuário').fill('servidor')
  await page.getByLabel('Senha').fill('e2e-local-only-password')
  const requiredStaffResponses = [
    '/staff/departments',
    '/staff/protocols',
    '/staff/organization/department-management',
    '/staff/organization/organogram'
  ].map(pathSuffix => page.waitForResponse(response =>
    response.request().method() === 'GET'
    && new URL(response.url()).pathname.endsWith(pathSuffix)
    && response.ok()
  ))
  await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
  await Promise.all(requiredStaffResponses)

  await expect(page.getByRole('heading', { name: 'Protocolos', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Organograma', exact: true })).toBeVisible()
  await expect(page.locator('.staff-access .q-spinner')).toBeHidden()
  await expect(page.locator('.inbox-panel .q-spinner')).toBeHidden()
  const workspace = page.locator('.staff-workspace')
  const desktopWidth = await workspace.evaluate(element => ({
    workspace: element.getBoundingClientRect().width,
    viewport: document.documentElement.clientWidth,
    columns: getComputedStyle(element).gridTemplateColumns.trim().split(/\s+/).length
  }))
  expect(desktopWidth.workspace / desktopWidth.viewport).toBeGreaterThan(0.85)
  expect(desktopWidth.columns).toBe(1)
  const emptyState = page.locator('.inbox-empty-state')
  await expect(emptyState).toBeVisible()
  const emptyStateLayout = await emptyState.evaluate(element => ({
    bounds: element.getBoundingClientRect().toJSON(),
    inboxBounds: element.parentElement!.getBoundingClientRect().toJSON(),
    textAlign: getComputedStyle(element).textAlign
  }))
  expect(emptyStateLayout.bounds.width / emptyStateLayout.inboxBounds.width).toBeGreaterThan(0.85)
  const emptyCenter = emptyStateLayout.bounds.x + emptyStateLayout.bounds.width / 2
  const inboxCenter = emptyStateLayout.inboxBounds.x + emptyStateLayout.inboxBounds.width / 2
  expect(Math.abs(emptyCenter - inboxCenter)).toBeLessThan(emptyStateLayout.inboxBounds.width * 0.01)
  expect(emptyStateLayout.textAlign).toBe('center')

  const inbox = page.locator('.inbox-panel')
  await inbox.scrollIntoViewIfNeeded()
  await attachEvidenceScreenshot(page, testInfo, 'servidor-workspace-full-width', inbox)

  await page.setViewportSize({ width: 390, height: 844 })
  const mobileLayout = await page.evaluate(() => ({
    document: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    workspace: (() => {
      const element = document.querySelector<HTMLElement>('.staff-workspace')
      return !!element && element.scrollWidth <= element.clientWidth
    })()
  }))
  expect(mobileLayout.document).toBe(true)
  expect(mobileLayout.workspace).toBe(true)
})
