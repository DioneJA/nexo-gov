# Evidência E2E — fluxo vertical de protocolo

**Execução:** 06/11/2026
**Natureza:** teste automatizado interno; não é ata nem resultado de POC oficial.

## Ambiente

- Frontend Vue 3/Quasar em Chrome via Playwright.
- API Spring Boot no perfil `local`.
- PostgreSQL 16.4 em volume Compose temporário `nexogov-e2e`; o contêiner e o volume foram removidos ao fim da execução.
- Organização de demonstração: Município Alfa; setor de destino: Secretaria de Obras.
- Dados totalmente fictícios gerados pelo teste; nenhum dado pessoal real foi usado.

## Passos demonstrados

1. Abrir solicitação na Central de Atendimento e obter número e token de consulta.
2. Baixar o recibo PDF; a integração da API decodifica o QR e verifica que aponta para o token emitido.
3. Entrar na área local do servidor e localizar o protocolo na caixa de entrada.
4. Encaminhar, receber, despachar para Secretaria de Obras, receber e concluir.
5. Consultar o protocolo como requerente e confirmar o estado `Concluído`.

## Resultado

- Playwright/Chrome: **1 teste aprovado**, 0 falhas.
- Testes backend Testcontainers/PostgreSQL: **3 testes aprovados**, 0 falhas, incluindo concorrência da numeração, QR PDF, token inválido, isolamento entre organizações, associação ao setor, histórico append-only e perfil não local fechado por padrão.
- Build do frontend: `npm run build` aprovado depois de `npm ci`.
- Status de aderência: os requisitos relacionados permanecem `EM_DESENVOLVIMENTO`, porque este fluxo não implementa o escopo completo de cada item.

## Requisitos relacionados

`ED91-0035`, `ED91-0059`, `ED91-0091`, `ED91-0101`, `ED91-0124`, `ED91-0144`, `ED91-0477`, `ED91-0479`, `ED91-0483` e `ED91-0566`.

## Limites da evidência

O ensaio básico prova somente este caminho vertical no ambiente local. Não demonstra login externo, permissões configuráveis, calendário, módulo completo de aprovação de projetos, assinatura, notificação, migração, SLA, treinamento, capacidade de produção ou os demais cenários da POC oficial. A cobertura parcial de anexos de protocolo e de interações posteriores está registrada separadamente abaixo; ela não prova storage produtivo.

## Evolução — administração de setores por organização

**Execução:** 06/11/2026
**Natureza:** teste automatizado interno; não é ata nem resultado de POC oficial.

- Backend Testcontainers/PostgreSQL 16.4: **5 testes aprovados**, 0 falhas, incluindo `departmentAdministrationIsTenantScopedAdminOnlyAndAudited` e teste de segurança da migração para dados duplicados antigos.
- O teste administrativo verifica que apenas o administrador ativo no escopo da organização pode criar setor; um usuário com papel `ADMIN` somente em setor recebe 403, a capacidade informa que não pode administrar, nomes repetidos sem distinção entre maiúsculas/minúsculas retornam conflito, a mesma denominação pode existir em outra organização e eventos de auditoria rejeitam atualização, exclusão e truncamento.
- Playwright/Chrome: **1 teste aprovado**; o roteiro cria um setor na sessão administrativa e, em seguida, executa o fluxo vertical completo de protocolo.
- Build Vue/Quasar: `npm run build` aprovado.
- Migração `V2__organization_administration_audit.sql` cria vínculo de papel organizacional, índice de unicidade por organização e tabela de auditoria append-only. Criação do setor e evento de auditoria são gravados na mesma transação. Se dados antigos tiverem nomes duplicados sem distinção de caixa, a migração falha com IDs e instrução de reconciliação, sem alterar registros.
- Requisitos parciais relacionados: `ED91-0009`, `ED91-0087`, `ED91-0103`, `ED91-0104`, `TR91-0015`, `TR91-0111` e `TR91-0112`.

Este incremento permite cadastrar setores planos. Ainda não implementa subsetores hierárquicos, organograma dinâmico, contatos, cargos, servidores, vínculos múltiplos nem gestão integral de usuários/perfis. Por isso, as sete linhas permanecem `EM_DESENVOLVIMENTO`, sem declaração de conformidade integral.

## Evolução — recibo com setor inicial e isolamento de transições

**Execução:** 07/11/2026

**Natureza:** testes automatizados internos; não são ata nem resultado de POC oficial.

- Migração V11 acrescenta o snapshot nullable `initial_assigned_department_name`; registros anteriores permanecem nulos porque o setor inicial não pode ser reconstituído com segurança.
- `./dev protocol-routing-test`: **6 testes aprovados**, 0 falhas, com PostgreSQL Testcontainers. O PDF é lido antes e depois de encaminhar/despachar; o recibo legado não inventa setor; `forward` sem body/destino retorna 400; os demais comandos aceitam body ausente; os quatro comandos retornam 404 sem alteração de estado, setor ou eventos em tentativa cross-tenant.
- `./dev e2e --grep 'administrador configura o destino e o setor aparece no recibo real do protocolo'`: **1 teste aprovado**, Chrome + API/PostgreSQL reais em ambiente Compose descartável removido ao final. O cidadão recebe confirmação imediatamente, baixa PDF real e o texto contém o setor configurado.
- `TR91-0510` fica `VALIDADO` apenas para o critério literal do recibo imediato com setor destinatário. `ED91-0481` permanece `EM_DESENVOLVIMENTO`: acesso setorial integral ao conteúdo/movimentações e resposta direta ainda não estão demonstrados.
- O relatório da matriz e a POC foram atualizados por `./dev matrix-refresh`; `./dev matrix` e `git diff --check` passaram. A POC oficial não foi executada.

## Evolução — perfil básico e presença dos servidores

**Execução:** 06/11/2026

**Natureza:** testes automatizados internos; não são ata nem resultado de POC oficial.

- A migração V5 cria perfis vinculados à organização e ao usuário. Os vínculos existentes recebem perfil sem data presumida; a data só é exibida como conhecida quando informada por origem confiável.
- A administração de organização pode atualizar nome, cargo e data conhecida de um servidor ativo. A API exige papel organizacional de administrador, mantém o tenant validado no servidor e registra auditoria sem copiar nome ou outros dados pessoais para o evento.
- Atividade autenticada atualiza `last_online_at`; o heartbeat da área interna mantém a atividade recente. O organograma exibe `ONLINE` até dois minutos, `OFFLINE` depois dessa janela e `NOT_OBSERVED` quando nunca houve atividade registrada. Isso representa atividade recente, não uma conexão contínua.
- `staffProfilesAndPresenceAreTenantScopedAndAudited` verifica persistência, estados de presença, auditoria, autorização e ausência de vazamento entre organizações usando PostgreSQL Testcontainers.
- Playwright/Chrome atualiza e confere nome, cargo e data fictícios, visualiza a presença e executa o fluxo de protocolo; 1 cenário E2E aprovado.
- A matriz registra `ED91-0103`, `ED91-0107`, `TR91-0111` e `TR91-0115` como `VALIDADO` dentro dos critérios e evidências documentados. `ED91-0105` e `TR91-0113` permanecem `EM_DESENVOLVIMENTO`: ainda não há identidade persistente da qual ler a data original de criação da conta. Oito itens relacionados não significam que o organograma ou gestão de servidores estejam integralmente prontos.

**Limite:** esta fatia não implementa diretório completo de identidade, importação do cadastro municipal, provisionamento seguro de usuários, gestão de múltiplas lotações e papéis, nem presença em tempo real por conexão contínua. Não usar dados pessoais reais. A POC do certame ocorreu em 2025 e aprovou a 1DOC; o NexoGov não participou nem foi avaliado.

## Evolução — hierarquia e organograma parcial

**Execução:** 06/11/2026

**Natureza:** testes automatizados internos; não são ata nem resultado de POC oficial.

- `ProtocolVerticalIntegrationTest.departmentHierarchyIsTenantScopedAndHasNoConfiguredDepthLimit` cria uma raiz e 32 níveis no PostgreSQL Testcontainers, confirma a lotação ativa e verifica que o pai de outra organização retorna 404 e que outra organização não recebe a árvore.
- Playwright/Chrome: **1 teste aprovado**; cria um setor raiz e um subsetor pela área do servidor, confere a árvore atualizada e então conclui o fluxo de protocolo pelo portal e pela área interna.
- A V4 adiciona o vínculo pai/filho com restrição de mesma organização. O endpoint monta o organograma a partir dos setores e lotações ativos do tenant atual.
- `./dev verify`: NEXO Core **3/3**, backend NexoGov **10/10**, build de produção Vue/Quasar aprovado, matriz **1.388/1.388 linhas estruturalmente válida** e E2E **1/1** aprovado. O banco E2E usou volume Compose temporário, removido ao final.
- O detector visual Impeccable não apontou ocorrências nos arquivos avaliados. As páginas pública e interna responderam HTTP 200 e foram inspecionadas no navegador local.
- Os requisitos relacionados continuam `EM_DESENVOLVIMENTO`: o edital e o TR pedem profundidade ilimitada, mas o ensaio mediu 32 níveis; importação do organograma municipal, cargo/posição, datas e indicadores online/offline ainda não foram implementados ou demonstrados.

## Evolução — anexos privados por assunto de protocolo

**Execução:** 07/11/2026

**Natureza:** teste automatizado interno de API/integração; não é E2E da interface, prova operacional de hospedagem nem resultado de POC oficial.

- `./dev attachment-test`: **1 teste aprovado**, 0 falhas, com PostgreSQL Testcontainers e Adobe S3Mock. `ProtocolVerticalIntegrationTest#protocolAttachmentsAreRequiredValidatedPrivateAndTenantScoped` cadastra regra obrigatória PDF, rejeita duplicidade, anexo obrigatório ausente e `.exe` sem consumir numeração, persiste/consulta e baixa PDF, verifica tamanho/contagem e recusa token inválido (404), outro tenant (404) e setor sem vínculo (403).
- `./dev e2e --grep 'assunto exige documento'`: **1 teste aprovado de 1**, em 24,1 s, em infraestrutura descartável. `frontend/e2e/protocol-attachments.spec.ts` autentica ADMIN de Alfa, configura documento obrigatório, confirma que o cidadão não submete sem arquivo, envia PDF, consulta e baixa como cidadão, e depois o servidor lista e baixa o mesmo anexo. O teste demonstra o fluxo de UI nos dois portais para o protocolo inicial e PDF obrigatório.
- O build Vue/Quasar também foi aprovado. O E2E de abertura não testa regra opcional, todos os formatos, prazo ou acesso de outro tenant/setor. O E2E separado de interações posteriores passou 1/1; a integração Testcontainers correspondente está registrada abaixo.
- Naquele registro, `ED91-0484`, `TR91-0516`, `TR91-0019`, `ED91-0170` e `TR91-0174` estavam em `EM_DESENVOLVIMENTO`, e `TR91-0517` ainda aguardava atualização da matriz. O estado atual do prazo está documentado na seção de evolução abaixo; `TR91-0525` permanece em desenvolvimento com evidência parcial de conversa.

**Limites:** S3Mock valida o uso da API compatível no teste e não demonstra bucket/servidor produtivo não público, política operacional ou cumprimento de obrigação contratual de hospedagem. Regras opcionais não têm teste dedicado e os formatos cobertos são limitados. A interação posterior está descrita separadamente abaixo; não há antivírus, pré-visualização ou reconciliação durável de objetos órfãos. A sessão de 90 minutos prorrogáveis da Errata I §1, p.1 não foi ensaiada; a POC oficial integral não foi executada.

## Evolução — interações posteriores, anexos e reabertura de protocolo

**Execução:** 07/11/2026

**Natureza:** teste automatizado interno; não é ata nem resultado de POC oficial.

- Fonte oficial: Termo de Referência §4, p.50 (`TR91-0525`); conclusão e reabertura: Edital §14.1.13, p.33 (`ED91-0483`) e TR §4, p.49 (`TR91-0515`). A configuração de formato por assunto é complementar aos requisitos de anexos `ED91-0484`/`TR91-0516`.
- Playwright/Chrome: `./dev e2e --grep 'conversa de protocolo'`, **1/1 aprovado** em Compose descartável. O cenário cria assunto de Alfa com PDF permitido, abre protocolo com token do requerente, troca mensagem pública com arquivo entre setor e requerente, confirma mensagem interna invisível ao cidadão, conclui e bloqueia novo envio externo, reabre no fluxo normal e aceita nova mensagem.
- O E2E não demonstra que pessoas externas adicionais conseguem acompanhar/interagir: o token emitido representa somente o requerente original. Não há cadastro de participantes, credenciais individuais ou isolamento de acompanhamento por pessoa; `TR91-0524` permanece `NAO_INICIADO`.
- `./dev interaction-test` passou quatro testes backend selecionados com PostgreSQL Testcontainers/S3Mock (`protocolInteractionsAreAppendOnlyPrivateAndReopenRequiresResponsibleSector`, `applicantInteractionSerializesWithProtocolCompletion`, `interactionHistoryUsesWriteOrderWhenAnOlderTransactionWaitsForProtocolLock` e `attachmentPolicyChangesWaitForProtocolSubjectReadLock`) e Playwright 1/1 contra API/storage descartáveis. A integração verifica isolamento tenant/setor, anexos internos, histórico append-only e sua ordem após lock, recusas de mensagem após conclusão, reabertura por setor responsável e corrida com conclusão.
- `ED91-0483` e `TR91-0515` estão `VALIDADO` somente para o critério literal de conclusão, bloqueio e reabertura por servidor do setor responsável. `TR91-0525` permanece `EM_DESENVOLVIMENTO`; `TR91-0524` permanece `NAO_INICIADO`. `ED91-0484`/`TR91-0516` continuam parciais: há configuração/uso de PDF, mas não há comprovação da regra opcional, lista completa de formatos nem prazo automático `TR91-0517`.

**Limites:** esse cenário não valida participantes além do requerente, formatos além do PDF, infraestrutura de storage de produção, a sessão de POC de 90 minutos ou o edital integral. A ata registra a aprovação da 1DOC na POC do certame; o NexoGov não participou e não possui laudo oficial.

## Evolução — prazo por assunto exibido no comprovante

**Execução:** 07/11/2026

**Natureza:** validação automatizada do critério de software de `TR91-0517`; não é laudo nem POC oficial.

- Fonte oficial: Termo de Referência §4, PDF pp.49–50, cláusula que permite configurar prazo em dias para cada assunto e exibi-lo no documento. O parágrafo seguinte trata separadamente da gestão de prazos/alertas.
- `./dev protocol-deadline-test`: **1 teste aprovado**, 0 falhas, com PostgreSQL Testcontainers. `subjectDeadlineIsConfiguredSnapshottedAndPrintedOnReceipt` prova configuração por ADMIN, recusa de edição cross-tenant sem mutação, preservação dos 20 dias quando o campo é omitido no PUT, remoção quando `null` é enviado explicitamente, snapshot no protocolo, imutabilidade do recibo quando o assunto muda de 10 para 20 dias e rejeição de zero (HTTP 400).
- `./dev e2e --grep 'prazo automático no comprovante'`: **1 teste aprovado de 1**, em 24,1 s, com API/PostgreSQL descartáveis e Playwright/Chrome. O ADMIN configura 8 dias, altera para 14, recarrega e confirma persistência; o cidadão abre protocolo e o texto extraído do PDF real contém `Prazo configurado: 14 dias`.
- `./dev openapi-check`: **1 teste aprovado**, validando sintaxe YAML, esquema de resposta opcional/nullável e semântica documentada do PUT.
- `./dev verify-fast`: Core 11/11, backend 42/42, build Vue/Quasar e matriz aprovados. A suíte inclui o teste de integração do prazo e o contrato OpenAPI; as execuções focadas acima deixam essas provas repetíveis de forma isolada.
- `TR91-0517` passa a `VALIDADO` somente para a configuração positiva de dias por assunto e a exibição do valor no comprovante. `ED91-0484` e `TR91-0516` continuam `EM_DESENVOLVIMENTO` pelas lacunas de regra opcional/formato e operação do storage; `TR91-0524` continua sem identidade de participantes adicionais.

**Limites:** essa evidência não equivale à avaliação da comissão, não valida cálculo de data final ou alertas de vencimento, e não promove as obrigações separadas de gestão de prazos. A ata oficial registra que a 1DOC foi aprovada na POC de 03/10/2025; o NexoGov não participou, e esse ensaio interno não representa resultado oficial.
