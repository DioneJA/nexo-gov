#!/usr/bin/env python3
"""Add individually addressable candidates from the scanned official TR OCR.

Each enumerated item is kept as its own candidate, including non-normative objectives.
Unnumbered paragraphs are included when they define scope or contain normative language.
Source transcription review and product validation are tracked as separate states.
"""
import csv
import re
import unicodedata
from collections import Counter
from pathlib import Path
from matrix_id_registry import MatrixIdRegistry

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "docs/licitacoes/mata-de-sao-joao-91-2025"
CSV_PATH = BASE / "MATRIZ_ADERENCIA.csv"
ID_REGISTRY = MatrixIdRegistry(BASE / "MATRIZ_IDS.csv")
OCR_PATH = BASE / "edital-original/texto-extraido/TERMODEREFERENCIAEJUSTIFICATIVAPE912025-ocr-por.txt"
NORMATIVE = re.compile(
    r"\b(devera|deverao|permit|possibil|sera|serao|havera|podera|poderao|capacidade|"
    r"garantir|realizar|manter|fornecer|possuir|disponibilizar|registrar|a contratada|"
    r"o sistema|a plataforma|a solucao|integracao|permitir|constituir|necessaria)\b", re.I)
SECTION = re.compile(r"^\s*(\d{1,2})[.)]\s*(.{3,150})$")
SUBSECTION = re.compile(r"^\s*([a-dA-D])[.)]\s+(.{3,150})$")
BULLET = re.compile(r"^\s*([a-zA-Z]|[ivxlcdm]{1,5}|\d{1,2})[.)]\s+(.+)$")
PAGE_MARKER = re.compile(r"^===== PÁGINA PDF (\d+) =====\s*$", re.M)
VISUALLY_REVIEWED_THROUGH_PDF_PAGE = 69
BOILERPLATE = (
    "prefeitura municipal de mata de sao joao", "secretaria de administracao e financas",
    "para verificar a validade", "assinado por 1 pessoa", "1doc.com.br/verificacao",
)


def folded(text):
    return "".join(char for char in unicodedata.normalize("NFKD", text.casefold()) if not unicodedata.combining(char))


def normalized(text):
    return " ".join(text.split()).strip()


def clean_source_line(line):
    text = normalized(line)
    low = folded(text)
    if not text or low.startswith(BOILERPLATE):
        return ""
    if re.fullmatch(r"[-–]\s*(?:\d{1,3}|[iIlL]{1,3})\s*[-–]|[oO]", text):
        return ""
    return text


def corrected_bullet(line, page_number, section):
    """Restore obvious OCR-dropped list markers where the scanned pages were checked."""
    low = folded(line)
    section_low = folded(section)
    if page_number == 19 and "§21" in section:
        ui_items = (
            ("e cor do tema predominante", "ui-theme-color"),
            ("e fotos para background", "ui-background-photo"),
            ("e brasao/logo", "ui-logo"),
            ("e custo da impressao/copia", "ui-print-cost"),
            ("e customizacao do e-mail de boas-vindas", "ui-welcome-email"),
        )
        for prefix, label in ui_items:
            if low.startswith(prefix):
                return label, line[2:].strip()
    if page_number in (25, 26) and "§32" in section:
        security_items = (
            ("e certificacao digital dos documentos", "fraud-certification"),
            ("e trilha de auditoria dos documentos", "fraud-audit-trail"),
            ("e criptografia dos documentos armazenados", "fraud-encryption"),
            ("e validacao de assinaturas com certificado digital", "fraud-icp-validation"),
        )
        for prefix, label in security_items:
            if low.startswith(prefix):
                return label, line[2:].strip()
    if page_number in (30, 31) and "§36" in section:
        report_items = (
            ("e indicador de eficiencia de cada setor", "report-efficiency"),
            ("e | indicador de eficiencia de cada setor", "report-efficiency"),
            ("e indicador de qualidade de cada setor", "report-quality"),
            ("e | indicador de qualidade de cada setor", "report-quality"),
            ("e indicador de engajamento dos servidores", "report-engagement"),
            ("e | indicador de engajamento dos servidores", "report-engagement"),
            ("e indicador de assiduidade dos servidores", "report-attendance"),
            ("e | indicador de assiduidade dos servidores", "report-attendance"),
        )
        for prefix, label in report_items:
            if low.startswith(prefix):
                return label, re.sub(r"^\s*e\s*\|?\s*", "", line, flags=re.I).strip()
    if page_number == 32 and "§36" in section:
        statistic_items = (
            ("e total de usuarios", "stat-users"),
            ("e | total de usuarios", "stat-users"),
            ("e total de setores", "stat-departments"),
            ("e | total de setores", "stat-departments"),
            ("e total de documentos", "stat-documents"),
            ("e | total de documentos", "stat-documents"),
            ("e anexos e seu tamanho total", "stat-attachments"),
            ("e | anexos e seu tamanho total", "stat-attachments"),
            ("e acessos", "stat-accesses"),
            ("e | acessos", "stat-accesses"),
        )
        for prefix, label in statistic_items:
            if low.startswith(prefix):
                return label, re.sub(r"^\s*e\s*\|?\s*", "", line, flags=re.I).strip()
    if page_number == 39 and "§43" in section and line.lstrip().startswith("*"):
        dashboard_items = (
            ("* capacidade de visualizacao das informacoes necessarias", "dashboard-interpretation"),
            ("* capacidade de realizacao de filtragem", "dashboard-filtering"),
            ("* capacidade de realizacao de download", "dashboard-image-download"),
            ("* capacidade de exportacao de dados", "dashboard-data-export"),
            ("* capacidade de acesso a descricao de cada grafico", "dashboard-chart-description"),
        )
        for prefix, label in dashboard_items:
            if low.startswith(prefix):
                return label, line.lstrip("* ").strip()
    if page_number == 40 and "§43" in section:
        dashboard_items = (
            ("capacidade de visualizacao em tela cheia", "dashboard-fullscreen"),
            ("capacidade de visualizacao do quantitativo de servidores da", "dashboard-staff-count"),
            ("capacidade de visualizacao do quantitativo de usuarios externos", "dashboard-external-count"),
            ("capacidade de visualizacao do quantitativo de setores vinculados", "dashboard-sector-count"),
            ("capacidade de visualizacao do percentual de eficiencia", "dashboard-efficiency"),
            ("capacidade de visualizacao do percentual de engajamento", "dashboard-engagement"),
            ("capacidade de visualizacao da qualidade da prestacao", "dashboard-service-quality"),
            ("capacidade de verificacao da quantidade de servidores", "dashboard-active-staff"),
            ("capacidade de identificacao percentual da quantidade de servidores", "dashboard-activity-windows"),
            ("capacidade de identificacao da quantidade de dias de inatividade", "dashboard-inactivity-days"),
        )
        for prefix, label in dashboard_items:
            if low.startswith(prefix):
                return label, line.strip()
    if page_number == 41 and "§43" in section:
        dashboard_items = (
            ("capacidade de realizacao de busca nominal", "dashboard-nominal-inactivity"),
            ("capacidade de visualizacao do quantitativo do total de paginas", "dashboard-pages-native-count"),
        )
        for prefix, label in dashboard_items:
            if low.startswith(prefix):
                return label, line.strip()
    if page_number == 42 and "§43" in section and line.lstrip().startswith("*"):
        dashboard_items = (
            ("* capacidade de visualizacao do quantitativo de assinaturas solicitadas", "dashboard-signatures-requested"),
            ("* capacidade de visualizacao do quantitativo do tempo medio decorrido", "dashboard-mean-signature-time"),
            ("* capacidade de disponibilizacao de grafico", "dashboard-resolved-by-user"),
        )
        for prefix, label in dashboard_items:
            if low.startswith(prefix):
                return label, line.lstrip("* ").strip()
    if page_number == 43 and "§45" in section:
        obligation_items = (
            ("manter-se, durante o periodo de vigencia", "d"),
            ("planejar, desenvolver, implantar, executar e manter", "e"),
            ("reportar a prefeitura imediatamente", "f"),
            ("reparar, corrigir, remover, reconstruir ou substituir", "g"),
            ("nao transferir a outrem", "h"),
            ("permitir o acompanhamento, pela prefeitura", "i"),
        )
        for prefix, label in obligation_items:
            if low.startswith(prefix):
                return label, line.strip()
    if page_number == 46 and "§3" in section:
        labels = {
            "denuncia": "manifestation-denuncia",
            "elogio": "manifestation-elogio",
            "reclamacao": "manifestation-reclamacao",
            "solicitacao": "manifestation-solicitacao",
            "sugestao": "manifestation-sugestao",
        }
        quoted = re.search(r'["“]([^"”]+)["”]', line)
        if quoted and folded(quoted.group(1)) in labels:
            body = re.sub(r"^\s*(?:e|¢|\*)\s*", "", line, flags=re.I).strip()
            return labels[folded(quoted.group(1))], body
    if page_number == 48 and "§3" in section:
        labels = {
            "sem sigilo": "identification-public",
            "sigilosa": "identification-confidential",
            "anonimo": "identification-anonymous",
        }
        quoted = re.search(r'["“]([^"”]+)["”]', line)
        if quoted and folded(quoted.group(1)) in labels:
            body = re.sub(r"^\s*(?:e|¢|\*)\s*", "", line, flags=re.I).strip()
            return labels[folded(quoted.group(1))], body
    if page_number == 7 and "§5.c" in section_low:
        for prefix, label in (
            ("e administrador geral:", "acl-admin"), ("e nivel 1:", "acl-level1"),
            ("e nivel 2:", "acl-level2"), ("e nivel 3:", "acl-level3"),
        ):
            if low.startswith(prefix):
                return label, line[2:].strip()
    if page_number == 8 and "§5.d" in section_low and low.startswith("e "):
        portal_items = (
            ("e abertura de atendimento", "portal-task-1"),
            ("e consulta de autenticidade", "portal-task-2"),
            ("e consulta de boletos", "portal-task-3"),
            ("e consulta, acompanhamento", "portal-task-4"),
            ("e anexo de novos arquivos", "portal-task-5"),
        )
        for prefix, label in portal_items:
            if low.startswith(prefix):
                return label, line[2:].strip()
    if page_number == 3 and "caracteristicas tecnicas" in section_low:
        sla = re.match(r"^o\s+At[eé]\s+(2|4|24)\s+horas?\s*[-–]\s*(.+)$", line, re.I)
        if sla:
            return f"d/SLA-{sla.group(1)}h", f"Até {sla.group(1)} horas — {sla.group(2)}"
    fixes = (
        (page_number == 2 and "objetivo do projeto" in section_low and low.startswith("|. aumentar a eficiencia"), "l"),
        (page_number == 3 and "objetivo do projeto" in section_low and low.startswith("criacao de um sistema de notificacao"), "r"),
        (page_number == 3 and "objetivo do projeto" in section_low and low.startswith(". assegurar que a solucao"), "s"),
        (page_number == 3 and "objetivo do projeto" in section_low and low.startswith("permitir o rastreamento"), "t"),
        (page_number == 4 and "caracteristicas tecnicas" in section_low and low.startswith("|. atender as obrigacoes"), "l"),
    )
    for matches, label in fixes:
        if matches:
            return label, re.sub(r"^\s*(?:\||\.)?\s*[.)]?\s*", "", line).strip()

    technical_markers = (
        (3, ". Sistema 100% WEB", "a"),
        (3, ". Acesso seguro:", "b"),
        (3, "Servidor de aplicação e banco de dados:", "c"),
        (3, ". Garantia de SLA", "d"),
        (3, ". Cópia de segurança", "e"),
        (3, "Sistema de notificação de e-mail", "f"),
        (3, ". E-mails de saída enviados", "g"),
        (3, ". Por se tratar de um serviço", "h"),
    )
    if "caracteristicas tecnicas" in section_low:
        for page, prefix, label in technical_markers:
            if page_number == page and line.startswith(prefix):
                return label, line.lstrip(". ").strip()

    item = BULLET.match(line)
    if item:
        return item.group(1).lower(), item.group(2)
    return None


def extract_candidates(text):
    markers = list(PAGE_MARKER.finditer(text))
    if len(markers) != 69:
        raise SystemExit(f"OCR deve conter marcador para 69 páginas; encontrado(s) {len(markers)}.")

    candidates = []
    section = "Termo de Referência, anexo técnico"
    appendix_ii_active = False
    normal_lines = []
    normal_page = None
    normal_end = None
    normal_section = None
    bullet = None

    def flush_normal():
        nonlocal normal_lines, normal_page, normal_end, normal_section
        body = normalized(" ".join(normal_lines))
        if body and (len(body) >= 35 or (normal_section and normal_section.strip().startswith("§"))):
            split_sentences = (normal_section and "(Anexo II)" in normal_section) or (
                normal_page == 41 and normal_section and "§43" in normal_section
            )
            sentences = ([sentence.strip() for sentence in re.split(r"(?<=[.!?])\s+(?=[A-ZÁÉÍÓÚÂÊÔÃÕÇ])", body)
                          if sentence.strip()] if split_sentences else [body])
            for sentence in sentences:
                marker = ""
                if normal_page == 41 and normal_section and "§43" in normal_section:
                    body_low = folded(sentence)
                    dashboard_paragraphs = (
                        ("que forem resolvidos durante um periodo", "dashboard-resolved"),
                        ("paginas decorrentes de documentos anexados", "dashboard-pages-native-count"),
                        ("grafico do quantitativo de processos tramitados na plataforma pela administracao publica", "dashboard-throughput-chart"),
                        ("grafico de distribuicao percentual do status dos processos", "dashboard-archive-chart"),
                        ("grafico do quantitativo de processos tramitados na plataforma, filtravel", "dashboard-process-filters"),
                        ("quantitativo de processos recebidos, total ou por determinados setores", "dashboard-received-count"),
                        ("quantitativo de processos criados e recebidos, total ou por determinados setores, modulos", "dashboard-created-count"),
                        ("quantitativo de processos criados e recebidos, total ou por determinados setores", "dashboard-created-received"),
                        ("quantitativo do tempo medio de resolucao", "dashboard-mean-resolution"),
                    )
                    for prefix, label in dashboard_paragraphs:
                        if prefix in body_low:
                            marker = label
                            break
                candidates.append({
                    "page_start": normal_page,
                    "page_end": normal_end or normal_page,
                    "section": normal_section,
                    "marker": marker,
                    "body": sentence,
                })
        normal_lines = []
        normal_page = None
        normal_end = None
        normal_section = None

    def flush_bullet():
        nonlocal bullet
        if bullet:
            body = normalized(" ".join(bullet["lines"]))
            if body:
                bullet["body"] = body
                candidates.append(bullet)
        bullet = None

    for page_index, marker in enumerate(markers):
        page_number = int(marker.group(1))
        end = markers[page_index + 1].start() if page_index + 1 < len(markers) else len(text)
        raw_page = text[marker.end():end]
        page_lines = [clean_source_line(raw_line) for raw_line in raw_page.splitlines()]
        while page_lines and not page_lines[0]:
            page_lines.pop(0)
        while page_lines and not page_lines[-1]:
            page_lines.pop()
        for line in page_lines:
            if not line:
                flush_normal()
                flush_bullet()
                continue

            if folded(line).strip(" .:") == "anexo ii":
                flush_normal()
                flush_bullet()
                appendix_ii_active = True
                section = "Anexo II — especificação técnica"
                continue
            if folded(line).strip(" .:") == "anexo iii":
                flush_normal()
                flush_bullet()
                appendix_ii_active = False
                section = "Anexo III — modelo de proposta e preços"
                continue

            if folded(line).strip(" .:") == "observacoes complementares":
                flush_normal()
                flush_bullet()
                continue

            heading = SECTION.match(line)
            if heading and re.search(r"[A-Za-zÀ-ÿ]", heading.group(2)) and line == line.upper():
                flush_normal()
                flush_bullet()
                appendix_suffix = " (Anexo II)" if appendix_ii_active else ""
                section = f"§{heading.group(1)} {heading.group(2).strip()}{appendix_suffix}"
                continue

            folded_line = folded(line.lstrip(". "))
            if folded_line.startswith("caracteristicas tecnicas da solucao"):
                flush_normal()
                flush_bullet()
                section = "§4 CARACTERISTICAS TÉCNICAS DA SOLUÇÃO"
                continue

            subsection = SUBSECTION.match(line)
            if subsection and (folded(section).startswith("§5 ") or folded(section).startswith("§5.")):
                flush_normal()
                flush_bullet()
                section = f"§5.{subsection.group(1).lower()} {subsection.group(2).strip()}"
                continue

            item = corrected_bullet(line, page_number, section)
            if item:
                flush_normal()
                flush_bullet()
                bullet = {
                    "page_start": page_number,
                    "page_end": page_number,
                    "section": section,
                    "marker": item[0],
                    "lines": [item[1]],
                }
                continue

            if bullet is not None:
                bullet["lines"].append(line)
                bullet["page_end"] = page_number
            else:
                if normal_page is None:
                    normal_page = page_number
                    normal_end = page_number
                    normal_section = section
                normal_lines.append(line)
                normal_end = page_number

        # Preserve only visibly incomplete text across a page boundary.
        if normal_lines and re.search(r"[.;:!?][\"'”’)]?$", normalized(" ".join(normal_lines))):
            flush_normal()
        if bullet and re.search(r"[.;:!?][\"'”’)]?$", normalized(" ".join(bullet["lines"]))):
            flush_bullet()

    flush_normal()
    flush_bullet()

    filtered = []
    for candidate in candidates:
        body = candidate["body"]
        low = folded(body)
        section_low = folded(candidate["section"])
        is_scope = candidate["section"].strip().startswith("§")
        if low.strip() in {"ie]", "ie", "i e]"}:
            continue
        if "§45" in candidate["section"] and re.fullmatch(r"(?:[a-i]|[0-9])\)", low.strip()):
            continue
        if not candidate["marker"] and low.rstrip(":.;") in {
            "devera conter", "conter", "informacoes gerenciais",
            "progresso no sistema de treinamento de usuarios", "quantitativo de documentos",
            "acessos diarios", "estatistica", "informacoes sobre consumo de recursos",
            "informacoes de auditoria", "graficos de utilizacao", "relatorio georreferenciado",
            "abrangem os seguintes servicos para atendimento das demandas",
            "funcionalidades de relatorios e analises", "indicadores visuais de desempenho",
            "monitoramento de usuarios e setores", "metricas de eficiencia e engajamento",
            "analise de processos e documentos", "graficos de tramite e desempenho",
        }:
            continue
        if ("§45" in candidate["section"] and
                low.startswith("adicionalmente as responsabilidades estabelecidas nos demais topicos constantes neste termo")):
            continue
        if ("§36" in candidate["section"] and
                low.startswith("de acordo com a utilizacao da plataforma, serao gerados automaticamente indicadores")):
            continue
        if "§36" in candidate["section"]:
            for heading in (
                "progresso no sistema de treinamento de usuarios",
                "quantitativo de documentos", "estatistica",
                "informacoes sobre consumo de recursos",
            ):
                if low.startswith(heading + " "):
                    candidate["body"] = normalized(candidate["body"][len(heading):])
                    low = folded(candidate["body"])
                    break
        if candidate["marker"] or is_scope or NORMATIVE.search(low):
            filtered.append(candidate)

    merged = []
    for candidate in filtered:
        body_low = folded(candidate["body"])
        is_url_continuation = (not candidate["marker"] and
                               (body_low.startswith(("2.htm#", "https://", "http://", "www.")) or
                                body_low.startswith("2.htm")))
        if (is_url_continuation and merged and not merged[-1]["marker"] and
                merged[-1]["page_end"] == candidate["page_start"] and
                "http" in folded(merged[-1]["body"])):
            merged[-1]["body"] = normalized(merged[-1]["body"] + " " + candidate["body"])
        elif (not candidate["marker"] and merged and not merged[-1]["marker"] and
              merged[-1]["section"] == candidate["section"] and
              ((folded(merged[-1]["body"]).endswith("mediante") and body_low.startswith("identificacao do usuario")) or
               (folded(merged[-1]["body"]).endswith("qual usuario") and body_low.startswith("responsavel na linha do tempo")) or
               (folded(merged[-1]["body"]).endswith("historico de usuarios ao") and body_low.startswith("longo do mes")))):
            merged[-1]["body"] = normalized(merged[-1]["body"] + " " + candidate["body"])
            merged[-1]["page_end"] = candidate["page_end"]
        elif (not candidate["marker"] and merged and not merged[-1]["marker"] and
              merged[-1]["section"] == candidate["section"] and "§4" in candidate["section"] and
              folded(merged[-1]["body"]).endswith("anexos identificados") and
              body_low.startswith("por assunto de protocolo")):
            merged[-1]["body"] = normalized(merged[-1]["body"] + " " + candidate["body"])
            merged[-1]["page_end"] = candidate["page_end"]
        elif (not candidate["marker"] and merged and not merged[-1]["marker"] and
              merged[-1]["section"] == candidate["section"] and "§7" in candidate["section"] and
              folded(merged[-1]["body"]).endswith("atraves de composicoes") and
              body_low.startswith("multiplas, favorecendo o envio de processos administrativos")):
            merged[-1]["body"] = normalized(merged[-1]["body"] + " " + candidate["body"])
            merged[-1]["page_end"] = candidate["page_end"]
        elif (not candidate["marker"] and merged and not merged[-1]["marker"] and
              merged[-1]["section"] == candidate["section"] and "§9" in candidate["section"] and
              folded(merged[-1]["body"]).endswith("configuracao de campo") and
              body_low.startswith("personalizado com funcionalidade de validacao automatica da existencia de cnpj")):
            merged[-1]["body"] = normalized(merged[-1]["body"] + " " + candidate["body"])
            merged[-1]["page_end"] = candidate["page_end"]
        elif (not candidate["marker"] and merged and not merged[-1]["marker"] and
              merged[-1]["section"] == candidate["section"] and "§10" in candidate["section"] and
              folded(merged[-1]["body"]).endswith("caso o cpf nao estiver associado") and
              body_low.startswith("a um cadastro existente")):
            merged[-1]["body"] = normalized(merged[-1]["body"] + " " + candidate["body"])
            merged[-1]["page_end"] = candidate["page_end"]
        elif (not candidate["marker"] and merged and not merged[-1]["marker"] and
              merged[-1]["section"] == candidate["section"] and "§18" in candidate["section"] and
              ((folded(merged[-1]["body"]).endswith("mata de sao joao") and body_low.startswith("necessite realizar a hospedagem")) or
               (folded(merged[-1]["body"]).endswith("devera cooperar") and body_low.startswith("e auxiliar, prestando total suporte")) or
               (folded(merged[-1]["body"]).endswith("pedido da") and body_low.startswith("administracao, limitada a uma copia")) or
               (folded(merged[-1]["body"]).endswith("ultima ie]") and body_low.startswith("solicitacao, a contratada disponibilizara")))):
            left = merged[-1]["body"]
            if folded(left).endswith("ultima ie]"):
                left = left[:left.rfind("IE]")].rstrip()
            merged[-1]["body"] = normalized(left + " " + candidate["body"])
            merged[-1]["page_end"] = candidate["page_end"]
        elif (not candidate["marker"] and merged and not merged[-1]["marker"] and
              merged[-1]["section"] == candidate["section"] and "§33" in candidate["section"] and
              folded(merged[-1]["body"]).endswith("por meio de") and
              body_low.startswith("digitacao do caractere especifico")):
            merged[-1]["body"] = normalized(merged[-1]["body"] + " " + candidate["body"])
            merged[-1]["page_end"] = candidate["page_end"]
        elif (not candidate["marker"] and merged and not merged[-1]["marker"] and
              merged[-1]["section"] == candidate["section"] and "§36" in candidate["section"] and
              folded(merged[-1]["body"]).endswith("informado pela") and
              body_low.startswith("administracao publica")):
            merged[-1]["body"] = normalized(merged[-1]["body"] + " " + candidate["body"])
            merged[-1]["page_end"] = candidate["page_end"]
        elif (not candidate["marker"] and merged and not merged[-1]["marker"] and
              merged[-1]["section"] == candidate["section"] and "§36" in candidate["section"] and
              folded(merged[-1]["body"]).endswith("com as mesmas caracteristicas") and
              body_low.startswith("escolhidas, este podera ser acessado")):
            merged[-1]["body"] = normalized(merged[-1]["body"] + " " + candidate["body"])
            merged[-1]["page_end"] = candidate["page_end"]
        else:
            merged.append(candidate)
    filtered = []
    for candidate in merged:
        if "§12" in candidate["section"]:
            parts = re.split(r"(?=Ferramenta para importação de contatos em vCARD\.)", candidate["body"], flags=re.I)
            if len(parts) == 2 and parts[0].rstrip().endswith("."):
                first = dict(candidate, body=parts[0].strip())
                second = dict(candidate, body=parts[1].strip())
                filtered.extend((first, second))
                continue
        filtered.append(candidate)

    objective_items = {
        row["marker"] for row in filtered
        if "objetivo do projeto" in folded(row["section"]) and row["page_start"] in (2, 3)
    }
    if not set("abcdefghijklmnopqrst").issubset(objective_items):
        missing = sorted(set("abcdefghijklmnopqrst") - objective_items)
        raise SystemExit(f"OCR enumerado do §3 não foi extraído integralmente; alíneas faltantes: {missing}")
    objective_q = next(
        row for row in filtered
        if "objetivo do projeto" in folded(row["section"]) and row["marker"] == "q"
    )
    if "atendimento presencial" not in folded(objective_q["body"]):
        raise SystemExit("A continuação da alínea q do §3 não foi juntada entre as páginas 2 e 3.")
    sla_items = {row["marker"] for row in filtered if row["marker"].startswith("d/SLA-")}
    if sla_items != {"d/SLA-2h", "d/SLA-4h", "d/SLA-24h"}:
        raise SystemExit(f"Subitens de SLA do §4 ausentes ou duplicados: {sorted(sla_items)}")
    required_phrases = (
        "certificado de seguranca ssl", "data center certificado", "99% de disponibilidade",
        "bloqueio de abuso/spam/robo", "georreferenciadas", "pentest whitebox", "e-tcm",
        "inteligencia artificial",
    )
    section_four = " ".join(folded(row["body"]) for row in filtered if "caracteristicas tecnicas" in folded(row["section"]))
    missing_phrases = [phrase for phrase in required_phrases if phrase not in section_four]
    if missing_phrases:
        raise SystemExit(f"OCR do §4 não trouxe itens técnicos esperados: {missing_phrases}")
    internal_access = [row for row in filtered if "§5.c" in folded(row["section"])]
    internal_text = " ".join(folded(row["body"]) for row in internal_access)
    for phrase in ("recuperacao de senha", "certificado digital", "token", "suspensao de seu acesso", "auditor"):
        if phrase not in internal_text:
            raise SystemExit(f"OCR de §5.c não trouxe a exigência esperada: {phrase}")
    acl_labels = {row["marker"] for row in internal_access}
    if not {"acl-admin", "acl-level1", "acl-level2", "acl-level3"}.issubset(acl_labels):
        raise SystemExit("OCR de §5.c não separou os quatro grupos de permissão visualmente identificados.")
    external_access = [row for row in filtered if "§5.d" in folded(row["section"])]
    portal_tasks = {row["marker"] for row in external_access}
    if not {f"portal-task-{number}" for number in range(1, 6)}.issubset(portal_tasks):
        raise SystemExit("OCR de §5.d não separou as cinco ações do portal externo.")
    permission_continuation = [row for row in internal_access if "segmentacao das permissoes" in folded(row["body"])]
    if not permission_continuation or "resolucao no 50/2022" not in folded(permission_continuation[0]["body"]):
        raise SystemExit("A continuação do requisito de níveis de acesso não foi juntada entre as páginas 6–7.")
    later_text = " ".join(folded(row["body"]) for row in filtered)
    for phrase in (
        "todos os modulos utilizarao do sistema de notificacoes multicanal",
        "possibilidade de parametrizacao para adicao de campos personalizados",
        "impressao de documentos em layout padronizado",
        "organograma dinamico criado automaticamente",
        "calendario mensal",
        "qr-code vinculado de modo a facilitar a rastreabilidade",
        "possibilitar excluir um prazo/atividade, incluindo anotacao de qual usuario responsavel",
        "registro e rastreamento de documentos por meio de qr code e mediante identificacao do usuario",
    ):
        if phrase not in later_text:
            raise SystemExit(f"OCR das páginas 9–12 não preservou o trecho de referência: {phrase}")
    if not any("§10" in row["section"] and "status de leitura" in folded(row["body"]) for row in filtered):
        raise SystemExit("O cabeçalho §10 não foi reconhecido; requisito de leitura ficou na seção anterior.")
    if not any("§11" in row["section"] and "historico de usuarios" in folded(row["body"]) for row in filtered):
        raise SystemExit("O cabeçalho §11 não foi reconhecido; requisito de plano da Prefeitura ficou ausente.")
    source_sections = {row["section"].split()[0] for row in filtered}
    if not {"§12", "§13", "§14", "§15", "§16", "§17"}.issubset(source_sections):
        raise SystemExit("OCR das páginas 13–16 não reconheceu todas as seções 12–17.")
    contact_tools = [row["body"] for row in filtered if "§12" in row["section"] and "ferramenta para importacao" in folded(row["body"])]
    if not any("importacao e exportacao de contatos em csv" in folded(body) for body in contact_tools):
        raise SystemExit("O requisito de importação/exportação CSV de contatos ficou ausente.")
    if not any("importacao de contatos em vcard" in folded(body) for body in contact_tools):
        raise SystemExit("O requisito de importação vCard de contatos ficou ausente.")
    later_sections_text = " ".join(folded(row["section"] + " " + row["body"]) for row in filtered)
    for phrase in (
        "possibilidade de unificacao de contatos repetidos", "documentos emitidos serao direcionados a um setor",
        "diferenciacao do acesso interno", "central de atendimento", "reabertura do documento",
        "sistema de notificacao multicanais", "os canais disponiveis serao: e-mail, sms",
    ):
        if phrase not in later_sections_text:
            raise SystemExit(f"OCR das páginas 13–16 não preservou o trecho de referência: {phrase}")
    hosting_text = " ".join(folded(row["body"]) for row in filtered if "§18" in row["section"])
    for phrase in (
        "servidor seguro", "99% do horario padrao", "backup diario, semanal e mensal",
        "prestando total suporte em tal procedimento", "por nao menos que 180 dias da ultima solicitacao",
    ):
        if phrase not in hosting_text:
            raise SystemExit(f"OCR da hospedagem segura, pp.17–19, não preservou: {phrase}")
    ui_markers = {row["marker"] for row in filtered if "§21" in row["section"]}
    if not {"ui-theme-color", "ui-background-photo", "ui-logo", "ui-print-cost", "ui-welcome-email"}.issubset(ui_markers):
        raise SystemExit("OCR da personalização da interface não separou os cinco itens visualizados na p.19.")
    section_ids = {row["section"].split()[0] for row in filtered}
    if not {f"§{number}" for number in range(18, 46)}.issubset(section_ids):
        raise SystemExit("OCR das páginas 17–43 não reconheceu todas as seções 18–45.")
    reviewed_text = " ".join(
        folded(row["section"] + " " + row["body"])
        for row in filtered if row["page_start"] >= 21 and row["page_end"] <= 24
    )
    for phrase in (
        "busca avancada dentro dos documentos gerados", "reconhecimento optico de caracteres",
        "contrassenha gerada tera numero limitado de uso", "marcadores do setor ficarao disponiveis",
        "visualizacao das demandas em modo kanban", "demandas com georeferenciamento em modo mapa",
        "geracao automatica de certificados no padrao x509", "formato pades",
        "fluxos de signatarios sequenciais e em paralelo",
    ):
        if phrase not in reviewed_text:
            raise SystemExit(f"A revisão visual das páginas 21–24 não preservou o trecho: {phrase}")
    signature_editor_text = " ".join(
        folded(row["section"] + " " + row["body"])
        for row in filtered if row["page_start"] >= 25 and row["page_end"] <= 28
    )
    for phrase in (
        "a cada nova assinatura no documento sera enviado um e-mail",
        "mecanismos que visem a seguranca e prevencao de fraudes",
        "certificacao digital dos documentos", "trilha de auditoria dos documentos",
        "criptografia dos documentos armazenados", "validacao de assinaturas com certificado digital",
        "certificados digitais individuais", "co-assinar documentos previamente assinados",
        "a1 ou a3 emitido icp-brasil", "co-assinaturas na linha do tempo",
        "editor de texto em tela cheia", "salvamento automatico do rascunho",
        "por um unico servidor habilitado", "variaveis de conteudo",
        "modelos de resposta", "hiperlink automatico entre os documentos",
        "passara a ter acesso automaticamente ao documento",
    ):
        if phrase not in signature_editor_text:
            raise SystemExit(f"A revisão visual das páginas 25–28 não preservou o trecho: {phrase}")
    spellcheck = next(
        (row for row in filtered if row["page_start"] == 27 and "corretor ortografico" in folded(row["body"])),
        None,
    )
    if not spellcheck or spellcheck["page_end"] != 28 or "formatacao com tabelas" not in folded(spellcheck["body"]):
        raise SystemExit("A continuação de corretor ortográfico do §33 não foi juntada entre as páginas 27–28.")
    reports_text = " ".join(
        folded(row["section"] + " " + row["body"])
        for row in filtered if row["page_start"] >= 29 and row["page_end"] <= 32
    )
    for phrase in (
        "sistema de classificacao e busca por etiqueta digital", "insercao de etiquetas digitais em lote",
        "organizacao das etiquetas de maneira estruturada", "material de treinamento preferencialmente em formato de video",
        "aplicacao de avaliacao de aprendizado", "certificado eletronico de conclusao",
        "indicador de eficiencia de cada setor", "indicador de qualidade de cada setor",
        "indicador de engajamento dos servidores", "indicador de assiduidade dos servidores",
        "90 (noventa) dias de inatividade", "exportacao dos relatorios de indicadores em formato csv",
        "relatorio de progresso dos servidores habilitados inscritos", "relatorio quantitativo de documentos por setor",
        "geracao assincrona de relatorio", "ultimos 5 (cinco) relatorios", "total de usuarios e por nivel de acesso",
        "volume de anexos", "economia estimada de recursos", "preco da impressao por folha",
        "acessos.",
    ):
        if phrase not in reports_text:
            raise SystemExit(f"A revisão visual das páginas 29–32 não preservou o trecho: {phrase}")
    attendance = next((row for row in filtered if row["marker"] == "report-attendance"), None)
    if not attendance or attendance["page_end"] != 31 or "45 (quarenta e cinco) dias" not in attendance["body"]:
        raise SystemExit("O indicador de assiduidade do §36 não foi unido entre as páginas 30–31.")
    if not any(row["marker"] == "stat-accesses" and folded(row["body"]) == "acessos." for row in filtered):
        raise SystemExit("O item curto ‘Acessos’ da lista estatística do §36 foi perdido.")
    if not any("preco da impressao por folha informado pela administracao publica" in folded(row["body"]) for row in filtered):
        raise SystemExit("A continuação do critério de custo de impressão foi separada na p.32.")
    audits_text = " ".join(
        folded(row["section"] + " " + row["body"])
        for row in filtered if row["page_start"] >= 33 and row["page_end"] <= 36
    )
    for phrase in (
        "relatorios de eventos ocorridos dentro da plataforma", "numero do acesso",
        "sistema operacional", "versao do navegador utilizado", "exportacao de informacoes dos eventos no formato csv",
        "painel de informacoes", "solicitacao de sua geracao", "forma assincrona",
        "relatorio georreferenciado nas modalidades de calor ou com pinos",
        "periodos de ate 20 dias apos sua emissao", "entre o 20o e 30o dia apos sua emissao",
        "quatro niveis hierarquicos",
        "codigo numerico unico", "suspensao de um codigo de classificacao",
        "importacao do plano de classificacao completo", "priorizacao da classificacao automatica do assunto",
        "classificacao automatica e retroativa", "servico de operacao assistida em producao",
        "10 (dez) dias uteis nas dependencias", "conversao e migracao dos dados atualmente existentes",
        "informacoes confidenciais", "dados sao exclusivamente de propriedade da contratante",
        "codigo fonte do programa sera de exclusiva propriedade da contratada",
    ):
        if phrase not in audits_text:
            raise SystemExit(f"A revisão visual das páginas 33–36 não preservou o trecho: {phrase}")
    if not any(row["page_start"] == 36 and "lgpd" in folded(row["body"]) for row in filtered):
        raise SystemExit("A cláusula LGPD iniciada na p.36 não foi preservada no inventário.")
    if not any(
        "§36" in row["section"] and "com as mesmas caracteristicas escolhidas" in folded(row["body"])
        for row in filtered
    ):
        raise SystemExit("A continuação de relatório salvo foi separada no OCR da p.33.")
    support_items = {row["marker"] for row in filtered if "§40" in row["section"] and row["marker"] in set("abcdef")}
    if support_items != set("abcdef"):
        raise SystemExit(f"OCR do §40 não separou integralmente os seis serviços de suporte: {sorted(support_items)}")
    dashboard_items = {row["marker"] for row in filtered if "§43" in row["section"]}
    if not {"dashboard-interpretation", "dashboard-filtering", "dashboard-image-download", "dashboard-data-export", "dashboard-chart-description"}.issubset(dashboard_items):
        raise SystemExit("OCR do §43 não preservou os cinco itens de relatórios/análises da p.39.")
    expected_dashboard = {"dashboard-fullscreen", "dashboard-staff-count", "dashboard-external-count", "dashboard-sector-count", "dashboard-efficiency", "dashboard-engagement", "dashboard-service-quality", "dashboard-active-staff", "dashboard-activity-windows", "dashboard-inactivity-days"}
    if not expected_dashboard.issubset(dashboard_items):
        raise SystemExit(f"OCR do §43 não separou as métricas da p.40; faltam {sorted(expected_dashboard - dashboard_items)}, presentes {sorted(dashboard_items)}.")
    if not any("§41" in row["section"] and "desclassificada" in folded(row["body"]) for row in filtered):
        raise SystemExit("A consequência da reprovação na POC (§41) não foi preservada.")
    tramita_text = " ".join(folded(row["body"]) for row in filtered if "§42" in row["section"])
    for phrase in ("tramita gov.br", "termo de adesao", "homologacao", "numero de registro eletronico", "recebimento, pela administracao publica"):
        if phrase not in tramita_text:
            raise SystemExit(f"A integração Tramita GOV.BR (§42) não preservou: {phrase}")
    support_text = " ".join(folded(row["body"]) for row in filtered if "§40" in row["section"])
    for phrase in (
        "suporte sem custos e ilimitado", "atendimento por chat", "investigacao e analise de incidentes",
        "relatorio de atualizacao de versao", "restabelecimento do servico", "remocao de causa raiz",
        "manutencoes preventivas", "compatibilidade da solucao", "relatorio mensal de acompanhamento",
        "segunda a sexta-feira em horario comercial",
    ):
        if phrase not in support_text:
            raise SystemExit(f"O suporte e a operação (§40) não preservaram: {phrase}")
    lgpd_clause = next(
        (row for row in filtered if row["page_start"] == 36 and "lgpd" in folded(row["body"])),
        None,
    )
    if not lgpd_clause or lgpd_clause["page_end"] != 37 or "da honra e da imagem" not in folded(lgpd_clause["body"]):
        raise SystemExit("A continuação da cláusula LGPD não foi unida e cotejada entre as páginas 36–37.")
    if not any(
        "§43" in row["section"] and "podera disponibilizar paineis de indicadores" in folded(row["body"])
    for row in filtered
    ):
        raise SystemExit("A declaração facultativa de painéis visuais do §43 não foi preservada.")
    final_dashboard_markers = {
        "dashboard-nominal-inactivity", "dashboard-created-received", "dashboard-resolved",
        "dashboard-pages-native-count", "dashboard-throughput-chart", "dashboard-archive-chart",
        "dashboard-process-filters", "dashboard-received-count", "dashboard-created-count",
        "dashboard-mean-resolution", "dashboard-signatures-requested", "dashboard-mean-signature-time",
        "dashboard-resolved-by-user",
    }
    if not final_dashboard_markers.issubset(dashboard_items):
        raise SystemExit(f"OCR do §43 não separou as métricas das páginas 41–42; faltam {sorted(final_dashboard_markers - dashboard_items)}.")
    update_text = " ".join(folded(row["body"]) for row in filtered if "§44" in row["section"])
    for phrase in ("sem nenhuma interrupcao no servico", "no minimo 1 semana de antecedencia", "horario de brasilia"):
        if phrase not in update_text:
            raise SystemExit(f"A manutenção programada (§44) não preservou: {phrase}")
    contract_items = {row["marker"] for row in filtered if "§45" in row["section"]}
    if not set("abcdefghi").issubset(contract_items):
        raise SystemExit(f"OCR do §45 não separou as obrigações a–i: {sorted(contract_items)}")
    appendix_one = [row for row in filtered if "§1" in row["section"] and "(Anexo II)" in row["section"]]
    appendix_text = " ".join(folded(row["body"]) for row in appendix_one)
    for phrase in (
        "numeracao automatica e sequencial", "geracao automatica de qr code", "tela cheia",
        "respostas e encaminhamentos", "apenas o remetente e o destinatario", "folha de rosto",
        "organograma cadastrado", "anexacao de multiplos arquivos", "modelos de documentos",
        "formato pdf para assinatura eletronica", "multiplos prazos", "listagem dos setores envolvidos",
    ):
        if phrase not in appendix_text:
            raise SystemExit(f"Anexo II, item 1 não preservou o requisito: {phrase}")
    communication_continuation = " ".join(
        folded(row["body"]) for row in filtered
        if "§1" in row["section"] and "(Anexo II)" in row["section"] and row["page_start"] == 45
    )
    if not all(phrase in communication_continuation for phrase in ("linha do tempo", "historico claro e conciso")):
        raise SystemExit("A continuação do item 1 do Anexo II não preservou a linha do tempo na p.45.")
    circular_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§2" in row["section"] and "(Anexo II)" in row["section"] and row["page_start"] <= 45
    )
    for phrase in (
        "carater informativo", 'prioridade "urgente"', "data e hora", "resposta aberta",
        "restrita", "multiplos arquivos", "interrupcao das notificacoes", "setor nao envolvido",
        "arquivamento automatico", "meramente informativa",
    ):
        if phrase not in circular_text:
            raise SystemExit(f"Anexo II, item 2 não preservou a condição da Circular: {phrase}")
    manifestation_items = {row["marker"] for row in filtered if row["marker"].startswith("manifestation-")}
    if manifestation_items != {
        "manifestation-denuncia", "manifestation-elogio", "manifestation-reclamacao",
        "manifestation-solicitacao", "manifestation-sugestao",
    }:
        raise SystemExit(f"Anexo II, item 3 não separou as cinco finalidades da Ouvidoria: {sorted(manifestation_items)}")
    identification_items = {row["marker"] for row in filtered if row["marker"].startswith("identification-")}
    if identification_items != {"identification-public", "identification-confidential", "identification-anonymous"}:
        raise SystemExit(f"Anexo II, item 3 não separou as três modalidades de identificação: {sorted(identification_items)}")
    ouvidoria_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§3" in row["section"] and "(Anexo II)" in row["section"] and row["page_start"] <= 48
    )
    for phrase in (
        "13.460/2017", "integracao com e-mail setorial", "geolocalizacao", "recategorizacao",
        "resolucao unilateral", "decisao administrativa final", "avaliacao da qualidade",
        "somente a ouvidoria tera acesso", "codigo externo",
    ):
        if phrase not in ouvidoria_text:
            raise SystemExit(f"Anexo II, item 3 de Ouvidoria não preservou: {phrase}")
    protocol_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§4" in row["section"] and "(Anexo II)" in row["section"]
    )
    if not all(phrase in protocol_text for phrase in ("abertura de protocolo", "central de atendimento", "lista ou calendario")):
        raise SystemExit("Anexo II, item 4 não preservou os modos de abertura e consulta de protocolo.")
    protocol_extension = " ".join(
        folded(row["body"]) for row in filtered
        if "§4" in row["section"] and "(Anexo II)" in row["section"]
    )
    for phrase in (
        "qr-code", "codigo externo", "movimentacoes", "direcionamento automatico",
        "central de distribuicao", "concluido definitivamente", "interacoes externas",
        "prazo automatico, em dias", "alertas de vencimento", "certificado icp-", "login gov.br",
        "georreferenciamento", "pessoas envolvidas", "classificacao anterior", "avaliacao da qualidade",
        "nota substituira", "comprovante com as informacoes do protocolo",
    ):
        if phrase not in protocol_extension:
            raise SystemExit(f"Anexo II, item 4 de Protocolo Eletrônico não preservou: {phrase}")
    esic_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§5" in row["section"] and "(Anexo II)" in row["section"]
    )
    for phrase in (
        "12.527/11", "diretamente pelo cidadao", "visita presencial, telefone ou correspondencia",
        "qualquer setor cadastrado", "ao menos um usuario ativo", "inclusao de anexos",
        "categorizacao das demandas por assunto", "grafico contendo informacoes de quantidade total",
    ):
        if phrase not in esic_text:
            raise SystemExit(f"Anexo II, item 5 E-SIC não preservou: {phrase}")
    oficio_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§6" in row["section"] and "(Anexo II)" in row["section"]
    )
    for phrase in (
        "rastreados", "notificacao por sms", "recepcao de respostas", "resposta do destinatario",
        "manutencao de todo o historico", "base unica de contatos", "mesma numeracao",
        "geracao de qr code",
    ):
        if phrase not in oficio_text:
            raise SystemExit(f"Anexo II, item 6 Ofício Eletrônico não preservou: {phrase}")
    process_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§7" in row["section"] and "(Anexo II)" in row["section"]
    )
    for phrase in (
        "criacao de processo administrativo", "assuntos pre-definidos", "setor destinatario inicial",
        "campos personalizados", "obrigatoriedade de preenchimento", "informacoes de ajuda",
        "codigo de enderecamento postal (cep)", "validacao automatica da existencia de cpf",
        "validacao automatica da existencia de cnpj", "automacao de calculos",
        "modelos de texto", "documentos complementares", "linha do tempo",
    ):
        if phrase not in process_text:
            raise SystemExit(f"Anexo II, item 7 Processo Administrativo não preservou: {phrase}")
    for phrase in (
        "deferido", "indeferido", "sequencia a numeracao geral", "multiplos prazos",
        "restricao de abertura e tramitacao", "anexos obrigatorios", "ordem preestabelecida",
        "multiplos setores responsaveis", "sla ideal para cada etapa", "formato zip",
        "anexos do processo como aprovados ou reprovados", "arquivo compilado e paginado",
        "formato pdf", "processamento assincrono", "notificado quando finalizada a sua geracao",
        "versao completa da arvore de processos", "verificacao de suas autenticidades",
        "versao personalizada", "configuracao comprimida", "5 (cinco) megabytes",
        "500 (quinhentos) kilobytes por pagina", "copias integrais atualizadas",
        "ultima versao de cada uma das tres configuracoes", "pagina de referencia aos anexos",
        "hiperlink de mencao", "pasta comprimida",
    ):
        if phrase not in process_text:
            raise SystemExit(f"Anexo II, item 7 Processo Administrativo não preservou: {phrase}")
    acts_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§8" in row["section"] and "(Anexo II)" in row["section"]
    )
    for phrase in (
        "atos oficiais da administracao publica", "roteamento automatico", "arquivos no formato .docx",
        "formato pdf/a", "assinatura eletronica avancada e qualificada", "certificado digital icp-brasil",
        "co-assinatura de documentos por usuarios externos", "consulta a documentos por usuarios externos",
        "numeracao automatica geral", "numeracao automatica por assunto", "numeracao manual",
        "qr-code identificador",
    ):
        if phrase not in acts_text:
            raise SystemExit(f"Anexo II, item 8 Atos Oficiais não preservou: {phrase}")
    project_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§9" in row["section"] and "(Anexo II)" in row["section"] and row["page_start"] <= 56
    )
    project_full_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§9" in row["section"] and "(Anexo II)" in row["section"]
    )
    for phrase in (
        "projetos de construcoes unifamiliares", "desmembramento, remembramento", "licenca ambiental previa",
        "licenca ambiental de instalacao", "licenca ambiental de operacao", "plano de gerenciamento de residuos solidos",
        "eia-rima", "alvaras/licencas", "fluxos de trabalho", "campos personalizados",
        "codigo de enderecamento postal (cep)", "validacao automatica da existencia de cpf",
    ):
        if phrase not in project_text:
            raise SystemExit(f"Anexo II, item 9 Projetos não preservou: {phrase}")
    for phrase in (
        "validacao automatica da existencia de cnpj", "classificacao nacional de atividades economicas (cnae)",
        "calculo de parametros urbanisticos", "revisao de documentos e plantas de projeto em formato pdf",
        "selo de aprovacao", "distancia real entre dois pontos", "area real de elipses",
        "escala numerica padrao", "escala numerica especifica", "casas decimais pretendidas",
        "deferimento ou indeferimento", "impossibilidade de exibicao ao usuario externo",
        "sem custos adicionais e sem limite de quantidade", "100% digital", "mediante link na homepage oficial",
        "pdf ou dwg", "documentos definidos previamente como obrigatorios", "sem a exigencia de login",
        "visualizaram ou se efetivaram o download", "requerimentos e formularios", "arquiteto, engenheiro",
        "numero do documento (cpf ou cnpj)", "copia de todos os arquivos anexados", "caixa de entrada do setor",
        "analise individualizada dos anexos", "motivacao do indeferimento", "copia sera enviada ao e-mail",
        "figuras geometricas, linhas e tracos", "arquivo original sera mantido inalterado",
        "linha do tempo", "sistema tributario", "codigo verificador e qr code", "miniatura de todos os arquivos",
        "sisobra", "transmissao automatizada", "habite-se", "certificado digital icp-brasil",
    ):
        if phrase not in project_full_text:
            raise SystemExit(f"Anexo II, item 9 Projetos não preservou: {phrase}")
    sisobra_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§9" in row["section"] and "(Anexo II)" in row["section"]
    )
    for phrase in (
        "data e hora da criacao e do envio", "retificacao de alvara", "numero de protocolo da transmissao anterior",
        "cancelamento de alvara", "reativacao de alvara", "transmissao da versao retificada",
        "cancelamento de documento de “habite-se”", "reativacao de documento de “habite-se”",
        "retorno de erro enviado pelo sisobra", "historico de cada alteracao",
    ):
        if phrase not in sisobra_text:
            raise SystemExit(f"Anexo II, item 9 Sisobra não preservou: {phrase}")
    mobile_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§10" in row["section"] and "(Anexo II)" in row["section"]
    )
    for phrase in (
        "aplicativo movel nativo", "android e ios", "play store e app store", "ordenadas por proximidade",
        "identidade visual", "integrado a plataforma web", "gov.br", "cpf nao estiver associado a um cadastro existente",
        "novo contato externo sera criado automaticamente", "pessoa fisica ou juridica", "em aberto", "arquivado",
        "sigla do setor", "12.527/2011", "mantera a conexao", "tramitacoes e os anexos",
        "documentos de diferentes modulos", "botao “novo”", "subassuntos", "assuntos em destaque",
        "obrigatoriedade de anexos e campos adicionais", "salvo imediatamente", "qual setor recebeu a demanda",
        "confirmacao de abertura", "organograma completo", "informacoes de contato",
    ):
        if phrase not in mobile_text:
            raise SystemExit(f"Anexo II, item 10 Aplicativo Móvel não preservou: {phrase}")
    workflow_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§11" in row["section"] and "(Anexo II)" in row["section"]
    )
    for phrase in (
        "impressao do processo em modo cronologico", "conteudos e seus anexos em pdf",
        "arvore do processo", "exportacao em formato pdf", "workflow basico por assunto",
        "ordem pre-estabelecida", "ordenar os anexos",
    ):
        if phrase not in workflow_text:
            raise SystemExit(f"Anexo II, item 11 Workflow não preservou: {phrase}")
    batch_signature_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§12" in row["section"] and "(Anexo II)" in row["section"]
    )
    for phrase in (
        "assinaturas pendentes", "assinatura digital em lote", "campo tipo checkbox", "formato de fila",
        "caso de falha", "nova tentativa", "assinatura nativa", "assinatura icp- brasil",
        "varias assinaturas simultaneamente", "documentos que ja foram assinados",
    ):
        if phrase not in batch_signature_text:
            raise SystemExit(f"Anexo II, item 12 Assinatura em Lote não preservou: {phrase}")
    pad_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§13" in row["section"] and "(Anexo II)" in row["section"]
    )
    for phrase in (
        "processo administrativo disciplinar - pad", "tipo de infracao", "legislacao municipal",
        "informacoes de ajuda", "validacao automatica da existencia de cpf e cnpj", "modelos de texto",
        "documentos complementares", "anexos nos atos administrativos", "numeracao propria",
        "prazos de apresentacao de documentos, defesas, julgamento",
    ):
        if phrase not in pad_text:
            raise SystemExit(f"Anexo II, item 13 PAD não preservou: {phrase}")
    selection_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§14" in row["section"] and "(Anexo II)" in row["section"]
    )
    for phrase in (
        "recebimento de inscricoes", "vagas", "conta google", "modelo de documento",
        "campos personalizados", "formato csv",
    ):
        if phrase not in selection_text:
            raise SystemExit(f"Anexo II, item 14 Processo Seletivo não preservou: {phrase}")
    legislative_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§15" in row["section"] and "(Anexo II)" in row["section"]
    )
    for phrase in (
        "requerimentos e indicacoes", "prazos de resposta", "proposicoes legislativas",
        "relatorios gerenciais", "proposituras recebidas e respondidas",
    ):
        if phrase not in legislative_text:
            raise SystemExit(f"Anexo II, item 15 Protocolo Legislativo não preservou: {phrase}")
    personal_request_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§16" in row["section"] and "(Anexo II)" in row["section"]
    )
    for phrase in (
        "setor de gestao de pessoas", "13.709/2018", "identificacao atraves de buscas",
        "qr-code", "login gov.br", "prazo automatico", "forma fisica",
    ):
        if phrase not in personal_request_text:
            raise SystemExit(f"Anexo II, item 16 Requerimento Pessoal não preservou: {phrase}")
    technical_ticket_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§17" in row["section"] and "(Anexo II)" in row["section"]
    )
    for phrase in (
        "chamados tecnicos", "manutencoes de equipamentos", "usuarios externos",
        "em nome de outro usuario",
    ):
        if phrase not in technical_ticket_text:
            raise SystemExit(f"Anexo II, item 17 Chamado Técnico não preservou: {phrase}")
    qualification_text = " ".join(
        folded(row["body"]) for row in filtered
        if "§18" in row["section"] and "(Anexo II)" in row["section"]
    )
    for phrase in (
        "atestados ou declaracoes de capacidade tecnica", "pessoa de direito publico ou privado",
        "contrato(s) de sustentacao", "empresas desenvolvedoras", "revendas", "contrato em vigencia",
    ):
        if phrase not in qualification_text:
            raise SystemExit(f"Anexo II, item 18 Qualificação Técnica não preservou: {phrase}")
    if any(folded(row["body"]).strip(" .:") == "observacoes complementares" for row in filtered):
        raise SystemExit("Um título editorial foi incluído indevidamente como requisito.")
    filtered.append({
        "page_start": 69,
        "page_end": 69,
        "section": "Anexo III — planilha de composição de custos",
        "marker": "commercial-cost-breakdown",
        "body": (
            "Solicita-se apresentar junto com a planilha de preços uma planilha de composição de custos, "
            "discriminando mão de obra, tributos, insumos, frete, lucro/BDI e outros, totalizando 100%."
        ),
    })
    cost_composition_text = folded(filtered[-1]["body"])
    for phrase in ("mao de obra", "tributos", "insumos", "frete", "lucro/bdi", "outros", "100%"):
        if phrase not in cost_composition_text:
            raise SystemExit(f"Anexo III, composição comercial não preservou: {phrase}")
    return filtered


def module_for(section, body):
    text = folded(section + " " + body)
    if "pentest whitebox" in text:
        return "IDENTIDADE_E_SEGURANCA"
    if "§10" in section:
        return "PORTAL_EXTERNO"
    if "§18" in section and "(Anexo II)" in section:
        return "HABILITACAO_EMPRESARIAL"
    if "Anexo III" in section:
        return "PROPOSTA_COMERCIAL"
    if "§44" in section:
        return "OPERACAO_E_INTEGRACOES"
    if "§45" in section:
        return "CONTRATUAL"
    if "§41" in section:
        return "POC_E_OPERACAO"
    if "§42" in section:
        return "OPERACAO_E_INTEGRACOES"
    if "§40" in section:
        return "SUPORTE"
    if "§43" in section:
        return "RELATORIOS_E_INDICADORES"
    if "§39" in section:
        return "CONTRATUAL"
    if "§38" in section:
        return "IMPLANTACAO_E_SUPORTE"
    if "§35" in section or "treinamento de usuarios" in text or "curso(s)" in text:
        return "TREINAMENTO"
    if "§36" in section or "relatorios de produtividade" in text:
        return "RELATORIOS_E_INDICADORES"
    if "§34" in section or "etiqueta digital" in text:
        return "GESTAO_DOCUMENTAL"
    for needle, mapped in (
        ("ouvidoria", "OUVIDORIA"), ("e-sic", "E_SIC"), ("protocolo eletronico", "PROTOCOLO"),
        ("processo administrativo disciplinar", "PROCESSO_ADMINISTRATIVO_DISCIPLINAR"),
        ("processo administrativo", "PROCESSOS_ADMINISTRATIVOS"),
        ("processo seletivo", "PROCESSO_SELETIVO_SIMPLIFICADO"), ("requerimento pessoal", "REQUERIMENTO_PESSOAL"),
        ("protocolo legislativo", "PROTOCOLO_LEGISLATIVO"), ("chamado tecnico", "CHAMADO_TECNICO"),
        ("aprovacao de projetos", "APROVACAO_PROJETOS"),
        ("memorando", "COMUNICACAO_OFICIAL"), ("circular", "COMUNICACAO_OFICIAL"),
        ("oficio", "COMUNICACAO_OFICIAL"), ("atos oficiais", "COMUNICACAO_OFICIAL"),
        ("assinatura", "ASSINATURAS"), ("documento", "GESTAO_DOCUMENTAL"), ("classificacao", "GESTAO_DOCUMENTAL"),
        ("implantacao", "IMPLANTACAO_E_SUPORTE"), ("suporte", "IMPLANTACAO_E_SUPORTE"),
        ("acesso interno", "IDENTIDADE_E_SEGURANCA"), ("autenticacao", "IDENTIDADE_E_SEGURANCA"),
        ("confidencialidade", "IDENTIDADE_E_SEGURANCA"), ("auditoria", "IDENTIDADE_E_SEGURANCA"),
        ("organograma", "ADMINISTRACAO"), ("usuarios internos", "CAPACIDADE_E_LICENCIAMENTO"),
        ("notificacao", "NOTIFICACOES"), ("sla", "OPERACAO_E_INTEGRACOES"),
        ("backup", "OPERACAO_E_INTEGRACOES"), ("data center", "OPERACAO_E_INTEGRACOES"),
        ("acesso externo", "PORTAL_EXTERNO"), ("workflow", "PROCESSOS_ADMINISTRATIVOS"),
    ):
        if needle in text:
            return mapped
    if "objetivo do projeto" in folded(section):
        return "PLATAFORMA_E_ARQUITETURA"
    return "PLATAFORMA_E_ARQUITETURA"


def nature_for(section, body):
    """Classify the obligation type independently from source-review state."""
    text = folded(section + " " + body)
    if "§42" in section or "icp-brasil" in text or "tramita gov.br" in text or "sisobra" in text or "gov.br" in text:
        return "INTEGRACAO_EXTERNA"
    if "§41" in section:
        return "POC"
    if "§40" in section:
        return "OPERACAO_CONTRATUAL"
    if "§44" in section:
        return "OPERACAO_CONTRATUAL"
    if "§45" in section:
        return "EXTERNO/CONTRATUAL"
    if "§18" in section and "(Anexo II)" in section:
        return "HABILITACAO_EMPRESARIAL"
    if "Anexo III" in section:
        return "PROPOSTA_COMERCIAL"
    if "pentest whitebox" in text:
        return "OPERACAO_CONTRATUAL"
    if "§35" in section and any(phrase in text for phrase in (
        "assista novamente as aulas",
        "aplicacao de avaliacao de aprendizado",
        "afericao do progresso no treinamento",
    )):
        return "SOFTWARE"
    if "§39" in section:
        return "EXTERNO/CONTRATUAL"
    if any(phrase in text for phrase in (
        "prova de conceito", "demonstracao da solucao", "apresentacao do sistema",
    )):
        return "POC"
    if any(phrase in text for phrase in (
        "atestado de capacidade tecnica", "qualificacao tecnica", "habilitacao empresarial",
        "registro no conselho profissional", "certidao de acervo tecnico",
    )):
        return "HABILITACAO_EMPRESARIAL"
    if any(phrase in text for phrase in (
        "backup", "hospedagem", "99% do horario", "data center", "treinamento",
        "suporte tecnico", "atendimento presencial", "manutencao", "link de internet",
        "durante a vigencia do contrato", "a contratada disponibilizara", "a contratada realizara",
        "devera cooperar e auxiliar", "migracao", "implantacao",
    )):
        return "OPERACAO_CONTRATUAL"
    if re.search(r"\bsla\b", text):
        return "OPERACAO_CONTRATUAL"
    return "SOFTWARE"


def priority_for(section, body):
    text = folded(section + " " + body)
    if "a plataforma podera disponibilizar paineis de indicadores" in text:
        return "PREFERENCIAL"
    return "OBRIGATÓRIO"


previous_progress = {}
with CSV_PATH.open(encoding="utf-8-sig", newline="") as stream:
    reader = csv.DictReader(stream, delimiter=";")
    fields = list(reader.fieldnames)
    previous_progress = {row["id"]: row for row in reader}
rows = [row for row in previous_progress.values() if not row["id"].startswith("TR91-")]
previous_tr_rows = {row["id"]: row for row in previous_progress.values() if row["id"].startswith("TR91-")}
if "validacao_fonte" not in fields:
    fields.append("validacao_fonte")
for row in rows:
    row["validacao_fonte"] = "CONFERIDA_NO_DOCUMENTO_OFICIAL"

candidates = extract_candidates(OCR_PATH.read_text(encoding="utf-8", errors="replace"))
identity_occurrences = Counter()
generated_tr_ids = set()
partial_protocol_read_status_requirements = {
    "TR91-0125": (
        'Termo de Referência, §10 “Situação dos Documentos e Identificação Visual”, parágrafo '
        '“Deverá conter identificação visual do status de leitura na caixa de entrada do setor”, '
        'PDF p.12; transcrição conferida visualmente no PDF oficial. Correspondência: ED91-0120, '
        'Edital, fl. impressa 17, §14.1.13, subtítulo “Situação dos documentos e identificação visual”.',
        "Parcial — na caixa de entrada, o indicador distingue, por protocolo e setor atualmente responsável, nenhum servidor do setor tendo lido, leitura por outro servidor do setor sem leitura pelo usuário atual e leitura pelo próprio usuário. O registro ocorre após carregar o conteúdo do protocolo. A evidência não registra leitura de cada documento individual nem satisfaz o histórico de acesso/auditoria, downloads ou demais obrigações do parágrafo.",
        "ProtocolService.recordRead e ProtocolController.persistem recibo idempotente em protocol_read_receipts (V21), com organização, protocolo, setor corrente, usuário e horário. A listagem calcula o estado sem gravar; POST exige lotação ativa no setor corrente e escopo da organização validado no backend.",
        "StaffApp.vue apresenta os três estados em texto acessível e registra leitura somente após carregar com sucesso as interações do protocolo; a caixa não mostra nomes de leitores.",
        "ProtocolVerticalIntegrationTest#protocolInboxReadReceiptIsIdempotentAndTenantAndDepartmentScoped (PostgreSQL Testcontainers) verifica listagem sem escrita, estados, idempotência, setor, organização e negação sem mutação. frontend/e2e/protocol-read-status.spec.ts verifica os três estados e persistência após recarga contra a API real. Esses testes não cobrem leitura por documento, trilha completa de acesso ou contagem de downloads.",
        "POC-LEITURA-CAIXA (parcial para TR91-0125): em duas organizações fictícias, abrir um protocolo atribuído a um setor; conferir inicialmente “não lido pelo setor”; abrir seu conteúdo como um servidor e confirmar que a listagem de outro servidor da mesma lotação indica leitura por colega; abrir o conteúdo com o segundo servidor e confirmar “lido por mim”. Repetir sem vínculo ao setor e em outra organização, confirmando recusa sem registro. A demonstração é por protocolo/setor e não comprova leitura individual de cada documento, histórico de auditoria/acessos, downloads ou a POC oficial.",
        "Implementação parcial em ProtocolService/Controller, migração V21 e StaffApp.vue; teste focal e E2E identificados acima. Capturas `leitura-nao-iniciada`, `leitura-por-colega` e `leitura-pelo-servidor` em frontend/e2e/protocol-read-status.spec.ts e no manifesto da execução aprovada. Status permanece EM_DESENVOLVIMENTO: o recibo representa leitura do conteúdo do protocolo no setor atual, não leitura por documento, e não demonstra auditoria/downloads nem o critério completo do edital.",
    ),
    "TR91-0145": (
        'Termo de Referência, §15 “Status de Leitura e Rastreabilidade dos Documentos”, primeiro parágrafo '
        '(identificação visual do status de leitura na caixa do setor), PDF p.14; transcrição conferida '
        'visualmente no PDF oficial. Correspondência: ED91-0140, Edital, fl. impressa 18, §14.1.13, '
        'subtítulo “Status de leitura e rastreabilidade dos documentos”.',
        "Parcial — a caixa de entrada indica, por protocolo e setor atualmente responsável, se nenhum servidor habilitado do setor leu o conteúdo, se outro servidor do setor leu antes do usuário atual ou se o próprio usuário leu. O recibo só é gravado depois do carregamento do conteúdo do protocolo. Não há prova de leitura de cada documento/despacho individual, histórico cronológico completo de acessos, leitura por usuário externo ou contagem de downloads exigidos nos parágrafos seguintes.",
        "ProtocolService.recordRead e ProtocolController gravam recibo idempotente na tabela protocol_read_receipts criada pela V21, particionado por organização, protocolo, setor atual e usuário. A caixa projeta o estado sem mutação; o servidor exige lotação ativa no setor responsável e aplica o tenant validado.",
        "StaffApp.vue exibe rótulo textual acessível para cada estado e registra a leitura após carregar com sucesso o conteúdo/interações do protocolo; os nomes dos servidores não são expostos nesta fatia.",
        "ProtocolVerticalIntegrationTest#protocolInboxReadReceiptIsIdempotentAndTenantAndDepartmentScoped (PostgreSQL Testcontainers) exercita estados, idempotência, mudança de setor, leitura sem mutação na listagem e negativas de setor/organização. frontend/e2e/protocol-read-status.spec.ts verifica o indicador e sua persistência após recarga contra a API real. Não cobre os demais registros de acesso, downloads ou usuário externo deste §15.",
        "POC-LEITURA-CAIXA (parcial para TR91-0145): com dados fictícios, abrir conteúdo de protocolo na lotação responsável; demonstrar a caixa sem leitura, após leitura por colega e após leitura pelo próprio servidor; conferir que a listagem sozinha não cria recibo. Tentar registrar leitura com lotação de outro setor e organização e confirmar recusa sem mutação. Apresentar separadamente como pendentes leitura por documento/despacho, consulta de histórico cronológico, acessos externos e quantidade de downloads. Este ensaio não equivale à POC oficial.",
        "Evidência parcial de recibo de leitura por protocolo/setor na migração V21, ProtocolService/Controller e teste Testcontainers. Capturas `leitura-nao-iniciada`, `leitura-por-colega` e `leitura-pelo-servidor` em frontend/e2e/protocol-read-status.spec.ts e no manifesto da execução aprovada. Não valida histórico de quem acessou cada documento, acesso externo, quantidade de downloads nem a POC integral; manter EM_DESENVOLVIMENTO.",
    ),
}
document_access_test = (
    "`./dev document-access-test` (PostgreSQL Testcontainers) comprova cronologia, tenant, ator/papel neutro e imutabilidade; "
    "`./dev attachment-test` cobre downloads autorizados e isolamento com S3Mock; `./dev openapi-check` valida o contrato; "
    "`frontend/e2e/protocol-attachments.spec.ts`, cenário `assunto exige documento, cidadão envia e servidor consulta e baixa o anexo`, "
    "via `./dev document-access-e2e`. Testes não cobrem tentativas negadas/anônimas, login ou outros módulos."
)
document_access_backend = (
    "V28 cria `protocol_document_access_events` append-only, com tenant/protocolo, documento, ator/papel e horário. "
    "ProtocolDocumentAccessService agrega leituras autorizadas aos downloads concluídos já registrados; a consulta exige autorização "
    "setorial ou ADMIN organizacional e tenant validado. Tokens e credenciais não são persistidos na trilha."
)
document_access_frontend = (
    "StaffApp.vue apresenta cronologia de acessos autorizados de protocolo, anexos e mensagens/despachos, identificando servidor "
    "ou papel externo, item e horário; a linha de anexo mostra o total de downloads concluídos. A trilha fica disponível somente "
    "ao servidor autorizado."
)
document_access_poc = (
    "POC-ACESSO-DOCUMENTAL (parcial): com dados fictícios, requerente e servidor autorizado consultam protocolo, anexo e mensagem; "
    "na área do servidor, conferir ator/papel, item, horário e total de downloads por arquivo. Tentar consultar com setor não envolvido "
    "ou outra organização e confirmar recusa. Tentativas negadas/anônimas, login, documentos de outros módulos e POC oficial permanecem "
    "pendentes; este roteiro não é uma sessão oficial da comissão."
)
document_access_evidence = (
    "Evidência parcial em docs/INCREMENTO_57.md, `./dev document-access-test`, `./dev attachment-test`, `./dev openapi-check`, "
    "build frontend e Playwright `./dev document-access-e2e`; capturas `historico-acesso-do-documento` e "
    "`anexo-setor-consultado-baixado`. Somente leituras autorizadas e downloads concluídos do módulo de protocolo são cobertos; "
    "tentativas, login, identidade civil externa, demais módulos e POC oficial permanecem sem evidência."
)
partial_protocol_document_access_requirements = {
    "TR91-0143": (
        'Termo de Referência, §14 “Funcionalidade de quem visualizou”, PDF p.13; transcrição conferida visualmente no PDF oficial.',
        "Parcial — registrar e exibir leituras autorizadas de cada demanda/protocolo, anexos e mensagens/despachos e downloads concluídos, com papel do ator e horário. A cobertura limita-se ao módulo de protocolo; tentativas negadas/anônimas, login do sistema e documentos de outros módulos permanecem pendentes.",
    ),
    "TR91-0144": (
        'Termo de Referência, §14 “Funcionalidade de quem visualizou”, PDF p.13; transcrição conferida visualmente no PDF oficial.',
        "Parcial — diferenciar servidor municipal autenticado (interno) e requerente/participante externo com credencial válida (externo). A credencial externa prova posse de acesso, não identidade civil; acessos não autorizados e demais módulos não são cobertos.",
    ),
    "TR91-0146": (
        'Termo de Referência, §15 “Status de Leitura e Rastreabilidade dos Documentos”, PDF p.14; transcrição conferida visualmente no PDF oficial.',
        "Parcial — persistir em trilha append-only e exibir cronologia de leituras autorizadas de protocolo, anexos e mensagens/despachos, com ator/papel e data/hora. Tentativas negadas/anônimas, login do sistema e documentos de outros módulos não são registrados nesta fatia.",
    ),
    "TR91-0147": (
        'Termo de Referência, §15 “Status de Leitura e Rastreabilidade dos Documentos”, PDF p.14; transcrição conferida visualmente no PDF oficial.',
        "Parcial — registrar acessos válidos de requerentes e participantes externos aos itens de protocolo visíveis à própria credencial, e disponibilizar esses eventos a servidor autorizado. O ator é apresentado por papel neutro, sem identidade civil verificada; acessos negados/anônimos e outros módulos continuam pendentes.",
    ),
    "TR91-0148": (
        'Termo de Referência, §15 “Status de Leitura e Rastreabilidade dos Documentos”, PDF p.14; transcrição conferida visualmente no PDF oficial.',
        "Parcial — servidor autorizado consulta quem leu protocolo/anexo/mensagem por papel e horário; cada anexo exibe o total de downloads concluídos. Cobertura restrita ao protocolo, leituras autorizadas e downloads; tentativas negadas/anônimas, demais módulos e POC oficial permanecem pendentes.",
    ),
}
for index, candidate in enumerate(candidates, start=1):
    page_ref = (f"PDF p.{candidate['page_start']}" if candidate["page_start"] == candidate["page_end"]
                else f"PDF pp.{candidate['page_start']}–{candidate['page_end']}")
    if candidate["marker"].startswith("d/SLA-"):
        item_ref = f", item d, subitem {candidate['marker'].split('/', 1)[1]}"
    elif candidate["marker"] == "commercial-cost-breakdown":
        item_ref = ", obrigação da proposta comercial"
    elif candidate["marker"].startswith(("ui-", "acl-", "portal-task-", "fraud-", "report-", "stat-", "dashboard-", "manifestation-", "identification-")):
        item_ref = ", subitem em lista no documento"
    else:
        item_ref = f", item {candidate['marker']}" if candidate["marker"] else f", candidato OCR PT {index}"
    visually_reviewed = candidate["page_end"] <= VISUALLY_REVIEWED_THROUGH_PDF_PAGE
    review_note = (
        "transcrição conferida visualmente no PDF oficial"
        if visually_reviewed else "OCR em português ainda não conferido visualmente"
    )
    origin = f"Termo de Referência, {candidate['section']}{item_ref}, {page_ref}; {review_note}."
    synthetic_marker = candidate["marker"].startswith(("ui-", "acl-", "portal-task-", "fraud-", "report-", "stat-", "dashboard-", "manifestation-", "identification-", "commercial-"))
    summary = (
        candidate["body"] if synthetic_marker else
        f"{candidate['marker']}. {candidate['body']}" if candidate["marker"] else
        candidate["body"]
    )
    page_identity = (f"p.{candidate['page_start']}" if candidate["page_start"] == candidate["page_end"]
                     else f"pp.{candidate['page_start']}-{candidate['page_end']}")
    base_identity = ID_REGISTRY.identity("TR91", page_identity, summary)[0]
    identity_occurrences[base_identity] += 1
    requirement_id = ID_REGISTRY.assign("TR91", page_identity, summary, identity_occurrences[base_identity])
    generated_tr_ids.add(requirement_id)
    if visually_reviewed:
        acceptance = (
            "Pendente de decomposição: separar as obrigações deste trecho, definir aceite observável, "
            "responsáveis backend/frontend, teste e evidência antes de iniciar a implementação."
        )
        test = "Pendente: definir e executar teste automatizado específico após decompor o requisito e fixar seu aceite."
        evidence = f"Conferido visualmente no PDF oficial, {page_ref}; implementação e teste ainda sem evidência."
        poc = f"POC-{requirement_id}: roteiro específico pendente de decomposição e implementação; fonte conferida, sem demonstração registrada."
    else:
        acceptance = "BLOQUEADO: comparar com o scan e dividir obrigações distintas deste item antes de definir aceite observável."
        test = "BLOQUEADO até confirmar a transcrição e decompor as obrigações deste item OCR."
        evidence = "Aguardando conferência da imagem oficial; OCR anexado apenas para descoberta."
        poc = f"POC-{requirement_id}: bloqueado até validação visual da página {candidate['page_start']} do Termo de Referência."
    partial_organization_requirements = {
        "TR91-0015": "A demonstração parcial permite cadastrar e listar setores por organização; lista atualizada de contatos e organograma oficial ainda não estão implementados.",
        "TR91-0111": "A demonstração parcial mantém setores isolados por organização; representação dinâmica que agregue usuários e setores ainda não está implementada.",
        "TR91-0112": "A demonstração parcial permite cadastrar setores; hierarquia pai/filho e subsetores sem limite de profundidade ainda não estão implementados.",
    }
    process_deadline_requirements = {
        "TR91-0562": (
            "Aceite observável proposto para decompor a cláusula: um servidor autorizado no processo pode cadastrar mais de um prazo, cada qual com identificação e vencimento; após persistência, a consulta do processo retorna os prazos associados somente a esse processo, ordenados pelo vencimento e com os campos preservados. O backend valida a organização e a autorização do servidor. Este critério não inclui edição ou conclusão de prazos, alertas, feriados/dias úteis ou SLA, que não são descritos nesta cláusula.",
            "Serviço/API de processo administrativo e persistência tenant-scoped; derivar a organização do TenantContext e validar o acesso vigente do servidor ao processo.",
            "Painel do processo administrativo na área do servidor: formulário para identificar o prazo e informar vencimento, com lista persistida ordenada pelo vencimento.",
            "`./dev process-deadline-test` (PostgreSQL Testcontainers) cobre múltiplos prazos, ordenação, autoria/registro, campos inválidos, imutabilidade, autorização organizacional/setorial, 403 sem mutação e 404 indistinguível entre organização alheia e ID ausente. `./dev process-deadline-e2e` percorre a interface contra API/PostgreSQL descartáveis, adiciona dois prazos, confirma ordenação e autoria, recarrega e autentica novamente para reabrir os dados persistidos.",
            "POC-TR91-0562 (roteiro interno): autenticar no servidor com organização fictícia, abrir um processo, cadastrar dois prazos identificados com vencimentos distintos, recarregar e autenticar novamente; conferir persistência, autoria e ordenação. Testes automatizados também exercitam recusa a setor não autorizado e a outra organização. Este roteiro local não é a demonstração oficial da comissão.",
            "Evidência local em `./dev process-deadline-test`, `./dev process-deadline-e2e` e screenshot `processos-prazos-multiplos` em Documentos, associado ao título exato `process-multiple-deadlines — servidor registra e consulta múltiplos prazos no processo` de `frontend/e2e/process-multiple-deadlines.spec.ts`. A imagem demonstra apenas a UI fictícia; autorização, validação e isolamento são provados pelo Testcontainers. Não é POC oficial nem aprovação da comissão.",
        ),
    }
    process_numbering_requirements = {
        "TR91-0561": (
            "Aceite observável para a cláusula literal: ao publicar um workflow, ADMIN escolhe sequência independente por assunto ou sequência geral compartilhada por todos os assuntos da mesma organização e informa um padrão com campos suportados; ao abrir um processo autorizado, o servidor persiste e apresenta o número gerado no processo e na busca/lista. A configuração fica congelada para a versão e o número emitido é imutável. Processos anteriores permanecem sem número inventado. Não inclui conclusão, regras legais de calendário nem POC oficial.",
            "AdministrativeWorkflowService/Controller e V25: a organização deriva do TenantContext; modo/modelo são versionados; contadores por assunto ou por organização são protegidos por lock transacional; o número é armazenado uma única vez, único por organização e imutável. `{ano}` usa Clock na zona configurável `NEXOGOV_PROCESS_NUMBERING_ZONE_ID` (ambiente, padrão America/Sao_Paulo).",
            "AdministrativeProcessPanel integra a configuração no editor de workflow, valida/exibe prévia do padrão e apresenta número ou indicação de legado não registrado na lista/detalhe; busca também aceita número.",
            "`./dev process-numbering-test` (PostgreSQL Testcontainers) cobre ambos os modos, dois assuntos na sequência geral, mistura de modos, isolamento entre organizações, validação e padrão geral padrão, carry-forward por campo omitido, replay idempotente, mudança de modo incompatível, `{ano}` no limite de ano/fuso fixo, concorrência, `Long.MAX_VALUE` e imutabilidade. `./dev process-numbering-e2e` cobre `frontend/e2e/process-workflow.spec.ts`, cenário `administrador publica workflow versionado e acompanha o snapshot do processo`, com configuração e abertura pela UI para as duas opções, padrão customizado, apresentação do número e recuperação após nova autenticação; `./dev openapi-check` verifica o contrato.",
            "POC-TR91-0561 (roteiro interno, não sessão oficial): com organização e dados fictícios, publicar dois assuntos em BY_SUBJECT, emitir números independentes e publicar dois assuntos em GENERAL_SEQUENCE, observar a sequência compartilhada; variar padrão com `{assunto}`, `{ano}` e `{numero}`, abrir processo autorizado, recarregar e conferir persistência do número. Testes separados cobrem overflow, isolamento, concorrência, legacy null e imutabilidade.",
            "Evidência local verificável em `docs/INCREMENTO_54.md`, `./dev process-numbering-test`, `./dev process-numbering-e2e`, `./dev openapi-check` e labels `workflow-numeracao-configurada`, `processo-numero-gerado`, `workflow-numeracao-geral-configurada` e `processo-numero-geral-gerado` da spec `frontend/e2e/process-workflow.spec.ts`. Capturas mostram estados fictícios, não provam por si só autorização, concorrência ou persistência e não equivalem à POC oficial.",
        ),
    }
    organization_branding_requirements = {
        "TR91-0669": (
            "Parcial — ADMIN da organização configura e publica um nome público alternativo e uma cor escolhida entre quatro tokens semânticos da UI Core; a marca aparece no portal do cidadão e na área do servidor e tem prévia local antes da publicação. Símbolo oficial, fundo de tela e aplicativo móvel nativo permanecem pendentes, portanto o requisito integral não está validado.",
            "V27 organization_branding; OrganizationBrandingController/Service deriva o tenant de TenantContext, exige ADMIN organizacional ativo para publicar, limita cores a tokens Core e grava auditoria somente quando há mudança. Projeção pública exclui capacidade administrativa e é no-store.",
            "App.vue e StaffApp.vue exibem o nome publicado e aplicam token permitido; StaffApp fornece prévia local, restauração do padrão e publicação explícita. O portal do cidadão mantém identidade municipal só na nova solicitação e usa cabeçalho NexoGov neutro no acompanhamento, cuja resposta não identifica a organização. Esta fatia cobre somente web; não inclui símbolo oficial, fundo ou app nativo.",
            "OrganizationBrandingIntegrationTest (PostgreSQL Testcontainers) cobre escopo, ADMIN/setor, token inválido, projeção pública, no-store, auditoria e reset; OpenApiContractTest verifica o contrato. organization-branding.spec.ts percorre publicação, recarga, apresentação cidadã/servidor, troca de organização, descarte de resposta antiga e nome de 160 caracteres em 390/320 px. organization-branding-tracking.spec.ts cria protocolo em outro tenant e verifica o cabeçalho neutro no acompanhamento. Executar ./dev organization-branding-test e ./dev organization-branding-e2e.",
            "POC-TR91-0669 (parcial web): autenticar como ADMIN fictício, escolher nome e token de cor permitido, conferir a prévia e publicar; recarregar e verificar nome/cor no portal do servidor e do cidadão. Trocar a organização e confirmar que a identidade anterior não permanece. Demonstrar separadamente como pendentes o símbolo, fundo e aplicativo nativo; este roteiro não é POC oficial.",
            "V27, OrganizationBrandingIntegrationTest, OpenApiContractTest, organization-branding.spec.ts e docs/INCREMENTO_55.md. Após ./dev evidence, associação visual parcial apenas se disponíveis os três labels `identidade-visual-previa`, `identidade-publicada-servidor` e `identidade-publicada-cidadao` da spec `frontend/e2e/organization-branding.spec.ts`, cenário `administrador publica identidade visual e a troca de organização descarta respostas antigas`. As capturas usam dados fictícios e não demonstram símbolo oficial, imagem de fundo, app móvel, documentos/PDF nem validação da comissão. Status EM_DESENVOLVIMENTO.",
        ),
    }
    if requirement_id in organization_branding_requirements:
        acceptance, backend, frontend, test, poc, evidence = organization_branding_requirements[requirement_id]
    if requirement_id in partial_organization_requirements:
        acceptance = "Parcial — " + partial_organization_requirements[requirement_id]
        test = "ProtocolVerticalIntegrationTest.departmentAdministrationIsTenantScopedAdminOnlyAndAudited e teste E2E do cadastro de setor; a parte hierárquica/dinâmica do requisito permanece sem teste."
        evidence = "Teste PostgreSQL confirma criação autorizada, isolamento entre organizações e trilha append-only; consulte docs/INCREMENTO_15.md."
        poc = "POC-ADMIN-ORGANIZACAO: autenticar como ADMIN, cadastrar setor, atualizar lista e demonstrar que outra organização não vê o novo setor; registrar lacunas hierárquicas/dinâmicas."
    if requirement_id in process_deadline_requirements:
        acceptance, backend, frontend, test, poc, evidence = process_deadline_requirements[requirement_id]
    if requirement_id in process_numbering_requirements:
        acceptance, backend, frontend, test, poc, evidence = process_numbering_requirements[requirement_id]
    contact_requirements = {
        "TR91-0135": (
            "Demonstrar que ADMIN organizacional consegue criar, consultar, pesquisar, editar, arquivar e reativar contatos internos persistidos; autorização e isolamento são aplicados no servidor.",
            "V23 organization_contacts; OrganizationContactController/Service deriva o tenant de TenantContext, exige ADMIN organizacional ativo, limita consultas/mutações por organization_id e grava auditoria append-only.",
            "Painel Contatos da organização na área do servidor permite criar, consultar, pesquisar, editar, arquivar e reativar; acesso visual acompanha a capacidade retornada pelo backend.",
            "OrganizationContactIntegrationTest (PostgreSQL Testcontainers) e organization-contacts.spec.ts (Playwright contra API real); executar ./dev contacts-test e ./dev contacts-e2e.",
            "POC-TR91-0135: entrar como ADMIN, manter contato fictício pela interface, recarregar e confirmar persistência; verificar recusa de ADMIN apenas setorial. Captura: organization-contacts.spec.ts, cenário 'administrador mantém contatos por tipo e importa/exporta CSV'.",
            "docs/INCREMENTO_51.md; OrganizationContactIntegrationTest; organization-contacts.spec.ts; screenshot privado `contatos-gerenciados` em Documentos após ./dev evidence. Não valida organograma oficial, vCard, fusão de duplicados ou listas.",
        ),
        "TR91-0136": (
            "Criar, consultar e editar contatos separados nas três categorias expressas: pessoa física, setor da Prefeitura (por exemplo Financeiro ou Suporte) e Prefeitura/pessoa jurídica; tipo persiste e aparece na interface.",
            "OrganizationContactDtos.ContactType e V23 restringem o tipo a PERSON, MUNICIPAL_DEPARTMENT ou LEGAL_ENTITY; operações permanecem no tenant validado.",
            "Painel interno mantém e exibe as três categorias com rótulos distintos.",
            "OrganizationContactIntegrationTest verifica enum e persistência; organization-contacts.spec.ts cria as três categorias e confirma os rótulos na interface real; executar ./dev contacts-test e ./dev contacts-e2e.",
            "POC-TR91-0136: criar e editar um contato fictício de cada tipo, recarregar e confirmar os três tipos persistidos. Captura: organization-contacts.spec.ts, cenário 'administrador mantém contatos por tipo e importa/exporta CSV'.",
            "docs/INCREMENTO_51.md; OrganizationContactIntegrationTest; organization-contacts.spec.ts; screenshot privado `contatos-tipos` em Documentos após ./dev evidence. Não valida categorias adicionais não descritas na fonte.",
        ),
        "TR91-0137": (
            "Importar contatos de um CSV UTF-8 e exportar contatos para CSV pela ferramenta; conferir persistência, conteúdo exportado, limites, validação e ausência de gravação parcial em CSV inválido.",
            "OrganizationContactCsv limita a leitura a 1 MiB e 1.000 linhas; valida encoding, cabeçalho, campos e IDs no tenant antes de aplicar a transação; a exportação escapa prefixos de fórmula.",
            "Painel oferece importação de arquivo CSV e download da exportação.",
            "OrganizationContactIntegrationTest verifica import/export, round-trip idempotente por ID, CSV inválido/oversize, fórmula, tenant e rollback atômico; organization-contacts.spec.ts importa arquivo e baixa a exportação pela interface real; executar ./dev contacts-test e ./dev contacts-e2e.",
            "POC-TR91-0137: importar CSV fictício pela interface, confirmar quantidade e persistência, exportar e conferir os registros. Captura: organization-contacts.spec.ts, cenário 'administrador mantém contatos por tipo e importa/exporta CSV'.",
            "docs/INCREMENTO_51.md; OrganizationContactIntegrationTest; organization-contacts.spec.ts; screenshot privado `contatos-csv-importado-exportado` em Documentos após ./dev evidence. Não inclui mapeamento de colunas de sistemas legados.",
        ),
    }
    if requirement_id in contact_requirements:
        acceptance, backend, frontend, test, poc, evidence = contact_requirements[requirement_id]
    row = {
        "id": requirement_id,
        "requisito_resumo_fiel": summary,
        "origem_documento_pagina_secao_item": origin,
        "prioridade": priority_for(candidate["section"], candidate["body"]),
        "modulo": "ADMINISTRACAO" if requirement_id in contact_requirements or requirement_id in organization_branding_requirements else "PROCESSOS_ADMINISTRATIVOS" if requirement_id in process_deadline_requirements or requirement_id in process_numbering_requirements else module_for(candidate["section"], candidate["body"]),
        "natureza": "SOFTWARE" if requirement_id in contact_requirements or requirement_id in organization_branding_requirements or requirement_id in process_deadline_requirements or requirement_id in process_numbering_requirements else nature_for(candidate["section"], candidate["body"]),
        "criterio_de_aceite": acceptance,
        "backend_envolvido": backend if requirement_id in contact_requirements or requirement_id in organization_branding_requirements or requirement_id in process_deadline_requirements or requirement_id in process_numbering_requirements else "OrganizationAdminService, OrganizationAdminController e tabela departments com escopo validado por TenantContext." if requirement_id in partial_organization_requirements else "A definir após validação visual e decomposição do requisito.",
        "frontend_envolvido": frontend if requirement_id in contact_requirements or requirement_id in organization_branding_requirements or requirement_id in process_deadline_requirements or requirement_id in process_numbering_requirements else "Área do servidor: cadastro e seleção de setores retornados pela organização atual." if requirement_id in partial_organization_requirements else "A definir após validação visual e decomposição do requisito.",
        "teste_automatizado": test,
        "roteiro_poc": poc,
        "evidencia": evidence,
        # Matrix generation describes implementation progress; it cannot observe test
        # execution or inspect exported evidence. Promotion to VALIDADO is a deliberate
        # traceability update after those proofs have been reviewed.
        "status": "EM_DESENVOLVIMENTO" if requirement_id in organization_branding_requirements or requirement_id in process_deadline_requirements or requirement_id in process_numbering_requirements or requirement_id in partial_organization_requirements else "NAO_INICIADO" if visually_reviewed else "BLOQUEADO",
        "validacao_fonte": "CONFERIDA_VISUALMENTE" if visually_reviewed else "OCR_PENDENTE",
    }
    if requirement_id in contact_requirements:
        row.update({
            "criterio_de_aceite": acceptance,
            "teste_automatizado": test,
            "roteiro_poc": poc,
            "evidencia": evidence,
            "status": "VALIDADO",
        })
    federated_identity_backend = (
        "SecurityConfiguration valida assinatura RS256 por JWKS, issuer, audience e validade; issuer e JWKS exigem HTTPS, com HTTP permitido apenas para loopback no perfil de teste; "
        "JdbcExternalIdentityResolver resolve vínculo ativo por organization_id + issuer + subject somente quando user_accounts.active=true; "
        "TenantMembershipFilter exige organização e membership ativa e recusa suspensão organizacional. "
        "V9 bloqueia links globais legados sem backfill/reconciliação."
    )
    federated_identity_frontend = (
        "A área do servidor implementa cliente público OIDC Authorization Code + PKCE, com validação de state/nonce e callback configurado; "
        "Basic só é selecionado explicitamente em desenvolvimento, e sem configuração OIDC válida a interface não inicia o acesso. "
        "O access token segue em Bearer e fica apenas em memória. Logout limpa a sessão local e não afirma revogação server-side do token. Os testes usam um provedor simulado; IdP municipal, provisionamento, "
        "login por e-mail/senha e recuperação ainda estão pendentes."
    )
    federated_identity_test = (
        "FederatedIdentityIntegrationTest cobre vínculo por organização, username/subject compartilhados, exigência de conta local ativa e recusa quando user_accounts.active=false, "
        "link ausente/inativo, membership, suspensão e JWT inválido; MigrationSafetyIntegrationTest#blocksGlobalExternalIdentityLinksWithoutChangingLegacyData "
        "verifica V9 sem backfill. Executar ./dev identity-test (PostgreSQL Testcontainers e JWKS RSA temporário). "
        "staff-oidc.spec.ts, executado por ./dev oidc-test, valida state, nonce, PKCE, bearer, logout e callback com IdP simulado; não comprova integração com provedor municipal."
    )
    federated_identity_route = (
        "POC-AUTH-FEDERADA-V9: executar ./dev identity-test. Demonstrar o mesmo username e issuer+subject em Alfa e Beta, "
        "com conta local ativa, link ativo em Alfa e inativo em Beta; confirmar 200 em Alfa e 403 em Beta. Definir user_accounts.active=false e confirmar 403 em ambos; reativar e confirmar 200 em Alfa. "
        "Demonstrar recusa sem link, membership ou após suspensão e rejeição de issuer/audience/exp inválidos. "
        "No teste de migração, partir de V8 com link global legado: V9 deve falhar sem converter/remover o link nem alterar membership/conta. "
        "Para o cliente da interface, executar ./dev oidc-test: verificar Authorization Code + PKCE S256, state, nonce, callback, bearer e logout com provedor simulado. "
        "Logout não comprova revogação server-side do access token. Isso não prova homologação/integração com IdP municipal nem autorização documental completa."
    )
    federated_identity_evidence = (
        "V9__organization_scoped_external_identity.sql; FederatedIdentityIntegrationTest e "
        "MigrationSafetyIntegrationTest#blocksGlobalExternalIdentityLinksWithoutChangingLegacyData; ./dev identity-test. "
        "docs/INCREMENTO_30.md e staff-oidc.spec.ts (./dev oidc-test; provedor simulado). Sem evidência de IdP real, provisionamento, login por e-mail/senha ou autorização documental completa."
    )
    partial_federated_identity_requirements = {
        "TR91-0045": (
            "Parcial — serviço de recursos valida JWT e resolve vínculo ativo issuer+subject na organização selecionada; membership ativa e suspensão organizacional são verificadas no backend. O cadastro de senha pessoal pelo administrador, IdP municipal/cliente OIDC real, provisionamento, login por e-mail/senha e autorização documental completa permanecem pendentes.",
            federated_identity_backend,
            federated_identity_frontend,
            federated_identity_test,
            federated_identity_route,
            federated_identity_evidence,
        ),
        "TR91-0048": (
            "Parcial — JWT/JWKS/issuer/audience e vínculo ativo issuer+subject por organização são verificados no backend. Cadastro por e-mail individual, senha pelo administrador, recuperação, cliente OIDC municipal e autorização documental completa não estão implementados.",
            federated_identity_backend,
            federated_identity_frontend,
            federated_identity_test,
            federated_identity_route,
            federated_identity_evidence,
        ),
    }
    if requirement_id in partial_federated_identity_requirements:
        criterion, backend, frontend, test, route, evidence = partial_federated_identity_requirements[requirement_id]
        row.update({
            "criterio_de_aceite": criterion,
            "backend_envolvido": backend,
            "frontend_envolvido": frontend,
            "teste_automatizado": test,
            "roteiro_poc": route,
            "evidencia": evidence,
            "status": "EM_DESENVOLVIMENTO",
        })
    poc_requirement_details = {
        "TR91-0385": (
            "A POC é obrigatória e avalia se a solução ofertada atende às especificações técnicas do TR; registrar evidência observável para cada obrigação aplicável.",
            "Sistema NexoGov e integrações demonstradas; não há teste automatizado que substitua a avaliação da comissão.",
            "Interface da plataforma ofertada durante a sessão da POC; roteiro de demonstração versionado.",
            "A ata registra que a 1DOC foi aprovada na POC em 03/10/2025; o NexoGov não participou daquela avaliação. Testes internos por requisito não equivalem a laudo oficial.",
            "POC-TR91-0385: para benchmark interno, percorrer requisitos e integrações com dados fictícios, registrar evidência por ID e lacunas. A ata histórica registra a aprovação da 1DOC; este roteiro não reabre nem substitui aquele procedimento.",
            "A ata oficial registra a aprovação da 1DOC em 03/10/2025 e informa que o relatório técnico foi enviado à Licitanet. O relatório detalhado não está preservado localmente e não há evidência de avaliação oficial do NexoGov.",
        ),
        "TR91-0386": (
            "Realizar sessão pública na sede indicada ou remotamente; licitante fornece equipamentos e internet; Prefeitura pode disponibilizar o Auditório Central.",
            "N/A — obrigação logística da sessão; operação e equipamentos são externos ao backend.",
            "Plataforma apresentada em sessão presencial ou remota; equipamentos de apresentação fornecidos pela licitante.",
            "Checklist logístico e ensaio de sessão pendentes; não comprovável por teste automatizado.",
            "POC-TR91-0386: confirmar modalidade, endereço/data, público, equipamentos, internet e disponibilidade do auditório; guardar checklist e evidência da sessão.",
            "Checklist e evidência operacional da POC ainda não existem.",
        ),
        "TR91-0387": (
            "Demonstrar integralmente os itens de Integração dos Sistemas e os Requisitos Obrigatórios do TR; cada item deve apontar para evidência rastreável.",
            "Módulos backend e conectores correspondentes aos requisitos obrigatórios; várias integrações ainda não implementadas/homologadas.",
            "Interface real dos módulos e integrações do NexoGov; telas pendentes não podem ser representadas por mock.",
            "Cobertura da matriz ainda tem requisitos NAO_INICIADO; POC integral não executada.",
            "POC-TR91-0387 (ensaio interno de prontidão, não a POC do PE 91/2025): para cada requisito obrigatório e integração, abrir a evidência do CSV, executar o cenário e registrar resultado; não pular requisitos pendentes. A POC histórica do certame está encerrada; para outro certame, seguir seu edital e convocação próprios.",
            "A matriz possui 5 de 742 requisitos TR validados somente em escopo delimitado; 717 de 742 ainda aguardam decomposição em aceite observável. A ata registra aprovação da 1DOC na POC de 03/10/2025; o NexoGov não participou, não possui laudo dessa comissão e não comprovou aderência global.",
        ),
        "TR91-0388": (
            "A ata/laudo deve registrar o resultado; se a licitante classificada não obtiver êxito, a Administração convoca as subsequentes conforme edital.",
            "N/A — consequência do procedimento licitatório, fora do software.",
            "N/A — registro documental do resultado da sessão, não uma tela do NexoGov.",
            "Não aplicável a teste de software; verificar o fluxo documental da avaliação e convocação.",
            "POC-TR91-0388: conferir no registro oficial da sessão se aprovação/reprovação e eventual convocação subsequente foram documentadas conforme Edital §9.9.2.",
            "Sem ata/laudo da POC do NexoGov; procedimento externo ao código.",
        ),
        "TR91-0389": (
            "A POC deve confirmar, por demonstração prática, que o produto ofertado atende às especificações exigidas no edital.",
            "N/A isoladamente; depende dos módulos reais e integrações descritos por requisito na matriz.",
            "Plataforma NexoGov real em execução durante a demonstração integral, sem telas ou dados simulados para itens obrigatórios.",
            "O teste E2E atual cobre apenas um fluxo parcial de protocolo; critérios restantes aguardam implementação e prova.",
            "POC-TR91-0389: confrontar cada cenário obrigatório e sua evidência com a fonte oficial, registrar lacunas, questionamentos e laudo; ainda sem demonstração integral.",
            "Sem laudo da comissão ou validação integral no NexoGov.",
        ),
    }
    if requirement_id in poc_requirement_details:
        criterion, backend, frontend, test, route, evidence = poc_requirement_details[requirement_id]
        row.update({
            "criterio_de_aceite": criterion,
            "backend_envolvido": backend,
            "frontend_envolvido": frontend,
            "teste_automatizado": test,
            "roteiro_poc": route,
            "evidencia": evidence,
            "status": "NAO_INICIADO",
        })
    previous = previous_progress.get(requirement_id)
    if previous and previous["requisito_resumo_fiel"] == summary:
        generated_blocker = previous["criterio_de_aceite"].startswith(
            "BLOQUEADO: transcrição conferida; decompor obrigações, definir aceite observável"
        )
        preserve_previous = not generated_blocker and requirement_id not in organization_branding_requirements and requirement_id not in process_numbering_requirements and requirement_id not in partial_federated_identity_requirements and requirement_id not in contact_requirements and not (
            requirement_id in poc_requirement_details and previous["status"] == "NAO_INICIADO"
        )
        if preserve_previous:
            for field in ("criterio_de_aceite", "backend_envolvido", "frontend_envolvido", "teste_automatizado", "roteiro_poc", "evidencia", "status"):
                row[field] = previous[field]
    qualification_routes = {
        "TR91-0738": "VERIFICACAO-HABILITACAO: avaliar, na fase de habilitação, atestados/declarações de capacidade técnica de entidade pública ou privada que demonstrem execução satisfatória de serviço pertinente e compatível; documento empresarial fora da demonstração funcional da POC.",
        "TR91-0739": "VERIFICACAO-HABILITACAO: avaliar atestado(s) que comprovem contrato(s) de sustentação da solução; documento empresarial fora da demonstração funcional da POC.",
        "TR91-0740": "VERIFICACAO-HABILITACAO: conferir a condição legal da licitante como desenvolvedora ou revenda/representante autorizada estabelecida no país; avaliação empresarial fora da demonstração funcional da POC.",
        "TR91-0741": "VERIFICACAO-HABILITACAO: se a licitante for representante/revenda, conferir contrato de autorização vigente pelo período previsto de atendimento do contrato administrativo; documento empresarial fora da demonstração funcional da POC.",
    }
    if requirement_id in qualification_routes:
        row["roteiro_poc"] = qualification_routes[requirement_id]
    partial_member_access_requirements = {
        "TR91-0052": (
            "Parcial — a API e a área do servidor listam membros e permitem suspender/reativar somente o acesso à organização; criação de contas e inclusão/alteração de vínculos ainda não estão implementadas.",
            "OrganizationAdminService/Controller, memberships organizacionais e tabela organization_member_access_status; TenantContext define a organização e exige papel ADMIN organizacional.",
            "A área do servidor mostra papéis organizacionais e de setor separadamente, confirma suspensão e oferece reativação; não cadastra nem provisiona contas.",
            "OrganizationMemberAccessIntegrationTest (PostgreSQL Testcontainers) cobre autorização de mutação com ADMIN apenas setorial (403) e alvo exclusivo de outro tenant (404), além de escopo, suspensão, reativação, preservação e último administrador; organization-members.spec.ts cobre confirmação, nomes acessíveis e estado de erro de ação isolado (409 simulado), junto do fluxo real da API; provisionamento de contas permanece sem teste.",
            "POC-TR91-0052: listar membros fictícios e separar papéis; provar que ADMIN apenas setorial não suspende e que alvo exclusivo de outra organização é recusado sem alteração; conferir nome acessível das ações. Provisionamento permanece pendente.",
            "docs/INCREMENTO_20.md, docs/INCREMENTO_21.md, docs/INCREMENTO_23.md, OrganizationMemberAccessIntegrationTest e organization-members.spec.ts; cadastro e provisionamento administrativo de contas permanecem sem evidência.",
        ),
        "TR91-0056": (
            "Parcial — o administrador pode suspender ou reativar o acesso somente na organização atual; conta global, memberships, perfil e histórico permanecem. A API não oferece exclusão de cadastro, mas ainda não há garantia contra operações administrativas externas ao aplicativo.",
            "V7 organization_member_access_status e TenantMembershipFilter aplicam a suspensão em cada requisição protegida, sem alterar user_accounts.active nem memberships.",
            "A área do servidor permite suspender após confirmação e reativar o membro; a API continua responsável pela autorização e pelo estado efetivo.",
            "OrganizationMemberAccessIntegrationTest comprova recusa da mutação por ADMIN setorial (403) e de alvo exclusivo de outro tenant (404), suspensão após autenticação, reativação, idempotência, preservação, auditoria e proteção concorrente do último administrador; organization-members.spec.ts exercita nomes acessíveis, estado de erro isolado (409 simulado) e ações pela UI contra API/PostgreSQL reais.",
            "POC-TR91-0056: suspender membro pela interface, verificar 403 na requisição seguinte, preservar conta, memberships e histórico e reativar; testar mutação por ADMIN apenas setorial (403) e alvo exclusivo de outro tenant (404), sem estado/auditoria alterados. Conferir nomes acessíveis e feedback específico de ação e recarga da lista.",
            "docs/INCREMENTO_20.md, docs/INCREMENTO_21.md, docs/INCREMENTO_23.md, OrganizationMemberAccessIntegrationTest (PostgreSQL Testcontainers) e organization-members.spec.ts; não existe mecanismo global de exclusão de cadastro nem IdP real homologado.",
        ),
    }
    partial_protocol_subject_requirements = {
        "TR91-0157": (
            "Parcial — o cidadão registra um protocolo escolhendo assunto configurado pela organização; assunto exclusivo de balcão é omitido do catálogo público e o backend rejeita ID presencial ou de outra organização antes da numeração. O nome permanece como snapshot. Campos, anexos obrigatórios e demais critérios da Central ainda não estão completos.",
            "ProtocolService valida organização, assunto e disponibilidade online antes de alterar protocol_counters; V8 relaciona somente protocolos novos e mantém os legados sem vínculo.",
            "Portal do cidadão seleciona assuntos disponíveis na Central; a área de ADMIN configura nomes e atendimento online ou balcão.",
            "ProtocolVerticalIntegrationTest.protocolSubjectCatalogIsTenantScopedOnlineOnlyAndKeepsSnapshots (PostgreSQL Testcontainers), protocol-subject-catalog.spec.ts e protocol-flow.spec.ts (Playwright/API/PostgreSQL reais).",
            "POC-ASSUNTOS-PROTOCOLO: cadastrar assunto online e assunto de balcão, demonstrar que só o primeiro aparece para o cidadão, abrir protocolo, tentar ID de balcão e de outro município e confirmar rejeição sem reservar número; verificar o snapshot. Critérios não cobertos seguem pendentes.",
            "Evidência parcial em docs/INCREMENTO_22.md e nos testes Testcontainers/Playwright; não prova os demais campos/anexos da Central nem a POC integral.",
        ),
        "TR91-0522": (
            "Parcial — assunto configurado para atendimento somente no balcão não é listado no portal público, e a API não aceita sua abertura online. A configuração completa dos assuntos e critérios adicionais do Protocolo Eletrônico seguem em desenvolvimento.",
            "ProtocolSubjectService salva online_available por organização; ProtocolService confere estado e organização do ID antes de reservar número.",
            "Área administrativa oferece a escolha entre Central de Atendimento e somente balcão; o portal lista exclusivamente os assuntos online.",
            "ProtocolVerticalIntegrationTest.protocolSubjectCatalogIsTenantScopedOnlineOnlyAndKeepsSnapshots (PostgreSQL Testcontainers) e protocol-subject-catalog.spec.ts com API/PostgreSQL reais.",
            "POC-TR91-0522: configurar assunto para somente balcão; comprovar que ele não aparece no catálogo externo, tentar submetê-lo diretamente à API e observar rejeição sem alteração do contador; repetir com assunto online e comprovar a exibição.",
            "Evidência parcial em docs/INCREMENTO_22.md e testes de integração/E2E; regras e recursos restantes do módulo não foram validados.",
        ),
    }
    partial_protocol_receipt_requirements = {
        "TR91-0510": (
            "Atendido para o escopo literal deste item: após a inclusão de solicitação online, o requerente recebe imediatamente o número/código e pode obter comprovante PDF que identifica o setor destinatário inicial. O snapshot mantém esse setor no recibo depois de tramitações; este estado não valida outros itens do Protocolo Eletrônico nem a POC oficial.",
            "ProtocolService valida e persiste o setor ativo definido para o assunto e seu nome inicial; ReceiptPdfService gera o comprovante usando o snapshot imutável. A V11 deixa o campo nulo para protocolos preexistentes, sem fabricar histórico.",
            "Após o envio, o portal exibe a confirmação, a chave de consulta e a ação para baixar o comprovante; o cenário Playwright extrai o PDF real e confere o setor destinatário.",
            "ProtocolVerticalIntegrationTest#configuredSubjectRoutesProtocolAndReceiptWithoutCrossTenantLeakOrCounterConsumption e #receiptKeepsTheOpeningDepartmentAfterForwardingAndDispatch (PostgreSQL Testcontainers); protocol-subject-catalog.spec.ts (Playwright contra API/PostgreSQL reais) confirma a disponibilidade imediata e extrai do PDF o setor configurado.",
            "POC-TR91-0510 (escopo restrito): registrar solicitação fictícia online e, sem navegação adicional, conferir número/código, chave e opção de baixar comprovante; abrir o PDF e conferir o setor inicial. Depois encaminhar e despachar, baixar novamente e confirmar que o recibo conserva o setor da abertura. Este roteiro automatizado não substitui avaliação da comissão nem valida o ciclo integral do protocolo.",
            "Evidência automatizada para o critério literal: teste Testcontainers compara PDF antes/depois de encaminhamento e despacho; cenário Playwright abre o protocolo, mostra a confirmação imediata e extrai do PDF real o setor. docs/INCREMENTO_29.md registra execução e limites. Não é laudo nem aprovação oficial da POC.",
            "VALIDADO",
        ),
    }
    validated_protocol_deadline_requirements = {
        "TR91-0517": (
            "VALIDADO para o critério literal desta linha: ADMIN configura dias positivos por assunto; protocolo novo captura o valor vigente na abertura; o comprovante PDF exibe esse mesmo valor. Alterar ou remover a configuração afeta apenas protocolos futuros; zero é recusado. Esta linha trata da duração configurada e sua exibição; o parágrafo seguinte, sobre gestão e alertas de prazos, permanece rastreado em requisitos próprios.",
            "V15 guarda automatic_deadline_days com restrição positiva no assunto e no protocolo. ProtocolSubjectService exige ADMIN da organização; ProtocolService lê o valor com lock do assunto e grava snapshot ao abrir; ReceiptPdfService imprime o snapshot, sem recalcular nem alterar documentos existentes.",
            "StaffApp permite configurar, editar ou remover dias no assunto; o portal cidadão abre protocolo e baixa o comprovante. O E2E altera o valor, recarrega a área do servidor e extrai o texto do PDF emitido pela API real.",
            "ProtocolVerticalIntegrationTest#subjectDeadlineIsConfiguredSnapshottedAndPrintedOnReceipt verifica configuração, negativa cross-tenant, snapshot antes/depois de editar, campo omitido no PUT preservado, `null` explícito removido, rejeição de zero e conteúdo dos PDFs com PostgreSQL Testcontainers. `frontend/e2e/protocol-subject-catalog.spec.ts` confirma configuração persistida pela interface e prazo no comprovante PDF real. `./dev openapi-check` analisa o YAML e confere o contrato opcional/nullável.",
            "POC-TR91-0517 (ensaio automatizado): com dados fictícios, configure 8 dias no assunto pelo portal do servidor, altere para 14 e recarregue para confirmar persistência; abra protocolo pelo portal cidadão e confira 14 dias no comprovante. Execute `./dev protocol-deadline-test` para criar protocolo com 10 dias, mudar o catálogo para 20, comprovar que o primeiro PDF mantém 10 e o seguinte exibe 20; envie um PUT sem a propriedade e confirme que 20 permanece; envie `null` explicitamente e confirme a remoção; tente zero e confirme HTTP 400 sem mutação. A ata registra a aprovação da 1DOC em 03/10/2025; o NexoGov não participou e este roteiro é evidência interna.",
            "`./dev verify-fast` passou Core 11/11, backend 42/42, build Vue/Quasar e matriz; inclui `subjectDeadlineIsConfiguredSnapshottedAndPrintedOnReceipt` e `OpenApiContractTest`. `./dev openapi-check` passou 1/1; `./dev protocol-deadline-test` passou 1/1; `./dev e2e --grep 'prazo automático no comprovante'` passou 1/1 contra API/PostgreSQL reais em infraestrutura descartável e extraiu texto do PDF. Evidência de software limitada ao prazo configurado exibido no recibo; não é laudo da POC oficial nem demonstra regra legal de contagem.",
            "VALIDADO",
        ),
    }
    partial_protocol_attachment_requirements = {
        "TR91-0019": (
            "Parcial — anexos do protocolo ficam associados ao registro e podem ser consultados por servidor autorizado do setor atualmente responsável ou ADMIN da organização; o backend aplica tenant e vínculo setorial. A evidência cobre apenas anexos do protocolo eletrônico, não todo tipo de documento do sistema nem acesso permanente a setores não envolvidos.",
            "ProtocolAttachmentService/ProtocolController lista anexos em rota autenticada, com TenantContext e associação ao setor/admin; ProtocolAttachmentStore mantém objetos fora de URLs públicas. O escopo atual é protocolo eletrônico.",
            "A área do servidor lista e baixa anexos do protocolo por demanda; protocol-attachments.spec.ts demonstra o download do anexo na interface pela lotação atual. O escopo continua restrito ao protocolo eletrônico.",
            "ProtocolVerticalIntegrationTest#protocolAttachmentsAreRequiredValidatedPrivateAndTenantScoped (Testcontainers + Adobe S3Mock) confirma listagem do setor autorizado, 404 cross-tenant e 403 para outro setor; protocol-attachments.spec.ts (./dev e2e --grep 'assunto exige documento') demonstra listagem e download pelo servidor, junto ao upload/download do cidadão (1/1, 24,1 s). Não testa outros módulos documentais.",
            "POC-TR91-0019 (parcial): executar ./dev e2e --grep 'assunto exige documento'; abrir protocolo com PDF fictício pelo cidadão, depois atualizar a caixa de Alfa, listar e baixar o mesmo arquivo como servidor. Executar ./dev attachment-test para negativas de outro setor (403) e organização (404). A cobertura não demonstra todos os tipos de documento ou toda a política de acesso exigida.",
            "Evidência parcial em frontend/e2e/protocol-attachments.spec.ts (Playwright: cidadão envia/baixa e servidor lista/baixa, 1/1 em ambiente descartável) e ProtocolVerticalIntegrationTest#protocolAttachmentsAreRequiredValidatedPrivateAndTenantScoped (API: tenant/setor); não cobre documentos de outros módulos ou POC oficial.",
        ),
        "TR91-0516": (
            "Parcial — o ADMIN configura documentos por assunto como obrigatórios/opcionais e define extensões permitidas; na abertura, o backend recusa ausência de anexo obrigatório ou extensão fora da regra antes da numeração. A interação posterior também usa as extensões configuradas, com cenário complementar de PDF. Regra opcional, formatos suportados completos e segurança operacional continuam pendentes.",
            "ProtocolAttachmentPolicyService persiste regras no escopo da organização/assunto; ProtocolAttachmentService valida extensão/obrigatoriedade antes do contador e grava metadados tenant-scoped (V12). ProtocolInteractionService reutiliza as extensões configuradas ao validar anexos de mensagens (V13).",
            "StaffApp administra regras por assunto e as duas áreas permitem envio de arquivos na abertura ou em mensagens. protocol-attachments.spec.ts cobre abertura com documento obrigatório PDF; protocol-interactions.spec.ts usa PDF configurado em conversa posterior. A regra opcional e outros formatos não têm cobertura suficiente para conclusão.",
            "ProtocolVerticalIntegrationTest#protocolAttachmentsAreRequiredValidatedPrivateAndTenantScoped cobre abertura e recusa antes da numeração. `./dev interaction-test` passou quatro testes backend Testcontainers e Playwright 1/1, validando PDF configurado na conversa, ordenação histórica e regras concorrentes; os E2E cobrem somente PDF, não todos os formatos.",
            "POC-TR91-0516 (parcial): executar ./dev e2e --grep 'assunto exige documento' para anexos na abertura e ./dev e2e --grep 'conversa de protocolo' para PDF numa interação posterior. Executar ./dev attachment-test para negativas de abertura, extensão, token, tenant e setor. Regra opcional, lista completa de formatos e operação do storage permanecem pendentes; prazo do recibo está separado em TR91-0517.",
            "Evidência complementar: protocol-attachments.spec.ts, Playwright 1/1 para PDF de abertura; `./dev interaction-test`, quatro testes Testcontainers e Playwright 1/1 para PDF em interação. Os testes não validam todos os formatos, regra opcional, storage produtivo ou POC oficial. TR91-0517 tem evidência própria para o prazo exibido no recibo.",
        ),
        "TR91-0174": (
            "Parcial — a aplicação entrega o arquivo por rotas dinâmicas controladas, sem devolver URL pública direta do objeto; token inválido, outra organização e setor sem vínculo são recusados. O storage usado nos testes é simulado; hospedagem não pública e evidência operacional/contratual do ambiente real ainda não foram comprovadas.",
            "ProtocolAttachmentStore persiste chave opaca; ProtocolAttachmentService/ProtocolController permitem download somente após validar token de acompanhamento ou autorização de servidor, tenant e setor. A operação de hospedagem real está fora da evidência local.",
            "Portal e área do servidor chamam a API para upload/consulta/download; protocol-attachments.spec.ts confirma downloads pelos dois perfis. Não houve homologação de storage produtivo.",
            "ProtocolVerticalIntegrationTest#protocolAttachmentsAreRequiredValidatedPrivateAndTenantScoped (Testcontainers + Adobe S3Mock) verifica token inválido 404, download por token, tenant 404 e setor não autorizado 403; protocol-attachments.spec.ts comprova download cidadão/servidor em ambiente descartável. O double não comprova bucket/servidor produtivo seguro ou não público.",
            "POC-TR91-0174 (parcial de software): executar ./dev e2e --grep 'assunto exige documento' e demonstrar download do cidadão e servidor por controles da aplicação; executar ./dev attachment-test para token inválido, outra organização e setor sem vínculo. Apresentar evidência do hosting/bucket reais para aceitar a obrigação operacional; S3Mock não a substitui.",
            "Evidência parcial do caminho da aplicação em frontend/e2e/protocol-attachments.spec.ts (Playwright, 1/1) e do controle de token/tenant/setor em ProtocolVerticalIntegrationTest com S3Mock. Sem comprovação de servidor/bucket real, política de exposição ou obrigação contratual e sem laudo de POC.",
        ),
    }
    partial_protocol_interaction_requirements = {
        "TR91-0515": (
            "VALIDADO para o critério desta linha: após conclusão, requerente e servidor não conseguem registrar interações; somente servidor ativo vinculado ao setor responsável pode reabrir, permitindo nova manifestação do requerente. Participantes adicionais são tratados separadamente em TR91-0524.",
            "ProtocolService.complete/reopen e ProtocolInteractionService serializam estado e gravação; a autorização deriva da organização e do setor responsável validados no backend.",
            "StaffApp conclui e reabre pelo setor responsável; App.vue exibe o estado concluído, oculta o formulário externo e o restaura depois da reabertura.",
            "`ProtocolVerticalIntegrationTest#protocolInteractionsAreAppendOnlyPrivateAndReopenRequiresResponsibleSector` verifica 409 para mensagem do requerente e do servidor após conclusão, reabertura negada a outra lotação e permitida à responsável; `applicantInteractionSerializesWithProtocolCompletion` verifica a corrida. `./dev interaction-test` também passou Playwright 1/1.",
            "POC-TR91-0515 (ensaio automatizado): executar `./dev interaction-test`; concluir protocolo, verificar 409 para tentativa de mensagem do requerente e servidor, tentar reabrir por setor não responsável, reabrir pelo responsável e confirmar nova mensagem. A ata registra aprovação da 1DOC na POC do certame em 03/10/2025; o NexoGov não participou. Participantes adicionais são cobertos por TR91-0524.",
            "Critério literal validado por `./dev interaction-test`: testes PostgreSQL/Testcontainers comprovam bloqueios e reabertura por setor, e Playwright 1/1 percorre o fluxo no navegador. A ata registra aprovação da 1DOC em 03/10/2025; este resultado não é do NexoGov. TR91-0524 permanece separado e pendente.",
            "VALIDADO",
        ),
        "TR91-0524": (
            "Parcial — a organização configura campos por assunto e, ao abrir um protocolo, o requerente pode vincular pessoas com valores próprios; cada pessoa recebe chave individual para acompanhar e interagir na Central, e o requerente pode revogar uma chave sem revogar as demais. As chaves comprovam posse do convite, não identidade civil; o envio é manual e a integração oficial de identidade e a POC completa seguem pendentes. Quantidade de participantes e mecanismo de autenticação são decisões de produto, não números prescritos por esta cláusula.",
            "ProtocolSubjectParticipantFieldService persiste a configuração com FK composto e autorização ADMIN. ProtocolParticipantService valida respostas sob bloqueio do assunto e antes da numeração, salva snapshot/valores e apenas hash SHA-256 de credenciais aleatórias; ProtocolPublicAccessService serializa acompanhamento, interação, anexo e revogação pelo bloqueio do protocolo. Cada leitura pública restringe os valores ao participante correspondente; somente o requerente lista e revoga pessoas vinculadas.",
            "StaffApp configura os campos; App.vue renderiza a captura configurada, mostra os convites uma vez em links com token no fragmento, permite acompanhar/interagir por pessoa e apresenta revogação individual. A entrega do link é por cópia manual; nenhuma mensagem de e-mail nem identidade civil verificada é alegada.",
            "`./dev participant-fields-test` cobre configuração, autorização e tenant; `./dev participant-access-test` cobre rejeição antes da numeração, hash, valores por pessoa, autoria neutra, interação, revogação e tentativas contra outro protocolo/organização sem mudança dos contadores de eventos/interações com PostgreSQL Testcontainers; `./dev participant-access-e2e` cobre configuração, dois contextos isolados, fragmento/storage, troca de credencial, resposta fora de ordem, interação e revogação pela API real. A integração Gov.br/ICP-Brasil e a POC oficial não estão cobertas por esses testes.",
            "POC-TR91-0524 (ensaio parcial): em ambiente descartável, ADMIN de Alfa configura campo por assunto; requerente abre protocolo com duas pessoas fictícias, copia os convites, consulta cada um em contexto isolado, verifica os próprios valores, autoria neutra, fragmento removido e token ausente do storage, envia mensagem, revoga uma chave e confirma que a outra continua válida. Trocar credenciais enquanto uma consulta anterior está atrasada e confirmar que a resposta obsoleta não substitui a atual. Executar `./dev participant-access-test` e `./dev participant-access-e2e`. A chave prova posse, não identidade civil; credenciais oficiais do órgão, envio automatizado e POC nos 90 minutos da Errata I seguem pendentes. Duas pessoas são massa de teste, não requisito de quantidade.",
            "Evidência parcial: `./dev participant-access-test` (PostgreSQL Testcontainers) verifica rejeição antes da numeração, hash, dados individuais, interação, escopo cross-org e ausência de mutação após 404; `./dev participant-access-e2e` (Playwright/API real) verifica contextos separados, URL/storage, troca de credencial, concorrência de consultas e revogação; `./dev participant-fields-test` e `./dev participant-fields-e2e` mantêm a evidência de configuração. Identidade civil oficial, entrega segura automatizada e POC oficial continuam pendentes.",
            "EM_DESENVOLVIMENTO",
        ),
        "TR91-0525": (
            "Parcial — requerente, pessoas vinculadas e servidores autorizados de setor envolvido conseguem registrar interações com texto; anexos respeitam extensões configuradas por assunto. A consulta pública contém apenas mensagens públicas; mensagens internas e anexos internos permanecem na área de servidor. Identidade civil, lista completa de formatos e POC integral ainda não estão comprovadas.",
            "ProtocolInteractionService persiste eventos append-only vinculados ao tenant/protocolo, valida estado e extensões configuradas, autoriza servidor por setor envolvido e identifica requerente ou participante por token próprio; downloads respeitam visibilidade, tenant e setor.",
            "App.vue permite ao requerente e à pessoa vinculada consultar e enviar mensagens/anexos; StaffApp.vue lista mensagens e permite ao setor envolvido responder, escolher visibilidade pública/interna e anexar arquivos.",
            "`./dev interaction-test` passou quatro testes Testcontainers selecionados (isolamento, anexos, integridade, conclusão/reabertura, ordenação histórica e serialização) e Playwright 1/1; a suite total não é inferida deste comando focado.",
            "POC-TR91-0525 (parcial, ambiente descartável): executar ./dev e2e --grep 'conversa de protocolo'; abrir protocolo fictício em Alfa, ADMIN configura extensão PDF permitida, servidor envia texto+PDF público, requerente e pessoa vinculada consultam e enviam mensagens, servidor envia nota interna e confirmar que não aparece ao portal; concluir e confirmar bloqueio, reabrir pelo setor responsável e confirmar novo envio externo. Executar testes backend para isolamento tenant/setor, formato inválido, anexos internos, append-only e corrida com conclusão. A identidade civil e a POC oficial permanecem pendentes.",
            "Evidência parcial: `./dev interaction-test` executa quatro testes Testcontainers e Playwright 1/1 para mensagens/anexos; `./dev participant-access-test` e `./dev participant-access-e2e` comprovam mensagens de participantes individuais, anonimização e revogação seletiva. Nenhum desses ensaios é a POC oficial nem valida todos os formatos requeridos.",
            "EM_DESENVOLVIMENTO",
        ),
    }
    if requirement_id in partial_member_access_requirements:
        criterion, backend, frontend, test, route, evidence = partial_member_access_requirements[requirement_id]
        row.update({
            "criterio_de_aceite": criterion,
            "backend_envolvido": backend,
            "frontend_envolvido": frontend,
            "teste_automatizado": test,
            "roteiro_poc": route,
            "evidencia": evidence,
            "status": "EM_DESENVOLVIMENTO",
        })
    if requirement_id in partial_protocol_subject_requirements:
        criterion, backend, frontend, test, route, evidence = partial_protocol_subject_requirements[requirement_id]
        row.update({
            "criterio_de_aceite": criterion,
            "backend_envolvido": backend,
            "frontend_envolvido": frontend,
            "teste_automatizado": test,
            "roteiro_poc": route,
            "evidencia": evidence,
            "status": "EM_DESENVOLVIMENTO",
        })
    if requirement_id in partial_protocol_attachment_requirements:
        criterion, backend, frontend, test, route, evidence = partial_protocol_attachment_requirements[requirement_id]
        row.update({
            "criterio_de_aceite": criterion,
            "backend_envolvido": backend,
            "frontend_envolvido": frontend,
            "teste_automatizado": test,
            "roteiro_poc": route,
            "evidencia": evidence,
            "status": "EM_DESENVOLVIMENTO",
        })
    if requirement_id in partial_protocol_interaction_requirements:
        (criterion, backend, frontend, test, route, evidence, status) = partial_protocol_interaction_requirements[requirement_id]
        row.update({
            "criterio_de_aceite": criterion,
            "backend_envolvido": backend,
            "frontend_envolvido": frontend,
            "teste_automatizado": test,
            "roteiro_poc": route,
            "evidencia": evidence,
            "status": status,
        })
    if requirement_id in validated_protocol_deadline_requirements:
        (criterion, backend, frontend, test, route, evidence, status) = validated_protocol_deadline_requirements[requirement_id]
        row.update({
            "criterio_de_aceite": criterion,
            "backend_envolvido": backend,
            "frontend_envolvido": frontend,
            "teste_automatizado": test,
            "roteiro_poc": route,
            "evidencia": evidence,
            "status": status,
        })
    read_status_details = partial_protocol_read_status_requirements.get(requirement_id)
    if read_status_details:
        (source, criterion, backend, frontend, test, route, evidence) = read_status_details
        row.update({
            "origem_documento_pagina_secao_item": source,
            "modulo": "PROTOCOLO",
            "criterio_de_aceite": criterion,
            "backend_envolvido": backend,
            "frontend_envolvido": frontend,
            "teste_automatizado": test,
            "roteiro_poc": route,
            "evidencia": evidence,
            "status": "EM_DESENVOLVIMENTO",
            "validacao_fonte": "CONFERIDA_VISUALMENTE",
        })
    document_access_details = partial_protocol_document_access_requirements.get(requirement_id)
    if document_access_details:
        source, criterion = document_access_details
        row.update({
            "origem_documento_pagina_secao_item": source,
            "modulo": "GESTAO_DOCUMENTAL",
            "criterio_de_aceite": criterion,
            "backend_envolvido": document_access_backend,
            "frontend_envolvido": document_access_frontend,
            "teste_automatizado": document_access_test,
            "roteiro_poc": document_access_poc,
            "evidencia": document_access_evidence,
            "status": "EM_DESENVOLVIMENTO",
            "validacao_fonte": "CONFERIDA_VISUALMENTE",
        })
    rows.append(row)

for requirement_id, previous in previous_tr_rows.items():
    if requirement_id not in generated_tr_ids:
        archived = previous.copy()
        archived["criterio_de_aceite"] = (
            "BLOQUEADO: este item constava na matriz anterior, mas não foi reconhecido pela extração atual; "
            "revisar o documento oficial antes de retirar ou substituir o requisito. Critério anterior: "
            + previous.get("criterio_de_aceite", "")
        )
        archived["evidencia"] = (
            "Preservado da matriz anterior; a extração atual não encontrou a âncora. "
            "Revisar a fonte e a alteração do parser antes de encerrar o ID."
        )
        archived["status"] = "BLOQUEADO"
        rows.append(archived)

for row in rows:
    if row["id"] == "TR91-0691":
        (row["criterio_de_aceite"], row["backend_envolvido"], row["frontend_envolvido"],
         row["teste_automatizado"], row["roteiro_poc"], row["evidencia"]) = (
            'Parcial — a API e a interface autenticada permitem publicar etapas versionadas por assunto, abrir processo com snapshot da definição, pesquisar/listar processos autorizados por tenant/setor com paginação e reabrir o detalhe após recarga, acompanhar etapas/eventos e avançar somente para a etapa seguinte e setor permitido. Permanecem ausentes condições/tempos/grupos configuráveis, encerramento integrado e POC oficial integral.',
            'AdministrativeWorkflowService/Controller, V19/V20 e tabelas tenant-scoped; escopo vem do TenantContext validado. A definição guarda versões imutáveis; processos preservam snapshot de versão e etapas; abertura bloqueia lotação e setor iniciais ativos; avanço bloqueia lotação corrente, setores da etapa e destino ativo em ordem estável, exige a etapa seguinte e setor permitido, e persiste o evento com atualização otimista. Publicação exige expectedVersion e idempotencyKey; o hash usa framing sem ambiguidade, e repetição idêntica retorna a mesma versão sem duplicar definição.',
            'AdministrativeProcessPanel em `/servidor/`, usando o cliente de servidor autenticado e o seletor de organização existente: configura versões por assunto, inicia processo a partir da primeira etapa, pesquisa/lista apenas resumos retornados pela API autorizada, recupera detalhe persistido após nova autenticação e mostra snapshot/linha do tempo do servidor com avanço sequencial.',
            '`./dev process-workflow-test` (PostgreSQL Testcontainers) cobre autorização ADMIN e isolamento; snapshots de nomes/versões; hashing com framing contra colisão de delimitadores; replay idempotente, expectedVersion obsoleta e publicação concorrente; abertura contra revogação/desativação; avanço sequencial, recusa sem mutação, timestamps monotônicos, cross-tenant, revogação e desativação de setor atual/destino; exige membership no setor atribuído mesmo em etapa multi-setor, rejeita setor alternativo e desativação concorrente; 403 antes de revelar estado do destino; contenção concorrente observada em pg_stat_activity; e GET de detalhe aguardando revogação de lotação e negando acesso após o commit sem mutação. `./dev process-list-test` (PostgreSQL Testcontainers) cobre tenant/setor atual, ADMIN organizacional, busca literal, paginação, ordenação estável e resumo sem eventos/etapas. `./dev openapi-check` e `OpenApiContractTest` conferem os contratos do módulo. `./dev process-workflow-e2e` (Playwright/API/PostgreSQL descartáveis; `frontend/e2e/process-workflow.spec.ts`) publica v1/v2, preserva o snapshot v1, recusa salto (409) e servidor sem lotação (403) sem mutação, avança sequencialmente, recarrega a página, autentica novamente, busca o processo persistido e abre seu detalhe pela API real.',
            'POC-WORKFLOW-BASICO (parcial): como ADMIN, publicar duas etapas em v1 e abrir processo; publicar v2 alterando os nomes e confirmar que o processo mantém v1; tentar saltar etapa e confirmar HTTP 409 sem evento; tentar avançar sem lotação ativa no setor atual e confirmar HTTP 403 sem mutação; avançar na ordem, recarregar a interface, autenticar novamente, buscar e reabrir o processo. Os testes Testcontainers cobrem isolamento entre organizações e casos concorrentes. Encerramento integrado, condições avançadas, exportações e POC oficial permanecem pendentes.',
            'Evidência parcial: `./dev process-workflow-test`, `./dev process-list-test`, `./dev openapi-check` e `./dev process-workflow-e2e` (cenário Playwright com API/PostgreSQL reais em infraestrutura descartável). A spec `frontend/e2e/process-workflow.spec.ts` registra subfluxos locais: `workflow-publicado-v1`, `workflow-publicado-v2`, `processo-snapshot-v1`, `processo-avancado-para-etapa-final`, `processo-listado-apos-reinicio` e `detalhe-reaberto-da-listagem`. As capturas não provam as recusas HTTP, encerramento, condições avançadas ou POC oficial; manter EM_DESENVOLVIMENTO.',
        )
        row["status"] = "EM_DESENVOLVIMENTO"
    if row["id"] == "TR91-0560":
        (row["criterio_de_aceite"], row["backend_envolvido"], row["frontend_envolvido"],
         row["teste_automatizado"], row["roteiro_poc"], row["evidencia"]) = (
            'Configurar, em cada etapa do workflow, quais setores podem registrar uma decisão “Deferido” ou “Indeferido” para o processo; exigir membership ativa do servidor no setor corrente configurado e manter o processo em andamento após a decisão.',
            'AdministrativeWorkflowService/Controller e V22: a permissão por setor é versionada e copiada ao snapshot do processo; TenantContext define a organização; a decisão bloqueia processo, setor e membership ativa exata do setor corrente. Repetir o resultado é idempotente; mudar o resultado acrescenta evento DECIDED sem fechar nem avançar o processo.',
            'AdministrativeProcessPanel em `/servidor/`: ADMIN configura os setores decisores em cada etapa; membro autorizado registra Deferido ou Indeferido; o estado permanece Em andamento e a decisão mais recente aparece no processo e no histórico após recarga.',
            '`./dev process-decision-test` (PostgreSQL Testcontainers) cobre configuração/snapshot, setor não autorizado, ADMIN organizacional sem lotação, membro de setor sem papel ADMIN, os dois resultados, repetição idempotente, mudança append-only, processo ativo e isolamento entre organizações. `./dev process-workflow-test` cobre regressões; `./dev openapi-check` e `OpenApiContractTest` conferem o contrato; `./dev process-workflow-e2e` usa API real no cenário `administrador publica workflow versionado e acompanha o snapshot do processo` em `frontend/e2e/process-workflow.spec.ts` para configuração, decisão e persistência após nova autenticação.',
            'Roteiro local de decisão administrativa: publicar workflow fictício com setor decisor configurado; abrir processo; autenticar com servidor de membership ativa no setor corrente; registrar Deferido ou Indeferido; confirmar decisão no detalhe/lista, histórico e estado Em andamento após recarga. Cobrir o resultado alternativo e acesso negado pelo teste focado. A sessão oficial da POC não foi executada.',
            'Evidência local verificável em `./dev process-decision-test`, `./dev openapi-check`, `./dev process-workflow-e2e` e labels `workflow-decisores-configurados`, `processo-decisao-registrada` e `processo-decisao-persistida`; capturas fictícias mostram configuração, resultado sem encerramento e persistência após recarga. Não são aprovação nem POC oficial.',
        )
        row["status"] = "VALIDADO"
    details = partial_protocol_subject_requirements.get(row["id"])
    if details:
        (row["criterio_de_aceite"], row["backend_envolvido"], row["frontend_envolvido"],
         row["teste_automatizado"], row["roteiro_poc"], row["evidencia"]) = details
        row["status"] = "EM_DESENVOLVIMENTO"
    attachment_details = partial_protocol_attachment_requirements.get(row["id"])
    if attachment_details:
        (row["criterio_de_aceite"], row["backend_envolvido"], row["frontend_envolvido"],
         row["teste_automatizado"], row["roteiro_poc"], row["evidencia"]) = attachment_details
        row["status"] = "EM_DESENVOLVIMENTO"
    interaction_details = partial_protocol_interaction_requirements.get(row["id"])
    if interaction_details:
        (row["criterio_de_aceite"], row["backend_envolvido"], row["frontend_envolvido"],
         row["teste_automatizado"], row["roteiro_poc"], row["evidencia"], row["status"]) = interaction_details
    deadline_details = validated_protocol_deadline_requirements.get(row["id"])
    if deadline_details:
        (row["criterio_de_aceite"], row["backend_envolvido"], row["frontend_envolvido"],
         row["teste_automatizado"], row["roteiro_poc"], row["evidencia"], row["status"]) = deadline_details
    receipt_details = partial_protocol_receipt_requirements.get(row["id"])
    if receipt_details:
        (row["criterio_de_aceite"], row["backend_envolvido"], row["frontend_envolvido"],
         row["teste_automatizado"], row["roteiro_poc"], row["evidencia"], row["status"]) = receipt_details
    document_access_details = partial_protocol_document_access_requirements.get(row["id"])
    if document_access_details:
        (row["origem_documento_pagina_secao_item"], row["criterio_de_aceite"]) = document_access_details
        row["modulo"] = "GESTAO_DOCUMENTAL"
        row["backend_envolvido"] = document_access_backend
        row["frontend_envolvido"] = document_access_frontend
        row["teste_automatizado"] = document_access_test
        row["roteiro_poc"] = document_access_poc
        row["evidencia"] = document_access_evidence
        row["status"] = "EM_DESENVOLVIMENTO"
        row["validacao_fonte"] = "CONFERIDA_VISUALMENTE"

rows.sort(key=lambda row: (0 if row["id"].startswith("ED91-") else 1 if row["id"].startswith("TR91-") else 2, row["id"]))
ID_REGISTRY.save()
with CSV_PATH.open("w", encoding="utf-8-sig", newline="") as stream:
    writer = csv.DictWriter(stream, fieldnames=fields, delimiter=";", quoting=csv.QUOTE_MINIMAL, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
verified_count = sum(row["validacao_fonte"] == "CONFERIDA_VISUALMENTE" for row in rows if row["id"].startswith("TR91-"))
pending_count = sum(row["validacao_fonte"] == "OCR_PENDENTE" for row in rows if row["id"].startswith("TR91-"))
print(f"Matriz atualizada: {len(rows)} linhas; {len(candidates)} requisitos TR, {verified_count} transcrições conferidas visualmente e {pending_count} pendentes. Requisitos não implementados seguem NAO_INICIADO; BLOQUEADO é reservado para impedimento concreto ou fonte ainda não conferida.")
