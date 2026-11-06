# Incremento 10 — protocolo, e-SIC, ofícios e processos

**Data:** 06/11/2026  
**Escopo:** conferência visual do Termo de Referência, PDF pp.49–52, continuação dos itens 4–7 do Anexo II.

## Mudanças

- Inventariados no Protocolo Eletrônico a lista/calendário, ordenação, histórico, consulta QR/token, distribuição, resolução setorial, conclusão/reversão, anexos, prazos, alertas, identificação ICP-Brasil/Gov.br, georreferenciamento, pessoas relacionadas, correção de assunto, avaliação e recebimento físico.
- Juntada explicitamente a continuação entre p.49–50 sobre anexos obrigatórios/opcionais por assunto; a continuação da avaliação após reabertura foi ligada entre pp.50–51.
- Separados os requisitos do e-SIC, incluindo LAI, canais de entrada, encaminhamento, anexos, categorias e indicadores públicos.
- Inventariados os ofícios eletrônicos, envio rastreado, SMS, respostas, histórico, contato comum, numeração externa/interna e QR Code.
- Decomposto o início de Processo Administrativo, incluindo assunto/setor inicial, campos configuráveis/obrigatórios, ajuda, CEP, CPF/CNPJ, cálculos, modelos e documentos complementares.
- Adicionadas verificações no extrator para os itens 4–7 do Anexo II e para as continuações de página identificadas visualmente.

## Estado e verificação

- `python3 scripts/extend-matrix-from-tr-ocr.py` — sucesso: 1.416 linhas; 770 requisitos candidatos do TR, 559 transcrições visualmente conferidas e 211 ainda pendentes nas páginas 53–69. Os 770 itens do TR continuam `BLOQUEADO` até critérios, testes e evidências.
- `python3 scripts/render-traceability.py` — sucesso; matriz-resumo e roteiro POC regenerados por requisito.
- `python3 scripts/check-matrix.py` — sucesso; zero requisitos `VALIDADO`.
- `git diff --check` — sem problemas de whitespace.
- Este incremento não implementa os módulos e integrações inventariados; registra texto oficial e status de implementação ainda pendente.

## Arquivos

- `scripts/extend-matrix-from-tr-ocr.py`
- `README.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/REQUISITOS.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/POC.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/RISCOS.md`
