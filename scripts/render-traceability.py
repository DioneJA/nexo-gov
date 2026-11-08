#!/usr/bin/env python3
"""Render auditable summary and a row-by-row POC register from the CSV source of truth."""
import csv
from collections import Counter
from datetime import date
from pathlib import Path

root = Path(__file__).resolve().parents[1]
base = root / "docs/licitacoes/mata-de-sao-joao-91-2025"
matrix_path = base / "MATRIZ_ADERENCIA.csv"
existing_poc_path = base / "POC.md"
existing_poc_text = existing_poc_path.read_text(encoding="utf-8") if existing_poc_path.exists() else ""
appendix_marker = "## Limites da evidência"
appendix_start = existing_poc_text.find(appendix_marker)
manual_appendix = existing_poc_text[appendix_start:].strip() if appendix_start >= 0 else ""
with matrix_path.open(encoding="utf-8-sig", newline="") as stream:
    rows = list(csv.DictReader(stream, delimiter=";"))

status_counts = Counter(row["status"] for row in rows)
module_counts = Counter(row["modulo"] for row in rows)
nature_counts = Counter(row["natureza"] for row in rows)
ed_rows = [row for row in rows if row["id"].startswith("ED91-")]
tr_rows = [row for row in rows if row["id"].startswith("TR91-")]
tr_status_counts = Counter(row["status"] for row in tr_rows)
tr_pending_decomposition = sum(
    row["criterio_de_aceite"].startswith("Pendente de decomposição:") for row in tr_rows
)
external_rows = [row for row in rows if row["natureza"] == "EXTERNO/CONTRATUAL"]
non_software_natures = {
    "EXTERNO/CONTRATUAL", "OPERACAO_CONTRATUAL", "HABILITACAO_EMPRESARIAL",
    "INTEGRACAO_EXTERNA", "POC", "PROPOSTA_COMERCIAL",
}
non_software_rows = [row for row in rows if row["natureza"] in non_software_natures]
operation_rows = [row for row in rows if row["natureza"] == "OPERACAO_CONTRATUAL"]
qualification_rows = [row for row in rows if row["natureza"] == "HABILITACAO_EMPRESARIAL"]
integration_rows = [row for row in rows if row["natureza"] == "INTEGRACAO_EXTERNA"]
poc_rows = [row for row in rows if row["natureza"] == "POC"]
commercial_rows = [row for row in rows if row["natureza"] == "PROPOSTA_COMERCIAL"]
preferential_rows = [row for row in rows if row["natureza"] == "PREFERENCIAL"]
tr_visually_reviewed = [row for row in tr_rows if row["validacao_fonte"] == "CONFERIDA_VISUALMENTE"]
tr_ocr_pending = [row for row in tr_rows if row["validacao_fonte"] == "OCR_PENDENTE"]

report = [
    "# Matriz de aderência — resumo de rastreabilidade", "",
    f"Gerada em {date.today().isoformat()} a partir de `MATRIZ_ADERENCIA.csv`.", "",
    "## Estado atual", "",
    f"- Total de linhas/requisitos rastreados: **{len(rows)}**.",
    f"- Marcadores do edital §14.1.13 (pp. impressas 13–40) e complementos ED91: **{len(ed_rows)}**.",
    f"- Itens de fonte do Termo de Referência: **{len(tr_rows)}**; transcrições conferidas visualmente: **{len(tr_visually_reviewed)}**; ainda pendentes de cotejo visual: **{len(tr_ocr_pending)}**. Status TR: `EM_DESENVOLVIMENTO`={tr_status_counts['EM_DESENVOLVIMENTO']}, `IMPLEMENTADO_NAO_TESTADO`={tr_status_counts['IMPLEMENTADO_NAO_TESTADO']}, `VALIDADO`={tr_status_counts['VALIDADO']}, `NAO_INICIADO`={tr_status_counts['NAO_INICIADO']}, `BLOQUEADO`={tr_status_counts['BLOQUEADO']}, `NAO_APLICAVEL_JUSTIFICADO`={tr_status_counts['NAO_APLICAVEL_JUSTIFICADO']}; **{tr_pending_decomposition}/{len(tr_rows)}** ainda aguardam decomposição em critérios observáveis. Os requisitos `VALIDADO` cobrem apenas seus critérios delimitados; a POC integral e a conformidade global não estão comprovadas.",
    f"- Obrigações sem implementação exclusiva em software: **{len(non_software_rows)}** (mesmo escopo para total e parcelas; inclui POC e proposta comercial; natureza `PREFERENCIAL` (**{len(preferential_rows)}**) é reportada separadamente). Operação contratual **{len(operation_rows)}**, integração externa **{len(integration_rows)}**, habilitação **{len(qualification_rows)}**, obrigações externas/contratuais **{len(external_rows)}**, POC **{len(poc_rows)}**, proposta comercial **{len(commercial_rows)}**.",
    f"- Status `VALIDADO`: **{status_counts['VALIDADO']}**. Não há alegação de 100% de aderência.", "",
    "## Contagem por status", "", "| Status | Linhas |", "|---|---:|"
]
for status in ("EM_DESENVOLVIMENTO", "IMPLEMENTADO_NAO_TESTADO", "VALIDADO", "NAO_INICIADO", "BLOQUEADO", "NAO_APLICAVEL_JUSTIFICADO"):
    report.append(f"| `{status}` | {status_counts[status]} |")
report += ["", "## Cobertura por módulo", "", "| Módulo | Linhas |", "|---|---:|"]
for module, count in sorted(module_counts.items()): report.append(f"| `{module}` | {count} |")
report += [
    "", "## Contagem por origem", "",
    "| Escopo | Total | `EM_DESENVOLVIMENTO` | `IMPLEMENTADO_NAO_TESTADO` | `VALIDADO` | `NAO_INICIADO` | `BLOQUEADO` | `NAO_APLICAVEL_JUSTIFICADO` |",
    "|---|---:|---:|---:|---:|---:|---:|---:|",
]
source_statuses = (
    ("Edital e complementos ED91", ed_rows, Counter(row["status"] for row in ed_rows)),
    ("Termo de Referência TR91", tr_rows, tr_status_counts),
    ("Total da matriz", rows, status_counts),
)
for label, source_rows, counts in source_statuses:
    report.append(
        f"| {label} | {len(source_rows)} | {counts['EM_DESENVOLVIMENTO']} | "
        f"{counts['IMPLEMENTADO_NAO_TESTADO']} | {counts['VALIDADO']} | "
        f"{counts['NAO_INICIADO']} | {counts['BLOQUEADO']} | {counts['NAO_APLICAVEL_JUSTIFICADO']} |"
    )
report += ["", "## Natureza", "", "| Natureza | Linhas |", "|---|---:|"]
for nature, count in sorted(nature_counts.items()): report.append(f"| `{nature}` | {count} |")
report += [
    "", "## Regra de aceite", "",
    "A planilha CSV contém o resumo do requisito, origem documental, prioridade, módulo, natureza, estado de conferência da fonte, critério, backend/frontend, teste, roteiro POC, evidência e status para cada ID. A conferência visual valida a transcrição, não a implementação. Nenhum status é elevado a `VALIDADO` por ter uma tela, endpoint ou documento. Atualize o CSV após cada incremento e execute `scripts/check-matrix.py`.",
    "", "## Estabilidade dos IDs", "",
    "`MATRIZ_IDS.csv` guarda uma âncora por página e texto normalizado para cada requisito numérico `ED91-*` e `TR91-*`. A regeneração mantém os IDs existentes e atribui novos IDs ao fim da sequência; se um item antigo deixar de ser reconhecido pelo parser, a linha é preservada como `BLOQUEADO` para revisão. `ED91-X*` identifica obrigações manuais estáveis definidas no gerador.",
    "", "## Fontes e ressalvas", "",
    "O edital e a Errata I são PDFs pesquisáveis; o Termo de Referência foi digitalizado. Cada linha `TR91` traz página PDF e bloco de texto para localizar o trecho. A coluna `validacao_fonte` informa se a transcrição foi conferida visualmente; requisitos ainda precisam de decomposição atômica e critérios de aceite. A numeração dos IDs é interna e não altera a numeração oficial dos itens.",
]
(base / "MATRIZ_ADERENCIA.md").write_text("\n".join(report) + "\n", encoding="utf-8")

poc = [
    "# Roteiro da Prova de Conceito (POC)", "",
    "## Estado de aderência reportado pela matriz",
    "",
    f"A matriz atual contém **{len(tr_rows)}** itens de fonte rastreados do Termo de Referência, dos quais **{tr_status_counts['VALIDADO']}** estão validados somente no escopo delimitado em suas linhas e **{tr_pending_decomposition}** ainda aguardam decomposição em critérios observáveis. Uma linha pode conter mais de uma obrigação até que sua decomposição seja concluída. A POC integral do NexoGov não foi executada e a conformidade global não está comprovada. A ata oficial registra que a 1DOC foi aprovada na POC em 03/10/2025 e informa que o relatório detalhado foi enviado à Licitanet. O NexoGov não participou daquela sessão. Consulte `andamento-oficial/STATUS_CERTAME.md`.",
    "",
    "## Situação temporal do certame",
    "",
    "O portal oficial da Prefeitura, atualizado em 06/11/2026, lista a adjudicação e homologação em 06/10/2025 e o Contrato nº 667/25 com 1DOC TECNOLOGIA S.A., publicado em 09/10/2025, com vigência informada de 09/10/2025 a 09/10/2027. O extrato local do contrato informa 24 meses a partir da publicação no PNCP. A ata registra a convocação da 1DOC para POC em 24/09/2025 e sua aprovação pela equipe técnica em 03/10/2025; também informa que o relatório detalhado foi enviado ao menu Documentos da Licitanet. Esse relatório não está preservado localmente nem listado entre os anexos exibidos na página municipal, portanto não inferimos os critérios individuais demonstrados. O NexoGov não participou daquela sessão. Prazos e contatos de 2025 são referências históricas; para novos certames, confira o novo edital e a convocação escrita.",
    "",
    "## Condições oficiais de 2025 (referência histórica)", "",
    "O Edital PE 91/2025, §9.9, e a Errata I §1 descrevem a sessão da licitação de 2025: após análise da proposta e habilitação, a empresa melhor classificada deveria comparecer em até 72 horas úteis após agendamento; a duração prevista passou a **90 minutos, podendo ser prorrogada**. A Errata I, p.2, registra o contato usado naquele procedimento. O Termo de Referência §41, PDF p.38, descreve sessão pública na sede da Prefeitura, Rua Luiz Antônio Garcez, 140, Centro, ou remota; a licitante forneceria equipamentos e internet, e o Município poderia disponibilizar auditório. A equipe técnica emitiria laudo conclusivo. O portal oficial registra adjudicação, homologação e contrato publicado em outubro de 2025; portanto, os prazos e meios de contato abaixo não são instrução para agendamento atual.",
    "",
    "### Checklist documental da sessão de 2025 — não é convocação vigente",
    "",
    "1. Naquele procedimento, após convocação e agendamento, a licitante deveria enviar solicitação a `suporte@pmsj.ba.gov.br` e pedir confirmação de recebimento; se não houvesse retorno, deveria ligar para `(71) 3635-1310` (Errata I, p.2; Edital §9.9).",
    "2. A convocação previa a modalidade presencial ou remota e comparecimento em até 72 horas úteis após agendamento. Para sessão presencial, o endereço indicado era Rua Luiz Antônio Garcez, 140, Centro, Mata de São João/BA; a sessão seria pública (TR §41, PDF p.38).",
    "3. A licitante deveria fornecer sistema, computadores/notebooks, projetor/datashow, internet e demais materiais; poderia solicitar o auditório municipal (Edital §9.9; TR §41, PDF p.38).",
    "4. A demonstração deveria durar 90 minutos, com possibilidade de prorrogação, conforme Errata I §1, p.1. Questionamentos e critérios de aderência do edital §9.9.2 permaneciam aplicáveis.",
    "5. A equipe técnica deveria registrar resultado, limitações observadas e laudo conclusivo. Os artefatos locais não incluem esse laudo da sessão de 2025; a adjudicação/contratação não é apresentada como prova de teste executado pelo NexoGov.",
    "", "## Ambiente e dados fictícios", "",
    "- Duas organizações isoladas: Município Alfa e Município Beta; cada uma tem Protocolo Geral e Secretaria de Obras.",
    "- Requerente: Pessoa de Teste; e-mail `cidadao@example.test`; solicitação sobre análise de via pública.",
    "- Não inserir dados pessoais reais, certificados, credenciais corporativas ou anexos oficiais.",
    "- API local: `http://localhost:8080`; portal: `http://localhost:9000`; API usa Postgres isolado.",
    "- Ensaio de navegador: `./dev e2e`; o harness inicia os serviços e portas em ambiente descartável, usa Chrome e remove somente o projeto temporário ao terminar.",
    "- O teste automatizado prova apenas o fluxo vertical implementado, não substitui a demonstração de todos os critérios da POC oficial.",
    "", "## Ensaio implementado hoje (aprox. 8–12 min; não é POC completa)", "",
    "1. Abra a página do cidadão, selecione Município Alfa e registre o protocolo `Solicitação de teste`.",
    "2. Verifique que recebeu número `AAAA-000001` (o número efetivo depende dos dados locais), token de consulta e recibo PDF com QR.",
    "3. Abra a área de servidor e autentique-se com a conta local de demonstração. Confirme que o contexto enviado é o Município Alfa.",
    "4. Encaminhe para Protocolo Geral e receba o protocolo.",
    "5. Despache para Secretaria de Obras com atualização pública, receba no setor e conclua.",
    "6. Consulte pelo token/QR: o cidadão deve ver estado concluído e somente eventos marcados como públicos.",
    "7. Tente encaminhar para setor pertencente ao Município Beta e confira rejeição. Carregue a caixa do Município Beta e confirme que o protocolo de Alfa não aparece.",
    "8. Salve como evidências o teste Testcontainers, PDF, JSON de estado, vídeo/captura da interface, logs sem segredos e planilha com IDs cobertos.",
    "", "**Limites do ensaio:** o fluxo ainda é um primeiro recorte e não prova os demais requisitos; HTTP Basic/seed existem apenas em perfil `local`; o cliente da área do servidor implementa OIDC Authorization Code + PKCE e foi testado com provedor simulado, mas não há IdP municipal configurado/homologado nem prova de provisionamento. Este cenário básico não inclui anexos, notificações, assinatura, dados reais, e-mail, integrações externas ou proteções contra abuso; há cenários separados e parciais de anexos e conversa com E2E de upload/download em ambiente descartável. Isso não prova storage produtivo nem a POC integral.",
    "", "## Cenário parcial — assuntos online e atendimento de balcão", "",
    "**Requisitos relacionados:** `ED91-0152`, `ED91-0487`, `TR91-0157` e `TR91-0522`. O backend rejeita assunto presencial ou ID de outra organização antes da numeração, e mantém o nome escolhido como snapshot do protocolo. `ED91-0487` também requer mapa/georreferenciamento; essa parte segue pendente. Este cenário não comprova os demais campos, anexos ou funções completas da Central de Atendimento.",
    "",
    "1. Entre em `/servidor/` como administrador de Município Alfa e abra **Assuntos de protocolo**.",
    "2. Cadastre um assunto como **Central de Atendimento** e outro como **Somente balcão interno**. Confirme os estados exibidos e a trilha de auditoria.",
    "3. Abra o portal do cidadão e confirme que o seletor contém o assunto online e não apresenta o assunto de balcão. Registre uma solicitação com o assunto online e confira seu nome no protocolo/recibo.",
    "4. Use o teste Testcontainers para enviar diretamente à API o ID do assunto presencial e o ID de assunto de Município Beta, antes e depois confira que o contador de Alfa não mudou.",
    "5. Renomeie e mova o assunto online para balcão. Consulte o protocolo já criado: o nome original deve permanecer no acompanhamento, sem alterar protocolos legados cujo vínculo de catálogo é nulo.",
    "",
    "**Evidência/limites:** `ProtocolVerticalIntegrationTest.protocolSubjectCatalogIsTenantScopedOnlineOnlyAndKeepsSnapshots`, `protocol-subject-catalog.spec.ts`, `protocol-flow.spec.ts` e `docs/INCREMENTO_22.md`; esses testes automatizados não substituem a observação pela comissão da POC oficial. Georreferenciamento com mapa/marcador continua não implementado.",
    "", "## Cenário parcial — campos de identificação de pessoas envolvidas", "",
    "**Requisito:** `TR91-0524`, Termo de Referência §4, PDF p.50 (transcrição conferida visualmente); `ED91-0488` contém cláusula combinada no Edital §14.1.13, folha impressa 33. A configuração, captura, acesso individual, interação e revogação têm ensaio automatizado. A chave demonstra posse do convite, não identidade civil; o envio é manual. O status continua `EM_DESENVOLVIMENTO` enquanto faltam validação oficial de identidade quando aplicável, operação segura de convites e POC oficial.",
    "",
    "**Estado inicial e dados fictícios:** ambiente descartável com PostgreSQL; ADMIN da organização Alfa; assunto `Solicitação geral`; organização Beta para tentativa cross-tenant. Chaves, pessoas e dados usados no teste são fictícios. A amostra de duas pessoas indicada no cenário completo não é uma quantidade exigida pelo edital.",
    "",
    "1. Execute `./dev participant-fields-test` e `./dev participant-fields-e2e`; como ADMIN, crie e confirme um campo de identificação no assunto fictício.",
    "2. Execute `./dev participant-access-test` para confirmar rejeição de campo desconhecido sem consumir numeração, hashes dos convites, valores associados apenas ao token correspondente, autoria neutralizada, escopo de protocolo e revogação.",
    "3. Execute `./dev participant-access-e2e`: abra protocolo com duas pessoas fictícias, copie cada convite, acesse em contextos de navegador separados, confirme que cada pessoa vê somente seus próprios dados, envie uma mensagem por uma delas e confira a conversa no acompanhamento do requerente.",
    "4. Revogue o acesso de uma pessoa pelo token do requerente; confirme que a chave revogada deixa de consultar/interagir e que a outra continua ativa. As credenciais em fragmento não são enviadas na requisição HTTP e são mostradas uma única vez para cópia manual.",
    "5. Este ensaio confirma posse de chave, não identidade civil. Credenciais oficiais Gov.br/ICP-Brasil, entrega automatizada segura, validações do órgão e observação pela comissão na POC oficial ainda não foram demonstradas. O edital não prescreve quantidade de participantes; duas pessoas são somente massa de teste.",
    "",
    "**Resultado/evidência parcial:** `./dev participant-fields-test` e `./dev participant-fields-e2e` verificam configuração; `./dev participant-access-test` usa PostgreSQL Testcontainers para cadastro, acesso, interação e revogação; `./dev participant-access-e2e` verifica o fluxo cidadão contra a API real. `TR91-0524` e `ED91-0488` permanecem `EM_DESENVOLVIMENTO`; a evidência automatizada não é a POC oficial nem prova identidade civil.",
    "", "## Cenário parcial — anexos do protocolo por assunto", "",
    "**Requisitos relacionados:** `ED91-0484`, `TR91-0516`, `TR91-0019`, `ED91-0170`, `TR91-0174` e `TR91-0517`. O prazo exibido no comprovante tem cenário próprio abaixo e status `VALIDADO` para esse critério literal; `ED91-0484` e `TR91-0516` seguem `EM_DESENVOLVIMENTO` por lacunas de anexos e operação. `TR91-0525` tem evidência parcial de conversa posterior, detalhada no cenário seguinte. Os itens `ED91-0171`, `ED91-0172` e `ED91-0173` mantêm a situação registrada em suas linhas próprias. Este é um ensaio automatizado parcial, não uma POC oficial.",
    "",
    "**Estado inicial e dados:** PostgreSQL e armazenamento descartáveis; Município Alfa com um assunto online e ADMIN fictício; Município Beta como tenant isolado; documento de teste PDF e tentativa de extensão `.exe`. Não usar anexos, tokens ou dados reais.",
    "",
    "1. Execute `./dev attachment-test`. O teste cria uma regra obrigatória PDF no assunto de Alfa; duplicar nome deve retornar conflito.",
    "2. Confira que abrir sem o documento obrigatório e enviar extensão `.exe` retorna erro antes de consumir número. Depois envie um PDF fictício, valide que o protocolo foi criado e consulte nome/tamanho do anexo pelo acompanhamento autenticado com o token.",
    "3. Baixe pelo endpoint da aplicação usando o token emitido; repita com token inválido e confirme `404`. Como servidor, confira a listagem no setor atual; repita com outro tenant (`404`) e usuário sem lotação no setor (`403`). A API não devolve URL direta do bucket.",
    "4. Execute `./dev e2e --grep 'assunto exige documento'` e confira que o Playwright demonstra configuração do requisito pelo ADMIN, bloqueio do envio do cidadão sem arquivo, upload do PDF, acompanhamento/download do cidadão e listagem/download pelo servidor em ambiente descartável. O cenário cobre apenas documento obrigatório PDF e o protocolo inicial.",
    "",
    "**Resultado/evidência parcial:** `./dev e2e --grep 'assunto exige documento'` executa `frontend/e2e/protocol-attachments.spec.ts`: o Playwright demonstra ADMIN configurando documento obrigatório, bloqueio da solicitação do cidadão sem arquivo, upload de PDF, download pelo cidadão e listagem/download pelo servidor. A execução informada passou 1/1 em 24,1 s no ambiente descartável. `ProtocolVerticalIntegrationTest#protocolAttachmentsAreRequiredValidatedPrivateAndTenantScoped`, por `./dev attachment-test`, usa PostgreSQL Testcontainers e Adobe S3Mock; valida ausência/extensão recusada sem consumir numeração, upload/download, tamanho/contagem e negativas de token/tenant/setor. O S3Mock é apenas um double de API: não comprova hospedagem ou bucket produtivo privado. `ED91-0484`, `TR91-0516`, `TR91-0019`, `ED91-0170` e `TR91-0174` permanecem `EM_DESENVOLVIMENTO`; esta evidência parcial não conclui seus critérios completos, nem prova disponibilidade da regra opcional, todos os formatos, prazo/documento ou segurança operacional. A conversa posterior com PDF configurado está registrada no cenário seguinte. A sessão de 90 minutos prorrogáveis da Errata I §1, p.1 não foi ensaiada e a POC integral não foi realizada.",
    "", "## Cenário parcial — interações posteriores, anexos e reabertura de protocolo", "",
    "**Fontes oficiais e requisitos relacionados:** `ED91-0483` — Edital PE 91/2025, §14.1.13, fl. impressa 33; `TR91-0515` — Termo de Referência, §4, PDF p.49; `TR91-0525` — Termo de Referência, §4, PDF p.50. O formato PDF usado na interação é evidência complementar de configuração por assunto para `ED91-0484` (Edital, p.33) e `TR91-0516` (TR, pp.49–50). `TR91-0524` está parcialmente implementado quanto à definição de campos; esta conversa segue usando apenas o token do requerente original e não oferece cadastro, credenciais ou isolamento para participantes adicionais.",
    "",
    "**Estado inicial e dados fictícios:** ambiente Playwright/Compose descartável; Município Alfa com assunto online e Protocolo Geral; ADMIN configura extensão permitida `.pdf`; cidadão cria solicitação com dados `example.test` e recebe token; Município Beta permanece como tenant negativo. Não usar dados, tokens ou anexos reais.",
    "",
    "1. Execute `./dev e2e --grep 'conversa de protocolo'`. No portal do servidor, cadastre assunto de Alfa com regra opcional de PDF e confirme que a interface informa essa extensão.",
    "2. No portal do cidadão, abra protocolo fictício e guarde o token emitido. Na área de servidor, publique texto e um PDF permitido; consulte com o token e confirme que mensagem e arquivo aparecem e que o download retorna o mesmo nome de arquivo.",
    "3. Envie resposta em texto como requerente e atualize a conversa do servidor. Publique uma nota interna no servidor; confirme que permanece visível na área interna e não aparece na consulta pública. O teste backend também confirma a ocultação de anexo interno e negativas de download por requerente/setor/tenant não autorizado.",
    "4. Conclua o protocolo e confirme que o portal informa o bloqueio e retira o formulário de nova mensagem. Tente enviar diretamente pela API e confira resposta de conflito, sem nova interação gravada.",
    "5. Reabra pelo servidor responsável e confirme o retorno do estado a aberto/recebido e que o requerente pode enviar nova mensagem. O teste backend confirma recusa de reabertura por servidor de outro setor e serialização do envio concorrente com a conclusão.",
    "6. Execute `./dev interaction-test` para repetir quatro métodos de integração PostgreSQL/Testcontainers — anexos/visibilidade com download por servidor e requerente, corrida com conclusão, ordenação após espera por lock e atualização concorrente de política — e o Playwright real em infraestrutura descartável. `./dev verify-fast` é a validação geral adicional; POC oficial e produção permanecem separadas.",
    "",
    "**Resultado e evidência disponível:** `./dev interaction-test` passou quatro testes backend selecionados com PostgreSQL Testcontainers/S3Mock e um cenário Playwright contra a API real em ambiente descartável. A conversa cobre mensagem pública, PDF configurado, resposta do requerente, nota interna invisível ao cidadão, bloqueio após conclusão, reabertura autorizada e novo envio; a API também comprova negativas tenant/setor, downloads sem auditoria em recusas, trilha append-only, ordenação histórica após lock, lock de política e corrida envio/conclusão. `ED91-0483`, `TR91-0515` e `TR91-0517` estão `VALIDADO` somente para seus critérios literais separados; `TR91-0525`, `ED91-0484` e `TR91-0516` permanecem `EM_DESENVOLVIMENTO`. Nenhuma evidência equivale à POC oficial.",
    "",
    "**Limites:** a conversa representa somente o requerente detentor do token; não demonstra cadastro nem acesso de participantes adicionais. O cenário usa PDF, não a lista completa de formatos. Não comprova disponibilidade/segurança operacional do storage, cálculo de vencimento legal, nem o desempenho/condições integrais da POC de 90 minutos.",
    "", "## Cenário parcial — prazo automático por assunto no comprovante", "",
    "**Requisito:** `TR91-0517`, Termo de Referência §4 PROTOCOLO ELETRÔNICO (Anexo II), PDF pp.49–50; transcrição conferida visualmente. O critério coberto é configurar quantidade positiva de dias para o assunto, conservar o valor vigente na abertura do protocolo e exibi-lo no comprovante. A evidência não interpreta esses dias como cálculo legal de vencimento.",
    "",
    "**Estado inicial e dados:** use o ADMIN fictício do Município Alfa, assunto online de teste, protocolos/cidadãos fictícios e armazenamento/PostgreSQL descartáveis. Nenhum dado pessoal real.",
    "",
    "1. Execute `./dev protocol-deadline-test`. O teste configura 10 dias, abre protocolo, altera o assunto para 20 e prova que o primeiro recibo conserva 10 e o novo apresenta 20; depois remove a configuração, verifica a ausência da linha em um novo recibo, rejeita zero (HTTP 400) e confirma que uma edição cross-tenant não alterou o valor.",
    "2. Execute `./dev e2e --grep 'prazo automático no comprovante'`. Na interface, configure 8 dias e depois 14 no assunto, recarregue o portal do servidor para verificar persistência, abra a solicitação no portal cidadão e extraia o texto do PDF real para conferir `Prazo configurado: 14 dias`.",
    "3. A ata registra que a equipe técnica aprovou a 1DOC na POC do certame em 03/10/2025; o NexoGov não participou daquela avaliação. Este roteiro é evidência automatizada interna. A validação não cobre regra jurídica de contagem, calendário, feriados ou cálculo de data final, que não aparecem neste trecho do edital.",
    "",
    "**Evidência/limites:** `ProtocolVerticalIntegrationTest#subjectDeadlineIsConfiguredSnapshottedAndPrintedOnReceipt` (PostgreSQL Testcontainers) e `frontend/e2e/protocol-subject-catalog.spec.ts` (Playwright contra API real); consultar `docs/INCREMENTO_34.md`. O resultado automatizado é evidência do software, não aprovação ou laudo da POC oficial.",
    "", "## Cenário parcial — hierarquia, dados e presença dos servidores", "",
    "**Requisitos relacionados:** `ED91-0087`, `ED91-0103`, `ED91-0104`, `ED91-0105`, `ED91-0107`, `TR91-0111`, `TR91-0112`, `TR91-0113` e `TR91-0115`. Os estados individuais e os limites de cada evidência constam na matriz abaixo; a execução local não equivale à POC oficial.",
    "",
    "1. Entre em `/servidor/` como administrador de Município Alfa e selecione o servidor fictício.",
    "2. Atualize nome e cargo; informe uma data de cadastro apenas quando houver dado confiável. Datas legadas sem origem ficam explicitamente `UNKNOWN`.",
    "3. Confirme a árvore com setor raiz, subsetor, lotação, nome, cargo, data de cadastro conhecida e atividade recente. A presença é `ONLINE` por até dois minutos após atividade autenticada, `OFFLINE` após o prazo e `NOT_OBSERVED` se nunca foi registrada.",
    "4. Troque para Município Beta e confirme que os dados de Alfa não aparecem; tente gravar perfil com usuário sem papel de administrador e confirme 403.",
    "5. O teste `staffProfilesAndPresenceAreTenantScopedAndAudited` cobre estados online/offline/não observado, perfil, auditoria e isolamento. O E2E executa o caminho visível com dados fictícios.",
    "",
    "**Limite:** há contas e vínculos persistentes `issuer + subject`; a data original do provedor ainda depende de provisionamento confiável. A migração mantém datas importadas apenas quando não há divergência entre organizações; registros sem origem permanecem desconhecidos. O heartbeat mede atividade autenticada recente, não uma conexão permanente do dispositivo.",
    "", "## Cenário parcial — identidade federada vinculada por organização", "",
    "**Requisitos relacionados:** `ED91-0035`, `ED91-0038`, `ED91-0059`, `TR91-0045` e `TR91-0048`. A área do servidor agora tem cliente público OIDC Authorization Code + PKCE. A demonstração cobre essa troca com provedor simulado e a fundação de JWT/JWKS/vínculo por organização; não valida IdP municipal, cadastro de e-mail/senha, provisionamento, recuperação, TOTP, e-CPF, login social/Gov.br ou autorização documental completa.",
    "",
    "**Estado inicial:** o harness cria PostgreSQL Testcontainers descartável, organizações Alfa e Beta, chave RSA e endpoint JWKS locais fictícios. O mesmo username local e o mesmo par `issuer + subject` têm um vínculo ativo em Alfa e um vínculo inativo em Beta; há também identidade sem vínculo, membership ausente e membro suspenso.",
    "",
    "1. Execute `./dev identity-test`. O alvo real do harness executa `FederatedIdentityIntegrationTest` e `MigrationSafetyIntegrationTest` com recursos descartáveis.",
    "2. Em `organizationScopedLinkWithSharedUsernameDoesNotGrantAccessToAnotherOrganization`, com `user_accounts.active=true`, confirme resposta 200 para o vínculo ativo de Alfa e 403 para Beta, cujo vínculo está inativo. Defina `user_accounts.active=false` e confirme 403 em Alfa e Beta; reative a conta e confirme novamente 200 em Alfa e 403 em Beta. O estado do vínculo continua isolado por organização, enquanto a conta local ativa é pré-requisito global.",
    "3. Em `rejectsUnlinkedAndInactiveOrganizationScopedIdentities`, confirme 403 para issuer/subject sem vínculo e para vínculo organizacional inativo. Em `organizationScopedIdentityStillRequiresMembershipAndUnsuspendedAccess`, confirme 403 sem membership e após suspensão organizacional.",
    "4. Em `rejectsWrongIssuerAudienceAndExpiredTokens`, confirme 401 para issuer, audience ou expiração inválidos, antes de conceder acesso à organização.",
    "5. Em `MigrationSafetyIntegrationTest.blocksGlobalExternalIdentityLinksWithoutChangingLegacyData`, preparar banco no alvo V8 com vínculo global legado e tentar a V9: a migração deve falhar pedindo reconciliação autoritativa, sem converter/remover o vínculo nem alterar membership/conta. Não executar esse caso no banco local persistente.",
    "6. Execute `./dev oidc-test` para a interface: o provedor simula discovery, autorização, token, JWKS e logout; confira PKCE S256, state, nonce, callback exato, Bearer e preservação do tenant. O cenário usa provedor e chamadas da API de servidor simulados; não comprova integração com IdP municipal nem revogação de token no servidor. A autorização real continua provada separadamente pelo teste backend acima. Execute também `./dev frontend-auth-bundle-test` para conferir que o grafo de imports do portal cidadão não contém auxiliares de Basic nem campos de encaminhamento internos.",
    "7. Conferir relatórios de teste e registros temporários. O harness descarta o PostgreSQL Testcontainers e o projeto Compose ao encerrar.",
    "",
    "**Resultado esperado:** a assinatura RS256, issuer HTTPS configurado, JWKS HTTPS e audience são verificados; HTTP só é aceito para loopback no perfil de teste, e configuração insegura/incompleta mantém a API protegida em deny-all. `issuer + subject` resolve apenas um vínculo ativo no tenant selecionado e exige `user_accounts.active=true`; o backend também valida membership ativa e suspensão por organização. O cliente OIDC não interpreta e-mail, subject ou roles para autorizar; apenas envia o access token como Bearer. O header escolhe o namespace e não concede acesso. O logout limpa a sessão/tokens locais e pode redirecionar ao logout do provedor quando anunciado; isso não revoga o access token no servidor nem garante sua invalidação imediata.",
    "",
    "**Evidência:** `FederatedIdentityIntegrationTest` (quatro cenários) e `MigrationSafetyIntegrationTest.blocksGlobalExternalIdentityLinksWithoutChangingLegacyData`, chamados por `./dev identity-test`; `staff-auth-config.spec.ts` e `staff-oidc.spec.ts` (provedor simulado), chamados por `./dev oidc-test`; migração `V9__organization_scoped_external_identity.sql`.",
    "",
    "**Limites/bloqueios:** o provedor do teste OIDC e o JWKS do teste backend são fictícios e temporários; nenhum IdP OIDC municipal ou cliente registrado em produção foi homologado/configurado. Provisionamento de vínculos, login por e-mail/senha e autorização completa por documento/grupo ainda não existem. O logout não prova revogação server-side do Bearer; um access token pode continuar válido até expirar, salvo recusa subsequente por conta, vínculo, membership ou suspensão. A V9 não faz backfill: ambientes com links globais precisam de reconciliação por fonte autoritativa antes da migração. Estes testes não substituem a POC oficial nem comprovam aderência integral.",
    "", "## Cenário parcial — recibo e setor destinatário inicial", "",
    "**Requisitos relacionados:** `TR91-0510` cobre o critério literal do comprovante imediato com o setor destinatário; esse item é acompanhado separadamente de `ED91-0481`, que também exige acesso setorial integral ao conteúdo/movimentações e resposta direta ao requerente. Esta evidência não conclui o requisito amplo nem representa validação oficial da POC.",
    "",
    "**Estado inicial:** organizações fictícias Alfa e Beta; assunto online de Alfa apontando para setor ativo; protocolo aberto com token e número fictícios. A migração V11 guarda o nome inicial em campo nullable e não infere esse nome para protocolos anteriores.",
    "",
    "1. Execute `./dev protocol-routing-test` em PostgreSQL Testcontainers. Confira que a API cria protocolo no setor do assunto e que a leitura do PDF mostra o setor na abertura.",
    "2. Encaminhe o protocolo, receba, despache para outro setor, baixe novamente o PDF com o token e confira que continua exibindo o setor inicial. Confira também que um protocolo legado com snapshot nulo não ganha setor inferido.",
    "3. Tente `forward` sem body e sem `destinationDepartmentId`; ambos devem retornar 400 sem evento/alteração. Confirme que receive/dispatch/complete aceitam body ausente quando não há destino a informar.",
    "4. Refaça `forward`, `receive`, `dispatch` e `complete` com o cabeçalho da organização Beta contra o ID de Alfa; cada resposta deve ser 404 e o protocolo, setor e histórico de Alfa devem permanecer iguais.",
    "5. O cenário Playwright `protocol-subject-catalog.spec.ts` cria protocolo na interface do cidadão, confirma o recibo logo após a inclusão e extrai o setor do PDF real. Não precisa de credenciais ou dados reais.",
    "",
    "**Evidência e limites:** `ProtocolVerticalIntegrationTest` cobre PDF antes/depois, protocolo legado sem setor inventado, 400, bodies opcionais e 404 sem mutação; `protocol-subject-catalog.spec.ts` cobre disponibilidade imediata e conteúdo do PDF real. Após a execução automatizada aprovada, `TR91-0510` fica `VALIDADO` somente no critério literal indicado na matriz. `ED91-0481` permanece parcial enquanto acesso integral aos documentos/movimentações e resposta direta não forem demonstrados. Nenhum desses testes substitui avaliação da comissão da POC oficial.",
    "", "## Cobertura individual rastreada", "",
    "A lista abaixo mantém um registro por requisito. Itens `NAO_INICIADO` ou `BLOQUEADO` informam explicitamente o que falta para um cenário demonstrável; itens em desenvolvimento apontam os limites da evidência parcial. A coluna `validacao_fonte` separa conferência da transcrição de validação do produto.",
    "", "| Código | Origem (resumo) | Estado inicial e dados | Passos | Resultado esperado | Evidência | Status |", "|---|---|---|---|---|---|---|"
]
for row in rows:
    source = row["origem_documento_pagina_secao_item"].replace("|", "\\|")
    summary = row["requisito_resumo_fiel"].replace("|", "\\|").replace("\n", " ")
    if row["status"] == "BLOQUEADO":
        if row["validacao_fonte"] == "OCR_PENDENTE":
            initial = "Conferir a página do PDF oficial e decompor o texto OCR."
            steps = "Bloqueado até revisar a transcrição; não tratar OCR como texto confirmado."
            expected = "Critério pendente de fixação após conferência visual da fonte."
        else:
            initial = "Há um impedimento concreto descrito na linha da matriz; preparar dados fictícios após removê-lo."
            steps = row["roteiro_poc"].replace("|", "\\|")
            expected = row["criterio_de_aceite"].replace("|", "\\|")
    elif row["status"] == "NAO_INICIADO":
        initial = "Fonte conferida; estado de teste e dados fictícios aguardam decomposição do requisito."
        steps = row["roteiro_poc"].replace("|", "\\|")
        expected = row["criterio_de_aceite"].replace("|", "\\|")
    elif row["status"] == "NAO_APLICAVEL_JUSTIFICADO":
        initial = "Requisito justificado como não aplicável; revisar a justificativa e sua evidência."
        steps = row["roteiro_poc"].replace("|", "\\|")
        expected = row["criterio_de_aceite"].replace("|", "\\|")
    else:
        initial = f"Dados fictícios apropriados ao módulo `{row['modulo']}`; configuração atual registrada no CSV."
        steps = f"{row['roteiro_poc']} Requisito: {summary}"
        expected = row["criterio_de_aceite"].replace("|", "\\|")
    evidence = row["evidencia"].replace("|", "\\|")
    poc.append(f"| `{row['id']}` | {source} | {initial} | {steps} | {expected} | {evidence} | `{row['status']}` |")
rendered_poc = "\n".join(poc)
if manual_appendix:
    rendered_poc += "\n\n" + manual_appendix
existing_poc_path.write_text(rendered_poc + "\n", encoding="utf-8")
print(f"Relatório de matriz e POC por requisito gerados para {len(rows)} linhas.")
