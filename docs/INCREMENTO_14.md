# Incremento 14 — fechamento visual do Termo de Referência

## Entrega

- Conferidas visualmente as páginas 65–69 do Termo de Referência oficial. Com isso, as 69 páginas do scan foram cotejadas com o OCR de descoberta.
- A matriz inclui os requisitos dos módulos de processo seletivo simplificado, protocolo legislativo, requerimento pessoal e chamado técnico, além da qualificação técnica empresarial.
- Incluída como obrigação de proposta comercial a planilha de composição de custos do Anexo III, com mão de obra, tributos, insumos, frete, lucro/BDI, outros e soma de 100%. Os valores estimados da planilha de preços continuam preservados como referência financeira, sem serem tratados como requisitos de software.
- Corrigido o reconhecimento de títulos numerados para não promover itens de lista e citações legais a seções. Corrigido também o classificador que associava a substring “sla” dentro da palavra “legislativo” à natureza operacional. Um fragmento ilegível de OCR e o título editorial “Observações complementares” foram removidos da lista de requisitos.
- Atualizados o CSV, os relatórios Markdown da matriz e da POC, o inventário, os riscos e o índice de incrementos.

## Estado verificável

- 1.388 linhas rastreadas: 646 requisitos do edital e errata, 742 candidatos do Termo de Referência.
- Fonte: 646 linhas conferidas no documento oficial pesquisável e 742 transcrições do Termo de Referência conferidas visualmente. Nenhum candidato do TR permanece apenas em OCR.
- Status: 742 `BLOQUEADO`, 10 `EM_DESENVOLVIMENTO`, 636 `NAO_INICIADO`; 0 de 1.388 `VALIDADO`.
- O bloqueio do TR agora é de decomposição funcional e comprovação de produto, teste ou operação, não de leitura da fonte.

## Validação executada

- `python3 scripts/extend-matrix-from-tr-ocr.py` — sucesso; reproduziu 1.388 linhas e 742 transcrições do TR visualmente conferidas.
- `python3 scripts/render-traceability.py` — sucesso; regenerou os relatórios por requisito e cenários POC.
- `python3 scripts/check-matrix.py` — sucesso; IDs e campos rastreados, estados e contagens consistentes.

## Arquivos alterados

- `scripts/extend-matrix-from-tr-ocr.py`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/POC.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/REQUISITOS.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/RISCOS.md`
- `README.md`

## Lacunas remanescentes

Este incremento encerra a revisão visual das fontes do edital e do Termo de Referência. Não transforma os candidatos em requisitos validados do produto: ainda faltam decomposição atômica, critérios observáveis, atribuição de backend e frontend, testes específicos, evidências POC e comprovação das obrigações empresariais e contratuais. Nenhum deploy foi feito. O NexoLar original permaneceu intocado.
