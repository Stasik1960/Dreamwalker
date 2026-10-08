"""Select an isolated test profile; never change the user's installed mods."""
import argparse
import hashlib
import json
from pathlib import Path
from audit_modset import inspect_jar

ROOT = Path(__file__).resolve().parents[1]
# Coupled render versions must be selected together; all are supplied files.
COUPLED = {"sodium": "0.5.13+mc1.20.1", "iris": "1.7.6+mc1.20.1", "indium": "1.0.36+mc1.20.1",
           "continuity": "3.0.0+1.20.1"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inventory", type=Path, default=ROOT / "reports/MODSET_INVENTORY.json")
    parser.add_argument("--installed", type=Path, default=Path("C:/Users/vakir/Limacina/project/dw/mods"))
    parser.add_argument("--output", type=Path, default=ROOT / "reports/MODSET_PROFILE.json")
    args = parser.parse_args()
    inventory = json.loads(args.inventory.read_text(encoding="utf8"))
    local = []
    if args.installed.exists():
        for path in sorted(args.installed.glob("*.jar")):
            data = path.read_bytes()
            jar = inspect_jar(data, path.name)
            metadata = jar.get("fabric.mod.json", {})
            local.append({"file": path.name, "size": len(data), "sha256": hashlib.sha256(data).hexdigest(),
                          "id": metadata.get("id"), "version": metadata.get("version")})
    selected, excluded = [], []
    groups = {}
    for jar in inventory["jars"]:
        groups.setdefault(jar["fabric.mod.json"]["id"], []).append(jar)
    for key, values in groups.items():
        if key in ("bloodborne_rp", "bloodborne_dw", "bloodborne_complete", "bloodborne_blocks"):
            excluded.extend({"path": jar["path"], "sha256": jar["sha256"], "reason": "replaced by combined dreamwalker JAR"} for jar in values)
            continue
        reason = "only supplied version"
        matches = [jar for jar in values if any(item["sha256"] == jar["sha256"] for item in local)]
        if len(values) == 1:
            chosen = values[0]
        elif key in COUPLED:
            chosen = next(jar for jar in values if jar["fabric.mod.json"]["version"] == COUPLED[key])
            reason = "consistent supplied Sodium 0.5.13 / Iris 1.7.6 / Indium 1.0.36 set; stable Continuity"
        elif len(matches) == 1:
            chosen = matches[0]
            reason = "exact SHA-256 match to the only installed version"
        else:
            raise ValueError("Unresolved duplicate requires an explicit deterministic rule: " + key)
        metadata = chosen["fabric.mod.json"]
        selected.append({"path": chosen["path"], "sha256": chosen["sha256"], "id": key,
                         "version": metadata["version"], "environment": metadata.get("environment", "*"),
                         "reason": reason})
        excluded.extend({"path": jar["path"], "sha256": jar["sha256"], "reason": "duplicate mod ID; selected " + chosen["path"]}
                        for jar in values if jar is not chosen)
    selected.sort(key=lambda item: item["path"])
    minimal = [item["path"] for item in selected if item["id"] in ("fabric-api", "geckolib")]
    loader_candidate = args.installed.parent / "0.19.5.jar"
    loader_evidence = None
    loader_version = "0.18.2"
    if loader_candidate.is_file():
        jar = inspect_jar(loader_candidate.read_bytes(), str(loader_candidate))
        if jar.get("fabric.mod.json", {}).get("id") == "fabricloader":
            loader_version = jar["fabric.mod.json"]["version"]
            loader_evidence = {"path": str(loader_candidate), "sha256": jar["sha256"], "version": loader_version}
    result = {"schema": "dreamwalker-isolated-modset-profile-v1", "source_modset_sha256": inventory["sha256"],
              "loader": loader_version, "loader_evidence": loader_evidence, "java": "17", "minecraft": "1.20.1",
              "installed_directory_read_only": str(args.installed), "installed_jar_metadata": local,
              "selection_status": "PREPARED_NOT_RUNTIME_VERIFIED", "selected": selected, "excluded": excluded,
              "profiles": {"minimal": minimal, "full_server": [item["path"] for item in selected if item["environment"] != "client"],
                           "full_client": [item["path"] for item in selected if item["environment"] != "server"]},
              "notes": ["The raw supplied archive and installed directory contain duplicate IDs; this isolated profile makes choices explicit.",
                        "Loader 0.18.2 is required by supplied Farmer's Delight 2.5.7 and nested PortingLib 2.3.15; existing supplied user loader is preferred when verified; compilation API baseline remains supplied 0.92.9.",
                        "Environment metadata does not prove dedicated-server safety; only an actual launch can verify it."]}
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"selected": len(selected), "excluded": len(excluded), "profiles": {key: len(values) for key, values in result["profiles"].items()}}))


if __name__ == "__main__":
    main()
