# Incremento 46 — autorização de leitura do detalhe de processo

## Alteração

O endpoint autenticado `GET /api/v1/staff/administrative-processes/{processId}` agora valida o tenant do contexto no servidor e permite a leitura somente a um administrador ativo da organização ou a um servidor com vínculo ativo no setor atualmente responsável, desde que esse setor também esteja ativo. Participação apenas em setor histórico não autoriza mais acesso ao detalhe. IDs inexistentes e IDs pertencentes a outra organização retornam a mesma resposta 404.

A leitura bloqueia o processo para todos os atores. Quando a autorização depende de lotação setorial, também bloqueia a membership e o setor atuais com locks compatíveis com o fluxo de avanço, evitando que a autorização e o setor atual sejam lidos em estados contraditórios. O ADMIN organizacional não precisa consultar ou bloquear uma lotação. A operação de leitura não escreve eventos nem altera processo.

## Evidências e verificações

- `./dev process-list-test` — passou (1/1): lista e detalhe, isolamento organizacional, lotação atual, busca literal e paginação.
- `./dev process-workflow-test` — passou (16/16): além dos casos acima, o novo teste mantém uma revogação da lotação sem commit, confirma via `pg_stat_activity` que o GET aguarda o `FOR SHARE OF sm`, libera a revogação e verifica 403 sem alteração de processo/eventos; ambas as operações terminam dentro dos limites do teste.
- `./dev openapi-check` — passou (2/2): contrato descreve 200/403/404 e o escopo de acesso.
- `git diff --check` — passou.
- Revisão independente somente leitura do incremento — sem achados P0/P1.

## Limites da prova concorrente

O teste determinístico cobre o interleaving em que a revogação obtém primeiro o lock exclusivo da lotação: o GET de detalhe espera no lock compartilhado, reavalia a lotação após o commit e nega acesso. Esse cenário termina sem deadlock e sem mutação do processo/eventos. Ainda não estão provados o interleaving inverso (GET obtém primeiro o lock e a revogação espera) nem uma corrida entre GET de detalhe e avanço do mesmo processo. A demonstração isolada desta regra também não tem screenshot mapeado; screenshots visuais não substituem os testes de autorização.

## Harness, skill e matriz

O harness existente `./dev process-list-test`, `./dev process-workflow-test` e `./dev openapi-check` já cobre as verificações introduzidas, portanto não foi necessário criar um novo comando. A regra reutilizável foi registrada na skill de fatia vertical junto ao incremento #47. Atualizei os geradores da matriz para refletir os 16 testes atuais e a nova prova de revogação concorrente; `./dev matrix-refresh` regenerou os artefatos e passou, mantendo 1.388 IDs, 45 `EM_DESENVOLVIMENTO`, 8 `VALIDADO` e 1.335 `NAO_INICIADO`. Nenhum status foi promovido. Neste complemento não foi alterado código de produção, contrato ou UI.
