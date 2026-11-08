import { expect, test } from '@playwright/test'
import { attachEvidenceScreenshot } from './evidence-fixtures'

test('conversa de protocolo mantém respostas, anexos e visibilidade no fluxo real', async ({ page }, testInfo) => {
  const suffix = Date.now()
  const subject = `Dúvida sobre serviço ${suffix}`
  const requirementName = `Arquivo para conversa ${suffix}`
  const staffMessage = `Resposta da equipe ${suffix}`
  const citizenFollowUp = `Complemento do requerente ${suffix}`
  const internalMessage = `Nota interna ${suffix}`
  const reopenedFollowUp = `Retorno após reabertura ${suffix}`
  const answerFilename = `resposta-${suffix}.pdf`

  const staffPage = await page.context().newPage()
  await staffPage.goto('/servidor/')
  await staffPage.getByLabel('Usuário').fill('servidor')
  await staffPage.getByLabel('Senha').fill('e2e-local-only-password')
  await staffPage.getByRole('combobox', { name: 'Organização' }).click()
  await staffPage.getByRole('option', { name: 'Município Alfa (demonstração)', exact: true }).click()
  await staffPage.getByRole('button', { name: 'Entrar e carregar caixa' }).click()

  const catalog = staffPage.getByRole('region', { name: 'Assuntos de protocolo' })
  await expect(catalog).toBeVisible()
  await expect(catalog.getByText('Carregando assuntos…')).toHaveCount(0)
  await catalog.getByLabel('Nome do assunto').fill(subject)
  await catalog.getByRole('combobox', { name: 'Disponibilidade de atendimento', exact: true }).first().click()
  await staffPage.getByRole('option', { name: 'Central de Atendimento', exact: true }).click()
  await catalog.getByRole('combobox', { name: 'Setor destinatário para solicitações online' }).click()
  await staffPage.getByRole('option', { name: 'Protocolo Geral', exact: true }).click()
  await catalog.getByRole('button', { name: 'Adicionar assunto' }).click()
  await expect(catalog.getByRole('status').filter({ hasText: `Assunto “${subject}” disponível online; as solicitações seguem para Protocolo Geral.` })).toBeVisible()

  const subjectRow = catalog.getByRole('list', { name: 'Assuntos de protocolo cadastrados' })
    .getByRole('listitem').filter({ hasText: subject })
  await subjectRow.getByLabel('Nome do documento').fill(requirementName)
  await subjectRow.getByRole('button', { name: 'Adicionar documento' }).click()
  await expect(catalog.getByRole('status').filter({ hasText: `Documento “${requirementName}” configurado para ${subject}.` })).toBeVisible()

  const createResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/public/protocols')
  )
  await page.goto('/')
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: 'Pular para o conteúdo' })).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.locator('#main-content')).toBeFocused()
  await page.getByRole('combobox', { name: 'Organização destinatária *' }).click()
  await page.getByRole('option', { name: 'Município Alfa (demonstração)', exact: true }).click()
  await page.getByLabel('Nome completo *').fill('Pessoa de Teste Interações')
  await page.getByLabel('E-mail *').fill('interacoes-e2e@example.test')
  await page.getByRole('combobox', { name: 'Assunto *', exact: true }).click()
  await page.getByRole('option', { name: subject, exact: true }).click()
  await page.getByLabel('Descreva sua solicitação *').fill('Peço esclarecimentos sobre um serviço público fictício.')
  await page.getByRole('button', { name: 'Registrar solicitação' }).click()

  const confirmation = page.getByText(/Protocolo .* registrado/)
  await expect(confirmation).toBeVisible()
  const protocolNumber = (await confirmation.textContent())?.match(/Protocolo (\d{4}-\d{6}) registrado/)?.[1]
  expect(protocolNumber).toBeTruthy()
  const token = await page.getByLabel('Chave do comprovante').inputValue()
  expect(token.length).toBeGreaterThan(40)
  const createdProtocol = await (await createResponsePromise).json() as { id: string }
  expect(createdProtocol.id).toBeTruthy()

  const citizenFileInput = page.getByLabel('Anexar documentos (opcional)')
  await expect(citizenFileInput).toHaveAttribute('accept', '.pdf')

  await staffPage.getByRole('button', { name: 'Atualizar', exact: true }).click()
  const protocolCard = staffPage.getByRole('article').filter({ hasText: protocolNumber! })
  await expect(protocolCard).toHaveCount(1)
  await protocolCard.getByRole('button', { name: `Ver mensagens do protocolo ${protocolNumber}` }).click()
  const staffConversation = staffPage.getByRole('region', { name: `Mensagens do protocolo ${protocolNumber}` })
  const staffComposer = staffConversation.getByRole('form', { name: `Enviar mensagem no protocolo ${protocolNumber}` })
  await expect(staffComposer.getByLabel('Anexar documentos (opcional)')).toHaveAttribute('accept', '.pdf')
  await staffComposer.getByRole('textbox', { name: 'Mensagem', exact: true }).fill(staffMessage)
  await staffComposer.getByLabel('Anexar documentos (opcional)').setInputFiles({
    name: answerFilename,
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.7\nResposta fictícia da equipe.\n')
  })
  await staffComposer.getByRole('button', { name: 'Enviar mensagem' }).click()
  await expect(staffConversation.getByText(staffMessage, { exact: true })).toBeVisible()
  const staffHistory = staffConversation.getByRole('list', { name: `Histórico de mensagens do protocolo ${protocolNumber}` })
  await expect(staffHistory.getByText('Visível ao cidadão', { exact: true })).toBeVisible()
  await protocolCard.getByRole('button', { name: 'Anexos da abertura' }).click()
  const openingAttachments = staffPage.getByRole('region', { name: `Documentos do protocolo ${protocolNumber}` })
  await expect(openingAttachments.getByText('Este protocolo não tem documentos anexados.')).toBeVisible()
  await expect(openingAttachments.getByText(answerFilename, { exact: true })).toHaveCount(0)
  await protocolCard.getByRole('button', { name: 'Anexos da abertura' }).click()

  const firstRefresh = page.waitForResponse(response =>
    response.request().method() === 'GET' && new URL(response.url()).pathname.endsWith('/public/protocols/tracking')
  )
  await page.getByLabel('Chave do comprovante').fill(token)
  await page.getByRole('button', { name: 'Consultar' }).click()
  const publicAfterResponse = await (await firstRefresh).json() as {
    interactions: Array<{ message: string; attachments: Array<{ filename: string }> }>
  }
  await expect(page.getByText(staffMessage, { exact: true })).toBeVisible()
  const citizenAnswer = publicAfterResponse.interactions.find(interaction => interaction.message === staffMessage)
  expect(citizenAnswer?.attachments.map(attachment => attachment.filename)).toContain(answerFilename)
  const attachmentDownload = page.waitForEvent('download')
  await page.getByRole('button', { name: `Baixar ${answerFilename}` }).click()
  expect((await attachmentDownload).suggestedFilename()).toBe(answerFilename)

  await page.getByLabel('Sua mensagem').fill(citizenFollowUp)
  await page.getByRole('button', { name: 'Enviar mensagem' }).click()
  await expect(page.getByText(citizenFollowUp, { exact: true })).toBeVisible()
  await protocolCard.getByRole('button', { name: 'Atualizar mensagens' }).click()
  await expect(staffConversation.getByText(citizenFollowUp, { exact: true })).toBeVisible()

  await staffConversation.getByRole('combobox', { name: `Visibilidade da mensagem para ${protocolNumber}` }).click()
  await staffPage.getByRole('option', { name: 'Somente equipe', exact: true }).click()
  await staffComposer.getByRole('textbox', { name: 'Mensagem', exact: true }).fill(internalMessage)
  await expect(staffConversation.getByText('Esta mensagem ficará somente na área do servidor.')).toBeVisible()
  await staffComposer.getByRole('button', { name: 'Enviar mensagem' }).click()
  await expect(staffConversation.getByText(internalMessage, { exact: true })).toBeVisible()
  await expect(staffHistory.getByText('Somente equipe', { exact: true })).toBeVisible()

  const afterInternalRefresh = page.waitForResponse(response =>
    response.request().method() === 'GET' && new URL(response.url()).pathname.endsWith('/public/protocols/tracking')
  )
  await page.getByRole('button', { name: 'Consultar' }).click()
  const publicAfterInternal = await (await afterInternalRefresh).json() as { interactions: Array<{ message: string; visibility?: string }> }
  await expect(page.getByText(internalMessage, { exact: true })).toHaveCount(0)
  expect(publicAfterInternal.interactions.some(interaction => interaction.message === internalMessage)).toBe(false)
  expect(publicAfterInternal.interactions.every(interaction => interaction.visibility === undefined || interaction.visibility === 'PUBLIC')).toBe(true)

  await protocolCard.getByRole('button', { name: 'Encaminhar' }).click()
  await protocolCard.getByRole('button', { name: 'Receber' }).click()
  await staffPage.getByLabel('Próximo setor').click()
  await staffPage.getByRole('option', { name: 'Secretaria de Obras' }).click()
  await protocolCard.getByRole('button', { name: 'Despachar' }).click()
  await protocolCard.getByRole('button', { name: 'Receber' }).click()
  await protocolCard.getByRole('button', { name: 'Concluir' }).click()
  await expect(protocolCard.getByText('Concluído', { exact: true })).toBeVisible()
  await attachEvidenceScreenshot(staffPage, testInfo, 'protocolo-concluido', protocolCard)

  const afterCompleteRefresh = page.waitForResponse(response =>
    response.request().method() === 'GET' && new URL(response.url()).pathname.endsWith('/public/protocols/tracking')
  )
  await page.getByRole('button', { name: 'Consultar' }).click()
  const completedTracking = await (await afterCompleteRefresh).json() as { status: string }
  expect(completedTracking.status).toBe('COMPLETED')
  await expect(page.getByText('Este protocolo foi concluído. Não é possível enviar novas mensagens.')).toBeVisible()
  await expect(page.getByRole('form', { name: 'Enviar mensagem ao protocolo' })).toHaveCount(0)

  const completedPost = await page.request.post(
    `http://127.0.0.1:${process.env.NEXOGOV_E2E_API_PORT || '8080'}/api/v1/public/protocols/${createdProtocol.id}/interactions`,
    {
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        request: {
          name: 'request', mimeType: 'application/json',
          buffer: Buffer.from(JSON.stringify({ message: 'Mensagem bloqueada após conclusão.' }))
        }
      }
    }
  )
  expect(completedPost.status()).toBeGreaterThanOrEqual(400)
  expect(completedPost.status()).toBeLessThan(500)

  await protocolCard.getByRole('button', { name: `Reabrir protocolo ${protocolNumber}` }).click()
  await expect(staffPage.getByText(`Protocolo ${protocolNumber} reaberto para o setor responsável.`)).toBeVisible()
  await attachEvidenceScreenshot(staffPage, testInfo, 'protocolo-reaberto', protocolCard)
  const afterReopenRefresh = page.waitForResponse(response =>
    response.request().method() === 'GET' && new URL(response.url()).pathname.endsWith('/public/protocols/tracking')
  )
  await page.getByRole('button', { name: 'Consultar' }).click()
  const reopenedTracking = await (await afterReopenRefresh).json() as { status: string }
  expect(reopenedTracking.status).not.toBe('COMPLETED')
  await page.getByLabel('Sua mensagem').fill(reopenedFollowUp)
  await page.getByRole('button', { name: 'Enviar mensagem' }).click()
  await expect(page.getByText(reopenedFollowUp, { exact: true })).toBeVisible()
})
