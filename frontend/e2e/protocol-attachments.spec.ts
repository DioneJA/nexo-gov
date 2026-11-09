import { expect, test } from '@playwright/test'
import { attachEvidenceScreenshot } from './evidence-fixtures'

test('assunto exige documento, cidadão envia e servidor consulta e baixa o anexo', async ({ page }, testInfo) => {
  const suffix = Date.now()
  const subject = `Análise de iluminação ${suffix}`
  const requirementName = `Comprovante técnico ${suffix}`
  const filename = `comprovante-e2e-${suffix}.pdf`

  await page.goto('/servidor/')
  await page.getByLabel('Usuário').fill('servidor')
  await page.getByLabel('Senha').fill('e2e-local-only-password')
  await page.getByRole('combobox', { name: 'Organização' }).click()
  await page.getByRole('option', { name: 'Município Alfa (demonstração)', exact: true }).click()
  await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()

  const catalog = page.getByRole('region', { name: 'Assuntos de protocolo' })
  await expect(catalog).toBeVisible()
  await expect(catalog.getByText('Carregando assuntos…')).toHaveCount(0)
  await expect(catalog.getByText('Carregando setores ativos…')).toHaveCount(0)

  await catalog.getByLabel('Nome do assunto').fill(subject)
  await catalog.getByRole('combobox', { name: 'Disponibilidade de atendimento', exact: true }).first().click()
  await page.getByRole('option', { name: 'Central de Atendimento', exact: true }).click()
  await catalog.getByRole('combobox', { name: 'Setor destinatário para solicitações online' }).click()
  await page.getByRole('option', { name: 'Protocolo Geral', exact: true }).click()
  await catalog.getByRole('button', { name: 'Adicionar assunto' }).click()
  await expect(catalog.getByRole('status').filter({ hasText: `Assunto “${subject}” disponível online; as solicitações seguem para Protocolo Geral.` })).toBeVisible()

  const subjectRow = catalog.getByRole('list', { name: 'Assuntos de protocolo cadastrados' })
    .getByRole('listitem').filter({ hasText: subject })
  const requiredDocument = subjectRow.getByText('Obrigatório para novos pedidos', { exact: true })
  await subjectRow.getByLabel('Nome do documento').fill(requirementName)
  await requiredDocument.click()
  await subjectRow.getByRole('button', { name: 'Adicionar documento' }).click()
  await expect(catalog.getByRole('status').filter({ hasText: `Documento “${requirementName}” configurado para ${subject}.` })).toBeVisible()
  await attachEvidenceScreenshot(page, testInfo, 'regra-anexo-persistida', subjectRow)

  const citizenPage = await page.context().newPage()
  await citizenPage.goto('/')
  let protocolCreateRequests = 0
  citizenPage.on('request', request => {
    if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/public/protocols')) protocolCreateRequests++
  })
  await citizenPage.getByRole('combobox', { name: 'Organização destinatária *' }).click()
  await citizenPage.getByRole('option', { name: 'Município Alfa (demonstração)', exact: true }).click()
  const subjectSelect = citizenPage.getByRole('combobox', { name: 'Assunto *', exact: true })
  await subjectSelect.click()
  await citizenPage.getByRole('option', { name: subject, exact: true }).click()
  await citizenPage.getByLabel('Nome completo *').fill('Pessoa de Teste E2E')
  await citizenPage.getByLabel('E-mail *').fill('cidadao-anexos-e2e@example.test')
  await citizenPage.getByLabel('Descreva sua solicitação *').fill('Solicitação fictícia com documentação técnica anexada.')

  await citizenPage.getByRole('button', { name: 'Registrar solicitação' }).click()
  await expect(citizenPage.getByText(`Anexe ${requirementName}.`)).toBeVisible()
  expect(protocolCreateRequests).toBe(0)

  await citizenPage.getByLabel(`${requirementName} *`).setInputFiles({
    name: filename,
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.7\nDocumento E2E fictício.\n')
  })
  await citizenPage.getByRole('button', { name: 'Registrar solicitação' }).click()
  const confirmation = citizenPage.getByText(/Protocolo .* registrado/)
  await expect(confirmation).toBeVisible()
  expect(protocolCreateRequests).toBe(1)
  const protocolNumber = (await confirmation.textContent())?.match(/Protocolo (\d{4}-\d{6}) registrado/)?.[1]
  expect(protocolNumber).toBeTruthy()

  const citizenAttachments = citizenPage.getByRole('list', { name: 'Documentos anexados ao protocolo' })
  await expect(citizenAttachments.getByText(filename)).toBeVisible()
  // The helper converts the receipt credential field to password and masks it
  // before taking a viewport-only capture. The UI does not render the QR code.
  await attachEvidenceScreenshot(citizenPage, testInfo, 'anexo-cidadao-enviado-listado', citizenAttachments)
  const citizenDownloadReady = citizenPage.waitForEvent('download')
  await citizenPage.getByRole('button', { name: `Baixar ${filename}` }).click()
  expect((await citizenDownloadReady).suggestedFilename()).toBe(filename)

  await page.getByRole('button', { name: 'Atualizar', exact: true }).click()
  const assignedProtocol = page.getByRole('article').filter({ hasText: protocolNumber! })
  await expect(assignedProtocol).toHaveCount(1)
  await assignedProtocol.getByRole('button', { name: 'Anexos da abertura (1)' }).click()
  const attachmentPanel = page.getByRole('region', { name: `Documentos do protocolo ${protocolNumber}` })
  await expect(attachmentPanel.getByText(filename)).toBeVisible()
  const staffDownloadReady = page.waitForEvent('download')
  await attachmentPanel.getByRole('button', { name: `Baixar ${filename}` }).click()
  expect((await staffDownloadReady).suggestedFilename()).toBe(filename)
  await expect(attachmentPanel.locator('.attachment-details')).toContainText('Downloads: 2')
  await attachEvidenceScreenshot(page, testInfo, 'anexo-setor-consultado-baixado', attachmentPanel)

  await assignedProtocol.getByRole('button', { name: 'Acessos' }).click()
  const accessHistory = page.getByRole('region', { name: `Histórico de acesso do protocolo ${protocolNumber}` })
  const accessEvents = accessHistory.getByRole('list', { name: `Eventos de leitura e download do protocolo ${protocolNumber}` })
  const accessEventItems = accessEvents.getByRole('listitem')
  await expect(accessEventItems.filter({ hasText: filename }).first()).toBeVisible()
  await expect(accessEventItems.filter({ hasText: 'Requerente' }).first()).toBeVisible()
  await expect(accessEventItems.filter({ hasText: 'servidor' }).first()).toBeVisible()
  await expect(accessEventItems.filter({ hasText: 'Download' })).toHaveCount(2)
  await attachEvidenceScreenshot(page, testInfo, 'historico-acesso-do-documento', accessHistory)
})
