# Incremento 49 — decisões configuráveis em processos administrativos

## Escopo

Cada etapa de uma versão imutável do workflow pode indicar quais setores podem registrar `Deferido` ou `Indeferido`. A política segue no snapshot do processo. A ação exige membership ativa do servidor no setor ativo atualmente atribuído, inclusive quando a conta tem papel ADMIN organizacional; um setor alternativo da mesma etapa não concede permissão.

A decisão é um atributo do processo inteiro e mostra o resultado mais recente. Repetir o resultado atual não duplica histórico; trocar o resultado acrescenta evento `DECIDED`. A decisão não altera a etapa nem fecha o processo. A interface e a lista preservam essa distinção após nova autenticação. A migration V22 adiciona configuração e snapshot de política, resultado/autor/data e o evento auditável.

A origem `ED91-0510` foi corrigida no gerador para referenciar o Termo de Referência §7, PDF p.53 (em vez de p.52). `TR91-0560` mantém a mesma âncora conferida visualmente; os IDs e sua origem não foram substituídos. `TR91-0691` deixa de apontar cobertura OpenAPI incompleta: as operações do controller foram incluídas e o contrato tem testes, enquanto encerramento e POC oficial permanecem fora desta fatia.

## Verificação

- `./dev process-decision-test`: passou, 2 testes Testcontainers; cobriu configuração e snapshot, recusa de setor não permitido, ADMIN sem lotação e membro setorial sem papel ADMIN, dois resultados, retry idempotente, troca auditada, processo ACTIVE, projeção de lista e isolamento por tenant.
- `./dev process-workflow-test`: passou, 18 testes Testcontainers.
- `./dev openapi-check`: passou, 4 testes do contrato.
- `./dev process-workflow-e2e`: passou, 1/1 cenário com API/PostgreSQL reais descartáveis; o teste comprova a política `canDecide` na publicação e no snapshot, estados de shell durante requisições reais, decisão sem encerramento, avanço/recusas e recuperação após recarga e nova autenticação.
- `./dev evidence-export-test`: passou, 14 testes; valida allowlist, labels explícitos, IDs existentes, links de matriz e casos parciais/compostos.
- `./dev matrix-refresh`: passou; a matriz permaneceu com 1.388 IDs: 10 `VALIDADO`, 45 `EM_DESENVOLVIMENTO`, 1.333 `NAO_INICIADO`.
- `./dev matrix`: passou após a regeneração e novamente após o gate integral.
- `./dev evidence`: passou em 8/8 cenários; exportou 22 PNGs para `/home/exati/Documents/NexoGov-Evidencias/PE-91-2025/20261108T183324Z-1765793/`, com permissões restritas. O manifesto associa 23 requisitos a cenários visuais, dos quais 9 têm status `VALIDADO`; 1.345 não têm cenário E2E matriciado e 5 não recebem mídia por privacidade. Esses números descrevem associação visual, não cobertura funcional integral.
- `./dev verify-fast` (dentro da primeira tentativa de `./dev verify`): passou; 11 testes Core, 65 backend, build frontend e matriz.
- A primeira suíte Basic do `./dev verify` encontrou uma colisão entre `protocol-participant-access.spec.ts` e `protocol-participant-fields.spec.ts`: ambos alteravam `Solicitação geral` no banco compartilhado. O teste de acessos passou a criar assunto próprio com sufixo único.
- A segunda suíte Basic executada em 8 workers encontrou outra corrida: `organization-members.spec.ts` suspendeu `servidor.teste` enquanto `protocol-read-status.spec.ts` usava a mesma conta. O trace confirma `403` nos endpoints do setor com `X-Organization-Id` de Município Alfa; a organização estava correta e o vínculo estava suspenso naquele intervalo. O harness agora executa os cenários num worker, serializando suspensão e uso posterior dessa conta.
- `./dev e2e`: passou, 14 cenários Basic, 4 casos OIDC condicionados ao outro modo foram ignorados; duração 1,5 min após serialização.
- `./dev verify`: passou integralmente após ajustar o mock OIDC para responder uma página válida; 11 testes Core, 65 backend, build frontend, matriz, 14 cenários Basic e 7 OIDC. Sem erro de renderização do painel OIDC.
- O browser ainda registra `ResizeObserver loop completed with undelivered notifications` durante cenas Basic; não falhou nenhum teste nem houve erro de renderização associado.
- `git diff --check` e `git diff --cached --check`: passaram após a inspeção visual final e a limpeza dos espaços residuais nos registros de incrementos anteriores.

## Revisão da interface

Revisto contra `.agents/skills/frontend-design-review/references/quick-checklist.md`, `DESIGN.md` e os tokens Core. O painel mantém hierarquia tipográfica NEXO, cores navy/azul contidas, separadores leves, controles Quasar e layout responsivo sem cartões ou métricas inventadas. A captura de configuração encontrou o rótulo do seletor decisor truncado e o cabeçalho fixo sobrepondo o foco; o rótulo foi encurtado para “Setores decisores N” e o helper passou a posicionar o foco abaixo da navegação fixa, mantendo a captura de viewport e `scrollIntoViewIfNeeded()`. A inspeção visual do run final confirmou o seletor completo, o registro de “Deferido” com o processo ainda em andamento e a persistência após recarga/avanço. Os alvos ficam abaixo do cabeçalho fixo; as capturas continuam limitadas ao viewport, e a parte superior de algumas telas não faz parte do recorte, conforme o helper.

Não foi fornecido arquivo Figma ou acesso a Dev Mode nesta fatia; o checklist foi confrontado com os tokens e a implementação disponíveis, sem alegar validação externa de design.

## Limites

O status `VALIDADO` de `ED91-0510` e `TR91-0560` significa apenas que o critério local descrito na matriz tem implementação, testes e evidência interna. Não demonstra participação, aceitação, POC oficial, conformidade integral, IdP municipal ou operação produtiva. O outro resultado, autorização e mudança de decisão têm cobertura backend; a captura de navegador mostra `Deferido`. Regras avançadas, encerramento integrado e demais obrigações do Processo Administrativo permanecem pendentes.

## Artefatos operacionais

- `README.md`, `AGENTS.md` e `dev`: comandos, estados e limites do fluxo atualizados.
- `.agents/skills/nexogov-vertical-slice/SKILL.md`: regra reutilizável para autorização, snapshot, histórico e limites da decisão.
- `scripts/build-edital-matrix.py`, `scripts/extend-matrix-from-tr-ocr.py`, `scripts/export-e2e-evidence.py` e testes: detalhes dos IDs, origem corrigida, labels e mapeamento de evidência.
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv`, `MATRIZ_ADERENCIA.md`, `POC.md`, `REQUISITOS.md` e `RISCOS.md`: artefatos regenerados e contagens reconciliadas.
- `contracts/openapi/nexogov-protocol-v1.yaml` e `backend/src/test/java/br/com/nexo/gov/contract/OpenApiContractTest.java`.
- `backend/src/main/resources/db/migration/V22__administrative_process_decisions.sql`, `backend/src/main/java/br/com/nexo/gov/process/` e `backend/src/test/java/br/com/nexo/gov/process/AdministrativeWorkflowIntegrationTest.java`.
- `frontend/src/staff/AdministrativeProcessPanel.vue` e `frontend/e2e/process-workflow.spec.ts`.
