import { expect, test } from '@playwright/test'
import { attachEvidenceScreenshot } from './evidence-fixtures'

test('administrador configura e mantém campos de identificação por assunto', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 960 })
  const suffix = Date.now().toString()
  const initialLabel = `Contato adicional ${suffix}`
  const updatedLabel = `Documento adicional ${suffix}`
  const fieldKey = `participant_${suffix}`
  const subjectName = 'Solicitação geral'

  async function signIn() {
    await page.goto('/servidor/')
    await page.getByLabel('Usuário').fill('servidor')
    await page.getByLabel('Senha').fill('e2e-local-only-password')
    await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
    await expect(page.getByRole('region', { name: 'Assuntos de protocolo' })).toBeVisible()
  }

  await signIn()
  const subjectRow = page.getByRole('list', { name: 'Assuntos de protocolo cadastrados' })
    .getByRole('listitem').filter({ hasText: subjectName })
  await subjectRow.getByRole('button', { name: 'Configurar campos' }).click()
  const fieldsPanel = subjectRow.getByText('Esta etapa configura os campos do cadastro.')
    .locator('xpath=ancestor::div[contains(@class,"participant-fields-panel")]')
  await expect(fieldsPanel).toBeVisible()
  expect(await fieldsPanel.evaluate(element => element.clientWidth), 'o teste deve exercitar um painel de administração estreito dentro de uma janela desktop').toBeLessThanOrEqual(520)
  await expect(fieldsPanel.getByText('Nenhum campo de participante foi definido para este assunto.')).toBeVisible()

  await fieldsPanel.getByLabel('Identificador do campo').fill(fieldKey)
  await fieldsPanel.getByLabel('Nome exibido').fill(initialLabel)
  await fieldsPanel.getByRole('button', { name: 'Adicionar campo' }).click()
  const fieldRow = fieldsPanel.locator('.participant-field-row').filter({ hasText: fieldKey })
  await expect(fieldRow).toBeVisible()
  const rowLayoutFits = await fieldRow.evaluate((element) => {
    const row = element.getBoundingClientRect()
    return element.scrollWidth <= element.clientWidth + 1 && [...element.children].every((child) => {
      const childBounds = child.getBoundingClientRect()
      return childBounds.left >= row.left - 1 && childBounds.right <= row.right + 1
    })
  })
  expect(rowLayoutFits, 'os campos e ações devem caber no painel estreito sem sobreposição ou rolagem horizontal').toBe(true)
  await expect(fieldsPanel.getByRole('status').filter({ hasText: `Campo “${initialLabel}” adicionado ao assunto.` })).toBeVisible()

  await fieldRow.getByLabel(`Nome do campo: ${initialLabel}`).fill(updatedLabel)
  await fieldRow.getByRole('combobox', { name: `Tipo do campo: ${initialLabel}` }).click()
  await page.getByRole('option', { name: 'Documento', exact: true }).click()
  await fieldRow.getByRole('button', { name: 'Salvar campo' }).click()
  await expect(fieldsPanel.getByRole('status').filter({ hasText: `Campo “${updatedLabel}” atualizado.` })).toBeVisible()

  // Reauthenticate after a full document load to prove the configuration is persisted.
  await page.reload()
  await signIn()
  const refreshedSubject = page.getByRole('list', { name: 'Assuntos de protocolo cadastrados' })
    .getByRole('listitem').filter({ hasText: subjectName })
  await refreshedSubject.getByRole('button', { name: 'Configurar campos' }).click()
  const refreshedField = refreshedSubject.locator('.participant-field-row').filter({ hasText: fieldKey })
  await expect(refreshedField.getByLabel(`Nome do campo: ${updatedLabel}`)).toHaveValue(updatedLabel)
  await expect(refreshedField.getByLabel(`Tipo do campo: ${updatedLabel}`)).toHaveValue('Documento')
  await attachEvidenceScreenshot(page, testInfo, 'configuracao-persistida', refreshedField)

  await refreshedField.getByRole('button', { name: 'Remover' }).click()
  const removalDialog = page.getByRole('dialog', { name: 'Remover campo?' })
  await expect(removalDialog.getByText(updatedLabel)).toBeVisible()
  const deleteResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'DELETE' && response.url().includes('/participant-fields/'))
  const confirmRemoval = removalDialog.getByRole('button', { name: 'Confirmar remoção' })
  await confirmRemoval.focus()
  await expect(confirmRemoval).toBeFocused()
  await page.keyboard.press('Enter')
  const deleteResponse = await deleteResponsePromise
  expect(deleteResponse.status(), await deleteResponse.text()).toBe(204)
  await expect(refreshedSubject.getByText('Nenhum campo de participante foi definido para este assunto.')).toBeVisible()
})
