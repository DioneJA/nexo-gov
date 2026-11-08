# Incremento 41 — campos de identificação em painéis estreitos

**Data:** 08/11/2026
**Escopo:** corrigir a compressão dos controles na configuração administrativa de campos de participantes quando a coluna lateral fica estreita em desktop. É um ajuste de interface; não amplia a funcionalidade do módulo nem altera a matriz do edital.

## Implementação

- O painel de campos usa uma container query, porque sua largura depende da coluna administrativa e não apenas da janela do navegador.
- Abaixo de 520 px no painel, os campos existentes e o formulário de criação passam a uma coluna; isso mantém rótulos e valores legíveis na coluna lateral. As ações podem quebrar linha sem sair dos limites do painel.
- O teste Playwright agora roda em janela desktop e verifica, no fluxo contra a API real, que os elementos da linha cabem sem rolagem horizontal ou ultrapassar os limites do painel.

## Verificações e rastreabilidade

- `./dev participant-fields-e2e`: passou, 1/1; o caso foi executado contra a API real, PostgreSQL temporário e Chrome.
- `npm run build` em `frontend/`: passou com `vue-tsc -b` e build de produção do Vite.
- `./dev evidence`: passou 5/5 cenários e exportou 7 PNGs para `Documents/NexoGov-Evidencias/PE-91-2025/20261108T144748Z-597412/`.
- `./dev evidence-export-test`: passou, 7/7; manifesto, hashes, permissões privadas e arquivos foram validados pelo exportador.
- `./dev matrix`: passou com 1.388 requisitos; `git diff --check`: passou.
- O novo PNG foi inspecionado visualmente: identificador estável e controles agora cabem no painel estreito. Essa imagem é artefato técnico sem ID de edital; ela não é usada para alegar aderência.
- A matriz do Pregão e o roteiro POC não mudam: este ajuste não satisfaz nem valida um novo critério do edital.
- Harness: `./dev participant-fields-e2e` já cobre essa tela; a descrição do comando foi ajustada para incluir o caso de painel estreito, sem criar um comando redundante.
- Skill: `nexogov-design-system` registra a regra de usar container queries quando a largura real do painel diverge da viewport.
- Este documento registra o desvio visual encontrado na revisão da captura anterior. A captura anterior permanece preservada como evidência daquela execução; uma nova captura só será publicada em outra execução aprovada.
- Recaptura posterior em `20261108T151454Z-751368` validou o painel atualizado junto de cinco outros cenários, no total de 6/6 cenários e 10 PNGs, com o hash da matriz final no manifesto. A imagem atual do formulário em coluna única está nesse pacote; detalhes e limites estão em `docs/INCREMENTO_42.md`.

### Revisão de interface

- Direção: preservar o padrão Apple HIG-inspired do NEXO, hierarquia nítida, controles simples, tipografia do sistema, cores contidas e tokens existentes.
- Checklist: alinhamento e reflow revistos no screenshot real; rótulos e valores estão legíveis, ações mantêm texto, o teste prova ausência de overflow em painel de até 520 px e o fluxo pode ser completado por teclado. Não foram acrescentados ícones sem rótulo, cores de estado novas nem superfícies decorativas.
- A única mudança visual é a composição responsiva do painel de configuração; paleta, marca e demais padrões permanecem inalterados.

## Evidência visual

- Captura anterior: screenshot de engenharia em `Documents/NexoGov-Evidencias/PE-91-2025/20261108T144009Z-554850/`, sem ID associado na matriz. A coluna dos campos estava visualmente comprimida; não deve ser tratada como evidência do layout corrigido.
- Captura intermediária: `Documents/NexoGov-Evidencias/PE-91-2025/20261108T144748Z-597412/midia/protocol-participant-fields--administrador-configura-e-mantem-campos-de-identificacao-por-assunto--configuracao-persistida.png`; esse layout usava duas colunas e foi substituído pela captura posterior em coluna única. O pacote atual é `Documents/NexoGov-Evidencias/PE-91-2025/20261108T151454Z-751368/midia/protocol-participant-fields--administrador-configura-e-mantem-campos-de-identificacao-por-assunto--configuracao-persistida.png`.

## Resultado

- Interface corrigida e verificada em coluna única no painel estreito. A captura anterior permanece como histórico; a recaptura mais recente registra o layout atual.
- Próxima fatia identificada por auditoria read-only: indicador de leitura na caixa do setor, limitado à distinção entre ninguém leu, outro servidor do setor leu e o usuário atual leu. As referências literais são ED91-0120 (edital fl. 17, §14.1.13) / TR91-0125 (Termo de Referência §10, PDF p. 12) e ED91-0140 (edital fl. 18, §14.1.13) / TR91-0145 (§15, PDF p. 14). Isso não cobre trilha de quem leu, download ou consulta individual, que pertencem a outros critérios.
