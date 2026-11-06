# Incremento 01 — Core de tenant e fluxo inicial de protocolo

**Data:** 06/11/2026
**Escopo:** primeiro incremento independente do NEXO Core e NexoGov.

## Construído

- Criado `nexo-core-tenant:0.1.0` sem regras de governo ou condomínio. `TenantContext.openVerified` exige confirmação da associação do ator antes de abrir o escopo e fecha/restaura o contexto com `AutoCloseable`.
- Criada API NexoGov com persistência PostgreSQL/Flyway, organizações de demonstração independentes, protocolo externo, numeração anual concorrente, tramitação por setor, timeline pública filtrada por token e recibo PDF com QR Code.
- Incluído portal web Vue 3/Quasar para abertura, acompanhamento e caixa local de servidores.
- Mantidos autenticação e dados de demonstração somente no perfil `local`; nenhum segredo de ambiente foi adicionado ao repositório.

## Verificação executada

- NEXO Core: `mvn -q clean test install` — sucesso; teste de contexto de tenant passou.
- Backend NexoGov: `NEXOGOV_DEMO_USERNAME=servidor NEXOGOV_DEMO_PASSWORD=test-only-password-2026 mvn -q clean test` — sucesso em PostgreSQL 16.4 via Testcontainers; 3 testes, 0 falhas, 0 erros.
- A integração cobre a abertura, número sequencial único sob 24 requisições concorrentes, PDF e QR decodificado para o token certo, token inválido retornando 404, encaminhamento, recebimento, despacho, conclusão, visibilidade de eventos, tentativa de encaminhar a setor de outra organização, negativa 403 para usuário sem associação ao setor, ausência de vazamento na lista de Beta, organização inexistente, falta de contexto e histórico append-only.
- `ProductionSecurityFailClosedTest` confirma que perfis fora de `local` mantêm leitura pública e negam rotas administrativas tanto sem credenciais quanto com HTTP Basic.
- Frontend: `npm run build` — sucesso (`vue-tsc` e Vite).
- Navegador: `npm --prefix frontend run e2e` — sucesso em Chrome; percorre abertura, recibo, tramitação, despacho e consulta, usando PostgreSQL de demonstração descartável.
- Matriz: `python3 scripts/check-matrix.py` — 1.140 linhas, IDs únicos, campos preenchidos e estados válidos.

## Estado de rastreabilidade

Dez itens estão `EM_DESENVOLVIMENTO` com limites explícitos: `ED91-0035`, `ED91-0059`, `ED91-0091`, `ED91-0101`, `ED91-0124`, `ED91-0144`, `ED91-0477`, `ED91-0479`, `ED91-0483` e `ED91-0566`. A classificação registra somente o que foi provado; contas externas, gestão de usuários, permissões documentais completas, calendário, módulo de aprovação de projetos, reabertura e outros documentos continuam pendentes. A matriz está em `licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv`.

O Termo de Referência está acessível e recebeu OCR em português; os 494 candidatos extraídos ainda exigem comparação visual e decomposição por obrigação. O total de `VALIDADO` permanece zero. Não há alegação de atendimento integral ou de aptidão empresarial/contratual.

## Separação e publicação

- Commit local do Core: `0e8182d` (`feat: add verified tenant context core`).
- O NexoGov tem como remoto configurado `https://github.com/DioneJA/nexo-gov.git`; ainda não foi publicado.
- Nenhum comando foi executado no NexoLar. O repositório e seus dados permanecem intactos.
