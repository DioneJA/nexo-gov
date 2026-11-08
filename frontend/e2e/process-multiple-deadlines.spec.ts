import { expect, request as playwrightRequest, test } from '@playwright/test'
import { attachEvidenceScreenshot } from './evidence-fixtures'

function localDateTime(value: Date) {
  const local = new Date(value.getTime() - value.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

test('process-multiple-deadlines — servidor registra e consulta múltiplos prazos no processo', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 1000 })

  const apiBaseUrl = `http://127.0.0.1:${process.env.NEXOGOV_E2E_API_PORT || '8080'}/api/v1`
  const organizationResponse = await page.request.get(`${apiBaseUrl}/public/organizations`)
  expect(organizationResponse.ok(), await organizationResponse.text()).toBeTruthy()
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

  try {
    const departmentResponse = await adminApi.get('staff/departments')
    expect(departmentResponse.ok(), await departmentResponse.text()).toBeTruthy()
    const departments = await departmentResponse.json() as Array<{ id: string; name: string }>
    const initialDepartment = departments.find(department => department.name === 'Protocolo Geral')
    const analysisDepartment = departments.find(department => department.name === 'Secretaria de Obras')
    expect(initialDepartment).toBeTruthy()
    expect(analysisDepartment).toBeTruthy()

    const suffix = Date.now()
    const subjectKey = `process-deadline-e2e-${suffix}`
    const processTitle = `Processo com prazos fictício ${suffix}`
    const workflowResponse = await adminApi.post('staff/administrative-workflows', {
      data: {
        subjectKey,
        subjectName: `Assunto fictício ${suffix}`,
        expectedVersion: 0,
        idempotencyKey: crypto.randomUUID(),
        steps: [
          { name: 'Recebimento inicial', departmentIds: [initialDepartment!.id], decisionDepartmentIds: [] },
          { name: 'Análise técnica', departmentIds: [analysisDepartment!.id], decisionDepartmentIds: [] }
        ]
      }
    })
    expect(workflowResponse.status(), await workflowResponse.text()).toBe(201)

    const processResponse = await adminApi.post('staff/administrative-processes', {
      data: {
        subjectKey,
        title: processTitle,
        initialDepartmentId: initialDepartment!.id
      }
    })
    expect(processResponse.status(), await processResponse.text()).toBe(201)
    const createdProcess = await processResponse.json() as { id: string }

    await page.goto('/servidor/')
    await page.getByRole('combobox', { name: 'Organização' }).click()
    await page.getByRole('option', { name: 'Município Alfa (demonstração)', exact: true }).click()
    await page.getByLabel('Usuário').fill('servidor')
    await page.getByLabel('Senha').fill('e2e-local-only-password')
    await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
    await expect(page.getByRole('heading', { name: 'Processos administrativos' })).toBeVisible()

    const panel = page.getByRole('region', { name: 'Processos administrativos' })
    await panel.getByLabel('Buscar por título, assunto ou número').fill(processTitle)
    await panel.getByRole('button', { name: 'Buscar', exact: true }).click()
    const processRow = panel.getByRole('list', { name: 'Lista de processos autorizados' })
    await expect(processRow).toContainText(processTitle)
    await panel.getByRole('button', { name: `Abrir processo ${processTitle}` }).click()

    const processDetail = panel.getByRole('region', { name: 'Etapas e histórico do processo' })
    await expect(processDetail).toContainText(processTitle)
    const deadlineSection = processDetail.getByRole('region', { name: 'Prazos do processo' })
    await expect(deadlineSection).toContainText('Nenhum prazo foi registrado neste processo.')

    const earlierDate = new Date(Date.now() + 36 * 60 * 60 * 1000)
    const laterDate = new Date(Date.now() + 72 * 60 * 60 * 1000)
    const earlierTitle = `Análise de documentos ${suffix}`
    const laterTitle = `Retorno ao interessado ${suffix}`

    await deadlineSection.getByLabel('Título do prazo').fill(laterTitle)
    await deadlineSection.getByLabel('Data e hora do prazo').fill(localDateTime(laterDate))
    const firstDeadlineResponsePromise = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith(`/staff/administrative-processes/${createdProcess.id}/deadlines`)
    )
    await deadlineSection.getByRole('button', { name: 'Adicionar prazo' }).click()
    const firstDeadlineResponse = await firstDeadlineResponsePromise
    expect(firstDeadlineResponse.status()).toBe(201)
    const firstDeadlineView = await firstDeadlineResponse.json() as {
      deadlines: Array<{ id: string; title: string; dueAt: string; createdBy: string; createdAt: string }>
    }
    expect(firstDeadlineView.deadlines).toHaveLength(1)
    expect(firstDeadlineView.deadlines[0]).toMatchObject({ title: laterTitle, createdBy: 'servidor' })
    await expect(deadlineSection).toContainText(laterTitle)

    await deadlineSection.getByLabel('Título do prazo').fill(earlierTitle)
    await deadlineSection.getByLabel('Data e hora do prazo').fill(localDateTime(earlierDate))
    const secondDeadlineResponsePromise = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith(`/staff/administrative-processes/${createdProcess.id}/deadlines`)
    )
    await deadlineSection.getByRole('button', { name: 'Adicionar prazo' }).click()
    const secondDeadlineResponse = await secondDeadlineResponsePromise
    expect(secondDeadlineResponse.status()).toBe(201)
    const secondDeadlineView = await secondDeadlineResponse.json() as {
      deadlines: Array<{ id: string; title: string; dueAt: string; createdBy: string; createdAt: string }>
    }
    expect(secondDeadlineView.deadlines).toHaveLength(2)
    expect(secondDeadlineView.deadlines.map(deadline => deadline.title)).toEqual([earlierTitle, laterTitle])
    expect(secondDeadlineView.deadlines.every(deadline => deadline.createdBy === 'servidor' && deadline.createdAt)).toBe(true)
    await expect(deadlineSection).toContainText(earlierTitle)
    await expect(deadlineSection).toContainText(laterTitle)
    await expect(deadlineSection.locator('.workflow-deadline-row').nth(0)).toContainText(earlierTitle)
    await expect(deadlineSection.locator('.workflow-deadline-row').nth(1)).toContainText(laterTitle)
    await expect(deadlineSection).toContainText('Registrado por servidor')

    await page.reload()
    await page.getByRole('combobox', { name: 'Organização' }).click()
    await page.getByRole('option', { name: 'Município Alfa (demonstração)', exact: true }).click()
    await page.getByLabel('Usuário').fill('servidor')
    await page.getByLabel('Senha').fill('e2e-local-only-password')
    await page.getByRole('button', { name: 'Entrar e carregar caixa' }).click()
    await expect(page.getByRole('heading', { name: 'Processos administrativos' })).toBeVisible()
    const reloadedPanel = page.getByRole('region', { name: 'Processos administrativos' })
    await reloadedPanel.getByLabel('Buscar por título, assunto ou número').fill(processTitle)
    await reloadedPanel.getByRole('button', { name: 'Buscar', exact: true }).click()
    await reloadedPanel.getByRole('button', { name: `Abrir processo ${processTitle}` }).click()

    const persistedDetail = reloadedPanel.getByRole('region', { name: 'Etapas e histórico do processo' })
    const persistedDeadlines = persistedDetail.getByRole('region', { name: 'Prazos do processo' })
    await expect(persistedDeadlines.locator('.workflow-deadline-row')).toHaveCount(2)
    await expect(persistedDeadlines.locator('.workflow-deadline-row').nth(0)).toContainText(earlierTitle)
    await expect(persistedDeadlines.locator('.workflow-deadline-row').nth(1)).toContainText(laterTitle)
    await expect(persistedDeadlines).toContainText('Registrado por servidor')
    await attachEvidenceScreenshot(page, testInfo, 'processos-prazos-multiplos', persistedDeadlines)
  } finally {
    await adminApi.dispose()
  }
})
