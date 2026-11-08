# Incremento 36 — convites individuais, privacidade de tela e troca segura de credencial

**Execução:** 07/11/2026
**Escopo:** concluir a fatia técnica de participantes do protocolo, corrigir o vazamento de convites antigos no estado do portal e comprovar a revogação individual. Nenhuma alteração foi feita no NexoLar.

## Fonte e critério

- `TR91-0524`: Termo de Referência, §4, PDF p. 50, página conferida visualmente. Exige dados próprios de identificação para pessoas vinculadas e acompanhamento/interação pela Central.
- `ED91-0488`: Edital, §14.1.13, folha impressa/PDF p. 33, requisito combinado de interações do requerente, pessoas vinculadas e setores, com texto e anexos.
- A Errata I, §1, p. 1, fixa a POC histórica em 90 minutos, prorrogável uma vez. Nenhum ensaio local é resultado de POC oficial; o certame de 2025 já foi adjudicado e homologado.
- O edital não determina quantidade de pessoas, tipos de campo nem autenticação específica. O limite de 20, a validação de campo e o convite por token são decisões de produto e segurança, não transcrições do edital.

## Implementação

- O backend valida definições e respostas por organização/assunto antes de reservar numeração, grava snapshots e valores ligados ao protocolo, gera um token aleatório independente por pessoa e persiste somente seu hash SHA-256. O requerente pode listar/revogar cada convite; a pessoa vinculada só vê seus valores e não enumera outros participantes.
- Consultas, interações e downloads revalidam o token no contexto do protocolo. A integração nega uma chave de pessoa usada em outro protocolo ou em outra organização. A migração V18 limita o tamanho dos valores armazenados.
- A listagem de interações do servidor seleciona o `participant_id` usado pelo mapeador de linhas, evitando resposta 500 ao apresentar a autoria anonimizada. A integração também compara contagens de interações e eventos antes/depois de tentativas com token de participante em outro protocolo e outra organização.
- O portal apresenta links para cópia manual uma única vez, usa fragmento da URL para transportar a chave e a remove do histórico antes da primeira consulta. Convites e recibo ficam associados ao token do requerente e ao ID do protocolo; mudar a chave apaga o estado e oculta os controles anteriores.
- O E2E usa contextos de navegador independentes para requerente e cada pessoa vinculada, verifica fragmento e armazenamento limpos, acompanha/interage com valores isolados, revoga uma chave, mantém a outra ativa e muda a credencial para confirmar que convites e comprovante antigos não reaparecem. Também atrasa deliberadamente a consulta da credencial A, resolve a B primeiro e confirma que a resposta antiga não substitui a tela atual.
- A interface agora informa, antes da coleta de dados de terceiros, a finalidade operacional, acesso por servidores autorizados, risco do link bearer e referência à política de privacidade do órgão. O nome exibido não é apresentado como verificação de identidade.

## Rastreabilidade, harness e skills

- `TR91-0524` e `ED91-0488` permanecem `EM_DESENVOLVIMENTO`; nenhum requisito foi elevado a `VALIDADO` por esta fatia parcial.
- O `./dev participant-access-e2e` foi documentado no harness, no `README.md` e em `AGENTS.md`, com as novas verificações de contexto independente e estado de credencial. O `./dev participant-access-test` comprova rejeição antes da numeração, hash, escopo e revogação em PostgreSQL Testcontainers.
- As skills `nexogov-vertical-slice` e `nexogov-design-system` registram as regras reutilizáveis para atrelar convites e estado visual ao protocolo/credencial, limpar dados ao trocar chave, invalidar respostas assíncronas antigas, testar contextos independentes, não persistir tokens, explicar risco do link bearer e tratar base legal sem pressupor consentimento.
- `./dev matrix-refresh` regenera os arquivos derivados depois deste registro; a matriz continua como fonte de status e evidência.
- Revisão visual aplicada ao fluxo alterado: hierarquia existente, tipografia de sistema, paleta azul/navy e espaçamento continuam nos tokens NEXO; o formulário usa `fieldset`/`legend`, campos rotulados, diálogo de revogação e regras responsivas já existentes. O foco visual desta alteração foi privacidade e texto, sem mudança de composição. O checklist local foi aplicado ao código; não há arquivo Figma/Dev Mode conectado para cotejo visual nem houve ensaio manual em alto contraste/leitor de tela nesta fatia.

## Verificações

- `./dev participant-access-test`: passou, **1 teste**, incluindo rejeição de acesso entre protocolos/organizações, Testcontainers PostgreSQL e 18 migrações em banco descartável.
- `./dev participant-access-e2e`: passou, **1/1 cenário**, API real e Compose descartável; o cenário cobre dois convites, sessões independentes, limpeza de URL/armazenamento, mensagens, revogação seletiva, troca de credencial e corrida de consultas.
- `./dev matrix-refresh` e `./dev matrix`: passaram; a matriz conserva 1.388 requisitos, com 39 `EM_DESENVOLVIMENTO`, 8 `VALIDADO` em escopo restrito e 1.341 `NAO_INICIADO`. Nenhum status foi promovido por esta fatia.
- `./dev verify-fast`: passou; NEXO Core **11/11**, backend **43/43**, compilação de produção do frontend e verificação da matriz concluídas com sucesso. A primeira execução expôs um `participant_id` ausente na projeção da listagem de servidor; após incluir o campo, a suíte ampla passou.
- `./dev openapi-check`: passou, **1/1 teste**. `./dev matrix`: passou com 1.388 IDs estáveis e sem divergências. `git diff --check`: passou sem erros de whitespace.

## Limites e riscos pendentes

- Os convites são links bearer compartilhados manualmente, sem expiração ou reenvio automatizado. O hash protege a credencial no banco, mas os valores de identificação ficam em texto; criptografia em repouso, backups, retenção/eliminação, base legal municipal, canal de direitos e política aprovada não foram comprovados.
- O nome informado pelo requerente e a posse do link não validam identidade civil. Gov.br/ICP-Brasil, entrega segura e POC oficial continuam pendentes.
- A prova atual verifica rejeição `404` e ausência de alteração em interações/eventos quando uma credencial de participante é usada em outro protocolo ou organização; também cobre leitura/interação e revogação sequencial. Ainda não cobre download de anexos com token de participante, download cross-tenant, nem concorrência entre revogação e requisição/download.
- A auditoria de download registra todas as pessoas vinculadas com o ator genérico `PARTICIPANT`; não permite identificar qual convite acessou o arquivo. A identificação do participante na trilha e testes de download/revogação concorrentes continuam pendentes.
- O nome e os valores de identificação de participantes ficam em texto no banco; ambos precisam de controles documentados de retenção, acesso, criptografia em repouso e backups antes de dados reais.
- `GET` de acompanhamento usa bloqueio exclusivo na linha do protocolo para serializar revogação; leituras concorrentes podem gerar contenção. A estratégia deve ser reavaliada com teste concorrente/carga antes de produção.
- O `S3Mock`, Testcontainers e navegador local não comprovam armazenamento, operação, segurança em produção, hospedagem, SLA ou POC da comissão. O NexoGov não participou do certame.

## Arquivos principais

- Backend: `ProtocolParticipantService.java`, `ProtocolPublicAccessService.java`, `ProtocolService.java`, `ProtocolInteractionService.java`, `ProtocolAttachmentService.java`, `V18__bound_protocol_participant_identity_values.sql` e `ProtocolVerticalIntegrationTest.java`.
- Interface/prova: `frontend/src/App.vue`, `frontend/e2e/protocol-participant-access.spec.ts` e `frontend/src/style.css`.
- Contrato e rastreabilidade: `contracts/openapi/nexogov-protocol-v1.yaml`, scripts da matriz e artefatos em `docs/licitacoes/mata-de-sao-joao-91-2025/`.
- Harness e manutenção: `dev`, `README.md`, `AGENTS.md` e `.agents/skills/nexogov-vertical-slice/SKILL.md`.
