# Incremento 22 — Catálogo de assuntos de protocolo

## Entrega

Foi adicionado um catálogo de assuntos por organização. Um ADMIN da organização pode criar assuntos e definir atendimento pela Central de Atendimento ou somente pelo balcão; o cidadão recebe apenas os assuntos online. A API valida organização, existência e disponibilidade do assunto antes de acessar o contador de numeração. Protocolos novos guardam a referência e uma cópia do nome do assunto. Protocolos legados continuam válidos com referência nula e título preservado.

A edição de assunto preserva o snapshot: renomear ou mudar sua disponibilidade não modifica o texto de protocolos já abertos. Listagem pública retorna somente assuntos online de organização ativa; criação e edição são autorizadas no backend, com trilha de auditoria e validação de ADMIN organizacional.

## Persistência e contratos

- `backend/src/main/resources/db/migration/V8__organization_protocol_subject_catalog.sql`: migração somente aditiva; não modifica V1–V7. Cria `protocol_subjects`, unicidade do nome normalizado por organização e vínculo composto entre organização e assunto. `protocols.protocol_subject_id` é anulável para compatibilidade legada.
- `GET /api/v1/public/organizations/{slug}/protocol-subjects` lista itens disponíveis online.
- `GET/POST /api/v1/staff/organization/protocol-subjects` e `PUT /api/v1/staff/organization/protocol-subjects/{id}` administram o catálogo com ADMIN da organização.
- `POST /api/v1/public/protocols` agora requer `subjectId`; IDs inexistentes, de outra organização ou presenciais são recusados antes de consumir numeração. A leitura compartilhada da linha impede uma alteração concorrente de disponibilidade durante a abertura.
- O contrato correspondente está em `contracts/openapi/nexogov-protocol-v1.yaml`.

## Matriz e POC

IDs preservados e status `EM_DESENVOLVIMENTO`:

- `ED91-0152` e `TR91-0157`: atendimento online por assunto configurado, com atendimento exclusivamente presencial omitido do portal. Critérios adicionais da Central ainda não implementados.
- `ED91-0487`: catálogo online/balcão parcialmente coberto. Georreferenciamento por mapa/marcador continua explicitamente pendente; não há teste ou evidência dessa parte.
- `TR91-0522`: assunto exclusivo de balcão não aparece na Central e é rejeitado pela abertura pública.

`./dev matrix-refresh` regenerou a matriz, os requisitos derivados e o roteiro POC; `./dev matrix` confirmou 1.388 requisitos: 31 `EM_DESENVOLVIMENTO`, 4 `VALIDADO` em escopo restrito e 1.353 `NAO_INICIADO`. O roteiro do assunto demonstra apenas o recorte implementado. Não representa aprovação nem execução da POC completa.

## Evidências de teste

| Verificação | Resultado |
|---|---|
| `./dev subject-catalog-test` | Testcontainers/PostgreSQL: 1 teste passou. Cobriu ADMIN, recusa de membro sem permissão, filtro público, assunto de balcão, ID de outra organização, ID desconhecido e `subjectId` ausente. Contador inalterado nas recusas e incremento único no protocolo válido; snapshots e protocolo legado verificados. |
| `npm --prefix frontend run build` | `vue-tsc` e build Vite passaram. |
| Primeira execução de `./dev e2e` | 1 passou, 2 falharam: o fluxo de protocolo achou mais de um botão “Encaminhar” porque testes paralelos criaram protocolos na mesma caixa; o teste novo achou dois textos iguais entre select e snapshot. A tela do servidor também emitiu alerta porque `QToggle` não estava registrado no entrypoint separado. |
| Correções após a primeira execução | Fluxo existente passou a restringir as ações ao artigo do protocolo aberto pelo próprio teste; locator de snapshot foi escopado; `QToggle` foi registrado no entrypoint de servidor e o teste passou a alternar balcão/online pela API real. |
| Segunda execução de `./dev e2e` | 3 testes passaram em 24,4 s, incluindo administração do catálogo, filtro público, abertura, recebimento de comprovante e fluxo geral de tramitação. Banco e portas foram isolados pelo harness descartável. |
| `./dev matrix` | Passou com 1.388 IDs, totais e registros estáveis. |
| Primeira execução de `./dev verify-fast` | Core 6/6 passou; a suíte backend teve 3 falhas. O cenário do catálogo havia escrito três eventos append-only na organização Alfa compartilhada pelos testes e fez uma asserção agregada falhar; as duas falhas subsequentes decorreram da limpeza que essa asserção impedia. |
| Correção da suíte | O caso do catálogo agora cria uma organização e vínculos temporários dedicados, sem alterar os dados de demonstração Alfa/Beta usados pelos demais cenários. |
| Segunda execução de `./dev verify-fast` | Passou integralmente: NEXO Core 6/6, backend/Testcontainers 20/20, `npm ci` sem vulnerabilidades reportadas, build Vite/TypeScript e verificação da matriz (1.388 requisitos; 1.377 IDs numéricos estáveis). |
| `git diff --check` | Passou sem whitespace errors. |

O teste focado usa PostgreSQL real via Testcontainers e a POC de interface usa a API/PostgreSQL locais ao Compose descartável; nenhum mock substitui persistência ou autorização nos cenários críticos.

## Decisões, arquivos e limites

Arquivos e áreas deste incremento:

- Backend: `ProtocolSubjectController.java`, `ProtocolSubjectDtos.java`, `ProtocolSubjectService.java`, `ProtocolDtos.java`, `ProtocolExceptionHandler.java`, `ProtocolService.java`, `LocalDemoData.java`, `ProtocolVerticalIntegrationTest.java` e migração V8.
- Interface/testes: `frontend/src/api.ts`, `frontend/src/App.vue`, `frontend/src/style.css`, `frontend/src/staff/StaffApp.vue`, `frontend/src/staff/main.ts`, `frontend/e2e/protocol-subject-catalog.spec.ts` e `frontend/e2e/protocol-flow.spec.ts`.
- Contrato/geradores: `contracts/openapi/nexogov-protocol-v1.yaml`, `scripts/build-edital-matrix.py`, `scripts/extend-matrix-from-tr-ocr.py` e `scripts/render-traceability.py`; artefatos derivados da licitação foram regenerados.
- Harness/documentação: `dev`, `README.md`, `AGENTS.md`, skills `nexogov-vertical-slice`, `nexogov-tender-traceability` e `nexogov-design-system`, além de `docs/licitacoes/mata-de-sao-joao-91-2025/RISCOS.md` e este registro.

Os assuntos de demonstração usam UUIDs fixos e `ON CONFLICT DO NOTHING` no seed local; não alteram identidade/senha do servidor de demonstração. O catálogo não atende aos requisitos restantes de tipos documentais, formulários/campos por assunto, anexos obrigatórios, mapas ou georreferenciamento. A edição de nome não altera processos antigos. Nenhum banco persistente local recebeu migração: a prova aplicou V1–V8 em bancos efêmeros Testcontainers/Compose. O ambiente persistente com checksum mismatch de V2 não foi corrigido, limpo nem alterado.

As skills foram atualizadas com as regras reutilizáveis de locking antes da numeração, referência composta por organização, snapshots/legados e limite de aderência geográfica. O harness expõe `./dev subject-catalog-test` para repetir o cenário de segurança. O desenho mantém tokens NEXO existentes, rótulos de estado claros e o controle de disponibilidade separado do formulário. Checklist de design aplicado ao código e aos fluxos Playwright: estados acessíveis e navegação dos campos testada. Não havia uma especificação Figma/Dev Mode vinculada para comparação; sem desvio visual de tokens identificado.

Não houve commit, publicação ou deploy. As demais alterações preexistentes no checkout permanecem intactas.
