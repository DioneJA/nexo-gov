# Incremento 43 — interface básica de workflow administrativo

**Data:** 08/11/2026
**Escopo:** interface autenticada para configurar versões de workflow por assunto, abrir processos a partir da versão ativa, exibir seu snapshot e histórico e avançar para a próxima etapa. Refere-se aos itens `ED91-0596` e `TR91-0691` conforme suas âncoras atuais na matriz; não incorpora requisitos independentes de impressão cronológica, árvore organizacional ou ordenação de anexos.

## Fronteira e decisões

- A tela usa os endpoints existentes `administrative-workflows` e `administrative-processes`; não houve alteração no contrato ou no backend.
- O editor fica somente no app autenticado de `/servidor/`. Reutiliza `staffApi`, o interceptor de autorização existente e `organizationHeaders`; organização e papel não são inferidos de valores de tela para conceder acesso.
- A administração da definição acompanha a capacidade organizacional existente, e a API continua exigindo papel ADMIN. A lotação do setor atual e do setor de destino continua sendo validada pelo serviço para cada avanço.
- O editor admite etapas ordenadas e múltiplos setores por etapa. A abertura oferece apenas os setores ativos da primeira etapa. O avanço oferece apenas setores ativos da próxima etapa do snapshot do processo.
- Depois de publicar uma versão seguinte, o processo existente é relido por seu ID e continua mostrando versão, etapas e nomes da definição inicial. O histórico vem do endpoint persistido.
- A última etapa é apresentada sem falsa conclusão: não existe nesta fatia endpoint de encerramento integrado ao workflow.
- O identificador do processo é mantido na tela para atualização e não é persistido em armazenamento do navegador. Mudança de organização ou assunto elimina os dados carregados; respostas assíncronas antigas são descartadas por geração de contexto.
- A tela segue `DESIGN.md`: tipografia do sistema, tokens NEXO, hierarquia direta, separadores finos, sequência numerada real e controles existentes do Quasar. Não foi criado um componente visual dependente de Figma nem alterado o design system.
- Revisão visual pelo checklist: hierarquia e ações principais estão nomeadas; cores e tipografia vêm dos tokens; foco de teclado herda o estilo global; o painel usa uma coluna abaixo de 900 px e quatro estados textuais; o E2E mede ausência de rolagem horizontal a 390 px. O checkout não contém especificação de componente Figma, e não foi introduzido suporte a tema escuro porque a aplicação ainda define somente o tema claro.

## Arquivos do incremento

- `frontend/src/staff/AdministrativeProcessPanel.vue`: editor de versão, abertura do processo, resumo do snapshot, etapas, timeline e tratamento visual de erros.
- `frontend/src/staff/StaffApp.vue`: inclusão do painel no app autenticado existente, sem duplicar login.
- `frontend/e2e/process-workflow.spec.ts`: prova Playwright contra API e PostgreSQL locais descartáveis.
- `frontend/playwright.evidence.config.ts` e `scripts/export-e2e-evidence.py`: inclusão do par exato spec/cenário e dos quatro labels necessários, sem promover status da matriz.
- `scripts/tests/test_export_e2e_evidence.py`: regressão de captura parcial para workflow.
- `dev`, `README.md`, `AGENTS.md` e `.agents/skills/nexogov-vertical-slice/SKILL.md`: comando repetível e regras operacionais desta fatia.

## Verificações executadas

- `npm run build` em `frontend/`: passou (`vue-tsc -b`, Vite); as entradas pública e do servidor foram geradas separadamente.
- `./dev process-workflow-e2e`: passou, 1/1 com API, PostgreSQL e storage simulados em projeto Compose descartável. O teste autentica como ADMIN fictício, publica v1, abre processo, publica v2 e atualiza o processo, verificando versão e nomes do snapshot v1; chama a API para tentar pular etapa e recebe 409 sem mudança; avança pela UI para a etapa final; chama a API com um membro sem lotação no setor corrente e recebe 403 sem alteração de etapa, setor ou histórico; verifica o layout do painel em viewport de 390 px.
- `./dev evidence-export-test`: passou, 9/9; valida que a captura do workflow só fica completa com os quatro labels definidos.
- A primeira execução encontrou somente problemas no teste: resolução do path relativo da API e duas opções de selects múltiplos simultaneamente presentes. O teste foi ajustado e a execução final passou.
- A captura privada em Documentos e a vinculação dos IDs pela matriz ficam para a revisão central de rastreabilidade; não foram alterados geradores nem relatórios oficiais neste incremento.

## Evidência visual

O cenário aprovado está allowlisted como `process-workflow.spec.ts` + `administrador publica workflow versionado e acompanha o snapshot do processo` e exige os labels `workflow-publicado-v1`, `workflow-publicado-v2`, `processo-snapshot-v1` e `processo-avancado-para-etapa-final`. O teste também verifica 409/403 e ausência de mutação, mas esses resultados negativos são asserções de API, não imagens. As capturas serão exportadas em execução própria do harness após a matriz citar explicitamente o cenário; nenhuma imagem foi inserida no repositório. A captura é complementar e não substitui o teste, laudo ou POC oficial.

## Limites restantes

- A interface ainda não lista processos existentes por assunto; é possível abrir e atualizar por ID apenas dentro da sessão atual. Uma busca/listagem tenant-scoped requer endpoint próprio.
- Não há fechamento, reabertura, workflow com condições, decisões/ramificações, prazos, grupos responsáveis, notificações, exportação ou especificação OpenAPI destes endpoints.
- O usuário pode editar o nome dos estágios e associar setores, mas permissões, memberships e setores ativos são sempre verificados pelo backend. A interface não demonstra todas as corridas de revogação/desativação já cobertas pelo conjunto Testcontainers.
- A tela e o E2E cobrem uma organização e setores de demonstração fictícios; não provam operação municipal, integração institucional ou POC da comissão.
- Os IDs `ED91-0596`/`TR91-0691` permanecem `EM_DESENVOLVIMENTO` até que a matriz confirme a relação desta spec, os critérios literais completos sejam cobertos e a validação externa aplicável ocorra. As obrigações e módulos associados continuam por concluir.
