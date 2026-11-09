# Incremento 56 — recuperação do comprovante após falha de acompanhamento

## Fonte e escopo

`ED91-0481`, Edital PE 91/2025, §14.1.13, fl. impressa 33, subtítulo “PROTOCOLO ELETRÔNICO”; referência complementar: Termo de Referência §4, PDF pp.48–49. A cláusula exige a apresentação imediata de comprovante com código e setor destinatário, além de acesso setorial integral às movimentações e resposta direta ao requerente.

Esta fatia corrige apenas a disponibilidade imediata do comprovante após a criação confirmada: se o GET de acompanhamento falhar temporariamente, a confirmação não é contradita, o PDF continua disponível e o cidadão pode tentar carregar o andamento novamente sem submeter outra abertura. O requisito amplo permanece `EM_DESENVOLVIMENTO`; acesso setorial integral, resposta direta e POC oficial ainda não foram demonstrados.

## Mudanças

- `App.vue` mantém caminho, ID e chave do recibo emitidos pelo POST em estado efêmero próprio. A chave inserida precisa corresponder exatamente à chave de requerente recebida na criação; o recibo não depende do resultado do GET de acompanhamento e uma chave de participante não o habilita.
- Falha no primeiro carregamento após criação confirmada produz uma mensagem de recuperação coerente com o protocolo já registrado. A confirmação original permanece visível. A ação **Consultar** repete somente o GET.
- `protocol-flow.spec.ts` injeta falha de transporte somente no primeiro GET de acompanhamento. POST de abertura, download do PDF e consulta de recuperação continuam reais no Compose descartável.
- `./dev protocol-receipt-recovery-e2e` torna a regressão focal repetível. `README.md`, a skill `nexogov-vertical-slice` e o gerador da matriz registram as regras reutilizáveis e os limites da prova.
- Não houve alteração de banco, migration, segredo, V2, dados persistentes, repositório NexoLar ou NEXO Core.

## Verificação

- `./dev protocol-receipt-recovery-e2e`: **1/1 passou**. O E2E executou com PostgreSQL e S3Mock descartáveis; as 27 migrations foram aplicadas ao banco vazio efêmero e os serviços/volumes temporários foram removidos ao final.
- `npm run build`: `vue-tsc` e Vite passaram; 93 módulos foram transformados.
- `./dev verify-fast`: Core **14/14**, backend/Testcontainers **84/84**, build dos dois portais e matriz passaram. Flyway validou e aplicou as migrations somente em bancos Testcontainers vazios e descartáveis.
- `./dev matrix-refresh`: regenerou CSV, relatório e POC com 1.388 linhas; `ED91-0481` continua `EM_DESENVOLVIMENTO` e 707 critérios TR ainda aguardam decomposição observável.
- `./dev matrix`: passou; IDs numéricos e contagens conferidos.
- `./dev evidence-export-test`: **20 verificações passaram**, inclusive associação exclusiva do label da recuperação a `ED91-0481`.
- `./dev evidence`: a primeira rodada aprovou 12/13 e falhou no preenchimento de etapa do E2E de workflow; o relatório foi preservado. O cenário passou isoladamente (`./dev process-workflow-e2e`, 1/1) e a segunda rodada completa passou **13/13**, exportando 35 PNGs para `~/Documents/NexoGov-Evidencias/PE-91-2025/20261109T000047Z-3256119`. O manifesto associa capturas a 34 requisitos, dos quais 16 têm status `VALIDADO` em escopo delimitado; 1.335 não têm cenário E2E matriciado e 5 seguem sem mídia por privacidade. A captura `midia/protocol-flow--cidadao-abre-protocolo-servidor-tramita-e-cidadao-acompanha-ate-a-conclusao--recibo-disponivel-apos-falha.png` foi inspecionada: a chave aparece mascarada, e a imagem não contém dados reais.
- `./dev participant-access-e2e`: **1/1 passou** com PostgreSQL e S3Mock descartáveis. Confirmou criação e revogação de convite individual, acompanhamento pelo participante e que a credencial do participante não habilita o recibo do requerente; os serviços e volumes temporários foram removidos.

## Evidência e limites

O teste confirma a resposta do cidadão à falha transitória e a recuperação visual do andamento, mas injeta a falha de rede em Playwright. A captura associada a `ED91-0481` é parcial; a inspeção visual confirmou chave mascarada, mas não demonstra indisponibilidade do serviço em produção, e-mail, sucesso operacional de storage, POC da comissão ou os demais comportamentos do requisito. O status continua parcial. A rodada inicial com falha do E2E de workflow permanece documentada no manifesto `20261108T235648Z-3235773`; a rodada completa posterior passou e não sobrescreveu o histórico.
