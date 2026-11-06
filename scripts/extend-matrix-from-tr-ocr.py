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
SECTION = re.compile(r"^\s*(\d{1,2})[.)]\s*(.{3,150})$")
SUBSECTION = re.compile(r"^\s*([a-dA-D])[.)]\s+(.{3,150})$")
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
    if re.fullmatch(r"[-–]\s*(?:\d{1,3}|[iIlL]{1,3})\s*[-–]|[oO]", text):
        return ""
    return text


def corrected_bullet(line, page_number, section):
    """Restore obvious OCR-dropped list markers where the scanned pages were checked."""
    low = folded(line)
    section_low = folded(section)
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
                    normal_section = section
                normal_lines.append(line)

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
