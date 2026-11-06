#!/usr/bin/env python3
"""Add individually addressable candidates from the scanned official TR OCR.

Each enumerated item is kept as its own candidate, including non-normative objectives.
Unnumbered paragraphs are included when they define scope or contain normative language.
Source transcription review and product validation are tracked as separate states.
"""
import csv
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "docs/licitacoes/mata-de-sao-joao-91-2025"
CSV_PATH = BASE / "MATRIZ_ADERENCIA.csv"
OCR_PATH = BASE / "edital-original/texto-extraido/TERMODEREFERENCIAEJUSTIFICATIVAPE912025-ocr-por.txt"
NORMATIVE = re.compile(
    r"\b(devera|deverao|permit|possibil|sera|serao|havera|podera|poderao|capacidade|"
    r"garantir|realizar|manter|fornecer|possuir|disponibilizar|registrar|a contratada|"
    r"o sistema|a plataforma|a solucao|integracao|permitir|constituir|necessaria)\b", re.I)
SECTION = re.compile(r"^\s*(\d{1,2})[.)]\s*(.{3,150})$")
SUBSECTION = re.compile(r"^\s*([a-dA-D])[.)]\s+(.{3,150})$")
BULLET = re.compile(r"^\s*([a-zA-Z]|[ivxlcdm]{1,5}|\d{1,2})[.)]\s+(.+)$")
PAGE_MARKER = re.compile(r"^===== PÁGINA PDF (\d+) =====\s*$", re.M)
VISUALLY_REVIEWED_THROUGH_PDF_PAGE = 52
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

            heading = SECTION.match(line)
            if heading and (line == line.upper() or len(line) < 110):
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
    return filtered


def module_for(section, body):
    text = folded(section + " " + body)
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
    if "§42" in section or "icp-brasil" in text or "tramita gov.br" in text:
        return "INTEGRACAO_EXTERNA"
    if "§41" in section:
        return "POC"
    if "§40" in section:
        return "OPERACAO_CONTRATUAL"
    if "§44" in section:
        return "OPERACAO_CONTRATUAL"
    if "§45" in section:
        return "EXTERNO/CONTRATUAL"
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
        "suporte tecnico", "atendimento presencial", "manutencao", "sla", "link de internet",
        "durante a vigencia do contrato", "a contratada disponibilizara", "a contratada realizara",
        "devera cooperar e auxiliar", "migracao", "implantacao",
    )):
        return "OPERACAO_CONTRATUAL"
    return "SOFTWARE"


def priority_for(section, body):
    text = folded(section + " " + body)
    if "a plataforma podera disponibilizar paineis de indicadores" in text:
        return "PREFERENCIAL"
    return "OBRIGATÓRIO"


with CSV_PATH.open(encoding="utf-8-sig", newline="") as stream:
    reader = csv.DictReader(stream, delimiter=";")
    fields = reader.fieldnames
    rows = [row for row in reader if not row["id"].startswith("TR91-")]
if "validacao_fonte" not in fields:
    fields.append("validacao_fonte")
for row in rows:
    row["validacao_fonte"] = "CONFERIDA_NO_DOCUMENTO_OFICIAL"

candidates = extract_candidates(OCR_PATH.read_text(encoding="utf-8", errors="replace"))
for index, candidate in enumerate(candidates, start=1):
    page_ref = (f"PDF p.{candidate['page_start']}" if candidate["page_start"] == candidate["page_end"]
                else f"PDF pp.{candidate['page_start']}–{candidate['page_end']}")
    if candidate["marker"].startswith("d/SLA-"):
        item_ref = f", item d, subitem {candidate['marker'].split('/', 1)[1]}"
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
    synthetic_marker = candidate["marker"].startswith(("ui-", "acl-", "portal-task-", "fraud-", "report-", "stat-", "dashboard-", "manifestation-", "identification-"))
    summary = (
        candidate["body"] if synthetic_marker else
        f"{candidate['marker']}. {candidate['body']}" if candidate["marker"] else
        candidate["body"]
    )
    if visually_reviewed:
        acceptance = (
            "BLOQUEADO: transcrição conferida; decompor obrigações, definir aceite observável, "
            "responsáveis backend/frontend, teste e evidência antes de avaliar implementação."
        )
        test = "Transcrição visualmente conferida; teste automatizado pendente de decomposição e critério de aceite."
        evidence = f"Conferido visualmente no PDF oficial, {page_ref}; implementação e teste ainda sem evidência."
        poc = f"POC-TR91-{index:04d}: roteiro pendente de decomposição e implementação; fonte conferida, sem demonstração registrada."
    else:
        acceptance = "BLOQUEADO: comparar com o scan e dividir obrigações distintas deste item antes de definir aceite observável."
        test = "BLOQUEADO até confirmar a transcrição e decompor as obrigações deste item OCR."
        evidence = "Aguardando conferência da imagem oficial; OCR anexado apenas para descoberta."
        poc = f"POC-TR91-{index:04d}: bloqueado até validação visual da página {candidate['page_start']} do Termo de Referência."
    rows.append({
        "id": f"TR91-{index:04d}",
        "requisito_resumo_fiel": summary,
        "origem_documento_pagina_secao_item": origin,
        "prioridade": priority_for(candidate["section"], candidate["body"]),
        "modulo": module_for(candidate["section"], candidate["body"]),
        "natureza": nature_for(candidate["section"], candidate["body"]),
        "criterio_de_aceite": acceptance,
        "backend_envolvido": "A definir após validação visual e decomposição do requisito.",
        "frontend_envolvido": "A definir após validação visual e decomposição do requisito.",
        "teste_automatizado": test,
        "roteiro_poc": poc,
        "evidencia": evidence,
        "status": "BLOQUEADO",
        "validacao_fonte": "CONFERIDA_VISUALMENTE" if visually_reviewed else "OCR_PENDENTE",
    })

rows.sort(key=lambda row: (0 if row["id"].startswith("ED91-") else 1 if row["id"].startswith("TR91-") else 2, row["id"]))
with CSV_PATH.open("w", encoding="utf-8-sig", newline="") as stream:
    writer = csv.DictWriter(stream, fieldnames=fields, delimiter=";", quoting=csv.QUOTE_MINIMAL, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
verified_count = sum(row["validacao_fonte"] == "CONFERIDA_VISUALMENTE" for row in rows if row["id"].startswith("TR91-"))
pending_count = sum(row["validacao_fonte"] == "OCR_PENDENTE" for row in rows if row["id"].startswith("TR91-"))
print(f"Matriz atualizada: {len(rows)} linhas; {len(candidates)} requisitos TR, {verified_count} transcrições conferidas visualmente e {pending_count} pendentes. Os requisitos TR seguem BLOQUEADO até decomposição, critérios, testes e evidências.")
