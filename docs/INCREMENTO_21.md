# Incremento 21 — interface de membros e acesso organizacional

## Entrega

- A área autenticada do servidor agora lista os membros retornados por `GET /api/v1/staff/organization/members`, mostrando nome, usuário, cargo e papéis organizacionais e de setor em linhas separadas. A lista só é carregada após a capability administrativa retornada pelo backend; ela não concede acesso e todas as ações continuam protegidas pelo servidor.
- A troca de organização limpa imediatamente a lista, estados e ações pendentes. Requisições antigas não podem preencher a lista de uma organização que deixou de estar selecionada.
- A suspensão abre diálogo com o alcance e a preservação de dados descritos. A reativação usa `PUT /api/v1/staff/organization/members/{username}/access`; os estados e mensagens de sucesso vêm da resposta real da API. Falhas e conflitos são apresentados ao administrador.
- A interface não oferece suspender o último administrador ativo conforme os dados visíveis; isso é somente uma barreira de usabilidade. A API continua sendo a autoridade e pode recusar a operação com conflito. Se a API aceitar a suspensão do próprio usuário, a área local é encerrada e a senha é removida da tela.
- Foram acrescentados estados de carregamento, lista vazia, erro com tentativa novamente e sucesso/erro, além de controles rotulados, foco visível, confirmação cancelável por teclado, lista semântica e disposição responsiva.

## Rastreabilidade

Atualizados os geradores para `ED91-0042`, `ED91-0046`, `TR91-0052` e `TR91-0056`, preservando IDs, fontes e status `EM_DESENVOLVIMENTO`. Os critérios agora distinguem interface, teste de UI real e prova backend; criação/provisionamento de contas, vínculo administrativo, autenticação federada integrada e outros limites permanecem pendentes. A matriz e a POC foram regeneradas por `./dev matrix-refresh`; nenhum requisito foi marcado `VALIDADO` por existir uma tela.

## Verificação

- `npm --prefix frontend run build`: passou; compila os bundles cidadão e servidor.
- `./dev e2e`: **2/2** cenários passaram em Chrome contra a API e PostgreSQL reais no Compose descartável. `organization-members.spec.ts` verifica papéis separados, confirmação/escape, suspensão, estado e feedback, recusa em uma nova autenticação do alvo e reativação; `protocol-flow.spec.ts` continua passando.
- `./dev admin-access-test`: **4/4** testes de integração passaram com PostgreSQL Testcontainers, incluindo autorização, isolamento, preservação, revogação, auditoria e proteção concorrente do último administrador.
- `./dev verify-fast`: passou; NEXO Core **6/6**, backend **19/19**, build das duas entradas e verificação da matriz com **1.388** linhas.
- `./dev matrix-refresh`: passou com **1.388** linhas, **27** `EM_DESENVOLVIMENTO`, **1.357** `NAO_INICIADO`, **4** `VALIDADO`, **742** requisitos TR e **1.377** IDs numéricos estáveis. Nenhum status foi elevado neste incremento.
- `./dev matrix`: passou dentro de `verify-fast`, sem alterar os artefatos após a geração.
- `git diff --check`: passou após as alterações do incremento.

## Revisão visual e acessibilidade

Aplicados `DESIGN.md`, `nexogov-design-system`, `frontend-design` e o quick checklist. A tela usa hierarquia administrativa simples, alinhamento e espaçamento do NEXO Core, azul/navy e controles já presentes na área do servidor; as novas cores, espaçamentos e raios usam tokens versionados. Não foram adicionados ícones decorativos, cartões repetidos, métricas fictícias ou fonte proprietária. A lista usa nomes textuais de status e papéis; o diálogo tem nome e descrição, e `Escape` fecha a confirmação sem realizar a ação. A disposição empilha as ações em telas estreitas.

Desvio registrado: não havia especificação de componente em Figma/Dev Mode disponível para esta tela. O estado escuro/alto contraste não foi validado visualmente de forma independente; o bundle Core atualmente declara `color-scheme: light`. O build e o fluxo de teclado do diálogo passaram, mas não constituem uma auditoria de acessibilidade independente.

## Harness e skill

A skill vertical foi atualizada com regras reutilizáveis para escopo da lista, separação dos papéis, proteção do último administrador, tratamento da resposta autoritativa do backend e encerramento da própria área em caso de suspensão aceita. O `./dev e2e` já executava todos os arquivos Playwright e por isso não precisou de novo comando; sua ajuda e o README agora explicam o membro fictício configurado por `NEXOGOV_DEMO_MEMBER_USERNAME`, que usa senha local somente em `.env`, e que a execução E2E usa credenciais isoladas do runner sem ler esse arquivo.

## Limites

- Esta tela não cria contas, não altera vínculos, não oferece login OIDC e não substitui o diretório ou a gestão completa de servidores.
- A barreira de último administrador no cliente pode ficar desatualizada entre leituras; o `409` e demais respostas da API continuam apresentados e aplicados. O backend conserva a validação concorrente.
- O teste E2E usa somente organizações e membros fictícios do ambiente temporário. Não demonstra identidade municipal, dados reais, operação em produção, nem conformidade total do edital ou aceite da POC oficial.
- Nenhum commit, deploy, publicação ou alteração no NexoLar foi feito.

## Arquivos deste incremento

- `frontend/src/staff/StaffApp.vue`, `frontend/src/staff/main.ts`, `frontend/src/api.ts` e `frontend/src/style.css`.
- `frontend/e2e/organization-members.spec.ts`.
- `scripts/build-edital-matrix.py`, `scripts/extend-matrix-from-tr-ocr.py` e os artefatos gerados em `docs/licitacoes/mata-de-sao-joao-91-2025/`.
- `.agents/skills/nexogov-vertical-slice/SKILL.md`, `README.md` e `dev`.
