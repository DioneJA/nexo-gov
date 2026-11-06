# Matriz de aderência — resumo de rastreabilidade

Gerada em 2026-11-06 a partir de `MATRIZ_ADERENCIA.csv`.

## Estado atual

- Total de linhas/requisitos rastreados: **1388**.
- Marcadores do edital §14.1.13 (pp. impressas 13–40) e complementos ED91: **646**.
- Requisitos extraídos do Termo de Referência: **742**; transcrições conferidas visualmente: **742**; ainda pendentes de cotejo visual: **0**. Todos seguem `BLOQUEADO` até definição de aceite, teste e evidência.
- Requisitos não estritamente de software: **218**; operação contratual **109**, integração externa **78**, habilitação **5**, obrigações externas/contratuais **26**.
- Status `VALIDADO`: **0**. Não há alegação de 100% de aderência.

## Contagem por status

| Status | Linhas |
|---|---:|
| `BLOQUEADO` | 742 |
| `EM_DESENVOLVIMENTO` | 10 |
| `NAO_INICIADO` | 636 |

## Cobertura por módulo

| Módulo | Linhas |
|---|---:|
| `ADMINISTRACAO` | 90 |
| `APROVACAO_PROJETOS` | 113 |
| `ASSINATURAS` | 117 |
| `CAPACIDADE_E_LICENCIAMENTO` | 5 |
| `CHAMADO_TECNICO` | 8 |
| `COMUNICACAO_OFICIAL` | 71 |
| `CONTRATUAL` | 16 |
| `E_SIC` | 11 |
| `GESTAO_DOCUMENTAL` | 166 |
| `HABILITACAO_EMPRESARIAL` | 7 |
| `IDENTIDADE_E_SEGURANCA` | 92 |
| `IMPLANTACAO_E_SUPORTE` | 7 |
| `MIGRACAO` | 1 |
| `NOTIFICACOES` | 19 |
| `OPERACAO_E_INTEGRACOES` | 25 |
| `OUVIDORIA` | 66 |
| `PLATAFORMA_E_ARQUITETURA` | 138 |
| `POC` | 1 |
| `POC_E_OPERACAO` | 7 |
| `PORTAL_EXTERNO` | 71 |
| `PRAZOS` | 14 |
| `PROCESSOS_ADMINISTRATIVOS` | 112 |
| `PROCESSO_ADMINISTRATIVO_DISCIPLINAR` | 12 |
| `PROCESSO_SELETIVO_SIMPLIFICADO` | 7 |
| `PROPOSTA_COMERCIAL` | 1 |
| `PROTOCOLO` | 50 |
| `PROTOCOLO_LEGISLATIVO` | 8 |
| `RELATORIOS_E_INDICADORES` | 110 |
| `REQUERIMENTO_PESSOAL` | 20 |
| `SUPORTE` | 11 |
| `TREINAMENTO` | 12 |

## Natureza

| Natureza | Linhas |
|---|---:|
| `EXTERNO/CONTRATUAL` | 26 |
| `HABILITACAO_EMPRESARIAL` | 5 |
| `INTEGRACAO_EXTERNA` | 78 |
| `OPERACAO_CONTRATUAL` | 109 |
| `POC` | 5 |
| `PREFERENCIAL` | 1 |
| `PROPOSTA_COMERCIAL` | 1 |
| `SOFTWARE` | 1163 |

## Regra de aceite

A planilha CSV contém o resumo do requisito, origem documental, prioridade, módulo, natureza, estado de conferência da fonte, critério, backend/frontend, teste, roteiro POC, evidência e status para cada ID. A conferência visual valida a transcrição, não a implementação. Nenhum status é elevado a `VALIDADO` por ter uma tela, endpoint ou documento. Atualize o CSV após cada incremento e execute `scripts/check-matrix.py`.

## Fontes e ressalvas

O edital e a Errata I são PDFs pesquisáveis; o Termo de Referência foi digitalizado. Cada linha `TR91` traz página PDF e bloco de texto para localizar o trecho. A coluna `validacao_fonte` informa se a transcrição foi conferida visualmente; requisitos ainda precisam de decomposição atômica e critérios de aceite. A numeração dos IDs é interna e não altera a numeração oficial dos itens.
