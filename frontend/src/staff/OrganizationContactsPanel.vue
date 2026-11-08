<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { organizationHeaders, staffApi, type OrganizationContact, type OrganizationContactInput, type OrganizationContactType, type OrganizationContactPage, type OrganizationContactImportResult } from '../api/staff'

const props = defineProps<{ organizationId: string }>()
const contacts = ref<OrganizationContact[]>([])
const total = ref(0)
const page = ref(0)
const query = ref('')
const includeArchived = ref(false)
const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const error = ref('')
const notice = ref('')
const editId = ref('')
const formOpen = ref(false)
const draft = ref({ type: 'PERSON' as OrganizationContactType, name: '', email: '', phone: '' })
let generation = 0
let searchTimer: number | undefined

const typeLabels: Record<OrganizationContactType, string> = {
  PERSON: 'Pessoa física',
  MUNICIPAL_DEPARTMENT: 'Setor da prefeitura',
  LEGAL_ENTITY: 'Prefeitura / pessoa jurídica'
}
const pageSize = 20
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const pageLabel = computed(() => `${page.value + 1} de ${pageCount.value}`)

function apiError(exception: any, fallback: string) {
  const status = exception?.response?.status
  if (status === 400) return exception.response?.data?.message || 'Confira os campos e o arquivo CSV.'
  if (status === 403) return 'Seu perfil não pode administrar os contatos desta organização.'
  if (status === 404) return 'O contato não está disponível nesta organização.'
  if (status === 413) return 'O arquivo CSV excede o limite de 1 MiB.'
  return fallback
}

async function loadContacts() {
  const currentGeneration = ++generation
  const selectedOrganization = props.organizationId
  if (!selectedOrganization) {
    contacts.value = []
    total.value = 0
    return
  }
  loading.value = true
  error.value = ''
  try {
    const response = await staffApi.get<OrganizationContactPage>('/staff/organization/contacts', {
      headers: organizationHeaders(selectedOrganization),
      params: { q: query.value.trim(), includeArchived: includeArchived.value, page: page.value, size: pageSize }
    })
    if (currentGeneration !== generation || selectedOrganization !== props.organizationId) return
    contacts.value = response.data.items
    total.value = response.data.total
  } catch (exception) {
    if (currentGeneration !== generation) return
    error.value = apiError(exception, 'Não foi possível carregar os contatos. Tente novamente.')
  } finally {
    if (currentGeneration === generation) loading.value = false
  }
}

watch(() => props.organizationId, () => {
  generation++
  contacts.value = []
  total.value = 0
  page.value = 0
  query.value = ''
  includeArchived.value = false
  formOpen.value = false
  editId.value = ''
  notice.value = ''
  error.value = ''
  void loadContacts()
}, { immediate: true })

watch([query, includeArchived], () => {
  page.value = 0
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => void loadContacts(), 250)
})
watch(page, () => void loadContacts())

function resetDraft() {
  draft.value = { type: 'PERSON', name: '', email: '', phone: '' }
}

function startCreate() {
  editId.value = ''
  resetDraft()
  formOpen.value = true
  error.value = ''
  notice.value = ''
}

function startEdit(contact: OrganizationContact) {
  editId.value = contact.id
  draft.value = { type: contact.type, name: contact.name, email: contact.email || '', phone: contact.phone || '' }
  formOpen.value = true
  error.value = ''
  notice.value = ''
}

function payload(): OrganizationContactInput {
  return {
    type: draft.value.type,
    name: draft.value.name.trim(),
    email: draft.value.email.trim() || null,
    phone: draft.value.phone.trim() || null
  }
}

async function saveContact() {
  if (!draft.value.name.trim() || saving.value) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const headers = organizationHeaders(props.organizationId)
    if (editId.value) await staffApi.put(`/staff/organization/contacts/${editId.value}`, payload(), { headers })
    else await staffApi.post('/staff/organization/contacts', payload(), { headers })
    formOpen.value = false
    editId.value = ''
    resetDraft()
    notice.value = 'Contato salvo.'
    await loadContacts()
  } catch (exception) {
    error.value = apiError(exception, 'Não foi possível salvar o contato.')
  } finally {
    saving.value = false
  }
}

async function setActive(contact: OrganizationContact, active: boolean) {
  error.value = ''
  notice.value = ''
  try {
    await staffApi.put(`/staff/organization/contacts/${contact.id}/active`, { active }, {
      headers: organizationHeaders(props.organizationId)
    })
    notice.value = active ? 'Contato reativado.' : 'Contato arquivado.'
    await loadContacts()
  } catch (exception) {
    error.value = apiError(exception, 'Não foi possível atualizar o contato.')
  }
}

async function exportCsv() {
  error.value = ''
  try {
    const response = await staffApi.get<Blob>('/staff/organization/contacts/export', {
      headers: organizationHeaders(props.organizationId),
      params: { includeArchived: includeArchived.value },
      responseType: 'blob'
    })
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = 'contatos.csv'
    link.click()
    URL.revokeObjectURL(url)
    notice.value = 'Arquivo CSV exportado.'
  } catch (exception) {
    error.value = apiError(exception, 'Não foi possível exportar os contatos.')
  }
}

async function importCsv(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  importing.value = true
  error.value = ''
  notice.value = ''
  try {
    const response = await staffApi.post<OrganizationContactImportResult>('/staff/organization/contacts/import', file, {
      headers: { ...organizationHeaders(props.organizationId), 'Content-Type': 'text/csv' }
    })
    const result = response.data
    notice.value = `Importação concluída: ${result.created} criados, ${result.updated} atualizados e ${result.unchanged} sem alteração.`
    page.value = 0
    await loadContacts()
  } catch (exception) {
    error.value = apiError(exception, 'Não foi possível importar o arquivo. Nenhuma linha foi aplicada se houver erro de validação.')
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <section class="contacts" aria-labelledby="contacts-title">
    <div class="contacts-heading">
      <div>
        <h3 id="contacts-title" class="subsection-heading">Contatos da organização</h3>
        <p class="aside-copy">Pessoas, setores da prefeitura e entidades jurídicas. A lista é privada desta organização.</p>
      </div>
      <button class="text-action" type="button" :disabled="loading || importing" @click="startCreate">Adicionar</button>
    </div>

    <p v-if="notice" class="contact-feedback" role="status">{{ notice }}</p>
    <p v-if="error" class="contact-feedback contact-error" role="alert">{{ error }}</p>

    <form v-if="formOpen" class="contact-form" @submit.prevent="saveContact">
      <label class="contact-field">
        <span>Tipo de contato</span>
        <select v-model="draft.type" required>
          <option value="PERSON">Pessoa física</option>
          <option value="MUNICIPAL_DEPARTMENT">Setor da prefeitura</option>
          <option value="LEGAL_ENTITY">Prefeitura / pessoa jurídica</option>
        </select>
      </label>
      <label class="contact-field">
        <span>Nome</span>
        <input v-model="draft.name" required maxlength="180" :autocomplete="draft.type === 'PERSON' ? 'name' : 'organization'" />
      </label>
      <label class="contact-field">
        <span>E-mail <small>Opcional</small></span>
        <input v-model="draft.email" type="email" maxlength="254" autocomplete="email" />
      </label>
      <label class="contact-field">
        <span>Telefone <small>Opcional</small></span>
        <input v-model="draft.phone" type="tel" maxlength="40" autocomplete="tel" />
      </label>
      <div class="contact-form-actions">
        <button class="primary-action" type="submit" :disabled="saving || !draft.name.trim()">{{ saving ? 'Salvando…' : 'Salvar contato' }}</button>
        <button class="quiet-action" type="button" :disabled="saving" @click="formOpen = false">Cancelar</button>
      </div>
    </form>

    <div class="contact-tools">
      <label class="contact-search">
        <span class="sr-only">Buscar contato</span>
        <input v-model="query" type="search" maxlength="120" placeholder="Buscar por nome, e-mail ou telefone" />
      </label>
      <label class="contact-archived"><input v-model="includeArchived" type="checkbox" /> Incluir arquivados</label>
    </div>
    <div class="contact-file-actions">
      <button class="quiet-action" type="button" :disabled="loading || importing" @click="exportCsv">Exportar CSV</button>
      <label class="file-action" :class="{ 'file-action-disabled': importing }">
        {{ importing ? 'Importando…' : 'Importar CSV' }}
        <input type="file" accept=".csv,text/csv" :disabled="importing" @change="importCsv" />
      </label>
      <span class="contact-file-note">UTF-8, até 1.000 linhas por vez</span>
    </div>

    <p v-if="loading" class="contact-status" role="status" aria-live="polite">Carregando contatos…</p>
    <p v-else-if="!contacts.length" class="contact-status" role="status">{{ query ? 'Nenhum contato corresponde à busca.' : 'Nenhum contato cadastrado.' }}</p>
    <div v-else class="contact-list" :aria-busy="loading">
      <article v-for="contact in contacts" :key="contact.id" class="contact-row">
        <div class="contact-summary">
          <div class="contact-name-line">
            <h4>{{ contact.name }}</h4>
            <span v-if="!contact.active" class="contact-archived-badge">Arquivado</span>
          </div>
          <p class="contact-type">{{ typeLabels[contact.type] }}</p>
          <p v-if="contact.email || contact.phone" class="contact-details">
            <span v-if="contact.email">{{ contact.email }}</span>
            <span v-if="contact.email && contact.phone" aria-hidden="true"> · </span>
            <span v-if="contact.phone">{{ contact.phone }}</span>
          </p>
        </div>
        <div class="contact-row-actions">
          <button class="quiet-action" type="button" :disabled="loading || importing" @click="startEdit(contact)">Editar</button>
          <button class="quiet-action" type="button" :disabled="loading || importing" @click="setActive(contact, !contact.active)">{{ contact.active ? 'Arquivar' : 'Reativar' }}</button>
        </div>
      </article>
    </div>

    <div v-if="total > pageSize" class="contact-pagination" aria-label="Paginação dos contatos">
      <button class="quiet-action" type="button" :disabled="page === 0 || loading" @click="page--">Anterior</button>
      <span>Página {{ pageLabel }} · {{ total }} contatos</span>
      <button class="quiet-action" type="button" :disabled="page + 1 >= pageCount || loading" @click="page++">Próxima</button>
    </div>
  </section>
</template>

<style scoped>
.contacts { container-type: inline-size; }
.contacts-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.contacts-heading .aside-copy { margin-top: 7px; }
.text-action, .quiet-action, .primary-action, .file-action { min-height: 44px; border: 0; background: transparent; color: var(--nexo-color-action); font: inherit; font-size: 13px; font-weight: 600; cursor: pointer; }
.text-action { flex: 0 0 auto; padding: 3px 0 3px 8px; }
.quiet-action { padding: 7px 8px; border-radius: var(--nexo-radius-sm); }
.primary-action { padding: 8px 13px; color: white; background: var(--nexo-color-navy); border-radius: var(--nexo-radius-sm); }
.text-action:hover, .quiet-action:hover { background: var(--nexo-color-selection); }
.text-action:focus-visible, .quiet-action:focus-visible, .primary-action:focus-visible, .file-action:focus-within { outline: 3px solid var(--nexo-color-action); outline-offset: 2px; }
.text-action:disabled, .quiet-action:disabled, .primary-action:disabled, .file-action-disabled { opacity: .55; cursor: not-allowed; }
.contact-feedback { margin: 14px 0 0; color: var(--nexo-color-success); font-size: 13px; line-height: 1.5; }
.contact-error { color: var(--nexo-color-danger); }
.contact-form { display: grid; gap: 12px; margin: 17px 0; padding-bottom: 16px; border-bottom: 1px solid var(--nexo-color-separator); }
.contact-field { display: grid; gap: 6px; color: var(--nexo-color-text-secondary); font-size: 12px; }
.contact-field small { color: var(--nexo-color-text-tertiary); }
.contact-field input, .contact-field select, .contact-search input { width: 100%; min-height: 44px; padding: 8px 10px; border: 1px solid var(--nexo-color-outline); border-radius: var(--nexo-radius-sm); background: var(--nexo-color-surface); color: var(--nexo-color-text); font: inherit; font-size: 14px; }
.contact-field input:focus, .contact-field select:focus, .contact-search input:focus { outline: 3px solid var(--nexo-color-action); outline-offset: 1px; }
.contact-form-actions, .contact-file-actions, .contact-row-actions, .contact-pagination { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.contact-tools { display: grid; gap: 9px; margin-top: 16px; }
.contact-archived { display: flex; align-items: center; gap: 8px; color: var(--nexo-color-text-secondary); font-size: 13px; }
.contact-archived input { accent-color: var(--nexo-color-primary); }
.contact-file-actions { margin-top: 8px; gap: 12px; }
.file-action { position: relative; display: inline-flex; align-items: center; padding: 7px 8px; border-radius: var(--nexo-radius-sm); }
.file-action:hover { background: var(--nexo-color-selection); }
.file-action input { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; }
.contact-file-note, .contact-pagination, .contact-status { color: var(--nexo-color-text-tertiary); font-size: 12px; line-height: 1.5; }
.contact-status { padding: 12px 0; }
.contact-list { margin-top: 8px; border-top: 1px solid var(--nexo-color-separator); }
.contact-row { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; padding: 13px 0; border-bottom: 1px solid var(--nexo-color-separator); }
.contact-summary { min-width: 0; }
.contact-name-line { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; }
.contact-name-line h4 { margin: 0; color: var(--nexo-color-text); font-size: 14px; font-weight: 600; line-height: 1.4; overflow-wrap: anywhere; }
.contact-type, .contact-details { margin: 3px 0 0; color: var(--nexo-color-text-secondary); font-size: 12px; line-height: 1.5; overflow-wrap: anywhere; }
.contact-archived-badge { padding: 2px 6px; color: var(--nexo-color-text-secondary); background: var(--nexo-color-selection); border-radius: 999px; font-size: 10px; }
.contact-row-actions { flex: 0 0 auto; gap: 0; }
.contact-pagination { justify-content: space-between; margin-top: 13px; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@container (max-width: 480px) { .contact-row { flex-direction: column; gap: 5px; } .contact-row-actions { align-self: flex-end; } .contact-file-note { flex-basis: 100%; } }
</style>
