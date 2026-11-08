# Incremento 20 — acesso de membros por organização

## Entrega

- Adicionadas rotas internas para consultar os membros associados à organização ativa e suspender ou reativar o acesso de um membro. O escopo vem exclusivamente de `TenantContext`, preenchido após validação da associação no servidor; o cliente não envia organização no corpo para definir escopo.
- A autorização administrativa exige `organization_memberships` ativo com `role_code='ADMIN'` na mesma organização. Um papel `ADMIN` em `staff_memberships` continua sendo papel de setor e não concede administração organizacional.
- A suspensão usa `organization_member_access_status`, uma tabela de estado organizacional independente. Não altera `user_accounts.active`, vínculos de organização ou setor, perfis, autoria de registros ou histórico. A ausência de uma linha preserva o acesso anterior. A reativação torna o vínculo organizacional utilizável novamente, sem reconstruir ou modificar os demais vínculos.
- `TenantMembershipFilter` consulta esse estado em cada requisição protegida. Portanto, uma sessão autenticada que tente continuar usando a organização após a suspensão é recusada na próxima chamada; reativar o vínculo permite nova autorização, sujeita à associação e autenticação existentes.
- Alterar o estado bloqueia a linha da organização (`FOR UPDATE`) dentro da transação, revalida a autorização e verifica o último administrador organizacional ativo antes da suspensão. Isso serializa suspensões concorrentes dentro da mesma organização. Mudanças efetivas produzem eventos em `organization_audit_events`; operações idempotentes não emitem evento adicional.
- O registro de `TenantMembershipFilter` no container Servlet foi explicitamente desativado. Ele é inserido manualmente na cadeia Spring Security e os testes confirmam a configuração de execução única.
- O contrato OpenAPI agora descreve Bearer/JWT para identidade configurada em produção e Basic somente no perfil local, além dos endpoints e schemas de membros. Isso corrige a descrição do modo de autenticação sem alterar a publicação ou as dependências do Core.

## Rotas e dados

- `GET /api/v1/staff/organization/members`: lista usuários associados à organização ativa, papéis separados por escopo, perfil funcional disponível e estado de acesso.
- `PUT /api/v1/staff/organization/members/{username}/access`: recebe apenas `{ "active": boolean }`; responde com o estado efetivo e se houve alteração. Suspender o último administrador ativo retorna `409 Conflict`.
- A migração Flyway V7 é forward-only e cria o estado organizacional com unicidade por organização/usuário e autoria da última alteração. Não migra, apaga ou reescreve registros existentes.

## Rastreabilidade

Atualizados os critérios de gestão de servidores e permissões em `ED91-0042`, `ED91-0046`, `TR91-0052` e `TR91-0056`, todos mantidos como `EM_DESENVOLVIMENTO`. Foram descritos o backend real, teste e evidência disponíveis; o incremento não implementa telas administrativas e não conclui o escopo integral daqueles requisitos.

A correção dos geradores de rastreabilidade mantém os status atuais, mas deixa de omitir os requisitos `VALIDADO` no resumo do Termo de Referência. A matriz agora informa corretamente **2/742** requisitos TR validados apenas em escopo delimitado e **727/742** ainda pendentes de decomposição em critérios observáveis. O texto da POC esclarece que a POC integral não foi executada e que conformidade global não está comprovada. Isso não representa alteração de escopo ou status dos requisitos TR.

A revisão final corrigiu a listagem de papéis para incluir somente vínculos ativos e fortaleceu o teste concorrente para exigir uma suspensão vencedora e conflito/negação para a requisição concorrente. O resumo inclui **224** obrigações sem implementação exclusiva em software — operação contratual 109, integração externa 78, habilitação 5, obrigação externa/contratual 26, POC 5 e proposta comercial 1 — usando escopo igual no total e no detalhamento.

## Verificação

- `./dev admin-access-test`: **4/4** testes de integração passaram com PostgreSQL 16.4 via Testcontainers. Cobrem autorização por papel organizacional, papel de setor sem privilégio organizacional, isolamento cross-tenant, suspensão/reativação por sessão previamente autenticada, auditoria, preservação de conta/memberships/perfil/autoria/histórico, último administrador e concorrência.
- `./dev verify-fast`: terminou com sucesso. NEXO Core **6/6** testes; backend **19/19** testes (incluindo as quatro provas deste incremento, protocolo e identidade federada); build de produção do frontend público e interno; verificação da matriz com **1.388** linhas.
- `./dev matrix-refresh`: geradores executados. Resultado atual: **1.388** linhas; `EM_DESENVOLVIMENTO=27`, `NAO_INICIADO=1.357`, `VALIDADO=4`; para TR, **742** itens, sendo `NAO_INICIADO=732`, `EM_DESENVOLVIMENTO=8` e `VALIDADO=2`.
- Parse do YAML OpenAPI: válido, com **13** operações de rota e esquemas de autenticação `StaffBearer`, `StaffBasic` e `TrackingToken`.
- `./dev matrix`: passou com **1.388** linhas e **1.377** IDs numéricos estáveis; o resumo reporta **2/742** TR validados em escopo limitado e **727/742** aguardando decomposição.
- `git diff --check`: passou após incorporar esta documentação.

## Limites e pendências

- Não foram alteradas telas frontend neste incremento. A administração visual de membros ainda não está entregue.
- A prova cobre a autorização nas rotas e no filtro de tenant. Não prova operações diretas de banco fora da aplicação, revogação de sessões em outros serviços nem comportamento de tokens emitidos por um IdP municipal real.
- O provedor de identidade, emissão e revogação de tokens e políticas de provisionamento continuam dependendo de configuração e operação externa. Basic permanece apenas no ambiente local de desenvolvimento.
- Esta funcionalidade não comprova conformidade integral do edital, aceite de uma POC oficial ou execução das obrigações contratuais. Permanecem 727/742 critérios TR sem decomposição em aceite observável.
- Sem atualização adicional do harness para a revisão final: `./dev admin-access-test` foi incluído neste incremento e centraliza a prova focada; `./dev verify-fast` já o inclui na suíte geral, e `./dev matrix` cobre o resumo atualizado. A documentação reutilizável da skill vertical foi atualizada com as regras de autorização por organização, estado separado, verificação em cada requisição, serialização do último administrador e modos de autenticação. A skill de rastreabilidade registra a contagem integral dos status, o escopo dos totais e a ressalva sobre POC/conformidade.

## Arquivos principais

- Backend: `backend/src/main/java/br/com/nexo/gov/organization/OrganizationAdminController.java`, `OrganizationAdminService.java`, `DepartmentAdminDtos.java`, `backend/src/main/java/br/com/nexo/gov/security/TenantMembershipFilter.java`, `backend/src/main/java/br/com/nexo/gov/config/SecurityConfiguration.java`.
- Banco e prova: `backend/src/main/resources/db/migration/V7__organization_member_access_status.sql`, `backend/src/test/java/br/com/nexo/gov/organization/OrganizationMemberAccessIntegrationTest.java`.
- Contrato/harness: `contracts/openapi/nexogov-protocol-v1.yaml`, `dev`, `AGENTS.md`.
- Geradores e relatórios: `scripts/build-edital-matrix.py`, `scripts/extend-matrix-from-tr-ocr.py`, `scripts/render-traceability.py`, `docs/licitacoes/mata-de-sao-joao-91-2025/`.
- Skills: `.agents/skills/nexogov-vertical-slice/SKILL.md`, `.agents/skills/nexogov-tender-traceability/SKILL.md`.

Nenhum commit, deploy, publicação ou alteração no NexoLar foi feito.
