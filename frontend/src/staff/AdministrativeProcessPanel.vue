<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { organizationHeaders, staffApi, type Department } from '../api/staff'

interface WorkflowDepartment {
  id: string
  name: string
  canDecide: boolean
}

interface WorkflowStep {
  order: number
  name: string
  departments: WorkflowDepartment[]
}

interface WorkflowView {
  id: string
  subjectKey: string
  subjectName: string
  version: number
  createdAt: string
  steps: WorkflowStep[]
}

interface WorkflowStepDraft {
  key: string
  name: string
  departmentIds: string[]
  decisionDepartmentIds: string[]
}

interface ProcessEvent {
  id: number
  actor: string
  action: string
  fromStepOrder: number | null
  toStepOrder: number
  fromDepartmentId: string | null
  toDepartmentId: string
  decisionOutcome: 'DEFERIDO' | 'INDEFERIDO' | null
  occurredAt: string
}

interface ProcessView {
  id: string
  subjectKey: string
  subjectName: string
  workflowId: string
  workflowVersion: number
  title: string
  currentStepOrder: number
  currentStepName: string
  currentDepartmentId: string
  currentDepartmentName: string
  status: string
  decisionOutcome: 'DEFERIDO' | 'INDEFERIDO' | null
  decisionActor: string | null
  decisionAt: string | null
  createdAt: string
  steps: WorkflowStep[]
  events: ProcessEvent[]
}

interface ProcessSummary {
  id: string
  subjectKey: string
  subjectName: string
  workflowId: string
  workflowVersion: number
  title: string
  currentStepOrder: number
  currentStepName: string
  currentDepartmentId: string
  currentDepartmentName: string
  status: string
  decisionOutcome: 'DEFERIDO' | 'INDEFERIDO' | null
  createdAt: string
}

interface ProcessPageView {
  items: ProcessSummary[]
  page: number
  size: number
  hasNext: boolean
}

const props = defineProps<{
  organizationId: string
  departments: Department[]
  canManageDefinitions: boolean
}>()
const emit = defineEmits<{
  (event: 'process-busy-change', busy: boolean): void
}>()

const subjectKey = ref('')
const subjectName = ref('')
const workflow = ref<WorkflowView | null>(null)
const steps = ref<WorkflowStepDraft[]>([makeStep(1), makeStep(2)])
const loadedSubjectKey = ref('')
const loadingWorkflow = ref(false)
const publishingWorkflow = ref(false)
const workflowNotice = ref('')
const workflowError = ref('')
const processTitle = ref('')
const initialDepartmentId = ref('')
const processId = ref('')
const process = ref<ProcessView | null>(null)
const loadingProcess = ref(false)
const processAction = ref(false)
const processNotice = ref('')
const processError = ref('')
const nextDepartmentId = ref('')
const processList = ref<ProcessSummary[]>([])
const processListQuery = ref('')
const processListPage = ref(0)
const processListHasNext = ref(false)
const processListLoading = ref(false)
const processListError = ref('')
const processListNotice = ref('')
let processListGeneration = 0
let contextGeneration = 0
const processPageSize = 25

function makeStep(order: number): WorkflowStepDraft {
  return { key: crypto.randomUUID(), name: `Etapa ${order}`, departmentIds: [], decisionDepartmentIds: [] }
}

const departmentOptions = computed(() => props.departments.map(department => ({
  label: department.name,
  value: department.id
})))
const activeDepartmentIds = computed(() => new Set(props.departments.map(department => department.id)))
const workflowVersion = computed(() => workflow.value?.version ?? 0)
const processBusy = computed(() => processAction.value || loadingProcess.value)
watch(processBusy, busy => emit('process-busy-change', busy), { immediate: true, flush: 'sync' })
const workflowDraftIsValid = computed(() => {
  const key = subjectKey.value.trim()
  return /^[a-z0-9][a-z0-9._-]{0,79}$/.test(key)
    && !!subjectName.value.trim()
    && steps.value.length >= 2
    && steps.value.every(step => !!step.name.trim() && step.departmentIds.length > 0
      && step.decisionDepartmentIds.every(departmentId => step.departmentIds.includes(departmentId)))
})
const firstWorkflowStep = computed(() => workflow.value?.steps.find(step => step.order === 1) || null)
const initialDepartmentOptions = computed(() => (firstWorkflowStep.value?.departments || [])
  .filter(department => activeDepartmentIds.value.has(department.id))
  .map(department => ({ label: department.name, value: department.id })))
const nextStep = computed(() => process.value?.steps.find(step => step.order === process.value!.currentStepOrder + 1) || null)
const nextDepartmentOptions = computed(() => (nextStep.value?.departments || [])
  .filter(department => activeDepartmentIds.value.has(department.id))
  .map(department => ({ label: department.name, value: department.id })))
const processStepsOrdered = computed(() => [...(process.value?.steps || [])].sort((left, right) => left.order - right.order))

onMounted(() => { void loadProcessList() })

watch(() => props.organizationId, () => {
  contextGeneration++
  processListGeneration++
  loadingWorkflow.value = false
  publishingWorkflow.value = false
  loadingProcess.value = false
  processAction.value = false
  workflow.value = null
  steps.value = [makeStep(1), makeStep(2)]
  loadedSubjectKey.value = ''
  subjectKey.value = ''
  subjectName.value = ''
  workflowNotice.value = ''
  workflowError.value = ''
  processTitle.value = ''
  initialDepartmentId.value = ''
  processId.value = ''
  process.value = null
  processNotice.value = ''
  processError.value = ''
  nextDepartmentId.value = ''
  processList.value = []
  processListQuery.value = ''
  processListPage.value = 0
  processListHasNext.value = false
  processListLoading.value = false
  processListError.value = ''
  processListNotice.value = ''
}, { flush: 'sync' })

watch(subjectKey, value => {
  const normalized = value.trim()
  if (processBusy.value) {
    // Keep the request context stable until a potentially committed mutation
    // response has been applied to the record shown in the panel.
    subjectKey.value = loadedSubjectKey.value || process.value?.subjectKey || ''
    return
  }
  if (!loadedSubjectKey.value || normalized === loadedSubjectKey.value) return
  contextGeneration++
  loadingWorkflow.value = false
  publishingWorkflow.value = false
  loadingProcess.value = false
  processAction.value = false
  loadedSubjectKey.value = ''
  workflow.value = null
  steps.value = [makeStep(1), makeStep(2)]
  processTitle.value = ''
  initialDepartmentId.value = ''
  processId.value = ''
  process.value = null
  workflowNotice.value = ''
  workflowError.value = ''
  processNotice.value = ''
  processError.value = ''
}, { flush: 'sync' })

function clearWorkflowMessages() {
  workflowNotice.value = ''
  workflowError.value = ''
}

function clearProcessMessages() {
  processNotice.value = ''
  processError.value = ''
}

function errorMessage(error: any, fallback: string) {
  if (error?.response?.status === 401 || error?.response?.status === 403)
    return 'Seu acesso ou sua lotação não permite esta ação nesta organização.'
  if (error?.response?.status === 404)
    return 'O registro não foi encontrado nesta organização. Confira o identificador e tente novamente.'
  return error?.response?.data?.message || fallback
}

async function loadProcessList(nextPage = false) {
  if (nextPage && (processListLoading.value || !processListHasNext.value)) return
  const organizationId = props.organizationId
  const generation = ++processListGeneration
  const page = nextPage ? processListPage.value + 1 : 0
  const query = processListQuery.value.trim()
  if (!nextPage) {
    processList.value = []
    processListPage.value = 0
    processListHasNext.value = false
  }
  processListLoading.value = true
  processListError.value = ''
  processListNotice.value = ''
  try {
    const response = await staffApi.get<ProcessPageView>('/staff/administrative-processes', {
      params: { page, size: processPageSize, q: query || undefined },
      headers: organizationHeaders(organizationId)
    })
    if (generation !== processListGeneration || organizationId !== props.organizationId) return
    processList.value = nextPage ? [...processList.value, ...response.data.items] : response.data.items
    processListPage.value = response.data.page
    processListHasNext.value = response.data.hasNext
    processListNotice.value = query
      ? `${processList.value.length} processo(s) autorizado(s) encontrado(s) nesta busca.`
      : `${processList.value.length} processo(s) autorizado(s) carregado(s).`
  } catch (error: any) {
    if (generation !== processListGeneration || organizationId !== props.organizationId) return
    processListError.value = errorMessage(error, 'Não foi possível consultar os processos autorizados. Tente novamente.')
  } finally {
    if (generation === processListGeneration && organizationId === props.organizationId)
      processListLoading.value = false
  }
}

async function openListedProcess(summary: ProcessSummary) {
  if (processBusy.value) return
  const generation = contextGeneration
  const organizationId = props.organizationId
  processId.value = summary.id
  process.value = null
  loadingProcess.value = true
  clearProcessMessages()
  try {
    const response = await staffApi.get<ProcessView>(
      `/staff/administrative-processes/${encodeURIComponent(summary.id)}`,
      { headers: organizationHeaders(organizationId) }
    )
    if (generation !== contextGeneration || organizationId !== props.organizationId) return
    applyProcess(response.data)
    processNotice.value = `Processo recuperado. Continua na versão ${response.data.workflowVersion} do fluxo.`
  } catch (error: any) {
    if (generation === contextGeneration && organizationId === props.organizationId)
      processError.value = errorMessage(error, 'Não foi possível abrir este processo nesta organização.')
  } finally {
    if (generation === contextGeneration && organizationId === props.organizationId)
      loadingProcess.value = false
  }
}

function applyWorkflow(value: WorkflowView) {
  workflow.value = value
  loadedSubjectKey.value = value.subjectKey
  subjectKey.value = value.subjectKey
  subjectName.value = value.subjectName
  steps.value = value.steps.map(step => ({
    key: crypto.randomUUID(),
    name: step.name,
    departmentIds: step.departments.map(department => department.id),
    decisionDepartmentIds: step.departments.filter(department => department.canDecide).map(department => department.id)
  }))
  initialDepartmentId.value = firstWorkflowStep.value?.departments.find(department =>
    activeDepartmentIds.value.has(department.id))?.id || ''
}

async function loadWorkflow() {
  const key = subjectKey.value.trim()
  if (!/^[a-z0-9][a-z0-9._-]{0,79}$/.test(key)) {
    workflowError.value = 'Use de 1 a 80 caracteres: letras minúsculas, números, ponto, hífen ou sublinhado.'
    return
  }
  const generation = ++contextGeneration
  const organizationId = props.organizationId
  loadingWorkflow.value = true
  clearWorkflowMessages()
  process.value = null
  processId.value = ''
  processTitle.value = ''
  processError.value = ''
  processNotice.value = ''
  try {
    const response = await staffApi.get<WorkflowView>(
      `/staff/administrative-workflows/${encodeURIComponent(key)}`,
      { headers: organizationHeaders(organizationId) }
    )
    if (generation !== contextGeneration || organizationId !== props.organizationId) return
    applyWorkflow(response.data)
    workflowNotice.value = `Versão ${response.data.version} carregada. Processos já abertos mantêm sua própria versão.`
  } catch (error: any) {
    if (generation !== contextGeneration || organizationId !== props.organizationId) return
    if (error?.response?.status === 404) {
      workflow.value = null
      loadedSubjectKey.value = key
      subjectName.value = ''
      steps.value = [makeStep(1), makeStep(2)]
      initialDepartmentId.value = ''
      workflowNotice.value = 'Ainda não há fluxo publicado para este identificador. Configure as etapas e publique a primeira versão.'
    } else {
      workflowError.value = errorMessage(error, 'Não foi possível carregar o fluxo. Tente novamente.')
    }
  } finally {
    if (generation === contextGeneration) loadingWorkflow.value = false
  }
}

function addStep() {
  if (steps.value.length >= 30) return
  steps.value.push(makeStep(steps.value.length + 1))
}

function removeStep(index: number) {
  if (steps.value.length <= 2) return
  steps.value.splice(index, 1)
  steps.value = steps.value.map((step, stepIndex) => ({ ...step, name: step.name || `Etapa ${stepIndex + 1}` }))
}

function setStepDepartments(step: WorkflowStepDraft, departmentIds: string[]) {
  step.departmentIds = departmentIds
  step.decisionDepartmentIds = step.decisionDepartmentIds.filter(departmentId => departmentIds.includes(departmentId))
}

async function publishWorkflow() {
  if (!workflowDraftIsValid.value) {
    workflowError.value = 'Preencha o assunto, dê nome às etapas e escolha ao menos um setor em cada etapa.'
    return
  }
  const generation = contextGeneration
  const organizationId = props.organizationId
  const key = subjectKey.value.trim()
  publishingWorkflow.value = true
  clearWorkflowMessages()
  try {
    const response = await staffApi.post<WorkflowView>('/staff/administrative-workflows', {
      subjectKey: key,
      subjectName: subjectName.value.trim(),
      expectedVersion: workflowVersion.value,
      idempotencyKey: crypto.randomUUID(),
      steps: steps.value.map(step => ({
        name: step.name.trim(),
        departmentIds: [...step.departmentIds],
        decisionDepartmentIds: [...step.decisionDepartmentIds].filter(id => step.departmentIds.includes(id))
      }))
    }, { headers: organizationHeaders(organizationId) })
    if (generation !== contextGeneration || organizationId !== props.organizationId) return
    applyWorkflow(response.data)
    workflowNotice.value = `Versão ${response.data.version} publicada. Novos processos usarão esta definição; os existentes preservam seu snapshot.`
  } catch (error: any) {
    if (generation === contextGeneration && organizationId === props.organizationId)
      workflowError.value = errorMessage(error, 'Não foi possível publicar esta versão do fluxo.')
  } finally {
    if (generation === contextGeneration) publishingWorkflow.value = false
  }
}

function processDepartmentName(departmentId: string | null) {
  if (!departmentId || !process.value) return '—'
  for (const step of process.value.steps) {
    const department = step.departments.find(item => item.id === departmentId)
    if (department) return department.name
  }
  return 'Setor não disponível no snapshot'
}

function applyProcess(value: ProcessView) {
  process.value = value
  processId.value = value.id
  nextDepartmentId.value = nextDepartmentOptions.value[0]?.value || ''
}

async function openProcess() {
  const currentWorkflow = workflow.value
  if (!currentWorkflow || loadedSubjectKey.value !== subjectKey.value.trim()) {
    processError.value = 'Carregue ou publique o fluxo deste assunto antes de abrir um processo.'
    return
  }
  if (!processTitle.value.trim() || !initialDepartmentId.value) {
    processError.value = 'Informe o título e escolha um setor pertencente à primeira etapa ativa.'
    return
  }
  const generation = contextGeneration
  const organizationId = props.organizationId
  processAction.value = true
  clearProcessMessages()
  try {
    const response = await staffApi.post<ProcessView>('/staff/administrative-processes', {
      subjectKey: currentWorkflow.subjectKey,
      title: processTitle.value.trim(),
      initialDepartmentId: initialDepartmentId.value
    }, { headers: organizationHeaders(organizationId) })
    if (generation !== contextGeneration || organizationId !== props.organizationId) return
    applyProcess(response.data)
    processNotice.value = `Processo aberto com snapshot da versão ${response.data.workflowVersion}.`
    void loadProcessList()
  } catch (error: any) {
    if (generation === contextGeneration && organizationId === props.organizationId)
      processError.value = errorMessage(error, 'Não foi possível abrir o processo. Confira sua lotação no setor inicial.')
  } finally {
    if (generation === contextGeneration) processAction.value = false
  }
}

async function reloadProcess() {
  if (!processId.value) return
  const generation = contextGeneration
  const organizationId = props.organizationId
  loadingProcess.value = true
  clearProcessMessages()
  try {
    const response = await staffApi.get<ProcessView>(
      `/staff/administrative-processes/${encodeURIComponent(processId.value)}`,
      { headers: organizationHeaders(organizationId) }
    )
    if (generation !== contextGeneration || organizationId !== props.organizationId) return
    applyProcess(response.data)
    processNotice.value = `Processo atualizado. Continua na versão ${response.data.workflowVersion} do fluxo.`
  } catch (error: any) {
    if (generation === contextGeneration && organizationId === props.organizationId)
      processError.value = errorMessage(error, 'Não foi possível atualizar o processo.')
  } finally {
    if (generation === contextGeneration) loadingProcess.value = false
  }
}

async function advanceProcess() {
  if (!process.value || !nextStep.value) return
  if (!nextDepartmentId.value || !nextDepartmentOptions.value.some(option => option.value === nextDepartmentId.value)) {
    processError.value = 'Escolha um setor ativo da etapa seguinte do snapshot do processo.'
    return
  }
  const generation = contextGeneration
  const organizationId = props.organizationId
  const targetStepOrder = nextStep.value.order
  processAction.value = true
  clearProcessMessages()
  try {
    const response = await staffApi.post<ProcessView>(
      `/staff/administrative-processes/${encodeURIComponent(process.value.id)}/advance`,
      { toStepOrder: targetStepOrder, destinationDepartmentId: nextDepartmentId.value },
      { headers: organizationHeaders(organizationId) }
    )
    if (generation !== contextGeneration || organizationId !== props.organizationId) return
    applyProcess(response.data)
    processNotice.value = `Processo avançou para “${response.data.currentStepName}”.`
    void loadProcessList()
  } catch (error: any) {
    if (generation === contextGeneration && organizationId === props.organizationId)
      processError.value = errorMessage(error, 'Não foi possível avançar o processo. Confira sua lotação e o estado atual.')
  } finally {
    if (generation === contextGeneration) processAction.value = false
  }
}

const currentDecisionDepartment = computed(() => process.value?.steps
  .find(step => step.order === process.value?.currentStepOrder)
  ?.departments.find(department => department.id === process.value?.currentDepartmentId) || null)

async function decideProcess(outcome: 'DEFERIDO' | 'INDEFERIDO') {
  if (!process.value || !currentDecisionDepartment.value?.canDecide || process.value.status !== 'ACTIVE') return
  const generation = contextGeneration
  const organizationId = props.organizationId
  processAction.value = true
  clearProcessMessages()
  try {
    const response = await staffApi.post<ProcessView>(
      `/staff/administrative-processes/${encodeURIComponent(process.value.id)}/decision`,
      { outcome },
      { headers: organizationHeaders(organizationId) }
    )
    if (generation !== contextGeneration || organizationId !== props.organizationId) return
    applyProcess(response.data)
    processNotice.value = `Decisão ${outcome === 'DEFERIDO' ? 'Deferido' : 'Indeferido'} registrada. O processo continua em andamento.`
    void loadProcessList()
  } catch (error: any) {
    if (generation === contextGeneration && organizationId === props.organizationId)
      processError.value = errorMessage(error, 'Não foi possível registrar a decisão nesta etapa.')
  } finally {
    if (generation === contextGeneration) processAction.value = false
  }
}

function eventActionLabel(action: string) {
  if (action === 'OPENED') return 'Processo aberto'
  if (action === 'ADVANCED') return 'Processo encaminhado'
  if (action === 'DECIDED') return 'Decisão registrada'
  return action
}

function decisionLabel(outcome: 'DEFERIDO' | 'INDEFERIDO' | null) {
  return outcome === 'DEFERIDO' ? 'Deferido' : outcome === 'INDEFERIDO' ? 'Indeferido' : 'Sem decisão'
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}
</script>

<template>
  <section class="administrative-process-panel" aria-labelledby="administrative-process-title">
    <header class="administrative-process-heading">
      <div>
        <h2 id="administrative-process-title" class="section-heading">Processos administrativos</h2>
        <p class="section-subheading">{{ canManageDefinitions ? 'Publique fluxos por assunto e acompanhe cada processo pela sua definição preservada.' : 'Consulte os processos disponíveis para sua organização e lotação.' }}</p>
      </div>
      <span v-if="workflow" class="workflow-version-label">Versão ativa {{ workflow.version }}</span>
    </header>

    <div v-if="canManageDefinitions" class="administrative-process-grid">
      <section class="workflow-editor" aria-labelledby="workflow-editor-title">
        <h3 id="workflow-editor-title" class="subsection-heading">Definição do fluxo</h3>
        <p class="member-access-note">Cada publicação cria uma versão. Processos já abertos continuam seguindo as etapas e os nomes registrados no seu snapshot.</p>

        <div class="workflow-lookup-row">
          <q-input
            v-model="subjectKey"
            label="Identificador do assunto"
            hint="Use letras minúsculas, números, ponto, hífen ou sublinhado."
            outlined dense maxlength="80"
            :disable="loadingWorkflow || publishingWorkflow || processBusy"
            @keyup.enter="loadWorkflow"
          />
          <q-btn
            color="primary" outline no-caps label="Carregar fluxo"
            :loading="loadingWorkflow"
            :disable="!subjectKey.trim() || loadingWorkflow || publishingWorkflow || processBusy"
            @click="loadWorkflow"
          />
        </div>

        <p v-if="workflowNotice" class="member-feedback member-feedback-success" role="status">{{ workflowNotice }}</p>
        <p v-if="workflowError" class="member-feedback member-feedback-error" role="alert">{{ workflowError }}</p>

        <template v-if="loadedSubjectKey && loadedSubjectKey === subjectKey.trim()">
          <q-input
            v-model="subjectName"
            label="Nome do assunto"
            outlined dense maxlength="180"
            :disable="publishingWorkflow || processBusy"
          />
          <ol class="workflow-draft-list" aria-label="Etapas da definição do fluxo">
            <li v-for="(step, index) in steps" :key="step.key" class="workflow-draft-step">
              <span class="workflow-sequence-number" aria-hidden="true">{{ index + 1 }}</span>
              <div class="workflow-step-fields">
                <q-input
                  v-model="step.name"
                  :label="`Nome da etapa ${index + 1}`"
                  outlined dense maxlength="180"
                  :disable="publishingWorkflow || processBusy"
                />
                <q-select
                  :model-value="step.departmentIds"
                  :options="departmentOptions"
                  :label="`Setores da etapa ${index + 1}`"
                  multiple use-chips emit-value map-options
                  outlined dense
                  :disable="publishingWorkflow || processBusy || !departmentOptions.length"
                  @update:model-value="setStepDepartments(step, $event)"
                />
                <q-select
                  v-model="step.decisionDepartmentIds"
                  :options="departmentOptions.filter(option => step.departmentIds.includes(option.value))"
                  :label="`Setores decisores ${index + 1}`"
                  hint="Escolha somente entre os setores desta etapa."
                  multiple use-chips emit-value map-options
                  outlined dense
                  :disable="publishingWorkflow || processBusy || !step.departmentIds.length"
                />
              </div>
              <q-btn
                flat color="primary" no-caps label="Remover etapa"
                :aria-label="`Remover etapa ${index + 1}`"
                :disable="steps.length <= 2 || publishingWorkflow || processBusy"
                @click="removeStep(index)"
              />
            </li>
          </ol>
          <div class="workflow-editor-actions">
            <q-btn
              color="primary" outline no-caps label="Adicionar etapa"
              :disable="steps.length >= 30 || publishingWorkflow || processBusy"
              @click="addStep"
            />
            <q-btn
              color="primary" unelevated no-caps
              :label="`Publicar versão ${workflowVersion + 1}`"
              :loading="publishingWorkflow"
              :disable="!workflowDraftIsValid || publishingWorkflow || loadingWorkflow || processBusy"
              @click="publishWorkflow"
            />
          </div>
          <p v-if="!departmentOptions.length" class="member-feedback member-feedback-error" role="status">
            Não há setores ativos disponíveis nesta organização para configurar o fluxo.
          </p>
        </template>
        <p v-else-if="!loadingWorkflow" class="workflow-empty-note" role="note">
          Informe um identificador e carregue o fluxo para editar uma versão existente ou publicar a primeira.
        </p>
      </section>

      <section class="process-opening" aria-labelledby="process-opening-title">
        <h3 id="process-opening-title" class="subsection-heading">Abrir processo</h3>
        <p class="member-access-note">O processo usa a versão vigente e só pode começar em um setor da primeira etapa. Sua lotação é conferida pelo serviço.</p>
        <template v-if="workflow">
          <q-input
            v-model="processTitle"
            label="Título do processo"
            outlined dense maxlength="240"
            :disable="processAction || loadingProcess"
          />
          <q-select
            v-model="initialDepartmentId"
            :options="initialDepartmentOptions"
            label="Setor inicial do processo"
            emit-value map-options outlined dense
            :disable="processAction || loadingProcess || !initialDepartmentOptions.length"
          />
          <p v-if="!initialDepartmentOptions.length" class="workflow-empty-note" role="status">
            Nenhum setor ativo da primeira etapa está disponível para abertura.
          </p>
          <q-btn
            color="primary" unelevated no-caps label="Abrir processo"
            :loading="processAction"
            :disable="!processTitle.trim() || !initialDepartmentId || !initialDepartmentOptions.some(option => option.value === initialDepartmentId) || processAction || loadingProcess"
            @click="openProcess"
          />
        </template>
        <p v-else class="workflow-empty-note" role="note">
          Publique ou carregue uma versão ativa antes de abrir um processo.
        </p>

        <p v-if="processNotice && !process" class="member-feedback member-feedback-success" role="status">{{ processNotice }}</p>
        <p v-if="processError && !process" class="member-feedback member-feedback-error" role="alert">{{ processError }}</p>
      </section>
    </div>

    <section class="workflow-process-list" aria-labelledby="workflow-process-list-title">
      <div class="workflow-process-list-heading">
        <div>
          <h3 id="workflow-process-list-title" class="subsection-heading">Processos disponíveis</h3>
          <p class="member-access-note">A lista mostra somente registros autorizados pelo serviço. Cada página traz até {{ processPageSize }} processos.</p>
        </div>
        <q-btn
          flat color="primary" no-caps label="Atualizar lista"
          :loading="processListLoading"
          :disable="processListLoading"
          @click="loadProcessList()"
        />
      </div>
      <div class="workflow-process-search">
        <q-input
          v-model="processListQuery"
          label="Buscar por título ou assunto"
          hint="A busca considera título, assunto e identificador."
          outlined dense maxlength="120"
          :disable="processListLoading"
          @keyup.enter="loadProcessList()"
        />
        <q-btn
          color="primary" outline no-caps label="Buscar"
          :loading="processListLoading"
          :disable="processListLoading"
          @click="loadProcessList()"
        />
      </div>
      <p v-if="processListNotice" class="member-access-note" role="status">{{ processListNotice }}</p>
      <p v-if="processListError" class="member-feedback member-feedback-error" role="alert">{{ processListError }}</p>
      <p v-if="processError && !process && !canManageDefinitions" class="member-feedback member-feedback-error" role="alert">{{ processError }}</p>
      <p v-if="processListLoading && !processList.length" class="workflow-empty-note" role="status">Carregando processos autorizados…</p>
      <p v-else-if="!processListLoading && !processList.length && !processListError" class="workflow-empty-note" role="status">
        Nenhum processo autorizado foi encontrado para esta busca.
      </p>
      <ol v-else class="workflow-process-list-items" aria-label="Lista de processos autorizados">
        <li v-for="item in processList" :key="item.id" class="workflow-process-list-item">
          <div class="workflow-process-list-summary">
            <strong>{{ item.title }}</strong>
            <span>Assunto: {{ item.subjectName }}</span>
            <span>Versão do fluxo: {{ item.workflowVersion }}</span>
            <span>Etapa {{ item.currentStepOrder }}: {{ item.currentStepName }}</span>
            <span>Setor responsável: {{ item.currentDepartmentName }}</span>
            <span>{{ item.status === 'ACTIVE' ? 'Em andamento' : item.status }} · {{ formatDate(item.createdAt) }}</span>
            <span>Decisão: {{ decisionLabel(item.decisionOutcome) }}</span>
          </div>
          <q-btn
            flat color="primary" no-caps label="Ver processo"
            :aria-label="`Abrir processo ${item.title}`"
            :loading="loadingProcess && processId === item.id"
            :disable="processBusy"
            @click="openListedProcess(item)"
          />
        </li>
      </ol>
      <q-btn
        v-if="processListHasNext"
        color="primary" outline no-caps label="Carregar mais processos"
        :loading="processListLoading"
        :disable="processListLoading"
        @click="loadProcessList(true)"
      />
    </section>

    <section v-if="process" class="workflow-process-record" aria-label="Etapas e histórico do processo">
      <p v-if="processNotice" class="member-feedback member-feedback-success workflow-process-notice" role="status">{{ processNotice }}</p>
      <p v-if="processError" class="member-feedback member-feedback-error workflow-process-notice" role="alert">{{ processError }}</p>
      <header class="workflow-process-header">
        <div>
          <p class="workflow-record-kicker">Processo administrativo</p>
          <h3 class="workflow-process-title">{{ process.title }}</h3>
          <p class="member-access-note">{{ process.subjectName }} · versão {{ process.workflowVersion }} · {{ process.currentDepartmentName }}</p>
        </div>
        <div class="workflow-process-controls">
          <span class="status-label">{{ process.status === 'ACTIVE' ? 'Em andamento' : process.status }}</span>
          <q-btn flat color="primary" no-caps label="Atualizar processo" :loading="loadingProcess" :disable="processAction" @click="reloadProcess" />
        </div>
      </header>

      <div class="workflow-snapshot-summary">
        <span><strong>Versão preservada</strong> {{ process.workflowVersion }}</span>
        <span><strong>Etapa atual</strong> {{ process.currentStepOrder }} de {{ process.steps.length }} — {{ process.currentStepName }}</span>
        <span><strong>Setor responsável</strong> {{ process.currentDepartmentName }}</span>
        <span><strong>Decisão do processo</strong> {{ decisionLabel(process.decisionOutcome) }}<template v-if="process.decisionAt"> · {{ formatDate(process.decisionAt) }}</template></span>
      </div>

      <ol class="workflow-process-steps" aria-label="Etapas do processo">
        <li
          v-for="step in processStepsOrdered"
          :key="step.order"
          class="workflow-process-step"
          :class="{
            'workflow-process-step-complete': step.order < process.currentStepOrder,
            'workflow-process-step-current': step.order === process.currentStepOrder,
            'workflow-process-step-future': step.order > process.currentStepOrder
          }"
          :aria-current="step.order === process.currentStepOrder ? 'step' : undefined"
        >
          <span class="workflow-step-marker" aria-hidden="true">{{ step.order }}</span>
          <span class="workflow-process-step-name">{{ step.name }}</span>
          <span class="workflow-process-step-departments">{{ step.departments.map(department => department.name).join(', ') }}</span>
          <span v-if="step.order === process.currentStepOrder" class="workflow-current-label">Etapa atual</span>
        </li>
      </ol>

      <div class="workflow-advance-area">
        <template v-if="nextStep">
          <div>
            <h4 class="workflow-next-title">Próxima etapa: {{ nextStep.name }}</h4>
            <p class="member-access-note">O destino fica limitado aos setores ativos dessa etapa. O servidor precisa ter lotação ativa no setor atual.</p>
          </div>
          <p v-if="!nextDepartmentOptions.length" class="member-feedback member-feedback-error workflow-stage-warning" role="status">
            Esta etapa não tem setor ativo no snapshot deste processo. Peça à administração da organização que reative um dos setores previstos nesta etapa. Uma nova versão do fluxo vale apenas para processos futuros e não altera este processo.
          </p>
          <q-select
            v-model="nextDepartmentId"
            :options="nextDepartmentOptions"
            label="Setor de destino"
            emit-value map-options outlined dense
            :disable="processAction || loadingProcess || !nextDepartmentOptions.length"
          />
          <q-btn
            color="primary" unelevated no-caps :label="`Avançar para ${nextStep.name}`"
            :loading="processAction"
            :disable="processAction || loadingProcess || !nextDepartmentId || !nextDepartmentOptions.length"
            @click="advanceProcess"
          />
        </template>
        <p v-else class="workflow-end-note" role="status">
          O processo chegou à última etapa do fluxo; isso não significa que foi encerrado. Não há uma ação de encerramento disponível aqui.
        </p>
      </div>

      <div v-if="currentDecisionDepartment?.canDecide && process.status === 'ACTIVE'" class="workflow-decision-area">
        <div>
          <h4 class="workflow-next-title">Decisão do processo</h4>
          <p class="member-access-note">O resultado mais recente aparece no processo e no histórico. Esta marcação não altera o andamento nem encerra o processo.</p>
        </div>
        <span class="workflow-decision-state">{{ decisionLabel(process.decisionOutcome) }}</span>
        <q-btn
          color="primary" outline no-caps label="Marcar como Deferido"
          :loading="processAction"
          :disable="processAction || loadingProcess || process.decisionOutcome === 'DEFERIDO'"
          @click="decideProcess('DEFERIDO')"
        />
        <q-btn
          color="primary" unelevated no-caps label="Marcar como Indeferido"
          :loading="processAction"
          :disable="processAction || loadingProcess || process.decisionOutcome === 'INDEFERIDO'"
          @click="decideProcess('INDEFERIDO')"
        />
      </div>

      <h4 class="workflow-history-title">Histórico registrado</h4>
      <ol class="workflow-event-list" aria-label="Histórico de movimentações do processo">
        <li v-for="event in process.events" :key="event.id" class="workflow-event-row">
          <span class="workflow-event-marker" aria-hidden="true"></span>
          <div class="workflow-event-copy">
            <strong>{{ eventActionLabel(event.action) }}<template v-if="event.decisionOutcome">: {{ decisionLabel(event.decisionOutcome) }}</template> — {{ process.steps.find(step => step.order === event.toStepOrder)?.name || `Etapa ${event.toStepOrder}` }}</strong>
            <span>{{ processDepartmentName(event.fromDepartmentId) }} <span aria-hidden="true">→</span> {{ processDepartmentName(event.toDepartmentId) }}</span>
            <span class="muted text-caption">{{ event.actor }} · {{ formatDate(event.occurredAt) }}</span>
          </div>
        </li>
      </ol>
    </section>
  </section>
</template>

<style scoped>
.administrative-process-panel {
  grid-column: 1 / -1;
  min-width: 0;
  margin-top: 8px;
  padding-top: 30px;
  border-top: 1px solid var(--nexo-color-separator);
}
.administrative-process-heading,
.workflow-process-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--nexo-space-6);
}
.administrative-process-heading .section-subheading { margin-bottom: 20px; }
.workflow-version-label,
.workflow-current-label {
  color: var(--nexo-color-text-secondary);
  font-size: 13px;
  font-weight: 600;
}
.administrative-process-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(280px, .8fr);
  gap: clamp(24px, 4vw, 56px);
  align-items: start;
}
.workflow-editor,
.process-opening { min-width: 0; }
.workflow-editor > .q-input,
.process-opening > .q-input,
.process-opening > .q-select { margin-top: var(--nexo-space-4); }
.workflow-lookup-row,
.workflow-editor-actions,
.workflow-process-controls {
  display: flex;
  align-items: flex-start;
  gap: var(--nexo-space-3);
}
.workflow-lookup-row .q-input { flex: 1; min-width: 0; }
.workflow-lookup-row .q-btn { flex: 0 0 auto; }
.workflow-draft-list,
.workflow-process-steps,
.workflow-event-list {
  margin: var(--nexo-space-5) 0 0;
  padding: 0;
  list-style: none;
}
.workflow-draft-step {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--nexo-space-3);
  padding: var(--nexo-space-4) 0;
  border-top: 1px solid var(--nexo-color-separator);
}
.workflow-sequence-number,
.workflow-step-marker {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border: 1px solid var(--nexo-color-outline);
  border-radius: 50%;
  color: var(--nexo-color-text-secondary);
  font-size: 13px;
  font-weight: 600;
}
.workflow-step-fields { display: grid; grid-template-columns: minmax(160px, .75fr) minmax(220px, 1.25fr); gap: var(--nexo-space-3); min-width: 0; }
.workflow-editor-actions { justify-content: space-between; flex-wrap: wrap; padding-top: var(--nexo-space-2); }
.workflow-empty-note,
.workflow-end-note { margin: var(--nexo-space-4) 0 0; color: var(--nexo-color-text-secondary); font-size: 14px; line-height: 1.55; }
.workflow-process-record { margin-top: var(--nexo-space-8); padding-top: var(--nexo-space-6); border-top: 1px solid var(--nexo-color-separator); }
.workflow-process-notice { margin: 0 0 var(--nexo-space-4); }
.workflow-process-list { margin-top: var(--nexo-space-8); padding-top: var(--nexo-space-6); border-top: 1px solid var(--nexo-color-separator); }
.workflow-process-list-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--nexo-space-4); }
.workflow-process-list-heading .member-access-note { margin: var(--nexo-space-1) 0 0; }
.workflow-process-search { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: start; gap: var(--nexo-space-3); margin-top: var(--nexo-space-4); }
.workflow-process-list-items { margin: var(--nexo-space-4) 0; padding: 0; list-style: none; }
.workflow-process-list-item { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: var(--nexo-space-4); padding: var(--nexo-space-4) 0; border-top: 1px solid var(--nexo-color-separator); }
.workflow-process-list-summary { display: grid; gap: var(--nexo-space-1); min-width: 0; color: var(--nexo-color-text-secondary); font-size: 13px; line-height: 1.45; }
.workflow-process-list-summary strong { color: var(--nexo-color-text); font-size: 15px; font-weight: 600; }
.workflow-record-kicker { margin: 0 0 var(--nexo-space-1); color: var(--nexo-color-text-secondary); font-size: 13px; }
.workflow-process-title { margin: 0; color: var(--nexo-color-text); font-size: 21px; font-weight: 650; letter-spacing: -.025em; line-height: 1.3; }
.workflow-process-controls { align-items: center; flex-wrap: wrap; justify-content: flex-end; }
.workflow-snapshot-summary { display: flex; flex-wrap: wrap; gap: var(--nexo-space-4) var(--nexo-space-8); margin-top: var(--nexo-space-5); padding: var(--nexo-space-4) 0; border-top: 1px solid var(--nexo-color-separator); border-bottom: 1px solid var(--nexo-color-separator); color: var(--nexo-color-text-secondary); font-size: 14px; }
.workflow-snapshot-summary strong { margin-right: var(--nexo-space-1); color: var(--nexo-color-text); font-weight: 600; }
.workflow-process-steps { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: var(--nexo-space-4); }
.workflow-process-step { min-width: 0; display: grid; grid-template-columns: 32px minmax(0, 1fr); gap: var(--nexo-space-1) var(--nexo-space-3); align-content: start; }
.workflow-process-step .workflow-step-marker { grid-row: span 2; }
.workflow-process-step-current .workflow-step-marker { border-color: var(--nexo-color-primary); background: var(--nexo-color-selection); color: var(--nexo-color-navy); }
.workflow-process-step-complete .workflow-step-marker { border-color: var(--nexo-color-success-outline); background: var(--nexo-color-success-surface); color: var(--nexo-color-success); }
.workflow-process-step-name { color: var(--nexo-color-text); font-size: 14px; font-weight: 600; }
.workflow-process-step-departments { grid-column: 2; color: var(--nexo-color-text-secondary); font-size: 13px; line-height: 1.45; }
.workflow-current-label { grid-column: 2; color: var(--nexo-color-action); font-size: 12px; }
.workflow-advance-area { display: grid; grid-template-columns: minmax(220px, 1fr) minmax(220px, .8fr) auto; align-items: end; gap: var(--nexo-space-4); margin-top: var(--nexo-space-6); padding: var(--nexo-space-5) 0; border-top: 1px solid var(--nexo-color-separator); border-bottom: 1px solid var(--nexo-color-separator); }
.workflow-decision-area { display: grid; grid-template-columns: minmax(220px, 1fr) auto auto auto; align-items: center; gap: var(--nexo-space-3); margin-top: var(--nexo-space-5); padding: var(--nexo-space-4) 0; border-bottom: 1px solid var(--nexo-color-separator); }
.workflow-decision-state { color: var(--nexo-color-text-secondary); font-size: 14px; font-weight: 600; }
.workflow-next-title,
.workflow-history-title { margin: 0; color: var(--nexo-color-text); font-size: 16px; font-weight: 650; line-height: 1.35; }
.workflow-stage-warning { grid-column: 1 / -1; margin: 0; }
.workflow-history-title { margin-top: var(--nexo-space-6); }
.workflow-event-list { display: grid; gap: 0; }
.workflow-event-row { position: relative; display: grid; grid-template-columns: 18px minmax(0, 1fr); gap: var(--nexo-space-3); padding: 0 0 var(--nexo-space-4); }
.workflow-event-row:not(:last-child)::before { position: absolute; top: 12px; bottom: 0; left: 6px; width: 1px; background: var(--nexo-color-separator); content: ''; }
.workflow-event-marker { z-index: 1; width: 13px; height: 13px; margin-top: 3px; border: 2px solid var(--nexo-color-primary); border-radius: 50%; background: var(--nexo-color-surface); }
.workflow-event-copy { display: grid; gap: var(--nexo-space-1); min-width: 0; color: var(--nexo-color-text-secondary); font-size: 13px; line-height: 1.45; }
.workflow-event-copy strong { color: var(--nexo-color-text); font-size: 14px; font-weight: 600; }
.workflow-event-copy .muted { color: var(--nexo-color-text-tertiary); }
@media (max-width: 900px) {
  .administrative-process-grid { grid-template-columns: minmax(0, 1fr); gap: var(--nexo-space-7); }
  .workflow-advance-area { grid-template-columns: minmax(0, 1fr) minmax(200px, .8fr); }
  .workflow-advance-area > .q-btn { grid-column: 2; justify-self: start; }
  .workflow-decision-area { grid-template-columns: minmax(0, 1fr) auto; }
}
@media (max-width: 600px) {
  .administrative-process-heading,
  .workflow-process-header { flex-direction: column; }
  .workflow-process-list-heading { flex-direction: column; align-items: stretch; }
  .workflow-lookup-row,
  .workflow-editor-actions,
  .workflow-process-controls { align-items: stretch; flex-direction: column; }
  .workflow-process-search,
  .workflow-process-list-item { grid-template-columns: minmax(0, 1fr); }
  .workflow-lookup-row .q-btn,
  .workflow-editor-actions .q-btn,
  .workflow-process-controls .q-btn { width: 100%; }
  .workflow-draft-step { grid-template-columns: 28px minmax(0, 1fr); }
  .workflow-draft-step > .q-btn { grid-column: 2; justify-self: start; }
  .workflow-step-fields { grid-template-columns: minmax(0, 1fr); }
  .workflow-advance-area { grid-template-columns: minmax(0, 1fr); }
  .workflow-advance-area > .q-btn { grid-column: auto; justify-self: stretch; }
  .workflow-advance-area > .q-btn { width: 100%; }
  .workflow-decision-area { grid-template-columns: minmax(0, 1fr); }
  .workflow-decision-area > .q-btn { width: 100%; }
  .workflow-snapshot-summary { display: grid; gap: var(--nexo-space-2); }
}
</style>
