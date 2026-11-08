# Incremento 53 — prazos múltiplos em processos administrativos

## Fonte e escopo

Implementa o requisito `TR91-0562`, Termo de Referência, §7 “PROCESSO ADMINISTRATIVO” (Anexo II), PDF p.53: “Haverá a possibilidade da inserção de múltiplos prazos dentro dos processos administrativos.” A página digitalizada foi conferida visualmente contra o PDF oficial; o parágrafo próximo sobre numeração automática/customizada não faz parte deste critério. `ED91-0511` permanece separado porque também inclui numeração de processos e não é satisfeito por esta entrega.

O servidor autorizado pode registrar vários prazos independentes por processo, cada um com título e vencimento. A organização vem do `TenantContext`; a autorização aceita ADMIN organizacional ativo ou membership ativa no setor atualmente responsável. A tabela `administrative_process_deadlines` mantém `created_by` e `created_at`, preserva cada inclusão sem edição/exclusão e vincula prazo e processo no mesmo tenant. O detalhe do processo apresenta a lista por vencimento, registro e identificador, com título, autor e data de inclusão.

O critério não acrescenta alertas, feriados/dias úteis, cálculo de prazo legal, edição/conclusão de prazos, SLA ou numeração de processos: a cláusula-fonte não os especifica. Não demonstra integração de calendário nem a POC oficial do certame.

## Arquivos e decisões

- Migração forward-only `V24__administrative_process_deadlines.sql`, com FK composta pelo tenant/processo, índice de consulta e proteção append-only contra `UPDATE`, `DELETE` e `TRUNCATE`.
- API `POST /api/v1/staff/administrative-processes/{processId}/deadlines`; request valida título não vazio (até 180 caracteres) e `OffsetDateTime` com offset explícito. Resposta inclui a visão atualizada do processo. OpenAPI foi atualizado.
- Área autenticada do servidor ganhou formulário de título/data-hora, feedback de sucesso/erro e lista persistida. Data/hora local é enviada como instante ISO-8601 com offset UTC; a exibição usa horário local do navegador.
- NEXO Gov passou a consumir o contrato independente NEXO Core `0.3.0`, que adiciona `TenantScopedExternalIdentityResolver`; contrato global anterior continua compatível.
- Harness: `./dev process-deadline-test` e `./dev process-deadline-e2e`. O cenário E2E cria seus dados fictícios por API, cadastra prazos via UI, recarrega, autentica novamente e busca o processo persistido.
- Skills vertical slice e traceabilidade, `AGENTS.md`, `README.md`, CSV/Markdown da matriz e roteiro POC foram sincronizados.

## Verificações

- `./dev process-deadline-test`: passou, 1 teste PostgreSQL 16.4/Testcontainers. Verifica migração, múltiplos registros, ordenação, autor/data, validação, imutabilidade, ADMIN, membro do setor atual, 403 sem gravação e 404 cross-tenant indistinguível de ID ausente.
- `./dev process-deadline-e2e`: passou, 1 cenário Playwright contra API/PostgreSQL/Chrome locais descartáveis. Criou dois prazos pela interface, conferiu ordenação/autoria, recarregou a página, fez nova autenticação e recuperou ambos.
- Build frontend (`npm run build`): passou.
- `./dev verify`: passou após corrigir um seletor antigo ambíguo do E2E de workflow; Core 14/14, backend/Testcontainers 72/72, build do frontend (92 módulos), matriz, E2E básico 16/16 e OIDC 7/7. Quatro cenários OIDC são excluídos da execução básica e passam na suíte OIDC separada. A primeira execução completa expôs a ambiguidade do seletor quando o processo fictício entrou na lista; `getByRole` agora exige o nome exato `Abrir processo`.
- `./dev evidence`: passou, 10 cenários aprovados e 26 capturas exportadas em `/home/exati/Documents/NexoGov-Evidencias/PE-91-2025/20261108T205307Z-2416011`. No instante capturado, o manifesto registrou 1.388 requisitos; screenshot mapeado para 30; 15 já `VALIDADO` com screenshot mapeado; 1.338 sem cenário E2E mapeado; 5 sem mídia por privacidade. Depois, `TR91-0562` foi validado separadamente após revisão da sua imagem; esses números delimitam evidência existente, não cobertura integral do edital.
- `./dev evidence-export-test`: 15/15 testes passaram. `./dev openapi-check`: 5/5 contratos passaram. `git diff --check`: sem erros de whitespace.
- Captura do requisito `TR91-0562`, inspecionada visualmente: `/home/exati/Documents/NexoGov-Evidencias/PE-91-2025/20261108T205307Z-2416011/midia/process-multiple-deadlines--process-multiple-deadlines-servidor-registra-e-consulta-multiplos-prazos-no-processo--processos-prazos-multiplos.png`; SHA-256 `031de444f33c05cc82ea17aa7edbd58044d06178cb7407033e832c4fb33e7b6b`. A tela mostra dois prazos persistidos e ordenados, autoria e datas. O manifesto lista os limites: imagem apenas da UI fictícia; segurança, validação, persistência e isolamento dependem também do Testcontainers.
- `./dev matrix-refresh && ./dev matrix`: passou depois de corrigir o gerador para que ele nunca promova status a `VALIDADO` apenas pela presença de um ID no mapa de implementação. A validação foi registrada explicitamente somente após teste, execução E2E e inspeção da captura; o refresh preserva o status auditado. Contagem atual: 1.388 requisitos, 17 `VALIDADO` em critérios delimitados, 45 `EM_DESENVOLVIMENTO`, 1.326 `NAO_INICIADO` e 709 critérios pendentes de decomposição observável.
- O sandbox manual permaneceu ativo em `http://127.0.0.1:39215`.

## Rastreabilidade e limites

`TR91-0562` fica `VALIDADO` somente para o critério observável de inserção múltipla, persistência, consulta ordenada, autoria, autorização e isolamento, sustentado por teste Testcontainers, E2E real e screenshot de viewport. Isso não declara aderência global, não cobre requisitos vizinhos, não substitui o relatório da comissão e não representa aprovação oficial. O procedimento oficial ocorreu em 2025 e o NexoGov não participou.

## Harness e skills

O harness contém comandos focados para repetir as verificações com Docker/Chrome. A skill `nexogov-vertical-slice` registra o padrão tenant-scoped append-only e autorização no setor corrente; `nexogov-tender-traceability` registra o recorte exato de `TR91-0562` e a distinção de evidência interna versus POC oficial. Não foi necessário criar uma nova abstração no NEXO Core para datas/processos de domínio.
