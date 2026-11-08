import { expect, test } from '@playwright/test'
import { attachEvidenceScreenshot } from './evidence-fixtures'

interface OrganizationChoice { id: string; slug: string; name: string }

test.use({ viewport: { width: 1440, height: 1000 } })

test('administrador publica identidade visual e a troca de organização descarta respostas antigas', async ({ page }, testInfo) => {
  let selectedOrganizationId = ''
  page.on('request', request => {
    if (request.method() === 'GET' && request.url().endsWith('/api/v1/staff/organization/branding')) {
      selectedOrganizationId = request.headers()['x-organization-id'] || ''
    }
  })

  const organizationsResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'GET' && response.url().endsWith('/api/v1/public/organizations'))
  await page.goto('/servidor/')
  const organizations = await (await organizationsResponsePromise).json() as OrganizationChoice[]
  await page.getByLabel('Usuário').fill('servidor')
  await page.getByLabel('Senha').fill('e2e-local-only-password')
  await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()

  const settings = page.getByRole('region', { name: 'Identidade da organização' })
  await expect(settings).toBeVisible()
  await expect(settings.getByLabel('Nome público alternativo')).toHaveValue('')
  await expect(page.locator('.topbar .portal-link')).toHaveCSS('color', 'rgb(0, 81, 213)')
  const alpha = organizations.find(organization => organization.id === selectedOrganizationId)
  expect(alpha).toBeDefined()
  const beta = organizations.find(organization => organization.id !== selectedOrganizationId)
  expect(beta).toBeDefined()

  await settings.getByLabel('Nome público alternativo').fill('Prefeitura Alfa E2E')
  await settings.getByLabel('Cor de destaque').click()
  await page.getByRole('option', { name: 'Verde de confirmação' }).click()
  await expect(settings.locator('.branding-preview')).toHaveClass(/branding-accent-SUCCESS_GREEN/)
  await expect(settings.locator('.branding-preview-action')).toHaveRole('button')
  await expect(settings.locator('.branding-preview-action')).toBeDisabled()
  await expect(settings.locator('.branding-preview-action')).toHaveCSS('background-color', 'rgb(21, 128, 61)')
  const publishButton = settings.getByRole('button', { name: 'Publicar identidade visual' })
  await expect(publishButton).not.toHaveCSS('background-color', 'rgb(21, 128, 61)')
  await attachEvidenceScreenshot(page, testInfo, 'identidade-visual-previa', settings.locator('.branding-preview'))
  await publishButton.click()
  await expect(settings.getByRole('status')).toHaveText('Identidade visual publicada para esta organização.')
  await expect(page.locator('.staff-app')).toHaveClass(/branding-accent-SUCCESS_GREEN/)
  await attachEvidenceScreenshot(page, testInfo, 'identidade-publicada-servidor', page.locator('.topbar-inner'))

  await page.reload()
  await page.getByLabel('Usuário').fill('servidor')
  await page.getByLabel('Senha').fill('e2e-local-only-password')
  await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
  const reloadedSettings = page.getByRole('region', { name: 'Identidade da organização' })
  await expect(reloadedSettings.getByLabel('Nome público alternativo')).toHaveValue('Prefeitura Alfa E2E')
  await expect(page.locator('.staff-app')).toHaveClass(/branding-accent-SUCCESS_GREEN/)

  const longOrganizationName = 'Prefeitura municipal '.repeat(10).slice(0, 160)
  await reloadedSettings.getByLabel('Nome público alternativo').fill(longOrganizationName)
  await reloadedSettings.getByRole('button', { name: 'Publicar identidade visual' }).click()
  await expect(reloadedSettings.getByRole('status')).toHaveText('Identidade visual publicada para esta organização.')
  await expect(page.locator('.brand-caption')).toHaveText(longOrganizationName)
  for (const width of [390, 320]) {
    await page.setViewportSize({ width, height: 844 })
    const headerBounds = await page.locator('.brand-caption').evaluate(element => ({
      viewportWidth: window.innerWidth,
      documentWidth: document.documentElement.scrollWidth,
      captionRight: element.getBoundingClientRect().right,
      clampedLines: getComputedStyle(element).webkitLineClamp
    }))
    expect(headerBounds.documentWidth).toBeLessThanOrEqual(headerBounds.viewportWidth)
    expect(headerBounds.captionRight).toBeLessThanOrEqual(headerBounds.viewportWidth)
    expect(headerBounds.clampedLines).toBe('2')
  }
  await reloadedSettings.getByLabel('Nome público alternativo').fill('Prefeitura Alfa E2E')
  await reloadedSettings.getByRole('button', { name: 'Publicar identidade visual' }).click()
  await expect(reloadedSettings.getByRole('status')).toHaveText('Identidade visual publicada para esta organização.')
  await page.setViewportSize({ width: 1440, height: 1000 })
  await expect(page.locator('.brand-caption')).toHaveText('Prefeitura Alfa E2E')

  let releaseAlphaRequest!: () => void
  const alphaGate = new Promise<void>(resolve => { releaseAlphaRequest = resolve })
  let signalAlphaRequest!: () => void
  const alphaRequestStarted = new Promise<void>(resolve => { signalAlphaRequest = resolve })
  const alphaBrandingUrl = `**/api/v1/public/organizations/${alpha!.slug}/branding`
  await page.route(alphaBrandingUrl, async route => {
    signalAlphaRequest()
    await alphaGate
    await route.continue()
  })

  await page.goto('/')
  await alphaRequestStarted
  const organizationSelector = page.getByLabel('Organização destinatária *')
  await organizationSelector.click()
  await page.getByRole('option', { name: beta!.name, exact: true }).click()
  await expect(page.locator('.brand-caption')).toContainText(beta!.name)
  await expect(page.locator('.app-shell')).toHaveClass(/branding-accent-ACTION_BLUE/)

  const delayedAlphaResponse = page.waitForResponse(response => response.url().endsWith(
    `/api/v1/public/organizations/${alpha!.slug}/branding`))
  releaseAlphaRequest()
  await delayedAlphaResponse
  await expect(page.locator('.brand-caption')).toContainText(beta!.name)
  await expect(page.locator('.app-shell')).toHaveClass(/branding-accent-ACTION_BLUE/)
  await page.unroute(alphaBrandingUrl)

  await organizationSelector.click()
  await page.getByRole('option', { name: alpha!.name, exact: true }).click()
  await expect(page.locator('.brand-caption')).toContainText('Prefeitura Alfa E2E')
  await expect(page.locator('.app-shell')).toHaveClass(/branding-accent-SUCCESS_GREEN/)
  await attachEvidenceScreenshot(page, testInfo, 'identidade-publicada-cidadao', page.locator('.topbar-inner'))

  await page.goto('/servidor/')
  await page.getByLabel('Usuário').fill('servidor')
  await page.getByLabel('Senha').fill('e2e-local-only-password')
  await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
  const resetSettings = page.getByRole('region', { name: 'Identidade da organização' })
  await resetSettings.getByRole('button', { name: 'Restaurar padrão' }).click()
  await expect(resetSettings.locator('.branding-preview')).toHaveClass(/branding-accent-ACTION_BLUE/)
  await resetSettings.getByRole('button', { name: 'Publicar identidade visual' }).click()
  await expect(resetSettings.getByRole('status')).toHaveText('Identidade visual publicada para esta organização.')
  await expect(page.locator('.staff-app')).toHaveClass(/branding-accent-ACTION_BLUE/)
})
