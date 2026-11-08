#!/usr/bin/env python3
import re
import sys
from pathlib import Path
from urllib.parse import urlsplit


if len(sys.argv) != 2:
    raise SystemExit("Uso: check-frontend-auth-bundle.py <diretório-do-build>")

build_dir = Path(sys.argv[1]).resolve()
entry_html = build_dir / "index.html"
if not entry_html.is_file():
    raise SystemExit(f"Entrada pública não encontrada: {entry_html}")

scripts = re.findall(r"<script[^>]+src=[\"']([^\"']+)[\"']", entry_html.read_text(encoding="utf-8"))
if not scripts:
    raise SystemExit("A página pública não contém script de entrada.")

import_pattern = re.compile(r"(?:\bfrom\s*|\bimport\s*\()\s*[\"']([^\"']+)[\"']")
blocked_patterns = {
    "HTTP Basic authorization helper": re.compile(r"Basic\s+"),
    "Axios request/auth implementation": re.compile(r"\b(?:AxiosError|AxiosHeaders|axios\.create)\b", re.IGNORECASE),
    "staff destination department field": re.compile(r"destinationDepartment(?:Id|Name)"),
}

pending = []
for script in scripts:
    url = urlsplit(script)
    relative = url.path.lstrip("/") if url.path.startswith("/") else url.path
    pending.append((build_dir / relative).resolve())

visited = set()
failures = []
while pending:
    asset = pending.pop()
    if asset in visited:
        continue
    if build_dir not in asset.parents or not asset.is_file():
        raise SystemExit(f"Import público fora do build ou ausente: {asset}")
    visited.add(asset)
    source = asset.read_text(encoding="utf-8")
    for label, pattern in blocked_patterns.items():
        if pattern.search(source):
            failures.append(f"{asset.relative_to(build_dir)}: contém {label}")
    for imported in import_pattern.findall(source):
        url = urlsplit(imported)
        if not url.path.endswith(".js"):
            continue
        relative = url.path.lstrip("/") if url.path.startswith("/") else url.path
        child = (asset.parent / relative).resolve() if not url.path.startswith("/") else (build_dir / relative).resolve()
        pending.append(child)

if failures:
    raise SystemExit("\n".join(failures))
print(f"Bundle público conferido: {len(visited)} módulos sem auxiliares de Basic/Auth do Axios ou campos internos do servidor.")
