# Incremento 02 — cobertura explícita do Termo de Referência

**Data:** 06/11/2026
**Escopo:** corrigir o inventário de requisitos extraídos do Termo de Referência digitalizado.

## Mudanças

- A conferência visual das páginas 1–4 encontrou itens enumerados que o primeiro extrator ignorava quando a linha não continha verbos de obrigação. O extrator agora inclui alíneas, parágrafos de escopo e objetivos, preserva itens que atravessam páginas e recupera alguns marcadores visivelmente omitidos pelo OCR.
- Foram separados os três tempos de resposta do SLA da alínea 4.d. A matriz mantém itens em estado `BLOQUEADO` até o cotejo visual e a decomposição de obrigações compostas.
- A transcrição OCR continua marcada como material de descoberta, nunca como reprodução oficial revisada.

## Verificação

- O extrator valida as 69 marcações de página, exige as 20 alíneas a–t do §3, verifica a continuação da alínea q entre as páginas 2–3, os três prazos de SLA e frases-chave do §4.
- `python3 scripts/extend-matrix-from-tr-ocr.py` — sucesso; 451 candidatos `TR91` gerados nesta versão.
- `python3 scripts/check-matrix.py` — sucesso; 1.097 linhas totais, 451 `BLOQUEADO`, 10 `EM_DESENVOLVIMENTO`, 636 `NAO_INICIADO`, zero `VALIDADO`.
- `python3 scripts/render-traceability.py` — sucesso; resumo e roteiro individual POC regenerados.
- A revisão visual cobre apenas pp.1–4; pp.5–69 seguem pendentes. O contador de linhas candidatas mudou porque a regra de agrupamento agora privilegia alíneas e itens do documento, sem tratar cada parágrafo OCR anterior como requisito atômico.

## Arquivos

- `scripts/extend-matrix-from-tr-ocr.py`
- `scripts/render-traceability.py`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/POC.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/REQUISITOS.md`
