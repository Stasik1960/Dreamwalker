"""Validate scoped, file-backed release evidence; validation is not readiness."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import xml.etree.ElementTree as ET
import zlib
from pathlib import Path, PureWindowsPath

GATES = ("TEST_SCOPE_PASS", "SUBSET_PASS", "COVERAGE_COMPLETENESS_PASS", "STATIC_PASS", "GAMETEST_PASS",
         "DEDICATED_RESTART_PASS", "CLIENT_VISUAL_PASS", "FULL_CITY_PASS", "RELEASE_READY")
STATUSES = {"PASS", "FAIL", "NOT_RUN", "BLOCKED"}
SCOPES = {"test-kit", "full-city"}
IDENTITIES = ("sourceCommit", "sourceSnapshotSha256", "resourceSha256", "inputSha256")
METRICS = ("fragmented", "protectedLoss", "orphans", "foreignOverwrites",
           "unknown", "rejected", "secondChanges")


def is_hex(value, length=64):
    return isinstance(value, str) and re.fullmatch("[0-9a-f]{" + str(length) + "}", value) is not None


def valid_capture(path):
    """Decode bounded, non-interlaced 8-bit RGB/RGBA PNG screenshots without extra dependencies."""
    try:
        data = path.read_bytes()
        if not data.startswith(b"\x89PNG\r\n\x1a\n"):
            return False
        offset, dimensions, pixels = 8, None, bytearray()
        while offset + 12 <= len(data):
            size = struct.unpack_from(">I", data, offset)[0]
            kind = data[offset + 4:offset + 8]
            payload = data[offset + 8:offset + 8 + size]
            if len(payload) != size or offset + size + 12 > len(data):
                return False
            crc = struct.unpack_from(">I", data, offset + 8 + size)[0]
            if zlib.crc32(kind + payload) & 0xffffffff != crc:
                return False
            offset += size + 12
            if kind == b"IHDR":
                if dimensions is not None or size != 13:
                    return False
                width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", payload)
                if not (width > 0 and height > 0 and width * height <= 16000000 and depth == 8 and color in (2, 6)
                        and compression == filtering == interlace == 0):
                    return False
                dimensions = width, height, 3 if color == 2 else 4
            elif kind == b"IDAT":
                if dimensions is None:
                    return False
                pixels.extend(payload)
            elif kind == b"IEND":
                if dimensions is None or size != 0 or offset != len(data):
                    return False
                width, height, channels = dimensions
                stride = width * channels + 1
                decoder = zlib.decompressobj()
                decoded = decoder.decompress(pixels, stride * height + 1)
                return decoder.eof and not decoder.unused_data and len(decoded) == stride * height and all(
                    decoded[row * stride] <= 4 for row in range(height))
        return False
    except (OSError, ValueError, struct.error, zlib.error):
        return False


def validate(document, root=None):
    errors, verified, json_bytes = [], {}, {}
    if not isinstance(document, dict):
        return {"schemaValid": False, "releaseReady": False, "errors": ["document must be an object"]}
    root = Path(root).resolve() if root is not None else None

    def error(message):
        errors.append(message)

    def artifact(row, label):
        if not isinstance(row, dict) or not isinstance(row.get("path"), str) or not row["path"] or not is_hex(row.get("sha256")):
            error(label + ": expected path and SHA-256")
            return None
        relative = Path(row["path"])
        windows = PureWindowsPath(row["path"])
        if relative.is_absolute() or windows.drive or ".." in relative.parts or ".." in windows.parts:
            error(label + ": unsafe artifact path")
            return None
        if root is None:
            error(label + ": evidence root is required")
            return None
        path = (root / relative).resolve()
        if not path.is_relative_to(root) or not path.is_file():
            error(label + ": missing/outside evidence root")
            return None
        if path not in verified:
            try:
                if path.suffix == ".json":
                    json_bytes[path] = path.read_bytes()
                    verified[path] = hashlib.sha256(json_bytes[path]).hexdigest()
                else:
                    with path.open("rb") as stream:
                        verified[path] = hashlib.file_digest(stream, "sha256").hexdigest()
            except OSError:
                error(label + ": unreadable artifact")
                return None
        if verified[path] != row["sha256"]:
            error(label + ": artifact hash mismatch")
            return None
        return path

    def json_artifact(row, label):
        path = artifact(row, label)
        if path is None:
            return None
        try:
            data = json_bytes.get(path)
            if data is None:
                data = path.read_bytes()
                if hashlib.sha256(data).hexdigest() != row["sha256"]:
                    error(label + ": artifact changed while reading")
                    return None
            value = json.loads(data)
        except (OSError, UnicodeError, ValueError):
            error(label + ": invalid JSON evidence")
            return None
        if not isinstance(value, dict):
            error(label + ": JSON evidence must be an object")
            return None
        return value

    def identities(value, label):
        if not isinstance(value, dict) or any(value.get(key) != document.get(key) for key in IDENTITIES):
            error(label + ": stale or missing provenance")

    def commands(rows, label):
        if not isinstance(rows, list) or not rows:
            error(label + ": no completed command")
            return
        for row in rows:
            if not isinstance(row, dict):
                error(label + ": malformed command")
                continue
            argv = row.get("argv")
            if not isinstance(argv, list) or not argv or not all(isinstance(value, str) and value for value in argv):
                error(label + ": argv must be a nonempty string list")
            if type(row.get("exitCode")) is not int or row["exitCode"] != 0:
                error(label + ": command did not succeed")
            record = json_artifact(row.get("record"), label + " command record")
            if record is not None and (record.get("command", record.get("argv")) != argv or
                                       type(record.get("exitCode")) is not int or record["exitCode"] != row.get("exitCode")):
                error(label + ": command disagrees with saved record")

    def distinct_runs(record, roles, label):
        runs = record.get("runs")
        if not isinstance(runs, list) or len(runs) != len(roles) or not all(isinstance(row, dict) for row in runs):
            error(label + ": separate runtime/pass records are required")
            return
        if [run.get("role") for run in runs] != list(roles):
            error(label + ": wrong run order/roles")
        ids = [run.get("runId") for run in runs]
        if not all(isinstance(value, str) and value for value in ids) or len(set(map(str, ids))) != len(roles):
            error(label + ": run IDs must be distinct")
        records = []
        for run in runs:
            commands(run.get("commands"), label + " " + str(run.get("role")))
            for row in run.get("commands", []) if isinstance(run.get("commands"), list) else []:
                if isinstance(row, dict) and isinstance(row.get("record"), dict):
                    records.append(row["record"].get("path"))
        if len(records) != len(set(map(str, records))):
            error(label + ": reused command records")

    def paired_manifests(record, first_key, second_key, label):
        first, second = record.get(first_key), record.get(second_key)
        first_data = json_artifact(first, label + " first manifest")
        second_data = json_artifact(second, label + " second manifest")
        if not isinstance(first, dict) or not isinstance(second, dict):
            return
        if first.get("path") == second.get("path") or first.get("sha256") != second.get("sha256"):
            error(label + ": separate, byte-identical manifests required")
        for data in (first_data, second_data):
            if data is not None and (not isinstance(data.get("files"), list) or not data["files"]):
                error(label + ": empty world/state manifest")
            elif data is not None:
                paths = set()
                for row in data["files"]:
                    if not isinstance(row, dict) or not isinstance(row.get("path"), str) or not row["path"] or not is_hex(row.get("sha256")):
                        error(label + ": malformed world/state entry")
                        continue
                    path = row["path"]
                    if Path(path).is_absolute() or PureWindowsPath(path).drive or ".." in Path(path).parts or ".." in PureWindowsPath(path).parts or path in paths:
                        error(label + ": unsafe/duplicate world/state path")
                    paths.add(path)

    if document.get("schemaVersion") != 1 or document.get("scope") != "full-city":
        error("schemaVersion/scope must be 1/full-city")
    for key in IDENTITIES:
        if not is_hex(document.get(key), 40 if key == "sourceCommit" else 64):
            error("invalid " + key)
    provenance = document.get("provenance", {})
    if not isinstance(provenance, dict):
        provenance = {}
        error("missing provenance records")
    for role, identity in (("sourceSnapshot", "sourceSnapshotSha256"),
                           ("resourceManifest", "resourceSha256"),
                           ("inputWorld", "inputSha256")):
        row = provenance.get(role)
        path = artifact(row, "provenance/" + role)
        if isinstance(row, dict) and row.get("sha256") != document.get(identity):
            error(role + ": identity does not match artifact")
        if path is not None and role != "inputWorld":
            manifest = json_artifact(row, role)
            if manifest is None:
                continue
            if manifest.get("sourceCommit") != document.get("sourceCommit"):
                error(role + ": source commit mismatch")
            entries = manifest.get("files")
            if not isinstance(entries, list) or not entries:
                error(role + ": empty source manifest")
            else:
                for entry in entries:
                    artifact(entry, role + " entry")

    raw_gates = document.get("gates")
    raw_gates = raw_gates if isinstance(raw_gates, dict) else {}
    if set(raw_gates) != set(GATES):
        error("exact named gates are required")
    gates = {}
    for name in GATES:
        gate = raw_gates.get(name)
        if not isinstance(gate, dict):
            error(name + ": gate must be an object")
            gate = {}
        gates[name] = gate
        if gate.get("status") not in STATUSES or gate.get("scope") not in SCOPES:
            error(name + ": invalid status/scope")
        if name != "TEST_SCOPE_PASS" and gate.get("scope") != "full-city":
            error(name + ": production gate requires full-city scope")
        evidence = gate.get("evidence", {})
        if not isinstance(evidence, dict):
            error(name + ": malformed evidence")
            continue
        if gate.get("status") == "PASS":
            identities(evidence, name)
            rows = evidence.get("artifacts")
            if not isinstance(rows, list) or not rows:
                error(name + ": missing artifacts")
            else:
                for row in rows:
                    artifact(row, name)
            commands(evidence.get("commands"), name)
        elif not isinstance(gate.get("plannedCommands", []), list):
            error(name + ": malformed plannedCommands")

    for name, kind in (("TEST_SCOPE_PASS", "test-scope-report"),
                       ("STATIC_PASS", "static-checks-report"),
                       ("GAMETEST_PASS", "gametest-report")):
        gate = gates[name]
        if gate.get("status") != "PASS" or not isinstance(gate.get("evidence"), dict):
            continue
        report = json_artifact(gate["evidence"].get("report"), name + " report")
        if report is None:
            continue
        identities(report, name)
        if report.get("kind") != kind or report.get("result") != "PASS":
            error(name + ": wrong report kind/result")
        if name == "TEST_SCOPE_PASS":
            lists = (report.get("included"), report.get("excluded"))
            if any(not isinstance(values, list) or not values or not all(isinstance(value, str) and value for value in values) for values in lists):
                error(name + ": explicit inclusions/exclusions required")
            elif set(lists[0]) & set(lists[1]):
                error(name + ": overlapping inclusions/exclusions")
        elif name == "STATIC_PASS":
            checks = report.get("checks")
            if not isinstance(checks, dict) or any(checks.get(key) != "PASS" for key in ("compile", "check", "build", "versions", "package")):
                error(name + ": missing static/package checks")
        else:
            xml_path = artifact(report.get("junit"), "GameTest XML")
            log_path = artifact(report.get("log"), "GameTest log")
            try:
                cases = list(ET.parse(xml_path).getroot().iter("testcase")) if xml_path else []
                count = len(cases)
                if type(report.get("tests")) is not int or count <= 0 or count != report["tests"] or any(
                        list(case.iter("failure")) or list(case.iter("error")) or list(case.iter("skipped")) for case in cases):
                    error("GameTest XML count/outcome mismatch")
                log = log_path.read_text(encoding="utf-8", errors="replace") if log_path else ""
                if f"All {count} required tests passed" not in log or f"{count} GAME TESTS COMPLETE" not in log:
                    error("GameTest log disagrees with XML")
            except (OSError, ET.ParseError):
                error("unreadable GameTest XML/log")

    coverage_gate = gates["COVERAGE_COMPLETENESS_PASS"]
    if coverage_gate.get("status") == "PASS" and isinstance(coverage_gate.get("evidence"), dict):
        coverage = json_artifact(coverage_gate["evidence"].get("sourceCoverage"), "source coverage report")
        if coverage is not None:
            if coverage.get("candidateScopeComplete") is not True:
                error("source coverage report: candidate scope is incomplete")
            if coverage.get("coverageCompleteness") != "PASS":
                error("source coverage report: coverage completeness is not PASS")
            counts = coverage.get("counts")
            if not isinstance(counts, dict) or type(counts.get("candidates")) is not int or counts["candidates"] <= 0:
                error("source coverage report: candidates must be nonempty")
            else:
                for key in ("unresolvedKnown", "unknown", "genuinelyUnknown"):
                    if type(counts.get(key, 0)) is not int or counts.get(key, 0) != 0:
                        error("source coverage report: " + key + " must be zero")
            helpers = coverage.get("helperBindingErrors")
            if not isinstance(helpers, list) or helpers:
                error("source coverage report: helper binding errors must be empty")
            if "helpererrors" in coverage and (type(coverage["helpererrors"]) is not int or coverage["helpererrors"] != 0):
                error("source coverage report: helpererrors must be zero")
            if any(key in coverage for key in ("ledger", "writerLedger", "successLedger")):
                error("source coverage report: writer ledger is not coverage evidence")

    full = gates["FULL_CITY_PASS"].get("evidence", {})
    if gates["FULL_CITY_PASS"].get("status") == "PASS" and isinstance(full, dict):
        independent = json_artifact(full.get("independentReport"), "independent full-city report")
        if independent is not None:
            identities(independent, "independent full-city report")
            if independent.get("kind") != "independent-full-city-verification" or independent.get("result") != "PASS":
                error("independent report has wrong kind/result")
            metrics = independent.get("metrics")
            if not isinstance(metrics, dict) or any(type(metrics.get(key)) is not int or metrics[key] != 0 for key in METRICS):
                error("full-city metrics are missing/nonzero")
            if independent.get("nonterrainByteIdentical") is not True:
                error("nonterrain files differ or lack proof")
            commands(independent.get("commands"), "independent full-city report")
            distinct_runs(independent, ("firstPass", "secondPass", "independentVerification"), "full-city")
            paired_manifests(independent, "firstWorldManifest", "secondWorldManifest", "full-city")

    for name, kind, checks, logs in (
        ("DEDICATED_RESTART_PASS", "dedicated-restart-qa",
         ("freshStart", "save", "restart", "persistentState"), ("freshStartLog", "restartLog", "savedWorldManifest")),
        ("CLIENT_VISUAL_PASS", "interactive-client-qa",
         ("freshStart", "resourceReload", "restart", "familyInteractions", "chunkBoundaries"), ("sessionLog",))):
        gate = gates[name]
        if gate.get("status") != "PASS" or not isinstance(gate.get("evidence"), dict):
            continue
        record = json_artifact(gate["evidence"].get("runtimeRecord"), name + " runtime record")
        if record is None:
            continue
        identities(record, name)
        recorded_checks = record.get("checks")
        if record.get("kind") != kind or not isinstance(recorded_checks, dict) or any(recorded_checks.get(check) != "PASS" for check in checks):
            error(name + ": missing actual runtime checks")
        for label in logs:
            artifact(record.get(label), name + " " + label)
        if name == "DEDICATED_RESTART_PASS":
            distinct_runs(record, ("freshStart", "restart", "verifyPersistentState"), name)
            if (isinstance(record.get("freshStartLog"), dict) and isinstance(record.get("restartLog"), dict) and
                    any(record["freshStartLog"].get(key) == record["restartLog"].get(key) for key in ("path", "sha256"))):
                error("dedicated fresh/restart logs must be distinct")
            paired_manifests(record, "savedWorldManifest", "restartedWorldManifest", name)
        if name == "CLIENT_VISUAL_PASS":
            distinct_runs(record, ("freshStart", "resourceReload", "restart", "interactions"), name)
            interactions = record.get("interactions")
            if not isinstance(interactions, dict) or any(
                    not isinstance(interactions.get(family), dict) or any(interactions[family].get(action) != "PASS" for action in ("place", "break", "use"))
                    for family in ("doors", "windows", "stairs", "seats", "attachments")):
                error("client family interactions are incomplete")
            captures = record.get("captures")
            if record.get("interactive") is not True or not isinstance(captures, list) or not captures:
                error("client proof requires interactive captures")
            else:
                for capture in captures:
                    path = artifact(capture, "client capture")
                    if path is not None:
                        if not valid_capture(path):
                            error("client capture must be a complete 8-bit RGB/RGBA PNG")

    blockers = document.get("knownReleaseBlockers")
    if not isinstance(blockers, list):
        error("knownReleaseBlockers must be a scoped list")
        blockers = [{}]
    for blocker in blockers:
        if not isinstance(blocker, dict) or blocker.get("scope") not in SCOPES or not isinstance(blocker.get("reason"), str) or not blocker["reason"]:
            error("ambiguous release blocker")
    discrepancies = document.get("unresolvedDiscrepancies")
    if not isinstance(discrepancies, list):
        error("unresolvedDiscrepancies must be explicit")
        discrepancies = ["unspecified"]
    prerequisites = not blockers and not discrepancies and all(gates[name].get("status") == "PASS" for name in GATES[:-1])
    if gates["RELEASE_READY"].get("status") == "PASS" and not prerequisites:
        error("premature RELEASE_READY")
    ready = not errors and prerequisites and gates["RELEASE_READY"].get("status") == "PASS"
    return {"schemaValid": not errors, "releaseReady": ready, "errors": errors}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("document", type=Path)
    parser.add_argument("--evidence-root", type=Path)
    parser.add_argument("--require-ready", action="store_true")
    args = parser.parse_args()
    result = validate(json.loads(args.document.read_text(encoding="utf8")),
                      args.evidence_root or args.document.parent)
    print(json.dumps(result, ensure_ascii=False))
    raise SystemExit(0 if result["schemaValid"] and (not args.require_ready or result["releaseReady"]) else 1)


if __name__ == "__main__":
    main()
