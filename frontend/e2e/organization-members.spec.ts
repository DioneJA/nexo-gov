import { expect, test } from '@playwright/test'
import { attachEvidenceScreenshot } from './evidence-fixtures'

test('administrador consulta membros e suspende/reativa acesso pela API da organização', async ({ page }, testInfo) => {
  await page.goto('/servidor/')
  await page.getByLabel('Usuário').fill('servidor')
  await page.getByLabel('Senha').fill('e2e-local-only-password')
  await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()

  const accessSection = page.getByRole('region', { name: 'Membros e acesso' })
  await expect(accessSection).toBeVisible()
  await expect(accessSection.locator('.organization-member-username').filter({ hasText: /^servidor$/ })).toBeVisible()
  await expect(accessSection.locator('.member-access-status')).toHaveCount(2)
  await expect(accessSection.getByText('Não é possível suspender o último administrador ativo.', { exact: true })).toBeVisible()

  const target = accessSection.locator('.organization-member-row').filter({ hasText: 'servidor.teste' })
  await expect(target).toBeVisible()
  await expect(target.getByText('Pessoa de Teste Local', { exact: true })).toBeVisible()
  await expect(target.getByText('Papéis nos setores: Servidor', { exact: true })).toBeVisible()

  const suspendButtonName = 'Suspender acesso de Pessoa de Teste Local'
  const reactivateButtonName = 'Reativar acesso de Pessoa de Teste Local'
  const accessEndpoint = '**/api/v1/staff/organization/members/servidor.teste/access'
  await page.route(accessEndpoint, route => route.fulfill({
    status: 409,
    contentType: 'application/json',
    body: JSON.stringify({})
  }))
  await target.getByRole('button', { name: suspendButtonName }).click()
  await page.getByRole('dialog', { name: 'Suspender acesso?' }).getByRole('button', { name: 'Confirmar suspensão' }).click()
  await expect(accessSection.getByRole('alert')).toHaveText('Não foi possível confirmar a alteração. Atualize a lista de membros antes de realizar outra ação.')
  await expect(accessSection.getByRole('button', { name: 'Atualizar lista de membros' })).toBeVisible()
  await expect(target.getByText('Acesso ativo', { exact: true })).toBeVisible()
  await page.unroute(accessEndpoint)

  await target.getByRole('button', { name: suspendButtonName }).click()
  const confirmation = page.getByRole('dialog', { name: 'Suspender acesso?' })
  await expect(confirmation).toBeVisible()
  await expect(confirmation.getByText(/cadastro, as lotações e o histórico serão preservados/i)).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(confirmation).toBeHidden()
  await expect(target.getByRole('button', { name: suspendButtonName })).toBeVisible()

  await target.getByRole('button', { name: suspendButtonName }).click()
  await page.getByRole('dialog', { name: 'Suspender acesso?' }).getByRole('button', { name: 'Confirmar suspensão' }).click()
  await expect(accessSection.getByRole('status').filter({ hasText: 'suspenso nesta organização' })).toBeVisible()
  await expect(target.getByText('Acesso suspenso', { exact: true })).toBeVisible()

  const suspendedMemberPage = await page.context().newPage()
  await suspendedMemberPage.goto('/servidor/')
  await suspendedMemberPage.getByLabel('Usuário').fill('servidor.teste')
  await suspendedMemberPage.getByLabel('Senha').fill('e2e-local-only-member-password')
  await suspendedMemberPage.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
  await expect(suspendedMemberPage.getByRole('alert')).toContainText('Acesso recusado')

  await target.getByRole('button', { name: reactivateButtonName }).click()
  await expect(accessSection.getByRole('status').filter({ hasText: 'reativado nesta organização' })).toBeVisible()
  await expect(target.getByText('Acesso ativo', { exact: true })).toBeVisible()
  await expect(target.getByRole('button', { name: suspendButtonName })).toBeVisible()

  await suspendedMemberPage.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
  await expect(suspendedMemberPage.getByRole('heading', { name: 'Protocolos' })).toBeVisible()
  await attachEvidenceScreenshot(page, testInfo, 'membro-reativado', target)
})
