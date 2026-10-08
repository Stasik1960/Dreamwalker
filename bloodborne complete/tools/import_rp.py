"""Import supplied RP main source and full assets without editing their bytes.

The standalone metadata is retained for provenance; the combined build must
exclude src/rp/resources/fabric.mod.json and use its own root metadata.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import pathlib
import subprocess
import zipfile


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=pathlib.Path)
    parser.add_argument("reference_jar", type=pathlib.Path)
    parser.add_argument("--project", type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[1])
    parser.add_argument("--branch", default="origin/bloodborne")
    args = parser.parse_args()
    repo = args.project.parent
    output = args.project / "src" / "rp"
    reports = args.project / "reports"
    reports.mkdir(parents=True, exist_ok=True)
    main_prefix = "Bloodborne-RP/src/main/"
    private_prefix = "Bloodborne-RP/private-assets/"
    selected: dict[str, tuple[str, bytes]] = {}
    overlays = []
    with zipfile.ZipFile(args.source) as source, zipfile.ZipFile(args.reference_jar) as jar:
        for name in source.namelist():
            if name.endswith("/"):
                continue
            destination = None
            if name.startswith(main_prefix + "java/"):
                destination = name[len(main_prefix):]
            elif name.startswith(main_prefix + "resources/"):
                destination = name[len(main_prefix):]
            else:
                for kind in ("test", "gametest", "clienttest", "servertest"):
                    prefix = "Bloodborne-RP/src/" + kind + "/"
                    if name.startswith(prefix + "java/") or name.startswith(prefix + "resources/"):
                        destination = "tests/" + kind + "/" + name[len(prefix):]
                        break
            if destination:
                selected[destination] = (name, source.read(name))
        for name in source.namelist():
            if name.startswith(private_prefix) and not name.endswith("/"):
                destination = "resources/" + name[len(private_prefix):]
                data = source.read(name)
                if destination in selected:
                    previous_name, previous = selected[destination]
                    overlays.append({"path": destination, "base": previous_name, "full": name,
                                     "base_sha256": sha(previous), "full_sha256": sha(data),
                                     "changed": data != previous})
                selected[destination] = (name, data)
        imported = []
        for destination, (source_entry, data) in sorted(selected.items()):
            path = (output / destination).resolve()
            if output.resolve() not in path.parents:
                raise ValueError("Unsafe archive path: " + destination)
            path.parent.mkdir(parents=True, exist_ok=True)
            if path.exists() and path.read_bytes() != data:
                raise ValueError("Refusing to overwrite edited RP source: " + destination)
            path.write_bytes(data)
            imported.append({"path": str(path.relative_to(args.project)).replace("\\", "/"),
                             "source_entry": source_entry, "size": len(data), "sha256": sha(data)})
        resource_checks = []
        for destination, (source_entry, data) in sorted(selected.items()):
            if not destination.startswith("resources/") or destination.endswith("fabric.mod.json"):
                continue
            name = destination[len("resources/"):]
            status = "MISSING" if name not in jar.namelist() else "PASS" if data == jar.read(name) else "DIFFERENT"
            resource_checks.append({"path": name, "status": status,
                                    "source_sha256": sha(data),
                                    "jar_sha256": sha(jar.read(name)) if name in jar.namelist() else None})
        main_java = {destination[len("java/"):]: (entry, data)
                     for destination, (entry, data) in selected.items() if destination.startswith("java/")}
        comparisons = []
        for prefix in ("Bloodborne-RP/src/main/java/", "bloodborne complete/src/main/java/"):
            tree = subprocess.run(["git", "ls-tree", "-r", "--name-only", args.branch, "--", prefix],
                                  cwd=repo, check=True, capture_output=True).stdout.decode().splitlines()
            known = set(tree)
            for relative, (entry, data) in sorted(main_java.items()):
                path = prefix + relative
                other = subprocess.run(["git", "show", args.branch + ":" + path], cwd=repo,
                                       capture_output=True, check=True).stdout if path in known else None
                comparisons.append({"branch_path": path, "source_entry": entry,
                                    "status": "MISSING" if other is None else "BYTE_EQUAL" if data == other else
                                    "TEXT_EQUAL" if data.decode("utf8").splitlines() == other.decode("utf8").splitlines() else "DIFFERENT",
                                    "branch_sha256": sha(other) if other is not None else None})
        result = {"schema": 1, "source": str(args.source), "source_sha256": sha(args.source.read_bytes()),
                  "reference_jar": str(args.reference_jar), "reference_jar_sha256": sha(args.reference_jar.read_bytes()),
                  "imported_files": imported, "private_asset_overlays": overlays,
                  "reference_jar_resources": resource_checks, "branch": args.branch,
                  "branch_java_comparison": comparisons,
                  "note": "RP bytes preserved; standalone fabric.mod.json must be excluded from combined JAR."}
        result["branch_sha"] = subprocess.run(["git", "rev-parse", args.branch], cwd=repo,
                                              check=True, capture_output=True).stdout.decode().strip()
        (reports / "RP_IMPORT.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
        counts = collections.Counter(c["status"] for c in resource_checks)
        print(json.dumps({"imported": len(imported), "main_java": len(main_java), "full_resource_checks": counts,
                          "overlays": len(overlays), "branch_java": collections.Counter(c["status"] for c in comparisons)}))
        if any(check["status"] != "PASS" for check in resource_checks):
            raise SystemExit("Imported full resources differ from supplied full RP JAR")


if __name__ == "__main__":
    main()
