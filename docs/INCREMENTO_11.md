# Incremento 11 — árvore de processos, atos oficiais e aprovação de projetos

**Data:** 06/11/2026  
**Escopo:** conferência visual do Termo de Referência, PDF pp.53–56, itens 7–9 do Anexo II.

## Mudanças

- Ampliado o inventário de Processo Administrativo para numeração, anexos obrigatórios, decisões deferido/indeferido, etapas ordenadas, múltiplos setores, SLA, download ZIP, avaliação de anexos e árvores de processo.
- Decompostas as versões completa, personalizada, comprimida e fracionada da árvore PDF, assinaturas verificáveis, paginação/limites por arquivo, processamento assíncrono, atualização de documentos referenciados e inclusão de menções vinculadas.
- Juntada a frase da árvore de processos que atravessa pp.53–54.
- Registrados os Atos Oficiais: elaboração, roteamento, modelos DOCX/PDF-A, assinaturas avançadas/qualificadas e coassinatura ICP-Brasil, consulta externa, opções de numeração e QR Code.
- Iniciado o módulo especializado de aprovação de construção e parcelamento: tipos de projetos e licenças ambientais, alvarás, fluxos, formulários e validação/ajuda de campos.
- Adicionadas verificações para os itens 7–9, incluindo a continuação do Anexo II em pp.53–54.

## Estado e verificação

- `python3 scripts/extend-matrix-from-tr-ocr.py` — sucesso: 1.416 linhas; 770 requisitos candidatos do TR, 610 transcrições visualmente conferidas e 160 candidatos ainda pendentes nas páginas 57–69. Os 770 requisitos do TR permanecem `BLOQUEADO` até aceite, teste e evidência.
- `python3 scripts/render-traceability.py` — sucesso; matriz-resumo e roteiro POC regenerados por requisito.
- `python3 scripts/check-matrix.py` — sucesso; zero requisitos `VALIDADO`.
- `git diff --check` — sem problemas de whitespace.
- A análise do módulo de projetos continua; o inventário nesta etapa cobre somente o trecho até p.56 e não implica implementação ou conformidade.

## Arquivos

- `scripts/extend-matrix-from-tr-ocr.py`
- `README.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/REQUISITOS.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/POC.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/RISCOS.md`
