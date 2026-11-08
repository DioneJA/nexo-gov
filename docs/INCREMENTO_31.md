# Incremento 31 — Anexos privados em protocolos

## Objetivo

Adicionar uma primeira fatia persistida para regras de documentos por assunto, abertura de protocolo com anexos, armazenamento privado compatível com S3 e consulta/baixa autorizada no portal do cidadão e na área do servidor.

## Decisões e comportamento

- Regras de anexo pertencem ao assunto dentro da organização e só podem ser administradas por `ADMIN` organizacional. A abertura lê as regras sob bloqueio compartilhado; alterações administrativas bloqueiam a mesma linha. Anexos obrigatórios e rejeições de quantidade, extensão, tamanho ou assinatura do conteúdo ocorrem antes de alocar número.
- Cada arquivo é associado explicitamente ao ID de sua regra. Chaves no armazenamento usam IDs gerados pelo servidor, sem nome fornecido pelo usuário. Metadados incluem organização, protocolo, tamanho, tipo, hash SHA-256 e contagem de downloads, com referências compostas por tenant.
- Os downloads não revelam URL de bucket. O cidadão usa o token de acompanhamento; o servidor precisa de vínculo ao setor atual ou papel administrativo organizacional. Arquivos são lidos com limite máximo configurado, tamanho e hash são conferidos antes de servir, e a contagem só aumenta após uma baixa bem-sucedida.
- A lista inicial de formatos é PDF, PNG, JPG/JPEG, TXT e MP4. Outros formatos ainda não estão cobertos; pré-visualização e galeria continuam pendentes.
- O backend usa S3 compatível por configuração de ambiente. O Compose local e os testes usam S3Mock 5.2.3 com versão/digest fixos. Esse dublê cobre operações básicas do fluxo, mas não oferece as garantias de segurança e operação de um serviço de produção. Consulte [Adobe S3Mock](https://github.com/adobe/S3Mock).

## Arquivos e interfaces

- `backend/src/main/java/br/com/nexo/gov/protocol/ProtocolAttachment*`: configuração, política e serviço de armazenamento/anexos.
- `backend/src/main/resources/db/migration/V12__protocol_private_attachments.sql`: políticas e metadados de anexos por tenant.
- `backend/src/main/java/br/com/nexo/gov/protocol/ProtocolController.java` e `ProtocolSubjectController.java`: abertura multipart, consulta/baixa e administração de políticas.
- `contracts/openapi/nexogov-protocol-v1.yaml`: contratos multipart e de acesso autenticado.
- `frontend/src/App.vue`, `frontend/src/main.ts`, `frontend/src/api/public.ts`, `frontend/src/staff/StaffApp.vue`, `frontend/src/api/staff.ts`, `frontend/src/style.css`, `frontend/e2e/protocol-attachments.spec.ts`: seleção de documentos no portal público, consulta e downloads, administração das regras e acesso de servidor, com cenário de navegador por API real.
- `compose.yaml`, `.env.example`, `scripts/start-local.sh`, `scripts/start-e2e.sh`, `scripts/run-e2e.sh`, `scripts/find-free-ports.py` e `dev`: serviço S3Mock local/descartável e execução focada.
- `.agents/skills/nexogov-vertical-slice/SKILL.md`, `AGENTS.md` e `README.md`: regras reutilizáveis, comando do harness e limites de garantia.

## Verificação

- `./dev attachment-test`: passou; o teste de integração usa PostgreSQL Testcontainers e S3Mock, exercitando configuração, política obrigatória, recusas antes da numeração, upload, consulta, download, contagem e negação entre tenants/setores.
- `./dev e2e --grep 'assunto exige documento'`: passou (1 cenário, 24,1 s) em projeto Compose, portas, volume e rede temporários. Demonstra política obrigatória, recusa antes do POST, upload PDF, acompanhamento/baixa do cidadão e listagem/baixa autenticada do servidor. O teste encontrou o registro Vue de `QFile` ausente; `frontend/src/main.ts` foi corrigido e o cenário passou depois.
- `./dev verify-fast`: passou após a correção da asserção de auditoria. NEXO Core: 11 testes; backend: 34 testes; build de produção Vue/Quasar passou; matriz: 1.388 linhas com 37 `EM_DESENVOLVIMENTO`, 1.346 `NAO_INICIADO` e 5 `VALIDADO`.
- `./dev verify`: passou integralmente. E2E Basic: 9 passaram e 4 foram ignorados por pertencerem ao modo OIDC; E2E OIDC isolado: 7 passaram. As duas execuções usaram portas, banco, S3Mock, volume e rede descartáveis removidos ao final.
- `git diff --check` e `./dev matrix` passaram após regenerar CSV, Markdown, roteiro de POC e log de evidências. Nenhum fluxo de tramitação completo nem a POC oficial está validado por estes testes automatizados.
- A primeira execução de `./dev verify-fast` encontrou duas falhas em `ProtocolVerticalIntegrationTest`: uma asserção antiga contava todos os eventos de auditoria da organização como se outras fatias não gravassem eventos no banco compartilhado; o primeiro teste então deixava o membership administrativo de teste removido e causava 403 em um teste seguinte. A asserção agora compara a quantidade específica de `DEPARTMENT_CREATED` antes/depois da tentativa negada, e a restauração dos papéis/memberships está em `finally`. Os dois testes afetados passaram juntos isoladamente e a suíte completa passou após a correção.
- A matriz oficial foi regenerada pelos geradores e verificada. Status parciais não cobrem prazos automáticos, interações posteriores com anexos, todos os formatos, pré-visualização ou obrigações de hospedagem.

## Limites e débitos

- O fluxo recebe anexos na abertura. Interações posteriores entre setor e cidadão com texto e novos arquivos não estão implementadas.
- O cancelamento/rollback tenta apagar objetos gravados; a limpeza é best-effort e ainda não existe reconciliador durável para órfãos.
- Não há varredura antimalware. MIME enviado pelo cliente não é considerado prova; a validação atual usa extensão e assinatura limitada a formatos suportados.
- Não há evidência do bucket de produção: política privada, bloqueio de acesso público, TLS operacional, criptografia em repouso, retenção, versionamento, backup/restauração, monitoramento ou residência de dados precisam ser configurados e comprovados no ambiente contratual.
- O dublê S3Mock não implementa todas as capacidades nem os controles de segurança de um serviço S3 de produção. O teste não prova integração com a conta/bucket contratual.
- A evidência parcial de software para acesso seguro não valida os requisitos separados de operação/hospedagem nem a obrigação de prazo automático por assunto.
- O fluxo de POC do edital ainda precisa cobrir comunicação posterior com anexos, os formatos e verificações exigidos, e as condições operacionais aplicáveis.

## Sincronia de skill e harness

A skill de fatia vertical recebeu regras reutilizáveis de autorização por download, validação anterior à numeração, associação explícita da regra, leitura limitada, integridade, isolamento, limites do S3Mock e isolamento de testes com banco compartilhado. O harness já tinha `./dev attachment-test`; sua descrição consta no `./dev`, `AGENTS.md` e `README.md`, junto da distinção entre o armazenamento local e o serviço de produção. A skill visual não foi alterada porque as telas preservam os tokens do Core e a direção do Civic Register; a revisão identificou e corrigiu estados de envio/erro e relação acessível do painel sem acrescentar tokens.
