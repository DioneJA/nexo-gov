# Incremento 30 — autenticação OIDC e autorização federada

**Data:** 07/11/2026
**Escopo:** cliente OIDC público da área do servidor, resolução federada no backend, separação do bundle cidadão e atualização da evidência operacional. Sem deploy, publicação ou alteração de fonte no NEXO Core.

## Decisões e implementação

- O cliente web usa Authorization Code + PKCE sem segredo, nonce aleatório validado pelo OIDC client, state transitório em `sessionStorage`, callback com origem/caminho exatos e tokens somente em memória. Não solicita `offline_access` nem refresh token. Só o access token segue como Bearer para a API de servidor.
- Basic é selecionado explicitamente nos scripts local/E2E. Configuração OIDC ausente ou inválida não cria fallback em produção. Os fluxos Playwright com provedor simulado são separados da prova de autorização backend.
- O backend resolve o par `(organization_id, issuer, subject)` somente se o vínculo estiver ativo e a linha correspondente de `user_accounts` também estiver `active=true`. Membership e suspensão da organização continuam verificadas em cada requisição. O teste confirma 403 em todos os tenants enquanto a conta global está inativa.
- Em produção, issuer e JWKS precisam usar HTTPS; configuração incompleta ou insegura deixa a cadeia protegida em deny-all. HTTP é aceito apenas para loopback quando o perfil de teste habilita essa exceção; o perfil `local` usa Basic.
- Logout limpa tokens e estado locais e pode redirecionar ao endpoint de saída do provedor. Ele **não revoga o access token no servidor** nem garante invalidação imediata; um token pode permanecer válido até expirar, salvo recusa posterior por conta, vínculo ou acesso organizacional.
- O portal público usa um cliente `fetch` e tipos próprios, sem importar o cliente Axios/Basic da área autenticada. A checagem percorre os imports da entrada cidadã e rejeita fingerprints de Basic/Axios e campos internos `destinationDepartmentId`/`destinationDepartmentName`.
- A tela do servidor anuncia a preparação/carregamento das organizações e explica os estados sem organizações ou erro, mantendo os controles desabilitados até haver seleção. Em ponteiros coarse, campos densos, botões e abas têm alvo mínimo de 48 px.

## Rastreabilidade do Pregão

- Os geradores foram corrigidos para exigir conta local ativa e para documentar HTTPS em issuer/JWKS. O roteiro não atribui revogação server-side ao logout e distingue o E2E de provedor/API simulados da integração Testcontainers do backend.
- `ED91-0481` mantém a referência secundária `Termo de Referência §4, PDF pp.48–49`; seu escopo amplo permanece `EM_DESENVOLVIMENTO`.
- `TR91-0387` informa 3/742 requisitos TR validados em escopo limitado e 724/742 ainda pendentes de decomposição/aceite observável. `RISCOS.md` e `REQUISITOS.md` registram o total geral de 1.388: 32 em desenvolvimento, 5 validados em escopo restrito e 1.351 não iniciados.
- Nenhum status foi promovido por causa do cliente OIDC ou do roteiro de teste.

## Revisão visual

Apliquei a checklist rápida de revisão frontend: hierarquia e estados de acesso ficam visíveis, a espera tem anúncio `role=status`, e os campos densos respeitam o alvo touch de 48 px. Bordas, seleção, separadores, scrollbars e contornos de feedback agora usam tokens NEXO já existentes. O pequeno selo de ambiente local permanece âmbar com valores locais porque `DESIGN.md`/tokens disponíveis não definem um token de aviso; nenhum token novo foi criado. A identidade navy/azul e a tipografia do produto foram preservadas.

## Testes e evidências

| Verificação | Resultado |
|---|---|
| `./dev identity-test` | Passou: 8 testes Testcontainers, incluindo conta inativa, vínculo por tenant e segurança da migração V9. |
| `IdentityProviderPropertiesTest` | Passou: 3 testes de HTTPS, HTTP loopback e configuração inválida. Também incluído na suíte completa. |
| `./dev oidc-test` | Passou: 7 testes com provedor simulado; cobre PKCE/state/nonce, callback, Bearer, logout, estado de carregamento e falhas fechadas. Não comprova IdP municipal. |
| `./dev frontend-auth-bundle-test` | Passou: build em diretório temporário e 2 módulos do grafo cidadão sem helpers de Basic/Axios ou campos de setor interno. |
| `./dev e2e` | Passou: 8 testes local Basic; 4 fluxos OIDC foram corretamente ignorados nesse modo. |
| `./dev verify-fast` | Passou: 11 testes do Core e 33 testes backend, build frontend em `/tmp`, matriz aprovada. O Core não teve fonte alterada; o comando executou testes e instalação Maven locais. |
| `./dev verify` | Passou com exit code 0: etapa anterior mais E2E Basic (8 passou, 4 OIDC-only ignorados) e E2E OIDC (7 passou), em dois projetos Compose descartáveis distintos. |
| `./dev matrix-refresh` / `./dev matrix` | Passaram: 1.388 requisitos; 742/742 transcrições do TR conferidas visualmente; 1.377 IDs numéricos estáveis; 32 `EM_DESENVOLVIMENTO`, 5 `VALIDADO`, 1.351 `NAO_INICIADO`. |
| `git diff --check` | Executado após a edição deste documento; sem erros. |

Os E2E usaram portas/Compose únicos por execução e removeram somente seus recursos temporários. O preview persistente não foi reiniciado, e o build final foi enviado para diretório temporário em vez de `frontend/dist`.

## Bloqueios e limites

- O IdP municipal, registro do cliente, provisionamento e autorização documental por recurso/grupo seguem sem prova. O mock de Playwright comprova apenas o protocolo do cliente e o envio de Bearer; a autorização real foi exercitada separadamente no backend.
- Não há revogação imediata do access token pelo servidor no logout. A expiração ou as verificações de conta, vínculo e acesso organizacional limitam a autorização subsequente.
- Auditoria somente leitura do pacote NEXO Core: o HEAD observado declarava `0.1.0` enquanto o NexoGov consome `0.2.0`; o checkout de trabalho usado no `verify` passou a checagem com `0.2.0` nos dois POMs. As fontes de identidade e `core-ui` não constam como versionadas; `core-ui` é privado e declara `UNLICENSED`; o frontend depende de `file:../../nexo-core` e o backend de artefato Maven local. Esses fatos e a recomendação de bloquear distribuição independente até haver fontes/artefatos reproduzíveis e termos de distribuição esclarecidos estão separados em `RISCOS.md`. Nenhuma fonte ou licença do Core foi alterada ou decidida neste incremento.

## Artefatos operacionais

A skill `.agents/skills/nexogov-vertical-slice/SKILL.md` agora exige conta local ativa, endpoints seguros, separa prova OIDC simulada da autorização backend e explicita o limite do logout. O harness mantém `./dev e2e` em Basic local, `./dev oidc-test` como prova focalizada e inclui os dois modos em `./dev verify`. `README.md`, `AGENTS.md`, `RISCOS.md`, arquitetura, plano de extração e matriz/POC foram sincronizados.
