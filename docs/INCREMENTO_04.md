# Incremento 04 — contatos, rastreabilidade e central de atendimento

**Data:** 06/11/2026
**Escopo:** revisão visual do Termo de Referência, pp.13–16, e separação de requisitos das seções 12–17.

## Mudanças

- Os subtítulos numéricos sem espaço depois do marcador, como `12.` e `17.`, agora iniciam seção corretamente.
- A matriz preserva os requisitos de gestão e importação/exportação de contatos; importação/exportação CSV e importação vCard foram separados em linhas distintas.
- Foram capturados parágrafos de demanda setorial, consulta de leitura, portal da Central de Atendimento, avaliação, reabertura, assinatura/verificação digital e notificações multicanais.
- Todos os candidatos do Termo de Referência continuam `BLOQUEADO` até conferência integral, separação atômica e definição de aceites verificáveis.

## Verificação

- `python3 scripts/extend-matrix-from-tr-ocr.py` — sucesso; verifica 69 páginas, alíneas e continuações anteriores, seções 12–17, funcionalidades de contatos, rastreabilidade, Central de Atendimento e canais de notificação.
- `python3 scripts/check-matrix.py` — sucesso; 1.304 linhas, 658 `BLOQUEADO`, 10 `EM_DESENVOLVIMENTO`, 636 `NAO_INICIADO`, zero `VALIDADO`.
- `python3 scripts/render-traceability.py` — sucesso; resumo da matriz e roteiro individual POC regenerados.
- A conferência visual agora alcança as páginas 1–16. As páginas 17–69 seguem pendentes.

## Arquivos

- `scripts/extend-matrix-from-tr-ocr.py`
- `scripts/render-traceability.py`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/POC.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/REQUISITOS.md`
