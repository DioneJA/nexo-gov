import { expect, test } from '@playwright/test'
import { attachEvidenceScreenshot } from './evidence-fixtures'

test('administrador mantém contatos por tipo e importa/exporta CSV', async ({ page }, testInfo) => {
  await page.goto('/servidor/')
  await page.getByLabel('Usuário').fill('servidor')
  await page.getByLabel('Senha').fill('e2e-local-only-password')
  await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()

  const contacts = page.getByRole('region', { name: 'Contatos da organização' })
  await expect(contacts).toBeVisible()
  const contactsWidth = await contacts.evaluate(element => element.getBoundingClientRect().width)
  const viewportWidth = await page.evaluate(() => window.innerWidth)
  expect(contactsWidth).toBeGreaterThan(viewportWidth * 0.85)

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

  const search = contacts.getByRole('searchbox', { name: 'Buscar contato' })
  await search.fill('Financeiro')
  await expect(contacts.locator('.contact-row')).toHaveCount(1)
  await expect(contacts.getByText('Financeiro', { exact: true })).toBeVisible()
  await search.fill('')
  await expect(contacts.locator('.contact-row')).toHaveCount(3)

  await expect(contacts.getByText('Pessoa física', { exact: true })).toBeVisible()
  await expect(contacts.getByText('Setor da prefeitura', { exact: true })).toBeVisible()
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
  await expect(updatedPerson.getByText('Arquivado', { exact: true })).toBeVisible()
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
})
