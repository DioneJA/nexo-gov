# Incremento 08 — painéis, obrigações e Anexo II

**Data:** 06/11/2026  
**Escopo:** conferência visual do Termo de Referência, PDF pp.41–44 (§§43–45 e Anexo II, item 1).

## Mudanças

- Separadas as métricas finais do painel: inatividade nominal, processos criados/recebidos e resolvidos, páginas/documentos nativos, gráficos de tramitação e arquivamento, tempo médio de resolução e assinaturas (§43).
- Registradas as atualizações sem interrupção e as janelas de manutenção com aviso mínimo de uma semana (§44).
- Separadas e restauradas as obrigações da contratada das alíneas a–i do §45, incluindo preposto, encargos, manutenção de habilitação, SLA, correção de falhas e fiscalização.
- O Anexo II passou a ter contexto próprio de origem; o item 1 de comunicação interna foi decomposto por frases para manter rastreáveis numeração, QR Code, editor, tramitação, privacidade, folha de rosto, organograma, anexos, modelos, PDF, prazos e setores envolvidos.
- Adicionados controles no extrator para conferir as métricas do §43, o conjunto de alíneas do §45 e os requisitos observáveis do item 1 do Anexo II.

## Estado e verificação

- `python3 scripts/extend-matrix-from-tr-ocr.py` — sucesso: 1.417 linhas, 771 requisitos candidatos do TR, dos quais 451 transcrições estão conferidas visualmente e 320 permanecem pendentes nas páginas 45–69. Todos os requisitos do TR continuam `BLOQUEADO` para aceite, teste e evidência de produto/operação.
- `python3 scripts/render-traceability.py` — sucesso; resumo da matriz e roteiro POC regenerados por requisito.
- `python3 scripts/check-matrix.py` — sucesso: 771 `BLOQUEADO`, 10 `EM_DESENVOLVIMENTO`, 636 `NAO_INICIADO`, zero `VALIDADO`.
- `git diff --check` — sem problemas de whitespace.
- Este incremento documenta a fonte e o trabalho de rastreabilidade; não implementa o painel, comunicação interna, operação contratual ou os demais módulos do Anexo II.

## Arquivos

- `scripts/extend-matrix-from-tr-ocr.py`
- `README.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/REQUISITOS.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/POC.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/RISCOS.md`
