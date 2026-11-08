# Incremento 40 — autorização no setor atribuído do workflow

**Data:** 08/11/2026
**Escopo:** corrigir autorização de avanço para exigir membership ativa especificamente no setor atualmente atribuído ao processo e verificar esse setor ativo sob lock. Nenhuma migration foi necessária. Os testes PostgreSQL usam Testcontainers descartável.

## Implementação

- `advance` agora obtém `current_department_id` do processo bloqueado e exige membership ativa do ator nessa lotação exata, juntando a linha do setor e bloqueando membership com `FOR UPDATE` e setor com `FOR SHARE` até o commit.
- Uma lotação em outro setor configurado na mesma etapa não autoriza a tramitação. A validação ocorre antes da leitura do destino, preservando 403 para o ator sem permissão e sem revelar o estado do destino.
- A etapa de múltiplos setores tem teste em que o servidor possui membership somente no setor alternativo e recebe 403, sem alterar processo ou eventos.
- A corrida de desativação do setor atribuído agora usa uma etapa com outro setor ativo e membership do mesmo ator; mesmo com a lotação alternativa disponível, a desativação que confirma primeiro resulta em 403 sem mutação.
- A prova concorrente de memberships foi ajustada para disputar deterministicamente a lotação realmente atribuída aos dois processos.

## Rastreabilidade e limites

- `ED91-0596` e `TR91-0691` permanecem `EM_DESENVOLVIMENTO`; esta correção fortalece a autorização do backend no fluxo parcial. Matriz e roteiros não foram editados nesta rodada.
- As alterações abrangem autorização e concorrência da API de workflow/processos; não demonstram UI, OpenAPI, notificações, condições avançadas, exportação ou POC oficial.

## Verificações

- `mvn -B -f backend/pom.xml -DskipTests test-compile`: aprovado; compilou produção e testes sem iniciar Docker/Testcontainers.
- `./dev process-workflow-test`: aprovado, **14 testes, 0 falhas/erros**, PostgreSQL 16.4 via Testcontainers descartável.
- `./dev verify-fast`: aprovado pelo agente coordenador após a correção — NEXO Core 11/11, backend 57/57, build frontend e verificação da matriz concluídos sem falhas. Esta execução é separada do `./dev evidence` e não incluiu a POC oficial.
- Revisão independente read-only: nenhum achado concreto no novo bloqueio por setor corrente e nos dois casos multi-setor.
- `git diff --check`: aprovado sem avisos nos arquivos rastreados; as verificações `--no-index --check` dos novos arquivos também não apontaram whitespace.

## Harness e skill

- O harness `./dev process-workflow-test` já executa a classe de integração que recebeu estes casos; a descrição de ajuda foi atualizada para destacar a autorização pela lotação efetivamente atribuída e as etapas multi-setor.
- A skill `nexogov-vertical-slice` registra que membership em setor alternativo da etapa não autoriza tramitação e exige testes de negação e desativação concorrente do setor corrente.
