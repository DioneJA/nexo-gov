# Incremento 23 — autorização administrativa e acessibilidade da lista de membros

## Entrega

Foram adicionados dois testes negativos de autorização à suíte de integração com PostgreSQL Testcontainers. Um ADMIN apenas setorial não consegue suspender o acesso a membro da organização (403); um administrador de Alfa não consegue suspender um membro vinculado somente a Beta (404). Ambos verificam que a tentativa não cria/alterada o estado de acesso nem grava auditoria e preserva o vínculo ativo.

Na lista de membros, suspender e reativar agora têm nomes acessíveis que incluem o nome da pessoa, usando o nome de usuário como alternativa. Falhas ao carregar a lista e falhas ao alterar acesso usam estados e mensagens separados. Depois de uma falha de mutação, a ação disponível é atualizar a lista; a mensagem genérica orienta a conferência antes de uma ação posterior e não instrui repetir a suspensão.

O backend de produção, o esquema e as migrações não precisaram de alteração: a auditoria anterior não encontrou bypass no fluxo. Os testes demonstram o comportamento de autorização existente contra API e banco PostgreSQL descartável.

## Rastreabilidade e estado

Os requisitos abaixo mantêm o status `EM_DESENVOLVIMENTO`, pois o incremento cobre somente parte das capacidades descritas no edital. A matriz e a POC foram atualizadas pelos geradores; seus IDs, transcrições e status foram preservados:

| ID | Origem | Evidência deste incremento | Limite mantido |
|---|---|---|---|
| `ED91-0042` | Edital, §14.1.13, fl. impressa 14, “Acesso interno à Plataforma” | ADMIN setorial recusado; alvo exclusivo de outro tenant não altera estado; ações da lista têm nomes acessíveis | Cadastro/provisionamento completo de contas e vínculos não implementado |
| `ED91-0046` | Edital, §14.1.13, fl. impressa 14, “Acesso interno à Plataforma” | Testes verificam recusa sem alteração/auditoria; suspensão preserva conta, vínculos e histórico | Ainda não há garantia contra exclusões por operações administrativas externas ao aplicativo |
| `TR91-0052` | Termo de Referência, §5.c, candidato OCR PT 52, PDF p.5, transcrição conferida visualmente | Testes PostgreSQL e fluxo administrativo; erro de mutação 409 isolado no teste de interface | Provisionamento de contas e vínculos ainda pendente |
| `TR91-0056` | Termo de Referência, §5.c, candidato OCR PT 56, PDF p.6, transcrição conferida visualmente | Recusas 403/404, preservação de dados e feedback de ação/lista | Não existe mecanismo global de imutabilidade contra ações fora da API do aplicativo |

O teste de interface injeta uma resposta HTTP 409 sintética para verificar o estado de erro genérico. Esse caso é somente uma simulação do frontend: não prova a causa ou semântica de um conflito real. As recusas 403/404, preservação de estado/auditoria, suspensão e reativação são exercitadas contra API e PostgreSQL Testcontainers reais. O fluxo E2E usa banco Compose descartável.

## Verificações

| Verificação | Resultado |
|---|---|
| `./dev admin-access-test` | Passou: 6 testes PostgreSQL Testcontainers, incluindo os dois casos negativos, sem falhas. |
| `./dev e2e` | 3 cenários passaram no Compose descartável. Inclui a verificação do 409 sintético e, em seguida, o fluxo real pela API para confirmar, suspender, verificar acesso recusado e reativar. |
| `./dev verify-fast` | Passou: NEXO Core 6/6; backend/Testcontainers 22/22; dependências frontend instaladas sem vulnerabilidades reportadas; `vue-tsc` e build Vite passaram; matriz conferida. |
| `./dev matrix-refresh` | Passou: 1.388 requisitos gerados; 742 linhas de TR, todas com transcrição visual conferida; nenhum pendente. |
| `./dev matrix` | Passou: 1.388 requisitos; `EM_DESENVOLVIMENTO=31`, `NAO_INICIADO=1353`, `VALIDADO=4`; 1.377 IDs numéricos estáveis. |
| `git diff --check` | Passou sem erros de whitespace. |

A matriz continua parcial: os quatro IDs deste incremento não foram marcados `VALIDADO` e nenhum resultado representa aprovação de POC ou aderência integral ao edital.

## Decisões de UI, skill e harness

O feedback de ação é separado do erro de carregamento da lista, e cada contexto oferece um comando com intenção explícita: “Atualizar lista de membros” após uma falha de mutação e “Tentar carregar membros novamente” após falha de carregamento. A operação de mutação não é repetida pelo botão de atualização. Os controles seguem o padrão Apple HIG-inspired e os tokens NEXO vigentes; ações repetidas incluem a pessoa no nome acessível. Não houve mudança de layout nesta fatia. A revisão foi estática e exercitada por seletores acessíveis no Playwright; não foi feita uma nova inspeção visual por screenshot, nem auditoria separada de contraste em temas alternativos.

A skill `nexogov-vertical-slice` registra as regras reutilizáveis para autorização negativa, ausência de estado/auditoria em recusas, nomes acessíveis e distinção entre falha simulada e falha coberta pelo servidor. `README.md` e a ajuda do harness descrevem os cenários de autorização de `./dev admin-access-test`. Não foi necessário criar um novo comando: `./dev admin-access-test`, `./dev e2e`, `./dev verify-fast` e `./dev matrix` já executam as provas necessárias; `./dev matrix-refresh` permanece o gerador dos artefatos derivados.

Não foi criado commit, publicado artefato ou executado deploy. Todos os testes com banco usaram recursos descartáveis; o banco local persistente não foi iniciado nem limpo.
