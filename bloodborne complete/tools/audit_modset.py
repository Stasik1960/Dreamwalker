"""Read-only inventory of supplied mod JARs and recursively nested JARs."""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import pathlib
import zipfile


def inspect_jar(data: bytes, path: str, depth: int = 0) -> dict:
    result = {"path": path, "size": len(data), "sha256": hashlib.sha256(data).hexdigest()}
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            names = set(archive.namelist())
            for metadata in ("fabric.mod.json", "quilt.mod.json", "META-INF/jarjar/metadata.json"):
                if metadata in names:
                    raw = archive.read(metadata)
                    try:
                        result[metadata] = json.loads(raw)
                    except json.JSONDecodeError as exc:
                        # Gson accepts some non-strict strings. Keep this
                        # visible and require actual Loader verification.
                        result.setdefault("metadata_warnings", []).append({"path": metadata,
                            "strict_json_error": str(exc), "sha256": hashlib.sha256(raw).hexdigest()})
                        result[metadata] = json.loads(raw, strict=False)
            for metadata in ("META-INF/mods.toml", "META-INF/neoforge.mods.toml"):
                if metadata in names:
                    result[metadata] = archive.read(metadata).decode("utf8")
            if "META-INF/MANIFEST.MF" in names:
                manifest = archive.read("META-INF/MANIFEST.MF").decode("utf8", errors="replace")
                result["manifest"] = {line.split(": ", 1)[0]: line.split(": ", 1)[1]
                                      for line in manifest.splitlines() if ": " in line}
            nested = sorted(name for name in names if name.endswith(".jar"))
            if depth > 8:
                raise ValueError("Unexpected nested JAR depth")
            result["nested"] = [inspect_jar(archive.read(name), path + "!/" + name, depth + 1) for name in nested]
            # Hash metadata and preserve all declared dependencies, but do not
            # expose unrelated launcher account/configuration files.
            result["entry_count"] = len(names)
    except Exception as exc:
        result["error"] = type(exc).__name__ + ": " + str(exc)
    return result


def flatten(jars: list[dict]):
    for jar in jars:
        yield jar
        yield from flatten(jar.get("nested", []))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("modset", type=pathlib.Path)
    parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[1] / "reports" / "MODSET_INVENTORY.json")
    args = parser.parse_args()
    with zipfile.ZipFile(args.modset) as archive:
        jars = [inspect_jar(archive.read(name), name) for name in sorted(archive.namelist()) if name.endswith(".jar")]
        nonjar = [{"path": entry.filename, "size": entry.file_size}
                  for entry in archive.infolist() if not entry.is_dir() and not entry.filename.endswith(".jar")]
    all_jars = list(flatten(jars))
    fabric = [jar for jar in all_jars if "fabric.mod.json" in jar]
    ids = {}
    for jar in fabric:
        metadata = jar["fabric.mod.json"]
        ids.setdefault(metadata["id"], []).append({"path": jar["path"], "version": metadata["version"]})
        for provided in metadata.get("provides", []):
            ids.setdefault(provided, []).append({"path": jar["path"], "version": metadata["version"], "provided": True})
    result = {"schema": 1, "source": str(args.modset), "sha256": hashlib.sha256(args.modset.read_bytes()).hexdigest(),
              "top_level_jar_count": len(jars), "nested_jar_count": len(all_jars) - len(jars),
              "fabric_metadata_count": len(fabric), "jars": jars, "nonjar_entries": nonjar,
              "fabric_ids": ids, "errors": [{"path": jar["path"], "error": jar["error"]} for jar in all_jars if "error" in jar]}
    primary_ids = {}
    for jar in jars:
        if "fabric.mod.json" in jar:
            metadata = jar["fabric.mod.json"]
            primary_ids.setdefault(metadata["id"], []).append({"path": jar["path"], "version": metadata["version"]})
    result["duplicate_top_level_mod_ids"] = {key: value for key, value in primary_ids.items() if len(value) > 1}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    important = {key: ids[key] for key in ("fabric-api", "geckolib", "bloodborne_rp", "bloodborne_dw", "worldedit", "iris", "sodium") if key in ids}
    print(json.dumps({key: result[key] for key in ("top_level_jar_count", "nested_jar_count", "fabric_metadata_count", "errors")}))
    print(json.dumps(important))


if __name__ == "__main__":
    main()
