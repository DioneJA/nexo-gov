# Incremento 58 — QR do recibo de protocolo

## Fonte e escopo

`TR91-0149`, Termo de Referência, §15 “Status de Leitura e Rastreabilidade dos Documentos”, PDF p.14: “Terá vinculação de QR-code a cada documento gerado, facilitando a rastreabilidade.” A transcrição foi conferida visualmente na página oficial. Esta fatia trata somente do QR já incluído no recibo do protocolo; não implementa nem comprova QR para todos os documentos, verificação de autenticidade ou assinatura digital.

## Mudanças

- O endpoint de recibo PDF agora responde `Cache-Control: private, no-store`, pois o QR contém credencial bearer para acompanhamento.
- O contrato OpenAPI descreve a credencial no QR, remoção da URL pelo portal, a política de cache e o limite da cobertura a recibos de protocolo.
- O teste Testcontainers baixa o PDF gerado, decodifica o QR com ZXing, verifica o destino do protocolo e o cabeçalho de cache.
- O E2E abre o destino em uma nova página, confirma o protocolo e assunto via API real e verifica que o fragmento com a credencial desaparece da URL antes da captura.
- A exportação permite o rótulo `protocolo-acessado-por-qr` apenas para `TR91-0149`. A captura mostra a tela depois da limpeza da URL; o PDF/QR não é capturado e o campo de credencial é mascarado.
- A matriz mantém `TR91-0149` como `EM_DESENVOLVIMENTO`. QR para outros documentos gerados, verificação de autenticidade/assinatura e POC oficial seguem pendentes.
- V2 permanece inalterada; nenhum migration foi criado ou aplicado ao sandbox manual persistente.

## Verificação

- `./dev protocol-qr-test`: **1/1 passou** em PostgreSQL Testcontainers; as 28 migrations foram aplicadas somente ao banco efêmero e o QR decodificado correspondeu ao destino do recibo.
- `./dev openapi-check`: **9/9 passaram**.
- `./dev protocol-qr-e2e`: **1/1 passou** contra API, PostgreSQL e S3Mock descartáveis.
- `./dev evidence-export-test`: **20 testes passaram** após a linha da matriz citar explicitamente a spec e o cenário do navegador.
- `./dev evidence`: **13/13 cenários passaram**, com 37 PNGs privados em `/home/exati/Documents/NexoGov-Evidencias/PE-91-2025/20261109T010614Z-3538439`. A captura QR inspecionada está em `midia/protocol-flow--cidadao-abre-protocolo-servidor-tramita-e-cidadao-acompanha-ate-a-conclusao--protocolo-acessado-por-qr.png`; mostra o acompanhamento do protocolo fictício e o campo de token mascarado. O manifesto registra SHA-256 `d5bb8e325e2ae76ea164e28d4bd91e7746f3bdf1f733d856d933e291dba3679b`.
- `./dev matrix-refresh` e `./dev matrix`: **passaram**; a matriz segue com 1.388 requisitos — 54 em desenvolvimento, 17 validados em escopo restrito e 1.317 não iniciados. Há 701 critérios ainda pendentes de decomposição observável.
- `./dev verify-fast`: **passou** — NEXO Core 14/14 testes, backend 87/87 testes Testcontainers, build dos dois bundles frontend e `./dev matrix`.

## Riscos e limites

O QR do recibo é uma credencial de posse: quem obtiver o PDF pode tentar consultar o protocolo. A credencial fica no fragmento, não é enviada no pedido inicial de navegação, e o portal remove a entrada do histórico antes da chamada de API; o PDF é `private, no-store`. Esta fatia não demonstra expiração/rotação da credencial do requerente, leitura por câmera/dispositivo, QR de outros tipos documentais, validação de assinatura nem operação de produção. A captura é evidência auxiliar de um estado fictício, não de conformidade integral ou POC oficial.

## Harness, skill e documentação

Atualizados `./dev protocol-qr-test`, `./dev protocol-qr-e2e`, `README.md`, as skills `nexogov-vertical-slice` e `nexogov-tender-traceability`, o gerador da matriz, `REQUISITOS.md`, `MATRIZ_ADERENCIA.csv`, `MATRIZ_ADERENCIA.md`, `POC.md` e `RISCOS.md`. A skill registra regras reutilizáveis para QR bearer, cabeçalho no-store, sanitização de URL, inspeção segura da evidência e status parcial por cobertura limitada.

Nenhum requisito externo foi marcado como integralmente atendido. A licitação está adjudicada a terceiro; ensaios locais do NexoGov não substituem uma POC oficial.
