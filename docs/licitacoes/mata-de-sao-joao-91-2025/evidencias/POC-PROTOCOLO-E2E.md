# Evidência E2E — fluxo vertical de protocolo

**Execução:** 06/11/2026
**Natureza:** teste automatizado interno; não é ata nem resultado de POC oficial.

## Ambiente

- Frontend Vue 3/Quasar em Chrome via Playwright.
- API Spring Boot no perfil `local`.
- PostgreSQL 16.4 em volume Compose temporário `nexogov-e2e`; o contêiner e o volume foram removidos ao fim da execução.
- Organização de demonstração: Município Alfa; setor de destino: Secretaria de Obras.
- Dados totalmente fictícios gerados pelo teste; nenhum dado pessoal real foi usado.

## Passos demonstrados

1. Abrir solicitação na Central de Atendimento e obter número e token de consulta.
2. Baixar o recibo PDF; a integração da API decodifica o QR e verifica que aponta para o token emitido.
3. Entrar na área local do servidor e localizar o protocolo na caixa de entrada.
4. Encaminhar, receber, despachar para Secretaria de Obras, receber e concluir.
5. Consultar o protocolo como requerente e confirmar o estado `Concluído`.

## Resultado

- Playwright/Chrome: **1 teste aprovado**, 0 falhas.
- Testes backend Testcontainers/PostgreSQL: **3 testes aprovados**, 0 falhas, incluindo concorrência da numeração, QR PDF, token inválido, isolamento entre organizações, associação ao setor, histórico append-only e perfil não local fechado por padrão.
- Build do frontend: `npm run build` aprovado depois de `npm ci`.
- Status de aderência: os requisitos relacionados permanecem `EM_DESENVOLVIMENTO`, porque este fluxo não implementa o escopo completo de cada item.

## Requisitos relacionados

`ED91-0035`, `ED91-0059`, `ED91-0091`, `ED91-0101`, `ED91-0124`, `ED91-0144`, `ED91-0477`, `ED91-0479`, `ED91-0483` e `ED91-0566`.

## Limites da evidência

O ensaio prova somente este caminho vertical no ambiente local. Não demonstra login externo, permissões configuráveis, calendário, anexos, reabertura, módulo completo de aprovação de projetos, assinatura, notificação, migração, SLA, treinamento, capacidade de produção ou os demais cenários da POC oficial.
