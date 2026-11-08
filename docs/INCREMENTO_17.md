# Incremento 17 — interfaces separadas e organograma parcial

## Entrega

- Separadas as entradas Vue/Quasar do portal público (`/`) e da área autenticada do servidor (`/servidor/`). Cada uma tem seu próprio HTML e bundle; compartilham cliente de API, contratos e tokens. A tela pública não importa a interface interna, e o backend continua responsável por autorizar cada operação.
- Removido o monograma em bloco da marca e eliminados adornos sem função da interface pública. O wordmark tipográfico, as superfícies planas, divisores úteis, hierarquia direta e cores NEXO comedidas seguem a referência Apple HIG sem copiar ativos, fontes ou chrome proprietários.
- Implementada hierarquia pai/filho de setores com migração Flyway V4, validação do pai na mesma organização, endpoint de organograma e renderização em árvore com lotações ativas. A montagem no backend é iterativa para não depender da recursão da pilha Java.
- Atualizados o OpenAPI, `DESIGN.md`, `.impeccable/design.json`, a skill de design do NexoGov, README, roteiro da POC e cinco linhas da matriz. As linhas continuam `EM_DESENVOLVIMENTO`.
- Instaladas as skills [Frontend Design](https://github.com/anthropics/skills/tree/main/skills/frontend-design) (Apache-2.0) e [Frontend Design Review](https://github.com/microsoft/skills/tree/main/.github/skills/frontend-design-review) (MIT) no NexoGov e no NEXO Core, incluindo referências e arquivos de licença junto das skills. As instruções locais adaptam o uso ao sistema visual NEXO e à separação das interfaces.

## Verificação

- `./dev verify` — exit code 0; NEXO Core: **3 testes aprovados**; backend NexoGov: **10 testes aprovados**; build de produção aprovou as duas entradas independentes; matriz com **1.388 linhas** validada; Playwright/Chrome: **1 teste aprovado**.
- O E2E abre um protocolo pelo portal, acessa a área do servidor, cria setor raiz e subsetor, troca de organização para conferir isolamento, tramita e conclui o protocolo e consulta o resultado como cidadão.
- O teste `departmentHierarchyIsTenantScopedAndHasNoConfiguredDepthLimit` usou PostgreSQL Testcontainers: percorre 32 níveis, confirma uma lotação ativa, rejeita pai de outra organização e verifica que a árvore não vaza para outro tenant.
- `./dev matrix` — 19 requisitos `EM_DESENVOLVIMENTO`, 1.369 `NAO_INICIADO` e 0 `VALIDADO`; sem declaração de aderência integral.
- `git diff --check` — aprovado. Detector Impeccable — sem ocorrências nos cinco arquivos de interface avaliados. `http://localhost:9000/` e `/servidor/` — HTTP 200; as duas telas foram inspecionadas no navegador.

## Limites

- O requisito oficial de profundidade ilimitada não pode ser comprovado por um teste finito; a entrega demonstra 32 níveis e não define teto de profundidade na aplicação. Carga de produção não foi medida.
- Continuam pendentes importação do organograma municipal, cargos/posições, datas de vínculo e último acesso, presença online/offline, cadastro integral de servidores e demais requisitos relacionados. Por isso, os itens `ED91-0087`, `ED91-0103`, `ED91-0104`, `TR91-0111` e `TR91-0112` permanecem parciais.
- A tela de servidor não equivale a autenticação de produção: HTTP Basic e conta de demonstração pertencem somente ao perfil local. A POC executada é teste interno; não é demonstração oficial para a Prefeitura.
- Nenhum deploy, publicação, commit ou alteração no NexoLar foi feito.

## Arquivos principais

- Frontend: `frontend/src/App.vue`, `frontend/src/staff/StaffApp.vue`, `frontend/src/staff/main.ts`, `frontend/servidor/index.html`, `frontend/src/style.css`, `frontend/vite.config.ts` e `frontend/e2e/protocol-flow.spec.ts`.
- Backend: `backend/src/main/java/br/com/nexo/gov/organization/`, `backend/src/main/resources/db/migration/V4__department_hierarchy.sql`, `backend/src/test/java/br/com/nexo/gov/protocol/ProtocolVerticalIntegrationTest.java` e `contracts/openapi/nexogov-protocol-v1.yaml`.
- Rastreabilidade e manutenção: `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv`, `MATRIZ_ADERENCIA.md`, `POC.md`, `evidencias/POC-PROTOCOLO-E2E.md`, `README.md`, `DESIGN.md` e `.agents/skills/`.
