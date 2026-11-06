# Incremento 03 — segmentação de requisitos de acesso

**Data:** 06/11/2026
**Escopo:** revisão visual do Termo de Referência, pp.5–8, e melhoria da captura de requisitos sob as seções 5.c e 5.d.

## Mudanças

- Os subtítulos “Acesso interno” e “Acesso externo” agora são tratados como seções, e cada parágrafo de requisito fica em linha própria. Antes, o extrator agrupava cada seção inteira numa linha composta.
- Quatro perfis de permissão interna foram separados; os cinco recursos enumerados do portal externo também ganharam linhas próprias.
- A continuação entre as páginas 6–7 permanece unida e os rodapés e marcadores de página foram removidos do texto extraído.
- Todos os candidatos do Termo de Referência seguem `BLOQUEADO`; nenhuma conferência de OCR foi promovida automaticamente a aceite funcional.

## Verificação

- `python3 scripts/extend-matrix-from-tr-ocr.py` — sucesso; valida marcadores de 69 páginas, alíneas do §3, subprazos do SLA, itens técnicos do §4, grupos de permissão do §5.c, recursos do §5.d e continuidade do texto entre páginas.
- `python3 scripts/check-matrix.py` — sucesso; 1.196 linhas, 550 `BLOQUEADO`, 10 `EM_DESENVOLVIMENTO`, 636 `NAO_INICIADO`, zero `VALIDADO`.
- `python3 scripts/render-traceability.py` — sucesso; matriz-resumo e POC individuais regenerados.
- A leitura visual feita neste incremento alcançou as páginas 5–8. Páginas 9–69 seguem pendentes de revisão integral; critérios e decomposição de todas as obrigações ainda precisam ser concluídos.

## Arquivos

- `scripts/extend-matrix-from-tr-ocr.py`
- `scripts/render-traceability.py`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/POC.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/REQUISITOS.md`
