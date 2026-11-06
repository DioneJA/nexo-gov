# Incremento 13 — Sisobra, aplicativo móvel, workflow e PAD

**Data:** 06/11/2026  
**Escopo:** conferência visual do Termo de Referência, PDF pp.61–64, continuação dos itens 9–14 do Anexo II.

## Mudanças

- Completados requisitos de retificação, cancelamento, reativação, retorno de erros e histórico de transmissões do Sisobra.
- Inventariado o aplicativo móvel nativo Android/iOS, seleção de organização por proximidade, identidade visual, integração com a plataforma web, login Gov.br, cadastro, inbox, consulta e abertura de solicitações.
- Separados requisitos resumidos de exportação/workflow por assunto, ordenação de anexos e assinatura digital em lote.
- Decomposto o Processo Administrativo Disciplinar: roteamento por tipo de infração, campos conforme legislação municipal, ajuda, CPF/CNPJ, modelos, documentos, anexos, número e prazos.
- Ajustado o leitor de cabeçalhos: referências legais numéricas deixam de ser interpretadas como novas seções, preservando o restante das especificações móveis sob Anexo II, item 10.
- Classificado Gov.br como dependência de integração externa e o aplicativo móvel no módulo de portal externo.

## Estado e verificação

- `python3 scripts/extend-matrix-from-tr-ocr.py` — sucesso: 1.391 linhas; 745 requisitos candidatos do TR, 713 transcrições visualmente conferidas e 32 candidatos pendentes nas páginas 65–69. Os requisitos continuam `BLOQUEADO` até critérios, testes e evidências.
- `python3 scripts/render-traceability.py` — sucesso; matriz-resumo e roteiro POC regenerados por requisito.
- `python3 scripts/check-matrix.py` — sucesso; zero requisitos `VALIDADO`.
- `git diff --check` — sem problemas de whitespace.
- Apenas as páginas 61–64 foram cotejadas nesta etapa; requisitos da POC, do produto e de dependências externas seguem sem prova de conformidade.

## Arquivos

- `scripts/extend-matrix-from-tr-ocr.py`
- `README.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/REQUISITOS.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/POC.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/RISCOS.md`
