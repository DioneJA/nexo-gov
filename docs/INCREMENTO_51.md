# Incremento 51 — contatos da organização, evidência e sandbox manual

## Escopo entregue

Implementei a manutenção persistida de contatos por organização, restrita a ADMIN organizacional no backend. A fatia inclui três categorias citadas no Termo de Referência, busca paginada, inclusão, edição, arquivamento/reativação e importação/exportação CSV com validação UTF-8, limites, proteção de fórmulas, IDs para atualização por round-trip e transação atômica. Arquivamento e reativação usam atualização condicional tenant-scoped para que solicitações simultâneas da mesma transição emitam um único evento de auditoria. A interface do servidor usa uma área administrativa de largura completa em desktop. O contrato OpenAPI, o harness, a matriz, a documentação de teste local e as skills pertinentes foram atualizados.

Os seis critérios promovidos cobrem somente `ED91-0130/0131/0132` e `TR91-0135/0136/0137`. Permanecem pendentes o organograma importado, listas de contatos, vCard, deduplicação, integração com sistemas legados e quaisquer outros critérios do edital relacionados.

Criei `./dev sandbox`, `./dev sandbox-status` e `./dev sandbox-stop` para uma execução manual de dados fictícios, sem ler `.env` ou usar o volume persistente local. O sandbox foi iniciado, verificado e encerrado; o encerramento validou tempo de início, grupo e comando antes de sinalizar somente os processos próprios. A URL temporária é escolhida em cada inicialização e é impressa pelo comando. O Compose tem PostgreSQL e S3Mock próprios. As credenciais fictícias exibidas pelo comando são válidas somente nesse sandbox.

## Verificações

- `./dev verify`: aprovado depois das correções finais; NEXO Core **11/11**, API com Testcontainers **71/71**, build Vue/Quasar **92 módulos**, Playwright Basic **15/15** (4 cenários OIDC específicos ignorados nesse modo) e Playwright OIDC **7/7**. O npm audit reportou **0 vulnerabilidades**.
- `./dev contacts-test`: aprovado **5/5**, incluindo duas solicitações concorrentes de arquivamento, uma transição e um evento de auditoria.
- `./dev contacts-e2e`: aprovado **1/1** no layout final, exercitando a interface real e API/PostgreSQL/S3Mock descartáveis; inclui uma asserção de largura administrativa em desktop.
- `./dev evidence-export-test`: aprovado **14/14** verificações do exportador de evidências.
- `./dev evidence`: aprovado **9/9** cenários; exportou **25 PNGs** para `/home/exati/Documents/NexoGov-Evidencias/PE-91-2025/20261108T201628Z-2246952/`. O hash SHA-256 da matriz no manifesto corresponde à matriz atual; os três IDs `ED91` de contatos agora também registram `organization-contacts.spec.ts` no campo de teste e no manifesto.
- `./dev sandbox`, `./dev sandbox-status` e `./dev sandbox-stop`: iniciaram os serviços isolados, confirmaram `{"status":"UP"}` na API e encerraram o projeto próprio após validar a identidade dos processos. A área do servidor foi aberta no navegador interno e acessada com a conta sintética de ADMIN.
- `bash -n scripts/manual-sandbox.sh` e `git diff --check`: aprovados.

O manifesto associa capturas a **29 de 1.388** requisitos; **15** desses aparecem como `VALIDADO` na matriz. **1.339** linhas não têm cenário E2E matriciado e **5** não recebem mídia por privacidade. Capturas de contatos estão em `midia/organization-contacts--administrador-mantem-contatos-por-tipo-e-importa-exporta-csv--*.png`. As imagens documentam subfluxos de navegador, não substituem os testes de API, contrato ou segurança.

## Situação do edital e limites

Após `./dev matrix`, há **45 `EM_DESENVOLVIMENTO`**, **16 `VALIDADO` em escopo delimitado** e **1.327 `NAO_INICIADO`**, sem itens `BLOQUEADO`. **710** itens do Termo de Referência ainda usam critério genérico pendente de decomposição. A cobertura da fonte oficial não equivale à cobertura funcional. A POC do certame de 2025 não foi executada pelo NexoGov; o certame foi adjudicado a outro fornecedor. Não há afirmação de aderência integral.

Os serviços locais usam dados fictícios; S3Mock não demonstra operação ou segurança do bucket produtivo. Obrigações operacionais, empresariais, contratuais, implantação e POC ainda exigem evidências próprias. A lista de lacunas segue em `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.md` e `RISCOS.md`.

## Atualizações dos artefatos operacionais

- `README.md`, `AGENTS.md`, `docs/TESTAR_LOCAL.md` e `dev` explicam como iniciar e parar o sandbox sem tocar volumes persistentes.
- As skills `nexogov-design-system`, `nexogov-vertical-slice` e `nexogov-tender-traceability` registram regras reutilizáveis sobre layout administrativo, teste de largura e inspeção visual de evidências.
- `MATRIZ_ADERENCIA.csv`, seu relatório Markdown e `POC.md` foram regenerados; `REQUISITOS.md` e `RISCOS.md` refletem o novo status e seus limites.
- `PLANO_IMPLANTACAO.md` não mudou: esta fatia não alterou obrigações, etapas ou capacidade de implantação contratual. Essa fronteira permanece descrita nos riscos e requisitos ainda não implementados.
- O checklist de revisão visual de Figma não pôde ser executado porque não há referência Figma/Dev Mode configurada no projeto; a revisão seguiu `DESIGN.md`, tokens versionados do NEXO Core e as capturas Playwright.

## Próximas lacunas prioritárias

Decompor os critérios TR ainda genéricos e separar os requisitos adjacentes de listas, vCard, deduplicação e organograma antes de implementá-los. Para cada fatia, acrescentar autorização organizacional, persistência, teste, cenário POC e evidência verificável sem promover requisitos compostos parcialmente atendidos.
