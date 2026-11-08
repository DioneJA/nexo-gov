# Incremento 33 — Downloads internos respeitam setor ativo

## Problema

Uma auditoria independente encontrou divergência entre listagem e download de anexos internos. A lista de interações exigia associação ativa a um setor ativo, mas a rota direta do anexo verificava apenas `staff_memberships.active`. Um servidor que ainda mantivesse essa associação poderia tentar baixar uma nota interna de setor desativado usando uma URL anterior.

## Alterações

- `ProtocolService.authorizeForAttachmentRead` agora exige setor ativo para a associação que concede leitura ao servidor; o bypass de ADMIN organizacional continua sujeito à associação organizacional ativa e não suspensa.
- A consulta de `ProtocolAttachmentService.download` também exige setor ativo no predicado que libera anexos `INTERNAL`, além de associação ativa ao setor autor da mensagem.
- O teste de integração existente simula desativação do setor após uma mensagem interna com anexo, tenta o endpoint e o caminho do serviço de armazenamento, confirma recusa sem nova trilha/auditoria e restaura o setor em `finally`.
- A fixture do cenário de escopo declara `active=true` explicitamente e confere a associação antes de abrir protocolo, tornando a falha diagnosticável.

## Matriz, harness e documentação

- A classificação/status de edital não mudou: a regressão não completa um critério contratual por si só.
- `./dev interaction-test` já seleciona a classe `ProtocolVerticalIntegrationTest` e o cenário real Playwright; não foi necessário criar um comando ou alterar o filtro do harness. `AGENTS.md` descreve agora a negativa do setor desativado.
- A skill `nexogov-vertical-slice` registra a regra reutilizável de autorização em ambos os caminhos e a asserção de ausência de auditoria.
- A matriz e a POC são regeneradas pelo harness e mantêm a evidência limitada; não equivalem à POC oficial.

## Verificação

- `./dev interaction-test`: passou os quatro métodos PostgreSQL Testcontainers/S3Mock selecionados e o cenário Playwright 1/1, incluindo a regressão de setor desativado.
- `./dev matrix-refresh`: regenerou os artefatos derivados; `./dev matrix` passou com 1.388 linhas, 38 `EM_DESENVOLVIMENTO`, 8 `VALIDADO` e 1.342 `NAO_INICIADO`.
- `./dev verify`: passou integralmente com código de saída 0, incluindo Core/backend, build Vue/Quasar, matriz, E2E Basic (10 aprovados, 4 ignorados pelo modo de autenticação) e E2E OIDC (7 aprovados).
- `git diff --check`: passou após a atualização dos registros. Nenhum commit ou deploy foi feito.

## Limites

O teste local usa PostgreSQL e S3Mock descartáveis. Não comprova armazenamento de produção, antimalware, backup/restauração, hospedagem, SLA nem conclusão de requisito do edital. Não foram alterados dados ou ambientes do NexoLar.
