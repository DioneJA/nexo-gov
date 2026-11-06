# Incremento 05 — Termo de Referência e rastreabilidade visual

**Data:** 06/11/2026  
**Escopo:** conferência visual do Termo de Referência, PDF pp.17–28, e correções do extrator e das saídas de rastreabilidade.

## Mudanças

- Conferidas visualmente as exigências de notificações, anexos e hospedagem, exportação e retenção por 180 dias, inatividade de usuários, transparência, personalização, mapa, auditoria e assuntos (§§17–26).
- Conferidas as seções de busca avançada, contrassenha, marcadores por setor, fluxo Kanban, mapa de demandas e assinatura ICP-Brasil/PAdES (§§27–32).
- Conferidas as exigências antifraude de assinatura e o editor de texto, incluindo autosalvamento, exclusão mútua de edição, variáveis, modelos e referências cruzadas entre documentos e usuários (§§32–33).
- Corrigida a continuação do requisito de corretor ortográfico entre as pp.27–28 e a continuação sobre referência a usuários na p.28.
- Removidos parágrafos introdutórios sem critério independente (por exemplo, “Deverá conter:”) e identificados separadamente os itens da lista de segurança que o OCR leu como letras.
- A coluna `validacao_fonte` separa conferência documental da validação da implementação; `natureza` voltou a classificar o tipo da obrigação, em vez de indicar a qualidade do OCR.
- O roteiro POC agora diferencia candidatos ainda não conferidos de requisitos cuja transcrição foi conferida, mas que não têm aceite, teste e evidência implementados.

## Estado e verificação

- `python3 scripts/extend-matrix-from-tr-ocr.py` — sucesso: 1.355 linhas, incluindo 709 requisitos candidatos do Termo de Referência; 302 transcrições conferidas visualmente e 407 ainda pendentes de cotejo. As 709 linhas do TR continuam `BLOQUEADO` até decomposição e evidência.
- `python3 scripts/check-matrix.py` — sucesso: 709 `BLOQUEADO`, 10 `EM_DESENVOLVIMENTO`, 636 `NAO_INICIADO`, zero `VALIDADO`.
- `python3 scripts/render-traceability.py` — sucesso; resumo de aderência e roteiro POC regenerados.
- A conferência visual do TR alcança as páginas 1–28; páginas 29–69 seguem pendentes. Não houve alteração ou nova validação do fluxo de produto neste incremento documental.

## Arquivos

- `scripts/extend-matrix-from-tr-ocr.py`
- `scripts/check-matrix.py`
- `scripts/render-traceability.py`
- `docs/licitacoes/mata-de-sao-joao-91-2025/REQUISITOS.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/POC.md`
