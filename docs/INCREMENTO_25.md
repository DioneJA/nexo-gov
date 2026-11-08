# Incremento 25 — identidade federada escopada por organização

## Entrega

O NEXO Core `nexo-core-tenant:0.2.0` preserva um contrato genérico para mapear uma identidade validada a um ator global. O modelo legado do NexoGov usa nomes de usuário em perfis e memberships locais a cada organização; por isso, o Gov implementa `OrganizationExternalIdentityResolver` e não força essa identidade para dentro do contrato global do Core.

A migration forward-only V9 passa a chavear `external_identity_links` por `(organization_id, issuer, subject)` e guarda o estado `active` por organização. Os links começam inativos e não há backfill automático. Se existirem links globais da V6, a migração para antes de alterar o esquema, com instrução para reconciliação por fonte autoritativa; a linha legada, contas e memberships permanecem intactas. `JdbcExternalIdentityResolver` só resolve link ativo do tenant selecionado. Depois, o filtro exige membership ativa e ausência de suspensão. O estado global `user_accounts.active` não autoriza nem revoga acesso entre organizações.

A validação JWT continua verificando assinatura RS256 por JWKS configurado, issuer, audience e validade. O header de organização seleciona o contexto; não concede acesso. A migração de data de cadastro também não replica um valor global para perfis de organizações quando a fonte é ausente ou conflitante.

## Rastreabilidade

Os IDs e textos oficiais permanecem estáveis. `ED91-0035`, `ED91-0038`, `ED91-0059`, `TR91-0045` e `TR91-0048` continuam `EM_DESENVOLVIMENTO`, com o trecho federado descrito como evidência parcial. Cadastro de senha/email pelo administrador, login OIDC municipal, provisionamento, recuperação de senha, autorização documental/grupos e demonstração com um provedor real continuam pendentes. `TR91-0069` continua `NAO_INICIADO`, sem decomposição.

O resumo da matriz permanece em 1.388 linhas: 31 `EM_DESENVOLVIMENTO`, 1.353 `NAO_INICIADO` e 4 `VALIDADO` com escopo restrito. Nenhum teste de identidade eleva sozinho o requisito completo a validado ou comprova aprovação da POC.

## Verificações

| Verificação | Resultado |
|---|---|
| `./dev identity-test` | Passou: 8 testes Testcontainers — quatro cenários de identidade federada e quatro de segurança de migração; PostgreSQL 16.4 e JWKS RSA efêmero/fictício. |
| `./dev matrix-refresh` | Passou; a regeneração seguinte produziu os mesmos cinco artefatos de rastreabilidade. |
| `./dev matrix` | Passou; IDs, origens, módulos e status foram conferidos para os cinco requisitos. |
| `git diff --check` | Passou na revisão da matriz; a conferência final do conjunto acumulado fica registrada no incremento mais recente. |

Os testes usam dois tenants fictícios com o mesmo username e subject, link ativo em um e inativo no outro; ignoram mudanças em `user_accounts.active`, e também verificam link ausente/inativo, membership ausente, suspensão, JWT inválido e falha segura da V9 diante de link global sem reconciliação. Não usam banco persistente nem um IdP de prefeitura.

## Skills, harness e documentação

`docs/ARQUITETURA_ORIGEM.md` e `docs/PLANO_EXTRACAO.md` distinguem o contrato genérico do Core do adaptador tenant-scoped do Gov. `./dev identity-test`, `AGENTS.md` e `README.md` registram a suíte focada que executa os testes federados e de segurança de migração. A skill `nexogov-vertical-slice` agora ensina a manter o vínculo escopado ao tenant quando a identidade local não for globalmente inequívoca, exigir membership e estado de suspensão no backend e recusar migração ambígua sem reconciliação autoritativa; ela preserva o contrato neutro do Core.

Este registro foi concluído em conjunto com o Incremento 26, que também atualizou a mesma skill com encerramento seguro de sessão e invalidação de respostas em voo. Para evitar sobrescrita entre agentes, o histórico da alteração da skill é compartilhado, mas cada incremento documenta somente a regra que introduziu. O harness de identidade existente foi suficiente; não foi criado um segundo comando para a mesma prova.

## Limites

A autenticação de produção continua fechada até haver configuração de issuer/JWKS/audience confiáveis e vínculo provisionado. A interface não implementa login OIDC e nenhum provedor externo foi integrado. O adaptador não é uma implementação genérica do Core. Um pacote Maven/UI consumível de forma reproduzível a partir de um clone limpo também continua pendente: o pacote UI permanece `private`/`UNLICENSED`, e não houve publicação.

Não houve deploy, publicação, commit, alteração do NexoLar nem acesso ao banco persistente.
