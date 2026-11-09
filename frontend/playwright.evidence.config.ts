import { defineConfig } from '@playwright/test'
import baseConfig from './playwright.config'

const approvedScenarioNames = [
  'administrador consulta membros e suspende/reativa acesso pela API da organização',
  'administrador mantém contatos por tipo e importa/exporta CSV',
  'administrador publica identidade visual e a troca de organização descarta respostas antigas',
  'administrador configura aviso municipal e cidadão vê o texto e a política antes do formulário',
  'cidadão consulta protocolo de outra organização sem atribuir a identidade selecionada',
  'administrador configura e mantém campos de identificação por assunto',
  'assunto exige documento, cidadão envia e servidor consulta e baixa o anexo',
  'cidadão abre protocolo, servidor tramita e cidadão acompanha até a conclusão',
  'caixa mostra leitura por setor, por outro servidor e pelo usuário atual',
  'administrador publica workflow versionado e acompanha o snapshot do processo',
  'caixa do servidor ocupa a largura disponível sem overflow após login',
  'process-multiple-deadlines — servidor registra e consulta múltiplos prazos no processo',
  'conversa de protocolo mantém respostas, anexos e visibilidade no fluxo real',
  'administrador configura o prazo automático no comprovante real do protocolo'
]

const safeScenarioPattern = new RegExp(`^(?:.*[ ›])?(?:${approvedScenarioNames.map(name =>
  name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
).join('|')})$`)

export default defineConfig({
  ...baseConfig,
  testMatch: /(?:^|[\\/])(?:inbox-workspace-layout|organization-members|organization-contacts|organization-branding|organization-branding-tracking|organization-privacy-notice|protocol-participant-fields|protocol-attachments|protocol-flow|protocol-read-status|protocol-interactions|protocol-subject-catalog|process-workflow|process-multiple-deadlines)\.spec\.ts$/,
  grep: safeScenarioPattern,
  retries: 0,
  workers: 1,
  outputDir: process.env.NEXOGOV_EVIDENCE_ARTIFACTS_DIR || './test-results/evidence',
  reporter: [
    ['list'],
    ['json', { outputFile: process.env.NEXOGOV_EVIDENCE_JSON_REPORT || './test-results/evidence-report.json' }]
  ],
  use: {
    ...baseConfig.use,
    screenshot: 'off',
    video: 'off',
    trace: 'off'
  }
})
