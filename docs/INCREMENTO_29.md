# Incremento 29 — recibo estável e contratos de tramitação

**Data:** 07/11/2026
**Escopo:** preservar no recibo o setor atribuído na abertura, especificar o comando `forward` separadamente e provar validação e isolamento tenant nas transições. Esta fatia não conclui o ciclo completo do protocolo.

## Alterações

- A migração forward-only `V11__protocol_initial_department_snapshot.sql` adiciona `protocols.initial_assigned_department_name`, nullable. Nenhum dado existente é preenchido: para protocolos anteriores a V11 não há fonte confiável do setor inicial depois de tramitações.
- Na abertura de protocolos novos, `ProtocolService` salva o nome do setor inicial junto do vínculo/setor validado. A geração do PDF lê esse snapshot, não o nome do setor atualmente responsável; encaminhamentos e despachos posteriores não mudam o recibo. Para protocolo legado com snapshot nulo, o PDF não inventa um setor.
- O OpenAPI distingue os comandos: `forward` exige body JSON e `destinationDepartmentId`; a API valida os dois e devolve 400 sem alterar protocolo/eventos quando faltam. `receive`, `dispatch` e `complete` aceitam body ausente quando não há destino a informar. O DTO de `forward` é independente de `TransitionRequest`.
- Testcontainers prova 404 para `forward`, `receive`, `dispatch` e `complete` quando se usa o tenant Beta contra um protocolo de Alfa, sem mudança de estado, setor atribuído ou contagem de eventos.
- `./dev protocol-routing-test` passa a selecionar as provas de roteamento/recibo, estado legado, contrato e isolamento; `./dev e2e --grep 'texto do cenário'` permite reproduzir um único cenário em infraestrutura efêmera.

## Rastreabilidade

- `TR91-0510` está `VALIDADO` apenas para seu texto literal: após a inclusão, a interface mostra a confirmação/número, disponibiliza o comprovante e o PDF real contém o setor destinatário inicial; a estabilidade do recibo após roteamento também é coberta. A evidência é teste automatizado local, não ata, laudo ou aprovação da comissão da POC.
- `ED91-0481` permanece `EM_DESENVOLVIMENTO`: acesso integral do setor ao conteúdo e movimentações e resposta direta ao requerente ainda não foram implementados/demonstrados. Não declarar o requisito amplo completo.
- A matriz e o roteiro POC mantêm os dois escopos separados e registram o cenário, os dados fictícios, os passos, o resultado esperado, a evidência e a limitação. A atualização deixou 1.388 linhas, 32 em desenvolvimento, 5 validadas em escopos individuais e 1.351 não iniciadas.

## Verificações executadas

- `./dev protocol-routing-test`: **6 testes aprovados, 0 falhas**, compilação backend concluída; PostgreSQL 16.4 em Testcontainers aplicou V1–V11. Inclui comparação do PDF antes/depois do encaminhamento e despacho, recibo legado sem setor inventado, body/destino ausentes em `forward` (400), body ausente nos demais comandos e quatro recusas cross-tenant (404) sem mutações.
- `./dev e2e --grep 'administrador configura o destino e o setor aparece no recibo real do protocolo'`: **1 teste Playwright aprovado**. A tela confirma o protocolo logo após a inclusão e o teste extrai o setor do PDF servido pela API real. PostgreSQL/Compose, portas e volume foram exclusivos da execução e removidos ao final; nenhum banco persistente foi usado.
- `./dev matrix-refresh`: **1.388 linhas**; 742/742 requisitos TR com transcrição conferida visualmente; IDs estáveis e matriz estrutural válidos.
- `./dev matrix`: aprovado.
- `git diff --check`: aprovado.

## Risco separado de autenticação de produção

A área do servidor usa HTTP Basic no cliente (`frontend/src/api.ts`) e o backend só habilita Basic no perfil `local` (`SecurityConfiguration`, método `localSecurityFilterChain`). No perfil não local, com provedor configurado, a cadeia aceita JWT Bearer; sem configuração, nega rotas internas. Não há ainda uma ponte/login OIDC visível na interface. Portanto, os testes deste incremento demonstram apenas o fluxo no perfil local e não provam que servidores municipais conseguem autenticar em produção. Este bloqueio fica fora do escopo da V11 e requer uma fatia própria de autenticação OIDC; nenhum status de aderência de autenticação foi elevado aqui.

## Limites e não execução

Não foram executadas a suíte completa, o build do NEXO Core ou o E2E completo; o foco foi o slice de protocolo. Não se usou banco persistente, não se reiniciou serviço local, não se publicou nem fez deploy, e não houve commit. Não foram alterados NexoLar ou NEXO Core.

## Verificação integrada posterior

Depois do fechamento desta fatia, em 07/11/2026, executei `./dev verify` para conferir os incrementos integrados: NEXO Core **11/11**, backend **30/30** (inclui Testcontainers/PostgreSQL), build TypeScript/Vite aprovado, auditoria npm sem vulnerabilidades reportadas, matriz **1.388 requisitos** (32 `EM_DESENVOLVIMENTO`, 5 `VALIDADO`, 1.351 `NAO_INICIADO`) e E2E **5/5**. O ambiente Compose e o volume dessa execução usaram UUID próprio e foram removidos pelo harness. O serviço local persistente em `localhost:9000` e seu banco/volume não foram usados nem reiniciados.

Para acompanhamento visual, também foi iniciada uma prévia isolada com projeto Compose UUID `nexogov-e2e-19860518-98c3-4153-8bfc-b8916f717008`, API em `localhost:56383` e UI em `localhost:45407`; os endpoints de saúde, organizações de demonstração Alfa/Beta e catálogo público de Alfa responderam corretamente. Essa segunda sessão permanece aberta no navegador e usa apenas PostgreSQL efêmero com dados fictícios; ao encerrar a sessão, o script remove somente o projeto/volume UUID correspondente.
