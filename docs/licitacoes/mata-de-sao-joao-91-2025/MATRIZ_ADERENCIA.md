# Matriz de aderência — resumo de rastreabilidade

Gerada em 2026-11-06 a partir de `MATRIZ_ADERENCIA.csv`.

## Estado atual

- Total de linhas/requisitos rastreados: **1097**.
- Marcadores do edital §14.1.13 (pp. impressas 13–40) e complementos ED91: **646**.
- Parágrafos e alíneas candidatos do Termo de Referência via OCR não conferido: **451**, todos `BLOQUEADO`.
- Requisitos contratuais/empresariais fora do escopo exclusivo de software: **11**.
- Status `VALIDADO`: **0**. Não há alegação de 100% de aderência.

## Contagem por status

| Status | Linhas |
|---|---:|
| `BLOQUEADO` | 451 |
| `EM_DESENVOLVIMENTO` | 10 |
| `NAO_INICIADO` | 636 |

## Cobertura por módulo

| Módulo | Linhas |
|---|---:|
| `ADMINISTRACAO` | 91 |
| `APROVACAO_PROJETOS` | 91 |
| `ASSINATURAS` | 97 |
| `CAPACIDADE_E_LICENCIAMENTO` | 5 |
| `CHAMADO_TECNICO` | 4 |
| `COMUNICACAO_OFICIAL` | 55 |
| `CONTRATUAL` | 1 |
| `E_SIC` | 10 |
| `GESTAO_DOCUMENTAL` | 162 |
| `HABILITACAO_EMPRESARIAL` | 3 |
| `IDENTIDADE_E_SEGURANCA` | 76 |
| `IMPLANTACAO_E_SUPORTE` | 2 |
| `MIGRACAO` | 1 |
| `NOTIFICACOES` | 17 |
| `OPERACAO_E_INTEGRACOES` | 17 |
| `OUVIDORIA` | 53 |
| `PLATAFORMA_E_ARQUITETURA` | 163 |
| `POC` | 1 |
| `POC_E_OPERACAO` | 2 |
| `PORTAL_EXTERNO` | 31 |
| `PRAZOS` | 14 |
| `PROCESSOS_ADMINISTRATIVOS` | 91 |
| `PROCESSO_ADMINISTRATIVO_DISCIPLINAR` | 2 |
| `PROCESSO_SELETIVO_SIMPLIFICADO` | 3 |
| `PROTOCOLO` | 36 |
| `PROTOCOLO_LEGISLATIVO` | 4 |
| `RELATORIOS_E_INDICADORES` | 42 |
| `REQUERIMENTO_PESSOAL` | 15 |
| `SUPORTE` | 1 |
| `TREINAMENTO` | 7 |

## Natureza

| Natureza | Linhas |
|---|---:|
| `CANDIDATO OCR PT NÃO CONFERIDO` | 451 |
| `EXTERNO/CONTRATUAL` | 11 |
| `HABILITACAO_EMPRESARIAL` | 1 |
| `OPERACAO_CONTRATUAL` | 59 |
| `PREFERENCIAL` | 1 |
| `SOFTWARE` | 574 |

## Regra de aceite

A planilha CSV contém o resumo do requisito, origem documental, prioridade, módulo, natureza, critério, backend/frontend, teste, roteiro POC, evidência e status para cada ID. Requisitos com OCR não validado permanecem bloqueados; nenhum status é elevado a `VALIDADO` por ter uma tela, endpoint ou documento. Atualize o CSV após cada incremento e execute `scripts/check-matrix.py`.

## Fontes e ressalvas

O edital e a Errata I são PDFs pesquisáveis; o Termo de Referência foi digitalizado. Cada linha `TR91` traz página PDF e bloco OCR para localizar o trecho, mas exige comparação visual com `edital-original/TERMODEREFERENCIAEJUSTIFICATIVAPE912025.pdf`. A numeração dos IDs é interna e não altera a numeração oficial dos itens.
