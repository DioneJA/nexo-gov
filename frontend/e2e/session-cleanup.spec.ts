import { expect, test } from '@playwright/test'

test('cidadão consulta com token de fragmento e a barra de endereço fica limpa', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Nome completo *').fill(`Pessoa de Teste ${Date.now()}`)
  await page.getByLabel('E-mail *').fill('cidadao-fragmento-e2e@example.test')
  await page.getByRole('combobox', { name: 'Assunto *', exact: true }).click()
  await page.getByRole('option', { name: 'Solicitação geral', exact: true }).click()
  await page.getByLabel('Descreva sua solicitação *').fill('Pedido fictício para validar a consulta segura por token.')
  await page.getByRole('button', { name: 'Registrar solicitação' }).click()

  await expect(page.getByText(/Protocolo .* registrado/)).toBeVisible()
  const token = await page.getByLabel('Chave do comprovante').inputValue()
  expect(token.length).toBeGreaterThan(40)

  await page.goto(`/?origem=recibo#token=${encodeURIComponent(token)}`)
  await expect(page.getByLabel('Chave do comprovante')).toHaveValue(token)
  await expect(page.getByText(/\d{4}-\d{6}/)).toBeVisible()
  await expect.poll(() => new URL(page.url()).hash).toBe('')
  expect(new URL(page.url()).search).toBe('?origem=recibo')

  // Preserve links already printed in receipts while removing their bearer token.
  await page.goto(`/#/acompanhar?token=${encodeURIComponent(token)}`)
  await expect(page.getByLabel('Chave do comprovante')).toHaveValue(token)
  await expect.poll(() => new URL(page.url()).hash).toBe('')
})

test('sair limpa o contexto do servidor e ignora uma resposta real que estava em voo', async ({ page }) => {
  let continueMemberRequest!: () => void
  const memberRequestGate = new Promise<void>(resolve => { continueMemberRequest = resolve })
  await page.route('**/api/v1/staff/organization/members', async route => {
    await memberRequestGate
    await route.continue()
  })

  const protocolMutations: string[] = []
  page.on('request', request => {
    const path = new URL(request.url()).pathname
    if (request.method() === 'POST' && /\/staff\/protocols\/[^/]+\/(forward|receive|dispatch|complete)$/.test(path)) {
      protocolMutations.push(path)
    }
  })
  await page.goto('/servidor/')
  await page.getByLabel('Usuário').fill('servidor')
  await page.getByLabel('Senha').fill('e2e-local-only-password')
  await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()

  const logout = page.getByRole('button', { name: 'Sair da área do servidor' })
  await expect(logout).toBeVisible()
  await expect(page.getByRole('status').filter({ hasText: 'Carregando membros' })).toBeVisible()
  const memberResponse = page.waitForResponse(response =>
    new URL(response.url()).pathname.endsWith('/api/v1/staff/organization/members')
  )

  await logout.click()
  await expect(page.getByLabel('Usuário')).toHaveValue('')
  await expect(page.getByLabel('Senha')).toHaveValue('')
  await expect(page.locator('.staff-access .q-select__selection')).toHaveCount(0)
  await expect(page.locator('.staff-workspace')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Entrar e carregar caixa' })).toBeVisible()

  // The route is only paused above; after logout, the response still comes from the real API.
  continueMemberRequest()
  expect((await memberResponse).status()).toBe(200)
  await expect(page.locator('.staff-workspace')).toHaveCount(0)
  await expect(page.getByLabel('Usuário')).toHaveValue('')
  expect(protocolMutations).toEqual([])
  await page.unroute('**/api/v1/staff/organization/members')
})
