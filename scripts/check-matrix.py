#!/usr/bin/env python3
"""Validate traceability structure and print current evidence coverage."""
import csv
from collections import Counter
from pathlib import Path

root = Path(__file__).resolve().parents[1]
matrix = root / "docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv"
allowed = {"NAO_INICIADO", "EM_DESENVOLVIMENTO", "IMPLEMENTADO_NAO_TESTADO", "VALIDADO", "BLOQUEADO", "NAO_APLICAVEL_JUSTIFICADO"}
required = {"id", "requisito_resumo_fiel", "origem_documento_pagina_secao_item", "prioridade", "modulo", "natureza", "criterio_de_aceite", "backend_envolvido", "frontend_envolvido", "teste_automatizado", "roteiro_poc", "evidencia", "status"}
with matrix.open(encoding="utf-8-sig", newline="") as stream:
    reader = csv.DictReader(stream, delimiter=";")
    missing = required - set(reader.fieldnames or [])
    if missing:
        raise SystemExit(f"Colunas ausentes: {sorted(missing)}")
    rows = list(reader)
ids = [row["id"] for row in rows]
duplicates = [key for key, count in Counter(ids).items() if count != 1]
bad = [(row["id"], row["status"]) for row in rows if row["status"] not in allowed]
empty = [(row["id"], key) for row in rows for key in required if not row.get(key, "").strip()]
if duplicates or bad or empty:
    raise SystemExit(f"IDs duplicados={duplicates}; status inválidos={bad}; campos vazios={empty[:20]}")
statuses = Counter(row["status"] for row in rows)
modules = Counter(row["modulo"] for row in rows)
print(f"Linhas rastreadas: {len(rows)}")
print("Status: " + ", ".join(f"{k}={v}" for k, v in sorted(statuses.items())))
print("Módulos: " + ", ".join(f"{k}={v}" for k, v in sorted(modules.items())))
print(f"Validados: {statuses['VALIDADO']}/{len(rows)}")
