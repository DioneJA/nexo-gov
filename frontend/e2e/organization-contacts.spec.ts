import { expect, test } from '@playwright/test'
import { attachEvidenceScreenshot } from './evidence-fixtures'

test.use({ hasTouch: true, viewport: { width: 1440, height: 1000 } })

test('administrador mantém contatos por tipo e importa/exporta CSV', async ({ page }, testInfo) => {
  let failFirstContactsLoad = true
  await page.route('**/api/v1/staff/organization/contacts**', async route => {
    if (route.request().method() === 'GET' && failFirstContactsLoad) {
      failFirstContactsLoad = false
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Serviço temporariamente indisponível.' })
      })
      return
    }
    await route.continue()
  })

  await page.goto('/servidor/')
  await page.getByLabel('Usuário').fill('servidor')
  await page.getByLabel('Senha').fill('e2e-local-only-password')
  await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()

  const contacts = page.getByRole('region', { name: 'Contatos da organização' })
  await expect(contacts).toBeVisible()
  const contactsWidth = await contacts.evaluate(element => element.getBoundingClientRect().width)
  const viewportWidth = await page.evaluate(() => window.innerWidth)
  expect(contactsWidth).toBeGreaterThan(viewportWidth * 0.85)

  await expect(contacts.getByRole('alert')).toHaveText('Não foi possível carregar os contatos. Tente novamente.')
  const retryContacts = contacts.getByRole('button', { name: 'Tentar novamente' })
  await expect(retryContacts).toBeVisible()
  await expect(contacts.getByText('Nenhum contato cadastrado.', { exact: true })).toHaveCount(0)
  await retryContacts.click()
  await expect(contacts.getByRole('button', { name: 'Tentar novamente' })).toHaveCount(0)
  await expect(contacts.getByText('Nenhum contato cadastrado.', { exact: true })).toBeVisible()

  const search = contacts.getByRole('searchbox', { name: 'Buscar contato' })
  const includeArchived = contacts.getByRole('checkbox', { name: 'Incluir arquivados' })
  await search.focus()
  await page.keyboard.press('Tab')
  await expect(includeArchived).toBeFocused()
  const checkboxFocusStyle = await includeArchived.evaluate(element => {
    const label = element.closest('.contact-archived')!
    const style = getComputedStyle(label)
    return { outlineStyle: style.outlineStyle, outlineWidth: style.outlineWidth }
  })
  expect(checkboxFocusStyle).toEqual({ outlineStyle: 'solid', outlineWidth: '3px' })

  async function createContact(type: string, name: string, email: string) {
    await contacts.getByRole('button', { name: 'Adicionar' }).click()
    await contacts.getByLabel('Tipo de contato').selectOption(type)
    await contacts.getByLabel('Nome').fill(name)
    await contacts.locator('input[type="email"]').fill(email)
    await contacts.getByRole('button', { name: 'Salvar contato' }).click()
    await expect(contacts.getByText(name, { exact: true })).toBeVisible()
  }

  await createContact('PERSON', 'Ana Pública', 'ana.publica@example.test')
  await createContact('MUNICIPAL_DEPARTMENT', 'Financeiro', 'financeiro@example.test')
  await createContact('LEGAL_ENTITY', 'Prefeitura Parceira', 'parceira@example.test')

  await search.fill('Financeiro')
  await expect(contacts.locator('.contact-row')).toHaveCount(1)
  await expect(contacts.getByText('Financeiro', { exact: true })).toBeVisible()
  await search.fill('')
  await expect(contacts.locator('.contact-row')).toHaveCount(3)

  await expect(contacts.getByText('Pessoa física', { exact: true })).toBeVisible()
  await expect(contacts.getByText('Setor da organização', { exact: true })).toBeVisible()
  await expect(contacts.getByText('Prefeitura / pessoa jurídica', { exact: true })).toBeVisible()
  await attachEvidenceScreenshot(page, testInfo, 'contatos-tipos', contacts.locator('.contact-list'))

  const person = contacts.locator('.contact-row').filter({ hasText: 'Ana Pública' })
  await person.getByRole('button', { name: 'Editar' }).click()
  await contacts.getByLabel('Nome').fill('Ana Pública atualizada')
  await contacts.locator('input[type="tel"]').fill('+55 71 3333-4400')
  await contacts.getByRole('button', { name: 'Salvar contato' }).click()
  await expect(contacts.getByText('Ana Pública atualizada', { exact: true })).toBeVisible()

  const updatedPerson = contacts.locator('.contact-row').filter({ hasText: 'Ana Pública atualizada' })
  await contacts.getByLabel('Incluir arquivados').check()
  await expect(updatedPerson).toBeVisible()
  await updatedPerson.getByRole('button', { name: 'Arquivar' }).click()
  const archivedStatus = updatedPerson.getByText('Arquivado', { exact: true })
  await expect(archivedStatus).toHaveClass(/contact-archived-status/)
  const archivedStatusStyle = await archivedStatus.evaluate(element => ({
    backgroundColor: getComputedStyle(element).backgroundColor,
    borderRadius: getComputedStyle(element).borderRadius
  }))
  expect(archivedStatusStyle.backgroundColor).toBe('rgba(0, 0, 0, 0)')
  expect(archivedStatusStyle.borderRadius).toBe('0px')
  await updatedPerson.getByRole('button', { name: 'Reativar' }).click()
  await expect(updatedPerson.getByRole('button', { name: 'Arquivar' })).toBeVisible()
  await attachEvidenceScreenshot(page, testInfo, 'contatos-gerenciados', contacts)

  const csv = 'type,name,email,phone\nPERSON,Contato importado,csv@example.test,\n'
  await contacts.locator('input[type="file"]').setInputFiles({
    name: 'contatos-teste.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(csv, 'utf8')
  })
  await expect(contacts.getByText('Importação concluída: 1 criados, 0 atualizados e 0 sem alteração.', { exact: true })).toBeVisible()
  await expect(contacts.getByText('Contato importado', { exact: true })).toBeVisible()

  const downloadPromise = page.waitForEvent('download')
  await contacts.getByRole('button', { name: 'Exportar CSV' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe('contatos.csv')
  await expect(contacts.getByText('Arquivo CSV exportado.', { exact: true })).toBeVisible()
  await attachEvidenceScreenshot(page, testInfo, 'contatos-csv-importado-exportado', contacts)

  await contacts.getByRole('button', { name: 'Adicionar' }).click()
  await page.setViewportSize({ width: 390, height: 844 })
  await contacts.scrollIntoViewIfNeeded()
  const mobileLayout = await contacts.evaluate(element => ({
    coarsePointer: window.matchMedia('(pointer: coarse)').matches,
    documentFits: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    panelFits: element.scrollWidth <= element.clientWidth,
    rowFits: [...element.querySelectorAll('.contact-row')].every(row => row.scrollWidth <= row.clientWidth),
    touchTargetHeights: [...element.querySelectorAll(
      '.text-action, .quiet-action, .primary-action, .file-action, .contact-field input, .contact-field select, .contact-search input, .contact-archived'
    )].map(target => target.getBoundingClientRect().height),
    checkboxSize: (() => {
      const checkbox = element.querySelector<HTMLInputElement>('.contact-archived input')!
      const bounds = checkbox.getBoundingClientRect()
      return { width: bounds.width, height: bounds.height }
    })()
  }))
  expect(mobileLayout.coarsePointer).toBe(true)
  expect(mobileLayout.documentFits).toBe(true)
  expect(mobileLayout.panelFits).toBe(true)
  expect(mobileLayout.rowFits).toBe(true)
  expect(mobileLayout.touchTargetHeights.length).toBeGreaterThan(0)
  expect(mobileLayout.touchTargetHeights.every(height => height >= 48)).toBe(true)
  expect(mobileLayout.checkboxSize.width).toBeGreaterThanOrEqual(18)
  expect(mobileLayout.checkboxSize.height).toBeGreaterThanOrEqual(18)
})
