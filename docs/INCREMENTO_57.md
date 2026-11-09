# Incremento 57 — histórico de acesso a documentos do protocolo

## Fonte e escopo

`TR91-0143` e `TR91-0144`, Termo de Referência §14 “Funcionalidade de quem visualizou”, PDF p.13; `TR91-0146`, `TR91-0147` e `TR91-0148`, §15 “Status de Leitura e Rastreabilidade dos Documentos”, PDF p.14. As cinco transcrições foram conferidas visualmente no PDF oficial.

Esta fatia registra leituras autorizadas do protocolo, anexos e mensagens/despachos e combina esses eventos com os downloads concluídos já auditados. O histórico mostra documento, ator/papel e horário na área do servidor; o total de downloads aparece na linha do anexo. Os cinco requisitos permanecem `EM_DESENVOLVIMENTO`: não se registram tentativas negadas/anônimas ou login do sistema, a credencial externa não prova identidade civil, o portal externo não consulta a trilha e documentos de outros módulos não são cobertos.

## Mudanças

- V28 cria `protocol_document_access_events`, escopada por organização e protocolo, com tipos de documento e ator, data/hora e imutabilidade lógica. A V2 foi preservada byte a byte; a reconciliação de índices existente continua nas migrations posteriores. Nenhuma migration foi aplicada ao banco local persistente.
- `ProtocolDocumentAccessService` grava leituras válidas e apresenta a cronologia combinada com downloads existentes. O serviço nunca persiste bearer ou credencial de requerente/participante; para atores externos, registra somente o papel e referência interna.
- `ProtocolService` registra acessos do servidor, requerente e participante após validação de escopo/credencial. `ProtocolInteractionService` registra a leitura da mensagem/anexo visível. A API de histórico exige a autorização do servidor para o protocolo e responde `private, no-store`.
- A área do servidor ganhou a seção **Acessos**, com lista cronológica, indicação de servidor/requerente/participante e horário; a lista de anexos exibe o total de downloads. O cenário E2E cobre upload público e download por requerente/servidor.
- Contrato OpenAPI, teste de contrato, integração Testcontainers, cenário Playwright, política de exportação, geradores da matriz, `README.md`, harness `./dev`, skills `nexogov-vertical-slice`/`nexogov-tender-traceability`, `REQUISITOS.md`, relatório e POC foram atualizados.
- O inventário dos requisitos TR é gerado por `scripts/extend-matrix-from-tr-ocr.py`; `scripts/build-edital-matrix.py` gera apenas o conjunto ED e deve continuar separado. A tabela de contagens em `REQUISITOS.md` é sincronizada manualmente e verificada por `./dev matrix`.

## Verificação

- `./dev document-access-test`: **1/1 passou** com PostgreSQL Testcontainers descartável. Verifica cronologia, papéis, ausência de bearer, isolamento cross-tenant e recusa de UPDATE/DELETE/TRUNCATE.
- `./dev attachment-test`: **1/1 passou** com Testcontainers e S3Mock. Verifica política, download autorizado e contagem concluída.
- `./dev openapi-check`: **8/8 passaram**.
- `./dev document-access-e2e`: **1/1 passou** com API, PostgreSQL e S3Mock descartáveis. Executa o fluxo de abertura, upload, consulta, download e histórico no navegador; as migrations foram aplicadas somente ao banco efêmero.
- `npm run build` no frontend: `vue-tsc` e Vite passaram.
- `./dev evidence-export-test`: **20 verificações passaram**.
- `./dev matrix-refresh` e `./dev matrix`: **passaram**, mantendo 1.388 IDs estáveis e status de desenvolvimento parcial para os cinco itens.
- `./dev verify-fast`: NEXO Core **14/14 testes**, backend **86/86 testes Testcontainers**, build dos portais e matriz passaram. Flyway aplicou V28 somente aos bancos efêmeros; o checksum da V2 permaneceu igual ao commit atual.
- `./dev evidence`: a primeira rodada passou 12/13; o cenário de workflow falhou ao preencher o nome da etapa. O relatório e 23 PNGs foram preservados em `~/Documents/NexoGov-Evidencias/PE-91-2025/20261109T003711Z-3410010`. `./dev process-workflow-e2e` passou isoladamente (1/1), e uma nova rodada completa passou **13/13**, exportando 36 PNGs para `~/Documents/NexoGov-Evidencias/PE-91-2025/20261109T004044Z-3428700`.

## Evidência e limites

O histórico é append-only e tenant-scoped para eventos gerados pela aplicação. O teste focal confirma isolamento e imutabilidade; o E2E confirma os estados visíveis com dados fictícios. A captura `historico-acesso-do-documento` foi inspecionada visualmente: mostra eventos de requerente/servidor, data/hora e downloads, sem tokens, credenciais ou QR. A rodada final associa capturas a 39 requisitos da matriz; 1.330 não têm cenário E2E matriciado e cinco não têm mídia por privacidade. S3Mock não demonstra operação/segurança do storage produtivo. O papel externo significa que uma credencial de protocolo foi apresentada com sucesso, não que a identidade civil foi verificada. O histórico não registra tentativas negadas, acesso anônimo ou login e não cobre todos os tipos documentais do edital. A matriz não marca nenhum dos cinco requisitos como validado, nem esta evidência local equivale à POC oficial.

## Integridade e estado operacional

V2 preservada com SHA-256 `336a23a34109be595edc1436671cbd00fc37bccf7834b2cbe9e9fb3778262852`. `./dev sandbox-status` confirmou o sandbox manual persistente ainda ativo em `http://127.0.0.1:39215/`; os testes não o acessaram nem modificaram. A implementação e as verificações usaram Testcontainers ou Compose de execução descartável. Não foram alterados NexoLar, NEXO Core, credenciais, segredos, buckets ou dados locais persistentes.
