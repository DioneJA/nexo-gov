# Incremento 32 — Interações em protocolos

## Objetivo e fonte

Implementar uma primeira fatia persistida de mensagens posteriores à abertura entre o requerente e setores que participaram do protocolo, com texto, anexos nos formatos configurados, visibilidade pública/interna, histórico e consulta nas áreas do cidadão e do servidor.

Fonte principal: Termo de Referência, §4 “PROTOCOLO ELETRÔNICO”, p. 50, requisito `TR91-0525`. A conclusão que bloqueia interação externa e a reabertura autorizada relacionam-se a Edital §14.1.13, fl. impressa 33 (`ED91-0483`) e TR §4, p. 49 (`TR91-0515`). Os anexos seguem as regras de formatos configuradas por assunto (`ED91-0484`/`TR91-0516`). As implementações e os critérios técnicos deste incremento são evidência parcial até a verificação completa e a POC apropriada.

## Escopo e limites

- O requerente atual interage com token de acompanhamento; o servidor autoriza as ações no backend por organização e setor envolvido.
- Mensagens do servidor podem ser públicas ou internas. O cidadão só recebe mensagens públicas e arquivos correspondentes.
- Concluir bloqueia novo envio externo; a reabertura exige servidor autorizado do setor responsável e registra a alteração.
- `TR91-0524` não está implementado integralmente: não há cadastro de participantes adicionais, credencial individual ou isolamento de acompanhamento por participante. O token existente representa somente o requerente.
- Resposta a e-mail (`ED91-0083`), autenticação verificada ICP-Brasil/Gov.br (`ED91-0486`), varredura antimalware, controles operacionais do bucket e a POC oficial permanecem fora desta evidência.

## Decisões técnicas

- Mensagens ficam em `protocol_interactions` com visibilidade explícita `PUBLIC` ou `INTERNAL`; a migração V13 inclui anexos ligados à interação e trilha append-only de downloads.
- A criação de interação e as transições que concluem/reabrem protocolo serializam na linha do protocolo. A autorização de servidor é derivada da organização validada e da lotação ativa no setor envolvido.
- O acompanhamento público devolve somente mensagens e arquivos públicos. Notas internas e autoria identificável não são expostas ao requerente; arquivos continuam saindo por endpoint autorizado, sem URL do storage.
- Extensões aceitas são derivadas das regras ativas do assunto no backend. O limite de quantidade/tamanho é aplicado antes de persistir os metadados; verificações finais dependem dos testes descritos abaixo.
- A lista administrativa “Anexos da abertura” contém apenas arquivos do pedido inicial; anexos de mensagens aparecem na conversa correspondente.
- Conclusão exige reabertura autorizada antes de nova mensagem. Os comportamentos e evidências desta fatia permanecem parciais em relação aos critérios integrais do edital.

## Arquivos e interfaces

- Persistência: `backend/src/main/resources/db/migration/V13__protocol_interactions_and_download_audit.sql` (interações/anexos/auditoria) e `V14__protocol_history_timestamp_order.sql` (usa clock_timestamp em eventos e interações para preservar a ordem efetiva após locks; timestamps históricos não são reescritos).
- API: `backend/src/main/java/br/com/nexo/gov/protocol/ProtocolInteractionService.java`, controladores e `contracts/openapi/nexogov-protocol-v1.yaml`.
- Contratos: `POST /api/v1/public/protocols/{id}/interactions`, `GET|POST /api/v1/staff/protocols/{id}/interactions` e `POST /api/v1/staff/protocols/{id}/reopen`; o acompanhamento do requerente inclui conversas públicas e formatos permitidos.
- Interface: `frontend/src/App.vue` (portal), `frontend/src/staff/StaffApp.vue` (caixa do servidor), `frontend/src/api/public.ts` e `frontend/src/api/staff.ts`.
- Testes: integração PostgreSQL em `ProtocolVerticalIntegrationTest`, E2E Playwright em `frontend/e2e/protocol-interactions.spec.ts`, executados pelo comando focado `./dev interaction-test`.

## Verificação

- `./dev interaction-test`: passou quatro métodos PostgreSQL Testcontainers/S3Mock — visibilidade/anexos e downloads por requerente/servidor, corrida envio/conclusão, ordem de gravação com transação antiga esperando pelo lock e atualização concorrente de política — e o cenário Playwright 1/1 em infraestrutura descartável.
- `./dev attachment-test`: resultado de uma execução anterior reportado pela revisão backend; não foi repetido nesta retomada e não é contado como prova atual do incremento.
- A primeira repetição de `./dev verify-fast` nesta retomada encontrou quatro testes com 403 e um teste de recibo sensível a espaços do nome do setor. A fixture do catálogo escolhia qualquer departamento ativo e um teste deixava um departamento legado em `ORG_ALPHA`; isso podia encaminhar um protocolo para setor sem vínculo do servidor de teste. O helper agora escolhe departamento ativo com membership explícita e a fixture temporária é removida em `finally`; o teste do recibo compara o nome conforme a normalização de exibição. A repetição passou: NEXO Core 11/11, backend 39/39, build Vue/Quasar e matriz estrutural (1.388 requisitos: 38 `EM_DESENVOLVIMENTO`, 8 `VALIDADO`, 1.342 `NAO_INICIADO`).
- A execução final de `./dev verify` nesta retomada passou com código de saída 0, incluindo Core/backend, build Vue/Quasar, matriz e os dois conjuntos E2E: Basic com 10 aprovados e 4 ignorados pelo modo de autenticação; OIDC com 7/7 aprovados. A falha anterior de `protocol-attachments.spec.ts` por rótulo desatualizado `Anexos (1)` não se repetiu. Nenhuma execução substitui a POC oficial ou a validação operacional dos serviços de produção.

## Débitos e riscos

- `TR91-0524` permanece incompleto: o token identifica o requerente atual; não há participantes adicionais com cadastro/identidade individual e autorização própria.
- Os uploads são materializados em memória como `byte[]`; os limites locais permitem até 10 arquivos por pedido e até 105 MB por requisição. Concorrência/limitação de taxa e pressão agregada de memória ainda precisam de tratamento antes de operação pública.
- PostgreSQL `now()` usa o início da transação e pode deixar a timeline fora da ordem após espera por lock; V14 usa `clock_timestamp()` para novos eventos/interações. Os timestamps históricos não são reescritos.
- Resposta por e-mail (`ED91-0083`), identidade verificada ICP-Brasil/Gov.br (`ED91-0486`), bucket produtivo privado, antimalware, política de backup/recuperação e POC oficial não são demonstrados aqui.
- Limpeza de objetos no storage depois de rollback da transação depende de limpeza best-effort; ainda não há reconciliação durável de objetos órfãos.
- `S3Mock`/Compose e testes locais não provam hospedagem, TLS, privacidade operacional, disponibilidade, SLA nem cumprimento integral do edital.

## Sincronia de skill, harness e documentação

- Adicionado `./dev interaction-test`, documentado em `README.md` e `AGENTS.md`; ele cobre testes de API/Testcontainers e E2E real em ambiente descartável. O harness já contemplava os cenários deste reparo; não houve necessidade de alterar o comando.
- A skill `nexogov-vertical-slice` registra as regras reutilizáveis para visibilidade, lock, arquivos e limites de evidência deste fluxo.
- A revisão anterior corrigiu o destino do link “Pular para o conteúdo”, limitou regiões vivas e tornou os formatos de anexos do servidor dependentes das regras ativas por assunto. O checklist visual não substitui validação com leitor de tela, viewport móvel, contraste, alto contraste ou tema escuro; não há Figma conectado, e esses pontos não são alegados como validados.
- A skill `nexogov-vertical-slice` registra anexos por política, locks compartilhados, visibilidade, limite de identidade do token, rota staff para anexos de mensagens públicas, timestamp de gravação para ordenação da timeline e a lacuna de capacidade de upload concorrente.
- O harness focado está documentado em `./dev`, README e AGENTS. A suíte repetível confirmou que fixtures em banco compartilhado devem escolher membership determinística e remover registros temporários em `finally`; essa regra foi acrescentada à skill.
