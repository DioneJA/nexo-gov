#!/usr/bin/env python3
"""Export exact, passed Playwright evidence screenshots and a full gap manifest."""

from __future__ import annotations

import argparse
import base64
import binascii
import csv
import hashlib
import json
import os
import re
import unicodedata
from datetime import datetime, timezone
from pathlib import Path


SAFE_SCENARIOS: dict[tuple[str, str], dict] = {
    (
        "organization-members.spec.ts",
        "administrador consulta membros e suspende/reativa acesso pela API da organização",
    ): {
        "labels": {"membro-reativado"},
        "ids": {"ED91-0042", "ED91-0046", "TR91-0052", "TR91-0056"},
        "id_labels": {
            "ED91-0042": {"membro-reativado"},
            "ED91-0046": {"membro-reativado"},
            "TR91-0052": {"membro-reativado"},
            "TR91-0056": {"membro-reativado"},
        },
        "nota": (
            "Subfluxo visual após reativação, com conta local fictícia. Esta imagem não prova, sozinha, "
            "autorização de backend, auditoria, preservação de vínculos/histórico, provisionamento nem o "
            "critério integral; os testes de integração são evidência separada."
        ),
    },
    (
        "organization-contacts.spec.ts",
        "administrador mantém contatos por tipo e importa/exporta CSV",
    ): {
        "labels": {"contatos-tipos", "contatos-gerenciados", "contatos-csv-importado-exportado"},
        "ids": {"ED91-0130", "ED91-0131", "ED91-0132", "TR91-0135", "TR91-0136", "TR91-0137"},
        "id_labels": {
            "ED91-0130": {"contatos-gerenciados"},
            "ED91-0131": {"contatos-tipos"},
            "ED91-0132": {"contatos-csv-importado-exportado"},
            "TR91-0135": {"contatos-gerenciados"},
            "TR91-0136": {"contatos-tipos"},
            "TR91-0137": {"contatos-csv-importado-exportado"},
        },
        "nota": (
            "Capturas da área de contatos da organização com dados fictícios: manutenção, categorias previstas no TR e CSV importado/exportado. "
            "As imagens não provam, sozinhas, autorização, persistência, rollback, isolamento ou auditoria; consulte testes Testcontainers. "
            "O cenário não comprova organograma oficial, vCard, deduplicação, listas de contatos, integração legada ou POC oficial."
        ),
    },
    (
        "protocol-participant-fields.spec.ts",
        "administrador configura e mantém campos de identificação por assunto",
    ): {
        "labels": {"configuracao-persistida"},
        "ids": set(),
        "id_labels": {},
        "nota": (
            "Artefato de engenharia da configuração administrativa. A matriz não referencia esta spec; "
            "nenhum ID de edital é associado por inferência. Não demonstra o fluxo de acesso/interação de participantes."
        ),
    },
    (
        "protocol-flow.spec.ts",
        "cidadão abre protocolo, servidor tramita e cidadão acompanha até a conclusão",
    ): {
        "labels": {"organograma-presenca"},
        "ids": {"ED91-0103", "ED91-0107", "TR91-0111"},
        "id_labels": {
            "ED91-0103": {"organograma-presenca"},
            "ED91-0107": {"organograma-presenca"},
            "TR91-0111": {"organograma-presenca"},
        },
        "nota": (
            "Subfluxo visual da árvore organizacional, perfil fictício e presença ONLINE após as verificações do teste. "
            "A imagem não expõe nem comprova a chave do protocolo; isolamento, persistência, auditoria e transições "
            "dependem também dos testes de backend referidos na matriz."
        ),
    },
    (
        "protocol-read-status.spec.ts",
        "caixa mostra leitura por setor, por outro servidor e pelo usuário atual",
    ): {
        "labels": {"leitura-nao-iniciada", "leitura-por-colega", "leitura-pelo-servidor"},
        "ids": {"ED91-0120", "ED91-0140", "TR91-0125", "TR91-0145"},
        "id_labels": {
            "ED91-0120": {"leitura-nao-iniciada", "leitura-por-colega", "leitura-pelo-servidor"},
            "ED91-0140": {"leitura-nao-iniciada", "leitura-por-colega", "leitura-pelo-servidor"},
            "TR91-0125": {"leitura-nao-iniciada", "leitura-por-colega", "leitura-pelo-servidor"},
            "TR91-0145": {"leitura-nao-iniciada", "leitura-por-colega", "leitura-pelo-servidor"},
        },
        "nota": (
            "Capturas mostram três estados do indicador na caixa de entrada, com dois servidores fictícios do mesmo setor. "
            "Não comprovam leitura individual de cada documento, nomes de leitores, histórico detalhado, contagem de downloads, "
            "outros módulos documentais ou POC oficial."
        ),
    },
    (
        "protocol-interactions.spec.ts",
        "conversa de protocolo mantém respostas, anexos e visibilidade no fluxo real",
    ): {
        "labels": {"protocolo-concluido", "protocolo-reaberto"},
        "ids": {"ED91-0483", "TR91-0515"},
        "id_labels": {
            "ED91-0483": {"protocolo-concluido", "protocolo-reaberto"},
            "TR91-0515": {"protocolo-concluido", "protocolo-reaberto"},
        },
        "nota": (
            "Capturas da área do servidor após conclusão e reabertura. São subfluxos visuais; autorização por setor, "
            "respostas HTTP, concorrência, append-only, isolamento e novas mensagens dependem dos testes "
            "Testcontainers/API indicados na matriz."
        ),
    },
    (
        "protocol-subject-catalog.spec.ts",
        "administrador configura o prazo automático no comprovante real do protocolo",
    ): {
        "labels": {"configuracao-persistida", "confirmacao-cidadao"},
        "ids": {"TR91-0510", "TR91-0517"},
        "id_labels": {
            "TR91-0510": {"confirmacao-cidadao"},
            "TR91-0517": {"configuracao-persistida"},
        },
        "nota": (
            "Capturas mostram a configuração persistida e a confirmação pública; a chave do comprovante é alterada "
            "para password e seu locator é passado à máscara do screenshot. A imagem exportada precisa ser inspecionada visualmente; "
            "os bytes comprimidos do PNG não provam a ocultação dos pixels. O PDF não é exportado porque pode conter chave/QR; "
            "o E2E extrai e verifica o conteúdo do PDF real, mas isso permanece evidência de teste separada."
        ),
    },
    (
        "protocol-attachments.spec.ts",
        "assunto exige documento, cidadão envia e servidor consulta e baixa o anexo",
    ): {
        "labels": {
            "regra-anexo-persistida",
            "anexo-cidadao-enviado-listado",
            "anexo-setor-consultado-baixado",
        },
        "ids": {"TR91-0516", "TR91-0019", "ED91-0170", "TR91-0174"},
        "id_labels": {
            "TR91-0516": {"regra-anexo-persistida", "anexo-cidadao-enviado-listado"},
            "TR91-0019": {"anexo-setor-consultado-baixado"},
            "ED91-0170": {"anexo-cidadao-enviado-listado", "anexo-setor-consultado-baixado"},
            "TR91-0174": {"anexo-cidadao-enviado-listado", "anexo-setor-consultado-baixado"},
        },
        "nota": (
            "Subfluxos visuais locais com assunto, protocolo e arquivo fictícios: configuração de regra, anexo enviado/listado pelo cidadão e anexo consultado/baixado no setor. "
            "Na captura pública a chave do comprovante é convertida para password e seu locator é incluído na máscara do helper; nenhum QR ou cabeçalho de autenticação é exibido. "
            "A ocultação dos pixels só é confirmada pela inspeção visual do PNG exportado, não por busca do bearer nos bytes comprimidos. "
            "S3Mock é um dublê local da API S3 e não comprova hospedagem, segurança ou operação de bucket produtivo. "
            "As imagens não demonstram a regra opcional, todos os formatos, todo o escopo documental, obrigação contratual completa ou POC oficial. "
            "ED91-0484 é composto também por prazo automático e permanece sem associação visual por este cenário de anexos."
        ),
    },
    (
        "process-workflow.spec.ts",
        "administrador publica workflow versionado e acompanha o snapshot do processo",
    ): {
        "labels": {
            "workflow-publicado-v1",
            "workflow-decisores-configurados",
            "workflow-publicado-v2",
            "processo-snapshot-v1",
            "processo-decisao-registrada",
            "processo-decisao-persistida",
            "processo-avancado-para-etapa-final",
            "processo-listado-apos-reinicio",
            "detalhe-reaberto-da-listagem",
        },
        "ids": {"ED91-0510", "ED91-0596", "TR91-0560", "TR91-0691"},
        "id_labels": {
            "ED91-0510": {
                "workflow-decisores-configurados",
                "processo-decisao-registrada",
                "processo-decisao-persistida",
            },
            "ED91-0596": {
                "workflow-publicado-v1",
                "workflow-publicado-v2",
                "processo-snapshot-v1",
                "processo-avancado-para-etapa-final",
                "processo-listado-apos-reinicio",
                "detalhe-reaberto-da-listagem",
            },
            "TR91-0560": {
                "workflow-decisores-configurados",
                "processo-decisao-registrada",
                "processo-decisao-persistida",
            },
            "TR91-0691": {
                "workflow-publicado-v1",
                "workflow-publicado-v2",
                "processo-snapshot-v1",
                "processo-avancado-para-etapa-final",
                "processo-listado-apos-reinicio",
                "detalhe-reaberto-da-listagem",
            },
        },
        "nota": (
            "Subfluxo visual local com organização e processo fictícios: publicação v1/v2, configuração do setor decisor, "
            "registro de Deferido sem encerrar o processo, persistência da decisão após recarga, snapshot v1 preservado, "
            "avanço à última etapa, listagem autorizada e reabertura do detalhe após nova autenticação. "
            "O Playwright também verifica HTTP 409 sem mutação ao tentar pular etapa e HTTP 403 sem mutação para servidor sem lotação no setor atual. "
            "As capturas mostram somente os três estados requeridos para a decisão; não provam sozinhas as recusas, "
            "outro resultado ou idempotência, encerramento do processo, condições avançadas, exportação, notificações, POC oficial ou critérios integrais."
        ),
    },
}

SENSITIVE_SPEC_REASONS = {
    "protocol-participant-access.spec.ts": "Sem captura: o fluxo manipula convites/chaves bearer e dados de identidade de participantes.",
    "session-cleanup.spec.ts": "Sem captura: o fluxo manipula chaves bearer e estado de sessão/token.",
    "staff-oidc.spec.ts": "Sem captura: o fluxo manipula tokens e estado de autenticação federada.",
}
SPEC_RE = re.compile(r"(?:frontend/e2e/)?([A-Za-z0-9_.-]+\.spec\.ts)")
GREP_RE = re.compile(r"\./dev\s+e2e\s+--grep\s+['\"]([^'\"]+)['\"]")
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
MAX_INLINE_ATTACHMENT_BYTES = 20 * 1024 * 1024


def required_labels_for_id(policy: dict, requirement_id: str) -> set[str]:
    return set(policy.get("id_labels", {}).get(requirement_id, policy["labels"]))


def capture_is_complete(policy: dict, found_labels: set[str]) -> bool:
    return found_labels == set(policy["labels"])


def supported_ids(policy: dict, found_labels: set[str]) -> set[str]:
    return {
        requirement_id
        for requirement_id in policy["ids"]
        if required_labels_for_id(policy, requirement_id).issubset(found_labels)
    }


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def slug(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value)
    ascii_value = normalized.encode("ascii", "ignore").decode("ascii").lower()
    return re.sub(r"-+", "-", re.sub(r"[^a-z0-9]+", "-", ascii_value)).strip("-")[:90] or "cenario"


def test_title_count(spec_path: Path, title: str | None = None) -> int:
    if not spec_path.is_file():
        return 0
    source = spec_path.read_text(encoding="utf-8")
    titles = [value for _, value in re.findall(r"\btest\s*\(\s*(['\"])(.*?)\1", source, re.DOTALL)]
    return titles.count(title) if title is not None else len(titles)


def evidence_capture_labels(spec_path: Path) -> list[str]:
    if not spec_path.is_file():
        return []
    source = spec_path.read_text(encoding="utf-8")
    return [
        label
        for _, label in re.findall(
            r"\battachEvidenceScreenshot\s*\(\s*[A-Za-z_$][\w$]*\s*,\s*testInfo\s*,\s*(['\"])(.*?)\1",
            source,
            re.DOTALL,
        )
    ]


def collect_specs(suites: list[dict], inherited_file: str = "") -> list[dict]:
    found: list[dict] = []
    for suite in suites:
        suite_file = suite.get("file") or inherited_file
        found.extend(collect_specs(suite.get("suites", []), suite_file))
        for spec in suite.get("specs", []):
            found.append({"file": spec.get("file") or suite_file, **spec})
    return found


def normalize_spec(value: str) -> str:
    return Path(value.replace("\\", "/")).name


def inside(path: Path, root: Path) -> bool:
    try:
        return os.path.commonpath([str(path.resolve(strict=True)), str(root.resolve(strict=True))]) == str(root.resolve(strict=True))
    except (OSError, ValueError):
        return False


def matrix_rows(path: Path) -> list[dict]:
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream, delimiter=";"))


def attachment_source(
    attachment: dict, artifact_root: Path, report_path: Path, repo_root: Path
) -> Path | None:
    raw = attachment.get("path")
    if not isinstance(raw, str) or not raw:
        return None
    raw_path = Path(raw)
    candidates = [raw_path] if raw_path.is_absolute() else [
        artifact_root / raw_path,
        report_path.parent / raw_path,
        repo_root / raw_path,
        repo_root / "frontend" / raw_path,
    ]
    for candidate in candidates:
        try:
            resolved = candidate.resolve(strict=True)
        except OSError:
            continue
        if resolved.is_file() and inside(resolved, artifact_root):
            return resolved
    return None


def attachment_bytes(
    attachment: dict, artifact_root: Path, report_path: Path, repo_root: Path
) -> bytes | None:
    """Read a PNG attachment only from the private stage or the in-stage JSON body."""
    source = attachment_source(attachment, artifact_root, report_path, repo_root)
    if source and source.suffix.lower() == ".png":
        try:
            source_bytes = source.read_bytes()
        except OSError:
            source_bytes = b""
        if source_bytes.startswith(PNG_SIGNATURE):
            return source_bytes

    body = attachment.get("body")
    if not isinstance(body, str) or not inside(report_path, artifact_root):
        return None
    # Bound decoding before allocating, then require canonical base64 input.
    if len(body) > ((MAX_INLINE_ATTACHMENT_BYTES + 2) // 3) * 4:
        return None
    try:
        decoded = base64.b64decode(body, validate=True)
    except (binascii.Error, ValueError):
        return None
    if len(decoded) > MAX_INLINE_ATTACHMENT_BYTES or not decoded.startswith(PNG_SIGNATURE):
        return None
    return decoded


def interaction_trace_is_exact(repo_root: Path, matrix_test: str, spec_path: Path, title: str) -> bool:
    if "./dev interaction-test" not in matrix_test:
        return False
    dev_text = (repo_root / "dev").read_text(encoding="utf-8")
    runner_text = (repo_root / "scripts/protocol-interaction-test.sh").read_text(encoding="utf-8")
    exact_dispatch = 'interaction-test) exec "$repo_root/scripts/protocol-interaction-test.sh" ;;' in dev_text
    exact_grep = '"$repo_root/dev" e2e --grep \'conversa de protocolo\'' in runner_text
    spec_text = spec_path.read_text(encoding="utf-8")
    return exact_dispatch and exact_grep and title in spec_text and test_title_count(spec_path, title) == 1


def explicit_row_maps_to_scenario(row: dict, spec_name: str, title: str, repo_root: Path) -> bool:
    text = "\n".join(
        row.get(field, "")
        for field in ("frontend_envolvido", "teste_automatizado", "roteiro_poc", "evidencia")
    )
    refs = {normalize_spec(value) for value in SPEC_RE.findall(text)}
    if spec_name in refs:
        source = repo_root / "frontend" / "e2e" / spec_name
        exact_grep = any(title == expression.strip() for expression in GREP_RE.findall(text))
        return test_title_count(source, title) == 1 or exact_grep
    if (spec_name, title) == (
        "protocol-interactions.spec.ts",
        "conversa de protocolo mantém respostas, anexos e visibilidade no fluxo real",
    ):
        return interaction_trace_is_exact(repo_root, text, repo_root / "frontend/e2e" / spec_name, title)
    return False


def safe_destination(destination: Path) -> None:
    if not destination.exists() or not destination.is_dir():
        raise SystemExit("O diretório run-id deve ser criado antes pelo runner de evidências.")
    if destination.stat().st_uid != os.getuid():
        raise SystemExit("O diretório de destino não pertence ao usuário atual.")
    if any(destination.iterdir()):
        raise SystemExit("O destino não está vazio; exportação recusada para evitar sobrescrita.")
    destination.chmod(0o700)


def write_private(path: Path, data: str) -> None:
    with path.open("x", encoding="utf-8") as stream:
        os.chmod(path, 0o600)
        stream.write(data)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--artifact-root", required=True, type=Path)
    parser.add_argument("--matrix", required=True, type=Path)
    parser.add_argument("--destination", required=True, type=Path)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--runner-exit-code", required=True, type=int)
    args = parser.parse_args()

    report_path = args.report.resolve()
    artifact_root = args.artifact_root.resolve()
    matrix_path = args.matrix.resolve(strict=True)
    destination = args.destination.resolve()
    repo_root = Path(__file__).resolve().parents[1]
    if not re.fullmatch(r"[A-Za-z0-9TzZ-]{8,80}", args.run_id):
        raise SystemExit("Run-id inválido.")
    safe_destination(destination)

    report_available = report_path.is_file()
    if report_available:
        try:
            report = json.loads(report_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            raise SystemExit(f"Relatório Playwright inválido: {error}") from error
    else:
        report = {"suites": []}

    rows = matrix_rows(matrix_path)
    reported_specs = collect_specs(report.get("suites", []))
    scenario_records: list[dict] = []
    captured_labels_by_scenario: dict[tuple[str, str], set[str]] = {}
    media_dir = destination / "midia"

    for spec in reported_specs:
        spec_name = normalize_spec(spec.get("file") or "")
        title = spec.get("title", "")
        exact_key = (spec_name, title)
        policy = SAFE_SCENARIOS.get(exact_key)
        for test in spec.get("tests", []):
            results = test.get("results", [])
            final_result = results[-1] if results else {}
            test_status = test.get("status", "unknown")
            result_status = final_result.get("status", "unknown")
            passed = test_status == "expected" and result_status == "passed"
            record = {
                "spec": spec_name,
                "cenario": title,
                "resultado_playwright": "APROVADO" if passed else (
                    "FALHOU" if test_status not in ("expected", "skipped") else "NAO_EXECUTADO"
                ),
                "tentativas": len(results),
                "allowlist_exata_spec_cenario": policy is not None,
                "artefatos": [],
                "ids_matriz_relacionados": [],
                "nota": policy["nota"] if policy else SENSITIVE_SPEC_REASONS.get(
                    spec_name, "Cenário fora da allowlist exata de captura; não foi exportada mídia."
                ),
            }

            if passed and policy:
                allowed_attachment_names = {
                    f"nexogov-evidence-{label}" for label in policy["labels"]
                }
                attachments = final_result.get("attachments", [])
                found_names: set[str] = set()
                for attachment in attachments:
                    name = attachment.get("name", "")
                    content_type = (attachment.get("contentType") or "").lower()
                    if name not in allowed_attachment_names or content_type != "image/png":
                        continue
                    source_bytes = attachment_bytes(attachment, artifact_root, report_path, repo_root)
                    if source_bytes is None:
                        continue
                    media_dir.mkdir(mode=0o700, exist_ok=True)
                    media_dir.chmod(0o700)
                    label = name.removeprefix("nexogov-evidence-")
                    filename = f"{slug(spec_name.removesuffix('.spec.ts'))}--{slug(title)}--{slug(label)}.png"
                    target = media_dir / filename
                    with target.open("xb") as stream:
                        os.chmod(target, 0o600)
                        stream.write(source_bytes)
                    source_hash = hashlib.sha256(source_bytes).hexdigest()
                    target_hash = sha256(target)
                    if source_hash != target_hash or target.read_bytes() != source_bytes:
                        target.unlink(missing_ok=True)
                        raise SystemExit(f"Hash/conteúdo divergente ao exportar {filename}.")
                    found_names.add(label)
                    record["artefatos"].append({
                        "tipo": "screenshot",
                        "arquivo": f"midia/{filename}",
                        "bytes": target.stat().st_size,
                        "sha256": target_hash,
                    })
                if found_names:
                    captured_labels_by_scenario[exact_key] = found_names
                missing_labels = set(policy["labels"]) - found_names
                if capture_is_complete(policy, found_names):
                    record["resultado_captura"] = "APROVADO_COM_SCREENSHOT"
                elif found_names:
                    record["resultado_captura"] = "APROVADO_COM_CAPTURA_PARCIAL"
                    record["nota"] += " Captura parcial; faltam os labels: " + ", ".join(sorted(missing_labels)) + "."
                else:
                    record["resultado_captura"] = "APROVADO_SEM_SCREENSHOT_EXPORTAVEL"
                    record["nota"] += " Nenhum anexo PNG da allowlist foi localizado dentro do estágio temporário."
            elif passed:
                record["resultado_captura"] = "APROVADO_SEM_CAPTURA_FORA_DA_ALLOWLIST"
            else:
                record["resultado_captura"] = "SEM_EVIDENCIA_VISUAL_DE_CENARIO_APROVADO"
            scenario_records.append(record)

    ids_by_scenario: dict[tuple[str, str], list[str]] = {}
    for row in rows:
        requirement_id = row.get("id", "")
        for scenario_key, policy in SAFE_SCENARIOS.items():
            captured_labels = captured_labels_by_scenario.get(scenario_key, set())
            if requirement_id in supported_ids(policy, captured_labels):
                if explicit_row_maps_to_scenario(row, scenario_key[0], scenario_key[1], repo_root):
                    ids_by_scenario.setdefault(scenario_key, []).append(requirement_id)

    for record in scenario_records:
        key = (record["spec"], record["cenario"])
        if record.get("resultado_captura") in ("APROVADO_COM_SCREENSHOT", "APROVADO_COM_CAPTURA_PARCIAL"):
            record["ids_matriz_relacionados"] = sorted(set(ids_by_scenario.get(key, [])))
            record["escopo_da_evidencia"] = (
                "Subfluxo visual de cenário Playwright aprovado. ID associado somente quando a matriz menciona "
                "explicitamente a spec/cadeia de comando verificável e o par spec/cenário está na allowlist. "
                "A captura não muda status nem comprova sozinha o critério integral ou a POC oficial."
            )

    ids_with_media = {
        requirement_id
        for record in scenario_records
        if record.get("resultado_captura") in ("APROVADO_COM_SCREENSHOT", "APROVADO_COM_CAPTURA_PARCIAL")
        for requirement_id in record.get("ids_matriz_relacionados", [])
    }
    requirements: list[dict] = []
    for row in rows:
        requirement_id = row.get("id", "")
        test_text = row.get("teste_automatizado", "")
        refs = sorted({normalize_spec(value) for value in SPEC_RE.findall(test_text)})
        linked = [
            record for record in scenario_records
            if requirement_id in record.get("ids_matriz_relacionados", [])
            and record.get("resultado_captura") in ("APROVADO_COM_SCREENSHOT", "APROVADO_COM_CAPTURA_PARCIAL")
        ]
        if requirement_id in ids_with_media and linked:
            visual_status = "E2E_APROVADO_COM_SCREENSHOT; associação explícita e unívoca"
            note = "Evidência visual auxiliar; não promove o status de aderência nem substitui testes/POC."
        elif refs and any(name in SENSITIVE_SPEC_REASONS for name in refs):
            visual_status = "SEM_MIDIA_POR_PRIVACIDADE"
            note = " ".join(sorted({SENSITIVE_SPEC_REASONS[name] for name in refs if name in SENSITIVE_SPEC_REASONS}))
        elif any(requirement_id in policy["ids"] for policy in SAFE_SCENARIOS.values()):
            visual_status = "SEM_CAPTURA_DE_CENARIO_APROVADO"
            mapped_policy = next(policy for policy in SAFE_SCENARIOS.values() if requirement_id in policy["ids"])
            missing_labels = sorted(required_labels_for_id(mapped_policy, requirement_id) - {
                label
                for key, policy in SAFE_SCENARIOS.items()
                if requirement_id in policy["ids"]
                for label in captured_labels_by_scenario.get(key, set())
            })
            note = "O cenário explicitamente permitido não produziu screenshot correspondente aprovado nesta execução."
            if missing_labels:
                note += " Labels necessários ausentes: " + ", ".join(missing_labels) + "."
        elif refs:
            visual_status = "REFERENCIA_E2E_SEM_MAPEAMENTO_VISUAL"
            note = "A matriz cita teste/spec, mas não há associação visual exata permitida para este ID nesta execução."
        else:
            visual_status = "SEM_CENARIO_E2E_MATRIZADO"
            note = "O campo teste_automatizado não referencia cenário Playwright; nenhuma mídia foi criada para este requisito."
        requirements.append({
            "id": requirement_id,
            "status_matriz": row.get("status", ""),
            "modulo": row.get("modulo", ""),
            "origem": row.get("origem_documento_pagina_secao_item", ""),
            "criterio_de_aceite": row.get("criterio_de_aceite", ""),
            "specs_referenciadas_na_matriz": refs,
            "status_evidencia_visual": visual_status,
            "nota": note,
            "cenarios_e_artefatos": [
                {"spec": item["spec"], "cenario": item["cenario"], "artefatos": item["artefatos"]}
                for item in linked
            ],
        })

    captured_count = sum(1 for item in requirements if item["status_evidencia_visual"].startswith("E2E_APROVADO"))
    validated_media_count = sum(
        1 for item in requirements
        if item["status_evidencia_visual"].startswith("E2E_APROVADO") and item["status_matriz"] == "VALIDADO"
    )
    media_files = [asset for record in scenario_records for asset in record["artefatos"]]
    manifest = {
        "tipo": "manifesto de evidência visual E2E",
        "execucao": args.run_id,
        "gerado_em_utc": datetime.now(timezone.utc).isoformat(),
        "resultado_runner": "APROVADO" if args.runner_exit_code == 0 else "FALHOU",
        "codigo_saida_runner": args.runner_exit_code,
        "relatorio_playwright_disponivel": report_available,
        "matriz": matrix_path.name,
        "matriz_sha256": sha256(matrix_path),
        "politica_midia": "Somente screenshots PNG por screenshot anexado explicitamente em teste aprovado; vídeo e PDF não exportados.",
        "totais": {
            "requisitos_na_matriz": len(requirements),
            "requisitos_com_screenshot_mapeado": captured_count,
            "requisitos_validado_com_screenshot_mapeado": validated_media_count,
            "requisitos_sem_cenario_e2e_matriciado": sum(1 for item in requirements if item["status_evidencia_visual"] == "SEM_CENARIO_E2E_MATRIZADO"),
            "requisitos_sem_midia_por_privacidade": sum(1 for item in requirements if item["status_evidencia_visual"] == "SEM_MIDIA_POR_PRIVACIDADE"),
            "cenarios_e2e_reportados": len(scenario_records),
            "screenshots_exportados": len(media_files),
        },
        "limites": [
            "IDs são associados apenas a pares spec/cenário explícitos na allowlist e referenciados pela matriz; a exceção de interação resolve uma cadeia de harness verificada até um título Playwright único.",
            "As capturas são subfluxos visuais de E2E aprovado, não comprovam automaticamente o critério integral, obrigações contratuais ou POC oficial.",
            "O status da matriz não é alterado por este exportador.",
            "Somente PNGs anexados pelo helper evidence-only são exportados. Vídeos estão desativados; PDFs não são exportados porque comprovantes podem incluir chave/QR.",
            "Quando há chave de acompanhamento preenchida, o helper muda o campo para type=password, verifica o atributo e passa o locator para a opção mask do Playwright. Isso comprova a configuração da captura, não o resultado dos pixels; cada PNG exportado que contém esse campo exige inspeção visual. Não use busca do bearer nos bytes PNG como prova de mascaramento.",
            "Mídias, nomes, manifesto e hash ficam sob Documentos com permissões restritas; o relatório Playwright bruto e o staging são removidos ao fim do runner.",
            "Se o runner parar antes de produzir JSON, o manifesto ainda lista todos os requisitos sem inventar cenários ou evidências.",
        ],
        "cenarios": scenario_records,
        "arquivos_exportados": media_files,
        "requisitos": requirements,
    }
    manifest_path = destination / "manifesto.json"
    write_private(manifest_path, json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")

    lines = [
        "# Evidências visuais E2E — NexoGov",
        "",
        f"- Execução: `{args.run_id}`",
        f"- Resultado do runner: **{manifest['resultado_runner']}**",
        f"- Relatório Playwright disponível: **{'sim' if report_available else 'não'}**",
        f"- Matriz verificada: `{matrix_path.name}` (SHA-256 `{manifest['matriz_sha256']}`)",
        f"- Requisitos com screenshot associado por referência explícita: **{captured_count}/{len(requirements)}**",
        f"- Desses, com status `VALIDADO` na matriz atual: **{validated_media_count}**",
        f"- Screenshots exportados: **{len(media_files)}**",
        f"- Requisitos sem cenário E2E matriciado: **{manifest['totais']['requisitos_sem_cenario_e2e_matriciado']}**",
        f"- Requisitos sem mídia por privacidade: **{manifest['totais']['requisitos_sem_midia_por_privacidade']}**",
        "",
        "> São screenshots de subfluxos Playwright aprovados. Não promovem status da matriz nem substituem a POC oficial.",
        "> Antes da captura, o campo do token é `type=password` e seu locator é incluído na máscara do Playwright; confirme visualmente os pixels de cada PNG exportado. O PDF de comprovante não é exportado.",
        "",
        "## Cenários executados",
        "",
    ]
    if scenario_records:
        for item in scenario_records:
            lines.extend([
                f"### {item['cenario']}",
                "",
                f"- Spec: `{item['spec']}`",
                f"- Resultado: **{item.get('resultado_captura', 'SEM_EVIDENCIA_VISUAL_DE_CENARIO_APROVADO')}**",
                f"- Nota: {item['nota']}",
            ])
            if item.get("ids_matriz_relacionados"):
                lines.append(f"- IDs relacionados: {', '.join(f'`{value}`' for value in item['ids_matriz_relacionados'])}")
            if item["artefatos"]:
                lines.append("- Screenshots:")
                lines.extend(
                    f"  - `{asset['arquivo']}` ({asset['bytes']} bytes, SHA-256 `{asset['sha256']}`)"
                    for asset in item["artefatos"]
                )
            lines.append("")
    lines.extend([
        "## Lacunas por requisito",
        "",
        "O manifesto JSON discrimina cada ID, status atual, origem, critério, referências E2E e motivo para ausência ou limite de captura. Linhas sem screenshot não receberam imagem substituta.",
        "",
    ])
    write_private(destination / "manifesto.md", "\n".join(lines))
    print(destination)
    print(json.dumps(manifest["totais"], ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
