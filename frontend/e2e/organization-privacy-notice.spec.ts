import { expect, test } from '@playwright/test'
import { attachEvidenceScreenshot } from './evidence-fixtures'

interface OrganizationChoice { id: string; slug: string; name: string }

test.use({ viewport: { width: 1440, height: 1000 } })

test('administrador configura aviso municipal e cidadão vê o texto e a política antes do formulário', async ({ page }, testInfo) => {
  let selectedOrganizationId = ''
  page.on('request', request => {
    if (request.method() === 'GET' && request.url().endsWith('/api/v1/staff/organization/privacy-notice')) {
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

  const adminNotice = page.getByRole('region', { name: 'Aviso de privacidade' })
  await expect(adminNotice).toBeVisible()
  await expect(adminNotice.getByLabel('Texto explicativo')).toHaveValue('')
  const selectedOrganization = organizations.find(organization => organization.id === selectedOrganizationId)
  expect(selectedOrganization).toBeDefined()

  const explanatoryText = 'Usaremos os dados informados para analisar e responder a esta solicitação.\nConsulte a política da Administração para conhecer seus direitos.'
  const policyUrl = 'https://www.municipio.example/privacidade'
  await adminNotice.getByLabel('Texto explicativo').fill(explanatoryText)
  await adminNotice.getByLabel('Endereço da Política de Privacidade').fill(policyUrl)
  await expect(adminNotice.locator('.privacy-notice-preview')).toContainText(explanatoryText)
  await expect(adminNotice.locator('.privacy-notice-preview')).toContainText('Política de Privacidade')
  await adminNotice.getByRole('button', { name: 'Publicar aviso' }).click()
  await expect(adminNotice.getByRole('status')).toHaveText('Aviso de privacidade publicado para esta organização.')
  await attachEvidenceScreenshot(page, testInfo, 'privacy-notice-admin-configured', adminNotice.locator('.privacy-notice-preview'))

  await page.reload()
  await page.getByLabel('Usuário').fill('servidor')
  await page.getByLabel('Senha').fill('e2e-local-only-password')
  await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
  const reloadedNotice = page.getByRole('region', { name: 'Aviso de privacidade' })
  await expect(reloadedNotice.getByLabel('Texto explicativo')).toHaveValue(explanatoryText)
  await expect(reloadedNotice.getByLabel('Endereço da Política de Privacidade')).toHaveValue(policyUrl)

  await page.goto('/')
  const organizationSelector = page.getByRole('combobox', { name: 'Organização destinatária *' })
  await organizationSelector.click()
  const organizationOption = page.getByRole('option', { name: selectedOrganization!.name, exact: true })
  await organizationOption.click()
  await expect(organizationOption).toBeHidden()
  const publicNotice = page.getByRole('region', { name: 'Privacidade' })
  await expect(publicNotice).toContainText(explanatoryText)
  const policyLink = publicNotice.getByRole('link', { name: 'Política de Privacidade' })
  await expect(policyLink).toHaveAttribute('href', policyUrl)
  await expect(policyLink).toHaveAttribute('target', '_blank')
  await expect(policyLink).toHaveAttribute('rel', 'noopener noreferrer')
  await expect(publicNotice).toBeVisible()
  await attachEvidenceScreenshot(page, testInfo, 'privacy-notice-public-displayed', publicNotice)

  const otherOrganization = organizations.find(organization => organization.id !== selectedOrganizationId)
  expect(otherOrganization).toBeDefined()
  await organizationSelector.click()
  const otherOrganizationOption = page.getByRole('option', { name: otherOrganization!.name, exact: true })
  await otherOrganizationOption.click()
  await expect(otherOrganizationOption).toBeHidden()
  await expect(publicNotice).toBeHidden()

  const applicantName = page.getByLabel('Nome completo *')
  const selectedPrivacyPath = `/api/v1/public/organizations/${selectedOrganization!.slug}/privacy-notice`
  let releaseSelectedPrivacy!: () => void
  let markSelectedPrivacyStarted!: () => void
  const selectedPrivacyGate = new Promise<void>(resolve => { releaseSelectedPrivacy = resolve })
  const selectedPrivacyStarted = new Promise<void>(resolve => { markSelectedPrivacyStarted = resolve })
  const privacyRoutePattern = '**/api/v1/public/organizations/*/privacy-notice'
  await page.route(privacyRoutePattern, async route => {
    if (new URL(route.request().url()).pathname === selectedPrivacyPath) {
      markSelectedPrivacyStarted()
      await selectedPrivacyGate
    }
    await route.continue()
  })
  try {
    await organizationSelector.click()
    await page.getByRole('option', { name: selectedOrganization!.name, exact: true }).click()
    await selectedPrivacyStarted
    await expect(applicantName).toBeDisabled()
    await expect(publicNotice).toBeVisible()
    await expect(publicNotice).toHaveAccessibleName('Privacidade')

    await organizationSelector.click()
    await page.getByRole('option', { name: otherOrganization!.name, exact: true }).click()
    await expect(applicantName).toBeEnabled()
    await expect(publicNotice).toBeHidden()

    const lateSelectedPrivacyResponse = page.waitForResponse(response =>
      response.request().method() === 'GET' && new URL(response.url()).pathname === selectedPrivacyPath)
    releaseSelectedPrivacy()
    await lateSelectedPrivacyResponse
    await expect(publicNotice).toBeHidden()
  } finally {
    releaseSelectedPrivacy()
    await page.unroute(privacyRoutePattern)
  }

  let failOtherOrganizationOnce = true
  await page.route(privacyRoutePattern, async route => {
    const path = new URL(route.request().url()).pathname
    if (path === `/api/v1/public/organizations/${otherOrganization!.slug}/privacy-notice` && failOtherOrganizationOnce) {
      failOtherOrganizationOnce = false
      await route.abort()
      return
    }
    await route.continue()
  })
  await organizationSelector.click()
  await page.getByRole('option', { name: selectedOrganization!.name, exact: true }).click()
  await expect(publicNotice).toContainText(explanatoryText)
  await organizationSelector.click()
  await page.getByRole('option', { name: otherOrganization!.name, exact: true }).click()
  await expect(publicNotice).toBeVisible()
  await expect(publicNotice).toHaveAccessibleName('Privacidade')
  await expect(publicNotice.getByRole('alert').filter({ hasText: 'Não foi possível carregar o aviso de privacidade desta organização.' })).toBeVisible()
  await expect(applicantName).toBeDisabled()
  const retryNotice = page.getByRole('button', { name: 'Tentar carregar o aviso novamente' })
  await expect(retryNotice).toBeVisible()
  await retryNotice.click()
  await expect(applicantName).toBeEnabled()
  await expect(page.locator('.public-privacy-notice')).toBeHidden()
  await page.unroute(privacyRoutePattern)
})
