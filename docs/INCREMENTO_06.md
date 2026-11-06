# Incremento 06 — etiquetas, treinamento e relatórios

**Data:** 06/11/2026  
**Escopo:** conferência visual do Termo de Referência, PDF pp.29–32 (§§33–36), e decomposição das saídas de OCR.

## Mudanças

- Confirmados o acesso a usuários externos referenciados e assinatura por menção (§33), a classificação e busca por etiquetas digitais com escopo por setor (§34), e o EAD com material, avaliações, certificados e progresso individual (§35).
- Separados os indicadores de eficiência, qualidade, engajamento e assiduidade; exportação CSV/PDF; progresso de treinamento; quantitativos por setor; relatórios assíncronos de acessos; estatísticas e consumo de recursos (§36).
- Recuperados itens curtos da lista estatística que o OCR descartava, incluindo contagem de acessos; removidos cabeçalhos e frases introdutórias que não constituem requisitos independentes.
- Unida a continuação do indicador de assiduidade entre as páginas 30–31 e do custo de impressão na p.32.
- Atribuídos os módulos `GESTAO_DOCUMENTAL`, `TREINAMENTO` e `RELATORIOS_E_INDICADORES` conforme as seções visuais do TR.

## Estado e verificação

- `python3 scripts/extend-matrix-from-tr-ocr.py` — sucesso: 1.349 linhas, com 703 requisitos candidatos do Termo de Referência; 341 transcrições conferidas visualmente e 362 pendentes de cotejo. As 703 linhas do TR seguem `BLOQUEADO` até decomposição e evidência.
- `python3 scripts/check-matrix.py` — sucesso: 703 `BLOQUEADO`, 10 `EM_DESENVOLVIMENTO`, 636 `NAO_INICIADO`, zero `VALIDADO`.
- `python3 scripts/render-traceability.py` — sucesso; resumo de aderência e roteiro POC regenerados.
- `git diff --check` — sem problemas de whitespace.
- A conferência visual do TR alcança as páginas 1–32; páginas 33–69 seguem pendentes. O incremento não altera nem valida o fluxo de produto.

## Arquivos

- `scripts/extend-matrix-from-tr-ocr.py`
- `docs/licitacoes/mata-de-sao-joao-91-2025/REQUISITOS.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/POC.md`
