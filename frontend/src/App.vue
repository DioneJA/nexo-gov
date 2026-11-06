<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, staffHeaders, type Department, type Organization, type Protocol } from './api'

const tab = ref('citizen')
const organizations = ref<Organization[]>([])
const departments = ref<Department[]>([])
const username = ref('servidor')
const password = ref('')
const organizationId = ref('10000000-0000-4000-8000-000000000001')
const protocols = ref<Protocol[]>([])
const loading = ref(false)
const notice = ref('')
const error = ref('')
const token = ref('')
const tracking = ref<Record<string, unknown> | null>(null)
const receiptPath = ref('')
const destinationDepartmentId = ref('')
const form = ref({ organizationSlug: 'municipio-alfa', applicantName: '', applicantEmail: '', subject: '', initialMessage: '' })

const statusLabels: Record<string, string> = {
  OPEN: 'Aberto', FORWARDED: 'Encaminhado', RECEIVED: 'Recebido', DISPATCHED: 'Despachado', COMPLETED: 'Concluído'
}
const authenticated = computed(() => password.value.length > 0)

onMounted(async () => {
  try {
    organizations.value = (await api.get<Organization[]>('/public/organizations')).data
    const fromQr = new URLSearchParams(window.location.hash.split('?')[1] || '').get('token')
    if (fromQr) { token.value = fromQr; tab.value = 'tracking'; await lookup() }
  } catch { error.value = 'Não foi possível conectar ao serviço. Confirme se a API está em execução.' }
})

async function createProtocol() {
  loading.value = true; error.value = ''; notice.value = ''
  try {
    const response = await api.post('/public/protocols', form.value)
    token.value = response.data.trackingToken
    receiptPath.value = response.data.receiptPath
    notice.value = `Protocolo ${response.data.protocolNumber} aberto. Guarde o token de consulta e baixe o comprovante.`
    tab.value = 'tracking'
    await lookup()
  } catch (e: any) { error.value = e.response?.data?.message || 'Não foi possível abrir o protocolo. Revise os campos e tente novamente.' }
  finally { loading.value = false }
}

async function lookup() {
  if (!token.value.trim()) { error.value = 'Informe o token que aparece no comprovante.'; return }
  loading.value = true; error.value = ''
  try { tracking.value = (await api.get('/public/protocols/tracking', { headers: { Authorization: `Bearer ${token.value.trim()}` } })).data }
  catch { tracking.value = null; error.value = 'Consulta não localizada. Confira o token do comprovante.' }
  finally { loading.value = false }
}

async function downloadReceipt() {
  if (!receiptPath.value || !token.value) return
  try {
    const response = await api.get(receiptPath.value.replace('/api/v1', ''), { responseType: 'blob', headers: { Authorization: `Bearer ${token.value}` } })
    const url = URL.createObjectURL(response.data); const anchor = document.createElement('a')
    anchor.href = url; anchor.download = 'comprovante-protocolo.pdf'; anchor.click(); URL.revokeObjectURL(url)
  } catch { error.value = 'Não foi possível gerar o comprovante agora.' }
}

async function loadStaff() {
  loading.value = true; error.value = ''; notice.value = ''
  try {
    const headers = staffHeaders(organizationId.value, username.value, password.value)
    const [departmentResponse, protocolResponse] = await Promise.all([
      api.get<Department[]>('/staff/departments', { headers }), api.get<Protocol[]>('/staff/protocols', { headers })
    ])
    departments.value = departmentResponse.data; protocols.value = protocolResponse.data
    if (!destinationDepartmentId.value && departments.value.length) destinationDepartmentId.value = departments.value[0].id
  } catch (e: any) { error.value = e.response?.status === 401 || e.response?.status === 403 ? 'Acesso recusado. Confira usuário, senha e organização.' : 'Não foi possível carregar os protocolos.' }
  finally { loading.value = false }
}

async function act(protocol: Protocol, action: 'forward' | 'receive' | 'dispatch' | 'complete') {
  const headers = staffHeaders(organizationId.value, username.value, password.value)
  const body: Record<string, unknown> = { visibleToApplicant: false }
  if (action === 'forward' || action === 'dispatch') body.destinationDepartmentId = destinationDepartmentId.value
  if (action === 'dispatch') { body.note = 'Encaminhado para análise do setor responsável.'; body.visibleToApplicant = true }
  loading.value = true; error.value = ''
  try {
    await api.post(`/staff/protocols/${protocol.id}/${action}`, body, { headers })
    notice.value = `Ação registrada para ${protocol.protocolNumber}.`
    await loadStaff()
  } catch (e: any) { error.value = e.response?.data?.message || 'A etapa não foi concluída.' }
  finally { loading.value = false }
}

function actionsFor(protocol: Protocol) {
  if (protocol.status === 'OPEN') return [{ label: 'Encaminhar', value: 'forward' as const }]
  if (protocol.status === 'FORWARDED' || protocol.status === 'DISPATCHED') return [{ label: 'Receber', value: 'receive' as const }]
  if (protocol.status === 'RECEIVED') return [{ label: 'Despachar', value: 'dispatch' as const }, { label: 'Concluir', value: 'complete' as const }]
  return []
}

function formatDate(value: unknown) {
  return typeof value === 'string' ? new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : ''
}
</script>

<template>
  <q-layout view="lHh Lpr lFf" class="app-shell">
    <q-header class="topbar"><div class="topbar-inner">
      <div class="brand-mark">N</div><div><div class="brand-name">NexoGov</div><div class="brand-caption">Serviços e gestão pública</div></div><q-space />
      <span class="status-pill">Ambiente de demonstração</span>
    </div></q-header>
    <q-page-container><q-page class="content">
      <div class="eyebrow">Atendimento municipal</div>
      <h1 class="page-title">Serviços públicos em um só lugar</h1>
      <p class="page-lead">Abra solicitações, acompanhe cada movimentação e mantenha o histórico organizado em um canal seguro.</p>

      <q-banner v-if="notice" class="q-mt-lg bg-green-1 text-positive rounded-borders" rounded>{{ notice }}</q-banner>
      <q-banner v-if="error" class="q-mt-lg bg-red-1 text-negative rounded-borders" rounded>{{ error }}</q-banner>

      <q-tabs v-model="tab" class="q-mt-xl text-grey-7" active-color="primary" indicator-color="primary" align="left" narrow-indicator>
        <q-tab name="citizen" label="Novo protocolo" no-caps />
        <q-tab name="tracking" label="Acompanhar" no-caps />
        <q-tab name="staff" label="Área do servidor" no-caps />
      </q-tabs>
      <q-separator />

      <q-tab-panels v-model="tab" animated class="bg-transparent q-mt-lg">
        <q-tab-panel name="citizen" class="q-pa-none">
          <q-card flat class="surface-card section-card">
            <h2 class="section-heading">Conte o que você precisa</h2><p class="section-subheading">Os campos com asterisco são necessários para registrar a solicitação.</p>
            <q-form class="form-grid" @submit.prevent="createProtocol">
              <q-select v-model="form.organizationSlug" :options="organizations.map(o => ({ label: o.name, value: o.slug }))" emit-value map-options outlined dense label="Órgão público *" />
              <q-input v-model="form.applicantName" outlined dense label="Nome completo *" maxlength="180" />
              <q-input v-model="form.applicantEmail" outlined dense type="email" label="E-mail *" maxlength="254" />
              <q-input v-model="form.subject" outlined dense label="Assunto *" maxlength="240" />
              <q-input v-model="form.initialMessage" class="span-two" outlined type="textarea" autogrow label="Descreva sua solicitação *" maxlength="8000" />
              <div class="span-two"><q-btn color="primary" unelevated rounded type="submit" :loading="loading" label="Registrar solicitação" no-caps icon-right="arrow_forward" /></div>
            </q-form>
          </q-card>
        </q-tab-panel>

        <q-tab-panel name="tracking" class="q-pa-none">
          <q-card flat class="surface-card section-card">
            <div class="toolbar-row"><div><h2 class="section-heading">Acompanhe sua solicitação</h2><p class="section-subheading">Use o token individual entregue com o comprovante.</p></div>
              <q-btn v-if="receiptPath" outline color="primary" rounded icon="picture_as_pdf" label="Baixar comprovante" no-caps @click="downloadReceipt" /></div>
            <div class="row q-col-gutter-sm items-start"><div class="col-12 col-md-8"><q-input v-model="token" outlined dense label="Token de consulta" autocomplete="off" /></div><div class="col-12 col-md-auto"><q-btn color="primary" unelevated rounded :loading="loading" label="Consultar" no-caps @click="lookup" /></div></div>
            <q-card v-if="tracking" flat bordered class="q-mt-lg q-pa-md rounded-borders">
              <div class="toolbar-row"><div><div class="protocol-number">{{ tracking.protocolNumber }}</div><div class="text-subtitle1 q-mt-xs">{{ tracking.subject }}</div></div><span class="status-pill">{{ statusLabels[String(tracking.status)] }}</span></div>
              <div class="muted q-mt-sm">Aberto em {{ formatDate(tracking.createdAt) }}</div><q-separator class="q-my-md" />
              <div v-for="(event, index) in (tracking.events as any[])" :key="index" class="row no-wrap q-mb-md">
                <q-icon name="radio_button_checked" color="primary" size="18px" class="q-mr-sm q-mt-xs" />
                <div><div class="text-weight-medium">{{ event.summary }}</div><div class="muted text-caption">{{ formatDate(event.createdAt) }}</div></div>
              </div>
            </q-card>
          </q-card>
        </q-tab-panel>

        <q-tab-panel name="staff" class="q-pa-none">
          <q-card flat class="surface-card section-card">
            <div class="toolbar-row"><div><h2 class="section-heading">Caixa de entrada</h2><p class="section-subheading">A tramitação é validada pelo servidor e pela organização no backend.</p></div><q-btn color="primary" outline rounded icon="refresh" label="Atualizar" no-caps :loading="loading" @click="loadStaff" /></div>
            <div class="form-grid q-mb-lg">
              <q-input v-model="username" outlined dense label="Usuário" autocomplete="username" />
              <q-input v-model="password" outlined dense type="password" label="Senha local de demonstração" autocomplete="current-password" />
              <q-select v-model="organizationId" :options="organizations.map(o => ({ label: o.name, value: o.id }))" emit-value map-options outlined dense label="Organização" />
              <q-select v-model="destinationDepartmentId" :options="departments.map(d => ({ label: d.name, value: d.id }))" emit-value map-options outlined dense label="Próximo setor" />
              <div class="span-two"><q-btn color="primary" unelevated rounded label="Entrar e carregar caixa" no-caps :loading="loading" @click="loadStaff" /></div>
            </div>
            <q-separator class="q-mb-md" />
            <div v-if="protocols.length" class="column q-gutter-sm">
              <q-card v-for="protocol in protocols" :key="protocol.id" flat bordered class="q-pa-md rounded-borders">
                <div class="toolbar-row"><div><div class="protocol-number">{{ protocol.protocolNumber }}</div><div class="text-weight-medium q-mt-xs">{{ protocol.subject }}</div><div class="muted text-caption">{{ protocol.applicantName }} · {{ protocol.assignedDepartmentName || 'Sem setor' }} · {{ formatDate(protocol.createdAt) }}</div></div>
                  <div class="row items-center q-gutter-xs"><span class="status-pill">{{ statusLabels[protocol.status] }}</span><q-btn v-for="action in actionsFor(protocol)" :key="action.value" size="sm" color="primary" outline rounded no-caps :label="action.label" @click="act(protocol, action.value)" /></div></div>
              </q-card>
            </div>
            <div v-else class="q-pa-lg text-center muted">Entre com seu usuário e senha local para carregar os protocolos.</div>
          </q-card>
        </q-tab-panel>
      </q-tab-panels>
      <div class="text-caption muted q-mt-xl">Demonstração inicial · Não cadastrar dados pessoais reais.</div>
    </q-page></q-page-container>
  </q-layout>
</template>
