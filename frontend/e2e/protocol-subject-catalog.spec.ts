import { readFile } from 'node:fs/promises'
import { dirname, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { getDocument } from 'pdfjs-dist/legacy/build/pdf.mjs'
import { expect, test } from '@playwright/test'
import { attachEvidenceScreenshot } from './evidence-fixtures'

const standardFontDataUrl = `${resolve(dirname(fileURLToPath(import.meta.url)), '../node_modules/pdfjs-dist/standard_fonts')}${sep}`

test('administrador configura o prazo automático no comprovante real do protocolo', async ({ page }, testInfo) => {
  const suffix = Date.now()
  const onlineSubject = `Consulta de iluminação ${suffix}`
  const counterSubject = `Atendimento presencial ${suffix}`
  const destinationDepartment = 'Secretaria de Obras'

  await page.goto('/servidor/')
  await page.getByLabel('Usuário').fill('servidor')
  await page.getByLabel('Senha').fill('e2e-local-only-password')
  await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()

  const catalog = page.getByRole('region', { name: 'Assuntos de protocolo' })
  await expect(catalog).toBeVisible()
  await expect(catalog.getByText('Carregando assuntos…')).toHaveCount(0)
  await expect(catalog.getByText('Carregando setores ativos…')).toHaveCount(0)

  await catalog.getByLabel('Nome do assunto').fill(onlineSubject)
  await catalog.getByRole('combobox', { name: 'Disponibilidade de atendimento', exact: true }).first().click()
  await page.getByRole('option', { name: 'Central de Atendimento', exact: true }).click()
  await catalog.getByRole('combobox', { name: 'Setor destinatário para solicitações online' }).click()
  await page.getByRole('option', { name: 'Protocolo Geral', exact: true }).click()
  await catalog.getByRole('spinbutton', { name: 'Dias de prazo no comprovante (opcional)' }).fill('8')
  await catalog.getByRole('button', { name: 'Adicionar assunto' }).click()
  await expect(catalog.getByRole('status').filter({ hasText: `Assunto “${onlineSubject}” disponível online; as solicitações seguem para Protocolo Geral.` })).toBeVisible()

  const subjectRow = catalog.getByRole('list', { name: 'Assuntos de protocolo cadastrados' })
    .getByRole('listitem').filter({ hasText: onlineSubject })
  const deadlineField = subjectRow.getByRole('spinbutton', { name: `Dias de prazo no comprovante: ${onlineSubject}` })
  await expect(deadlineField).toHaveValue('8')
  await deadlineField.fill('14')
  await subjectRow.getByRole('combobox', { name: `Setor destinatário: ${onlineSubject}` }).click()
  await page.getByRole('option', { name: destinationDepartment, exact: true }).click()
  await subjectRow.getByRole('button', { name: `Salvar configuração do assunto ${onlineSubject}` }).click()
  await expect(deadlineField).toHaveValue('14')
  await expect(catalog.getByRole('status').filter({ hasText: `Assunto “${onlineSubject}” disponível online; as solicitações seguem para ${destinationDepartment}.` })).toBeVisible()

  await catalog.getByLabel('Nome do assunto').fill(counterSubject)
  await catalog.getByRole('combobox', { name: 'Disponibilidade de atendimento', exact: true }).first().click()
  await page.getByRole('option', { name: 'Somente balcão interno', exact: true }).click()
  await catalog.getByRole('button', { name: 'Adicionar assunto' }).click()
  await expect(catalog.getByRole('status').filter({ hasText: `Assunto “${counterSubject}” configurado para atendimento somente no balcão.` })).toBeVisible()

  // A fresh page load confirms the edited value is returned by the persisted catalog API.
  await page.reload()
  await page.getByLabel('Usuário').fill('servidor')
  await page.getByLabel('Senha').fill('e2e-local-only-password')
  await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
  const refreshedCatalog = page.getByRole('region', { name: 'Assuntos de protocolo' })
  await expect(refreshedCatalog).toBeVisible()
  await expect(refreshedCatalog.getByText('Carregando assuntos…')).toHaveCount(0)
  const persistedSubjectRow = refreshedCatalog.getByRole('list', { name: 'Assuntos de protocolo cadastrados' })
    .getByRole('listitem').filter({ hasText: onlineSubject })
  await expect(persistedSubjectRow.getByRole('spinbutton', { name: `Dias de prazo no comprovante: ${onlineSubject}` })).toHaveValue('14')
  await attachEvidenceScreenshot(page, testInfo, 'configuracao-persistida', persistedSubjectRow)

  const citizenPage = await page.context().newPage()
  await citizenPage.goto('/')
  const subjectSelect = citizenPage.getByRole('combobox', { name: 'Assunto *', exact: true })
  await subjectSelect.click()
  await expect(citizenPage.getByRole('option', { name: onlineSubject, exact: true })).toBeVisible()
  await expect(citizenPage.getByRole('option', { name: counterSubject, exact: true })).toHaveCount(0)
  await citizenPage.getByRole('option', { name: onlineSubject, exact: true }).click()
  await citizenPage.getByLabel('Nome completo *').fill('Pessoa de Teste E2E')
  await citizenPage.getByLabel('E-mail *').fill('cidadao-e2e@example.test')
  await citizenPage.getByLabel('Descreva sua solicitação *').fill('Solicitação fictícia encaminhada ao setor configurado.')
  await citizenPage.getByRole('button', { name: 'Registrar solicitação' }).click()
  await expect(citizenPage.getByText(/Protocolo .* registrado/)).toBeVisible()
  await expect(citizenPage.locator('.protocol-subject').getByText(onlineSubject, { exact: true })).toBeVisible()
  await attachEvidenceScreenshot(citizenPage, testInfo, 'confirmacao-cidadao', citizenPage.locator('.protocol-subject'))

  const receiptReady = citizenPage.waitForEvent('download')
  await citizenPage.getByRole('button', { name: 'Baixar comprovante' }).click()
  const receipt = await receiptReady
  expect(receipt.suggestedFilename()).toBe('comprovante-protocolo.pdf')
  const receiptPath = await receipt.path()
  expect(receiptPath).toBeTruthy()
  const pdf = await getDocument({
    data: new Uint8Array(await readFile(receiptPath!)),
    standardFontDataUrl
  }).promise
  let receiptText = ''
  for (let pageNumber = 1; pageNumber <= pdf.numPages; pageNumber++) {
    const receiptPage = await pdf.getPage(pageNumber)
    const pageText = await receiptPage.getTextContent()
    receiptText += pageText.items.map(item => 'str' in item ? item.str : '').join(' ')
  }
  await pdf.destroy()
  expect(receiptText).toContain(`Setor destinatário: ${destinationDepartment}`)
  expect(receiptText).toContain('Prazo configurado: 14 dias')
})
