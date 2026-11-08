# Incremento 42 — estado de leitura na caixa do protocolo

**Data:** 08/11/2026
**Escopo:** persistir e exibir estado de leitura por protocolo, setor atualmente responsável e servidor autenticado. O recorte demonstra somente a caixa de protocolos; não equivale a leitura confirmada por documento individual.

## Fontes e limites do requisito

- `ED91-0120`: Edital PE 91/2025, fl. impressa 17, §14.1.13, subtítulo “Situação dos documentos e identificação visual”; par `TR91-0125`, Termo de Referência §10, PDF p.12.
- `ED91-0140`: Edital PE 91/2025, fl. impressa 18, §14.1.13, subtítulo “Status de leitura e rastreabilidade dos documentos”; par `TR91-0145`, Termo de Referência §15, PDF p.14.
- Os PDFs oficiais foram conferidos visualmente. A errata não substitui esses trechos.
- A matriz mantém os quatro IDs em `EM_DESENVOLVIMENTO`, módulo `PROTOCOLO`. O edital pede leitura por documento; esta versão registra leitura do conteúdo do protocolo para o setor corrente. Trilha nominal/cronológica por documento, acesso externo, contagem de downloads, outros módulos e POC oficial continuam pendentes.

## Implementação

- Flyway V21 cria recibos append-only com escopo de organização, protocolo, setor corrente e servidor, chave idempotente e vínculos tenant-aware.
- `ProtocolService` projeta os estados `UNREAD_BY_DEPARTMENT`, `READ_BY_OTHER` e `READ_BY_ME` sem mutação na caixa. O recibo só é criado após a interface abrir o conteúdo; o servidor exige membership ativa no setor atualmente atribuído, inclusive para ADMIN. Organização e autorização derivam de contexto validado no backend.
- Respostas da listagem e da gravação usam `Cache-Control: private, no-store`. A tela mostra texto sem expor os nomes dos leitores.
- A especificação OpenAPI descreve o endpoint e a semântica; os geradores da matriz registram critério parcial, teste, roteiro POC e limites por ID.

## Verificações

- `./dev protocol-read-status-test`: passou, 1/1 com PostgreSQL Testcontainers; verifica listagem sem escrita, estados, repetição idempotente, mudança de setor, ADMIN sem lotação, setor divergente e organização divergente sem recibo/mutação.
- Regressão `ProtocolVerticalIntegrationTest#otherTransitionBodiesAreOptionalAndDoNotRequireDestination`: passou, 1/1 após incluir o estado na projeção de protocolos.
- `./dev protocol-read-status-e2e`: passou, 1/1 contra API e banco descartáveis. Confirma zero `POST /read` na listagem/atualização e exatamente um recibo ao abrir o conteúdo; verifica resposta, leitura por colega, leitura própria e persistência após recarga.
- `./dev participant-fields-e2e`: passou, 1/1; confirma o layout em uma coluna no painel estreito.
- `npm run build` em `frontend/`: passou (`vue-tsc -b` e Vite).
- `./dev openapi-check`: passou, 1/1.
- `./dev evidence-export-test`: passou, 8/8.
- `./dev evidence`: passou, 6/6 cenários; exportou 10 PNGs. A captura contém os três estados de leitura e a versão atual do painel de campos. Os arquivos foram revisados visualmente; diretórios `0700`, arquivos `0600`, hashes/signaturas verificados; o token do recibo fica mascarado e PDFs/vídeo não são exportados.
- `./dev matrix`: passou para 1.388 IDs: 45 `EM_DESENVOLVIMENTO`, 8 `VALIDADO` em escopo literal limitado e 1.335 `NAO_INICIADO`. O refresh também regenerou `MATRIZ_ADERENCIA.md` e `POC.md` e as contagens em `REQUISITOS.md` foram reconciliadas.
- `./dev verify-fast`: passou; 11 testes do NEXO Core, 58 testes do backend (inclui Testcontainers), build do frontend e matriz.

## Capturas e rastreabilidade

Execução privada final (hash da matriz atual): `~/Documents/NexoGov-Evidencias/PE-91-2025/20261108T151454Z-751368/`.

- `leitura-nao-iniciada.png`: caixa sem recibo, antes de abrir mensagens.
- `leitura-por-colega.png`: a primeira pessoa da mesma lotação leu; a conta atual ainda não.
- `leitura-pelo-servidor.png`: conteúdo aberto pela conta atual; estado persiste após recarga.
- `protocol-participant-fields...configuracao-persistida.png`: layout atual em coluna estreita, sem associação a requisito, pois a matriz não relaciona esse fluxo administrativo a um ID correspondente.

O manifesto completo lista as 15 linhas da matriz associadas a 10 imagens, os 6 cenários aprovados, os limites por cena e as lacunas por ID. No conjunto inteiro, 1.349 requisitos ainda não têm cenário E2E matriciado e cinco não têm mídia por privacidade. Screenshots complementam os testes; não promovem status nem comprovam a POC do certame.

## Harness, skills e documentação

- `./dev protocol-read-status-test` e `./dev protocol-read-status-e2e` tornam as provas repetíveis.
- `nexogov-vertical-slice` agora registra as regras de recibo, tenant/setor, idempotência e testes negativos.
- `nexogov-tender-traceability` agora exige distinção explícita entre indicador por protocolo e leitura por documento.
- `AGENTS.md`, `README.md`, `REQUISITOS.md`, matriz, roteiro POC e este incremento foram alinhados.

## Riscos e próximos passos

- Implementar e testar leitura individual de documentos e despachos, trilha cronológica identificável e consulta de downloads conforme cada cláusula original, sem confundir esses IDs irmãos com o indicador da caixa.
- Determinar regras municipais para acesso externo, transparência e retenção antes de exibir identidades de leitura.
- O ensaio automatizado é parcial e não demonstra os 90 minutos, condições operacionais ou laudo da POC histórica. Os 1.388 requisitos seguem em acompanhamento; não se afirma aderência integral.
