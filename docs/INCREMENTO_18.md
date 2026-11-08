# Incremento 18 — perfil básico e presença dos servidores

## Entrega

- Criada a tabela tenant-scoped `staff_profiles` para nome, cargo, data de cadastro com origem e atividade autenticada recente.
- A migração V5 inicializa os perfis legados sem inventar datas: registros sem fonte confiável ficam com data desconhecida.
- Incluída edição administrativa do perfil básico de servidor ativo, limitada a administrador da organização. A auditoria registra a operação sem gravar dados pessoais no payload do evento.
- Incluídos heartbeat autenticado e estados textuais `ONLINE`, `OFFLINE` e `NOT_OBSERVED` no organograma. A janela é de dois minutos; a escrita de última atividade é limitada para evitar gravações excessivas.
- A interface interna apresenta nome, cargo, data conhecida, lotação e presença. O portal do cidadão continua separado.
- Atualizados o contrato OpenAPI, o roteiro da POC, as evidências E2E, README, riscos e inventário dos requisitos.

## Rastreabilidade

A matriz permanece com 1.388 linhas: 19 `EM_DESENVOLVIMENTO`, 1.365 `NAO_INICIADO` e 4 `VALIDADO` no escopo dos critérios descritos. Os itens validados são `ED91-0103`, `ED91-0107`, `TR91-0111` e `TR91-0115`. `ED91-0105` e `TR91-0113` seguem parciais porque não existe integração a um cadastro persistente de identidade que forneça a data original da conta. Os demais requisitos ligados a organograma e gestão funcional de pessoas continuam pendentes conforme a matriz.

## Evidência e limites

- PostgreSQL Testcontainers cobre perfil, presença, autorização, auditoria e isolamento entre organizações.
- Playwright/Chrome cobre edição/visualização do perfil, rótulo de presença, troca de organização e fluxo de protocolo com dados fictícios.
- `./dev verify`: NEXO Core **3/3**, backend **11/11**, build de produção das entradas pública e interna, matriz **1.388/1.388** estruturalmente válida e Playwright/Chrome **1/1** aprovado.
- Durante a verificação foi identificada uma corrida no E2E: a consulta pública iniciava antes da resposta assíncrona de conclusão. O roteiro agora aguarda a confirmação e o estado concluído na área do servidor antes de consultar pelo portal; a execução completa subsequente passou.
- A infraestrutura E2E usa banco Compose temporário isolado e o harness o remove ao final. A evidência automatizada local não é POC oficial.
- O perfil local continua usando autenticação de demonstração. Esta mudança não entrega identidade, cadastro de servidores, SSO, sessão de produção ou integração municipal completos.

## Arquivos principais

- Backend: `backend/src/main/java/br/com/nexo/gov/organization/`, `backend/src/main/java/br/com/nexo/gov/security/TenantMembershipFilter.java`, `backend/src/main/resources/db/migration/V5__staff_directory_and_presence.sql`.
- Frontend: `frontend/src/staff/StaffApp.vue`, `frontend/src/api.ts`, `frontend/e2e/protocol-flow.spec.ts`.
- Contrato e rastreabilidade: `contracts/openapi/nexogov-protocol-v1.yaml`, `docs/licitacoes/mata-de-sao-joao-91-2025/`.

Nenhum deploy, publicação, commit ou alteração no NexoLar foi feito.
