#!/usr/bin/env python3
"""Validate traceability structure and print current evidence coverage."""
import csv
import hashlib
import re
import sys
import tempfile
from collections import Counter
from pathlib import Path

root = Path(__file__).resolve().parents[1]
matrix = root / "docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv"
registry_path = matrix.with_name("MATRIZ_IDS.csv")
sys.path.insert(0, str(root / "scripts"))
from matrix_id_registry import MatrixIdRegistry
allowed = {"NAO_INICIADO", "EM_DESENVOLVIMENTO", "IMPLEMENTADO_NAO_TESTADO", "VALIDADO", "BLOQUEADO", "NAO_APLICAVEL_JUSTIFICADO"}
required = {"id", "requisito_resumo_fiel", "origem_documento_pagina_secao_item", "prioridade", "modulo", "natureza", "criterio_de_aceite", "backend_envolvido", "frontend_envolvido", "teste_automatizado", "roteiro_poc", "evidencia", "status", "validacao_fonte"}
valid_source_states = {"CONFERIDA_NO_DOCUMENTO_OFICIAL", "CONFERIDA_VISUALMENTE", "OCR_PENDENTE"}
with matrix.open(encoding="utf-8-sig", newline="") as stream:
    reader = csv.DictReader(stream, delimiter=";")
    missing = required - set(reader.fieldnames or [])
    if missing:
        raise SystemExit(f"Colunas ausentes: {sorted(missing)}")
    rows = list(reader)
ids = [row["id"] for row in rows]
duplicates = [key for key, count in Counter(ids).items() if count != 1]
bad = [(row["id"], row["status"]) for row in rows if row["status"] not in allowed]
bad_source = [(row["id"], row["validacao_fonte"]) for row in rows if row["validacao_fonte"] not in valid_source_states]
empty = [(row["id"], key) for row in rows for key in required if not row.get(key, "").strip()]
if duplicates or bad or bad_source or empty:
    raise SystemExit(f"IDs duplicados={duplicates}; status inválidos={bad}; validação de fonte inválida={bad_source}; campos vazios={empty[:20]}")

# Keep the manually maintained source inventory's status table aligned with
# the CSV so counts cannot drift after a matrix refresh.
requirements_doc = matrix.with_name("REQUISITOS.md")
requirements_text = requirements_doc.read_text(encoding="utf-8")
source_status_order = (
    "EM_DESENVOLVIMENTO", "IMPLEMENTADO_NAO_TESTADO", "VALIDADO",
    "NAO_INICIADO", "BLOQUEADO", "NAO_APLICAVEL_JUSTIFICADO",
)
source_groups = (
    ("Edital e complementos ED91", lambda row: row["id"].startswith("ED91-")),
    ("Termo de Referência TR91", lambda row: row["id"].startswith("TR91-")),
    ("Total da matriz", lambda row: True),
)
for label, predicate in source_groups:
    scoped_rows = [row for row in rows if predicate(row)]
    expected_counts = [len(scoped_rows)] + [
        sum(row["status"] == status for row in scoped_rows) for status in source_status_order
    ]
    table_row = next((line for line in requirements_text.splitlines()
                      if line.startswith(f"| {label} |")), None)
    if table_row is None:
        raise SystemExit(f"Linha de contagem ausente em REQUISITOS.md: {label}")
    try:
        # Requirements prose follows pt-BR thousands separators (for example,
        # 1.388), while counts parsed from the CSV are plain integers.
        observed_counts = [int(value.strip().replace(".", ""))
                           for value in table_row.strip("|").split("|")[1:]]
    except ValueError as failure:
        raise SystemExit(f"Contagem não numérica em REQUISITOS.md: {label}: {table_row}") from failure
    if observed_counts != expected_counts:
        raise SystemExit(
            f"Contagens desatualizadas em REQUISITOS.md ({label}): {observed_counts}; "
            f"esperado {expected_counts}. Atualize a tabela após revisar os estados da matriz."
        )

# Guard against the generic “gerenciamento de atividades” phrase being
# mistaken for deadline/calendar handling during edital-module classification.
expected_modules = {"ED91-0001": "PLATAFORMA_E_ARQUITETURA"}
rows_by_id = {row["id"]: row for row in rows}
for requirement_id, expected_module in expected_modules.items():
    row = rows_by_id.get(requirement_id)
    if row is None:
        raise SystemExit(f"Requisito esperado ausente da matriz: {requirement_id}")
    if row["modulo"] != expected_module:
        raise SystemExit(
            f"Classificação regressiva para {requirement_id}: módulo={row['modulo']}; "
            f"esperado={expected_module}"
        )
    if not row["roteiro_poc"].startswith(f"POC-{expected_module}:"):
        raise SystemExit(
            f"Roteiro POC não acompanha o módulo de {requirement_id}: {row['roteiro_poc']}"
        )

numeric_ids = {requirement_id for requirement_id in ids if re.fullmatch(r"(?:ED91|TR91)-\d+", requirement_id)}
registry = MatrixIdRegistry(registry_path)
registered_ids = set(registry.ids)
if numeric_ids != registered_ids:
    raise SystemExit(
        f"Registro de IDs divergente: sem âncora={sorted(numeric_ids - registered_ids)[:20]}; "
        f"sem linha de matriz={sorted(registered_ids - numeric_ids)[:20]}"
    )
for row in registry.entries.values():
    if hashlib.sha256(row["source_anchor"].encode("utf-8")).hexdigest() != row["identity_key"]:
        raise SystemExit(f"Âncora/ID hash divergente: {row['id']}")

with tempfile.TemporaryDirectory(prefix="nexogov-matrix-ids-") as temporary_directory:
    temporary_path = Path(temporary_directory) / "MATRIZ_IDS.csv"
    test_registry = MatrixIdRegistry(temporary_path)
    first = test_registry.assign("TR91", "p.48", "Obrigação de teste A")
    second = test_registry.assign("TR91", "p.48", "Obrigação de teste B")
    test_registry.save()
    reordered = MatrixIdRegistry(temporary_path)
    if reordered.assign("TR91", "p.48", "Obrigação de teste B") != second:
        raise SystemExit("O ID estável mudou ao reordenar os itens.")
    if reordered.assign("TR91", "p.48", "Obrigação de teste A") != first:
        raise SystemExit("O ID estável mudou ao reordenar os itens.")
    third = reordered.assign("TR91", "p.48", "Obrigação nova")
    if third != "TR91-0003":
        raise SystemExit(f"Novo ID não foi acrescentado ao fim da sequência: {third}")
statuses = Counter(row["status"] for row in rows)
modules = Counter(row["modulo"] for row in rows)
pending_decomposition = [
    row for row in rows
    if row["criterio_de_aceite"].startswith("Pendente de decomposição:")
]
pending_decomposition_by_source = Counter(
    row["id"].split("-", 1)[0] for row in pending_decomposition
)
print(f"Linhas rastreadas: {len(rows)}")
print("Status: " + ", ".join(f"{k}={v}" for k, v in sorted(statuses.items())))
print("Módulos: " + ", ".join(f"{k}={v}" for k, v in sorted(modules.items())))
print(f"Validados: {statuses['VALIDADO']}/{len(rows)}")
print(
    "Critérios pendentes de decomposição observável: "
    f"{len(pending_decomposition)} "
    f"(ED91={pending_decomposition_by_source['ED91']}; "
    f"TR91={pending_decomposition_by_source['TR91']})"
)
source_counts = Counter(row["validacao_fonte"] for row in rows)
print("Fonte: " + ", ".join(f"{k}={v}" for k, v in sorted(source_counts.items())))
print(f"IDs numéricos estáveis: {len(registered_ids)}; integridade do registro e reordenação verificadas")
