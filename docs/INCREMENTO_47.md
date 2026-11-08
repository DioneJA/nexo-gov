# Incremento 47 — proteger a operação de workflow na shell do servidor

## Escopo

Enquanto o painel de processos está abrindo ou avançando um processo, a aplicação impede a troca da organização e a saída da área do servidor. O painel comunica seu estado ocupado ao `StaffApp.vue`; a shell desabilita os dois controles e informa que deve aguardar a confirmação. `logoutStaff()` também recusa uma chamada durante esse estado e apresenta feedback, para evitar que uma mutação confirmada pelo servidor pareça perdida e seja aberta novamente.

O estado inclui também a atualização de detalhe, pois sua resposta atualiza o processo visível. Não altera autorização ou contratos do backend, nem a regra de listagem/detalhe existente.

## Verificação

- `./dev process-workflow-e2e`: **passou (1/1)** na execução inicial; os follow-ups #48 adicionam proteção aos controles e links da shell, refresh de detalhe e sinal `beforeunload`. O E2E atualizado passou 1/1. Ver [INCREMENTO_48.md](INCREMENTO_48.md) para o escopo e os resultados vigentes.
- Build do frontend: **passou** com `vue-tsc -b && vite build`.
- `git diff --check`: **passou**; auditoria de whitespace/final de linha passou nos oito arquivos deste incremento.
- `./dev verify-fast` passou em 2026-11-08 (Core 11/11, backend 61/61, build frontend e matriz); a exportação visual vigente está no pacote `/home/exati/Documents/NexoGov-Evidencias/PE-91-2025/20261108T171019Z-1373500/`.

## Revisão da interface

Revisado contra `frontend-design-review/references/quick-checklist.md`, `DESIGN.md` e os tokens existentes: a mudança mantém o aviso no padrão de feedback da shell, usa estados desabilitados acessíveis dos controles Quasar e não adiciona cores, fontes, ícones ou componentes visuais novos. O cenário real confirmou a orientação de estado e o bloqueio/reabilitação. Não foi possível validar em Figma/Dev Mode ou na biblioteca visual conectada neste ambiente; não houve redesign visual nesta fatia.

## Limites

O E2E usa ambiente local descartável e contas/dados fictícios; não comprova a POC oficial, a autenticação municipal ou a disponibilidade operacional. A proteção é da UI autenticada: operações administrativas externas continuam sujeitas à autorização do servidor. A trilha de workflow permanece sem ação de encerramento integrada.

## Arquivos deste incremento

- `frontend/src/staff/AdministrativeProcessPanel.vue`
- `frontend/src/staff/StaffApp.vue`
- `frontend/e2e/process-workflow.spec.ts`
- `README.md`
- `AGENTS.md`
- `dev`
- `.agents/skills/nexogov-vertical-slice/SKILL.md`
- `docs/INCREMENTO_47.md`

Sem alteração da matriz: o escopo implementa uma proteção de contexto do fluxo já coberto, sem alterar requisito, origem ou status de aderência.
