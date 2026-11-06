# Incremento 09 — circular e ouvidoria

**Data:** 06/11/2026  
**Escopo:** conferência visual do Termo de Referência, PDF pp.45–48, continuação do Anexo II, itens 1–4.

## Mudanças

- Incluída a continuação do item 1 sobre linha do tempo e estado de resolução por setor.
- Decompostos os recursos da Circular: seleção por organograma, prioridade, leitura, respostas abertas/restritas, anexos, prazos, arquivamento, notificações e encaminhamento.
- Separadas as cinco finalidades da Ouvidoria (denúncia, elogio, reclamação, solicitação e sugestão) com marcadores estáveis para evitar que a corrupção dos marcadores pelo OCR pareça uma única alínea.
- Separadas as modalidades sem sigilo, sigilosa e anônima; mantidos os requisitos de privacidade, código externo e acompanhamento.
- Inventariados geolocalização, listagem/calendário/mapa, filtros, movimentações, direcionamento e recategorização, comprovante, resolução setorial, decisão final e avaliação do atendimento.
- Registrada a abertura e consulta de Protocolo Eletrônico por portal ou servidor, em lista ou calendário.
- Adicionadas verificações para continuidade de texto entre as páginas 47–48, as cinco finalidades e as três modalidades de identificação.

## Estado e verificação

- `python3 scripts/extend-matrix-from-tr-ocr.py` — sucesso: 1.417 linhas; 771 requisitos candidatos do TR, 502 transcrições visualmente conferidas e 269 ainda pendentes nas páginas 49–69. Os 771 itens do TR continuam `BLOQUEADO` até decomposição, aceite, vínculo com testes e evidência do produto/operação.
- `python3 scripts/render-traceability.py` — sucesso; matriz-resumo e roteiro POC regenerados por requisito.
- `python3 scripts/check-matrix.py` — sucesso; zero requisitos `VALIDADO`.
- `git diff --check` — sem problemas de whitespace.
- Este incremento registra requisitos de fonte; não implementa a Circular, Ouvidoria ou o módulo completo de Protocolo Eletrônico.

## Arquivos

- `scripts/extend-matrix-from-tr-ocr.py`
- `README.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/REQUISITOS.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/POC.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/RISCOS.md`
