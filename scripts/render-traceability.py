#!/usr/bin/env python3
"""Render auditable summary and a row-by-row POC register from the CSV source of truth."""
import csv
from collections import Counter
from datetime import date
from pathlib import Path

root = Path(__file__).resolve().parents[1]
base = root / "docs/licitacoes/mata-de-sao-joao-91-2025"
matrix_path = base / "MATRIZ_ADERENCIA.csv"
with matrix_path.open(encoding="utf-8-sig", newline="") as stream:
    rows = list(csv.DictReader(stream, delimiter=";"))

status_counts = Counter(row["status"] for row in rows)
module_counts = Counter(row["modulo"] for row in rows)
nature_counts = Counter(row["natureza"] for row in rows)
ed_rows = [row for row in rows if row["id"].startswith("ED91-")]
tr_rows = [row for row in rows if row["id"].startswith("TR91-")]
external_rows = [row for row in rows if row["natureza"] == "EXTERNO/CONTRATUAL"]

report = [
    "# Matriz de aderência — resumo de rastreabilidade", "",
    f"Gerada em {date.today().isoformat()} a partir de `MATRIZ_ADERENCIA.csv`.", "",
    "## Estado atual", "",
    f"- Total de linhas/requisitos rastreados: **{len(rows)}**.",
    f"- Marcadores do edital §14.1.13 (pp. impressas 13–40) e complementos ED91: **{len(ed_rows)}**.",
    f"- Parágrafos candidatos do Termo de Referência via OCR não conferido: **{len(tr_rows)}**, todos `BLOQUEADO`.",
    f"- Requisitos contratuais/empresariais fora do escopo exclusivo de software: **{len(external_rows)}**.",
    f"- Status `VALIDADO`: **{status_counts['VALIDADO']}**. Não há alegação de 100% de aderência.", "",
    "## Contagem por status", "", "| Status | Linhas |", "|---|---:|"
]
for status, count in sorted(status_counts.items()): report.append(f"| `{status}` | {count} |")
report += ["", "## Cobertura por módulo", "", "| Módulo | Linhas |", "|---|---:|"]
for module, count in sorted(module_counts.items()): report.append(f"| `{module}` | {count} |")
report += ["", "## Natureza", "", "| Natureza | Linhas |", "|---|---:|"]
for nature, count in sorted(nature_counts.items()): report.append(f"| `{nature}` | {count} |")
report += [
    "", "## Regra de aceite", "",
    "A planilha CSV contém o resumo do requisito, origem documental, prioridade, módulo, natureza, critério, backend/frontend, teste, roteiro POC, evidência e status para cada ID. Requisitos com OCR não validado permanecem bloqueados; nenhum status é elevado a `VALIDADO` por ter uma tela, endpoint ou documento. Atualize o CSV após cada incremento e execute `scripts/check-matrix.py`.",
    "", "## Fontes e ressalvas", "",
    "O edital e a Errata I são PDFs pesquisáveis; o Termo de Referência foi digitalizado. Cada linha `TR91` traz página PDF e bloco OCR para localizar o trecho, mas exige comparação visual com `edital-original/TERMODEREFERENCIAEJUSTIFICATIVAPE912025.pdf`. A numeração dos IDs é interna e não altera a numeração oficial dos itens.",
]
(base / "MATRIZ_ADERENCIA.md").write_text("\n".join(report) + "\n", encoding="utf-8")

poc = [
    "# Roteiro da Prova de Conceito (POC)", "",
    "## Condições oficiais", "",
    "O Edital PE 91/2025, §9.9, prevê apresentação da solução em até 72 horas úteis depois da convocação/agendamento, em local indicado pela Administração ou remotamente, com equipamentos e internet providos pelo licitante. O §9.9.1 original limitava a apresentação a 90 minutos. A Errata I §1, p.1, altera esse limite para **90 minutos, prorrogáveis**; não elimina o exame de aderência nem o §9.9.2. A comissão pode solicitar esclarecimentos; a não apresentação ou desconformidade produz os efeitos previstos no edital.",
    "", "## Ambiente e dados fictícios", "",
    "- Duas organizações isoladas: Município Alfa e Município Beta; cada uma tem Protocolo Geral e Secretaria de Obras.",
    "- Requerente: Pessoa de Teste; e-mail `cidadao@example.test`; solicitação sobre análise de via pública.",
    "- Não inserir dados pessoais reais, certificados, credenciais corporativas ou anexos oficiais.",
    "- API local: `http://localhost:8080`; portal: `http://localhost:9000`; API usa Postgres isolado.",
    "- Ensaio de navegador: `npm --prefix frontend run e2e`; inicia API/UI locais e PostgreSQL temporário separado, usa Chrome e remove o volume ao terminar.",
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
    "", "**Limites do ensaio:** o fluxo ainda é um primeiro recorte e não prova os demais requisitos; HTTP Basic/seed existem apenas em perfil `local`; anexos, notificações, assinatura, dados reais, e-mail, integrações externas e proteções contra abuso não fazem parte desta demonstração.",
    "", "## Cobertura individual rastreada", "",
    "A lista abaixo gera um cenário por requisito. Para itens já construídos, a referência de cenário/teste/evidência deve ser específica no CSV. Para todos os demais, o cenário está registrado como pendente e o roteiro só pode ser finalizado depois de implementar e conhecer os dados de teste do módulo. Itens `TR91` estão bloqueados até conferência visual do scan.",
    "", "| Código | Origem (resumo) | Estado inicial e dados | Passos | Resultado esperado | Evidência | Status |", "|---|---|---|---|---|---|---|"
]
for row in rows:
    source = row["origem_documento_pagina_secao_item"].replace("|", "\\|")
    summary = row["requisito_resumo_fiel"].replace("|", "\\|").replace("\n", " ")
    if row["status"] == "BLOQUEADO":
        initial = "Conferir primeiro o scan oficial e decompor o texto OCR."
        steps = "Bloqueado até revisão visual; não simular OCR como requisito confirmado."
        expected = "Critério pendente de fixação após conferência da fonte."
    else:
        initial = f"Dados fictícios apropriados ao módulo `{row['modulo']}`; configuração atual registrada no CSV."
        steps = f"{row['roteiro_poc']} Requisito: {summary}"
        expected = row["criterio_de_aceite"].replace("|", "\\|")
    evidence = row["evidencia"].replace("|", "\\|")
    poc.append(f"| `{row['id']}` | {source} | {initial} | {steps} | {expected} | {evidence} | `{row['status']}` |")
(base / "POC.md").write_text("\n".join(poc) + "\n", encoding="utf-8")
print(f"Relatório de matriz e POC por requisito gerados para {len(rows)} linhas.")
