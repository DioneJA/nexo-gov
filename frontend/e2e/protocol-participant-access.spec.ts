import { expect, test } from '@playwright/test'

test('cidadão cria acessos individuais, acompanha e revoga uma pessoa vinculada', async ({ browser }) => {
  const suffix = Date.now().toString()
  const fieldKey = `contact_${suffix}`
  const fieldLabel = `E-mail da pessoa ${suffix}`
  const subjectName = `Acompanhamento individual ${suffix}`

  const applicantContext = await browser.newContext()
  const applicantPage = await applicantContext.newPage()
  await applicantPage.goto('/servidor/')
  await applicantPage.getByLabel('Usuário').fill('servidor')
  await applicantPage.getByLabel('Senha').fill('e2e-local-only-password')
  await applicantPage.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
  await expect(applicantPage.getByRole('region', { name: 'Assuntos de protocolo' })).toBeVisible()

  const subjectCatalog = applicantPage.getByRole('region', { name: 'Assuntos de protocolo' })
  await subjectCatalog.getByLabel('Nome do assunto').fill(subjectName)
  await subjectCatalog.getByRole('combobox', { name: 'Disponibilidade de atendimento', exact: true }).first().click()
  await applicantPage.getByRole('option', { name: 'Central de Atendimento', exact: true }).click()
  await subjectCatalog.getByRole('combobox', { name: 'Setor destinatário para solicitações online' }).click()
  await applicantPage.getByRole('option', { name: 'Protocolo Geral', exact: true }).click()
  await subjectCatalog.getByRole('button', { name: 'Adicionar assunto' }).click()
  await expect(subjectCatalog.getByRole('status').filter({
    hasText: `Assunto “${subjectName}” disponível online; as solicitações seguem para Protocolo Geral.`
  })).toBeVisible()

  const subjectRow = applicantPage.getByRole('list', { name: 'Assuntos de protocolo cadastrados' })
    .getByRole('listitem').filter({ hasText: subjectName })
  await subjectRow.getByRole('button', { name: 'Configurar campos' }).click()
  const fieldsPanel = subjectRow.getByText('Esta etapa configura os campos do cadastro.')
    .locator('xpath=ancestor::div[contains(@class,"participant-fields-panel")]')
  await expect(fieldsPanel).toBeVisible()
  await fieldsPanel.getByLabel('Identificador do campo').fill(fieldKey)
  await fieldsPanel.getByLabel('Nome exibido').fill(fieldLabel)
  await fieldsPanel.getByLabel('Tipo de resposta').click()
  await applicantPage.getByRole('option', { name: 'E-mail', exact: true }).click()
  await fieldsPanel.getByLabel('Obrigatório').check()
  await fieldsPanel.getByRole('button', { name: 'Adicionar campo' }).click()
  await expect(fieldsPanel.locator('.participant-field-row').filter({ hasText: fieldKey })).toBeVisible()

  const subjectCatalogResponse = applicantPage.waitForResponse(response =>
    response.url().includes('/protocol-subjects') && response.request().method() === 'GET')
  await applicantPage.goto('/')
  const publicSubject = (await (await subjectCatalogResponse).json())
    .find((subject: { name: string }) => subject.name === subjectName)
  expect(publicSubject?.participantFields).toContainEqual(expect.objectContaining({ key: fieldKey, label: fieldLabel, inputType: 'EMAIL', required: true }))
  await applicantPage.getByLabel('Nome completo *').fill('Requerente E2E')
  await applicantPage.getByLabel('E-mail *').fill('requerente-e2e@example.test')
  await applicantPage.getByRole('combobox', { name: 'Assunto *', exact: true }).click()
  await applicantPage.getByRole('option', { name: subjectName, exact: true }).click()
  const participants = applicantPage.getByRole('group', { name: 'Pessoas que também acompanharão' })
  await participants.getByRole('button', { name: 'Adicionar pessoa' }).click()
  await expect(participants.getByText(fieldLabel, { exact: false }).first()).toBeVisible()
  await participants.getByRole('button', { name: 'Adicionar pessoa' }).click()
  const drafts = participants.locator('.participant-draft')
  await drafts.nth(0).getByLabel('Nome da pessoa *').fill('Marina E2E')
  await drafts.nth(0).getByLabel(`${fieldLabel} *`).fill('marina-e2e@example.test')
  await drafts.nth(1).getByLabel('Nome da pessoa *').fill('Rafael E2E')
  await drafts.nth(1).getByLabel(`${fieldLabel} *`).fill('rafael-e2e@example.test')
  await applicantPage.getByLabel('Descreva sua solicitação *').fill('Solicito análise de um endereço fictício.')
  await applicantPage.getByRole('button', { name: 'Registrar solicitação' }).click()

  await expect(applicantPage.getByText(/Protocolo .* registrado/)).toBeVisible()
  const applicantToken = await applicantPage.getByLabel('Chave do comprovante').inputValue()
  expect(applicantToken.length).toBeGreaterThan(40)
  const marinaLink = await applicantPage.getByRole('textbox', { name: 'Convite de Marina E2E' }).inputValue()
  const rafaelLink = await applicantPage.getByRole('textbox', { name: 'Convite de Rafael E2E' }).inputValue()
  expect(new URL(marinaLink).hash).toMatch(/^#token=.{40,}$/)
  expect(new URL(rafaelLink).hash).toMatch(/^#token=.{40,}$/)

  const marinaContext = await browser.newContext()
  const marinaPage = await marinaContext.newPage()
  await marinaPage.goto(marinaLink)
  await expect.poll(() => marinaPage.evaluate(() => window.location.hash)).toBe('')
  const marinaStorage = await marinaPage.evaluate(() => JSON.stringify({
    local: Object.keys(localStorage).map(key => localStorage.getItem(key)),
    session: Object.keys(sessionStorage).map(key => sessionStorage.getItem(key))
  }))
  expect(marinaStorage).not.toContain(new URL(marinaLink).hash.slice('#token='.length))
  await expect(marinaPage.getByText(/Este convite foi criado para Marina E2E/)).toBeVisible()
  await expect(marinaPage.getByText('marina-e2e@example.test')).toBeVisible()
  await expect(marinaPage.getByText('rafael-e2e@example.test')).toHaveCount(0)
  await expect(marinaPage.getByText('Rafael E2E', { exact: true })).toHaveCount(0)
  await marinaPage.getByLabel('Sua mensagem').fill('Complemento enviado pela pessoa vinculada.')
  await marinaPage.getByRole('button', { name: 'Enviar mensagem' }).click()
  await expect(marinaPage.getByText('Complemento enviado pela pessoa vinculada.')).toBeVisible()

  const rafaelContext = await browser.newContext()
  const rafaelPage = await rafaelContext.newPage()
  await rafaelPage.goto(rafaelLink)
  await expect(rafaelPage.getByText(/Este convite foi criado para Rafael E2E/)).toBeVisible()
  await expect(rafaelPage.getByText('rafael-e2e@example.test')).toBeVisible()
  await expect(rafaelPage.getByText('marina-e2e@example.test')).toHaveCount(0)
  await expect(rafaelPage.getByText('Pessoa envolvida', { exact: true })).toBeVisible()
  await expect(rafaelPage.getByText('Rafael E2E', { exact: true })).toHaveCount(0)

  await applicantPage.getByRole('button', { name: 'Consultar' }).click()
  await expect(applicantPage.getByText('Complemento enviado pela pessoa vinculada.')).toBeVisible()
  await expect(applicantPage.getByText('Pessoa envolvida', { exact: true })).toBeVisible()
  const revokeButton = applicantPage.getByRole('button', { name: 'Revogar acesso de Marina E2E' })
  await revokeButton.click()
  const revokeDialog = applicantPage.getByRole('dialog', { name: 'Revogar acesso?' })
  await expect(revokeDialog).toBeVisible()
  await revokeDialog.getByRole('button', { name: 'Revogar acesso', exact: true }).click()
  await expect(applicantPage.getByText('O acesso de Marina E2E foi revogado.')).toBeVisible()
  await expect(applicantPage.getByText('Marina E2E Acesso revogado')).toBeVisible()

  await marinaPage.getByRole('button', { name: 'Consultar' }).click()
  await expect(marinaPage.getByRole('alert').filter({ hasText: 'Protocolo não localizado' })).toBeVisible()
  await rafaelPage.getByRole('button', { name: 'Consultar' }).click()
  await expect(rafaelPage.getByText(/Este convite foi criado para Rafael E2E/)).toBeVisible()

  await applicantPage.getByLabel('Chave do comprovante').fill(new URL(rafaelLink).hash.slice('#token='.length))
  await applicantPage.getByRole('button', { name: 'Consultar' }).click()
  await expect(applicantPage.getByText(/Este convite foi criado para Rafael E2E/)).toBeVisible()
  await expect(applicantPage.getByRole('region', { name: 'Convites para outras pessoas' })).toHaveCount(0)
  await expect(applicantPage.getByRole('button', { name: 'Baixar comprovante' })).toHaveCount(0)

  let releaseApplicantResponse!: () => void
  const applicantResponseGate = new Promise<void>(resolve => { releaseApplicantResponse = resolve })
  let markApplicantLookupStarted!: () => void
  const applicantLookupStarted = new Promise<void>(resolve => { markApplicantLookupStarted = resolve })
  let markApplicantLookupCompleted!: () => void
  const applicantLookupCompleted = new Promise<void>(resolve => { markApplicantLookupCompleted = resolve })
  await applicantPage.route('**/public/protocols/tracking', async route => {
    if (route.request().headers().authorization === `Bearer ${applicantToken}`) {
      markApplicantLookupStarted()
      await applicantResponseGate
      await route.continue()
      markApplicantLookupCompleted()
      return
    }
    await route.continue()
  })
  await applicantPage.getByLabel('Chave do comprovante').fill(applicantToken)
  await applicantPage.getByRole('button', { name: 'Consultar' }).click()
  await applicantLookupStarted
  await applicantPage.getByLabel('Chave do comprovante').fill(new URL(rafaelLink).hash.slice('#token='.length))
  await applicantPage.getByRole('button', { name: 'Consultar' }).click()
  await expect(applicantPage.getByText(/Este convite foi criado para Rafael E2E/)).toBeVisible()
  releaseApplicantResponse()
  await applicantLookupCompleted
  await expect(applicantPage.getByText(/Este convite foi criado para Rafael E2E/)).toBeVisible()
  await expect(applicantPage.getByRole('region', { name: 'Acessos de pessoas vinculadas' })).toHaveCount(0)
  await applicantPage.unroute('**/public/protocols/tracking')

  await applicantContext.close()
  await marinaContext.close()
  await rafaelContext.close()
})
