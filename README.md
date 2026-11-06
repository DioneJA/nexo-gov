# NexoGov

Plataforma SaaS independente para gestão pública. Este repositório não contém dados, credenciais, buckets nem dependências de execução do NexoLar.

## Primeiro fluxo vertical

A implementação inicial cobre abertura externa de protocolo, numeração anual por organização, recibo PDF com QR Code, consulta por token aleatório, encaminhamento, recebimento, despacho, consulta pública filtrada e conclusão. O backend valida associação ativa do servidor à organização no filtro HTTP e aplica `organization_id` às consultas internas. O histórico de eventos é append-only no banco.

O perfil local inclui dois órgãos fictícios e uma conta de demonstração. Não insira dados pessoais reais. HTTP Basic existe somente para o perfil `local`; a solução de identidade de produção, perfis granulares, TLS, armazenamento seguro de anexos, retenção, limitação de abuso e a maior parte dos requisitos do edital ainda não estão implementados.

## Executar localmente

Pré-requisitos: Java 21, Maven, Node/npm e Docker Compose.

1. Copie `.env.example` para `.env` e altere a senha de demonstração.
2. Execute `./scripts/bootstrap-local.sh` para instalar o NEXO Core local, rodar os testes/build e compilar a interface.
3. Em um terminal, execute `./scripts/start-local.sh` para iniciar o Postgres isolado e a API em `http://localhost:8080`.
4. Em outro terminal, execute `npm --prefix frontend run dev`.
5. Na área do servidor, informe o usuário e a senha definidos no `.env`. O perfil local cria `Município Alfa` e `Município Beta`, cada qual com Protocolo Geral e Secretaria de Obras.

Para executar o fluxo automatizado no Chrome, use `npm --prefix frontend run e2e`. Requer Docker e Google Chrome instalados. O teste cria um PostgreSQL descartável no projeto Compose `nexogov-e2e`, na porta 55434, e remove o contêiner e o volume ao terminar. Não precisa do `.env` de desenvolvimento.

A conta `servidor`/`troque-esta-senha-local` é apenas um exemplo de desenvolvimento; altere a senha antes de executar. A API documentada fica em `/swagger-ui/index.html` e a saúde em `/actuator/health`.

O banco de demonstração usa volume e porta próprios (`127.0.0.1:55433`, volume `nexogov-local_nexogov_pgdata`). Nenhum comando de implantação ou publicação é configurado.

## Estrutura

- `backend/`: API Spring Boot, migrações Flyway, validação de tenant, fluxo de protocolo e teste de integração com PostgreSQL Testcontainers.
- `frontend/`: aplicação Vue 3 e Quasar para cidadão e servidor.
- `contracts/`: contratos OpenAPI versionados.
- `docs/`: análise de origem, decisão de extração, edital, rastreabilidade, riscos e plano de implantação.
- `docs/INCREMENTO_01.md` a `docs/INCREMENTO_06.md`: histórico dos incrementos verificáveis.
- `../nexo-core/`: repositório Maven independente do NEXO Core; a versão `0.1.0` é instalada localmente antes do build do Gov.

## Estado de aderência

A matriz oficial é a fonte de status requisito por requisito. O primeiro incremento registra 10 itens como `EM_DESENVOLVIMENTO` com evidência parcial de teste; nenhum deles é declarado validado. `VALIDADO` exige satisfazer o critério completo, teste específico e evidência verificável. A POC do Pregão PE 91/2025 foi corrigida pela Errata I para duração de 90 minutos, prorrogável. O certame já aparece adjudicado/contratado na página oficial, portanto este código não representa candidatura ou conformidade contratual.
