# Incremento 12 — detalhamento da aprovação de projetos

**Data:** 06/11/2026  
**Escopo:** conferência visual do Termo de Referência, PDF pp.57–60, continuação do item 9 do Anexo II.

## Mudanças

- Inventariados cálculos e validações para CNAE, parâmetros urbanísticos, CPF/CNPJ, medição dimensional em PDF, escalas, precisão, anotações e selos de aprovação.
- Separados estados de deferimento/indeferimento e restrições de visualização pública enquanto o processo estiver pendente ou indeferido.
- Registrados protocolo integralmente digital, submissão de PDFs/DWG, classificação obrigatória de arquivos, consulta externa sem login e rastreamento de leitura/download.
- Mapeados formulários/requerimentos, papéis dos participantes técnicos/proprietário, cadastro externo, confirmações por e-mail, inbox por setor e análise/rejeição motivada de anexos com preservação do original.
- Incluídos recursos de anotação de desenhos, linha do tempo, emissão/assinatura/QR e compilação do protocolo em PDF.
- Classificada a transmissão de Alvarás e Habite-se pelo Sisobra, incluindo ICP-Brasil, como integração externa; não há credenciais, certificado nem homologação neste ambiente.
- Estendidos os controles automáticos do extrator para todo o conjunto específico das páginas 57–60.

## Estado e verificação

- `python3 scripts/extend-matrix-from-tr-ocr.py` — sucesso: 1.416 linhas; 770 requisitos candidatos do TR, 659 transcrições visualmente conferidas e 111 pendentes nas páginas 61–69. Os 770 requisitos do TR continuam `BLOQUEADO` até critérios, teste e evidência de produto/operação.
- `python3 scripts/render-traceability.py` — sucesso; matriz-resumo e roteiro POC regenerados por requisito.
- `python3 scripts/check-matrix.py` — sucesso; zero requisitos `VALIDADO`.
- `git diff --check` — sem problemas de whitespace.
- O módulo de aprovação de projetos é extenso e especializado. Este incremento não o reduz a formulário genérico nem afirma que suas funções estejam implementadas.

## Arquivos

- `scripts/extend-matrix-from-tr-ocr.py`
- `README.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/REQUISITOS.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/POC.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/RISCOS.md`
