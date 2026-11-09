import { expect, test } from '@playwright/test'
import { attachEvidenceScreenshot } from './evidence-fixtures'

test('cidadão abre protocolo, servidor tramita e cidadão acompanha até a conclusão', async ({ page }, testInfo) => {
  const subject = 'Solicitação geral'
  let failedTrackingLookups = 0
  await page.route('**/api/v1/public/protocols/tracking', async route => {
    if (failedTrackingLookups++ === 0) {
      await route.abort('failed')
      return
    }
    await route.continue()
  })
  await page.goto('/')

  await page.getByLabel('Nome completo *').fill('Pessoa de Teste E2E')
  await page.getByLabel('E-mail *').fill('cidadao-e2e@example.test')
  await page.getByRole('combobox', { name: 'Assunto *', exact: true }).click()
  await page.getByRole('option', { name: subject, exact: true }).click()
  await page.getByLabel('Descreva sua solicitação *').fill('Solicito análise de uma via pública fictícia.')
  await page.getByRole('button', { name: 'Registrar solicitação' }).click()

  const confirmation = page.getByText(new RegExp(`Protocolo .* registrado`))
  await expect(confirmation).toBeVisible()
  const confirmationText = await confirmation.textContent()
  const protocolNumber = confirmationText?.match(/Protocolo (\d{4}-\d{6}) registrado/)?.[1]
  expect(protocolNumber).toBeTruthy()
  const token = await page.getByLabel('Chave do comprovante').inputValue()
  expect(token.length).toBeGreaterThan(40)
  await expect(page.getByRole('button', { name: 'Baixar comprovante' })).toBeVisible()
  await expect(page.getByText('O protocolo foi registrado, mas não foi possível carregar o andamento agora. Tente consultar novamente; o comprovante continua disponível.')).toBeVisible()
  await attachEvidenceScreenshot(page, testInfo, 'recibo-disponivel-apos-falha', page.getByRole('button', { name: 'Baixar comprovante' }))

  const downloadReady = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Baixar comprovante' }).click()
  const receipt = await downloadReady
  expect(receipt.suggestedFilename()).toBe('comprovante-protocolo.pdf')

  await page.getByRole('button', { name: 'Consultar' }).click()
  await expect(page.getByText(subject, { exact: true })).toBeVisible()
  await expect(page.getByText('O protocolo foi registrado, mas não foi possível carregar o andamento agora. Tente consultar novamente; o comprovante continua disponível.')).toHaveCount(0)

  const staffPage = await page.context().newPage()
  await staffPage.goto('/servidor/')
  await staffPage.getByLabel('Usuário').fill('servidor')
  await staffPage.getByLabel('Senha').fill('e2e-local-only-password')
  await staffPage.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
  const assignedProtocol = staffPage.getByRole('article').filter({ hasText: protocolNumber! })
  await expect(assignedProtocol).toHaveCount(1)
  await expect(assignedProtocol.getByText(subject, { exact: true })).toBeVisible()
  await expect(staffPage.getByText('Estrutura da organização')).toBeVisible()

  await staffPage.getByRole('combobox', { name: 'Organização' }).click()
  await staffPage.getByRole('option', { name: 'Município Beta (demonstração)' }).click()
  await expect(staffPage.getByText('Estrutura da organização')).toHaveCount(0)
  await staffPage.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
  await expect(staffPage.getByText('Estrutura da organização')).toHaveCount(0)
  await staffPage.getByRole('combobox', { name: 'Organização' }).click()
  await staffPage.getByRole('option', { name: 'Município Alfa (demonstração)' }).click()
  await staffPage.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
  await expect(staffPage.getByText('Estrutura da organização')).toBeVisible()

  const department = `Unidade E2E ${Date.now()}`
  await staffPage.getByLabel('Nome do novo setor').fill(department)
  const createDepartmentButton = staffPage.getByRole('button', { name: 'Criar setor' })
  await expect(createDepartmentButton).toHaveClass(/q-btn--actionable/)
  const createDepartmentResponse = staffPage.waitForResponse(response =>
    response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/staff/departments')
  )
  await createDepartmentButton.click()
  expect((await createDepartmentResponse).status()).toBe(201)
  await expect(staffPage.locator('.organogram-node-name').filter({ hasText: department })).toBeVisible()

  const childDepartment = `Subsetor E2E ${Date.now()}`
  const departmentNameInput = staffPage.getByLabel('Nome do novo setor')
  await departmentNameInput.fill(childDepartment)
  const parentDepartmentSelect = staffPage.getByRole('combobox', { name: 'Setor superior (opcional)' })
  await parentDepartmentSelect.click()
  await staffPage.getByRole('option', { name: department, exact: true }).click()
  await expect(departmentNameInput).toHaveValue(childDepartment)
  await expect(parentDepartmentSelect).toHaveAttribute('aria-expanded', 'false')
  await expect(
    staffPage.locator('.q-field.q-select').filter({ has: parentDepartmentSelect }).locator('.q-select__selected-value')
  ).toHaveText(department)
  await expect(staffPage.getByText('Informe o nome do setor.', { exact: true })).toHaveCount(0)
  await expect(createDepartmentButton).toHaveClass(/q-btn--actionable/)
  await expect(createDepartmentButton).toBeEnabled()
  const createChildDepartmentResponse = staffPage.waitForResponse(response =>
    response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/staff/departments')
  )
  await createDepartmentButton.click()
  expect((await createChildDepartmentResponse).status()).toBe(201)
  await expect(staffPage.locator('.organogram-node-name').filter({ hasText: childDepartment })).toBeVisible()

  await staffPage.getByRole('combobox', { name: 'Servidor' }).click()
  await staffPage.getByRole('option', { name: 'servidor', exact: true }).click()
  await staffPage.getByLabel('Nome completo').fill('Joana Servidora E2E')
  await staffPage.getByLabel('Cargo').fill('Analista de Protocolo')
  await staffPage.getByLabel('Data de cadastro conhecida').fill('2022-05-19')
  await staffPage.getByRole('button', { name: 'Salvar dados do servidor' }).click()
  await expect(staffPage.getByText('Dados de Joana Servidora E2E atualizados no organograma.')).toBeVisible()
  await expect(staffPage.getByText('O acesso da área do servidor não está configurado para este ambiente.')).toHaveCount(0)
  await expect(staffPage.getByText('Joana Servidora E2E', { exact: true }).first()).toBeVisible()
  await expect(staffPage.locator('.presence-online').first()).toBeVisible()
  const joanaPerson = staffPage.locator('.organogram-person')
    .filter({ has: staffPage.getByText('Joana Servidora E2E', { exact: true }) })
    .first()
  await expect(joanaPerson).toBeVisible()
  await attachEvidenceScreenshot(staffPage, testInfo, 'organograma-presenca', joanaPerson)

  await assignedProtocol.getByRole('button', { name: 'Encaminhar' }).click()
  await assignedProtocol.getByRole('button', { name: 'Receber' }).click()
  await staffPage.getByLabel('Próximo setor').click()
  await staffPage.getByRole('option', { name: 'Secretaria de Obras' }).click()
  await assignedProtocol.getByRole('button', { name: 'Despachar' }).click()
  await assignedProtocol.getByRole('button', { name: 'Receber' }).click()
  await assignedProtocol.getByRole('button', { name: 'Concluir' }).click()
  await expect(staffPage.getByText(`Ação registrada para ${protocolNumber}.`)).toBeVisible()
  await expect(staffPage.getByText('Concluído', { exact: true })).toBeVisible()

  await page.getByRole('tab', { name: 'Acompanhar protocolo' }).click()
  await page.getByLabel('Chave do comprovante').fill(token)
  await page.getByRole('button', { name: 'Consultar' }).click()
  await expect(page.getByText('Concluído', { exact: true })).toBeVisible()
  await expect(page.getByText(subject)).toBeVisible()
})
