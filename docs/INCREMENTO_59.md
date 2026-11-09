# Incremento 59 — aviso de privacidade por organização

## Fonte e escopo

`ED91-0150`, Edital PE 91/2025, §14.1.13, folha impressa 19, subtítulo “CENTRAL DE ATENDIMENTO”; `TR91-0155`, Termo de Referência, §16, PDF p.15 (transcrição conferida visualmente). Ambos pedem configuração de texto explicativo e link à Política de Privacidade da Administração na Central de Atendimento, com referência ao art. 50 da LGPD. O critério validado nesta fatia cobre somente configuração, persistência, escopo por organização e exibição antes da coleta no portal cidadão. A Administração fornece e aprova o conteúdo jurídico. Isso não prova adequação da política, conformidade geral com a LGPD ou aprovação em POC oficial.

## Mudanças

- `V29__organization_privacy_notice.sql` cria armazenamento próprio por organização; não altera tabelas ou histórico de migrations anteriores.
- `OrganizationPrivacyNoticeController` e `OrganizationPrivacyNoticeService` usam `TenantContext` no servidor, exigem ADMIN organizacional ativo para publicar, verificam organização ativa, aceitam apenas texto simples e URL HTTPS pública e disponibilizam ao cidadão somente esses dois campos com `Cache-Control: no-store`. O corpo PUT exige presença dos dois campos JSON para não confundir `{}` com uma limpeza; `null` explícito ou valores em branco limpam os dois juntos.
- A alteração é auditada sem registrar o texto ou a URL no resumo. Publicação e limpeza são atômicas: texto e endereço precisam vir juntos ou ser limpos juntos.
- O portal do servidor separa a edição do conteúdo da identidade visual, mostra prévia local e requer ação explícita para publicar. A UI mascara o controle administrativo conforme a capacidade retornada, mas a autorização decisiva permanece no backend.
- A Central do cidadão exibe o aviso da organização selecionada antes dos dados pessoais, como texto simples, com link fixo “Política de Privacidade” aberto com `rel="noopener noreferrer"`. Mudanças de organização limpam respostas antigas; campos ficam bloqueados durante busca/erro, com retry após falha, e o E2E verifica a ausência do aviso de Alfa ao selecionar Beta. A região mantém seu nome acessível em carregamento, erro e sucesso.
- OpenAPI, regras visuais, skills de fatia vertical, design e rastreabilidade, `DESIGN.md`, `AGENTS.md`, `README.md`, harness e artefatos derivados da matriz foram atualizados. `DESIGN.md` agora registra as referências públicas pesquisadas para formulários e acessibilidade, mantendo Apple HIG, tokens NEXO e marca NEXO como sistema aplicado.
- Nenhuma alteração foi feita no NexoLar. O sandbox manual persistente não foi iniciado, migrado, reparado ou apagado. V29 foi aplicada apenas nos bancos descartáveis de Testcontainers/E2E; V2 permanece idêntica ao HEAD.

## Verificação

- `./dev privacy-notice-test`: **13 testes passaram** — 3 de integração PostgreSQL Testcontainers e 10 de contrato OpenAPI. Cobrem ADMIN/setor, duas organizações, autorização, persistência, remoção, URL inválida ou com credenciais, configuração incompleta, corpo `{}` rejeitado, limpeza explícita com `null`, auditoria redigida, organização inativa, `no-store` e schema sem `format: uri` incompatível com string vazia.
- `npm run build` em `frontend/`: **passou** (TypeScript/Vue e Vite).
- `./dev verify-fast`: **passou** — NEXO Core com 14 testes, backend com 91 testes, build do frontend e verificação da matriz. Flyway validou V29 em Testcontainers e o runner não iniciou nem tocou no sandbox persistente.
- `./dev privacy-notice-e2e`: **1/1 passou** em duas execuções finais contra API real com PostgreSQL e S3Mock descartáveis. O fluxo publica, recarrega, exibe aviso multilinha e link, bloqueia campos, confirma o nome acessível enquanto a consulta está pendente/em erro, troca município com resposta atrasada e recupera falha por retry.
- `npm run build` em `frontend/`: **passou** (TypeScript/Vue e Vite).
- `./dev evidence-export-test`: **23 testes passaram**, incluindo a allowlist dos dois labels, IDs/referências e rejeição de captura parcial ou requisito composto sem todas as mídias.
- `./dev evidence-privacy-notice`: **1/1 cenário passou** e exportou dois PNGs, ambos inspecionados visualmente, em `/home/exati/Documents/NexoGov-Evidencias/PE-91-2025/20261109T210731Z-1075844/midia/`. O manifesto e hashes ficam em [manifesto.md](/home/exati/Documents/NexoGov-Evidencias/PE-91-2025/20261109T210731Z-1075844/manifesto.md).
- `./dev matrix-refresh` e `./dev matrix`: **passaram**. `ED91-0150` e `TR91-0155` permanecem `VALIDADO` somente para o critério limitado descrito; o refresh não promoveu nenhum status. A matriz mantém 1.388 linhas: 54 `EM_DESENVOLVIMENTO`, 19 `VALIDADO` em escopos limitados e 1.315 `NAO_INICIADO`; 700 critérios ainda aguardam decomposição observável.
- A execução de todos os 14 cenários de evidência passou uma vez (14/14). Uma segunda execução terminou 13/14: o cenário independente de workflow falhou ao aguardar “Análise técnica” após seleção de setor. O fluxo focado de privacidade passou novamente e gerou o conjunto limpo citado acima; a falha do workflow permanece registrada como instabilidade alheia a esta fatia.
- Não foi feito deploy nem teste contra serviço, bucket, credencial ou dado real.

## Revisão visual

O painel de privacidade fica em uma seção administrativa própria, abaixo da identidade visual, e a Central coloca o aviso antes dos campos. O link e o foco visível usam tokens NEXO; o texto é interpolado, sem HTML. Uma primeira captura revelou o menu de organização ainda em transição; o cenário passou a esperar o menu fechar. As duas capturas finais foram inspecionadas sem esse artefato; mostram apenas conteúdo fictício e a apresentação/administração do aviso, não os estados de falha, a conformidade jurídica ou o fluxo completo do edital.

O repositório não contém uma especificação Figma conectada para validar Dev Mode ou biblioteca visual. O modo escuro, alto contraste e navegação completa por teclado não foram verificados nesta fatia; o E2E exercita os fluxos funcionais e a interface usa os tokens/layout existentes. Essas lacunas não foram marcadas como conformidade de acessibilidade.

## Harness, skills e documentação

`./dev privacy-notice-test`, `./dev privacy-notice-e2e`, `./dev evidence-privacy-notice` e `./dev evidence-export-test` tornam repetíveis os testes de servidor, fluxo no navegador, capturas privadas e regressão do exportador. AGENTS.md e `./dev help` descrevem corpo ausente versus null explícito, rótulo acessível em carregamento/erro e troca com resposta atrasada. As skills `nexogov-vertical-slice`, `nexogov-tender-traceability` e `nexogov-design-system` registram os limites reutilizáveis: armazenamento independente, autorização ADMIN no servidor, escopo por organização, texto simples/HTTPS, prévia, publicação explícita, remoção de conteúdo da auditoria e proibição de afirmar adequação jurídica.

## Limites e pendências

O NexoGov não redige a política oficial, não escolhe base legal nem prazo de retenção e não certifica conformidade jurídica. A prova local demonstra apenas a configuração e apresentação do texto/link por organização. O conteúdo deverá ser revisado e aprovado pelo Município antes do uso real; controles de consentimento, retenção, governança, política pública completa e a POC oficial continuam avaliados separadamente na matriz.
