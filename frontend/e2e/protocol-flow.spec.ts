import { expect, test } from '@playwright/test'

test('cidadão abre protocolo, servidor tramita e cidadão acompanha até a conclusão', async ({ page }) => {
  const subject = `Solicitação E2E ${Date.now()}`
  await page.goto('/')

  await page.getByLabel('Nome completo *').fill('Pessoa de Teste E2E')
  await page.getByLabel('E-mail *').fill('cidadao-e2e@example.test')
  await page.getByLabel('Assunto *').fill(subject)
  await page.getByLabel('Descreva sua solicitação *').fill('Solicito análise de uma via pública fictícia.')
  await page.getByRole('button', { name: 'Registrar solicitação' }).click()

  await expect(page.getByText(new RegExp(`Protocolo .* aberto`))).toBeVisible()
  const token = await page.getByLabel('Token de consulta').inputValue()
  expect(token.length).toBeGreaterThan(40)

  const downloadReady = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Baixar comprovante' }).click()
  const receipt = await downloadReady
  expect(receipt.suggestedFilename()).toBe('comprovante-protocolo.pdf')

  await page.getByRole('tab', { name: 'Área do servidor' }).click()
  await page.getByLabel('Senha local de demonstração').fill('e2e-local-only-password')
  await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
  await expect(page.getByText(subject)).toBeVisible()

  await page.getByRole('button', { name: 'Encaminhar' }).click()
  await page.getByRole('button', { name: 'Receber' }).click()
  await page.getByLabel('Próximo setor').click()
  await page.getByRole('option', { name: 'Secretaria de Obras' }).click()
  await page.getByRole('button', { name: 'Despachar' }).click()
  await page.getByRole('button', { name: 'Receber' }).click()
  await page.getByRole('button', { name: 'Concluir' }).click()

  await page.getByRole('tab', { name: 'Acompanhar' }).click()
  await page.getByLabel('Token de consulta').fill(token)
  await page.getByRole('button', { name: 'Consultar' }).click()
  await expect(page.getByText('Concluído', { exact: true })).toBeVisible()
  await expect(page.getByText(subject)).toBeVisible()
})
