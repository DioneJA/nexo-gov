# Matriz de aderência — resumo de rastreabilidade

Gerada em 2026-11-06 a partir de `MATRIZ_ADERENCIA.csv`.

## Estado atual

- Total de linhas/requisitos rastreados: **1341**.
- Marcadores do edital §14.1.13 (pp. impressas 13–40) e complementos ED91: **646**.
- Requisitos extraídos do Termo de Referência: **695**; transcrições conferidas visualmente: **414**; ainda pendentes de cotejo visual: **281**. Todos seguem `BLOQUEADO` até definição de aceite, teste e evidência.
- Requisitos não estritamente de software: **198**; operação contratual **117**, integração externa **60**, habilitação **4**, obrigações externas/contratuais **17**.
- Status `VALIDADO`: **0**. Não há alegação de 100% de aderência.

## Contagem por status

| Status | Linhas |
|---|---:|
| `BLOQUEADO` | 695 |
| `EM_DESENVOLVIMENTO` | 10 |
| `NAO_INICIADO` | 636 |

## Cobertura por módulo

| Módulo | Linhas |
|---|---:|
| `ADMINISTRACAO` | 91 |
| `APROVACAO_PROJETOS` | 93 |
| `ASSINATURAS` | 117 |
| `CAPACIDADE_E_LICENCIAMENTO` | 5 |
| `CHAMADO_TECNICO` | 8 |
| `COMUNICACAO_OFICIAL` | 54 |
| `CONTRATUAL` | 7 |
| `E_SIC` | 10 |
| `GESTAO_DOCUMENTAL` | 179 |
| `HABILITACAO_EMPRESARIAL` | 3 |
| `IDENTIDADE_E_SEGURANCA` | 92 |
| `IMPLANTACAO_E_SUPORTE` | 8 |
| `MIGRACAO` | 1 |
| `NOTIFICACOES` | 19 |
| `OPERACAO_E_INTEGRACOES` | 24 |
| `OUVIDORIA` | 58 |
| `PLATAFORMA_E_ARQUITETURA` | 204 |
| `POC` | 1 |
| `POC_E_OPERACAO` | 7 |
| `PORTAL_EXTERNO` | 42 |
| `PRAZOS` | 14 |
| `PROCESSOS_ADMINISTRATIVOS` | 93 |
| `PROCESSO_ADMINISTRATIVO_DISCIPLINAR` | 12 |
| `PROCESSO_SELETIVO_SIMPLIFICADO` | 6 |
| `PROTOCOLO` | 35 |
| `PROTOCOLO_LEGISLATIVO` | 8 |
| `RELATORIOS_E_INDICADORES` | 109 |
| `REQUERIMENTO_PESSOAL` | 18 |
| `SUPORTE` | 11 |
| `TREINAMENTO` | 12 |

## Natureza

| Natureza | Linhas |
|---|---:|
| `EXTERNO/CONTRATUAL` | 17 |
| `HABILITACAO_EMPRESARIAL` | 4 |
| `INTEGRACAO_EXTERNA` | 60 |
| `OPERACAO_CONTRATUAL` | 117 |
| `POC` | 5 |
| `PREFERENCIAL` | 1 |
| `SOFTWARE` | 1137 |

## Regra de aceite

A planilha CSV contém o resumo do requisito, origem documental, prioridade, módulo, natureza, estado de conferência da fonte, critério, backend/frontend, teste, roteiro POC, evidência e status para cada ID. A conferência visual valida a transcrição, não a implementação. Nenhum status é elevado a `VALIDADO` por ter uma tela, endpoint ou documento. Atualize o CSV após cada incremento e execute `scripts/check-matrix.py`.

## Fontes e ressalvas

O edital e a Errata I são PDFs pesquisáveis; o Termo de Referência foi digitalizado. Cada linha `TR91` traz página PDF e bloco de texto para localizar o trecho. A coluna `validacao_fonte` informa se a transcrição foi conferida visualmente; requisitos ainda precisam de decomposição atômica e critérios de aceite. A numeração dos IDs é interna e não altera a numeração oficial dos itens.
