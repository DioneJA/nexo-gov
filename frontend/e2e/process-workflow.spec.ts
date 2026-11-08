import { expect, request as playwrightRequest, test, type Page, type Route } from '@playwright/test'
import { attachEvidenceScreenshot } from './evidence-fixtures'

async function delayNextStaffRequest(page: Page, method: string, pathSuffix: string) {
  const pattern = `**${pathSuffix}`
  let resolveIntercepted!: () => void
  let releaseRequest!: () => void
  const intercepted = new Promise<void>(resolve => { resolveIntercepted = resolve })
  const released = new Promise<void>(resolve => { releaseRequest = resolve })
  const handler = async (route: Route) => {
    const request = route.request()
    const pathname = new URL(request.url()).pathname
    if (request.method() === method && pathname.endsWith(pathSuffix)) {
      resolveIntercepted()
      await released
      await route.continue()
      await page.unroute(pattern, handler)
      return
    }
    await route.continue()
  }
  await page.route(pattern, handler)
  return { intercepted, release: releaseRequest }
}

async function selectVisibleOption(page: Page, field: ReturnType<Page['getByRole']>, optionName: string) {
  await field.click()
  const menu = page.locator('.q-menu:visible').last()
  await menu.getByRole('option', { name: optionName, exact: true }).click()
  await page.keyboard.press('Escape')
  await expect(menu).toBeHidden()
}

async function expectPortalNavigationBlocked(page: Page) {
  const currentUrl = page.url()
  const links = [page.locator('.brand-lockup'), page.locator('.portal-link')]
  for (const link of links) {
    await expect(link).toHaveJSProperty('tagName', 'SPAN')
    await expect(link).not.toHaveAttribute('role')
    await expect(link).not.toHaveAttribute('aria-disabled')
    await expect(link).not.toHaveAttribute('tabindex')
    await expect(link).not.toHaveAttribute('href')
    await expect(link).toHaveJSProperty('tabIndex', -1)
    await expect(page.getByText('A operação do processo está pendente.', { exact: false })).toBeVisible()
    await link.click()
    await expect(page).toHaveURL(currentUrl)
  }
  const beforeUnloadCanceled = await page.evaluate(() => {
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    return event.defaultPrevented
  })
  expect(beforeUnloadCanceled).toBe(true)
}

async function expectPortalNavigationAvailable(page: Page) {
  for (const link of [page.locator('.brand-lockup'), page.locator('.portal-link')]) {
    await expect(link).toHaveJSProperty('tagName', 'A')
    await expect(link).not.toHaveAttribute('role')
    await expect(link).not.toHaveAttribute('aria-disabled')
    await expect(link).not.toHaveAttribute('tabindex')
    await expect(link).toHaveAttribute('href', '/')
  }
  const beforeUnloadCanceled = await page.evaluate(() => {
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    return event.defaultPrevented
  })
  expect(beforeUnloadCanceled).toBe(false)
}

test('administrador publica workflow versionado e acompanha o snapshot do processo', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/servidor/')
  await page.getByRole('combobox', { name: 'Organização' }).click()
  await page.getByRole('option', { name: 'Município Alfa (demonstração)', exact: true }).click()
  await page.getByLabel('Usuário').fill('servidor')
  await page.getByLabel('Senha').fill('e2e-local-only-password')
  await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
  await expect(page.getByRole('heading', { name: 'Processos administrativos' })).toBeVisible()

  const apiBaseUrl = `http://127.0.0.1:${process.env.NEXOGOV_E2E_API_PORT || '8080'}/api/v1`
  const organizationResponse = await page.request.get(`${apiBaseUrl}/public/organizations`)
  expect(organizationResponse.ok()).toBeTruthy()
  const organizations = await organizationResponse.json() as Array<{ id: string; name: string }>
  const organizationId = organizations.find(organization => organization.name === 'Município Alfa (demonstração)')?.id
  expect(organizationId).toBeTruthy()

  const adminApi = await playwrightRequest.newContext({
    baseURL: `${apiBaseUrl}/`,
    extraHTTPHeaders: {
      Authorization: `Basic ${Buffer.from('servidor:e2e-local-only-password').toString('base64')}`,
      'X-Organization-Id': organizationId!
    }
  })
  const memberApi = await playwrightRequest.newContext({
    baseURL: `${apiBaseUrl}/`,
    extraHTTPHeaders: {
      Authorization: `Basic ${Buffer.from('servidor.teste:e2e-local-only-member-password').toString('base64')}`,
      'X-Organization-Id': organizationId!
    }
  })

  try {
    const departmentResponse = await adminApi.get('staff/departments')
    expect(departmentResponse.ok(), await departmentResponse.text()).toBeTruthy()
    const departments = await departmentResponse.json() as Array<{ id: string; name: string }>
    const intake = departments.find(department => department.name === 'Protocolo Geral')
    const works = departments.find(department => department.name === 'Secretaria de Obras')
    expect(intake).toBeTruthy()
    expect(works).toBeTruthy()

    const suffix = Date.now()
    const subjectKey = `workflow-e2e-${suffix}`
    const panel = page.getByRole('region', { name: 'Processos administrativos' })
    const organizationSelector = page.getByRole('combobox', { name: 'Organização' })
    const signOutButton = page.getByRole('button', { name: 'Sair da área do servidor' })
    const staffAccessButton = page.getByRole('button', { name: 'Entrar e carregar caixa' })
    const refreshStaffButton = page.getByRole('button', { name: 'Atualizar', exact: true })
    await expectPortalNavigationAvailable(page)
    await expect(organizationSelector).toBeEnabled()
    await expect(signOutButton).toBeEnabled()
    await expect(staffAccessButton).toBeEnabled()
    await expect(refreshStaffButton).toBeEnabled()
    await panel.getByLabel('Identificador do assunto').fill(subjectKey)
    await panel.getByRole('button', { name: 'Carregar fluxo' }).click()
    await expect(panel.locator('.workflow-editor').getByRole('status')).toContainText('Ainda não há fluxo publicado')

    await panel.getByLabel('Nome do assunto').fill(`Licença fictícia ${suffix}`)
    await panel.getByLabel('Nome da etapa 1').fill('Recebimento inicial')
    await selectVisibleOption(page, panel.getByLabel('Setores da etapa 1'), 'Protocolo Geral')
    await selectVisibleOption(page, panel.getByLabel('Setores decisores 1'), 'Protocolo Geral')
    const secondStageName = panel.getByLabel('Nome da etapa 2')
    await secondStageName.fill('Análise técnica')
    await selectVisibleOption(page, panel.getByLabel('Setores da etapa 2'), 'Secretaria de Obras')
    await expect(secondStageName).toHaveValue('Análise técnica')

    const versionOnePromise = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/staff/administrative-workflows')
    )
    await panel.getByRole('button', { name: 'Publicar versão 1' }).click()
    const versionOneResponse = await versionOnePromise
    expect(versionOneResponse.status()).toBe(201)
    const versionOne = await versionOneResponse.json() as {
      steps: Array<{ name: string; departments: Array<{ id: string; name: string; canDecide: boolean }> }>
    }
    expect(versionOne.steps[1].name).toBe('Análise técnica')
    expect(versionOne.steps[0].departments).toContainEqual(expect.objectContaining({
      id: intake!.id,
      canDecide: true
    }))
    await expect(panel.locator('.workflow-editor').getByRole('status')).toContainText('Versão 1 publicada')
    await attachEvidenceScreenshot(page, testInfo, 'workflow-publicado-v1', panel.locator('.workflow-editor'))
    await attachEvidenceScreenshot(page, testInfo, 'workflow-decisores-configurados', panel.locator('.workflow-editor'))

    await panel.getByLabel('Título do processo').fill(`Processo fictício ${suffix}`)
    const subjectKeyInput = panel.getByLabel('Identificador do assunto')
    const delayedOpen = await delayNextStaffRequest(page, 'POST', '/staff/administrative-processes')
    const openProcessPromise = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/staff/administrative-processes')
    )
    await panel.getByRole('button', { name: 'Abrir processo', exact: true }).click()
    await delayedOpen.intercepted
    await expect(subjectKeyInput).toBeDisabled()
    await expect(panel.getByRole('button', { name: 'Carregar fluxo' })).toBeDisabled()
    await expect(organizationSelector).toBeDisabled()
    await expect(signOutButton).toBeDisabled()
    await expect(staffAccessButton).toBeDisabled()
    await expect(refreshStaffButton).toBeDisabled()
    await expect(page.getByText('A operação do processo está pendente.', { exact: false })).toBeVisible()
    await expectPortalNavigationBlocked(page)
    delayedOpen.release()
    const openProcessResponse = await openProcessPromise
    expect(openProcessResponse.status()).toBe(201)
    await expect(subjectKeyInput).toBeEnabled()
    await expect(organizationSelector).toBeEnabled()
    await expect(signOutButton).toBeEnabled()
    await expect(staffAccessButton).toBeEnabled()
    await expect(refreshStaffButton).toBeEnabled()
    await expectPortalNavigationAvailable(page)
    const createdProcess = await openProcessResponse.json() as { id: string; workflowVersion: number }
    expect(createdProcess.workflowVersion).toBe(1)
    await expect(panel.getByRole('region', { name: 'Etapas e histórico do processo' })).toContainText('versão 1')

    await panel.getByLabel('Nome da etapa 1').fill('Recebimento revisado v2')
    await panel.getByLabel('Nome da etapa 2').fill('Análise técnica revisada v2')
    const versionTwoPromise = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/staff/administrative-workflows')
    )
    await panel.getByRole('button', { name: 'Publicar versão 2' }).click()
    const versionTwoResponse = await versionTwoPromise
    expect(versionTwoResponse.status()).toBe(201)
    await expect(panel.locator('.workflow-editor').getByRole('status')).toContainText('Versão 2 publicada')
    await attachEvidenceScreenshot(page, testInfo, 'workflow-publicado-v2', panel.locator('.workflow-editor'))

    const record = panel.getByRole('region', { name: 'Etapas e histórico do processo' })
    const delayedRefresh = await delayNextStaffRequest(page, 'GET', `/staff/administrative-processes/${createdProcess.id}`)
    const refreshResponsePromise = page.waitForResponse(response =>
      response.request().method() === 'GET'
      && new URL(response.url()).pathname.endsWith(`/staff/administrative-processes/${createdProcess.id}`)
    )
    await panel.getByRole('button', { name: 'Atualizar processo' }).click()
    await delayedRefresh.intercepted
    await expect(subjectKeyInput).toBeDisabled()
    await expect(organizationSelector).toBeDisabled()
    await expect(signOutButton).toBeDisabled()
    await expect(staffAccessButton).toBeDisabled()
    await expect(refreshStaffButton).toBeDisabled()
    await expectPortalNavigationBlocked(page)
    delayedRefresh.release()
    const refreshResponse = await refreshResponsePromise
    expect(refreshResponse.status()).toBe(200)
    await expect(subjectKeyInput).toBeEnabled()
    await expect(organizationSelector).toBeEnabled()
    await expect(signOutButton).toBeEnabled()
    await expect(staffAccessButton).toBeEnabled()
    await expect(refreshStaffButton).toBeEnabled()
    await expectPortalNavigationAvailable(page)
    await expect(record).toContainText('versão 1')
    await expect(record).toContainText('Recebimento inicial')
    await expect(record).toContainText('Análise técnica')
    await expect(record).not.toContainText('revisada v2')
    await attachEvidenceScreenshot(page, testInfo, 'processo-snapshot-v1', record)

    const delayedDecision = await delayNextStaffRequest(
      page,
      'POST',
      `/staff/administrative-processes/${createdProcess.id}/decision`
    )
    const decisionPromise = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith(`/staff/administrative-processes/${createdProcess.id}/decision`)
    )
    await panel.getByRole('button', { name: 'Marcar como Deferido' }).click()
    await delayedDecision.intercepted
    await expect(subjectKeyInput).toBeDisabled()
    await expect(organizationSelector).toBeDisabled()
    await expect(signOutButton).toBeDisabled()
    await expect(staffAccessButton).toBeDisabled()
    await expect(refreshStaffButton).toBeDisabled()
    await expectPortalNavigationBlocked(page)
    delayedDecision.release()
    const decisionResponse = await decisionPromise
    expect(decisionResponse.status()).toBe(200)
    await expect(subjectKeyInput).toBeEnabled()
    await expect(organizationSelector).toBeEnabled()
    await expect(signOutButton).toBeEnabled()
    await expect(staffAccessButton).toBeEnabled()
    await expect(refreshStaffButton).toBeEnabled()
    await expectPortalNavigationAvailable(page)
    const decidedProcess = await decisionResponse.json() as {
      status: string
      decisionOutcome: string
      decisionActor: string
      decisionAt: string
      currentStepOrder: number
      events: Array<{ action: string; decisionOutcome: string | null }>
    }
    expect(decidedProcess).toMatchObject({
      status: 'ACTIVE',
      decisionOutcome: 'DEFERIDO',
      decisionActor: 'servidor',
      currentStepOrder: 1
    })
    expect(decidedProcess.decisionAt).toBeTruthy()
    expect(decidedProcess.events).toHaveLength(2)
    expect(decidedProcess.events[1]).toMatchObject({ action: 'DECIDED', decisionOutcome: 'DEFERIDO' })
    await expect(record).toContainText('Decisão do processo')
    await expect(record).toContainText('Deferido')
    await expect(record).toContainText('não altera o andamento nem encerra o processo')
    await attachEvidenceScreenshot(page, testInfo, 'processo-decisao-registrada', record)

    const skippedStepResponse = await adminApi.post(`staff/administrative-processes/${createdProcess.id}/advance`, {
      data: { toStepOrder: 3, destinationDepartmentId: works!.id }
    })
    expect(skippedStepResponse.status()).toBe(409)
    const unchangedAfterSkip = await skippedStepResponse.text()
    expect(unchangedAfterSkip).toContain('imediatamente seguinte')
    const afterSkip = await (await adminApi.get(`staff/administrative-processes/${createdProcess.id}`)).json() as {
      currentStepOrder: number
      events: Array<unknown>
      workflowVersion: number
    }
    expect(afterSkip).toMatchObject({ currentStepOrder: 1, workflowVersion: 1 })
    expect(afterSkip.events).toHaveLength(2)

    await selectVisibleOption(page, panel.getByRole('combobox', { name: 'Setor de destino' }), 'Secretaria de Obras')
    const advancePromise = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith(`/staff/administrative-processes/${createdProcess.id}/advance`)
    )
    const delayedAdvance = await delayNextStaffRequest(page, 'POST', `/staff/administrative-processes/${createdProcess.id}/advance`)
    await panel.getByRole('button', { name: 'Avançar para Análise técnica' }).click()
    await delayedAdvance.intercepted
    await expect(subjectKeyInput).toBeDisabled()
    await expect(organizationSelector).toBeDisabled()
    await expect(signOutButton).toBeDisabled()
    await expect(staffAccessButton).toBeDisabled()
    await expect(refreshStaffButton).toBeDisabled()
    await expectPortalNavigationBlocked(page)
    delayedAdvance.release()
    const advanceResponse = await advancePromise
    expect(advanceResponse.status()).toBe(200)
    await expect(subjectKeyInput).toBeEnabled()
    await expect(organizationSelector).toBeEnabled()
    await expect(signOutButton).toBeEnabled()
    await expect(staffAccessButton).toBeEnabled()
    await expect(refreshStaffButton).toBeEnabled()
    await expectPortalNavigationAvailable(page)
    await expect(record).toContainText('Etapa atual')
    await expect(record).toContainText('Análise técnica')
    await attachEvidenceScreenshot(page, testInfo, 'processo-avancado-para-etapa-final', record)

    const noAssignmentResponse = await memberApi.post(`staff/administrative-processes/${createdProcess.id}/advance`, {
      data: { toStepOrder: 3, destinationDepartmentId: intake!.id }
    })
    expect(noAssignmentResponse.status()).toBe(403)
    const afterDeniedMembership = await (await adminApi.get(`staff/administrative-processes/${createdProcess.id}`)).json() as {
      currentStepOrder: number
      currentDepartmentId: string
      events: Array<unknown>
      workflowVersion: number
    }
    expect(afterDeniedMembership).toMatchObject({
      currentStepOrder: 2,
      currentDepartmentId: works!.id,
      workflowVersion: 1
    })
    expect(afterDeniedMembership.events).toHaveLength(3)
    await expect(record).toContainText('chegou à última etapa do fluxo; isso não significa que foi encerrado')

    await page.setViewportSize({ width: 390, height: 844 })
    await expect(record).toBeVisible()
    const panelHasHorizontalOverflow = await panel.evaluate(element => element.scrollWidth > element.clientWidth)
    expect(panelHasHorizontalOverflow).toBe(false)

    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.reload()
    await page.getByRole('combobox', { name: 'Organização' }).click()
    await page.getByRole('option', { name: 'Município Alfa (demonstração)', exact: true }).click()
    await page.getByLabel('Usuário').fill('servidor')
    await page.getByLabel('Senha').fill('e2e-local-only-password')
    await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
    await expect(page.getByRole('heading', { name: 'Processos administrativos' })).toBeVisible()

    const persistedList = panel.getByRole('list', { name: 'Lista de processos autorizados' })
    await expect(panel.getByLabel('Buscar por título ou assunto')).toBeEnabled()
    await panel.getByLabel('Buscar por título ou assunto').fill(`Processo fictício ${suffix}`)
    const listResponsePromise = page.waitForResponse(response =>
      response.request().method() === 'GET'
      && new URL(response.url()).pathname.endsWith('/staff/administrative-processes')
      && new URL(response.url()).searchParams.get('q') === `Processo fictício ${suffix}`
    )
    await panel.getByRole('button', { name: 'Buscar', exact: true }).click()
    const listResponse = await listResponsePromise
    expect(listResponse.status()).toBe(200)
    const listedPage = await listResponse.json() as {
      page: number
      size: number
      items: Array<{ id: string; title: string; currentStepOrder: number; workflowVersion: number; decisionOutcome: string | null }>
    }
    expect(listedPage).toMatchObject({ page: 0, size: 25 })
    expect(listedPage.items).toContainEqual(expect.objectContaining({
      id: createdProcess.id,
      title: `Processo fictício ${suffix}`,
      currentStepOrder: 2,
      workflowVersion: 1,
      decisionOutcome: 'DEFERIDO'
    }))
    await expect(persistedList).toContainText(`Processo fictício ${suffix}`)
    await attachEvidenceScreenshot(page, testInfo, 'processo-listado-apos-reinicio', persistedList)

    const reopenedDetailPromise = page.waitForResponse(response =>
      response.request().method() === 'GET'
      && new URL(response.url()).pathname.endsWith(`/staff/administrative-processes/${createdProcess.id}`)
    )
    await panel.getByRole('button', { name: `Abrir processo Processo fictício ${suffix}` }).click()
    const reopenedDetailResponse = await reopenedDetailPromise
    expect(reopenedDetailResponse.status()).toBe(200)
    const reopenedProcess = await reopenedDetailResponse.json() as {
      status: string
      decisionOutcome: string | null
      events: Array<{ action: string; decisionOutcome: string | null }>
    }
    expect(reopenedProcess).toMatchObject({ status: 'ACTIVE', decisionOutcome: 'DEFERIDO' })
    expect(reopenedProcess.events.filter(event => event.action === 'DECIDED'))
      .toEqual([expect.objectContaining({ decisionOutcome: 'DEFERIDO' })])
    await expect(record.locator('.workflow-process-notice')).toContainText('Processo recuperado. Continua na versão 1 do fluxo.')
    await expect(record).toContainText('Análise técnica')
    await expect(record).toContainText('Decisão do processo')
    await expect(record).toContainText('Deferido')
    await expect(record).toContainText('Histórico registrado')
    await attachEvidenceScreenshot(page, testInfo, 'detalhe-reaberto-da-listagem', record)
    await attachEvidenceScreenshot(page, testInfo, 'processo-decisao-persistida', record)

    await page.locator('.portal-link').click()
    await expect(page).toHaveURL(/\/$/)
  } finally {
    await Promise.all([adminApi.dispose(), memberApi.dispose()])
  }
})
