# Matriz de aderência — resumo de rastreabilidade

Gerada em 2026-11-08 a partir de `MATRIZ_ADERENCIA.csv`.

## Estado atual

- Total de linhas/requisitos rastreados: **1388**.
- Marcadores do edital §14.1.13 (pp. impressas 13–40) e complementos ED91: **646**.
- Itens de fonte do Termo de Referência: **742**; transcrições conferidas visualmente: **742**; ainda pendentes de cotejo visual: **0**. Status TR: `EM_DESENVOLVIMENTO`=20, `IMPLEMENTADO_NAO_TESTADO`=0, `VALIDADO`=10, `NAO_INICIADO`=712, `BLOQUEADO`=0, `NAO_APLICAVEL_JUSTIFICADO`=0; **707/742** ainda aguardam decomposição em critérios observáveis. Os requisitos `VALIDADO` cobrem apenas seus critérios delimitados; a POC integral e a conformidade global não estão comprovadas.
- Obrigações sem implementação exclusiva em software: **221** (mesmo escopo para total e parcelas; inclui POC e proposta comercial; natureza `PREFERENCIAL` (**1**) é reportada separadamente). Operação contratual **106**, integração externa **78**, habilitação **8**, obrigações externas/contratuais **23**, POC **5**, proposta comercial **1**.
- Status `VALIDADO`: **17**. Não há alegação de 100% de aderência.

## Contagem por status

| Status | Linhas |
|---|---:|
| `EM_DESENVOLVIMENTO` | 48 |
| `IMPLEMENTADO_NAO_TESTADO` | 0 |
| `VALIDADO` | 17 |
| `NAO_INICIADO` | 1323 |
| `BLOQUEADO` | 0 |
| `NAO_APLICAVEL_JUSTIFICADO` | 0 |

## Cobertura por módulo

| Módulo | Linhas |
|---|---:|
| `ADMINISTRACAO` | 93 |
| `APROVACAO_PROJETOS` | 113 |
| `ASSINATURAS` | 117 |
| `CAPACIDADE_E_LICENCIAMENTO` | 5 |
| `CHAMADO_TECNICO` | 8 |
| `COMUNICACAO_OFICIAL` | 71 |
| `CONTRATUAL` | 16 |
| `E_SIC` | 11 |
| `GESTAO_DOCUMENTAL` | 164 |
| `HABILITACAO_EMPRESARIAL` | 7 |
| `IDENTIDADE_E_SEGURANCA` | 94 |
| `IMPLANTACAO_E_SUPORTE` | 6 |
| `MIGRACAO` | 1 |
| `NOTIFICACOES` | 19 |
| `OPERACAO_E_INTEGRACOES` | 24 |
| `OUVIDORIA` | 66 |
| `PLATAFORMA_E_ARQUITETURA` | 136 |
| `POC` | 1 |
| `POC_E_OPERACAO` | 7 |
| `PORTAL_EXTERNO` | 69 |
| `PRAZOS` | 13 |
| `PROCESSOS_ADMINISTRATIVOS` | 111 |
| `PROCESSO_ADMINISTRATIVO_DISCIPLINAR` | 12 |
| `PROCESSO_SELETIVO_SIMPLIFICADO` | 7 |
| `PROPOSTA_COMERCIAL` | 1 |
| `PROTOCOLO` | 54 |
| `PROTOCOLO_LEGISLATIVO` | 8 |
| `RELATORIOS_E_INDICADORES` | 110 |
| `REQUERIMENTO_PESSOAL` | 20 |
| `SUPORTE` | 11 |
| `TREINAMENTO` | 13 |

## Contagem por origem

| Escopo | Total | `EM_DESENVOLVIMENTO` | `IMPLEMENTADO_NAO_TESTADO` | `VALIDADO` | `NAO_INICIADO` | `BLOQUEADO` | `NAO_APLICAVEL_JUSTIFICADO` |
|---|---:|---:|---:|---:|---:|---:|---:|
| Edital e complementos ED91 | 646 | 28 | 0 | 7 | 611 | 0 | 0 |
| Termo de Referência TR91 | 742 | 20 | 0 | 10 | 712 | 0 | 0 |
| Total da matriz | 1388 | 48 | 0 | 17 | 1323 | 0 | 0 |

## Natureza

| Natureza | Linhas |
|---|---:|
| `EXTERNO/CONTRATUAL` | 23 |
| `HABILITACAO_EMPRESARIAL` | 8 |
| `INTEGRACAO_EXTERNA` | 78 |
| `OPERACAO_CONTRATUAL` | 106 |
| `POC` | 5 |
| `PREFERENCIAL` | 1 |
| `PROPOSTA_COMERCIAL` | 1 |
| `SOFTWARE` | 1166 |

## Regra de aceite

A planilha CSV contém o resumo do requisito, origem documental, prioridade, módulo, natureza, estado de conferência da fonte, critério, backend/frontend, teste, roteiro POC, evidência e status para cada ID. A conferência visual valida a transcrição, não a implementação. Nenhum status é elevado a `VALIDADO` por ter uma tela, endpoint ou documento. Atualize o CSV após cada incremento e execute `scripts/check-matrix.py`.

## Estabilidade dos IDs

`MATRIZ_IDS.csv` guarda uma âncora por página e texto normalizado para cada requisito numérico `ED91-*` e `TR91-*`. A regeneração mantém os IDs existentes e atribui novos IDs ao fim da sequência; se um item antigo deixar de ser reconhecido pelo parser, a linha é preservada como `BLOQUEADO` para revisão. `ED91-X*` identifica obrigações manuais estáveis definidas no gerador.

## Fontes e ressalvas

O edital e a Errata I são PDFs pesquisáveis; o Termo de Referência foi digitalizado. Cada linha `TR91` traz página PDF e bloco de texto para localizar o trecho. A coluna `validacao_fonte` informa se a transcrição foi conferida visualmente; requisitos ainda precisam de decomposição atômica e critérios de aceite. A numeração dos IDs é interna e não altera a numeração oficial dos itens.
