#!/usr/bin/env python3
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

repo = Path(__file__).resolve().parents[1]
core = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else (repo.parent / "nexo-core").resolve()
namespace = {"m": "http://maven.apache.org/POM/4.0.0"}

def parse(path: Path):
    return ET.parse(path).getroot()

def value(root, query: str, source: Path) -> str:
    node = root.find(query, namespace)
    if node is None or node.text is None:
        raise SystemExit(f"Propriedade Maven ausente em {source}: {query}")
    return node.text.strip()

core_pom = core / "pom.xml"
gov_pom = repo / "backend/pom.xml"
if not core_pom.is_file():
    raise SystemExit(f"NEXO Core não encontrado: {core_pom}. Defina NEXO_CORE_DIR.")

core_root = parse(core_pom)
gov_root = parse(gov_pom)
core_group = value(core_root, "m:groupId", core_pom)
core_artifact = value(core_root, "m:artifactId", core_pom)
core_version = value(core_root, "m:version", core_pom)
core_dependency = next((item for item in gov_root.findall("m:dependencies/m:dependency", namespace)
                        if item.findtext("m:artifactId", namespaces=namespace) == core_artifact), None)
if core_dependency is None:
    raise SystemExit(f"O backend não declara dependência do artefato {core_artifact}.")
gov_group = value(core_dependency, "m:groupId", gov_pom)
gov_version_ref = value(core_dependency, "m:version", gov_pom)
if gov_group != core_group:
    raise SystemExit(f"NEXO Core groupId divergente: backend={gov_group}, Core={core_group}")
if gov_version_ref != "${nexo-core.version}":
    raise SystemExit(f"Versão da dependência deve usar ${{nexo-core.version}}, encontrada: {gov_version_ref}")
gov_version = value(gov_root, "m:properties/m:nexo-core.version", gov_pom)
if core_version != gov_version:
    raise SystemExit(f"Versão NEXO Core divergente: Core={core_version}, NexoGov={gov_version}")
print(f"NEXO Core alinhado: {core_group}:{core_artifact}:{core_version} ({core})")
