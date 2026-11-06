# Matriz de aderência — resumo de rastreabilidade

Gerada em 2026-11-06 a partir de `MATRIZ_ADERENCIA.csv`.

## Estado atual

- Total de linhas/requisitos rastreados: **1140**.
- Marcadores do edital §14.1.13 (pp. impressas 13–40) e complementos ED91: **646**.
- Parágrafos candidatos do Termo de Referência via OCR não conferido: **494**, todos `BLOQUEADO`.
- Requisitos contratuais/empresariais fora do escopo exclusivo de software: **11**.
- Status `VALIDADO`: **0**. Não há alegação de 100% de aderência.

## Contagem por status

| Status | Linhas |
|---|---:|
| `BLOQUEADO` | 494 |
| `EM_DESENVOLVIMENTO` | 10 |
| `NAO_INICIADO` | 636 |

## Cobertura por módulo

| Módulo | Linhas |
|---|---:|
| `ADMINISTRACAO` | 84 |
| `APROVACAO_PROJETOS` | 91 |
| `ASSINATURAS` | 135 |
| `CAPACIDADE_E_LICENCIAMENTO` | 1 |
| `CHAMADO_TECNICO` | 3 |
| `COMUNICACAO_OFICIAL` | 51 |
| `CONTRATUAL` | 1 |
| `E_SIC` | 10 |
| `GESTAO_DOCUMENTAL` | 89 |
| `HABILITACAO_EMPRESARIAL` | 3 |
| `IDENTIDADE_E_SEGURANCA` | 70 |
| `IMPLANTACAO_E_SUPORTE` | 1 |
| `MIGRACAO` | 1 |
| `NOTIFICACOES` | 13 |
| `OPERACAO_E_INTEGRACOES` | 12 |
| `OUVIDORIA` | 50 |
| `PLATAFORMA_E_ARQUITETURA` | 291 |
| `POC` | 1 |
| `POC_E_OPERACAO` | 2 |
| `PORTAL_EXTERNO` | 31 |
| `PRAZOS` | 14 |
| `PROCESSOS_ADMINISTRATIVOS` | 88 |
| `PROTOCOLO` | 35 |
| `PROTOCOLO_LEGISLATIVO` | 4 |
| `RELATORIOS_E_INDICADORES` | 42 |
| `REQUERIMENTO_PESSOAL` | 9 |
| `SUPORTE` | 1 |
| `TREINAMENTO` | 7 |

## Natureza

| Natureza | Linhas |
|---|---:|
| `CANDIDATO OCR PT NÃO CONFERIDO` | 494 |
| `EXTERNO/CONTRATUAL` | 11 |
| `HABILITACAO_EMPRESARIAL` | 1 |
| `OPERACAO_CONTRATUAL` | 59 |
| `PREFERENCIAL` | 1 |
| `SOFTWARE` | 574 |

## Regra de aceite

A planilha CSV contém o resumo do requisito, origem documental, prioridade, módulo, natureza, critério, backend/frontend, teste, roteiro POC, evidência e status para cada ID. Requisitos com OCR não validado permanecem bloqueados; nenhum status é elevado a `VALIDADO` por ter uma tela, endpoint ou documento. Atualize o CSV após cada incremento e execute `scripts/check-matrix.py`.

## Fontes e ressalvas

O edital e a Errata I são PDFs pesquisáveis; o Termo de Referência foi digitalizado. Cada linha `TR91` traz página PDF e bloco OCR para localizar o trecho, mas exige comparação visual com `edital-original/TERMODEREFERENCIAEJUSTIFICATIVAPE912025.pdf`. A numeração dos IDs é interna e não altera a numeração oficial dos itens.
