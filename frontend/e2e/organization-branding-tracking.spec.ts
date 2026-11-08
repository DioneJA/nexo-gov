import { expect, test } from '@playwright/test'

interface OrganizationChoice { id: string; slug: string; name: string }

test('cidadão consulta protocolo de outra organização sem atribuir a identidade selecionada', async ({ page }) => {
  const organizationsResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'GET' && response.url().endsWith('/api/v1/public/organizations'))
  await page.goto('/')
  const organizations = await (await organizationsResponsePromise).json() as OrganizationChoice[]
  const alpha = organizations.find(organization => organization.name.includes('Município Alfa'))
  const beta = organizations.find(organization => organization.name.includes('Município Beta'))
  expect(alpha).toBeDefined()
  expect(beta).toBeDefined()

  const organizationSelector = page.getByRole('combobox', { name: 'Organização destinatária *' })
  await organizationSelector.click()
  await page.getByRole('option', { name: beta!.name, exact: true }).click()
  await page.getByLabel('Nome completo *').fill('Pessoa Beta E2E')
  await page.getByLabel('E-mail *').fill('pessoa.beta@example.test')
  await page.getByRole('combobox', { name: 'Assunto *', exact: true }).click()
  await page.getByRole('option', { name: 'Solicitação geral', exact: true }).click()
  await page.getByLabel('Descreva sua solicitação *').fill('Solicitação fictícia da organização Beta.')

  const creationResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/api/v1/public/protocols'))
  await page.getByRole('button', { name: 'Registrar solicitação' }).click()
  const creationResponse = await creationResponsePromise
  expect(creationResponse.status()).toBe(200)
  const createdProtocol = await creationResponse.json() as { trackingToken: string }
  expect(createdProtocol.trackingToken.length).toBeGreaterThan(40)
  await expect(page.getByText(/Protocolo .* registrado/)).toBeVisible()

  await page.getByLabel('Chave do comprovante').fill('')
  await page.getByRole('tab', { name: 'Nova solicitação' }).click()
  await organizationSelector.click()
  await page.getByRole('option', { name: alpha!.name, exact: true }).click()
  await expect(page.locator('.brand-caption')).toContainText(alpha!.name)

  await page.getByRole('tab', { name: 'Acompanhar protocolo' }).click()
  await expect(page.locator('.brand-caption')).toHaveText('Gestão pública digital')
  await page.getByLabel('Chave do comprovante').fill(createdProtocol.trackingToken)
  const trackingResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'GET' && new URL(response.url()).pathname.endsWith('/api/v1/public/protocols/tracking'))
  await page.getByRole('button', { name: 'Consultar' }).click()
  const trackingResponse = await trackingResponsePromise
  expect(trackingResponse.status()).toBe(200)
  await expect(page.locator('.tracking-result')).toContainText('Solicitação geral')
  await expect(page.locator('.brand-caption')).toHaveText('Gestão pública digital')
})
