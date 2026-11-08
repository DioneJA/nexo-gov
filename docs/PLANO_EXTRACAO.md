# Plano de extração para o NEXO Core

## Regra de separação

O Core contém infraestrutura e contratos reutilizáveis, sem entidades de condomínio, prefeitura ou cliente. NexoGov consome uma versão identificada do artefato Maven e declara suas regras de organização e protocolo no próprio produto.

## Fase implementada

`nexo-core-tenant:0.2.0` fornece `TenantContext`, `MissingTenantContextException`, `TenantMembershipVerifier`, `TenantAccessDeniedException`, `ExternalIdentity` e a porta genérica `ExternalIdentityResolver`. O consumidor autentica o ator e passa uma verificação de associação ativa, que o Core exige antes de abrir o escopo; o escopo é fechado ao fim da requisição. O contrato preserva issuer e subject, mas a resolução global para ator pressupõe uma identidade global inequívoca. Os nomes legados do NexoGov são locais a cada organização; por isso, o Gov usa `OrganizationExternalIdentityResolver` e o adapter JDBC `JdbcExternalIdentityResolver`, com tabela V9 chaveada por organização, issuer e subject. A migração não cria links automaticamente e recusa vínculos globais antigos até reconciliação. A aplicação mantém validação de token, conta `user_accounts.active`, ativação explícita do link, autorização e membership; o header de organização apenas seleciona o vínculo e não concede acesso.

## Próximas extrações candidatas

1. Contrato de autorização por recurso, auditoria append-only e política de contexto assíncrono.
2. Portas para armazenamento de arquivo, notificação, configuração de tenant e erros API.
3. Componentes Vue/Quasar sem textos, entidades ou fluxos de domínio do produto.
4. Testes comuns de isolamento, upload, busca e autorização para aplicações consumidoras.

Cada extração requer teste do contrato e adaptador concreto dentro do produto. O que ainda não tem segundo consumidor permanece simples e não vira abstração antecipada.

## Distribuição e atualização

Os repositórios são independentes. O NexoGov depende da versão explícita `0.2.0`; `scripts/bootstrap-local.sh` instala essa versão na Maven local para o desenvolvimento deste workspace. Para onboarding externo, a versão deve ser publicada em repositório de pacotes com assinatura e imutabilidade de release. Nunca republicar artefato com o mesmo número. A aplicação deve ter testes de compatibilidade antes de avançar a versão do Core.
