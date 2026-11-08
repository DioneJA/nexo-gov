import { expect, test } from '@playwright/test'
import { attachEvidenceScreenshot } from './evidence-fixtures'

test('caixa mostra leitura por setor, por outro servidor e pelo usuário atual', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 960 })
  const suffix = Date.now()
  const protocolSubject = 'Solicitação geral'

  await page.goto('/')
  await page.getByRole('combobox', { name: 'Organização destinatária *' }).click()
  await page.getByRole('option', { name: 'Município Alfa (demonstração)', exact: true }).click()
  await page.getByLabel('Nome completo *').fill(`Pessoa de Teste Leitura ${suffix}`)
  await page.getByLabel('E-mail *').fill(`leitura-${suffix}@example.test`)
  await page.getByRole('combobox', { name: 'Assunto *', exact: true }).click()
  await page.getByRole('option', { name: protocolSubject, exact: true }).click()
  await page.getByLabel('Descreva sua solicitação *').fill('Peço análise de um serviço público fictício.')
  const createResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/public/protocols')
  )
  await page.getByRole('button', { name: 'Registrar solicitação' }).click()
  await expect(page.getByText(/Protocolo .* registrado/)).toBeVisible()
  const created = await (await createResponsePromise).json() as { id: string; protocolNumber: string }

  async function signInStaff(staffPage: typeof page, username: string) {
    await staffPage.goto('/servidor/')
    await staffPage.getByRole('combobox', { name: 'Organização' }).click()
    await staffPage.getByRole('option', { name: 'Município Alfa (demonstração)', exact: true }).click()
    await staffPage.getByLabel('Usuário').fill(username)
    const password = username === 'servidor' ? 'e2e-local-only-password' : 'e2e-local-only-member-password'
    await staffPage.getByLabel('Senha').fill(password)
    await staffPage.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
    await expect(staffPage.getByRole('heading', { name: 'Protocolos', exact: true })).toBeVisible()
  }

  const primaryStaffPage = await page.context().newPage()
  const primaryReadRequests: string[] = []
  primaryStaffPage.on('request', request => {
    if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith(`/staff/protocols/${created.id}/read`))
      primaryReadRequests.push(request.url())
  })
  await signInStaff(primaryStaffPage, 'servidor')
  const primaryProtocol = primaryStaffPage.getByRole('article').filter({ hasText: created.protocolNumber })
  await expect(primaryProtocol).toHaveCount(1)
  const primaryReadStatus = primaryProtocol.locator('.protocol-read-indicator')
  await expect(primaryReadStatus).toHaveText('Leitura: Ainda não lido por ninguém do setor')
  expect(primaryReadRequests).toHaveLength(0)
  await attachEvidenceScreenshot(primaryStaffPage, testInfo, 'leitura-nao-iniciada', primaryProtocol)

  const otherStaffPage = await page.context().newPage()
  const otherReadRequests: string[] = []
  otherStaffPage.on('request', request => {
    if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith(`/staff/protocols/${created.id}/read`))
      otherReadRequests.push(request.url())
  })
  await signInStaff(otherStaffPage, 'servidor.teste')
  const otherProtocol = otherStaffPage.getByRole('article').filter({ hasText: created.protocolNumber })
  await expect(otherProtocol).toHaveCount(1)
  await expect(otherProtocol.locator('.protocol-read-indicator')).toHaveText('Leitura: Ainda não lido por ninguém do setor')
  expect(primaryReadRequests).toHaveLength(0)
  expect(otherReadRequests).toHaveLength(0)

  const otherReadResponsePromise = otherStaffPage.waitForResponse(response =>
    response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith(`/staff/protocols/${created.id}/read`)
  )
  await otherProtocol.getByRole('button', { name: `Ver mensagens do protocolo ${created.protocolNumber}` }).click()
  const otherReadResponse = await otherReadResponsePromise
  expect(otherReadResponse.status()).toBe(200)
  expect(await otherReadResponse.json()).toMatchObject({ protocolId: created.id, readStatus: 'READ_BY_ME' })
  expect(otherReadRequests).toHaveLength(1)
  await expect(otherProtocol.locator('.protocol-read-indicator')).toHaveText('Leitura: Você já leu')

  await primaryStaffPage.getByRole('button', { name: 'Atualizar', exact: true }).click()
  expect(primaryReadRequests).toHaveLength(0)
  await expect(primaryProtocol.locator('.protocol-read-indicator')).toHaveText(
    'Leitura: Outro servidor habilitado do setor já leu; você ainda não'
  )
  await attachEvidenceScreenshot(primaryStaffPage, testInfo, 'leitura-por-colega', primaryProtocol)

  const currentReadResponsePromise = primaryStaffPage.waitForResponse(response =>
    response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith(`/staff/protocols/${created.id}/read`)
  )
  await primaryProtocol.getByRole('button', { name: `Ver mensagens do protocolo ${created.protocolNumber}` }).click()
  const currentReadResponse = await currentReadResponsePromise
  expect(currentReadResponse.status()).toBe(200)
  expect(await currentReadResponse.json()).toMatchObject({ protocolId: created.id, readStatus: 'READ_BY_ME' })
  expect(primaryReadRequests).toHaveLength(1)
  await expect(primaryProtocol.locator('.protocol-read-indicator')).toHaveText('Leitura: Você já leu')
  await attachEvidenceScreenshot(primaryStaffPage, testInfo, 'leitura-pelo-servidor', primaryProtocol)

  await primaryStaffPage.reload()
  await signInStaff(primaryStaffPage, 'servidor')
  const reloadedProtocol = primaryStaffPage.getByRole('article').filter({ hasText: created.protocolNumber })
  await expect(reloadedProtocol.locator('.protocol-read-indicator')).toHaveText('Leitura: Você já leu')
  await Promise.all([otherStaffPage.close(), primaryStaffPage.close()])
})
