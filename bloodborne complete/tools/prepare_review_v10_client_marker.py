"""Bind an actual fresh V10 author output and exact ordinary JAR to new QA.

This only writes an explicit marker; it neither launches a game nor supplies
synthetic successful client/scene evidence. Historical V9 markers stay intact.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def prepare(jar: Path, author_path: Path, output: Path, mode: str,
            shader_marker: Path | None = None, diagnostics: bool = True) -> dict:
    jar = jar.resolve(strict=True)
    author_path = author_path.resolve(strict=True)
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    author = json.loads(author_path.read_text(encoding="utf-8"))
    if author.get("schema") != "dw-review-v10-scene-output-v1" or not str(author.get("status", "")).startswith("PASS_SCENE_AUTHORED"):
        raise ValueError("An actual PASS fresh V10 author output is required")
    if author.get("productionJarSha256") != digest:
        raise ValueError("Author output is bound to another production JAR")
    samples = author.get("architectureSamples", [])
    expected = {"90001", "90002", "90003", "90004", "90005", "90006", "90007", "90010", *[str(i) for i in range(90011, 90021)]}
    if len(samples) != 18 or {row.get("expectedType") for row in samples} != expected:
        raise ValueError("Exactly the18 canonical architecture samples are required")
    fixtures = {row.get("key"): row for row in author.get("fixtures", [])}
    keys = {"lamp_A", "lamp_B", "lamp_C", "lamp_D", "lever_1", "lever_2", "lever_3", "architecture_door", "rp_door", "wood_gate", "rp_ladder", "rp_stairs", "diag_rp_door"}
    if not keys <= fixtures.keys():
        raise ValueError("Required menu/remote/diagnostics fixtures absent")
    marker = {
        "schema": "dw-review-v10-client-input-v1", "schemaVersion": 1,
        "revision": "V10", "guard": "ISOLATED_SAVED_REVIEW_CLIENT_ONLY",
        "worldName": "prototype-fixture", "productionJarSha256": digest,
        "artifactSha256": digest, "authorOutput": str(author_path),
        "authorOutputSha256": hashlib.sha256(author_path.read_bytes()).hexdigest(),
        "menus": {"mode": mode}, "maxClientTicks": 24000,
        "expectedRegistryIds": sorted(row["registry"] for row in samples),
        "scope": "Actual first-entry Creative, real Screen widgets and ordinary item/attack packets; no manual/GPU/catalogue completeness claim.",
    }
    if diagnostics:
        marker["diagnosticsReview"] = {"root": [0, 64, 4], "seconds": 20, "windowTicks": 100}
    if shader_marker:
        source = json.loads(shader_marker.read_text(encoding="utf-8"))
        if not source.get("expectedShaderPack") or len(source.get("expectedShaderOptions", {})) != 42:
            raise ValueError("Strict shader marker must contain the actual pack name and42 requested options")
        marker["expectedShaderPack"] = source["expectedShaderPack"]
        marker["expectedShaderOptions"] = source["expectedShaderOptions"]
        marker["shaderMarkerSource"] = str(shader_marker.resolve())
        marker["shaderMarkerSourceSha256"] = hashlib.sha256(shader_marker.read_bytes()).hexdigest()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(marker, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {"status": "MARKER_PREPARED_ACTUAL_CLIENT_NOT_RUN", "output": str(output.resolve()), "sha256": hashlib.sha256(output.read_bytes()).hexdigest(), "productionJarSha256": digest, "mode": mode}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--author-output", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--mode", choices=("AUTHOR", "REENTER"), default="AUTHOR")
    parser.add_argument("--shader-marker", type=Path)
    parser.add_argument("--no-diagnostics", action="store_true")
    args = parser.parse_args()
    print(json.dumps(prepare(args.jar, args.author_output, args.output, args.mode, args.shader_marker, not args.no_diagnostics), ensure_ascii=True))


if __name__ == "__main__":
    main()
