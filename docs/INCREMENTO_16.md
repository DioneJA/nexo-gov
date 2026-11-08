# Incremento 16 — escopo de caixa por setor e tramitação serializada

## Entrega

- `GET /api/v1/staff/protocols` agora filtra no backend pela organização do `TenantContext` e, para servidores sem papel de administrador da organização, pelos setores com lotação ativa. Administradores de organização continuam vendo todos os protocolos do tenant.
- As transições de protocolo leem o registro com `SELECT ... FOR UPDATE`; tentativas concorrentes validam o estado atualizado e não duplicam o evento.
- A migração Flyway V3 bloqueia `TRUNCATE` na tabela `protocol_events`, além da proteção existente contra `UPDATE` e `DELETE`.
- OpenAPI registra a política de visibilidade da caixa.
- Os requisitos ED91-0062 e ED91-0063 passaram a `EM_DESENVOLVIMENTO`, com testes e limites explicitados. A implementação cobre apenas a caixa e o protocolo atual; níveis e tipos documentais completos continuam pendentes.
- Corrigido o aceite de operação da POC: Errata I §1, p.1 é a fonte dos 90 minutos prorrogáveis; a Errata I p.2 contém a confirmação de e-mail e telefone alternativo. O roteiro agora lista local/modalidade, caráter público, materiais, janela e laudo.
- A matriz usa `MATRIZ_IDS.csv` para preservar os IDs numéricos por âncora de página/texto. Itens que deixem de ser reconhecidos pelo parser permanecem na matriz como `BLOQUEADO` para revisão. Requisitos do TR sem implementação ou dependência externa concreta ficam `NAO_INICIADO`, não `BLOQUEADO`.
- Os cinco parágrafos de POC do TR §41 (PDF p.38) receberam aceite, responsabilidades, teste/limite, roteiro e evidência compatíveis com o estado real; não existe demonstração integral.

## Verificação executada

- `./dev verify` — concluído com exit code 0: NEXO Core (3 testes) e backend NexoGov (9 testes) aprovados; build de produção Vue/Quasar concluído; matriz validada; E2E `cidadão abre protocolo, servidor tramita e cidadão acompanha até a conclusão` aprovado (1/1) em PostgreSQL descartável. O Compose temporário foi removido ao final.
- `mvn -B -f backend/pom.xml test` — 9 testes aprovados, zero falhas/erros, usando PostgreSQL 16.4 em Testcontainers. Inclui negação entre setores, acesso após lotação ativa, escopo do administrador, concorrência na conclusão e recusa de `TRUNCATE`.
- `./dev matrix-refresh` — 1.388 linhas, IDs existentes preservados (0 ausentes, 0 novos na regeneração); 742 transcrições do TR visualmente conferidas; status atual: 19 `EM_DESENVOLVIMENTO`, 1.369 `NAO_INICIADO`, 0 `VALIDADO` e 0 `BLOQUEADO`.
- Repetição idempotente de `./dev matrix-refresh` — hash dos quatro artefatos de rastreabilidade permaneceu igual.
- `git diff --check` — sem erros de whitespace.

## Limites e riscos restantes

- A matriz não representa aderência integral: nenhum requisito está `VALIDADO`; muitos itens ainda precisam de decomposição, implementação, testes e evidências.
- A lista de protocolos não substitui permissões por tipo documental, classificação de sigilo ou autorização para ler anexos, ainda ausentes.
- A identidade de produção não está implementada. A V2 exige concessão explícita em `organization_memberships`; não se promove automaticamente o antigo papel setorial `ADMIN`, para evitar escalada de privilégio. O procedimento seguro de bootstrap precisa ser construído antes de implantação.
- A demonstração corrente de protocolo cobre um fluxo parcial; agenda, equipe, infraestrutura, integrações externas e laudo de POC são evidências operacionais ainda pendentes.
- Nenhuma tela foi alterada neste incremento. Nenhum deploy, publicação ou acesso ao NexoLar foi realizado.

## Arquivos deste incremento

- Backend: `ProtocolService`, `ProtocolController`, migração `V3__protocol_event_truncate_guard.sql`, `ProtocolVerticalIntegrationTest` e contrato OpenAPI.
- Rastreabilidade: `MATRIZ_IDS.csv`, geradores e validador, `REQUISITOS.md`, `MATRIZ_ADERENCIA.csv`, `MATRIZ_ADERENCIA.md`, `POC.md`, `RISCOS.md` e esta evidência.
- Skills/documentação de uso: `nexogov-tender-traceability/SKILL.md` e README do produto.
