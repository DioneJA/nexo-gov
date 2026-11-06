# Auditoria de arquitetura do NexoLar (somente leitura)

**Origem inspecionada:** `/home/exati/Desktop/pessoal/nexolar`, remoto `https://github.com/DioneJA/nexolar.git`, branch `master`, HEAD `9c14dbf5bf749717b2370e10d95294663e1334b3` (`docs: record Render cold-start evidence`). A referência do remoto estava em `26fc3293b3c067620dfebc34bdf956530de80aad`. A árvore local já apresentava 551 entradas modificadas ou não rastreadas. O repositório original não foi alterado; a divergência local permanece sem tocar.

## Stack observada nos arquivos

- Java 21 e Spring Boot 3.3.2 em `backend/pom.xml`, Maven, PostgreSQL e Flyway.
- Vue 3.4, Quasar 2.16, Pinia, Vue Router, TypeScript e Vite nas dependências do frontend; Vitest para testes de componentes.
- Testes Java e frontend já existentes. Foram encontrados, entre outros, `backend/src/test/java/com/base/app/MultiTenantIsolationTest.java`, `TenantSecurityHttpIsolationTest.java` e `AccountContextPermissionTest.java`.
- Migrations Flyway sequenciais (`backend/src/main/resources/db/migration`), imagem de backend e infraestrutura local com Docker/Postgres.

## Fronteiras observadas

- `security/context/AccountContextFilter.java` verifica o contexto de conta contra associações ativas antes de definir `AccountContext`; `security/jwt/JwtAuthenticationFilter.java` autentica o bearer JWT. A autorização é aplicada também nos endpoints, por exemplo com `@PreAuthorize`.
- `frontend/src/api/client.ts` anexa credenciais/contexto e concentra o tratamento da API.
- Existem serviços de autenticação, armazenamento S3, auditoria, e-mail/notificações e integrações, porém ainda não foram auditados endpoint a endpoint quanto ao acoplamento ou às garantias operacionais. A existência desses serviços não prova isolamento integral.
- Os testes de isolamento existentes são referências valiosas, mas não foram executados nesta auditoria porque a árvore do NexoLar está suja e fora do escopo de alteração.

## Componentes não portáveis como domínio comum

Pacotes, unidades, moradores, visitantes, reservas, animais, cobrança condominial, cadastro de condomínios e suas regras não devem entrar no NEXO Core. Entidades, migrations, API, traduções, notificações, templates e componentes que incorporam essas regras devem permanecer no NexoLar. Os nomes e padrões observados orientam uma implementação nova; nenhum código de domínio foi copiado.

## Limites desta auditoria

É um inventário estático amostral da árvore local, não uma certificação de segurança, revisão linha a linha de todas as 551 entradas locais, nem confirmação de que cada integração está pronta para produção. O NexoLar está dois commits divergente do remoto observado e tem trabalho local não rastreado/modificado. Não foi feito `fetch`, checkout, limpeza, migration, build, teste, commit ou alteração nessa origem.
