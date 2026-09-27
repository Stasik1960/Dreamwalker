"""Negative release-readiness claims, using isolated file-backed fixtures."""
import copy
import hashlib
import json
import tempfile
import unittest
import struct
import zlib
from pathlib import Path

from release_gates import GATES, IDENTITIES, METRICS, validate


class ReleaseGatesTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.commit = "a" * 40

    def artifact(self, name, content):
        data = json.dumps(content, sort_keys=True).encode() if isinstance(content, dict) else content
        path = self.root / name
        path.write_bytes(data)
        return {"path": name, "sha256": hashlib.sha256(data).hexdigest()}

    def fixture(self):
        source = self.artifact("source.txt", b"source")
        resources = self.artifact("resource.txt", b"resource")
        source_manifest = self.artifact("source.json", {"sourceCommit": self.commit, "files": [source]})
        resource_manifest = self.artifact("resources.json", {"sourceCommit": self.commit, "files": [resources]})
        world = self.artifact("input.zip", b"test world")
        identities = dict(sourceCommit=self.commit, sourceSnapshotSha256=source_manifest["sha256"],
                          resourceSha256=resource_manifest["sha256"], inputSha256=world["sha256"])
        command = {"command": ["test", "synthetic-fixture"], "exitCode": 0}
        command_ref = self.artifact("command.json", command)
        commands = [{"argv": command["command"], "exitCode": 0, "record": command_ref}]
        def runs(roles):
            return [{"role": role, "runId": role, "commands": [{"argv": command["command"], "exitCode": 0,
                     "record": self.artifact("command-" + role + ".json", command)}]} for role in roles]
        log = self.artifact("run.log", b"synthetic fixture, no real runtime claim")
        state = self.artifact("world.json", {"files": [{"path": "region/r.0.0.mca", "sha256": "c" * 64}]})
        second_state = self.artifact("second-world.json", json.loads((self.root / "world.json").read_bytes()))
        independent = self.artifact("independent.json", {
            **identities, "kind": "independent-full-city-verification", "result": "PASS",
            "metrics": dict.fromkeys(METRICS, 0), "nonterrainByteIdentical": True,
            "commands": commands, "firstWorldManifest": state, "secondWorldManifest": second_state,
            "runs": runs(("firstPass", "secondPass", "independentVerification"))})
        restart = self.artifact("restart.json", {
            **identities, "kind": "dedicated-restart-qa",
            "checks": dict.fromkeys(("freshStart", "save", "restart", "persistentState"), "PASS"),
            "freshStartLog": log, "restartLog": self.artifact("restart.log", b"restart fixture"),
            "savedWorldManifest": state, "restartedWorldManifest": second_state,
            "runs": runs(("freshStart", "restart", "verifyPersistentState"))})
        def chunk(kind, data):
            return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
        capture = self.artifact("capture.png", b"\x89PNG\r\n\x1a\n" +
                                chunk(b"IHDR", struct.pack(">IIBBBBB", 2, 2, 8, 2, 0, 0, 0)) +
                                chunk(b"IDAT", zlib.compress(b"\0" * 14)) + chunk(b"IEND", b""))
        client = self.artifact("client.json", {
            **identities, "kind": "interactive-client-qa", "interactive": True,
            "checks": dict.fromkeys(("freshStart", "resourceReload", "restart", "familyInteractions", "chunkBoundaries"), "PASS"),
            "sessionLog": log, "captures": [capture],
            "runs": runs(("freshStart", "resourceReload", "restart", "interactions")),
            "interactions": {family: dict.fromkeys(("place", "break", "use"), "PASS") for family in ("doors", "windows", "stairs", "seats", "attachments")}})
        evidence = {**identities, "artifacts": [log], "commands": commands}
        gates = {name: {"status": "PASS", "scope": "full-city", "evidence": copy.deepcopy(evidence)} for name in GATES}
        gates["TEST_SCOPE_PASS"]["scope"] = "test-kit"
        gates["FULL_CITY_PASS"]["evidence"]["independentReport"] = independent
        gates["DEDICATED_RESTART_PASS"]["evidence"]["runtimeRecord"] = restart
        gates["CLIENT_VISUAL_PASS"]["evidence"]["runtimeRecord"] = client
        gates["TEST_SCOPE_PASS"]["evidence"]["report"] = self.artifact("scope.json", {
            **identities, "kind": "test-scope-report", "result": "PASS", "included": ["synthetic"], "excluded": ["real runtime"]})
        gates["STATIC_PASS"]["evidence"]["report"] = self.artifact("static.json", {
            **identities, "kind": "static-checks-report", "result": "PASS", "checks": dict.fromkeys(("compile", "check", "build", "versions", "package"), "PASS")})
        gates["GAMETEST_PASS"]["evidence"]["report"] = self.artifact("gametest.json", {
            **identities, "kind": "gametest-report", "result": "PASS", "tests": 1,
            "junit": self.artifact("gametest.xml", b'<testsuite><testcase name="synthetic"/></testsuite>'),
            "log": self.artifact("gametest.log", b'1 GAME TESTS COMPLETE\nAll 1 required tests passed')})
        return {**identities, "schemaVersion": 1, "scope": "full-city", "provenance": {
            "sourceSnapshot": source_manifest, "resourceManifest": resource_manifest, "inputWorld": world},
            "gates": gates, "knownReleaseBlockers": [], "unresolvedDiscrepancies": []}

    def test_complete_evidence_and_explicit_ready(self):
        self.assertEqual(validate(self.fixture(), self.root), {"schemaValid": True, "releaseReady": True, "errors": []})

    def test_blocked_document_is_valid_but_never_ready(self):
        document = self.fixture()
        for name in ("FULL_CITY_PASS", "RELEASE_READY"):
            document["gates"][name] = {"status": "BLOCKED", "scope": "full-city", "evidence": {}, "plannedCommands": []}
        document["knownReleaseBlockers"] = [{"scope": "full-city", "reason": "unproven ownership"}]
        result = validate(document, self.root)
        self.assertTrue(result["schemaValid"], result)
        self.assertFalse(result["releaseReady"])

    def test_declared_blocked_and_failed_test_scope_cannot_be_ready(self):
        for name in ("TEST_SCOPE_PASS", "RELEASE_READY"):
            with self.subTest(name=name):
                document = self.fixture()
                document["gates"][name]["status"] = "BLOCKED"
                self.assertFalse(validate(document, self.root)["releaseReady"])

    def test_artifact_root_and_actual_hashes_are_mandatory(self):
        document = self.fixture()
        self.assertFalse(validate(document)["schemaValid"])
        (self.root / "source.txt").write_bytes(b"changed after verification")
        self.assertFalse(validate(document, self.root)["schemaValid"])
        document = self.fixture()
        (self.root / "run.log").unlink()
        self.assertFalse(validate(document, self.root)["schemaValid"])

    def test_provenance_cannot_be_arbitrary_matching_strings(self):
        for key in IDENTITIES:
            with self.subTest(identity=key):
                document = self.fixture()
                document[key] = "b" * (40 if key == "sourceCommit" else 64)
                for gate in document["gates"].values():
                    gate["evidence"][key] = document[key]
                self.assertFalse(validate(document, self.root)["schemaValid"])

    def test_malformed_documents_fail_closed_without_exceptions(self):
        self.assertFalse(validate([], self.root)["schemaValid"])
        for bad in ([], None, True, "PASS"):
            document = self.fixture()
            document["gates"]["FULL_CITY_PASS"] = bad
            self.assertFalse(validate(document, self.root)["schemaValid"])

    def test_boolean_exit_code_and_empty_command_are_invalid(self):
        for value in (False, "0", None):
            document = self.fixture()
            document["gates"]["STATIC_PASS"]["evidence"]["commands"][0]["exitCode"] = value
            self.assertFalse(validate(document, self.root)["schemaValid"])
        document = self.fixture()
        document["gates"]["STATIC_PASS"]["evidence"]["commands"][0]["argv"] = []
        self.assertFalse(validate(document, self.root)["schemaValid"])

    def test_full_city_requires_independent_exact_zero_metrics(self):
        for value in (True, False, 1, None):
            document = self.fixture()
            report = json.loads((self.root / "independent.json").read_bytes())
            report["metrics"]["fragmented"] = value
            document["gates"]["FULL_CITY_PASS"]["evidence"]["independentReport"] = self.artifact("independent.json", report)
            self.assertFalse(validate(document, self.root)["schemaValid"])

    def test_runtime_claims_cannot_be_truthy_placeholders(self):
        for name in ("DEDICATED_RESTART_PASS", "CLIENT_VISUAL_PASS"):
            document = self.fixture()
            document["gates"][name]["evidence"]["runtimeRecord"] = True
            self.assertFalse(validate(document, self.root)["schemaValid"])

    def test_malformed_runtime_checks_fail_closed(self):
        for bad in (None, [], True):
            document = self.fixture()
            record = json.loads((self.root / "restart.json").read_bytes())
            record["checks"] = bad
            document["gates"]["DEDICATED_RESTART_PASS"]["evidence"]["runtimeRecord"] = self.artifact("restart.json", record)
            self.assertFalse(validate(document, self.root)["schemaValid"])

    def test_scopes_and_unresolved_discrepancies_block_release(self):
        document = self.fixture()
        document["gates"]["RELEASE_READY"]["scope"] = "test-kit"
        self.assertFalse(validate(document, self.root)["schemaValid"])
        document = self.fixture()
        document["knownReleaseBlockers"] = ["unknown scope"]
        self.assertFalse(validate(document, self.root)["schemaValid"])
        document = self.fixture()
        document["unresolvedDiscrepancies"] = ["JUnit count differs from log"]
        self.assertFalse(validate(document, self.root)["releaseReady"])

    def test_command_record_cannot_contradict_claimed_success(self):
        document = self.fixture()
        evidence = document["gates"]["STATIC_PASS"]["evidence"]
        evidence["commands"][0]["record"] = self.artifact("failed-command.json", {"command": ["test", "synthetic-fixture"], "exitCode": 1})
        self.assertFalse(validate(document, self.root)["schemaValid"])

    def test_single_pass_or_single_manifest_is_not_second_pass_evidence(self):
        for field, replacement in (("runs", []), ("secondWorldManifest", "same")):
            document = self.fixture()
            report = json.loads((self.root / "independent.json").read_bytes())
            report[field] = report["firstWorldManifest"] if replacement == "same" else replacement
            document["gates"]["FULL_CITY_PASS"]["evidence"]["independentReport"] = self.artifact("independent.json", report)
            self.assertFalse(validate(document, self.root)["schemaValid"])

    def test_reused_restart_log_and_invalid_capture_are_rejected(self):
        document = self.fixture()
        record = json.loads((self.root / "restart.json").read_bytes())
        record["restartLog"] = record["freshStartLog"]
        document["gates"]["DEDICATED_RESTART_PASS"]["evidence"]["runtimeRecord"] = self.artifact("restart.json", record)
        self.assertFalse(validate(document, self.root)["schemaValid"])
        document = self.fixture()
        record = json.loads((self.root / "client.json").read_bytes())
        record["captures"] = [self.artifact("fake.png", b"\x89PNG\r\n\x1a\nsynthetic")]
        document["gates"]["CLIENT_VISUAL_PASS"]["evidence"]["runtimeRecord"] = self.artifact("client.json", record)
        self.assertFalse(validate(document, self.root)["schemaValid"])

    def test_gametest_log_cannot_replace_junit_or_report(self):
        document = self.fixture()
        document["gates"]["GAMETEST_PASS"]["evidence"].pop("report")
        self.assertFalse(validate(document, self.root)["schemaValid"])

    def test_malformed_world_rows_and_string_scopes_are_rejected(self):
        document = self.fixture()
        report = json.loads((self.root / "independent.json").read_bytes())
        report["firstWorldManifest"] = self.artifact("world.json", {"files": [True]})
        report["secondWorldManifest"] = self.artifact("second-world.json", {"files": [True]})
        document["gates"]["FULL_CITY_PASS"]["evidence"]["independentReport"] = self.artifact("independent.json", report)
        self.assertFalse(validate(document, self.root)["schemaValid"])
        document = self.fixture()
        report = json.loads((self.root / "scope.json").read_bytes())
        report["included"] = "synthetic"
        document["gates"]["TEST_SCOPE_PASS"]["evidence"]["report"] = self.artifact("scope.json", report)
        self.assertFalse(validate(document, self.root)["schemaValid"])
        document = self.fixture()
        report = json.loads((self.root / "gametest.json").read_bytes())
        report["junit"] = self.artifact("gametest.xml", b'<testsuite><testcase name="bad"><failure/></testcase></testsuite>')
        document["gates"]["GAMETEST_PASS"]["evidence"]["report"] = self.artifact("gametest.json", report)
        self.assertFalse(validate(document, self.root)["schemaValid"])


if __name__ == "__main__":
    unittest.main()
