# Incremento 19 — fundação de identidade federada

## Entrega

- NEXO Core `0.2.0` publica `ExternalIdentity` e `ExternalIdentityResolver`, contratos Java pequenos e neutros de domínio. O Gov consome a versão instalada como dependência Maven; nenhuma regra de prefeitura ou condomínio entrou no Core.
- O NexoGov valida a assinatura do token contra as chaves JWKS configuradas, além do `issuer`, validade temporal e `audience`. A validação segue a integração oficial de Resource Server do Spring Security; não foi configurado nem homologado um IdP municipal.
- O `issuer + subject` exato resolve para uma conta local persistida e ativa. E-mail e papéis do token não fazem vínculo nem concedem autorização. O filtro seguinte continua validando a associação ativa à organização em toda chamada protegida.
- A migração V6 cria contas e vínculos externos. Ela importa a data histórica de cadastro somente quando todos os perfis de organização concordam; datas conflitantes ou ausentes ficam explicitamente desconhecidas.
- O perfil de produção permanece fechado quando issuer, JWKS ou audience estão ausentes. HTTP Basic continua restrito ao perfil `local`.
- Um teste integrado inicial revelou que o filtro federado também era registrado pelo container no perfil local e recusava Basic. O filtro agora só existe fora de `local` e seu registro automático no container é desativado para que execute uma vez, na posição definida dentro da cadeia Spring Security.
- Adicionado `./dev identity-test`, que executa isoladamente os testes de JWT, associação persistida e isolamento entre organizações.

## Rastreabilidade

Atualizadas as linhas de identidade e cadastro de usuário relacionadas a senha/credencial, suspensão, vínculo de usuário e data de cadastro, sem elevar requisito parcial a `VALIDADO`. As linhas da matriz descrevem separadamente o que o teste comprova e o que ainda depende de serviço de identidade e operações administrativas. A matriz tem **1.388 linhas**, com **27 `EM_DESENVOLVIMENTO`**, **1.357 `NAO_INICIADO`** e **4 `VALIDADO`** em escopo limitado.

## Verificação

- NEXO Core: **6/6** testes e instalação local da versão `0.2.0` passaram.
- `./dev identity-test`: **3/3** testes de integração com PostgreSQL Testcontainers e JWKS local efêmero — issuer/audience/expiração inválidos, conta sem vínculo/inativa, membership e tentativa cross-tenant.
- `MigrationSafetyIntegrationTest`: **2/2** testes passaram, incluindo preservação de dados legados e importação de datas somente quando não ambíguas.
- `./dev verify`: passou; Core **6/6**, backend **15/15**, build das entradas pública e interna, matriz **1.388/1.388** e Playwright/Chrome **1/1**.
- A primeira verificação integrada falhou em 7 chamadas da suíte local; após restringir o filtro federado ao perfil de produção e desativar seu registro no container, as 15 integrações passaram e a nova execução completa concluiu sem erro.
- `git diff --check`: passou nos dois repositórios; nenhum espaço em branco indevido foi encontrado.

## Limites e dependências

- Ainda falta definir o IdP municipal, seus metadados/chaves, políticas e responsáveis de provisionamento. Não há integração ou evidência de autenticação contra um provedor real.
- Ainda não existe cliente OIDC na interface, cadastro administrativo auditável de contas e vínculos, procedimento de primeiro administrador, recuperação de senha, TOTP/MFA, e-CPF ou login social/Gov.br.
- A conta local serve apenas ao desenvolvimento; esse incremento não valida a POC oficial nem a identidade completa do edital.
- As skills de contrato público no Core e de fatia vertical no Gov foram atualizadas; o Core registra a mudança em `docs/INCREMENTO_02.md`. `AGENTS.md`, `README.md`, os harnesses, a matriz e os roteiros foram sincronizados nos dois projetos.

## Arquivos principais

- Core: `../nexo-core/src/main/java/br/com/nexo/core/identity/`, `../nexo-core/CHANGELOG.md`, `../nexo-core/pom.xml`.
- Backend: `backend/src/main/java/br/com/nexo/gov/config/SecurityConfiguration.java`, `backend/src/main/java/br/com/nexo/gov/security/`, `backend/src/main/resources/db/migration/V6__persistent_external_identity.sql`.
- Testes/harness: `backend/src/test/java/br/com/nexo/gov/security/FederatedIdentityIntegrationTest.java`, `backend/src/test/java/br/com/nexo/gov/migration/MigrationSafetyIntegrationTest.java`, `dev`.
- Rastreabilidade: `docs/licitacoes/mata-de-sao-joao-91-2025/`.

Nenhum deploy, publicação, commit ou alteração no NexoLar foi feito.
