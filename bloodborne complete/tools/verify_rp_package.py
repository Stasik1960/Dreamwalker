"""Verify full RP resource bytes and catalog closure in a final combined JAR."""
from __future__ import annotations
import argparse
import collections
import hashlib
import json
from pathlib import Path
import zipfile
from rp_migration import registry_schema

ROOT = Path(__file__).resolve().parents[1]


def verify(reference: Path, final: Path | None, output: Path) -> dict:
    source = ROOT / "src/rp/resources"
    catalog = json.loads((source / "assets/bloodborne_rp/catalog.json").read_text(encoding="utf8"))
    registry = registry_schema()
    checks, catalog_checks, animation_checks = [], [], []
    final_metadata = None
    with zipfile.ZipFile(reference) as baseline:
        artifact = zipfile.ZipFile(final) if final else None
        try:
            baseline_names = set(baseline.namelist())
            final_names = set(artifact.namelist()) if artifact else set()
            for name in sorted(n for n in baseline_names if n.startswith("assets/bloodborne_rp/") or n == "import-manifest.json"):
                if name.endswith("/"):
                    continue
                expected = baseline.read(name)
                path = source / name
                source_status = "PASS" if path.exists() and path.read_bytes() == expected else "FAIL"
                package_status = "NOT_RUN" if artifact is None else "PASS" if name in final_names and artifact.read(name) == expected else "FAIL"
                checks.append({"path": name, "sha256": hashlib.sha256(expected).hexdigest(),
                               "source_status": source_status, "package_status": package_status})
            for key, spec in sorted(catalog.items()):
                for part in ("model", "texture", "animation"):
                    resource = spec[part]
                    namespace, path = resource.split(":", 1) if ":" in resource else ("bloodborne_rp", resource)
                    name = "assets/" + namespace + "/" + path
                    data = (source / name).read_bytes() if (source / name).is_file() else None
                    intentional_static = part == "animation" and path == "animations/fallback.animation.json" and not spec.get("clips")
                    fallback_geometry = part == "model" and "fallback" in path
                    status = "PASS" if data is not None and not fallback_geometry else "FAIL"
                    if data is not None and part != "texture":
                        json.loads(data)
                    if data is not None and part == "texture" and not data.startswith(b"\x89PNG\r\n\x1a\n"):
                        status = "FAIL"
                    catalog_checks.append({"asset": key, "part": part, "path": name, "status": status,
                                           "intentional_no_animation_resource": intentional_static,
                                           "fallback_geometry": fallback_geometry})
                    if part == "animation" and data is not None:
                        animations = json.loads(data).get("animations", {})
                        for clip in spec.get("clips", {}).values():
                            animation_checks.append({"asset": key, "path": name, "clip": clip["name"],
                                                     "status": "PASS" if clip["name"] in animations else "FAIL"})
            if artifact is not None:
                final_metadata = json.loads(artifact.read("fabric.mod.json"))
                expected_entrypoints = {"main": "dev.dreamwalker.bloodbornerp.BloodborneRp",
                                        "client": "dev.dreamwalker.bloodbornerp.client.BloodborneRpClient"}
                for kind, classname in expected_entrypoints.items():
                    values = final_metadata.get("entrypoints", {}).get(kind, [])
                    count = values.count(classname)
                    checks.append({"path": "fabric.mod.json/entrypoints/" + kind, "source_status": "PASS",
                                   "package_status": "PASS" if count == 1 else "FAIL", "entrypoint_count": count})
                checks.append({"path": "fabric.mod.json/provides/bloodborne_rp", "source_status": "PASS",
                               "package_status": "PASS" if "bloodborne_rp" in final_metadata.get("provides", []) else "FAIL"})
                classes = [name for name in final_names if name.startswith("dev/dreamwalker/bloodbornerp/") and name.endswith(".class")]
                if not classes:
                    checks.append({"path": "dev/dreamwalker/bloodbornerp", "source_status": "PASS", "package_status": "FAIL"})
        finally:
            if artifact is not None:
                artifact.close()
    result = {"schema": "dreamwalker-rp-package-verification-v1", "reference": str(reference),
              "reference_sha256": hashlib.sha256(reference.read_bytes()).hexdigest(),
              "final_jar": str(final) if final else None, "final_jar_sha256": hashlib.sha256(final.read_bytes()).hexdigest() if final else None,
              "checks": checks, "catalog_checks": catalog_checks, "animation_clip_checks": animation_checks,
              "registered_mobs": len(registry["mobs"]), "registered_objects": len(registry["objects"]),
              "registered_entity_types": len(registry["entity_ids"]), "registered_item_types": len(registry["item_ids"]),
              "rp_block_types": 0, "final_metadata": final_metadata,
              "runtime_fallback_selection": "NOT_RUN", "client_visuals": "NOT_RUN",
              "note": "Static asset entries with no clips intentionally share fallback.animation.json; no catalog geometry uses fallback."}
    result["source_status"] = "PASS" if all(c["source_status"] == "PASS" for c in checks) and all(c["status"] == "PASS" for c in catalog_checks + animation_checks) else "FAIL"
    result["package_status"] = "NOT_RUN" if final is None else "PASS" if all(c["package_status"] == "PASS" for c in checks) else "FAIL"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"resources": len(checks), "catalog_references": len(catalog_checks), "animation_clips": len(animation_checks),
                      "source_status": result["source_status"], "package_status": result["package_status"]}))
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, default=Path("C:/Users/vakir/Limacina/project/dw/mods/bloodborne-rp-1.0.0-rp.2-full-local.jar"))
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "reports/RP_PACKAGE_VERIFICATION.json")
    args = parser.parse_args()
    result = verify(args.reference, args.jar, args.output)
    if result["source_status"] == "FAIL" or result["package_status"] == "FAIL":
        raise SystemExit(1)
