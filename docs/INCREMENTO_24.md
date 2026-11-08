# Incremento 24 — classificação do requisito ED91-0001

## Entrega

O requisito `ED91-0001` descreve o fornecimento de uma plataforma web transversal para comunicação, documentação, atendimento e gerenciamento de atividades, segura, escalável e responsiva (Edital PE 91/2025, §14.1.13, fl. impressa 13). A regra anterior em `module_for()` tratava qualquer ocorrência de `atividade` como `PRAZOS`, embora a frase não descreva prazo ou calendário.

O classificador agora reconhece especificamente “gerenciamento de atividades” sem contexto de prazo/calendário como `PLATAFORMA_E_ARQUITETURA`, módulo já existente e mais fiel à natureza transversal do requisito. A regra ampla remanescente não foi removida nesta fatia para evitar reclassificar outros IDs sem revisão individual. Ao mudar o módulo de uma linha, a regeneração atualiza apenas a rota POC genérica derivada do módulo e preserva rotas POC mantidas manualmente.

O verificador da matriz agora fixa esse caso: exige `ED91-0001` em `PLATAFORMA_E_ARQUITETURA` e verifica que a rota POC genérica corresponde ao módulo. `./dev matrix` já executa esse verificador e `./dev matrix-refresh` também o executa após regenerar os artefatos; não foi necessário alterar o comando do harness.

## Escopo pendente

O gatilho legado por `atividade` ainda ocorre em `ED91-0014`, `ED91-0429` e `ED91-0432`. Eles não foram reclassificados nesta correção estreita; suas naturezas e módulos devem ser revisados individualmente antes de qualquer ajuste adicional, sem inferir um novo requisito.

## Rastreabilidade e diferenças revisadas

- `ED91-0001` manteve ID, transcrição, origem documental e status `NAO_INICIADO`.
- O roteiro mudou de `POC-PRAZOS` para `POC-PLATAFORMA_E_ARQUITETURA`, coerente com o módulo atualizado.
- No resumo por módulo, `PRAZOS` passou de 14 para 13 linhas e `PLATAFORMA_E_ARQUITETURA` de 138 para 139. Nenhum total ou contagem por status mudou.
- Comparação antes/depois confirmou invariância de ID, resumo do requisito, origem e status para as 1.388 linhas.
- O texto oficial, o critério, backend/frontend, teste, evidência e status do requisito não foram alterados.

## Verificações

| Verificação | Resultado |
|---|---|
| `./dev matrix-refresh` | Passou; regenerou 1.388 requisitos e os relatórios derivados. |
| `./dev matrix` | Passou; 1.388 linhas, 31 `EM_DESENVOLVIMENTO`, 1.353 `NAO_INICIADO`, 4 `VALIDADO`, 1.377 IDs numéricos estáveis. |
| `git diff --check` | Passou sem erros de whitespace. |

Não houve mudança em funcionalidade de produto nem nos status; por isso, `REQUISITOS.md`, `RISCOS.md` e `PLANO_IMPLANTACAO.md` não precisaram ser atualizados. O resumo gerado da matriz e a tabela gerada da POC foram conferidos. A skill de rastreabilidade registra a regra de classificação e a preservação de rotas POC manuais. O `dev` já oferecia os comandos necessários, então nenhum novo alvo de harness foi criado.

## Limites

Esta mudança é somente de organização e rastreabilidade; não implementa a capacidade descrita em `ED91-0001` nem valida aderência funcional. Nenhum banco, requisito oficial, `docs/INCREMENTO_23.md`, NexoLar ou ambiente de produção foi alterado. Não houve deploy ou commit.
