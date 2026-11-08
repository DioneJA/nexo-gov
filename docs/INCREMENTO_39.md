# Incremento 39 — concorrência e autorização de workflow

**Data:** 08/11/2026
**Escopo:** fechar condições de corrida entre autorização de lotação, ativação dos setores e escrita de processos administrativos. A V20 permanece inalterada; não foi necessária nova migração. Todas as verificações usam PostgreSQL Testcontainers descartável.

## Implementação

- A abertura de processo agora valida e bloqueia a lotação ativa e o setor inicial até persistir processo e evento. Se revogação de lotação ou desativação de setor confirmar primeiro, a abertura retorna 403 sem criar processo ou evento.
- O avanço primeiro bloqueia e revalida a lotação do ator na etapa atual. Só então confere avanço sequencial e consulta os setores da etapa atual e o destino numa leitura ordenada por UUID com `FOR SHARE`. Essa mesma leitura verifica se o destino está na etapa seguinte e ativo; os bloqueios são mantidos até o CAS e o evento confirmarem.
- Desativação confirmada antes do avanço no setor atual resulta em 403; desativação do destino resulta em 409. Ambos preservam estado e eventos. Ator sem lotação atual recebe 403 antes de consultar o estado do destino.
- O teste de ordem de locks agora mantém bloqueada uma linha de membership comum até comprovar por `pg_stat_activity` que dois avanços de processos distintos estão efetivamente esperando; depois libera a linha e confirma ambas as transições sem deadlock.
- A skill `nexogov-vertical-slice` recebeu a ordem dos bloqueios, semântica de erros e casos de concorrência reutilizáveis. A descrição de `./dev process-workflow-test` foi ampliada para refletir essas provas.

## Rastreabilidade e limites

- `ED91-0596` e `TR91-0691` permanecem `EM_DESENVOLVIMENTO`; este incremento adiciona evidência de consistência concorrente ao slice de API. Matriz, resumo e POC não foram editados nesta rodada, pois a coordenação está consolidando a contagem final.
- O teste prova as escritas API de workflow/processo contra PostgreSQL, incluindo as corridas explícitas descritas acima. Não prova UI, workflow configurável avançado, OpenAPI, notificações, exportação ou a POC oficial. Não promover o requisito a `VALIDADO` com base nestes testes.
- Os eventos existentes continuam append-only; as observações de backfill e ordenação histórica da V20 permanecem documentadas no incremento 38.

## Verificações

- `./dev process-workflow-test`: aprovado, 13 testes, 0 falhas/erros, PostgreSQL 16.4 via Testcontainers; inclui corridas de abertura por revogação/desativação, desativação de setor atual/destino, anti-oráculo de destino e contenção real de locks em dois processos.
- `git diff --check`: aprovado para os arquivos deste incremento.
- `./dev verify-fast`: aprovado pela coordenação depois das alterações finais; NEXO Core 11/11, backend 56/56 (inclui 13 testes de workflow), build de produção do frontend e matriz com 1.388 IDs íntegros. A suíte E2E não faz parte deste comando.

## Harness e skill

- `./dev process-workflow-test` permanece a única entrada necessária para executar a suite focada com PostgreSQL Testcontainers; a linha de ajuda foi atualizada para descrever a cobertura real.
- A skill foi atualizada com o padrão de bloqueio/ordem e regressões necessárias para futuras escritas de processo.
