<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { publicApi, type Organization, type PublicParticipantField, type PublicParticipantInvitation, type PublicProtocolAttachment, type PublicProtocolInteraction, type PublicProtocolInteractionAttachment, type PublicProtocolSubject, type PublicProtocolTracking } from './api/public'

const tab = ref('new')
const organizations = ref<Organization[]>([])
const protocolSubjects = ref<PublicProtocolSubject[]>([])
const loading = ref(false)
const trackingLookupLoading = ref(false)
const subjectsLoading = ref(false)
const subjectLoadError = ref('')
const notice = ref('')
const error = ref('')
const token = ref('')
const tracking = ref<PublicProtocolTracking | null>(null)
const receiptPath = ref('')
const participantDrafts = ref<Array<{ name: string; identityValues: Record<string, string> }>>([])
const participantInvitations = ref<PublicParticipantInvitation[]>([])
const participantInvitationApplicantToken = ref('')
const participantInvitationProtocolId = ref('')
const invitationCopyNotice = ref('')
const participantNotice = ref('')
const participantRevocation = ref<{ id: string; name: string } | null>(null)
const participantRevoking = ref(false)
const form = ref({ organizationSlug: '', applicantName: '', applicantEmail: '', subjectId: '', initialMessage: '' })
const selectedSubject = computed(() => protocolSubjects.value.find(subject => subject.id === form.value.subjectId) || null)
const visibleParticipantInvitations = computed(() => tracking.value?.applicantView
  && tracking.value.id === participantInvitationProtocolId.value
  && token.value.trim() === participantInvitationApplicantToken.value
  ? participantInvitations.value : [])
const canDownloadReceipt = computed(() => !!receiptPath.value && tracking.value?.applicantView === true
  && tracking.value.id === participantInvitationProtocolId.value
  && token.value.trim() === participantInvitationApplicantToken.value)
const activeAttachmentRequirements = computed(() => selectedSubject.value?.attachmentRequirements?.filter(requirement => requirement.active) || [])
const filesByRequirement = ref<Record<string, File | null>>({})
const attachmentFieldErrors = ref<Record<string, string>>({})
const attachmentFieldRefs = new Map<string, { focus: () => void }>()
const uploadingAttachments = ref(false)
const downloadingAttachmentId = ref('')
const citizenInteractionMessage = ref('')
const citizenInteractionFiles = ref<File[]>([])
const citizenInteractionSubmitting = ref(false)
const citizenInteractionError = ref('')
const citizenInteractionNotice = ref('')
const visibleTrackingInteractions = computed(() => (tracking.value?.interactions || [])
  .filter(interaction => interaction.visibility === undefined || interaction.visibility === 'PUBLIC'))
const openingTrackingAttachments = computed(() => {
  const interactionAttachmentIds = new Set((tracking.value?.interactions || [])
    .flatMap(interaction => interaction.attachments || []).map(attachment => attachment.id))
  return (tracking.value?.attachments || []).filter(attachment => !interactionAttachmentIds.has(attachment.id))
})
const citizenInteractionAllowedExtensions = computed(() => (tracking.value?.interactionAllowedExtensions || [])
  .filter(extension => /^\.[a-z0-9]{1,10}$/i.test(extension)))
const isLocalEnvironment = import.meta.env.DEV
let subjectRequestVersion = 0
let trackingLookupGeneration = 0

const statusLabels: Record<string, string> = {
  OPEN: 'Aberto', FORWARDED: 'Encaminhado', RECEIVED: 'Recebido', DISPATCHED: 'Despachado', COMPLETED: 'Concluído'
}

onMounted(async () => {
  const fragment = window.location.hash.slice(1)
  const queryIndex = fragment.indexOf('?')
  const tokenQuery = queryIndex >= 0 ? fragment.slice(queryIndex + 1) : fragment
  const fromQr = new URLSearchParams(tokenQuery).get('token')?.trim() || ''
  if (fromQr) {
    token.value = fromQr
    tab.value = 'tracking'
    window.history.replaceState(
      window.history.state,
      '',
      `${window.location.pathname}${window.location.search}`
    )
  }

  try {
    organizations.value = (await publicApi.get<Organization[]>('/public/organizations')).data
    if (organizations.value.length) form.value.organizationSlug = organizations.value[0].slug
  } catch { error.value = 'Não foi possível conectar ao serviço. Tente novamente em instantes.' }
  if (fromQr) await lookup()
})

watch(() => form.value.organizationSlug, slug => {
  const requestVersion = ++subjectRequestVersion
  protocolSubjects.value = []
  form.value.subjectId = ''
  subjectLoadError.value = ''
  if (!slug) return
  subjectsLoading.value = true
  void publicApi.get<PublicProtocolSubject[]>(`/public/organizations/${encodeURIComponent(slug)}/protocol-subjects`)
    .then(response => {
      if (requestVersion === subjectRequestVersion && slug === form.value.organizationSlug) protocolSubjects.value = response.data
    })
    .catch(() => {
      if (requestVersion === subjectRequestVersion && slug === form.value.organizationSlug) subjectLoadError.value = 'Não foi possível carregar os assuntos. Tente novamente.'
    })
    .finally(() => {
      if (requestVersion === subjectRequestVersion) subjectsLoading.value = false
    })
}, { flush: 'sync' })

watch(() => form.value.subjectId, () => {
  filesByRequirement.value = {}
  attachmentFieldErrors.value = {}
  participantDrafts.value = []
}, { flush: 'sync' })

watch(token, value => {
  trackingLookupGeneration += 1
  tracking.value = null
  trackingLookupLoading.value = false
  error.value = ''
  citizenInteractionError.value = ''
  citizenInteractionNotice.value = ''
  if (participantInvitationApplicantToken.value && value.trim() !== participantInvitationApplicantToken.value) {
    participantInvitations.value = []
    participantInvitationApplicantToken.value = ''
    participantInvitationProtocolId.value = ''
    receiptPath.value = ''
    invitationCopyNotice.value = ''
  }
}, { flush: 'sync' })

function addParticipant() {
  if (participantDrafts.value.length >= 20) return
  participantDrafts.value.push({ name: '', identityValues: {} })
}

function removeParticipant(index: number) {
  participantDrafts.value.splice(index, 1)
}

function participantFieldRules(field: PublicParticipantField) {
  return [
    (value: string) => !field.required || !!value?.trim() || `Informe ${field.label.toLowerCase()}.`,
    (value: string) => !value || value.length <= 500 || 'Use até 500 caracteres.',
    (value: string) => !value || field.inputType !== 'EMAIL' || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
      || 'Informe um e-mail válido.'
  ]
}

function participantFieldInputType(field: PublicParticipantField) {
  if (field.inputType === 'EMAIL') return 'email'
  if (field.inputType === 'PHONE') return 'tel'
  return 'text'
}

function participantInvitationLink(invitation: PublicParticipantInvitation) {
  return `${window.location.origin}${window.location.pathname}#token=${encodeURIComponent(invitation.token)}`
}

async function copyParticipantInvitation(invitation: PublicParticipantInvitation) {
  invitationCopyNotice.value = ''
  try {
    await navigator.clipboard.writeText(participantInvitationLink(invitation))
    invitationCopyNotice.value = `Convite de ${invitation.name} copiado.`
  } catch {
    invitationCopyNotice.value = 'Não foi possível copiar o convite. Selecione e copie o link exibido.'
  }
}

async function confirmParticipantRevocation() {
  if (!tracking.value?.applicantView || !participantRevocation.value || !token.value.trim()) return
  const selected = participantRevocation.value
  participantRevoking.value = true
  participantNotice.value = ''
  try {
    await publicApi.post<void>(
      `/public/protocols/${encodeURIComponent(tracking.value.id)}/participants/${encodeURIComponent(selected.id)}/revoke`,
      undefined,
      { headers: { Authorization: `Bearer ${token.value.trim()}` } }
    )
    participantRevocation.value = null
    participantNotice.value = `O acesso de ${selected.name} foi revogado.`
    await lookup()
  } catch {
    participantNotice.value = 'Não foi possível revogar esse acesso. Consulte o protocolo e tente novamente.'
  } finally {
    participantRevoking.value = false
  }
}

function attachmentRules(requirement: PublicProtocolSubject['attachmentRequirements'][number]) {
  const rules: Array<(value: any) => true | string> = []
  if (requirement.required) rules.push(value => !!value || `Anexe ${requirement.name}.`)
  rules.push(value => {
    if (!value) return true
    const maximum = selectedSubject.value?.maximumAttachmentSizeBytes ?? 0
    return maximum > 0 && value.size <= maximum
      || `O arquivo deve ter no máximo ${formatFileSize(maximum)}.`
  })
  return rules
}

function setAttachmentFieldRef(requirementId: string, field: unknown) {
  if (field && typeof (field as { focus?: unknown }).focus === 'function') {
    attachmentFieldRefs.set(requirementId, field as { focus: () => void })
  } else {
    attachmentFieldRefs.delete(requirementId)
  }
}

function clearAttachmentFieldError(requirementId: string) {
  delete attachmentFieldErrors.value[requirementId]
}

function setAttachmentFieldError(requirementId: string, message: string) {
  attachmentFieldErrors.value[requirementId] = message
  void nextTick(() => attachmentFieldRefs.get(requirementId)?.focus())
}

function attachmentErrorMessage(message: string, status: number) {
  if (status === 413 || /limite de tamanho|excede.{0,30}limite|excede o limite/i.test(message)) {
    const maximum = selectedSubject.value?.maximumAttachmentSizeBytes ?? 0
    return maximum > 0
      ? `O arquivo excede o limite de ${formatFileSize(maximum)} por arquivo.`
      : 'O tamanho do arquivo excede o limite permitido.'
  }
  if (/conteúdo.{0,50}formato|extensão.{0,50}não é aceita|extensão permitida/i.test(message)) {
    return 'O arquivo não corresponde a um formato aceito. Escolha outro arquivo.'
  }
  return message
}

async function createProtocol() {
  trackingLookupGeneration += 1
  tracking.value = null
  loading.value = true; error.value = ''; notice.value = ''
  participantInvitations.value = []
  participantInvitationApplicantToken.value = ''
  participantInvitationProtocolId.value = ''
  receiptPath.value = ''
  invitationCopyNotice.value = ''
  attachmentFieldErrors.value = {}
  const selectedFiles = (selectedSubject.value?.attachmentRequirements || [])
    .map(requirement => ({ requirementId: requirement.id, file: filesByRequirement.value[requirement.id] }))
    .filter((entry): entry is { requirementId: string; file: File } => entry.file instanceof File)
  uploadingAttachments.value = selectedFiles.length > 0
  try {
    const requestBody = {
      ...form.value,
      participants: participantDrafts.value.map(participant => ({
        name: participant.name.trim(),
        identityValues: Object.fromEntries(Object.entries(participant.identityValues)
          .map(([key, value]) => [key, value.trim()]).filter(([, value]) => value))
      }))
    }
    const body: FormData | typeof requestBody = selectedFiles.length
      ? (() => {
          const multipart = new FormData()
          multipart.append('request', new Blob([JSON.stringify(requestBody)], { type: 'application/json' }))
          for (const entry of selectedFiles) {
            multipart.append('files', entry.file, entry.file.name)
            multipart.append('attachmentRequirementIds', entry.requirementId)
          }
          return multipart
        })()
      : requestBody
    const response = await publicApi.post<{ id: string; trackingToken: string; receiptPath: string; protocolNumber: string; participantInvitations: PublicParticipantInvitation[] }>(
      '/public/protocols', body, { timeoutMs: selectedFiles.length ? 120_000 : undefined }
    )
    token.value = response.data.trackingToken
    receiptPath.value = response.data.receiptPath
    participantInvitations.value = response.data.participantInvitations || []
    participantInvitationApplicantToken.value = response.data.trackingToken
    participantInvitationProtocolId.value = response.data.id
    notice.value = `Protocolo ${response.data.protocolNumber} registrado. Guarde o comprovante para consultar o andamento.`
    tab.value = 'tracking'
    await lookup()
  } catch (e: any) {
    const status = e.response?.status || 0
    const serverMessage = typeof e.response?.data?.message === 'string' ? e.response.data.message : ''
    const serverRequirementId = e.response?.data?.attachmentRequirementId
    const requirementId = selectedFiles.some(entry => entry.requirementId === serverRequirementId)
      ? serverRequirementId as string
      : status === 413 ? selectedFiles[0]?.requirementId : undefined
    if (requirementId) {
      const fieldMessage = status === 413
        ? 'O tamanho total dos documentos excedeu o limite desta solicitação. Reduza os arquivos e tente novamente.'
        : attachmentErrorMessage(serverMessage, status)
      setAttachmentFieldError(requirementId, fieldMessage || 'Revise este documento e tente novamente.')
      error.value = 'Revise o documento indicado para continuar.'
    } else {
      error.value = status === 404
        ? 'Este assunto não está mais disponível online. Atualize a página e escolha outro.'
        : serverMessage || 'Não foi possível registrar a solicitação. Confira os campos e tente novamente.'
    }
  }
  finally { loading.value = false; uploadingAttachments.value = false }
}

async function lookup() {
  if (!token.value.trim()) { error.value = 'Informe a chave que aparece no comprovante.'; return }
  const requestedToken = token.value.trim()
  const requestGeneration = ++trackingLookupGeneration
  if (requestedToken !== participantInvitationApplicantToken.value) {
    participantInvitations.value = []
    participantInvitationApplicantToken.value = ''
    participantInvitationProtocolId.value = ''
    receiptPath.value = ''
    invitationCopyNotice.value = ''
  }
  trackingLookupLoading.value = true; error.value = ''
  tracking.value = null
  citizenInteractionError.value = ''
  citizenInteractionNotice.value = ''
  try {
    const response = await publicApi.get<PublicProtocolTracking>('/public/protocols/tracking', {
      headers: { Authorization: `Bearer ${requestedToken}` }
    })
    if (requestGeneration === trackingLookupGeneration && requestedToken === token.value.trim())
      tracking.value = response.data
  }
  catch {
    if (requestGeneration === trackingLookupGeneration && requestedToken === token.value.trim())
      error.value = 'Protocolo não localizado. Confira a chave do comprovante.'
  }
  finally {
    if (requestGeneration === trackingLookupGeneration) trackingLookupLoading.value = false
  }
}

function interactionFileExtension(filename: string) {
  const dot = filename.lastIndexOf('.')
  return dot > 0 ? filename.slice(dot).toLowerCase() : ''
}

async function sendCitizenInteraction() {
  if (!tracking.value || !token.value.trim()) return
  const message = citizenInteractionMessage.value.trim()
  if (!message) {
    citizenInteractionError.value = 'Escreva uma mensagem antes de enviar.'
    return
  }
  const allowedExtensions = citizenInteractionAllowedExtensions.value.map(extension => extension.toLowerCase())
  const invalidFile = citizenInteractionFiles.value.find(file => !allowedExtensions.includes(interactionFileExtension(file.name)))
  if (invalidFile) {
    citizenInteractionError.value = `O formato de “${invalidFile.name}” não está disponível para este assunto.`
    return
  }

  const multipart = new FormData()
  multipart.append('request', new Blob([JSON.stringify({ message })], { type: 'application/json' }))
  for (const file of citizenInteractionFiles.value) multipart.append('files', file, file.name)
  citizenInteractionSubmitting.value = true
  citizenInteractionError.value = ''
  citizenInteractionNotice.value = ''
  try {
    await publicApi.post<PublicProtocolInteraction>(
      `/public/protocols/${encodeURIComponent(tracking.value.id)}/interactions`,
      multipart,
      { headers: { Authorization: `Bearer ${token.value.trim()}` }, timeoutMs: 120_000 }
    )
    citizenInteractionMessage.value = ''
    citizenInteractionFiles.value = []
    await lookup()
    citizenInteractionNotice.value = 'Mensagem enviada.'
  } catch (e: any) {
    const status = e.response?.status || 0
    const serverMessage = typeof e.response?.data?.message === 'string' ? e.response.data.message : ''
    citizenInteractionError.value = status === 409 || tracking.value?.status === 'COMPLETED'
      ? 'Este protocolo foi concluído. Não é possível enviar novas mensagens.'
      : serverMessage || 'Não foi possível enviar a mensagem. Tente novamente.'
  } finally {
    citizenInteractionSubmitting.value = false
  }
}

async function downloadReceipt() {
  if (!receiptPath.value || !token.value) return
  try {
    const response = await publicApi.get<Blob>(receiptPath.value.replace('/api/v1', ''), { responseType: 'blob', headers: { Authorization: `Bearer ${token.value}` } })
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url; anchor.download = 'comprovante-protocolo.pdf'; anchor.click(); URL.revokeObjectURL(url)
  } catch { error.value = 'Não foi possível obter o comprovante agora.' }
}

async function downloadAttachment(attachment: PublicProtocolAttachment) {
  if (!token.value || downloadingAttachmentId.value) return
  downloadingAttachmentId.value = attachment.id
  error.value = ''
  try {
    const response = await publicApi.get<Blob>(attachment.downloadPath.replace('/api/v1', ''), {
      responseType: 'blob', headers: { Authorization: `Bearer ${token.value.trim()}` }, timeoutMs: 60_000
    })
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = attachment.filename
    anchor.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1_000)
  } catch {
    error.value = 'Não foi possível obter este anexo. Consulte o protocolo novamente e tente de novo.'
  } finally {
    downloadingAttachmentId.value = ''
  }
}

async function downloadInteractionAttachment(attachment: PublicProtocolInteractionAttachment) {
  if (!token.value || downloadingAttachmentId.value) return
  downloadingAttachmentId.value = attachment.id
  citizenInteractionError.value = ''
  try {
    const response = await publicApi.get<Blob>(attachment.downloadPath.replace('/api/v1', ''), {
      responseType: 'blob', headers: { Authorization: `Bearer ${token.value.trim()}` }, timeoutMs: 60_000
    })
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = attachment.filename
    anchor.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1_000)
  } catch {
    citizenInteractionError.value = 'Não foi possível baixar este documento. Consulte o protocolo e tente novamente.'
  } finally {
    downloadingAttachmentId.value = ''
  }
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} bytes`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toLocaleString('pt-BR', { maximumFractionDigits: 0 })} KB`
  return `${(bytes / (1024 * 1024)).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} MB`
}

function formatDate(value: unknown) {
  return typeof value === 'string' ? new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : ''
}
</script>

<template>
  <q-layout view="lHh Lpr lFf" class="app-shell">
    <a class="skip-link" href="#main-content">Pular para o conteúdo</a>
    <q-header class="topbar"><div class="topbar-inner">
      <a class="brand-lockup" href="#main-content" aria-label="NexoGov — início">
        <span class="brand-name">NEXO<span class="brand-gov">GOV</span></span>
        <span class="brand-caption">Gestão pública digital</span>
      </a>
      <q-space />
      <span v-if="isLocalEnvironment" class="environment-label">Ambiente local</span>
      <span class="topbar-context">Portal do cidadão</span>
    </div></q-header>

    <q-page-container><q-page id="main-content" class="content" tabindex="-1">
      <div class="page-heading">
        <h1 class="page-title">Protocolo digital</h1>
        <p class="page-lead">Registre uma solicitação para a organização pública ou acompanhe um protocolo existente.</p>
      </div>

      <q-banner v-if="notice" class="feedback-banner feedback-success" role="status">{{ notice }}</q-banner>
      <q-banner v-if="error" class="feedback-banner feedback-error" role="alert">{{ error }}</q-banner>

      <q-tabs v-model="tab" class="service-tabs" active-color="primary" indicator-color="primary" align="left" narrow-indicator aria-label="Serviços de protocolo">
        <q-tab name="new" label="Nova solicitação" no-caps />
        <q-tab name="tracking" label="Acompanhar protocolo" no-caps />
      </q-tabs>
      <q-separator />

      <q-tab-panels v-model="tab" animated class="service-panels bg-transparent">
        <q-tab-panel name="new" class="q-pa-none">
          <div class="service-layout">
            <section class="service-sheet section-card" aria-labelledby="new-request-title">
              <h2 id="new-request-title" class="section-heading">Nova solicitação</h2>
              <p class="section-subheading">Informe os dados do pedido. Os campos com * são obrigatórios.</p>
              <q-form class="form-grid" @submit.prevent="createProtocol">
                <q-select v-model="form.organizationSlug" :options="organizations.map(o => ({ label: o.name, value: o.slug }))" :disable="loading || organizations.length === 0" :rules="[value => !!value || 'Selecione uma organização.']" emit-value map-options outlined dense label="Organização destinatária *" />
                <q-input v-model="form.applicantName" :rules="[value => !!value?.trim() || 'Informe seu nome.']" outlined dense label="Nome completo *" maxlength="180" autocomplete="name" />
                <q-input v-model="form.applicantEmail" :rules="[value => !!value?.trim() || 'Informe seu e-mail.']" outlined dense type="email" label="E-mail *" maxlength="254" autocomplete="email" />
                <q-select v-model="form.subjectId" :options="protocolSubjects.map(subject => ({ label: subject.name, value: subject.id }))" :disable="loading || subjectsLoading || protocolSubjects.length === 0" :rules="[value => !!value || 'Selecione um assunto.']" emit-value map-options outlined dense label="Assunto *" />
                <p v-if="subjectsLoading" class="subject-list-status" role="status">Carregando assuntos disponíveis…</p>
                <p v-else-if="subjectLoadError" class="subject-list-status subject-list-error" role="alert">{{ subjectLoadError }}</p>
                <p v-else-if="form.organizationSlug && protocolSubjects.length === 0" class="subject-list-status" role="status">Esta organização ainda não disponibilizou assuntos para atendimento online. Procure o balcão interno.</p>
                <fieldset v-if="selectedSubject" class="span-two participant-registration" :disabled="loading">
                  <legend>Pessoas que também acompanharão</legend>
                  <p class="participant-guidance">Os dados informados ficam vinculados a este protocolo e disponíveis a servidores autorizados para atendimento. Avise a pessoa indicada sobre essa finalidade. O convite concede acesso a quem possuir o link; compartilhe-o somente com ela. Consulte a política de privacidade do órgão sobre retenção e direitos.</p>
                  <p v-if="selectedSubject.participantFields.length === 0" class="subject-list-status">A organização não configurou campos adicionais de identificação para este assunto.</p>
                  <div v-for="(participant, participantIndex) in participantDrafts" :key="participantIndex" class="participant-draft">
                    <div class="participant-draft-heading">
                      <h3>Pessoa {{ participantIndex + 1 }}</h3>
                      <q-btn flat dense no-caps color="primary" label="Remover pessoa"
                        :aria-label="`Remover pessoa ${participantIndex + 1}`" @click="removeParticipant(participantIndex)" />
                    </div>
                    <q-input v-model="participant.name" outlined dense maxlength="180" label="Nome da pessoa *"
                      :rules="[value => !!value?.trim() || 'Informe o nome da pessoa.']" />
                    <q-input v-for="field in selectedSubject.participantFields" :key="field.key"
                      v-model="participant.identityValues[field.key]" outlined dense maxlength="500"
                      :type="participantFieldInputType(field)" :label="`${field.label}${field.required ? ' *' : ''}`"
                      :autocomplete="field.inputType === 'EMAIL' ? 'email' : 'off'"
                      :rules="participantFieldRules(field)" />
                  </div>
                  <q-btn v-if="participantDrafts.length < 20" outline no-caps color="primary" label="Adicionar pessoa"
                    :disable="loading" @click="addParticipant" />
                  <p v-else class="subject-list-status" role="status">Limite de 20 pessoas por solicitação.</p>
                </fieldset>
                <fieldset v-if="activeAttachmentRequirements.length" class="span-two citizen-attachment-fields" :disabled="loading">
                  <legend>Documentos para este assunto</legend>
                  <p class="attachment-guidance">Envie um arquivo por tipo de documento. Os campos com * são obrigatórios.</p>
                  <q-file
                    v-for="requirement in activeAttachmentRequirements"
                    :key="requirement.id"
                    :ref="field => setAttachmentFieldRef(requirement.id, field)"
                    v-model="filesByRequirement[requirement.id]"
                    :rules="attachmentRules(requirement)"
                    :accept="requirement.allowedExtensions.join(',')"
                    :label="`${requirement.name}${requirement.required ? ' *' : ''}`"
                    :hint="`Tipos aceitos: ${requirement.allowedExtensions.join(', ')}. Até ${formatFileSize(selectedSubject?.maximumAttachmentSizeBytes || 0)} por arquivo.`"
                    :error="!!attachmentFieldErrors[requirement.id]"
                    :error-message="attachmentFieldErrors[requirement.id]"
                    outlined dense clearable
                    @update:model-value="clearAttachmentFieldError(requirement.id)"
                  />
                </fieldset>
                <p v-if="uploadingAttachments" class="span-two attachment-upload-status" role="status" aria-live="polite">Enviando a solicitação e os documentos. Aguarde a confirmação.</p>
                <q-input v-model="form.initialMessage" :rules="[value => !!value?.trim() || 'Descreva sua solicitação.']" class="span-two" outlined type="textarea" autogrow label="Descreva sua solicitação *" maxlength="8000" />
                <div class="span-two form-actions"><q-btn color="primary" unelevated type="submit" :loading="loading" label="Registrar solicitação" no-caps icon-right="arrow_forward" /></div>
              </q-form>
            </section>
            <aside class="service-aside" aria-labelledby="aside-title">
              <h2 id="aside-title" class="aside-heading">Guarde o comprovante</h2>
              <p class="aside-copy">Ele contém o número do protocolo e a chave necessária para consultar as movimentações.</p>
              <q-separator class="aside-rule" />
              <h3 class="aside-secondary-heading">Acompanhe as movimentações</h3>
              <p class="aside-copy">O pedido passa a ter um histórico de tramitação. Consulte esse histórico usando a chave do comprovante.</p>
              <button class="aside-link" type="button" @click="tab = 'tracking'">Já tenho um protocolo</button>
            </aside>
          </div>
        </q-tab-panel>

        <q-tab-panel name="tracking" class="q-pa-none">
          <section class="service-sheet section-card tracking-sheet" aria-labelledby="tracking-title">
            <div class="toolbar-row tracking-heading"><div><h2 id="tracking-title" class="section-heading">Acompanhe o protocolo</h2><p class="section-subheading">Informe a chave que aparece no comprovante.</p></div>
              <q-btn v-if="canDownloadReceipt" outline color="primary" icon="picture_as_pdf" label="Baixar comprovante" no-caps @click="downloadReceipt" /></div>
            <div class="tracking-controls"><q-input v-model="token" outlined label="Chave do comprovante" autocomplete="off" @keyup.enter="lookup" /><q-btn color="primary" unelevated :loading="trackingLookupLoading" label="Consultar" no-caps @click="lookup" /></div>
            <section v-if="tracking" class="tracking-result q-pt-lg">
              <div class="toolbar-row"><div><div class="protocol-number">{{ tracking.protocolNumber }}</div><div class="protocol-subject q-mt-xs">{{ tracking.subject }}</div></div><span class="status-label">{{ statusLabels[String(tracking.status)] || tracking.status }}</span></div>
              <p v-if="!tracking.applicantView && tracking.participantName" class="participant-current-role" role="status">Este convite foi criado para {{ tracking.participantName }}. O acesso é concedido pelo link e o nome não confirma a identidade de quem o abriu.</p>
              <div class="muted q-mt-sm">Aberto em {{ formatDate(tracking.createdAt) }}</div><q-separator class="q-my-md" />
              <section v-if="visibleParticipantInvitations.length" class="participant-invitations" aria-labelledby="participant-invitations-title">
                <h3 id="participant-invitations-title" class="subsection-heading">Convites para outras pessoas</h3>
                <p>Estes links são chaves de acesso: qualquer pessoa que os possuir poderá consultar e enviar mensagens. Envie somente à pessoa indicada; o nome e os dados informados não confirmam a identidade de quem abrir o link.</p>
                <ul class="participant-invitation-list">
                  <li v-for="invitation in visibleParticipantInvitations" :key="invitation.id">
                    <q-input :model-value="participantInvitationLink(invitation)" readonly outlined dense
                      :label="`Convite de ${invitation.name}`" />
                    <q-btn outline no-caps color="primary" label="Copiar convite"
                      :aria-label="`Copiar convite de ${invitation.name}`" @click="copyParticipantInvitation(invitation)" />
                  </li>
                </ul>
                <p v-if="invitationCopyNotice" class="member-feedback" role="status">{{ invitationCopyNotice }}</p>
              </section>
              <section v-if="tracking.applicantView && tracking.participants.length" class="participant-access-list" aria-labelledby="participant-access-title">
                <h3 id="participant-access-title" class="subsection-heading">Acessos de pessoas vinculadas</h3>
                <p>Você pode revogar uma chave individual. As demais pessoas continuarão com acesso.</p>
                <ul>
                  <li v-for="participant in tracking.participants" :key="participant.id">
                    <span>{{ participant.name }} <span class="muted">{{ participant.active ? 'Acesso ativo' : 'Acesso revogado' }}</span></span>
                    <q-btn v-if="participant.active" flat dense no-caps color="primary" label="Revogar acesso"
                      :aria-label="`Revogar acesso de ${participant.name}`"
                      @click="participantRevocation = { id: participant.id, name: participant.name }" />
                  </li>
                </ul>
                <p v-if="participantNotice" class="member-feedback" role="status">{{ participantNotice }}</p>
              </section>
              <section v-if="!tracking.applicantView && tracking.participantFields.length" class="participant-own-details" aria-labelledby="participant-own-details-title">
                <h3 id="participant-own-details-title" class="subsection-heading">Seus dados de identificação</h3>
                <dl>
                  <div v-for="field in tracking.participantFields" :key="field.key">
                    <dt>{{ field.label }}</dt><dd>{{ field.value }}</dd>
                  </div>
                </dl>
              </section>
              <div v-for="(event, index) in tracking.events || []" :key="index" class="history-event">
                <span class="history-marker" aria-hidden="true"></span>
                <div><div class="text-weight-medium">{{ event.summary }}</div><div class="muted text-caption">{{ formatDate(event.createdAt) }}</div></div>
              </div>
              <section class="tracking-attachments" aria-labelledby="tracking-attachments-title">
                <h3 id="tracking-attachments-title" class="subsection-heading">Documentos anexados</h3>
                <p v-if="openingTrackingAttachments.length === 0" class="empty-state" role="status">Este protocolo não tem documentos de abertura anexados.</p>
                <ul v-else class="attachment-list" aria-label="Documentos anexados ao protocolo">
                  <li v-for="attachment in openingTrackingAttachments" :key="attachment.id" class="attachment-row">
                    <div class="attachment-details">
                      <span class="attachment-name">{{ attachment.filename }}</span>
                      <span class="muted text-caption">{{ formatFileSize(attachment.sizeBytes) }} · {{ formatDate(attachment.uploadedAt) }}</span>
                    </div>
                    <q-btn
                      color="primary" outline no-caps label="Baixar arquivo"
                      :loading="downloadingAttachmentId === attachment.id"
                      :disable="!!downloadingAttachmentId"
                      :aria-label="`Baixar ${attachment.filename}`"
                      @click="downloadAttachment(attachment)"
                    />
                  </li>
                </ul>
              </section>
              <section class="citizen-interactions" aria-labelledby="citizen-interactions-title">
                <div class="citizen-interactions-heading">
                  <h3 id="citizen-interactions-title" class="subsection-heading">Mensagens</h3>
                  <p class="muted">As mensagens e os documentos enviados aqui ficam visíveis para você e para a equipe responsável.</p>
                </div>
                <p v-if="visibleTrackingInteractions.length === 0" class="empty-state" role="status">Ainda não há mensagens neste protocolo.</p>
                <ol v-else class="interaction-list" aria-label="Mensagens visíveis ao requerente" aria-live="polite" aria-relevant="additions text">
                  <li v-for="interaction in visibleTrackingInteractions" :key="interaction.id" class="interaction-entry">
                    <div class="interaction-entry-heading">
                      <strong class="interaction-author">{{ interaction.author }}</strong>
                      <span class="muted interaction-date">{{ formatDate(interaction.createdAt) }}</span>
                    </div>
                    <p v-if="interaction.department" class="interaction-department">{{ interaction.department }}</p>
                    <p class="interaction-message">{{ interaction.message }}</p>
                    <ul v-if="interaction.attachments?.length" class="interaction-files" :aria-label="`Documentos da mensagem de ${interaction.author}`">
                      <li v-for="attachment in interaction.attachments" :key="attachment.id" class="interaction-file-row">
                        <span class="attachment-name">{{ attachment.filename }}</span>
                        <q-btn
                          color="primary" outline no-caps label="Baixar arquivo"
                          :aria-label="`Baixar ${attachment.filename}`"
                          :loading="downloadingAttachmentId === attachment.id"
                          :disable="!!downloadingAttachmentId"
                          @click="downloadInteractionAttachment(attachment)"
                        />
                      </li>
                    </ul>
                  </li>
                </ol>
                <p v-if="citizenInteractionNotice" class="member-feedback member-feedback-success" role="status">{{ citizenInteractionNotice }}</p>
                <p v-if="citizenInteractionError" class="member-feedback member-feedback-error" role="alert">{{ citizenInteractionError }}</p>
                <p v-if="tracking.status === 'COMPLETED'" class="interaction-closed-note" role="status">Este protocolo foi concluído. Não é possível enviar novas mensagens.</p>
                <q-form v-else class="citizen-interaction-form" aria-label="Enviar mensagem ao protocolo" @submit.prevent="sendCitizenInteraction">
                  <q-input
                    v-model="citizenInteractionMessage"
                    outlined type="textarea" autogrow maxlength="8000"
                    label="Sua mensagem" hint="Não inclua senhas ou dados de acesso."
                    :disable="citizenInteractionSubmitting"
                  />
                  <q-file
                    v-if="citizenInteractionAllowedExtensions.length"
                    v-model="citizenInteractionFiles"
                    :accept="citizenInteractionAllowedExtensions.join(',')"
                    multiple clearable outlined dense
                    label="Anexar documentos (opcional)"
                    :hint="`Tipos aceitos: ${citizenInteractionAllowedExtensions.join(', ')}`"
                    :disable="citizenInteractionSubmitting"
                  />
                  <p v-else class="interaction-format-note" role="status">Este assunto não aceita documentos em mensagens posteriores. Você ainda pode enviar uma mensagem.</p>
                  <div class="interaction-form-actions">
                    <q-btn color="primary" unelevated no-caps type="submit" label="Enviar mensagem" :loading="citizenInteractionSubmitting" :disable="!citizenInteractionMessage.trim()" />
                  </div>
                </q-form>
              </section>
            </section>
          </section>
        </q-tab-panel>
      </q-tab-panels>

      <q-dialog :model-value="!!participantRevocation" @update:model-value="value => { if (!value && !participantRevoking) participantRevocation = null }">
        <q-card class="participant-revocation-dialog" role="dialog" aria-labelledby="participant-revocation-title">
          <q-card-section>
            <h2 id="participant-revocation-title" class="section-heading">Revogar acesso?</h2>
            <p>A chave de {{ participantRevocation?.name }} deixará de abrir este protocolo. A pessoa requerente continuará com acesso.</p>
          </q-card-section>
          <q-card-actions align="right">
            <q-btn flat no-caps label="Cancelar" :disable="participantRevoking" @click="participantRevocation = null" />
            <q-btn color="primary" unelevated no-caps label="Revogar acesso" :loading="participantRevoking" @click="confirmParticipantRevocation" />
          </q-card-actions>
        </q-card>
      </q-dialog>

      <footer class="app-footer">NexoGov <span aria-hidden="true">·</span> Serviços públicos digitais</footer>
    </q-page></q-page-container>
  </q-layout>
</template>
