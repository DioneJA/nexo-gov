# Incremento 34 — contrato do prazo por assunto e estado oficial do certame

**Execução:** 07/11/2026
**Escopo:** corrigir semântica do `PUT` do catálogo de assuntos para dias exibidos no recibo, validar o contrato OpenAPI e sincronizar evidências da matriz. Este incremento não altera o NexoLar nem declara aderência integral ao edital.

## Alterações

- `SubjectUpdateRequest` agora registra se `automaticDeadlineDays` foi enviado. Em atualização, campo omitido preserva o valor configurado; `null` explícito remove a configuração. Zero continua inválido. O serviço aplica a mesma semântica na persistência, no retorno e na auditoria.
- O teste Testcontainers cobre atualização omitida e `null` explícito usando corpo JSON bruto, além de isolamento entre organizações, snapshots anteriores e novos, recibo e valor zero.
- O OpenAPI corrige a lista `required` de `OrganizationProtocolSubject`, deixa o campo de prazo opcional e anulável e documenta os comportamentos de criação e atualização. `OpenApiContractTest` analisa YAML e valida a estrutura e a semântica registrada.
- A interface identifica os dias como informação do comprovante e esclarece que o produto não calcula data de vencimento. O cenário Playwright percorre configuração administrativa, abertura cidadã e extração do PDF real.
- O registro oficial do certame informa que a 1DOC foi aprovada na POC em 03/10/2025. O NexoGov não participou; a ata não detalha os critérios demonstrados e o relatório técnico citado não está preservado neste projeto.
- A matriz, o roteiro POC e os riscos foram regenerados a partir dos scripts fonte. `TR91-0517` cobre apenas configuração positiva, snapshot na abertura e exibição no recibo; as obrigações distintas de gestão de prazos/alertas e os requisitos de anexos mantêm seus status próprios.
- A revisão de consistência corrigiu também o resumo desatualizado da linha `TR91-0387`: 5/742 requisitos do TR estão `VALIDADO` em critérios delimitados e 717/742 aguardam decomposição em aceite observável. O ensaio dessa linha é somente uma revisão interna de prontidão; não é a POC histórica do certame.

## Verificações executadas

- `./dev openapi-check`: **1 teste aprovado**.
- `./dev protocol-deadline-test`: **1 teste de integração aprovado** com PostgreSQL Testcontainers.
- `./dev e2e --grep 'prazo automático no comprovante'`: **1 cenário Playwright aprovado** (24,0 s), com API e PostgreSQL descartáveis e extração do PDF real.
- `./dev verify-fast`: Core **11/11**, backend **42/42**, build Vue/Quasar e verificação da matriz aprovados.
- `./dev matrix-refresh`: regenerou artefatos a partir das fontes e validou **1.388** requisitos, sem IDs numéricos instáveis.
- `./dev matrix`: aprovado; status agregado **8 VALIDADO**, **39 EM_DESENVOLVIMENTO** e **1.341 NAO_INICIADO**. Esses oito registros são validações delimitadas por critério e evidência, não declaração de conformidade global.

## Limites e pendências

- Os dias são exibidos como prazo configurado; não há cálculo de data final, calendário, feriados, alertas ou gestão integral de vencimentos demonstrada por este incremento.
- O E2E confirma o caso de configuração, persistência e impressão; não é POC oficial, laudo da comissão, evidência de produção ou demonstração das condições integrais de sessão da POC.
- O estado publicado do PE 91/2025 é de adjudicação/homologação e contrato com a 1DOC. O edital permanece benchmark para evolução e oportunidades futuras; este projeto não pode ganhar aquele certame encerrado.
- Não foi necessário criar novo comando de harness: `./dev protocol-deadline-test` e `./dev openapi-check` já executam as provas focadas; `dev`, `README.md` e `AGENTS.md` descrevem seu escopo. A skill `nexogov-vertical-slice` registra a regra reutilizável de presença do campo, teste com JSON bruto e validação do OpenAPI.

## Arquivos deste incremento

- Backend: `ProtocolSubjectDtos.java`, `ProtocolSubjectService.java`, `ProtocolVerticalIntegrationTest.java`, `OpenApiContractTest.java`.
- Contrato e interface: `contracts/openapi/nexogov-protocol-v1.yaml`, `frontend/src/staff/StaffApp.vue`, `frontend/e2e/protocol-subject-catalog.spec.ts`.
- Rastreabilidade: `scripts/extend-matrix-from-tr-ocr.py`, `scripts/render-traceability.py`, `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv`, `MATRIZ_ADERENCIA.md`, `POC.md`, `RISCOS.md` e `evidencias/POC-PROTOCOLO-E2E.md`.
- Registro oficial e orientação de desenvolvimento: `docs/licitacoes/mata-de-sao-joao-91-2025/andamento-oficial/STATUS_CERTAME.md`, `REQUISITOS.md`, `README.md`, `AGENTS.md`, `dev` e `.agents/skills/nexogov-vertical-slice/SKILL.md`.
