#!/usr/bin/env python3
"""Add individually addressable candidates from the scanned official TR OCR.

Each enumerated item is kept as its own candidate, including non-normative objectives.
Unnumbered paragraphs are included when they define scope or contain normative language.
Nothing generated from OCR is treated as verified until compared with the scanned PDF.
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
SECTION = re.compile(r"^\s*(\d{1,2})[.)]\s+(.{3,150})$")
BULLET = re.compile(r"^\s*([a-zA-Z]|[ivxlcdm]{1,5}|\d{1,2})[.)]\s+(.+)$")
PAGE_MARKER = re.compile(r"^===== PÁGINA PDF (\d+) =====\s*$", re.M)
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
    if re.fullmatch(r"[-–]\s*\d{1,3}\s*[-–]|[oO]", text):
        return ""
    return text


def corrected_bullet(line, page_number, section):
    """Restore obvious OCR-dropped list markers where the scanned pages were checked."""
    low = folded(line)
    section_low = folded(section)
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
    normal_lines = []
    normal_page = None
    normal_section = None
    bullet = None

    def flush_normal():
        nonlocal normal_lines, normal_page, normal_section
        body = normalized(" ".join(normal_lines))
        if body and len(body) >= 35:
            candidates.append({
                "page_start": normal_page,
                "page_end": normal_page,
                "section": normal_section,
                "marker": "",
                "body": body,
            })
        normal_lines = []
        normal_page = None
        normal_section = None

    def flush_bullet():
        nonlocal bullet
        if bullet:
            body = normalized(" ".join(bullet["lines"]))
            if len(body) >= 12:
                bullet["body"] = body
                candidates.append(bullet)
        bullet = None

    for page_index, marker in enumerate(markers):
        page_number = int(marker.group(1))
        end = markers[page_index + 1].start() if page_index + 1 < len(markers) else len(text)
        raw_page = text[marker.end():end]
        for raw_line in raw_page.splitlines():
            line = clean_source_line(raw_line)
            if not line:
                if bullet is None:
                    flush_normal()
                continue

            heading = SECTION.match(line)
            if heading and (line == line.upper() or len(line) < 110):
                flush_normal()
                flush_bullet()
                section = f"§{heading.group(1)} {heading.group(2).strip()}"
                continue

            folded_line = folded(line.lstrip(". "))
            if folded_line.startswith("caracteristicas tecnicas da solucao"):
                flush_normal()
                flush_bullet()
                section = "§4 CARACTERISTICAS TÉCNICAS DA SOLUÇÃO"
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
                    normal_section = section
                normal_lines.append(line)

        flush_normal()
        # A trailing bullet can continue at the top of the next scanned page.

    flush_normal()
    flush_bullet()

    filtered = []
    for candidate in candidates:
        body = candidate["body"]
        low = folded(body)
        section_low = folded(candidate["section"])
        is_scope = any(term in section_low for term in ("objeto", "justificativa", "objetivo do projeto", "caracteristicas tecnicas"))
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
        else:
            merged.append(candidate)
    filtered = merged

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
    return filtered


def module_for(section, body):
    text = folded(section + " " + body)
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


with CSV_PATH.open(encoding="utf-8-sig", newline="") as stream:
    reader = csv.DictReader(stream, delimiter=";")
    fields = reader.fieldnames
    rows = [row for row in reader if not row["id"].startswith("TR91-")]

candidates = extract_candidates(OCR_PATH.read_text(encoding="utf-8", errors="replace"))
for index, candidate in enumerate(candidates, start=1):
    page_ref = (f"PDF p.{candidate['page_start']}" if candidate["page_start"] == candidate["page_end"]
                else f"PDF pp.{candidate['page_start']}–{candidate['page_end']}")
    if candidate["marker"].startswith("d/SLA-"):
        item_ref = f", item d, subitem {candidate['marker'].split('/', 1)[1]}"
    else:
        item_ref = f", item {candidate['marker']}" if candidate["marker"] else f", candidato OCR PT {index}"
    origin = f"Termo de Referência, {candidate['section']}{item_ref}, {page_ref}; OCR em português não conferido visualmente."
    summary = f"{candidate['marker']}. {candidate['body']}" if candidate["marker"] else candidate["body"]
    rows.append({
        "id": f"TR91-{index:04d}",
        "requisito_resumo_fiel": summary,
        "origem_documento_pagina_secao_item": origin,
        "prioridade": "OBRIGATÓRIO",
        "modulo": module_for(candidate["section"], candidate["body"]),
        "natureza": "CANDIDATO OCR PT NÃO CONFERIDO",
        "criterio_de_aceite": "BLOQUEADO: comparar com o scan e dividir obrigações distintas deste item antes de definir aceite observável.",
        "backend_envolvido": "A definir após validação visual e decomposição do requisito.",
        "frontend_envolvido": "A definir após validação visual e decomposição do requisito.",
        "teste_automatizado": "BLOQUEADO até confirmar a transcrição e decompor as obrigações deste item OCR.",
        "roteiro_poc": f"POC-TR91-{index:04d}: bloqueado até validação visual da página {candidate['page_start']} do Termo de Referência.",
        "evidencia": "Aguardando conferência da imagem oficial; OCR anexado apenas para descoberta.",
        "status": "BLOQUEADO",
    })

rows.sort(key=lambda row: (0 if row["id"].startswith("ED91-") else 1 if row["id"].startswith("TR91-") else 2, row["id"]))
with CSV_PATH.open("w", encoding="utf-8-sig", newline="") as stream:
    writer = csv.DictWriter(stream, fieldnames=fields, delimiter=";", quoting=csv.QUOTE_MINIMAL, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
print(f"Matriz atualizada: {len(rows)} linhas; {len(candidates)} candidatos do TR (alíneas e parágrafos) marcados BLOQUEADO até conferência visual.")
