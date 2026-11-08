# Incremento 44 — recuperação e listagem de workflows administrativos

## Escopo entregue

A tela autenticada `/servidor/` mantém a caixa de processos visível a servidores autorizados e deixa a edição/publicação de definições e a abertura de processos sob a capacidade administrativa organizacional já existente. A implementação usa o `staffApi` e o seletor de organização validado existentes; não cria outro caminho de autenticação.

A lista consome `GET /api/v1/staff/administrative-processes?page=0&size=25&q=...`, com paginação limitada a 25 itens por chamada e busca simples. Renderiza somente os campos fornecidos pela API; não cria número de processo fictício. Selecionar um resultado carrega o detalhe pelo ID com `GET /api/v1/staff/administrative-processes/{id}`. Depois de recarregar a página e autenticar novamente, o servidor consegue buscar e reabrir os processos persistidos.

Durante abertura, avanço ou atualização de detalhe, controles de assunto/contexto e de carregamento de workflow são desabilitados. A resposta confirmada da mutação é aplicada antes de iniciar a atualização independente da listagem, evitando perda de estado se a lista demorar ou falhar. Mensagens distinguem falha de consulta/listagem e falha de mutação. Se a etapa seguinte não tiver setor ativo, a interface explica que é necessário reativar um setor que já consta do snapshot; uma nova versão publicada vale para processos futuros. Ao chegar à última etapa, o texto deixa claro que isso não encerra o processo.

## Contrato e autorização

A listagem retorna `{items, page, size, hasNext}`; cada resumo inclui `id`, assunto, versão do workflow, título, etapa e setor atuais, estado e data de criação. Os resumos não incluem etapas nem eventos. O filtro textual é literal e case-insensitive. A API aplica isolamento de tenant e filtra itens por vínculo ativo no setor atual; a prova backend correspondente está no comando `./dev process-list-test`.

O limite histórico do GET de detalhe descrito na primeira versão deste registro foi corrigido no incremento #46: agora a leitura exige ADMIN organizacional ativo ou vínculo ativo no setor atual, com setor ativo. Consulta por vínculo somente histórico retorna 403; IDs de outro tenant e inexistentes retornam o mesmo 404. A prova atual está em `./dev process-list-test` e `./dev process-workflow-test`.

## Verificação executada

- `./dev process-workflow-e2e` — passou, 1 cenário Playwright contra API e serviços locais descartáveis. Cobre publicação v1/v2, preservação do snapshot v1, timeline, bloqueio observável dos controles enquanto chamadas reais estão pendentes, tentativa de pular etapa (409), recusa por falta de lotação (403), recarga, autenticação nova, busca/listagem e reabertura por detalhe autorizado.
- `npm run build` em `frontend/` — passou após integração da lista.
- O teste de backend `./dev process-list-test` foi executado pelo agente que implementou o endpoint e passou; essa prova cobre escopo tenant/setor, paginação, ordenação, busca, projeção resumida e detalhe autorizado para o caso permitido.
- Há uma mensagem não fatal do Chromium sobre `ResizeObserver loop completed with undelivered notifications` ao redimensionar viewport no cenário; não falhou o teste. O cenário também verifica responsividade pelo conteúdo.
- O E2E foi compilado/rodado com browser e API reais locais. O `route` de teste somente segura a requisição antes de `continue()`; não intercepta nem simula resposta.

## Evidência visual

A spec registra screenshots allowlisted, apenas de viewport e com dados fictícios locais:

- `processo-listado-apos-reinicio`
- `detalhe-reaberto-da-listagem`

Esses labels foram associados ao par de cenário e ao requisito ED91-0596/TR91-0691 no exportador e no teste adversarial da allowlist. Nenhuma captura de vídeo contínua foi habilitada. As capturas foram exportadas em `/home/exati/Documents/NexoGov-Evidencias/PE-91-2025/20261108T171019Z-1373500/midia/`; o manifesto registra seis imagens desta spec e seus limites. Todas as 19 imagens do pacote foram inspecionadas visualmente: nenhuma chave de comprovante, token de convite, credencial ou dado real aparece; as fixtures são fictícias. As capturas provam somente o estado visual local demonstrado, não requisitos de software além do conteúdo visível, nem operação contratual ou POC oficial.

## Design e manutenção

A tela usa os tokens existentes do NEXO Core e os padrões de espaçamento, tipografia, bordas e mensagens de estado de `DESIGN.md`; não introduz paleta ou marca da Apple. A checklist visual local foi aplicada ao estado da interface. Não havia integração/ponte Figma disponível para validar uma referência externa; essa etapa de comparação visual não foi executada. Não foi necessário alterar `frontend/playwright.evidence.config.ts`, pois o cenário já está allowlisted e a política de labels exatos é mantida no exportador.

O harness e a documentação agora distinguem `process-list-test` (prova backend de autorização/paginação) de `process-workflow-e2e` (interface e recuperação). A skill `nexogov-vertical-slice` registra regras reutilizáveis sobre resumo versus detalhe, controle de concorrência na interface, lotação inativa e última etapa. Para vincular as capturas só a requisitos com referência explícita, atualizei os dois geradores de ED91-0596/TR91-0691 com a spec, a recuperação/listagem e os seis labels demonstráveis. `./dev matrix-refresh` regenerou CSV, Markdown, requisitos e roteiro POC sem promover status; `./dev matrix` confirmou 1.388 IDs, 45 `EM_DESENVOLVIMENTO`, 8 `VALIDADO` e 1.335 `NAO_INICIADO`.

## Ainda pendente

- Alinhar a autorização do GET de detalhe com a regra de setor atual, com testes negativos e de não mutação no backend.
- Cobrir visualmente uma definição cuja próxima etapa tenha todos os setores inativos; a mensagem está implementada, mas o E2E deste incremento não desativa um setor para demonstrar esse estado.
- Requisitos avançados de workflow, encerramento/reabertura, notificações, exportação e evidências de POC continuam fora deste incremento.
- Os controles desabilitados para troca de organização, recarga, saída e navegação durante operações pendentes foram concluídos no #48. A decisão “Deferido/Indeferido” configurável por setor continua sem implementação e tem sua próxima fatia preparada em `ED91-0510`/`TR91-0560`. Nenhum status oficial de requisito é promovido por imagens.
