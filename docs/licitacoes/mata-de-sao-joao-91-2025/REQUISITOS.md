# Inventário dos requisitos — Pregão Eletrônico 91/2025

## Fontes oficiais obtidas

O pacote de origem está em `edital-original/` e preserva o edital, a Errata I, o Termo de Referência e a planilha de orçamento baixados da página oficial. Os PDFs são os originais usados como fonte soberana. Os arquivos `.txt` em `texto-extraido/` são derivados para busca; o texto do edital foi extraído do PDF pesquisável. O Termo de Referência é um scan de 69 páginas; há OCR em português com marcador por página, usado apenas para localizar candidatos até conferência visual.

## Método de cobertura

- `ED91-####`: cada marcador de requisito/bullet encontrado no §14.1.13 do edital, folhas impressas 13–40. A matriz conserva o resumo textual, folha e subtítulo e inclui uma linha separada para condições relevantes do edital/errata/termo que não aparecem como bullet nessa seção.
- `TR91-####`: candidatos por alínea, subitem e parágrafo extraídos por OCR em português do Termo de Referência, PDF pp.1–69. Cada item enumerado vira linha própria quando o OCR preserva ou permite recuperar o marcador; parágrafos de todas as seções numeradas também são incluídos. As linhas ficam `BLOQUEADO` até cotejo visual, decomposição atômica e critérios de aceite. Um item pode conter mais de uma obrigação e ainda precisa ser dividido sem perder o texto original. Algumas linhas podem duplicar exigências do edital, mas ficam separadas para preservar a origem do TR.
- A coluna `validacao_fonte` separa conferência do documento de validação do produto: `CONFERIDA_NO_DOCUMENTO_OFICIAL`, `CONFERIDA_VISUALMENTE` ou `OCR_PENDENTE`. A coluna `natureza` classifica a obrigação (software, operação contratual, habilitação etc.); não registra o estado da transcrição.
- `ED91-X###`: habilitação, POC, implantação, licenças, migração, suporte e prazo contratual, inclusive condições não resolvíveis apenas por software.
- Os anexos legíveis dentro do PDF do edital e a planilha de orçamento também foram preservados. A planilha é referência de preço, não tratada como requisito funcional.
- Status não significa conformidade. `VALIDADO` exige aceite, teste/evidência identificados e registro de aprovação; código existente sem prova continua pendente.

## Natureza dos requisitos

A matriz distingue software, integração, serviço/operação contratual, habilitação empresarial, implantação, capacidade/licenciamento e POC. Disponibilidade, SLA, hospedagem nacional/certificações, suporte, treinamento presencial, migração real, pentest periódico, homologação externa, habilitação e presença/equipamentos para POC precisam de prova operacional ou empresarial além de código.

## Referências POC corrigidas

O edital §9.9 (folha impressa 10) determina convocação da POC, prazo de até 72 horas úteis após agendamento e fornecimento de equipamento/conectividade pelo licitante. A Errata I §1 substitui o limite de apresentação de 90 minutos por **90 minutos prorrogáveis**; os demais critérios do edital continuam aplicáveis. Consulte `POC.md`.

## Limite de leitura e bloqueios documentados

A versão vigente foi obtida em 06/11/2026 na página oficial. O edital e a errata são PDFs pesquisáveis e foram examinados integralmente. O Termo de Referência é imagem; as 69 páginas receberam OCR em português para descoberta. As páginas 1–32 foram conferidas visualmente durante a revisão do extrator, com correções de marcadores omitidos, títulos de estrutura e continuações entre páginas. No estado atual, 341 transcrições do TR foram cotejadas visualmente e 362 candidatos das páginas seguintes ainda aguardam revisão. Mesmo as transcrições conferidas permanecem `BLOQUEADO` até decomposição de obrigações compostas, aceite, vínculos backend/frontend, teste e evidência. A matriz não pode ser declarada integralmente validada enquanto houver pendências de fonte, produto ou operação.

## Evidência do primeiro incremento

O fluxo inicial de protocolo foi compilado e testado com PostgreSQL 16.4 em Testcontainers. O teste integrado cobre abertura externa, número concorrente, recibo PDF com QR decodificável, consulta com token, tramitação, conclusão, rejeição de setor de outra organização e ausência de acesso sem associação válida. Um teste adicional confirma que perfis fora de `local` negam acesso administrativo, mesmo com credenciais Basic. O CSV marca dez requisitos com evidência parcial como `EM_DESENVOLVIMENTO`; detalhes, testes e lacunas estão nas próprias linhas da matriz e em `../../INCREMENTO_01.md`. Nenhuma linha foi marcada `VALIDADO`.

## Estado do certame

A página oficial atualmente informa adjudicação/contrato com 1DOC TECNOLOGIA S.A., vigência publicada de 09/10/2025 a 09/10/2027. O projeto NexoGov pode usar as exigências como referência de produto, mas não representa participação ou aptidão contratual para esse certame.
