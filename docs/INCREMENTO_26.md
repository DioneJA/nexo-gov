# Incremento 26 — limpeza do token público e saída do servidor

## Entrega

- O portal captura tokens não vazios enviados em `#token=...` e mantém compatibilidade com os links atuais de QR `#/acompanhar?token=...`. Antes de iniciar a consulta, substitui a entrada atual do histórico por caminho e query originais sem fragmento; preserva `history.state` e não registra o token em logs.
- A área do servidor oferece a ação **Sair** durante a sessão e enquanto uma carga inicial está em andamento. A saída limpa usuário, senha, organização selecionada, avisos e dados da organização em memória e retorna à identificação.
- Troca de organização e saída invalidam uma geração de contexto. Respostas e `finally` de carregamentos, catálogo, membros, presença e ações administrativas/protocolo só atualizam o estado se a geração e a organização continuarem atuais.
- O teste de corrida segura a resposta de listagem de membros na rede do navegador e, após a saída, libera a mesma chamada para a API real. Não fornece resposta falsa nem substitui a API.

## Rastreabilidade e limites

- Nenhuma linha, ID, critério, evidência ou status da matriz foi alterado. A fatia trata da limpeza local da sessão e da exposição de token em URL; não amplia nem demonstra integralmente um critério funcional do edital. Os status existentes permanecem inalterados.
- Nenhum endpoint ou contrato de backend foi modificado. A saída local não revoga uma identidade no provedor; isso continua dependendo do fluxo federado de autenticação, ainda pendente na interface.
- O harness não precisou de novo comando: `./dev e2e` já executa Playwright contra o API real em ambiente Compose descartável e agora inclui os casos de token e saída.

## Revisão visual e acessibilidade

- A ação usa um `QBtn` textual na barra superior existente, sem nova paleta, superfície ou navegação; mantém a hierarquia Apple HIG-inspired e os tokens/estilos NEXO já importados.
- O controle tem nome acessível explícito. Os testes verificam que a saída limpa os campos de usuário/senha e a seleção de organização e oculta a área de trabalho.
- A revisão foi feita contra `DESIGN.md`, a skill `nexogov-design-system` e o quick-checklist. Não há especificação Figma Dev Mode disponível neste checkout para conferir medidas; não foi introduzido componente visual novo além do botão Quasar existente. Tema escuro/alto contraste e leitor de tela não foram ensaiados nesta fatia.

## Arquivos

- `frontend/src/App.vue` — leitura e remoção antecipada do token no fragmento.
- `frontend/src/staff/StaffApp.vue` — saída, limpeza de contexto e proteção das respostas assíncronas.
- `frontend/e2e/session-cleanup.spec.ts` — regressões Playwright usando API real.
- `.agents/skills/nexogov-vertical-slice/SKILL.md` — regra reutilizável de saída e invalidação de contexto.

## Verificações

| Verificação | Resultado |
|---|---|
| `npm run build` | Passou; Vue/TypeScript e Vite produziram os dois bundles. |
| E2E focado (`session-cleanup.spec.ts`) | A primeira tentativa isolada criou infraestrutura descartável, mas parou antes do Playwright ao compilar o backend: `ProtocolService.java:110` chamava `ReceiptPdfService.create` com cinco argumentos, enquanto a assinatura em edição ainda recebia quatro. Não alterei arquivos de backend. |
| `ProtocolVerticalIntegrationTest` (execução do agente responsável pelo backend) | Passou 11/11 após os refinamentos de roteamento por assunto e destino ativo, conforme confirmação do agente. |
| `./dev e2e` (rodada final desta fatia) | 3/5 passaram; os dois testes de `session-cleanup.spec.ts` e o teste de membros organizacionais passaram. Falhou `protocol-flow.spec.ts:32` porque `getByText('Solicitação geral')` encontrou dois elementos na área do servidor (violação de strict mode). Falhou `protocol-subject-catalog.spec.ts:44` porque o assunto online criado sem setor destinatário ativo foi recusado pela API; o aviso é “O assunto está sem um setor destinatário ativo configurado”. A UI ainda não oferece configuração desse destino, pendência reservada para incremento separado. |
| `git diff --check` | Passou sem erros de whitespace. |

Sem alteração da matriz, migrações, banco persistente, configuração de produção, NexoLar, publicação ou commit.
