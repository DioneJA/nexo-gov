import base64
import importlib.util
import json
import os
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


EXPORTER_PATH = Path(__file__).resolve().parents[1] / "export-e2e-evidence.py"
SPEC = importlib.util.spec_from_file_location("export_e2e_evidence", EXPORTER_PATH)
assert SPEC is not None and SPEC.loader is not None
EXPORTER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(EXPORTER)


def policy_for(spec_name: str) -> dict:
    return next(policy for (name, _), policy in EXPORTER.SAFE_SCENARIOS.items() if name == spec_name)


class ExportEvidencePolicyTests(unittest.TestCase):
    def test_helper_configures_bearer_password_and_passes_its_locator_to_mask(self):
        repo_root = EXPORTER_PATH.parents[1]
        helper = (repo_root / "frontend/e2e/evidence-fixtures.ts").read_text(encoding="utf-8")

        password_setup = re.search(
            r"if \(bearer\) \{\s*await receiptKey\.evaluate\(element => \{\s*"
            r"\(element as HTMLInputElement\)\.type = 'password'\s*\}\)\s*"
            r"await expect\(receiptKey\)\.toHaveAttribute\('type', 'password'\)\s*\}",
            helper,
            re.DOTALL,
        )
        self.assertIsNotNone(password_setup, "token preenchido deve virar password e ter o atributo verificado")
        self.assertIn("automaticBearerMasks.push(receiptKey)", helper)
        self.assertIn("const masks = [...additionalMasks, ...automaticBearerMasks]", helper)
        self.assertIn("focus.scrollIntoViewIfNeeded()", helper)
        self.assertIn("'.q-header, header'", helper)
        self.assertIn("window.scrollBy(0, adjustment)", helper)
        self.assertRegex(
            helper,
            r"page\.screenshot\(\{\s*fullPage: false,\s*mask: masks,",
            "o locator bearer deve ser passado como mask na captura de viewport",
        )
        self.assertNotIn(
            "screenshot.includes(Buffer.from(bearer",
            helper,
            "PNG comprimido não permite provar mascaramento visual por busca do bearer em bytes",
        )

    def test_policy_matches_capture_config_specs_labels_and_explicit_matrix_links(self):
        repo_root = EXPORTER_PATH.parents[1]
        config_text = (repo_root / "frontend/playwright.evidence.config.ts").read_text(encoding="utf-8")
        approved_block = config_text.split("const approvedScenarioNames = [", 1)[1].split("]", 1)[0]
        configured_titles = re.findall(r"'([^']+)'", approved_block)
        policy_titles = [title for _, title in EXPORTER.SAFE_SCENARIOS]
        self.assertEqual(len(configured_titles), len(set(configured_titles)), "scenario title duplicado no config")
        self.assertCountEqual(policy_titles, configured_titles)

        test_match = re.search(r"testMatch:\s*/(.*?)/,", config_text)
        self.assertIsNotNone(test_match, "testMatch da configuração de evidências ausente")

        matrix_path = repo_root / "docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv"
        rows_by_id = {row["id"]: row for row in EXPORTER.matrix_rows(matrix_path)}
        self.assertNotIn("ED91-0484", {rid for policy in EXPORTER.SAFE_SCENARIOS.values() for rid in policy["ids"]})

        for (spec_name, title), policy in EXPORTER.SAFE_SCENARIOS.items():
            with self.subTest(spec=spec_name, title=title):
                self.assertIn(spec_name.removesuffix(".spec.ts"), test_match.group(1))
                spec_path = repo_root / "frontend/e2e" / spec_name
                self.assertEqual(1, EXPORTER.test_title_count(spec_path, title), "título deve existir uma vez na spec")

                labels = EXPORTER.evidence_capture_labels(spec_path)
                self.assertEqual(len(labels), len(set(labels)), "label duplicado em chamadas do helper")
                self.assertEqual(policy["labels"], set(labels), "labels da política devem corresponder às chamadas explícitas")
                for label in policy["labels"]:
                    self.assertRegex(label, r"^[a-z0-9]+(?:-[a-z0-9]+)*$", f"label inválido: {label}")

                self.assertEqual(policy["ids"], set(policy["id_labels"]))
                for requirement_id, required_labels in policy["id_labels"].items():
                    self.assertTrue(required_labels, f"{requirement_id} exige ao menos um label")
                    self.assertLessEqual(required_labels, policy["labels"], f"labels inválidos em {requirement_id}")
                    self.assertIn(requirement_id, rows_by_id, f"ID inexistente na matriz: {requirement_id}")
                    self.assertTrue(
                        EXPORTER.explicit_row_maps_to_scenario(rows_by_id[requirement_id], spec_name, title, repo_root),
                        f"{requirement_id} não cita explicitamente a spec/cadeia verificável",
                    )

    def test_attachment_policy_requires_only_labels_appropriate_to_each_requirement(self):
        policy = policy_for("protocol-attachments.spec.ts")
        self.assertEqual({
            "TR91-0516": {"regra-anexo-persistida", "anexo-cidadao-enviado-listado"},
            "TR91-0019": {"anexo-setor-consultado-baixado"},
            "ED91-0170": {"anexo-cidadao-enviado-listado", "anexo-setor-consultado-baixado"},
            "TR91-0174": {"anexo-cidadao-enviado-listado", "anexo-setor-consultado-baixado"},
        }, policy["id_labels"])
        self.assertNotIn("ED91-0484", policy["ids"])
        self.assertEqual({"TR91-0516"}, EXPORTER.supported_ids(policy, {
            "regra-anexo-persistida", "anexo-cidadao-enviado-listado"
        }))

    def test_composite_attachment_and_deadline_requirement_gets_no_media_from_attachment_only_scenario(self):
        repo_root = EXPORTER_PATH.parents[1]
        spec_name, title = next(
            key for key in EXPORTER.SAFE_SCENARIOS if key[0] == "protocol-attachments.spec.ts"
        )
        labels = EXPORTER.SAFE_SCENARIOS[(spec_name, title)]["labels"]
        attachments = [{
            "name": f"nexogov-evidence-{label}",
            "contentType": "image/png",
            "body": base64.b64encode(EXPORTER.PNG_SIGNATURE + label.encode("ascii")).decode("ascii"),
        } for label in sorted(labels)]
        report = {
            "suites": [{
                "file": spec_name,
                "specs": [{
                    "title": title,
                    "file": spec_name,
                    "tests": [{"status": "expected", "results": [{"status": "passed", "attachments": attachments}]}],
                }],
            }],
        }

        with tempfile.TemporaryDirectory(prefix="nexogov-attachment-composite-") as temporary:
            root = Path(temporary)
            artifacts = root / "artifacts"
            destination = root / "export"
            artifacts.mkdir(mode=0o700)
            destination.mkdir(mode=0o700)
            report_path = artifacts / "report.json"
            report_path.write_text(json.dumps(report), encoding="utf-8")
            subprocess.run(
                [
                    sys.executable, str(EXPORTER_PATH),
                    "--report", str(report_path),
                    "--artifact-root", str(artifacts),
                    "--matrix", str(repo_root / "docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv"),
                    "--destination", str(destination),
                    "--run-id", "test-attachment-composite-20261108",
                    "--runner-exit-code", "0",
                ],
                capture_output=True,
                text=True,
                check=True,
            )
            manifest = json.loads((destination / "manifesto.json").read_text(encoding="utf-8"))

        attachment_scenario = manifest["cenarios"][0]
        self.assertEqual("APROVADO_COM_SCREENSHOT", attachment_scenario["resultado_captura"])
        self.assertNotIn("ED91-0484", attachment_scenario["ids_matriz_relacionados"])
        composite_row = next(row for row in manifest["requisitos"] if row["id"] == "ED91-0484")
        self.assertNotIn("E2E_APROVADO", composite_row["status_evidencia_visual"])
        self.assertEqual([], composite_row["cenarios_e_artefatos"])
        self.assertNotIn("ED91-0484", {
            requirement_id
            for scenario in manifest["cenarios"]
            for requirement_id in scenario["ids_matriz_relacionados"]
        })

    def test_no_attachment_or_deadline_scenario_means_no_composite_media(self):
        repo_root = EXPORTER_PATH.parents[1]
        with tempfile.TemporaryDirectory(prefix="nexogov-no-attachment-deadline-") as temporary:
            root = Path(temporary)
            artifacts = root / "artifacts"
            destination = root / "export"
            artifacts.mkdir(mode=0o700)
            destination.mkdir(mode=0o700)
            report_path = artifacts / "report.json"
            report_path.write_text(json.dumps({"suites": []}), encoding="utf-8")
            subprocess.run(
                [
                    sys.executable, str(EXPORTER_PATH),
                    "--report", str(report_path),
                    "--artifact-root", str(artifacts),
                    "--matrix", str(repo_root / "docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv"),
                    "--destination", str(destination),
                    "--run-id", "test-no-attachment-deadline-20261108",
                    "--runner-exit-code", "0",
                ],
                capture_output=True,
                text=True,
                check=True,
            )
            manifest = json.loads((destination / "manifesto.json").read_text(encoding="utf-8"))

        composite_row = next(row for row in manifest["requisitos"] if row["id"] == "ED91-0484")
        self.assertNotIn("E2E_APROVADO", composite_row["status_evidencia_visual"])
        self.assertEqual([], composite_row["cenarios_e_artefatos"])
        self.assertEqual(0, manifest["totais"]["screenshots_exportados"])

    def test_interaction_scenario_missing_reopen_capture_is_incomplete_and_maps_no_ids(self):
        policy = policy_for("protocol-interactions.spec.ts")
        found = {"protocolo-concluido"}

        self.assertFalse(EXPORTER.capture_is_complete(policy, found))
        self.assertEqual(set(), EXPORTER.supported_ids(policy, found))

    def test_subject_scenario_maps_each_requirement_only_to_its_required_label(self):
        policy = policy_for("protocol-subject-catalog.spec.ts")
        found = {"confirmacao-cidadao"}

        self.assertFalse(EXPORTER.capture_is_complete(policy, found))
        self.assertEqual({"TR91-0510"}, EXPORTER.supported_ids(policy, found))
        self.assertNotIn("TR91-0517", EXPORTER.supported_ids(policy, found))

    def test_subject_scenario_requires_both_labels_for_complete_scenario(self):
        policy = policy_for("protocol-subject-catalog.spec.ts")
        found = {"configuracao-persistida", "confirmacao-cidadao"}

        self.assertTrue(EXPORTER.capture_is_complete(policy, found))
        self.assertEqual({"TR91-0510", "TR91-0517"}, EXPORTER.supported_ids(policy, found))

    def test_read_status_scenario_requires_all_three_states_before_mapping(self):
        policy = policy_for("protocol-read-status.spec.ts")
        partial = {"leitura-nao-iniciada", "leitura-por-colega"}
        self.assertFalse(EXPORTER.capture_is_complete(policy, partial))
        self.assertEqual(set(), EXPORTER.supported_ids(policy, partial))
        complete = partial | {"leitura-pelo-servidor"}
        self.assertTrue(EXPORTER.capture_is_complete(policy, complete))
        self.assertEqual(policy["ids"], EXPORTER.supported_ids(policy, complete))

    def test_workflow_and_decision_scenario_requires_labels_per_requirement(self):
        policy = policy_for("process-workflow.spec.ts")
        partial = {
            "workflow-publicado-v1",
            "workflow-publicado-v2",
            "processo-snapshot-v1",
            "processo-avancado-para-etapa-final",
            "processo-listado-apos-reinicio",
            "detalhe-reaberto-da-listagem",
            "workflow-decisores-configurados",
        }
        self.assertFalse(EXPORTER.capture_is_complete(policy, partial))
        self.assertEqual({"ED91-0596", "TR91-0691"}, EXPORTER.supported_ids(policy, partial))
        complete = partial | {
            "workflow-publicado-v2",
            "processo-decisao-registrada",
            "processo-decisao-persistida",
            "processo-avancado-para-etapa-final",
            "processo-listado-apos-reinicio",
            "detalhe-reaberto-da-listagem",
        }
        self.assertTrue(EXPORTER.capture_is_complete(policy, complete))
        self.assertEqual({"ED91-0510", "ED91-0596", "TR91-0560", "TR91-0691"},
                         EXPORTER.supported_ids(policy, complete))

    def test_unexpected_label_does_not_complete_an_allowlisted_scenario(self):
        policy = policy_for("protocol-interactions.spec.ts")

        self.assertFalse(EXPORTER.capture_is_complete(policy, {"protocolo-concluido", "inventado"}))

    def test_inline_png_requires_report_inside_artifact_root_and_valid_signature(self):
        with tempfile.TemporaryDirectory(prefix="nexogov-inline-boundary-") as temporary:
            root = Path(temporary)
            artifact_root = root / "artifacts"
            artifact_root.mkdir()
            report_path = artifact_root / "report.json"
            report_path.write_text("{}", encoding="utf-8")
            valid_body = base64.b64encode(EXPORTER.PNG_SIGNATURE + b"image").decode("ascii")
            attachment = {"body": valid_body}

            self.assertEqual(
                EXPORTER.PNG_SIGNATURE + b"image",
                EXPORTER.attachment_bytes(attachment, artifact_root, report_path, root),
            )
            self.assertIsNone(EXPORTER.attachment_bytes(
                {"body": "not-base64!!!"}, artifact_root, report_path, root
            ))
            self.assertIsNone(EXPORTER.attachment_bytes(
                {"body": base64.b64encode(b"not-png").decode("ascii")}, artifact_root, report_path, root
            ))
            self.assertIsNone(EXPORTER.attachment_bytes(
                {"body": valid_body}, artifact_root, root / "external-report.json", root
            ))

    def test_file_attachment_path_outside_artifact_root_is_rejected(self):
        with tempfile.TemporaryDirectory(prefix="nexogov-path-boundary-") as temporary:
            root = Path(temporary)
            artifact_root = root / "artifacts"
            artifact_root.mkdir()
            report_path = artifact_root / "report.json"
            report_path.write_text("{}", encoding="utf-8")
            outside_png = root / "outside.png"
            outside_png.write_bytes(EXPORTER.PNG_SIGNATURE + b"image")

            self.assertIsNone(EXPORTER.attachment_bytes(
                {"path": str(outside_png)}, artifact_root, report_path, root
            ))

    def test_partial_real_export_maps_only_requirement_with_present_label(self):
        repo_root = EXPORTER_PATH.parents[1]
        title = "administrador configura o prazo automático no comprovante real do protocolo"
        png_body = base64.b64encode(EXPORTER.PNG_SIGNATURE + b"adversarial-test").decode("ascii")
        report = {
            "suites": [{
                "file": "protocol-subject-catalog.spec.ts",
                "specs": [{
                    "title": title,
                    "file": "protocol-subject-catalog.spec.ts",
                    "tests": [{
                        "status": "expected",
                        "results": [{
                            "status": "passed",
                            "attachments": [{
                                "name": "nexogov-evidence-confirmacao-cidadao",
                                "contentType": "image/png",
                                "body": png_body,
                            }],
                        }],
                    }],
                }],
            }],
        }

        with tempfile.TemporaryDirectory(prefix="nexogov-export-partial-") as temporary:
            root = Path(temporary)
            artifacts = root / "artifacts"
            destination = root / "export"
            artifacts.mkdir(mode=0o700)
            destination.mkdir(mode=0o700)
            report_path = artifacts / "report.json"
            report_path.write_text(json.dumps(report), encoding="utf-8")
            completed = subprocess.run(
                [
                    sys.executable,
                    str(EXPORTER_PATH),
                    "--report", str(report_path),
                    "--artifact-root", str(artifacts),
                    "--matrix", str(repo_root / "docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv"),
                    "--destination", str(destination),
                    "--run-id", "test-partial-20261108",
                    "--runner-exit-code", "0",
                ],
                capture_output=True,
                text=True,
                check=True,
            )
            manifest = json.loads((destination / "manifesto.json").read_text(encoding="utf-8"))
            image = manifest["cenarios"][0]["artefatos"][0]
            image_path = destination / image["arquivo"]
            self.assertEqual(0o600, os.stat(image_path).st_mode & 0o777)
            self.assertTrue(image_path.read_bytes().startswith(EXPORTER.PNG_SIGNATURE))
            self.assertEqual(image["sha256"], EXPORTER.sha256(image_path))

        scenario = manifest["cenarios"][0]
        self.assertEqual("APROVADO_COM_CAPTURA_PARCIAL", scenario["resultado_captura"])
        self.assertEqual(["TR91-0510"], scenario["ids_matriz_relacionados"])
        self.assertEqual(1, manifest["totais"]["screenshots_exportados"])
        requirements = {row["id"]: row for row in manifest["requisitos"]}
        self.assertTrue(requirements["TR91-0510"]["status_evidencia_visual"].startswith("E2E_APROVADO"))
        self.assertEqual("SEM_CAPTURA_DE_CENARIO_APROVADO", requirements["TR91-0517"]["status_evidencia_visual"])
        self.assertIn("configuracao-persistida", requirements["TR91-0517"]["nota"])


if __name__ == "__main__":
    unittest.main()
