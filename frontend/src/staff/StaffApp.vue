<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { publicApi, type Organization } from '../api/public'
import { organizationHeaders, staffApi, type AttachmentRequirement, type Department, type DepartmentMember, type OrganizationMember, type OrganizationMemberAccessResult, type OrganogramNode, type ParticipantIdentityField, type ParticipantIdentityFieldInput, type ParticipantIdentityFieldType, type Protocol, type ProtocolAttachment, type ProtocolInteraction, type ProtocolInteractionAttachment, type ProtocolInteractionVisibility, type ProtocolReadReceipt, type ProtocolReadStatus, type StaffProtocolSubject } from '../api/staff'
import { beginOidcSignIn, clearStaffAuthorization, completeOidcCallback, hasStaffAuthorization, setLocalBasicCredentials, signOutStaff, staffAuthMode } from '../auth/staff-session'
import AdministrativeProcessPanel from './AdministrativeProcessPanel.vue'

interface ProtocolSubjectDraft {
  onlineAvailable: boolean
  destinationDepartmentId: string | null
  automaticDeadlineDays: number | null
}

interface AttachmentRequirementDraft {
  name: string
  required: boolean
  allowedExtensions: string[]
}

const attachmentExtensionOptions = ['.pdf', '.png', '.jpg', '.jpeg', '.txt', '.mp4']
const participantFieldTypeOptions: Array<{ label: string; value: ParticipantIdentityFieldType }> = [
  { label: 'Texto', value: 'TEXT' },
  { label: 'E-mail', value: 'EMAIL' },
  { label: 'Telefone', value: 'PHONE' },
  { label: 'Documento', value: 'DOCUMENT' }
]

const organizations = ref<Organization[]>([])
const organizationsLoading = ref(true)
const organizationsLoadFailed = ref(false)
const organizationId = ref('')
const username = ref('')
const password = ref('')
const departments = ref<Department[]>([])
const organogram = ref<OrganogramNode[]>([])
const protocols = ref<Protocol[]>([])
const protocolSubjects = ref<StaffProtocolSubject[]>([])
const organizationMembers = ref<OrganizationMember[]>([])
const canManageDepartments = ref(false)
const staffLoaded = ref(false)
const loading = ref(false)
const administrativeProcessBusy = ref(false)
const membersLoading = ref(false)
const memberActionUsername = ref('')
const membersLoadError = ref('')
const memberActionError = ref('')
const membersNotice = ref('')
const pendingSuspension = ref<OrganizationMember | null>(null)
const showSuspensionDialog = ref(false)
const notice = ref('')
const error = ref('')
const destinationDepartmentId = ref('')
const newDepartmentName = ref('')
const parentDepartmentId = ref<string | null>(null)
const parentDepartmentOptions = computed(() => flattenDepartments(organogram.value))
const selectedStaffUsername = ref('')
const staffProfileName = ref('')
const staffPositionTitle = ref('')
const staffRegistrationDate = ref('')
const protocolSubjectName = ref('')
const protocolSubjectOnlineAvailable = ref(true)
const protocolSubjectDestinationDepartmentId = ref<string | null>(null)
const protocolSubjectAutomaticDeadlineDays = ref<number | null>(null)
const protocolSubjectDrafts = reactive<Record<string, ProtocolSubjectDraft>>({})
const newAttachmentDrafts = reactive<Record<string, AttachmentRequirementDraft>>({})
const attachmentRequirementDrafts = reactive<Record<string, AttachmentRequirementDraft>>({})
const expandedParticipantSubjectId = ref('')
const participantFieldRemoval = ref<{ subject: StaffProtocolSubject; field: ParticipantIdentityField } | null>(null)
const showParticipantFieldRemovalDialog = ref(false)
const participantFieldsBySubject = reactive<Record<string, ParticipantIdentityField[]>>({})
const participantFieldDrafts = reactive<Record<string, Record<string, ParticipantIdentityFieldInput>>>({})
const newParticipantFieldDrafts = reactive<Record<string, ParticipantIdentityFieldInput>>({})
const participantFieldsLoading = reactive<Record<string, boolean>>({})
const participantFieldsSaving = reactive<Record<string, boolean>>({})
const participantFieldsLoaded = reactive<Record<string, boolean>>({})
const participantFieldsErrors = reactive<Record<string, string>>({})
const participantFieldsNotices = reactive<Record<string, string>>({})
const attachmentPolicyActionId = ref('')
const expandedProtocolId = ref('')
const protocolAttachments = reactive<Record<string, ProtocolAttachment[]>>({})
const protocolAttachmentLoading = reactive<Record<string, boolean>>({})
const protocolAttachmentErrors = reactive<Record<string, string>>({})
const protocolAttachmentDownloadErrors = reactive<Record<string, string>>({})
const downloadingProtocolAttachmentId = ref('')
const expandedInteractionProtocolId = ref('')
const protocolInteractions = reactive<Record<string, ProtocolInteraction[]>>({})
const protocolInteractionLoading = reactive<Record<string, boolean>>({})
const protocolInteractionErrors = reactive<Record<string, string>>({})
const protocolInteractionNotices = reactive<Record<string, string>>({})
const protocolReadErrors = reactive<Record<string, string>>({})
const protocolInteractionDrafts = reactive<Record<string, { message: string; visibility: ProtocolInteractionVisibility; files: File[] }>>({})
const protocolInteractionDownloadErrors = reactive<Record<string, string>>({})
const downloadingProtocolInteractionAttachmentId = ref('')
const submittingProtocolInteractionId = ref('')
const protocolSubjectsLoading = ref(false)
const protocolRoutingDepartmentsLoading = ref(false)
const protocolRoutingDepartmentsError = ref('')
const protocolSubjectSaving = ref(false)
const protocolSubjectActionId = ref('')
const protocolSubjectsError = ref('')
const protocolSubjectsNotice = ref('')
const staffOptions = computed(() => flattenStaff(organogram.value))
let presenceTimer: number | undefined
let membersRequestVersion = 0
let staffContextGeneration = 0
const isLocalEnvironment = import.meta.env.DEV
const authMode = ref(staffAuthMode())
const authReady = ref(false)
const oidcAuthenticated = ref(false)

const statusLabels: Record<string, string> = {
  OPEN: 'Aberto', FORWARDED: 'Encaminhado', RECEIVED: 'Recebido', DISPATCHED: 'Despachado', COMPLETED: 'Concluído'
}
const protocolReadStatusLabels: Record<ProtocolReadStatus, string> = {
  UNREAD_BY_DEPARTMENT: 'Ainda não lido por ninguém do setor',
  READ_BY_OTHER: 'Outro servidor habilitado do setor já leu; você ainda não',
  READ_BY_ME: 'Você já leu'
}

watch(organizationId, () => {
  staffContextGeneration++
  membersRequestVersion++
  canManageDepartments.value = false
  departments.value = []
  organogram.value = []
  protocols.value = []
  protocolSubjects.value = []
  organizationMembers.value = []
  membersLoading.value = false
  membersLoadError.value = ''
  memberActionError.value = ''
  membersNotice.value = ''
  protocolSubjectsError.value = ''
  protocolSubjectsNotice.value = ''
  protocolSubjectsLoading.value = false
  protocolRoutingDepartmentsLoading.value = false
  protocolRoutingDepartmentsError.value = ''
  protocolSubjectSaving.value = false
  protocolSubjectActionId.value = ''
  attachmentPolicyActionId.value = ''
  protocolSubjectName.value = ''
  protocolSubjectOnlineAvailable.value = true
  protocolSubjectDestinationDepartmentId.value = null
  protocolSubjectAutomaticDeadlineDays.value = null
  for (const subjectId of Object.keys(protocolSubjectDrafts)) delete protocolSubjectDrafts[subjectId]
  clearParticipantFieldState()
  clearProtocolAttachmentState()
  memberActionUsername.value = ''
  pendingSuspension.value = null
  showSuspensionDialog.value = false
  staffLoaded.value = false
  destinationDepartmentId.value = ''
  newDepartmentName.value = ''
  parentDepartmentId.value = null
  selectedStaffUsername.value = ''
  staffProfileName.value = ''
  staffPositionTitle.value = ''
  staffRegistrationDate.value = ''
}, { flush: 'sync' })

watch(administrativeProcessBusy, busy => {
  if (!busy && notice.value.startsWith('Aguarde a confirmação da operação do processo')) notice.value = ''
})

function isCurrentStaffContext(generation: number, selectedOrganizationId: string) {
  return generation === staffContextGeneration && selectedOrganizationId === organizationId.value
}

function clearParticipantFieldState() {
  expandedParticipantSubjectId.value = ''
  participantFieldRemoval.value = null
  showParticipantFieldRemovalDialog.value = false
  for (const subjectId of Object.keys(participantFieldsBySubject)) delete participantFieldsBySubject[subjectId]
  for (const subjectId of Object.keys(participantFieldDrafts)) delete participantFieldDrafts[subjectId]
  for (const subjectId of Object.keys(newParticipantFieldDrafts)) delete newParticipantFieldDrafts[subjectId]
  for (const subjectId of Object.keys(participantFieldsLoading)) delete participantFieldsLoading[subjectId]
  for (const subjectId of Object.keys(participantFieldsSaving)) delete participantFieldsSaving[subjectId]
  for (const subjectId of Object.keys(participantFieldsLoaded)) delete participantFieldsLoaded[subjectId]
  for (const subjectId of Object.keys(participantFieldsErrors)) delete participantFieldsErrors[subjectId]
  for (const subjectId of Object.keys(participantFieldsNotices)) delete participantFieldsNotices[subjectId]
}

function participantFieldsEndpoint(subjectId: string) {
  return `/staff/organization/protocol-subjects/${subjectId}/participant-fields`
}

function nextParticipantFieldOrder(subjectId: string) {
  const fields = participantFieldsBySubject[subjectId] || []
  const used = new Set(fields.map(field => field.order))
  const highest = fields.reduce((value, field) => Math.max(value, field.order), -1)
  if (highest < 1000) return highest + 1
  for (let order = 0; order <= 1000; order++) {
    if (!used.has(order)) return order
  }
  return 1000
}

function newParticipantFieldDraft(subjectId: string) {
  newParticipantFieldDrafts[subjectId] ??= {
    key: '', label: '', inputType: 'TEXT', required: false,
    order: nextParticipantFieldOrder(subjectId)
  }
  return newParticipantFieldDrafts[subjectId]
}

function hasParticipantFieldChanges(subjectId: string, field: ParticipantIdentityField) {
  const draft = participantFieldDrafts[subjectId]?.[field.id]
  return !!draft && (draft.label.trim() !== field.label || draft.inputType !== field.inputType
    || draft.required !== field.required || draft.order !== field.order)
}

function validParticipantField(draft: ParticipantIdentityFieldInput) {
  return /^[a-z][a-z0-9_]{0,63}$/.test(draft.key)
    && !!draft.label.trim() && draft.label.trim().length <= 120
    && Number.isInteger(draft.order) && draft.order >= 0 && draft.order <= 1000
}

function participantFieldErrorMessage(errorResponse: any, fallback: string) {
  if (errorResponse?.response?.status === 403)
    return 'A configuração dos campos exige perfil de administrador da organização.'
  if (errorResponse?.response?.status === 409)
    return 'Já existe um campo com este identificador neste assunto.'
  return errorResponse?.response?.data?.message || fallback
}

async function loadParticipantFields(subject: StaffProtocolSubject) {
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  participantFieldsLoading[subject.id] = true
  delete participantFieldsErrors[subject.id]
  try {
    const response = await staffApi.get<ParticipantIdentityField[]>(participantFieldsEndpoint(subject.id), {
      headers: organizationHeaders(selectedOrganizationId)
    })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    participantFieldsBySubject[subject.id] = response.data
    participantFieldDrafts[subject.id] = Object.fromEntries(response.data.map(field => [field.id, {
      key: field.key, label: field.label, inputType: field.inputType,
      required: field.required, order: field.order
    }]))
    participantFieldsLoaded[subject.id] = true
    newParticipantFieldDrafts[subject.id] = {
      key: '', label: '', inputType: 'TEXT', required: false, order: nextParticipantFieldOrder(subject.id)
    }
  } catch (e: any) {
    if (isCurrentStaffContext(generation, selectedOrganizationId))
      participantFieldsErrors[subject.id] = participantFieldErrorMessage(e, 'Não foi possível carregar os campos de identificação.')
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) participantFieldsLoading[subject.id] = false
  }
}

async function toggleParticipantFields(subject: StaffProtocolSubject) {
  if (expandedParticipantSubjectId.value === subject.id) {
    expandedParticipantSubjectId.value = ''
    return
  }
  expandedParticipantSubjectId.value = subject.id
  if (!participantFieldsLoaded[subject.id] || participantFieldsErrors[subject.id])
    await loadParticipantFields(subject)
}

async function createParticipantField(subject: StaffProtocolSubject) {
  const draft = newParticipantFieldDraft(subject.id)
  if (!validParticipantField(draft)) {
    participantFieldsErrors[subject.id] = 'Informe um identificador válido, um nome e uma ordem entre 0 e 1000.'
    return
  }
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  participantFieldsSaving[subject.id] = true
  delete participantFieldsErrors[subject.id]
  delete participantFieldsNotices[subject.id]
  try {
    const response = await staffApi.post<ParticipantIdentityField>(participantFieldsEndpoint(subject.id), {
      ...draft, key: draft.key.trim(), label: draft.label.trim()
    }, { headers: organizationHeaders(selectedOrganizationId) })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    participantFieldsBySubject[subject.id] = [...(participantFieldsBySubject[subject.id] || []), response.data]
      .sort((left, right) => left.order - right.order || left.key.localeCompare(right.key, 'pt-BR'))
    participantFieldDrafts[subject.id][response.data.id] = {
      key: response.data.key, label: response.data.label, inputType: response.data.inputType,
      required: response.data.required, order: response.data.order
    }
    newParticipantFieldDrafts[subject.id] = {
      key: '', label: '', inputType: 'TEXT', required: false,
      order: nextParticipantFieldOrder(subject.id)
    }
    participantFieldsNotices[subject.id] = `Campo “${response.data.label}” adicionado ao assunto.`
  } catch (e: any) {
    if (isCurrentStaffContext(generation, selectedOrganizationId))
      participantFieldsErrors[subject.id] = participantFieldErrorMessage(e, 'Não foi possível adicionar este campo.')
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) participantFieldsSaving[subject.id] = false
  }
}

async function saveParticipantField(subject: StaffProtocolSubject, field: ParticipantIdentityField) {
  const draft = participantFieldDrafts[subject.id]?.[field.id]
  if (!draft || !hasParticipantFieldChanges(subject.id, field)) return
  if (!validParticipantField(draft)) {
    participantFieldsErrors[subject.id] = 'Confira o nome e a ordem do campo (0 a 1000).'
    return
  }
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  participantFieldsSaving[subject.id] = true
  delete participantFieldsErrors[subject.id]
  delete participantFieldsNotices[subject.id]
  try {
    const response = await staffApi.put<ParticipantIdentityField>(
      `${participantFieldsEndpoint(subject.id)}/${field.id}`,
      { label: draft.label.trim(), inputType: draft.inputType, required: draft.required, order: draft.order },
      { headers: organizationHeaders(selectedOrganizationId) }
    )
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    participantFieldsBySubject[subject.id] = participantFieldsBySubject[subject.id]
      .map(current => current.id === field.id ? response.data : current)
      .sort((left, right) => left.order - right.order || left.key.localeCompare(right.key, 'pt-BR'))
    participantFieldDrafts[subject.id][field.id] = {
      key: response.data.key, label: response.data.label, inputType: response.data.inputType,
      required: response.data.required, order: response.data.order
    }
    participantFieldsNotices[subject.id] = `Campo “${response.data.label}” atualizado.`
  } catch (e: any) {
    if (isCurrentStaffContext(generation, selectedOrganizationId))
      participantFieldsErrors[subject.id] = participantFieldErrorMessage(e, 'Não foi possível salvar este campo.')
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) participantFieldsSaving[subject.id] = false
  }
}

function requestParticipantFieldRemoval(subject: StaffProtocolSubject, field: ParticipantIdentityField) {
  participantFieldRemoval.value = { subject, field }
  showParticipantFieldRemovalDialog.value = true
}

function cancelParticipantFieldRemoval() {
  showParticipantFieldRemovalDialog.value = false
  participantFieldRemoval.value = null
}

async function confirmParticipantFieldRemoval() {
  const pending = participantFieldRemoval.value
  if (!pending) return
  const { subject, field } = pending
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  cancelParticipantFieldRemoval()
  participantFieldsSaving[subject.id] = true
  delete participantFieldsErrors[subject.id]
  delete participantFieldsNotices[subject.id]
  try {
    await staffApi.delete(`${participantFieldsEndpoint(subject.id)}/${field.id}`, {
      headers: organizationHeaders(selectedOrganizationId)
    })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    participantFieldsBySubject[subject.id] = participantFieldsBySubject[subject.id].filter(current => current.id !== field.id)
    delete participantFieldDrafts[subject.id][field.id]
    participantFieldsNotices[subject.id] = `Campo “${field.label}” removido do assunto.`
  } catch (e: any) {
    if (isCurrentStaffContext(generation, selectedOrganizationId))
      participantFieldsErrors[subject.id] = participantFieldErrorMessage(e, 'Não foi possível remover este campo.')
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) participantFieldsSaving[subject.id] = false
  }
}

function clearProtocolAttachmentState() {
  expandedProtocolId.value = ''
  attachmentPolicyActionId.value = ''
  downloadingProtocolAttachmentId.value = ''
  for (const protocolId of Object.keys(protocolAttachments)) delete protocolAttachments[protocolId]
  for (const protocolId of Object.keys(protocolAttachmentLoading)) delete protocolAttachmentLoading[protocolId]
  for (const protocolId of Object.keys(protocolAttachmentErrors)) delete protocolAttachmentErrors[protocolId]
  for (const attachmentId of Object.keys(protocolAttachmentDownloadErrors)) delete protocolAttachmentDownloadErrors[attachmentId]
  clearProtocolInteractionState()
  for (const subjectId of Object.keys(newAttachmentDrafts)) delete newAttachmentDrafts[subjectId]
  for (const requirementId of Object.keys(attachmentRequirementDrafts)) delete attachmentRequirementDrafts[requirementId]
}

function clearProtocolInteractionState() {
  expandedInteractionProtocolId.value = ''
  submittingProtocolInteractionId.value = ''
  downloadingProtocolInteractionAttachmentId.value = ''
  for (const protocolId of Object.keys(protocolInteractions)) delete protocolInteractions[protocolId]
  for (const protocolId of Object.keys(protocolInteractionLoading)) delete protocolInteractionLoading[protocolId]
  for (const protocolId of Object.keys(protocolInteractionErrors)) delete protocolInteractionErrors[protocolId]
  for (const protocolId of Object.keys(protocolInteractionNotices)) delete protocolInteractionNotices[protocolId]
  for (const protocolId of Object.keys(protocolReadErrors)) delete protocolReadErrors[protocolId]
  for (const protocolId of Object.keys(protocolInteractionDrafts)) delete protocolInteractionDrafts[protocolId]
  for (const attachmentId of Object.keys(protocolInteractionDownloadErrors)) delete protocolInteractionDownloadErrors[attachmentId]
}

async function logoutStaff() {
  if (administrativeProcessBusy.value) {
    notice.value = 'Aguarde a confirmação da operação do processo antes de sair. O resultado será aplicado assim que o serviço responder.'
    return
  }
  const selectedOrganizationId = organizationId.value
  staffContextGeneration++
  username.value = ''
  password.value = ''
  clearStaffAuthorization()
  oidcAuthenticated.value = false
  organizationId.value = selectedOrganizationId
  staffLoaded.value = false
  loading.value = false
  canManageDepartments.value = false
  departments.value = []
  organogram.value = []
  protocols.value = []
  protocolSubjects.value = []
  organizationMembers.value = []
  for (const subjectId of Object.keys(protocolSubjectDrafts)) delete protocolSubjectDrafts[subjectId]
  clearParticipantFieldState()
  clearProtocolAttachmentState()
  clearProtocolInteractionState()
  destinationDepartmentId.value = ''
  newDepartmentName.value = ''
  parentDepartmentId.value = null
  selectedStaffUsername.value = ''
  staffProfileName.value = ''
  staffPositionTitle.value = ''
  staffRegistrationDate.value = ''
  protocolSubjectName.value = ''
  protocolSubjectOnlineAvailable.value = true
  protocolSubjectDestinationDepartmentId.value = null
  error.value = ''
  notice.value = ''
  if (authMode.value === 'oidc') {
    try { await signOutStaff(selectedOrganizationId) }
    catch { error.value = 'A sessão local foi encerrada; não foi possível concluir a saída no provedor.' }
  }
}

function guardPendingProcessUnload(event: BeforeUnloadEvent) {
  if (!administrativeProcessBusy.value) return
  event.preventDefault()
  event.returnValue = ''
}

watch(showSuspensionDialog, visible => {
  if (!visible) pendingSuspension.value = null
})

watch(selectedStaffUsername, username => {
  const selected = staffOptions.value.find(option => option.value === username)?.member
  staffProfileName.value = selected?.fullName || ''
  staffPositionTitle.value = selected?.positionTitle || ''
  staffRegistrationDate.value = selected?.registrationDate || ''
})

watch(staffLoaded, loaded => {
  if (presenceTimer !== undefined) window.clearInterval(presenceTimer)
  presenceTimer = undefined
  if (loaded) {
    void sendPresenceHeartbeat()
    presenceTimer = window.setInterval(() => void sendPresenceHeartbeat(), 60_000)
  }
})

onUnmounted(() => {
  if (presenceTimer !== undefined) window.clearInterval(presenceTimer)
  window.removeEventListener('beforeunload', guardPendingProcessUnload)
})

onMounted(async () => {
  window.addEventListener('beforeunload', guardPendingProcessUnload)
  let shouldLoadReturnedOrganization = false
  try {
    const callback = await completeOidcCallback()
    if (callback.error) error.value = callback.error
    if (callback.signedOut) oidcAuthenticated.value = false
    try {
      organizations.value = (await publicApi.get<Organization[]>('/public/organizations')).data
    } catch {
      organizationsLoadFailed.value = true
      throw new Error('organization-list-unavailable')
    }
    const returnedOrganization = callback.organizationId
    organizationId.value = organizations.value.some(organization => organization.id === returnedOrganization)
      ? returnedOrganization!
      : organizations.value[0]?.id || ''
    shouldLoadReturnedOrganization = callback.handled && !!callback.organizationId && !callback.error
  } catch {
    organizationsLoadFailed.value = true
    error.value = 'Não foi possível conectar ao serviço. Tente novamente em instantes.'
  } finally {
    organizationsLoading.value = false
    authReady.value = true
  }
  if (shouldLoadReturnedOrganization) {
    oidcAuthenticated.value = true
    await loadStaff()
  }
})

async function submitStaffAccess() {
  if (administrativeProcessBusy.value) {
    notice.value = 'Aguarde a confirmação da operação do processo antes de carregar a caixa.'
    return
  }
  error.value = ''
  if (!organizationId.value) { error.value = 'Selecione uma organização.'; return }
  if (authMode.value === 'unavailable') {
    error.value = 'O acesso da área do servidor não está configurado para este ambiente.'
    return
  }
  if (authMode.value === 'oidc') {
    if (oidcAuthenticated.value || hasStaffAuthorization()) {
      await loadStaff()
      return
    }
    try { await beginOidcSignIn(organizationId.value) }
    catch { error.value = 'Não foi possível iniciar o acesso pelo provedor configurado.' }
    return
  }
  await loadStaff()
}

async function loadStaff() {
  if (administrativeProcessBusy.value) {
    notice.value = 'Aguarde a confirmação da operação do processo antes de atualizar a caixa.'
    return
  }
  if (authMode.value === 'unavailable') { error.value = 'O acesso da área do servidor não está configurado para este ambiente.'; return }
  if (authMode.value === 'local-basic') {
    if (!username.value.trim() || !password.value) { error.value = 'Informe usuário e senha.'; return }
    setLocalBasicCredentials(username.value.trim(), password.value)
  }
  if (authMode.value === 'oidc' && !hasStaffAuthorization()) {
    oidcAuthenticated.value = false
    error.value = 'Inicie o acesso pelo provedor configurado.'
    return
  }
  if (!organizationId.value) { error.value = 'Selecione uma organização.'; return }
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  loading.value = true; error.value = ''; notice.value = ''
  protocolRoutingDepartmentsLoading.value = true
  protocolRoutingDepartmentsError.value = ''
  canManageDepartments.value = false
  staffLoaded.value = false
  membersRequestVersion++
  departments.value = []; organogram.value = []; protocols.value = []; protocolSubjects.value = []; organizationMembers.value = []; destinationDepartmentId.value = ''
  clearProtocolAttachmentState()
  clearProtocolInteractionState()
  membersLoading.value = false; membersLoadError.value = ''; memberActionError.value = ''; membersNotice.value = ''
  try {
    const headers = organizationHeaders(selectedOrganizationId)
    const departmentRequest = staffApi.get<Department[]>('/staff/departments', { headers })
      .then(response => ({ departments: response.data, failed: false }))
      .catch(() => ({ departments: [] as Department[], failed: true }))
    const [departmentResult, protocolResponse, capabilityResponse, organogramResponse] = await Promise.all([
      departmentRequest,
      staffApi.get<Protocol[]>('/staff/protocols', { headers }),
      staffApi.get<{ canManageDepartments: boolean }>('/staff/organization/department-management', { headers }),
      staffApi.get<OrganogramNode[]>('/staff/organization/organogram', { headers })
    ])
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    departments.value = departmentResult.departments
    protocolRoutingDepartmentsError.value = departmentResult.failed
      ? 'Não foi possível carregar os setores ativos. Tente novamente.'
      : ''
    protocols.value = protocolResponse.data
    organogram.value = organogramResponse.data
    canManageDepartments.value = capabilityResponse.data.canManageDepartments
    staffLoaded.value = true
    if (departments.value.length) destinationDepartmentId.value = departments.value[0].id
    if (canManageDepartments.value) await Promise.all([loadOrganizationMembers(), loadProtocolSubjects()])
  } catch (e: any) {
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    canManageDepartments.value = false
    departments.value = []; organogram.value = []; protocols.value = []; protocolSubjects.value = []; organizationMembers.value = []; destinationDepartmentId.value = ''; staffLoaded.value = false
    error.value = e.response?.status === 401 || e.response?.status === 403
      ? 'Acesso recusado. Confira suas credenciais e a organização selecionada.'
      : 'Não foi possível carregar a caixa de entrada.'
  }
  finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) {
      loading.value = false
      protocolRoutingDepartmentsLoading.value = false
    }
  }
}

async function reloadRoutingDepartments() {
  if (!canManageDepartments.value || !staffLoaded.value || !organizationId.value) return
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  protocolRoutingDepartmentsLoading.value = true
  protocolRoutingDepartmentsError.value = ''
  try {
    const response = await staffApi.get<Department[]>('/staff/departments', {
      headers: organizationHeaders(selectedOrganizationId)
    })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    departments.value = response.data
    if (!departments.value.some(department => department.id === destinationDepartmentId.value))
      destinationDepartmentId.value = departments.value[0]?.id || ''
  } catch (e: any) {
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    protocolRoutingDepartmentsError.value = e.response?.status === 401 || e.response?.status === 403
      ? 'Seu acesso não permite consultar os setores desta organização.'
      : 'Não foi possível carregar os setores ativos. Tente novamente.'
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) protocolRoutingDepartmentsLoading.value = false
  }
}

async function loadOrganizationMembers() {
  if (!canManageDepartments.value || !staffLoaded.value || !organizationId.value) return
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  const requestVersion = ++membersRequestVersion
  const headers = organizationHeaders(selectedOrganizationId)
  membersLoading.value = true
  membersLoadError.value = ''
  memberActionError.value = ''
  try {
    const response = await staffApi.get<OrganizationMember[]>('/staff/organization/members', { headers })
    if (requestVersion !== membersRequestVersion || !isCurrentStaffContext(generation, selectedOrganizationId)) return
    organizationMembers.value = response.data
  } catch (e: any) {
    if (requestVersion !== membersRequestVersion || !isCurrentStaffContext(generation, selectedOrganizationId)) return
    membersLoadError.value = e.response?.status === 401 || e.response?.status === 403
      ? 'Seu acesso não permite consultar os membros desta organização.'
      : 'Não foi possível carregar os membros. Tente novamente.'
  } finally {
    if (requestVersion === membersRequestVersion && isCurrentStaffContext(generation, selectedOrganizationId)) membersLoading.value = false
  }
}

async function loadProtocolSubjects() {
  if (!canManageDepartments.value || !staffLoaded.value || !organizationId.value) return
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  const headers = organizationHeaders(selectedOrganizationId)
  protocolSubjectsLoading.value = true
  protocolSubjectsError.value = ''
  clearParticipantFieldState()
  try {
    const response = await staffApi.get<StaffProtocolSubject[]>('/staff/organization/protocol-subjects', { headers })
    if (isCurrentStaffContext(generation, selectedOrganizationId)) replaceProtocolSubjects(response.data, true)
  } catch (e: any) {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) {
      protocolSubjectsError.value = e.response?.status === 401 || e.response?.status === 403
        ? 'Seu acesso não permite consultar os assuntos desta organização.'
        : 'Não foi possível carregar os assuntos. Tente novamente.'
    }
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) protocolSubjectsLoading.value = false
  }
}

function replaceProtocolSubjects(subjects: StaffProtocolSubject[], resetAttachmentDrafts = false) {
  protocolSubjects.value = subjects
  for (const subjectId of Object.keys(protocolSubjectDrafts)) delete protocolSubjectDrafts[subjectId]
  if (resetAttachmentDrafts) {
    for (const subjectId of Object.keys(newAttachmentDrafts)) delete newAttachmentDrafts[subjectId]
    for (const requirementId of Object.keys(attachmentRequirementDrafts)) delete attachmentRequirementDrafts[requirementId]
  }
  for (const subject of subjects) {
    protocolSubjectDrafts[subject.id] = {
      onlineAvailable: subject.onlineAvailable,
      destinationDepartmentId: subject.destinationDepartmentId || null,
      automaticDeadlineDays: subject.automaticDeadlineDays ?? null
    }
    newAttachmentDrafts[subject.id] ??= { name: '', required: false, allowedExtensions: ['.pdf'] }
    for (const requirement of subject.attachmentRequirements || []) {
      attachmentRequirementDrafts[requirement.id] ??= {
        name: requirement.name,
        required: requirement.required,
        allowedExtensions: [...requirement.allowedExtensions]
      }
    }
  }
}

function routingNotice(subject: StaffProtocolSubject) {
  if (!subject.onlineAvailable) return `Assunto “${subject.name}” configurado para atendimento somente no balcão.`
  if (!subject.destinationDepartmentId || !subject.destinationDepartmentName)
    return `Assunto “${subject.name}” está disponível online, mas sem destino. A abertura fica bloqueada até configurar um setor ativo.`
  return `Assunto “${subject.name}” disponível online; as solicitações seguem para ${subject.destinationDepartmentName}.`
}

function hasProtocolSubjectDraftChanges(subject: StaffProtocolSubject) {
  const draft = protocolSubjectDrafts[subject.id]
  return !!draft && (draft.onlineAvailable !== subject.onlineAvailable
    || (draft.destinationDepartmentId || null) !== (subject.destinationDepartmentId || null)
    || (draft.automaticDeadlineDays ?? null) !== (subject.automaticDeadlineDays ?? null))
}

function automaticDeadlineDaysError(value: unknown): string | null {
  if (value === '' || value === null || value === undefined) return null
  return typeof value === 'number' && Number.isInteger(value) && value > 0
    ? null
    : 'Informe um número inteiro de dias maior que zero.'
}

function validAutomaticDeadlineDays(value: unknown) {
  return automaticDeadlineDaysError(value) || true
}

function normalizedAutomaticDeadlineDays(value: unknown): number | null {
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : null
}

async function createProtocolSubject() {
  if (!canManageDepartments.value || !protocolSubjectName.value.trim()) return
  const deadlineError = automaticDeadlineDaysError(protocolSubjectAutomaticDeadlineDays.value)
  if (deadlineError) {
    protocolSubjectsError.value = deadlineError
    return
  }
  if (protocolSubjectOnlineAvailable.value && !protocolSubjectDestinationDepartmentId.value) {
    protocolSubjectsError.value = departments.value.length
      ? 'Escolha um setor ativo para receber as solicitações online.'
      : 'Cadastre ou ative um setor antes de disponibilizar o assunto online.'
    return
  }
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  protocolSubjectSaving.value = true
  protocolSubjectsError.value = ''
  protocolSubjectsNotice.value = ''
  try {
    const response = await staffApi.post<StaffProtocolSubject>('/staff/organization/protocol-subjects', {
      name: protocolSubjectName.value.trim(),
      onlineAvailable: protocolSubjectOnlineAvailable.value,
      destinationDepartmentId: protocolSubjectOnlineAvailable.value ? protocolSubjectDestinationDepartmentId.value : null,
      automaticDeadlineDays: normalizedAutomaticDeadlineDays(protocolSubjectAutomaticDeadlineDays.value)
    }, { headers: organizationHeaders(selectedOrganizationId) })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    replaceProtocolSubjects([...protocolSubjects.value, response.data].sort((left, right) => left.name.localeCompare(right.name, 'pt-BR')))
    protocolSubjectName.value = ''
    protocolSubjectOnlineAvailable.value = true
    protocolSubjectDestinationDepartmentId.value = null
    protocolSubjectAutomaticDeadlineDays.value = null
    protocolSubjectsNotice.value = routingNotice(response.data)
  } catch (e: any) {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) protocolSubjectsError.value = e.response?.data?.message || 'Não foi possível salvar o assunto.'
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) protocolSubjectSaving.value = false
  }
}

async function updateProtocolSubject(subject: StaffProtocolSubject) {
  const draft = protocolSubjectDrafts[subject.id]
  if (!draft || !hasProtocolSubjectDraftChanges(subject)) return
  const deadlineError = automaticDeadlineDaysError(draft.automaticDeadlineDays)
  if (deadlineError) {
    protocolSubjectsError.value = deadlineError
    return
  }
  if (draft.onlineAvailable && !draft.destinationDepartmentId) {
    protocolSubjectsError.value = 'Escolha um setor ativo antes de salvar um assunto disponível online.'
    return
  }
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  protocolSubjectActionId.value = subject.id
  protocolSubjectsError.value = ''
  protocolSubjectsNotice.value = ''
  try {
    const response = await staffApi.put<StaffProtocolSubject>(`/staff/organization/protocol-subjects/${subject.id}`, {
      name: subject.name,
      onlineAvailable: draft.onlineAvailable,
      destinationDepartmentId: draft.destinationDepartmentId,
      automaticDeadlineDays: normalizedAutomaticDeadlineDays(draft.automaticDeadlineDays)
    }, { headers: organizationHeaders(selectedOrganizationId) })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    const subjects = protocolSubjects.value.map(current => current.id === response.data.id ? response.data : current)
    replaceProtocolSubjects(subjects)
    protocolSubjectsNotice.value = routingNotice(response.data)
  } catch (e: any) {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) protocolSubjectsError.value = e.response?.data?.message || 'Não foi possível atualizar a disponibilidade do assunto.'
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) protocolSubjectActionId.value = ''
  }
}

function attachmentRequirementEndpoint(subjectId: string) {
  return `/staff/organization/protocol-subjects/${subjectId}/attachment-requirements`
}

function updateLocalAttachmentRequirement(subjectId: string, requirement: AttachmentRequirement) {
  const subject = protocolSubjects.value.find(current => current.id === subjectId)
  if (!subject) return
  subject.attachmentRequirements = (subject.attachmentRequirements || [])
    .filter(current => current.id !== requirement.id)
    .concat(requirement)
    .sort((left, right) => left.name.localeCompare(right.name, 'pt-BR'))
  attachmentRequirementDrafts[requirement.id] = {
    name: requirement.name,
    required: requirement.required,
    allowedExtensions: [...requirement.allowedExtensions]
  }
}

function hasAttachmentRequirementChanges(requirement: AttachmentRequirement) {
  const draft = attachmentRequirementDrafts[requirement.id]
  if (!draft) return false
  const expected = [...requirement.allowedExtensions].sort().join(',')
  const actual = [...draft.allowedExtensions].sort().join(',')
  return draft.name.trim() !== requirement.name
    || draft.required !== requirement.required
    || actual !== expected
}

function validAttachmentDraft(draft: AttachmentRequirementDraft) {
  return !!draft.name.trim() && draft.allowedExtensions.length > 0 && draft.allowedExtensions.length <= 8
}

async function createAttachmentRequirement(subject: StaffProtocolSubject) {
  const draft = newAttachmentDrafts[subject.id]
  if (!draft || !validAttachmentDraft(draft)) {
    protocolSubjectsError.value = 'Informe o nome do documento e ao menos um tipo de arquivo.'
    return
  }
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  const actionId = `new:${subject.id}`
  attachmentPolicyActionId.value = actionId
  protocolSubjectsError.value = ''
  protocolSubjectsNotice.value = ''
  try {
    const response = await staffApi.post<AttachmentRequirement>(attachmentRequirementEndpoint(subject.id), {
      name: draft.name.trim(),
      required: draft.required,
      allowedExtensions: draft.allowedExtensions
    }, { headers: organizationHeaders(selectedOrganizationId) })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    updateLocalAttachmentRequirement(subject.id, response.data)
    newAttachmentDrafts[subject.id] = { name: '', required: false, allowedExtensions: ['.pdf'] }
    protocolSubjectsNotice.value = `Documento “${response.data.name}” configurado para ${subject.name}.`
  } catch (e: any) {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) {
      protocolSubjectsError.value = e.response?.status === 403
        ? 'Seu papel não permite configurar os documentos deste assunto.'
        : e.response?.data?.message || 'Não foi possível adicionar o documento ao assunto.'
    }
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) attachmentPolicyActionId.value = ''
  }
}

async function saveAttachmentRequirement(subject: StaffProtocolSubject, requirement: AttachmentRequirement) {
  const draft = attachmentRequirementDrafts[requirement.id]
  if (!draft || !validAttachmentDraft(draft) || !hasAttachmentRequirementChanges(requirement)) return
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  attachmentPolicyActionId.value = requirement.id
  protocolSubjectsError.value = ''
  protocolSubjectsNotice.value = ''
  try {
    const response = await staffApi.put<AttachmentRequirement>(
      `${attachmentRequirementEndpoint(subject.id)}/${requirement.id}`,
      { name: draft.name.trim(), required: draft.required, allowedExtensions: draft.allowedExtensions },
      { headers: organizationHeaders(selectedOrganizationId) }
    )
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    updateLocalAttachmentRequirement(subject.id, response.data)
    protocolSubjectsNotice.value = `Configuração de “${response.data.name}” salva.`
  } catch (e: any) {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) {
      protocolSubjectsError.value = e.response?.status === 403
        ? 'Seu papel não permite configurar os documentos deste assunto.'
        : e.response?.data?.message || 'Não foi possível salvar a configuração do documento.'
    }
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) attachmentPolicyActionId.value = ''
  }
}

async function deactivateAttachmentRequirement(subject: StaffProtocolSubject, requirement: AttachmentRequirement) {
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  attachmentPolicyActionId.value = requirement.id
  protocolSubjectsError.value = ''
  protocolSubjectsNotice.value = ''
  try {
    await staffApi.delete(`${attachmentRequirementEndpoint(subject.id)}/${requirement.id}`, {
      headers: organizationHeaders(selectedOrganizationId)
    })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    updateLocalAttachmentRequirement(subject.id, { ...requirement, active: false })
    protocolSubjectsNotice.value = `“${requirement.name}” foi removido dos novos pedidos de ${subject.name}.`
  } catch (e: any) {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) {
      protocolSubjectsError.value = e.response?.status === 403
        ? 'Seu papel não permite configurar os documentos deste assunto.'
        : e.response?.data?.message || 'Não foi possível desativar esta regra de documento.'
    }
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) attachmentPolicyActionId.value = ''
  }
}

function askToSuspendMember(member: OrganizationMember) {
  pendingSuspension.value = member
  showSuspensionDialog.value = true
}

function cancelSuspension() {
  showSuspensionDialog.value = false
  pendingSuspension.value = null
}

async function confirmSuspension() {
  const member = pendingSuspension.value
  if (!member) return
  await setOrganizationMemberAccess(member, false)
  showSuspensionDialog.value = false
}

async function reactivateMember(member: OrganizationMember) {
  await setOrganizationMemberAccess(member, true)
}

async function setOrganizationMemberAccess(member: OrganizationMember, active: boolean) {
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  const actorUsername = username.value.trim()
  memberActionUsername.value = member.username
  memberActionError.value = ''
  membersNotice.value = ''
  try {
    const response = await staffApi.put<OrganizationMemberAccessResult>(
      `/staff/organization/members/${encodeURIComponent(member.username)}/access`,
      { active },
      { headers: organizationHeaders(selectedOrganizationId) }
    )
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    organizationMembers.value = organizationMembers.value.map(current => current.username === response.data.username
      ? { ...current, accessStatus: response.data.accessStatus }
      : current)

    if (!active && response.data.username.toLowerCase() === actorUsername.toLowerCase()) {
      staffLoaded.value = false
      departments.value = []; organogram.value = []; protocols.value = []; organizationMembers.value = []
      password.value = ''
      notice.value = 'Seu acesso foi suspenso nesta organização. A área do servidor foi encerrada.'
      return
    }

    membersNotice.value = response.data.changed
      ? `Acesso de ${member.fullName || member.username} ${active ? 'reativado' : 'suspenso'} nesta organização.`
      : `O acesso de ${member.fullName || member.username} já estava ${active ? 'ativo' : 'suspenso'}.`
    await loadOrganizationMembers()
  } catch (e: any) {
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    memberActionError.value = e.response?.data?.message || (e.response?.status === 403
      ? 'Ação recusada. Sua autorização para esta organização mudou.'
      : 'Não foi possível confirmar a alteração. Atualize a lista de membros antes de realizar outra ação.')
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) memberActionUsername.value = ''
  }
}

const activeOrganizationAdminCount = computed(() => organizationMembers.value.filter(member =>
  member.accessStatus === 'ACTIVE' && member.organizationRoles.includes('ADMIN')
).length)

function isLastActiveOrganizationAdmin(member: OrganizationMember) {
  return member.accessStatus === 'ACTIVE'
    && member.organizationRoles.includes('ADMIN')
    && activeOrganizationAdminCount.value <= 1
}

function accessStatusLabel(status: OrganizationMember['accessStatus']) {
  if (status === 'ACTIVE') return 'Acesso ativo'
  if (status === 'SUSPENDED') return 'Acesso suspenso'
  return 'Sem vínculo ativo'
}

function roleLabel(role: string) {
  if (role === 'ADMIN') return 'Administrador'
  if (role === 'STAFF') return 'Servidor'
  return role
}

function rolesLabel(roles: string[]) {
  return roles.length ? roles.map(roleLabel).join(', ') : 'Nenhum'
}

async function createDepartment() {
  if (!newDepartmentName.value.trim()) { error.value = 'Informe o nome do setor.'; return }
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  loading.value = true; error.value = ''; notice.value = ''
  try {
    const response = await staffApi.post<Department>('/staff/departments', {
      name: newDepartmentName.value,
      parentDepartmentId: parentDepartmentId.value
    }, { headers: organizationHeaders(selectedOrganizationId) })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    // Clear the submitted draft before refreshing the organogram. The form
    // remains editable during that refresh, so a later draft must survive it.
    newDepartmentName.value = ''
    parentDepartmentId.value = null
    await loadStaff()
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    notice.value = `Setor “${response.data.name}” criado. O organograma foi atualizado.`
  } catch (e: any) {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) error.value = e.response?.data?.message || 'Não foi possível criar o setor.'
  }
  finally { if (isCurrentStaffContext(generation, selectedOrganizationId)) loading.value = false }
}

async function updateStaffProfile() {
  if (!selectedStaffUsername.value) { error.value = 'Selecione um servidor.'; return }
  if (!staffProfileName.value.trim()) { error.value = 'Informe o nome do servidor.'; return }
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  loading.value = true; error.value = ''; notice.value = ''
  try {
    const response = await staffApi.put<{
      username: string; fullName: string; positionTitle: string | null
      registrationDate: string | null; registrationDateSource: 'IMPORTED' | 'UNKNOWN'
    }>(`/staff/organization/staff/${encodeURIComponent(selectedStaffUsername.value)}`, {
      fullName: staffProfileName.value,
      positionTitle: staffPositionTitle.value,
      registrationDate: staffRegistrationDate.value || null
    }, { headers: organizationHeaders(selectedOrganizationId) })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    await loadStaff()
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    selectedStaffUsername.value = response.data.username
    staffProfileName.value = response.data.fullName
    staffPositionTitle.value = response.data.positionTitle || ''
    staffRegistrationDate.value = response.data.registrationDate || ''
    notice.value = `Dados de ${response.data.fullName} atualizados no organograma.`
  } catch (e: any) {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) error.value = e.response?.data?.message || 'Não foi possível atualizar os dados do servidor.'
  }
  finally { if (isCurrentStaffContext(generation, selectedOrganizationId)) loading.value = false }
}

async function sendPresenceHeartbeat() {
  if (!staffLoaded.value || !organizationId.value) return
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  try {
    const headers = organizationHeaders(selectedOrganizationId)
    await staffApi.post('/staff/session/heartbeat', {}, { headers })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    const response = await staffApi.get<OrganogramNode[]>('/staff/organization/organogram', { headers })
    if (isCurrentStaffContext(generation, selectedOrganizationId)) organogram.value = response.data
  } catch {
    // Presence expires naturally after two minutes without an authenticated request.
  }
}

async function act(protocol: Protocol, action: 'forward' | 'receive' | 'dispatch' | 'complete') {
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  const headers = organizationHeaders(selectedOrganizationId)
  const body: Record<string, unknown> = { visibleToApplicant: false }
  if (action === 'forward' || action === 'dispatch') body.destinationDepartmentId = destinationDepartmentId.value
  if (action === 'dispatch') { body.note = 'Encaminhado para análise do setor responsável.'; body.visibleToApplicant = true }
  loading.value = true; error.value = ''; notice.value = ''
  try {
    await staffApi.post(`/staff/protocols/${protocol.id}/${action}`, body, { headers })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    await loadStaff()
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    notice.value = `Ação registrada para ${protocol.protocolNumber}.`
  } catch (e: any) {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) error.value = e.response?.data?.message || 'A etapa não foi concluída.'
  }
  finally { if (isCurrentStaffContext(generation, selectedOrganizationId)) loading.value = false }
}

function actionsFor(protocol: Protocol) {
  if (protocol.status === 'OPEN') return [{ label: 'Encaminhar', value: 'forward' as const }]
  if (protocol.status === 'FORWARDED' || protocol.status === 'DISPATCHED') return [{ label: 'Receber', value: 'receive' as const }]
  if (protocol.status === 'RECEIVED') return [{ label: 'Despachar', value: 'dispatch' as const }, { label: 'Concluir', value: 'complete' as const }]
  return []
}

function interactionDraftFor(protocolId: string) {
  protocolInteractionDrafts[protocolId] ??= { message: '', visibility: 'PUBLIC', files: [] }
  return protocolInteractionDrafts[protocolId]
}

function interactionVisibilityLabel(visibility: ProtocolInteractionVisibility) {
  return visibility === 'INTERNAL' ? 'Somente equipe' : 'Visível ao cidadão'
}

function interactionAttachmentHint(protocol: Protocol) {
  const extensions = protocol.interactionAllowedExtensions ?? []
  return extensions.length
    ? `Tipos aceitos para este assunto: ${extensions.join(', ')}`
    : 'Este assunto não tem formatos habilitados para anexos em mensagens.'
}

async function loadProtocolInteractions(protocol: Protocol) {
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  protocolInteractionLoading[protocol.id] = true
  delete protocolInteractionErrors[protocol.id]
  try {
    const response = await staffApi.get<ProtocolInteraction[]>(`/staff/protocols/${protocol.id}/interactions`, {
      headers: organizationHeaders(selectedOrganizationId)
    })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    protocolInteractions[protocol.id] = response.data
    await recordProtocolRead(protocol, selectedOrganizationId, generation)
  } catch (e: any) {
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    protocolInteractionErrors[protocol.id] = e.response?.status === 401 || e.response?.status === 403
      ? 'Seu acesso não permite consultar as mensagens deste protocolo.'
      : 'Não foi possível carregar as mensagens. Tente novamente.'
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) protocolInteractionLoading[protocol.id] = false
  }
}

async function recordProtocolRead(protocol: Protocol, selectedOrganizationId: string, generation: number) {
  delete protocolReadErrors[protocol.id]
  try {
    const response = await staffApi.post<ProtocolReadReceipt>(`/staff/protocols/${protocol.id}/read`, undefined, {
      headers: organizationHeaders(selectedOrganizationId)
    })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    const listedProtocol = protocols.value.find(candidate => candidate.id === protocol.id)
    if (listedProtocol) listedProtocol.readStatus = response.data.readStatus
  } catch (e: any) {
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    protocolReadErrors[protocol.id] = e.response?.status === 403
      ? 'O conteúdo foi carregado, mas sua conta não tem lotação ativa no setor atual para registrar a leitura.'
      : 'O conteúdo foi carregado, mas não foi possível registrar a leitura. Atualize a caixa e tente novamente.'
  }
}

async function toggleProtocolInteractions(protocol: Protocol) {
  if (expandedInteractionProtocolId.value === protocol.id) {
    expandedInteractionProtocolId.value = ''
    return
  }
  expandedInteractionProtocolId.value = protocol.id
  interactionDraftFor(protocol.id)
  if (!Object.prototype.hasOwnProperty.call(protocolInteractions, protocol.id) || protocolInteractionErrors[protocol.id])
    await loadProtocolInteractions(protocol)
}

async function sendStaffInteraction(protocol: Protocol) {
  const draft = interactionDraftFor(protocol.id)
  const message = draft.message.trim()
  if (!message) {
    protocolInteractionErrors[protocol.id] = 'Escreva uma mensagem antes de enviar.'
    return
  }
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  const multipart = new FormData()
  multipart.append('request', new Blob([JSON.stringify({ message, visibility: draft.visibility })], { type: 'application/json' }))
  for (const file of draft.files) multipart.append('files', file, file.name)
  submittingProtocolInteractionId.value = protocol.id
  delete protocolInteractionErrors[protocol.id]
  delete protocolInteractionNotices[protocol.id]
  try {
    await staffApi.post(`/staff/protocols/${protocol.id}/interactions`, multipart, {
      headers: organizationHeaders(selectedOrganizationId), timeout: 120_000
    })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    draft.message = ''
    draft.files = []
    await loadProtocolInteractions(protocol)
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    protocolInteractionNotices[protocol.id] = draft.visibility === 'INTERNAL'
      ? 'Mensagem interna enviada à equipe.'
      : 'Mensagem enviada ao requerente.'
  } catch (e: any) {
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    protocolInteractionErrors[protocol.id] = e.response?.status === 403
      ? 'Seu acesso não permite enviar mensagens para este protocolo.'
      : e.response?.data?.message || 'Não foi possível enviar a mensagem. Tente novamente.'
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) submittingProtocolInteractionId.value = ''
  }
}

async function reopenProtocol(protocol: Protocol) {
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    await staffApi.post(`/staff/protocols/${protocol.id}/reopen`, {}, {
      headers: organizationHeaders(selectedOrganizationId)
    })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    await loadStaff()
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    notice.value = `Protocolo ${protocol.protocolNumber} reaberto para o setor responsável.`
  } catch (e: any) {
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    error.value = e.response?.status === 403
      ? 'Apenas o setor responsável pode reabrir este protocolo.'
      : e.response?.data?.message || 'Não foi possível reabrir o protocolo.'
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) loading.value = false
  }
}

async function downloadStaffInteractionAttachment(protocol: Protocol, attachment: ProtocolInteractionAttachment) {
  if (downloadingProtocolInteractionAttachmentId.value) return
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  downloadingProtocolInteractionAttachmentId.value = attachment.id
  delete protocolInteractionDownloadErrors[attachment.id]
  try {
    const relativePath = attachment.downloadPath.replace(/^\/api\/v1\/?/, '')
    const response = await staffApi.get<Blob>(relativePath, {
      responseType: 'blob', headers: organizationHeaders(selectedOrganizationId), timeout: 60_000
    })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = attachment.filename
    anchor.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1_000)
  } catch (e: any) {
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    protocolInteractionDownloadErrors[attachment.id] = e.response?.status === 401 || e.response?.status === 403
      ? 'Seu acesso não permite baixar este documento.'
      : 'Não foi possível baixar o documento. Tente novamente.'
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) downloadingProtocolInteractionAttachmentId.value = ''
  }
}

async function loadProtocolAttachments(protocol: Protocol) {
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  protocolAttachmentLoading[protocol.id] = true
  delete protocolAttachmentErrors[protocol.id]
  try {
    const response = await staffApi.get<ProtocolAttachment[]>(`/staff/protocols/${protocol.id}/attachments`, {
      headers: organizationHeaders(selectedOrganizationId)
    })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    protocolAttachments[protocol.id] = response.data
  } catch (e: any) {
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    protocolAttachmentErrors[protocol.id] = e.response?.status === 401 || e.response?.status === 403
      ? 'Seu acesso não permite consultar os documentos deste protocolo.'
      : 'Não foi possível carregar os documentos. Tente novamente.'
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) protocolAttachmentLoading[protocol.id] = false
  }
}

async function toggleProtocolAttachments(protocol: Protocol) {
  if (expandedProtocolId.value === protocol.id) {
    expandedProtocolId.value = ''
    return
  }
  expandedProtocolId.value = protocol.id
  if (!Object.prototype.hasOwnProperty.call(protocolAttachments, protocol.id) || protocolAttachmentErrors[protocol.id]) {
    await loadProtocolAttachments(protocol)
  }
}

async function downloadStaffAttachment(protocol: Protocol, attachment: ProtocolAttachment) {
  if (downloadingProtocolAttachmentId.value) return
  const selectedOrganizationId = organizationId.value
  const generation = staffContextGeneration
  downloadingProtocolAttachmentId.value = attachment.id
  delete protocolAttachmentDownloadErrors[attachment.id]
  error.value = ''
  try {
    const relativePath = attachment.downloadPath.replace(/^\/api\/v1\/?/, '')
    const response = await staffApi.get<Blob>(relativePath, {
      responseType: 'blob', headers: organizationHeaders(selectedOrganizationId), timeout: 60_000
    })
    if (!isCurrentStaffContext(generation, selectedOrganizationId)) return
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = attachment.filename
    anchor.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1_000)
  } catch (e: any) {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) {
      protocolAttachmentDownloadErrors[attachment.id] = e.response?.status === 401 || e.response?.status === 403
        ? 'Seu acesso não permite baixar este documento.'
        : 'Não foi possível baixar o documento. Tente novamente.'
    }
  } finally {
    if (isCurrentStaffContext(generation, selectedOrganizationId)) downloadingProtocolAttachmentId.value = ''
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

function formatLocalDate(value: string | null) {
  return value ? new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short' }).format(new Date(`${value}T12:00:00`)) : ''
}

function presenceLabel(status: DepartmentMember['presenceStatus'] | undefined) {
  if (status === 'ONLINE') return 'Online'
  if (status === 'OFFLINE') return 'Offline'
  return 'Presença não observada'
}

function flattenDepartments(nodes: OrganogramNode[]) {
  const options: Array<{ label: string; value: string }> = []
  const pending = nodes.slice().reverse().map(node => ({ node, depth: 0 }))
  while (pending.length) {
    const { node, depth } = pending.pop()!
    options.push({ label: `${'— '.repeat(depth)}${node.name}`, value: node.id })
    for (const child of (node.children || []).slice().reverse()) pending.push({ node: child, depth: depth + 1 })
  }
  return options
}

function flattenStaff(nodes: OrganogramNode[]) {
  const members = new Map<string, DepartmentMember>()
  const pending = nodes.slice().reverse()
  while (pending.length) {
    const node = pending.pop()!
    for (const member of node.staff || []) members.set(member.username, member)
    for (const child of (node.children || []).slice().reverse()) pending.push(child)
  }
  return [...members.values()].map(member => ({
    label: member.fullName ? `${member.fullName} (${member.username})` : member.username,
    value: member.username,
    member
  }))
}

</script>

<template>
  <q-layout view="lHh Lpr lFf" class="app-shell staff-app">
    <a class="skip-link" href="#main-content">Pular para o conteúdo</a>
    <q-header class="topbar"><div class="topbar-inner">
      <span v-if="administrativeProcessBusy" class="brand-lockup nav-link-static">
        <span class="brand-name">NEXO<span class="brand-gov">GOV</span></span>
        <span class="brand-caption">Gestão pública digital</span>
      </span>
      <a v-else class="brand-lockup" href="/" aria-label="NexoGov — portal do cidadão">
        <span class="brand-name">NEXO<span class="brand-gov">GOV</span></span>
        <span class="brand-caption">Gestão pública digital</span>
      </a>
      <q-space />
      <span v-if="isLocalEnvironment" class="environment-label">Ambiente local</span>
      <q-btn v-if="staffLoaded || loading || oidcAuthenticated" flat color="primary" no-caps label="Sair" aria-label="Sair da área do servidor" :disable="administrativeProcessBusy" @click="logoutStaff" />
      <span v-if="administrativeProcessBusy" class="topbar-context portal-link nav-link-static">Portal do cidadão</span>
      <a v-else class="topbar-context portal-link" href="/">Portal do cidadão</a>
    </div></q-header>

    <q-page-container><q-page id="main-content" class="content" tabindex="-1">
      <div class="page-heading">
        <h1 class="page-title">Caixa de entrada</h1>
        <p class="page-lead">Consulte os protocolos atribuídos à sua organização e registre as próximas etapas.</p>
      </div>

      <q-banner v-if="notice" class="feedback-banner feedback-success" role="status">{{ notice }}</q-banner>
      <q-banner v-if="error" class="feedback-banner feedback-error" role="alert">{{ error }}</q-banner>
      <q-banner v-if="administrativeProcessBusy" class="feedback-banner" role="status">
        A operação do processo está pendente. Aguarde a confirmação antes de trocar a organização, atualizar a caixa, sair ou navegar para outro portal. A página pedirá confirmação se você tentar recarregar ou voltar.
      </q-banner>

      <section class="staff-login" aria-labelledby="staff-login-title">
        <h2 id="staff-login-title" class="section-heading">Identificação do servidor</h2>
        <p class="section-subheading">Acesse com sua conta de trabalho e selecione a organização.</p>
        <q-form class="form-grid staff-access" @submit.prevent="submitStaffAccess">
          <q-input v-if="authMode === 'local-basic'" v-model="username" :rules="[value => !!value?.trim() || 'Informe seu usuário.']" outlined dense label="Usuário" autocomplete="username" />
          <q-input v-if="authMode === 'local-basic'" v-model="password" :rules="[value => !!value || 'Informe sua senha.']" outlined dense type="password" label="Senha" autocomplete="current-password" />
          <q-select v-model="organizationId" :options="organizations.map(o => ({ label: o.name, value: o.id }))" :disable="!authReady || organizationsLoading || loading || administrativeProcessBusy || organizations.length === 0" :rules="[value => !!value || 'Selecione uma organização.']" emit-value map-options outlined dense label="Organização" />
          <div v-if="authMode !== 'unavailable'" class="span-two"><q-btn color="primary" unelevated type="submit" :label="authMode === 'oidc' ? (oidcAuthenticated ? 'Carregar caixa' : 'Entrar com conta institucional') : 'Entrar e carregar caixa'" no-caps :loading="loading" :disable="!authReady || organizationsLoading || administrativeProcessBusy || organizations.length === 0" /></div>
          <p v-if="organizationsLoading || !authReady" class="span-two staff-auth-status" role="status" aria-live="polite">Preparando o acesso e carregando organizações…</p>
          <p v-else-if="organizationsLoadFailed" class="span-two staff-auth-status" role="status">Não foi possível carregar as organizações. Recarregue a página para tentar novamente.</p>
          <p v-else-if="organizations.length === 0" class="span-two staff-auth-status" role="status">Nenhuma organização está disponível para seleção.</p>
          <p v-else-if="authMode === 'unavailable'" class="span-two staff-auth-unavailable" role="status">O acesso da área do servidor não está configurado para este ambiente.</p>
          <p v-if="authMode === 'oidc'" class="span-two staff-auth-note">A autenticação é realizada pelo provedor institucional; as permissões são verificadas pelo serviço da organização.</p>
        </q-form>
      </section>

      <div v-if="staffLoaded" class="staff-workspace">
        <section class="inbox-panel" aria-labelledby="inbox-title">
          <div class="inbox-heading">
            <div><h2 id="inbox-title" class="section-heading">Protocolos</h2><p class="section-subheading">{{ protocols.length }} registro(s) na caixa de entrada</p></div>
            <q-btn flat color="primary" icon="refresh" label="Atualizar" no-caps :loading="loading" :disable="administrativeProcessBusy" @click="loadStaff" />
          </div>
          <div v-if="departments.length" class="destination-control">
            <q-select v-model="destinationDepartmentId" :options="departments.map(d => ({ label: d.name, value: d.id }))" :disable="loading" emit-value map-options outlined dense label="Próximo setor" />
          </div>
          <div v-if="protocols.length" class="protocol-list">
            <article v-for="protocol in protocols" :key="protocol.id" class="protocol-row">
              <div class="toolbar-row">
                <div class="protocol-copy">
                  <div class="protocol-row-meta"><span class="protocol-number">{{ protocol.protocolNumber }}</span><span class="meta-divider">·</span><span class="muted">{{ formatDate(protocol.createdAt) }}</span></div>
                  <div class="protocol-subject q-mt-xs">{{ protocol.subject }}</div>
                  <div class="muted text-caption">{{ protocol.applicantName }} <span aria-hidden="true">·</span> {{ protocol.assignedDepartmentName || 'Sem setor' }}</div>
                  <p class="protocol-read-indicator" role="status" :aria-label="`Situação de leitura: ${protocolReadStatusLabels[protocol.readStatus] || 'indisponível'}`">
                    Leitura: {{ protocolReadStatusLabels[protocol.readStatus] || 'indisponível' }}
                  </p>
                  <p v-if="protocolReadErrors[protocol.id]" class="member-feedback member-feedback-error protocol-read-error" role="alert">
                    {{ protocolReadErrors[protocol.id] }}
                  </p>
                </div>
                <div class="row items-center q-gutter-xs protocol-actions">
                  <span class="status-label">{{ statusLabels[protocol.status] || protocol.status }}</span>
                  <q-btn
                    color="primary" flat no-caps icon="attach_file"
                    :label="`Anexos da abertura (${protocol.attachmentCount ?? 0})`"
                    :aria-expanded="expandedProtocolId === protocol.id"
                    :aria-controls="`protocol-attachments-${protocol.id}`"
                    :disable="loading"
                    @click="toggleProtocolAttachments(protocol)"
                  />
                  <q-btn
                    color="primary" flat no-caps icon="forum"
                    :label="protocolInteractions[protocol.id]?.length ? `Mensagens (${protocolInteractions[protocol.id].length})` : 'Mensagens'"
                    :aria-label="`${expandedInteractionProtocolId === protocol.id ? 'Ocultar' : 'Ver'} mensagens do protocolo ${protocol.protocolNumber}`"
                    :aria-expanded="expandedInteractionProtocolId === protocol.id"
                    :aria-controls="`protocol-interactions-${protocol.id}`"
                    :disable="loading || !!submittingProtocolInteractionId"
                    @click="toggleProtocolInteractions(protocol)"
                  />
                  <q-btn v-for="action in actionsFor(protocol)" :key="action.value" color="primary" outline no-caps :label="action.label" :disable="loading" @click="act(protocol, action.value)" />
                  <q-btn
                    v-if="protocol.status === 'COMPLETED'"
                    color="primary" outline no-caps label="Reabrir"
                    :aria-label="`Reabrir protocolo ${protocol.protocolNumber}`"
                    :disable="loading"
                    @click="reopenProtocol(protocol)"
                  />
                </div>
              </div>
              <section
                v-show="expandedProtocolId === protocol.id"
                :id="`protocol-attachments-${protocol.id}`"
                class="protocol-attachments-panel"
                :aria-label="`Documentos do protocolo ${protocol.protocolNumber}`"
              >
                <p v-if="protocolAttachmentLoading[protocol.id]" class="member-loading" role="status">Carregando documentos…</p>
                <div v-else-if="protocolAttachmentErrors[protocol.id]" class="member-retry">
                  <p class="member-feedback member-feedback-error" role="alert">{{ protocolAttachmentErrors[protocol.id] }}</p>
                  <q-btn color="primary" outline no-caps label="Tentar carregar documentos novamente" :loading="protocolAttachmentLoading[protocol.id]" @click="loadProtocolAttachments(protocol)" />
                </div>
                <ul v-else-if="protocolAttachments[protocol.id]?.length" class="attachment-list" :aria-label="`Documentos anexados ao protocolo ${protocol.protocolNumber}`">
                  <li v-for="attachment in protocolAttachments[protocol.id]" :key="attachment.id" class="attachment-row attachment-row-staff">
                    <div class="attachment-details">
                      <span class="attachment-name">{{ attachment.filename }}</span>
                      <span class="muted text-caption">{{ formatFileSize(attachment.sizeBytes) }} · {{ formatDate(attachment.uploadedAt) }}</span>
                    </div>
                    <q-btn
                      color="primary" outline no-caps label="Baixar arquivo"
                      :aria-label="`Baixar ${attachment.filename}`"
                      :loading="downloadingProtocolAttachmentId === attachment.id"
                      :disable="!!downloadingProtocolAttachmentId"
                      @click="downloadStaffAttachment(protocol, attachment)"
                    />
                    <p v-if="protocolAttachmentDownloadErrors[attachment.id]" class="member-feedback member-feedback-error attachment-download-error" role="alert">
                      {{ protocolAttachmentDownloadErrors[attachment.id] }}
                    </p>
                  </li>
                </ul>
                <p v-else class="empty-state" role="status">Este protocolo não tem documentos anexados.</p>
              </section>
              <section
                v-show="expandedInteractionProtocolId === protocol.id"
                :id="`protocol-interactions-${protocol.id}`"
                class="protocol-interactions-panel"
                :aria-label="`Mensagens do protocolo ${protocol.protocolNumber}`"
              >
                <div class="interaction-panel-heading">
                  <div>
                    <h3 class="subsection-heading">Mensagens</h3>
                    <p class="member-access-note">Mensagens internas ficam disponíveis apenas na área do servidor.</p>
                  </div>
                  <q-btn flat color="primary" no-caps label="Atualizar mensagens" :loading="protocolInteractionLoading[protocol.id]" @click="loadProtocolInteractions(protocol)" />
                </div>
                <p v-if="protocolInteractionLoading[protocol.id]" class="member-loading" role="status">Carregando mensagens…</p>
                <div v-else-if="protocolInteractionErrors[protocol.id]" class="member-retry">
                  <p class="member-feedback member-feedback-error" role="alert">{{ protocolInteractionErrors[protocol.id] }}</p>
                  <q-btn color="primary" outline no-caps label="Tentar carregar mensagens novamente" :loading="protocolInteractionLoading[protocol.id]" @click="loadProtocolInteractions(protocol)" />
                </div>
                <template v-else>
                  <p v-if="protocolInteractionNotices[protocol.id]" class="member-feedback member-feedback-success" role="status">{{ protocolInteractionNotices[protocol.id] }}</p>
                  <p v-if="protocolInteractions[protocol.id]?.length === 0" class="empty-state" role="status">Ainda não há mensagens neste protocolo.</p>
                  <ol v-else class="interaction-list staff-interaction-list" :aria-label="`Histórico de mensagens do protocolo ${protocol.protocolNumber}`">
                    <li v-for="interaction in protocolInteractions[protocol.id] || []" :key="interaction.id" class="interaction-entry">
                      <div class="interaction-entry-heading">
                        <strong class="interaction-author">{{ interaction.author }}</strong>
                        <span class="interaction-visibility">{{ interactionVisibilityLabel(interaction.visibility) }}</span>
                        <span class="muted interaction-date">{{ formatDate(interaction.createdAt) }}</span>
                      </div>
                      <p v-if="interaction.department" class="interaction-department">{{ interaction.department }}</p>
                      <p class="interaction-message">{{ interaction.message }}</p>
                      <ul v-if="interaction.attachments?.length" class="interaction-files" :aria-label="`Documentos da mensagem de ${interaction.author}`">
                        <li v-for="attachment in interaction.attachments" :key="attachment.id" class="interaction-file-row">
                          <span class="attachment-name">{{ attachment.filename }}</span>
                          <q-btn
                            color="primary" outline no-caps label="Baixar arquivo"
                            :aria-label="`Baixar ${attachment.filename} da conversa do protocolo ${protocol.protocolNumber}`"
                            :loading="downloadingProtocolInteractionAttachmentId === attachment.id"
                            :disable="!!downloadingProtocolInteractionAttachmentId"
                            @click="downloadStaffInteractionAttachment(protocol, attachment)"
                          />
                          <p v-if="protocolInteractionDownloadErrors[attachment.id]" class="member-feedback member-feedback-error attachment-download-error" role="alert">
                            {{ protocolInteractionDownloadErrors[attachment.id] }}
                          </p>
                        </li>
                      </ul>
                    </li>
                  </ol>
                  <p v-if="protocol.status === 'COMPLETED'" class="interaction-closed-note" role="status">Reabra o protocolo antes de registrar novas mensagens.</p>
                  <q-form
                    v-else
                    class="staff-interaction-form"
                    :aria-label="`Enviar mensagem no protocolo ${protocol.protocolNumber}`"
                    @submit.prevent="sendStaffInteraction(protocol)"
                  >
                    <q-select
                      v-model="interactionDraftFor(protocol.id).visibility"
                      :options="[{ label: 'Visível ao cidadão', value: 'PUBLIC' }, { label: 'Somente equipe', value: 'INTERNAL' }]"
                      :label="`Visibilidade da mensagem para ${protocol.protocolNumber}`"
                      emit-value map-options outlined dense
                      :disable="submittingProtocolInteractionId === protocol.id"
                    />
                    <p v-if="interactionDraftFor(protocol.id).visibility === 'INTERNAL'" class="interaction-format-note" role="status">Esta mensagem ficará somente na área do servidor.</p>
                    <q-input
                      v-model="interactionDraftFor(protocol.id).message"
                      outlined type="textarea" autogrow maxlength="8000"
                      label="Mensagem" :disable="submittingProtocolInteractionId === protocol.id"
                    />
                    <q-file
                      v-model="interactionDraftFor(protocol.id).files"
                      :accept="protocol.interactionAllowedExtensions?.join(',') || undefined"
                      multiple clearable outlined dense
                      label="Anexar documentos (opcional)"
                      :hint="interactionAttachmentHint(protocol)"
                      :disable="submittingProtocolInteractionId === protocol.id || !protocol.interactionAllowedExtensions?.length"
                    />
                    <p v-if="protocolInteractionErrors[protocol.id]" class="member-feedback member-feedback-error" role="alert">{{ protocolInteractionErrors[protocol.id] }}</p>
                    <div class="interaction-form-actions">
                      <q-btn
                        color="primary" unelevated no-caps type="submit" label="Enviar mensagem"
                        :loading="submittingProtocolInteractionId === protocol.id"
                        :disable="!interactionDraftFor(protocol.id).message.trim() || !!submittingProtocolInteractionId"
                      />
                    </div>
                  </q-form>
                </template>
              </section>
            </article>
          </div>
          <div v-else class="empty-state">Não há protocolos atribuídos para exibir.</div>
        </section>

        <aside class="organization-panel" aria-labelledby="organization-title">
          <div class="organogram-heading">
            <div><h2 id="organization-title" class="aside-heading">Organograma</h2><p class="aside-copy">Setores ativos e suas lotações nesta organização.</p></div>
          </div>
          <q-tree v-if="organogram.length" :nodes="organogram" node-key="id" label-key="name" default-expand-all class="organogram-tree">
            <template #default-header="prop">
              <div class="organogram-node">
                <span class="organogram-node-name">{{ prop.node.name }}</span>
                <div v-if="prop.node.staff?.length" class="organogram-people">
                  <div v-for="member in prop.node.staff" :key="`${member.username}:${member.roleCode}`" class="organogram-person">
                    <div class="person-title-row">
                      <span class="organogram-node-staff">{{ member.fullName || member.username }}</span>
                      <span class="presence-label" :class="`presence-${member.presenceStatus.toLowerCase()}`">{{ presenceLabel(member.presenceStatus) }}</span>
                    </div>
                    <div class="person-details">{{ member.positionTitle || 'Cargo não informado' }} · Papel {{ member.roleCode }}</div>
                    <div class="person-details">
                      <span>{{ member.registrationDate ? `Cadastro ${formatLocalDate(member.registrationDate)}` : 'Data de cadastro não informada' }}</span>
                      <span aria-hidden="true"> · </span>
                      <span>{{ member.lastOnlineAt ? `Última atividade ${formatDate(member.lastOnlineAt)}` : 'Última atividade não observada' }}</span>
                    </div>
                  </div>
                </div>
                <span v-else class="organogram-node-empty">Sem lotação ativa</span>
              </div>
            </template>
          </q-tree>
          <p v-else class="empty-state">Esta organização ainda não tem setores ativos.</p>

          <section v-if="canManageDepartments" class="department-editor" aria-labelledby="department-title">
            <h3 id="department-title" class="subsection-heading">Estrutura da organização</h3>
            <p class="aside-copy">Cadastre setores e subsetores.</p>
            <q-form class="department-form" @submit.prevent="createDepartment">
              <q-input v-model="newDepartmentName" :rules="[value => !!value?.trim() || 'Informe o nome do setor.']" outlined dense maxlength="180" label="Nome do novo setor" />
              <q-select v-model="parentDepartmentId" :options="parentDepartmentOptions" emit-value map-options clearable outlined dense label="Setor superior (opcional)" />
              <q-btn type="submit" color="primary" outline label="Criar setor" no-caps :loading="loading" />
            </q-form>
          </section>

          <section v-if="canManageDepartments && staffOptions.length" class="department-editor" aria-labelledby="staff-profile-title">
            <h3 id="staff-profile-title" class="subsection-heading">Dados dos servidores</h3>
            <p class="aside-copy">Mantenha nome e cargo atualizados. Informe a data de cadastro quando houver registro confiável.</p>
            <q-form class="department-form" @submit.prevent="updateStaffProfile">
              <q-select v-model="selectedStaffUsername" :options="staffOptions" emit-value map-options outlined dense label="Servidor" />
              <q-input v-model="staffProfileName" :rules="[value => !!value?.trim() || 'Informe o nome do servidor.']" outlined dense maxlength="180" label="Nome completo" />
              <q-input v-model="staffPositionTitle" outlined dense maxlength="180" label="Cargo" />
              <q-input v-model="staffRegistrationDate" outlined dense type="date" stack-label label="Data de cadastro conhecida" />
              <q-btn type="submit" color="primary" outline label="Salvar dados do servidor" no-caps :loading="loading" />
            </q-form>
          </section>

          <section v-if="canManageDepartments" class="member-access-section" aria-labelledby="member-access-title">
            <h3 id="member-access-title" class="subsection-heading">Membros e acesso</h3>
            <p class="aside-copy">Consulte os vínculos desta organização. Suspender o acesso não remove cadastros nem lotações.</p>

            <p v-if="membersNotice" class="member-feedback member-feedback-success" role="status">{{ membersNotice }}</p>
            <p v-if="memberActionError" class="member-feedback member-feedback-error" role="alert">{{ memberActionError }}</p>
            <p v-if="membersLoadError" class="member-feedback member-feedback-error" role="alert">{{ membersLoadError }}</p>
            <div v-if="memberActionError" class="member-retry">
              <q-btn color="primary" outline no-caps label="Atualizar lista de membros" :disable="!!memberActionUsername || membersLoading" @click="loadOrganizationMembers" />
            </div>

            <p v-if="membersLoading" class="member-loading" role="status" aria-live="polite">Carregando membros…</p>
            <div v-else-if="membersLoadError" class="member-retry">
              <q-btn color="primary" outline no-caps label="Tentar carregar membros novamente" :disable="!!memberActionUsername || membersLoading" @click="loadOrganizationMembers" />
            </div>
            <p v-else-if="organizationMembers.length === 0" class="empty-state" role="status">Não há membros vinculados a esta organização.</p>
            <ul v-else class="organization-member-list" aria-label="Membros da organização">
              <li v-for="member in organizationMembers" :key="member.username" class="organization-member-row">
                <div class="organization-member-details">
                  <h4 class="organization-member-name">{{ member.fullName || member.username }}</h4>
                  <p class="organization-member-username">{{ member.username }}</p>
                  <p v-if="member.positionTitle" class="organization-member-meta">{{ member.positionTitle }}</p>
                  <p class="organization-member-meta">Papel na organização: {{ rolesLabel(member.organizationRoles) }}</p>
                  <p class="organization-member-meta">Papéis nos setores: {{ rolesLabel(member.departmentRoles) }}</p>
                </div>
                <div class="organization-member-controls">
                  <span class="member-access-status">{{ accessStatusLabel(member.accessStatus) }}</span>
                  <q-btn
                    v-if="member.accessStatus === 'ACTIVE' && !isLastActiveOrganizationAdmin(member)"
                    color="negative" outline no-caps label="Suspender acesso"
                    :aria-label="`Suspender acesso de ${member.fullName?.trim() || member.username}`"
                    :loading="memberActionUsername === member.username"
                    :disable="!!memberActionUsername || loading"
                    @click="askToSuspendMember(member)"
                  />
                  <p v-else-if="member.accessStatus === 'ACTIVE' && isLastActiveOrganizationAdmin(member)" class="member-access-note">
                    Não é possível suspender o último administrador ativo.
                  </p>
                  <q-btn
                    v-else-if="member.accessStatus === 'SUSPENDED'"
                    color="primary" outline no-caps label="Reativar acesso"
                    :aria-label="`Reativar acesso de ${member.fullName?.trim() || member.username}`"
                    :loading="memberActionUsername === member.username"
                    :disable="!!memberActionUsername || loading"
                    @click="reactivateMember(member)"
                  />
                </div>
              </li>
            </ul>
          </section>

          <section v-if="canManageDepartments" class="protocol-subject-section" aria-labelledby="protocol-subject-title">
            <h3 id="protocol-subject-title" class="subsection-heading">Assuntos de protocolo</h3>
            <p class="aside-copy">Defina onde cada solicitação online deve chegar. Assuntos sem setor ativo não podem receber novos protocolos.</p>
            <p v-if="protocolSubjectsNotice" class="member-feedback member-feedback-success" role="status">{{ protocolSubjectsNotice }}</p>
            <p v-if="protocolSubjectsError" class="member-feedback member-feedback-error" role="alert">{{ protocolSubjectsError }}</p>
            <q-form class="department-form protocol-subject-form" @submit.prevent="createProtocolSubject">
              <q-input v-model="protocolSubjectName" :rules="[value => !!value?.trim() || 'Informe o nome do assunto.']" outlined dense maxlength="180" label="Nome do assunto" :disable="protocolSubjectSaving" />
              <q-select v-model="protocolSubjectOnlineAvailable" :options="[{ label: 'Central de Atendimento', value: true }, { label: 'Somente balcão interno', value: false }]" emit-value map-options outlined dense label="Disponibilidade de atendimento" :disable="protocolSubjectSaving" />
              <q-select
                v-model="protocolSubjectDestinationDepartmentId"
                :options="departments.map(department => ({ label: department.name, value: department.id }))"
                :rules="[value => !protocolSubjectOnlineAvailable || !!value || 'Escolha o setor que receberá as solicitações online.']"
                :disable="protocolSubjectSaving || protocolRoutingDepartmentsLoading || !protocolSubjectOnlineAvailable || departments.length === 0"
                :loading="protocolRoutingDepartmentsLoading"
                emit-value map-options outlined dense label="Setor destinatário para solicitações online"
              />
              <q-input
                v-model.number="protocolSubjectAutomaticDeadlineDays"
                type="number" min="1" step="1" clearable
                label="Dias de prazo no comprovante (opcional)"
                hint="O comprovante mostra esse número de dias; o sistema não calcula uma data de vencimento."
                :rules="[validAutomaticDeadlineDays]"
                :disable="protocolSubjectSaving"
                outlined dense bottom-slots
              />
              <p v-if="protocolSubjectOnlineAvailable && departments.length === 0 && !protocolRoutingDepartmentsLoading && !protocolRoutingDepartmentsError" class="member-access-note" role="status">
                Nenhum setor ativo disponível. Cadastre ou ative um setor antes de disponibilizar um assunto online.
              </p>
              <q-btn type="submit" color="primary" unelevated label="Adicionar assunto" no-caps :loading="protocolSubjectSaving" :disable="protocolRoutingDepartmentsLoading || (protocolSubjectOnlineAvailable && departments.length === 0)" />
            </q-form>
            <p v-if="protocolRoutingDepartmentsLoading" class="member-loading" role="status" aria-live="polite">Carregando setores ativos…</p>
            <div v-else-if="protocolRoutingDepartmentsError" class="member-retry">
              <p class="member-feedback member-feedback-error" role="alert">{{ protocolRoutingDepartmentsError }}</p>
              <q-btn color="primary" outline no-caps label="Tentar carregar setores novamente" :disable="!!protocolSubjectActionId || protocolSubjectSaving" @click="reloadRoutingDepartments" />
            </div>
            <p v-if="protocolSubjectsLoading" class="member-loading" role="status">Carregando assuntos…</p>
            <div v-else-if="protocolSubjectsError" class="member-retry">
              <q-btn color="primary" outline no-caps label="Tentar carregar assuntos" :disable="!!protocolSubjectActionId || protocolSubjectSaving" @click="loadProtocolSubjects" />
            </div>
            <p v-else-if="protocolSubjects.length === 0" class="empty-state" role="status">Ainda não há assuntos cadastrados nesta organização.</p>
            <ul v-else class="protocol-subject-list" aria-label="Assuntos de protocolo cadastrados">
              <li
                v-for="subject in protocolSubjects"
                :key="subject.id"
                class="protocol-subject-row"
                :class="{ 'protocol-subject-row-unconfigured': subject.onlineAvailable && (!subject.destinationDepartmentId || !subject.destinationDepartmentName) }"
              >
                <div>
                  <h4 class="organization-member-name">{{ subject.name }}</h4>
                  <p
                    class="organization-member-meta"
                    :class="{ 'protocol-routing-warning': subject.onlineAvailable && (!subject.destinationDepartmentId || !subject.destinationDepartmentName) }"
                    :role="subject.onlineAvailable && (!subject.destinationDepartmentId || !subject.destinationDepartmentName) ? 'status' : undefined"
                  >{{ routingNotice(subject) }}</p>
                </div>
                <div class="protocol-subject-editor">
                  <q-select
                    v-model="protocolSubjectDrafts[subject.id].onlineAvailable"
                    :options="[{ label: 'Central de Atendimento', value: true }, { label: 'Somente balcão interno', value: false }]"
                    :label="`Disponibilidade: ${subject.name}`"
                    :disable="!!protocolSubjectActionId || protocolSubjectSaving"
                    emit-value map-options outlined dense
                  />
                  <q-select
                    v-model="protocolSubjectDrafts[subject.id].destinationDepartmentId"
                    :options="departments.map(department => ({ label: department.name, value: department.id }))"
                    :label="`Setor destinatário: ${subject.name}`"
                    :disable="!!protocolSubjectActionId || protocolSubjectSaving || protocolRoutingDepartmentsLoading || departments.length === 0"
                    :loading="protocolRoutingDepartmentsLoading"
                    emit-value map-options outlined dense
                  />
                  <q-input
                    v-model.number="protocolSubjectDrafts[subject.id].automaticDeadlineDays"
                    type="number" min="1" step="1" clearable
                    :label="`Dias de prazo no comprovante: ${subject.name}`"
                    hint="O comprovante mostra esse número de dias; o sistema não calcula uma data de vencimento."
                    :rules="[validAutomaticDeadlineDays]"
                    :disable="!!protocolSubjectActionId || protocolSubjectSaving"
                    outlined dense bottom-slots
                  />
                  <div class="protocol-subject-actions">
                    <q-btn
                      color="primary" outline no-caps label="Salvar configuração"
                      :aria-label="`Salvar configuração do assunto ${subject.name}`"
                      :loading="protocolSubjectActionId === subject.id"
                      :disable="!!protocolSubjectActionId || protocolSubjectSaving || !hasProtocolSubjectDraftChanges(subject)"
                      @click="updateProtocolSubject(subject)"
                    />
                  </div>
                </div>
                <section class="subject-participant-fields" :aria-labelledby="`participant-fields-title-${subject.id}`">
                  <div class="participant-fields-heading">
                    <div>
                      <h5 :id="`participant-fields-title-${subject.id}`" class="attachment-policy-title">Campos das pessoas envolvidas</h5>
                      <p class="member-access-note">Defina os dados que serão solicitados quando este assunto admitir pessoas participantes.</p>
                    </div>
                    <q-btn
                      flat no-caps color="primary"
                      :label="expandedParticipantSubjectId === subject.id ? 'Ocultar campos' : 'Configurar campos'"
                      :aria-expanded="expandedParticipantSubjectId === subject.id"
                      :aria-controls="`participant-fields-panel-${subject.id}`"
                      :loading="participantFieldsLoading[subject.id]"
                      :disable="!!participantFieldsLoading[subject.id] || !!participantFieldsSaving[subject.id]"
                      @click="toggleParticipantFields(subject)"
                    />
                  </div>
                  <div v-if="expandedParticipantSubjectId === subject.id" :id="`participant-fields-panel-${subject.id}`" class="participant-fields-panel">
                    <p class="participant-fields-limit" role="note">
                      Esta etapa configura os campos do cadastro. O cadastro e o acesso individual das pessoas à Central ainda não estão disponíveis.
                    </p>
                    <p v-if="participantFieldsErrors[subject.id]" class="member-feedback member-feedback-error" role="alert">{{ participantFieldsErrors[subject.id] }}</p>
                    <p v-if="participantFieldsNotices[subject.id]" class="member-feedback member-feedback-success" role="status">{{ participantFieldsNotices[subject.id] }}</p>
                    <p v-if="participantFieldsLoading[subject.id]" class="member-loading" role="status" aria-live="polite">Carregando campos…</p>
                    <p v-if="participantFieldsSaving[subject.id]" class="member-loading" role="status" aria-live="polite">Salvando configuração…</p>
                    <template v-if="participantFieldsLoaded[subject.id]">
                      <ul v-if="participantFieldsBySubject[subject.id]?.length" class="participant-field-list" :aria-label="`Campos de participantes do assunto ${subject.name}`">
                        <li v-for="field in participantFieldsBySubject[subject.id]" :key="field.id" class="participant-field-row">
                          <div class="participant-field-identity">
                            <span class="participant-field-key">{{ field.key }}</span>
                            <span class="muted text-caption">Identificador estável</span>
                          </div>
                          <q-input
                            v-model="participantFieldDrafts[subject.id][field.id].label"
                            :label="`Nome do campo: ${field.label}`"
                            outlined dense maxlength="120"
                            :disable="!!participantFieldsLoading[subject.id] || !!participantFieldsSaving[subject.id]"
                          />
                          <q-select
                            v-model="participantFieldDrafts[subject.id][field.id].inputType"
                            :options="participantFieldTypeOptions"
                            :label="`Tipo do campo: ${field.label}`"
                            emit-value map-options outlined dense
                            :disable="!!participantFieldsLoading[subject.id] || !!participantFieldsSaving[subject.id]"
                          />
                          <q-input
                            v-model.number="participantFieldDrafts[subject.id][field.id].order"
                            type="number" min="0" max="1000" step="1"
                            :label="`Ordem do campo: ${field.label}`"
                            outlined dense
                            :disable="!!participantFieldsLoading[subject.id] || !!participantFieldsSaving[subject.id]"
                          />
                          <q-toggle
                            v-model="participantFieldDrafts[subject.id][field.id].required"
                            label="Obrigatório"
                            :disable="!!participantFieldsLoading[subject.id] || !!participantFieldsSaving[subject.id]"
                          />
                          <div class="participant-field-actions">
                            <q-btn
                              outline no-caps color="primary" label="Salvar campo"
                              :loading="participantFieldsSaving[subject.id]"
                              :disable="!!participantFieldsLoading[subject.id] || !!participantFieldsSaving[subject.id] || !hasParticipantFieldChanges(subject.id, field)"
                              @click="saveParticipantField(subject, field)"
                            />
                            <q-btn
                              flat no-caps color="negative" label="Remover"
                              :disable="!!participantFieldsLoading[subject.id] || !!participantFieldsSaving[subject.id]"
                              @click="requestParticipantFieldRemoval(subject, field)"
                            />
                          </div>
                        </li>
                      </ul>
                      <p v-else class="empty-state attachment-empty" role="status">Nenhum campo de participante foi definido para este assunto.</p>
                      <q-form class="participant-field-create-form" @submit.prevent="createParticipantField(subject)">
                        <h6 class="attachment-create-title">Adicionar campo</h6>
                        <q-input
                          v-model="newParticipantFieldDraft(subject.id).key"
                          label="Identificador do campo"
                          hint="Use letras minúsculas, números e _; comece com uma letra."
                          maxlength="64" outlined dense
                          :disable="!!participantFieldsLoading[subject.id] || !!participantFieldsSaving[subject.id]"
                        />
                        <q-input
                          v-model="newParticipantFieldDraft(subject.id).label"
                          label="Nome exibido"
                          maxlength="120" outlined dense
                          :disable="!!participantFieldsLoading[subject.id] || !!participantFieldsSaving[subject.id]"
                        />
                        <q-select
                          v-model="newParticipantFieldDraft(subject.id).inputType"
                          :options="participantFieldTypeOptions"
                          label="Tipo de resposta"
                          emit-value map-options outlined dense
                          :disable="!!participantFieldsLoading[subject.id] || !!participantFieldsSaving[subject.id]"
                        />
                        <q-input
                          v-model.number="newParticipantFieldDraft(subject.id).order"
                          type="number" min="0" max="1000" step="1"
                          label="Ordem de exibição"
                          outlined dense
                          :disable="!!participantFieldsLoading[subject.id] || !!participantFieldsSaving[subject.id]"
                        />
                        <q-toggle v-model="newParticipantFieldDraft(subject.id).required" label="Obrigatório" :disable="!!participantFieldsLoading[subject.id] || !!participantFieldsSaving[subject.id]" />
                        <q-btn
                          type="submit" outline no-caps color="primary" label="Adicionar campo"
                          :loading="!!participantFieldsSaving[subject.id]"
                          :disable="!!participantFieldsLoading[subject.id] || !!participantFieldsSaving[subject.id] || !validParticipantField(newParticipantFieldDraft(subject.id))"
                        />
                      </q-form>
                    </template>
                    <div v-else-if="participantFieldsErrors[subject.id]" class="member-retry">
                      <q-btn outline no-caps color="primary" label="Tentar carregar campos" :disable="!!participantFieldsLoading[subject.id] || !!participantFieldsSaving[subject.id]" @click="loadParticipantFields(subject)" />
                    </div>
                  </div>
                </section>
                <section class="subject-attachment-policy" :aria-labelledby="`attachment-policy-title-${subject.id}`">
                  <div class="attachment-policy-heading">
                    <h5 :id="`attachment-policy-title-${subject.id}`" class="attachment-policy-title">Documentos solicitados</h5>
                    <p class="member-access-note">Defina os tipos de arquivo aceitos e se cada documento é obrigatório.</p>
                  </div>
                  <ul v-if="(subject.attachmentRequirements || []).length" class="attachment-rule-list" :aria-label="`Documentos configurados para ${subject.name}`">
                    <li
                      v-for="requirement in subject.attachmentRequirements"
                      :key="requirement.id"
                      class="attachment-rule"
                      :class="{ 'attachment-rule-inactive': !requirement.active }"
                    >
                      <template v-if="requirement.active">
                        <q-input
                          v-model="attachmentRequirementDrafts[requirement.id].name"
                          :label="`Nome do documento: ${requirement.name}`"
                          outlined dense maxlength="120"
                          :disable="!!attachmentPolicyActionId || protocolSubjectSaving"
                        />
                        <q-toggle
                          v-model="attachmentRequirementDrafts[requirement.id].required"
                          label="Obrigatório para novos pedidos"
                          :disable="!!attachmentPolicyActionId || protocolSubjectSaving"
                        />
                        <q-select
                          v-model="attachmentRequirementDrafts[requirement.id].allowedExtensions"
                          :options="attachmentExtensionOptions"
                          :label="`Tipos de arquivo aceitos: ${requirement.name}`"
                          multiple use-chips outlined dense
                          :disable="!!attachmentPolicyActionId || protocolSubjectSaving"
                        />
                        <div class="attachment-rule-actions">
                          <q-btn
                            color="primary" outline no-caps label="Salvar documento"
                            :loading="attachmentPolicyActionId === requirement.id"
                            :disable="!!attachmentPolicyActionId || protocolSubjectSaving || !validAttachmentDraft(attachmentRequirementDrafts[requirement.id]) || !hasAttachmentRequirementChanges(requirement)"
                            @click="saveAttachmentRequirement(subject, requirement)"
                          />
                          <q-btn
                            color="negative" flat no-caps label="Desativar"
                            :loading="attachmentPolicyActionId === requirement.id"
                            :disable="!!attachmentPolicyActionId || protocolSubjectSaving"
                            @click="deactivateAttachmentRequirement(subject, requirement)"
                          />
                        </div>
                      </template>
                      <div v-else class="attachment-rule-summary">
                        <span class="attachment-name">{{ requirement.name }}</span>
                        <span class="muted text-caption">Desativado para novos pedidos</span>
                      </div>
                    </li>
                  </ul>
                  <p v-else class="empty-state attachment-empty" role="status">Nenhum documento foi definido para este assunto.</p>
                  <q-form class="attachment-create-form" @submit.prevent="createAttachmentRequirement(subject)">
                    <h6 class="attachment-create-title">Adicionar documento</h6>
                    <q-input
                      v-model="newAttachmentDrafts[subject.id].name"
                      outlined dense maxlength="120" label="Nome do documento"
                      :disable="!!attachmentPolicyActionId || protocolSubjectSaving"
                    />
                    <q-toggle
                      v-model="newAttachmentDrafts[subject.id].required"
                      label="Obrigatório para novos pedidos"
                      :disable="!!attachmentPolicyActionId || protocolSubjectSaving"
                    />
                    <q-select
                      v-model="newAttachmentDrafts[subject.id].allowedExtensions"
                      :options="attachmentExtensionOptions"
                      label="Tipos de arquivo aceitos"
                      multiple use-chips outlined dense
                      :disable="!!attachmentPolicyActionId || protocolSubjectSaving"
                    />
                    <q-btn
                      type="submit" color="primary" outline no-caps label="Adicionar documento"
                      :loading="attachmentPolicyActionId === `new:${subject.id}`"
                      :disable="!!attachmentPolicyActionId || protocolSubjectSaving || !validAttachmentDraft(newAttachmentDrafts[subject.id])"
                    />
                  </q-form>
                </section>
              </li>
            </ul>
          </section>
        </aside>
        <AdministrativeProcessPanel
          v-if="staffLoaded"
          :organization-id="organizationId"
          :departments="departments"
          :can-manage-definitions="canManageDepartments"
          @process-busy-change="administrativeProcessBusy = $event"
        />
      </div>

      <footer class="app-footer">NexoGov <span aria-hidden="true">·</span> Área do servidor</footer>
    </q-page></q-page-container>

    <q-dialog v-model="showSuspensionDialog" aria-labelledby="suspension-dialog-title" aria-describedby="suspension-dialog-description">
      <q-card class="confirmation-dialog">
        <q-card-section>
          <h2 id="suspension-dialog-title" class="section-heading">Suspender acesso?</h2>
          <p class="confirmation-dialog-person">{{ pendingSuspension?.fullName || pendingSuspension?.username }}</p>
          <p id="suspension-dialog-description" class="confirmation-dialog-copy">
            Esta pessoa perderá acesso a {{ organizations.find(org => org.id === organizationId)?.name || 'esta organização' }}. O cadastro, as lotações e o histórico serão preservados.
          </p>
        </q-card-section>
        <q-card-actions align="right" class="confirmation-dialog-actions">
          <q-btn flat no-caps label="Cancelar" :disable="!!memberActionUsername" @click="cancelSuspension" />
          <q-btn color="negative" unelevated no-caps label="Confirmar suspensão" :loading="!!memberActionUsername" @click="confirmSuspension" />
        </q-card-actions>
      </q-card>
    </q-dialog>
    <q-dialog v-model="showParticipantFieldRemovalDialog" aria-labelledby="participant-field-removal-title" aria-describedby="participant-field-removal-description">
      <q-card class="confirmation-dialog">
        <q-card-section>
          <h2 id="participant-field-removal-title" class="section-heading">Remover campo?</h2>
          <p class="confirmation-dialog-person">{{ participantFieldRemoval?.field.label }}</p>
          <p id="participant-field-removal-description" class="confirmation-dialog-copy">
            O campo deixará de fazer parte da configuração de “{{ participantFieldRemoval?.subject.name }}”.
          </p>
        </q-card-section>
        <q-card-actions align="right" class="confirmation-dialog-actions">
          <q-btn flat no-caps label="Cancelar" @click="cancelParticipantFieldRemoval" />
          <q-btn color="negative" unelevated no-caps label="Confirmar remoção" @click="confirmParticipantFieldRemoval" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-layout>
</template>
