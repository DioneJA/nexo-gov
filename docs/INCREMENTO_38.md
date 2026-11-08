# Incremento 38 — workflow administrativo básico

**Data:** 08/11/2026
**Escopo:** publicação versionada de workflow por assunto e abertura/avanço de processo no backend. Não altera o NexoLar, o NEXO Core nem bancos persistentes; a verificação usa PostgreSQL Testcontainers descartável.

## Implementação

- `V19__administrative_workflow_versions_and_process_snapshots.sql` cria definições versionadas tenant-scoped, etapas/setores, processos com snapshot da versão, autorização por lotação e eventos de tramitação.
- `V20__workflow_membership_snapshot_and_publication_safety.sql` guarda nomes de setores nas definições e snapshots de processos, protege publicação com `expectedVersion` e `idempotencyKey`, e força ordem temporal crescente nos eventos novos.
- `AdministrativeWorkflowService` valida escopo por `TenantContext`, exige ADMIN organizacional para publicar/consultar definições, preserva versões e snapshots, e só avança processos uma etapa por vez com membership ativa no estágio atual.
- Retries com a mesma chave e payload devolvem a versão publicada; chave reutilizada com conteúdo diferente ou versão esperada obsoleta recebe conflito. A primeira publicação responde 201; replay responde 200.
- O backend e os testes estão implementados. Não há interface de administração do workflow, cliente integrado, contrato OpenAPI, notificações, exportação cronológica/árvore, condições ou POC integral.

## Rastreabilidade

- `ED91-0596` (Edital §14.1.13, fl. impressa 39, “WORKFLOW”) e `TR91-0691` (TR §11, PDF p.63) mudaram para `EM_DESENVOLVIMENTO` nos geradores correspondentes. Os IDs, textos e origens foram preservados.
- O status é parcial: o teste comprova publicação, snapshot e avanço sequencial por API; não cobre a interface, integração completa, exportações, condições avançadas ou POC oficial. Requisitos de workflow mais amplos não herdam esse estado.
- A matriz, resumo e roteiro foram regenerados com `./dev matrix-refresh`: 1.388 itens; 41 em desenvolvimento, 8 validados apenas no escopo de seus critérios e 1.339 não iniciados.

## Verificações

- `./dev process-workflow-test`: 4 testes aprovados, 0 falhas/erros, PostgreSQL 16.4 via Testcontainers; cobre snapshots de nomes/versionamento, autorização e isolamento, avanço sequencial, corrida com revogação, timestamps crescentes e replay idempotente.
- `./dev matrix-refresh`: aprovado; 1.388 IDs únicos e 1.377 IDs numéricos estáveis reconciliados com `MATRIZ_IDS.csv`.
- `./dev verify-fast`: aprovado; NEXO Core 11/11, backend 47/47 (inclui os 4 testes de workflow), build de produção do frontend e matriz/IDs estáveis. A suíte E2E não faz parte deste comando.
- A primeira tentativa de `./dev evidence` falhou em um cenário e exportou zero imagens. O relatório JSON e os anexos foram movidos para a mesma raiz temporária privada; a nova execução completa e inspeção visual ainda estão pendentes.

## Limitações e risco de migração

- O backfill de nomes da V20 usa o nome do setor existente quando a migração roda. Se um setor já foi renomeado antes da V20, o nome histórico anterior não está disponível para reconstrução; o snapshot antigo reflete o nome atual no momento da migração.
- A V20 não reescreve timestamps de eventos existentes, que permanecem append-only. A trigger ordena inserções novas após o maior timestamp existente; qualquer inversão antiga continua nos dados históricos.
- A aplicação precisa publicar essas operações no OpenAPI e criar a interface do servidor antes de demonstrar configuração e tramitação em fluxo visual.
- Este incremento não valida impressão cronológica, exportação da árvore, workflow completo, condições/ramificações, notificações, prazos ou POC oficial.

## Harness e skill

- O `./dev process-workflow-test` centraliza a prova focal Testcontainers e está listado no `dev`, `README.md` e `AGENTS.md`.
- A skill `nexogov-vertical-slice` registra regras reutilizáveis para snapshots de workflow, lotação concorrente, ordenação de eventos, idempotência e limites de status.
- O harness de captura `./dev evidence` permanece separado: evidência visual só pode apontar a um par exato de spec/cenário e não promove status da matriz.
