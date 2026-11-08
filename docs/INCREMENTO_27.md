# Incremento 27 — configuração do destino inicial por assunto

## Entrega

- A administração de assuntos agora configura um setor destinatário inicial usando a lista de setores ativos retornada pelo endpoint autenticado e escopado à organização selecionada.
- Criação e edição mantêm rascunhos separados dos dados salvos. A mudança de organização limpa estado de catálogo/destinos associado ao contexto anterior.
- A interface informa carregamento, lista vazia e falha recuperável com ação de recarga. Assunto de balcão, assunto online com destino e assunto online sem destino têm estados distintos; no último caso, a interface informa que a abertura pública fica bloqueada até configurar um setor ativo.
- O teste Playwright cria um assunto online, altera o destino para “Secretaria de Obras”, cria um assunto de balcão, confirma a disponibilidade pública correta, abre um protocolo como cidadão pela API real e lê o PDF real do recibo para conferir o setor destinatário.
- O locator estrito ambíguo em `protocol-flow.spec.ts` agora delimita o protocolo pelo artigo acessível que contém o número, antes de buscar o assunto.

## Escopo e rastreabilidade

- Não houve alteração na matriz, nos geradores, no CSV de requisitos, na POC, em `REQUISITOS.md` ou `RISCOS.md`. O incremento prova configuração da interface, visibilidade pública do catálogo e presença do destino inicial no recibo; não comprova os demais requisitos do processo nem tramitação/resposta completa. Os status da matriz permanecem inalterados.
- Nenhum código de backend foi alterado nesta fatia. Os endpoints existentes já aceitam o destino no catálogo e fornecem setores ativos; as regras de validação e emissão do recibo são cobertas separadamente pelos testes de backend e permanecem autoritativas.
- Não foi necessário alterar o harness: `./dev protocol-routing-test` já oferece a prova focada com PostgreSQL/Testcontainers, e `./dev e2e` já executa Playwright contra o API real e banco descartável por execução. Não houve acesso ao banco local persistente, publicação ou commit.

## Revisão visual e acessibilidade

- A configuração foi adicionada à área existente de organização, sem introduzir nova navegação nem uma segunda linguagem visual. Foram usados controles Quasar e tokens NEXO para espaçamento, estados e responsividade; os textos dos campos, ações e status têm nomes acessíveis.
- A apresentação é uma lista administrativa em linhas, com indicação semântica e textual para destino não configurado; em telas estreitas os controles passam a uma coluna e a ação ocupa a largura disponível.
- Revisão contra `DESIGN.md`, `nexogov-design-system`, `frontend-design` e `frontend-design-review/references/quick-checklist.md`: hierarquia e componentes existentes foram preservados, os estados cobertos no código são explícitos e a seleção de destino permanece operável por controle de formulário padrão. Figma Dev Mode não está disponível neste checkout; modos escuro/alto contraste e leitor de tela não foram ensaiados. O E2E usa seletores acessíveis, mas não substitui ensaio manual de teclado/leitor de tela. As falhas de carregamento têm caminho de recuperação implementado, porém o E2E feliz não força uma falha de rede para validar visualmente esse estado.

## Arquivos desta fatia

- `frontend/src/staff/StaffApp.vue` — seleção de destino, estado e operações de criação/edição no catálogo.
- `frontend/src/api.ts` — tipos para os campos opcionais do destino retornados no contexto administrativo.
- `frontend/src/style.css` — linhas do catálogo, estado de destino pendente e adaptação responsiva com tokens NEXO.
- `frontend/e2e/protocol-subject-catalog.spec.ts` — fluxo real de configuração e verificação textual do PDF gerado.
- `frontend/e2e/protocol-flow.spec.ts` — escopo acessível estável para localizar o protocolo e o assunto.
- `frontend/package.json`, `frontend/package-lock.json` — `pdfjs-dist` para extrair texto do recibo real no E2E.
- `.agents/skills/nexogov-vertical-slice/SKILL.md` — orientação reutilizável para estados e seleção de destino no catálogo.

## Verificações

| Verificação | Resultado |
|---|---|
| Build do frontend (`npm run build`) | Passou após a implementação da interface. |
| `./dev protocol-routing-test` | Passou, 1/1; Testcontainers com PostgreSQL 16.4, conforme execução confirmada durante esta fatia. |
| `./dev e2e` | Passou, 5/5 em 27,2 s: limpeza de sessão, listagem de membros, catálogo/destino/recibo e fluxo de protocolo. A extração do PDF terminou sem avisos de fontes. |
| `git diff --check` | Passou sem erros nas alterações rastreadas; arquivos novos também conferidos sem espaços em branco finais. |

Sem evidência de conformidade integral com o Pregão 91/2025.
