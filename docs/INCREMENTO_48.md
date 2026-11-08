# Incremento 48 — preservar a shell durante operações de workflow

## Escopo

O estado ocupado de `AdministrativeProcessPanel` agora protege as ações globais que poderiam limpar, recarregar ou abandonar o contexto do servidor:

- seletor de organização;
- botão de acesso (`Entrar e carregar caixa` / `Carregar caixa`);
- atualização da caixa;
- saída da área do servidor.
- navegação pelos links da marca e do Portal do cidadão;
- recarga ou navegação pelo histórico do navegador, por meio da confirmação nativa de `beforeunload`.

Além do estado disabled, `submitStaffAccess()` e `loadStaff()` retornam com orientação antes de limpar mensagens, credenciais, dados carregados ou desmontar o painel. Enquanto ocupado, a marca e o Portal do cidadão são texto estático sem papel ou atributos de link, foco programático, destino ou handlers. O banner global acessível explica que a operação deve terminar antes de navegar. As âncoras comuns voltam após a resposta ser aplicada. O `beforeunload` solicita confirmação do navegador em recarga/volta, e o usuário ainda pode confirmar a saída. O E2E verifica os controles e textos durante abertura, avanço e refresh de detalhe, usando requests reais atrasados que seguem para a API, e verifica a restauração e navegação real ao final.

Corrigidos os locators de `Atualizar` em `protocol-interactions.spec.ts` e `protocol-read-status.spec.ts` para `exact: true`, evitando capturar por engano botões com nomes como `Atualizar lista`.

## Verificação

- `./dev process-workflow-e2e`: **passou (1/1)** após o follow-up; verificou abertura, avanço e refresh de detalhe atrasados, bloqueio dos controles e links durante a requisição real e reabilitação após a resposta.
- Cobertura de navegação: durante os três pedidos reais atrasados, marca e Portal do cidadão são spans sem role, tabindex ou href; o teste clica neles e confirma que a URL não muda, enquanto o banner global informa que a operação está pendente. Também verifica um evento `beforeunload` cancelável. Após a resposta, os elementos voltam a ser âncoras com destino e um clique real navega ao portal público.
- Correção de acessibilidade após revisão: um span com `role="link"` navegou para `/` no clique do Playwright apesar de não ter `href`. O estado pendente agora é texto simples sem semântica ou handlers de navegação; o E2E focal passou 1/1 com esse comportamento.
- `./dev e2e --grep 'conversa de protocolo mantém respostas, anexos e visibilidade no fluxo real'`: **passou (1/1)** após tornar o locator `Atualizar` exato.
- `./dev e2e --grep 'caixa mostra leitura por setor, por outro servidor e pelo usuário atual'`: **passou (1/1)** após tornar o locator `Atualizar` exato.
- `npm run build` em `frontend/`: **passou** após o follow-up (`vue-tsc -b` e `vite build`; 89 módulos transformados).
- `git diff --check`: **passou** após o follow-up; varredura de whitespace nos arquivos do incremento também passou.
- Revisão de interface conforme `frontend-design-review/references/quick-checklist.md`, `DESIGN.md` e tokens: o estado ocupado é texto simples com orientação global acessível; foco e interação retornam com as âncoras após a resposta. Não houve redesign nem validação em Figma/Dev Mode neste ambiente.
- `./dev verify-fast`: **passou** após o patch final — Core 11/11, backend 61/61, build frontend com 89 módulos e matriz íntegra de 1.388 linhas; estado: 8 `VALIDADO`, 45 `EM_DESENVOLVIMENTO`, 1.335 `NAO_INICIADO`.
- `./dev matrix` e `git diff --check`: **passaram** após o patch final.
- `./dev evidence`: **passou**, 8/8 cenários Playwright e 19 screenshots exportados em `/home/exati/Documents/NexoGov-Evidencias/PE-91-2025/20261108T171019Z-1373500/`. O manifesto cobre todos os 1.388 requisitos e relaciona imagens somente a 21 IDs explicitamente referenciados por cenários; 1.347 não têm cenário E2E matriciado e 5 não têm mídia por privacidade.
- Inspeção visual manual: **19/19 PNGs revisados**; nenhum token, segredo, chave do comprovante legível ou dado real foi identificado. O campo da chave aparece como máscara sólida. A pasta e arquivos estão com permissões privadas.
- Revisão independente final: **sem achados** na proteção de navegação, semântica acessível do estado ocupado e precisão das limitações do E2E.

## Limites

As chamadas são mantidas por guardas de UI contra reset ou desmontagem da shell; autorização continua pertencendo ao backend. `beforeunload` produz a confirmação nativa do navegador, que o usuário ainda pode aceitar; o E2E verifica o evento cancelável, não a interface da confirmação nativa. Os E2Es usam API/infraestrutura local descartável e fixtures fictícias, não demonstram IdP municipal, POC oficial, hospedagem ou serviço produtivo. As imagens são complemento para 21 requisitos e não cobrem visualmente os outros 1.367; 1.335 linhas da matriz continuam `NAO_INICIADO`. Nenhuma matriz ou status de aderência foi alterado.

## Arquivos

- `frontend/src/staff/StaffApp.vue`
- `frontend/e2e/process-workflow.spec.ts`
- `frontend/e2e/protocol-interactions.spec.ts`
- `frontend/e2e/protocol-read-status.spec.ts`
- `README.md`
- `AGENTS.md`
- `dev`
- `.agents/skills/nexogov-vertical-slice/SKILL.md`
- `docs/INCREMENTO_47.md` (atualização do escopo de cobertura)
- `docs/INCREMENTO_48.md`
