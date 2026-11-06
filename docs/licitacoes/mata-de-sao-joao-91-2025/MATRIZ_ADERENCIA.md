# Matriz de aderência — resumo de rastreabilidade

Gerada em 2026-11-06 a partir de `MATRIZ_ADERENCIA.csv`.

## Estado atual

- Total de linhas/requisitos rastreados: **1355**.
- Marcadores do edital §14.1.13 (pp. impressas 13–40) e complementos ED91: **646**.
- Requisitos extraídos do Termo de Referência: **709**; transcrições conferidas visualmente: **302**; ainda pendentes de cotejo visual: **407**. Todos seguem `BLOQUEADO` até definição de aceite, teste e evidência.
- Requisitos não estritamente de software (serviço/operação, obrigação externa/contratual ou habilitação empresarial): **125**; operação contratual **110**, habilitação **4**, obrigações externas/contratuais **11**.
- Status `VALIDADO`: **0**. Não há alegação de 100% de aderência.

## Contagem por status

| Status | Linhas |
|---|---:|
| `BLOQUEADO` | 709 |
| `EM_DESENVOLVIMENTO` | 10 |
| `NAO_INICIADO` | 636 |

## Cobertura por módulo

| Módulo | Linhas |
|---|---:|
| `ADMINISTRACAO` | 91 |
| `APROVACAO_PROJETOS` | 93 |
| `ASSINATURAS` | 120 |
| `CAPACIDADE_E_LICENCIAMENTO` | 5 |
| `CHAMADO_TECNICO` | 8 |
| `COMUNICACAO_OFICIAL` | 56 |
| `CONTRATUAL` | 1 |
| `E_SIC` | 10 |
| `GESTAO_DOCUMENTAL` | 204 |
| `HABILITACAO_EMPRESARIAL` | 3 |
| `IDENTIDADE_E_SEGURANCA` | 95 |
| `IMPLANTACAO_E_SUPORTE` | 18 |
| `MIGRACAO` | 1 |
| `NOTIFICACOES` | 21 |
| `OPERACAO_E_INTEGRACOES` | 19 |
| `OUVIDORIA` | 58 |
| `PLATAFORMA_E_ARQUITETURA` | 269 |
| `POC` | 1 |
| `POC_E_OPERACAO` | 2 |
| `PORTAL_EXTERNO` | 42 |
| `PRAZOS` | 14 |
| `PROCESSOS_ADMINISTRATIVOS` | 95 |
| `PROCESSO_ADMINISTRATIVO_DISCIPLINAR` | 12 |
| `PROCESSO_SELETIVO_SIMPLIFICADO` | 6 |
| `PROTOCOLO` | 35 |
| `PROTOCOLO_LEGISLATIVO` | 8 |
| `RELATORIOS_E_INDICADORES` | 42 |
| `REQUERIMENTO_PESSOAL` | 18 |
| `SUPORTE` | 1 |
| `TREINAMENTO` | 7 |

## Natureza

| Natureza | Linhas |
|---|---:|
| `EXTERNO/CONTRATUAL` | 11 |
| `HABILITACAO_EMPRESARIAL` | 4 |
| `OPERACAO_CONTRATUAL` | 110 |
| `POC` | 5 |
| `PREFERENCIAL` | 1 |
| `SOFTWARE` | 1224 |

## Regra de aceite

A planilha CSV contém o resumo do requisito, origem documental, prioridade, módulo, natureza, estado de conferência da fonte, critério, backend/frontend, teste, roteiro POC, evidência e status para cada ID. A conferência visual valida a transcrição, não a implementação. Nenhum status é elevado a `VALIDADO` por ter uma tela, endpoint ou documento. Atualize o CSV após cada incremento e execute `scripts/check-matrix.py`.

## Fontes e ressalvas

O edital e a Errata I são PDFs pesquisáveis; o Termo de Referência foi digitalizado. Cada linha `TR91` traz página PDF e bloco de texto para localizar o trecho. A coluna `validacao_fonte` informa se a transcrição foi conferida visualmente; requisitos ainda precisam de decomposição atômica e critérios de aceite. A numeração dos IDs é interna e não altera a numeração oficial dos itens.
