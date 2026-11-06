#!/usr/bin/env python3
"""Add individually addressable candidate requirements from the scanned official TR OCR.

Every added row remains BLOQUEADO until a person compares the OCR transcription against the
official scanned page. The OCR output is only a discovery aid and is never represented as
verified source text.
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
    r"o sistema|a plataforma|a solucao|integracao|permitir)\b", re.I)
SECTION = re.compile(r"^\s*(\d{1,2})[.)]\s+(.{3,150})$")
PAGE_MARKER = re.compile(r"^===== PÁGINA PDF (\d+) =====\s*$", re.M)

def folded(text):
    return "".join(char for char in unicodedata.normalize("NFKD", text.casefold()) if not unicodedata.combining(char))

def normalized(text):
    return " ".join(text.split()).strip()

with CSV_PATH.open(encoding="utf-8-sig", newline="") as stream:
    reader = csv.DictReader(stream, delimiter=";")
    fields = reader.fieldnames
    rows = list(reader)
    existing = {row["id"] for row in rows}
    rows = [row for row in rows if not row["id"].startswith("TR91-")]

ocr_text = OCR_PATH.read_text(encoding="utf-8", errors="replace")
markers = list(PAGE_MARKER.finditer(ocr_text))
if len(markers) != 69:
    raise SystemExit(f"OCR deve conter marcador para 69 páginas; encontrado(s) {len(markers)}.")
section = "Termo de Referência, anexo técnico"
tr_rows = []
for page_index, marker in enumerate(markers):
    page_number = int(marker.group(1))
    end = markers[page_index + 1].start() if page_index + 1 < len(markers) else len(ocr_text)
    raw_page = ocr_text[marker.end():end]
    blocks = re.split(r"\n\s*\n", raw_page)
    block_number = 0
    for block in blocks:
        lines = []
        for line in block.splitlines():
            text = normalized(line)
            low_line = folded(text)
            if not text or low_line.startswith(("prefeitura municipal de mata de sao joao", "secretaria de administracao e financas", "para verificar a validade", "assinado por 1 pessoa", "1doc.com.br/verificacao")):
                continue
            heading = SECTION.match(text)
            if heading and (text == text.upper() or len(text) < 110):
                section = f"§{heading.group(1)} {heading.group(2).strip()}"
            lines.append(text)
        body = normalized(" ".join(lines))
        if len(body) < 45 or not NORMATIVE.search(folded(body)):
            continue
        block_number += 1
        if page_number > 66:  # Qualification and price forms are handled in explicit rows.
            continue
        rid = f"TR91-{len(tr_rows)+1:04d}"
        module = "PLATAFORMA_E_ARQUITETURA"
        low = folded(section)
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
            ("acesso interno", "IDENTIDADE_E_SEGURANCA"), ("acesso externo", "PORTAL_EXTERNO"),
            ("workflow", "PROCESSOS_ADMINISTRATIVOS"),
        ):
            if needle in low:
                module = mapped
                break
        rows.append({
            "id": rid,
            "requisito_resumo_fiel": body,
            "origem_documento_pagina_secao_item": f"Termo de Referência, {section}, PDF p.{page_number}, candidato OCR em português {block_number}; transcrição a conferir visualmente no scan.",
            "prioridade": "OBRIGATÓRIO",
            "modulo": module,
            "natureza": "CANDIDATO OCR PT NÃO CONFERIDO",
            "criterio_de_aceite": "BLOQUEADO: confrontar a transcrição com o PDF original, separar todos os requisitos do parágrafo e então registrar um critério observável sem alterar o sentido.",
            "backend_envolvido": "A definir após validação visual do requisito.",
            "frontend_envolvido": "A definir após validação visual do requisito.",
            "teste_automatizado": "BLOQUEADO até confirmar o texto e decompor as obrigações deste parágrafo OCR.",
            "roteiro_poc": f"POC-{rid}: bloqueado até validação visual da página {page_number} do Termo de Referência.",
            "evidencia": "Aguardando conferência da imagem oficial; OCR anexado apenas para busca.",
            "status": "BLOQUEADO",
        })
        tr_rows.append(rows[-1])

rows.sort(key=lambda row: (0 if row["id"].startswith("ED91-") else 1 if row["id"].startswith("TR91-") else 2, row["id"]))
with CSV_PATH.open("w", encoding="utf-8-sig", newline="") as stream:
    writer = csv.DictWriter(stream, fieldnames=fields, delimiter=";", quoting=csv.QUOTE_MINIMAL)
    writer.writeheader()
    writer.writerows(rows)
print(f"Matriz atualizada: {len(rows)} linhas; {len(tr_rows)} requisitos candidatos do TR marcados BLOQUEADO até conferência visual.")
