"""Compare every source resource with the remapped runtime JAR (no game launch)."""
from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from check_staged_resources import ROOT, inventory


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def validate(jar_path, resource_root, class_root, generated_refmap=None):
    source = inventory(resource_root)
    with zipfile.ZipFile(jar_path) as jar:
        names = jar.namelist()
        require(len(names) == len(set(names)), "Duplicate ZIP entries")
        require(jar.testzip() is None, "ZIP CRC failure")
        missing = set(source) - set(names)
        require(not missing, f"Missing resources: {sorted(missing)[:8]}")
        production_classes = {name for name in inventory(class_root) if name.endswith(".class")}
        packaged_classes = {name for name in names if name.endswith(".class")}
        require(packaged_classes == production_classes, "Packaged classes differ from compiled production classes")
        metadata = json.loads(source["fabric.mod.json"].read_text(encoding="utf-8"))
        generated = {"META-INF/MANIFEST.MF"}
        expected_mixins = {}
        for entry in metadata.get("mixins", []):
            config_name = entry if isinstance(entry, str) else entry["config"]
            config = json.loads(source[config_name].read_text(encoding="utf-8"))
            # Pinned Loom adds its generated refmap and reformats mixin JSON.
            # No other semantic difference is permitted.
            if "refmap" not in config and generated_refmap is not None:
                config["refmap"] = generated_refmap
            if "refmap" in config:
                generated.add(config["refmap"])
            expected_mixins[config_name] = config
        def verify(name):
            data = source[name].read_bytes()
            packed = jar.read(name)
            if name in expected_mixins:
                require(json.loads(packed) == expected_mixins[name], f"Packaged mixin config differs: {name}")
            else:
                require(packed == data, f"Packaged resource differs: {name}")
            return f"{name}\0{hashlib.sha256(data).hexdigest()}\n"

        with ThreadPoolExecutor(max_workers=8) as executor:
            entries = list(executor.map(verify, sorted(source)))
        files = {name for name in names if not name.endswith("/")}
        extras = files - set(source) - production_classes - generated
        require(not extras, f"Stale/unexpected packaged entries: {sorted(extras)[:8]}")
        require(generated <= files, f"Missing generated metadata: {sorted(generated - files)}")
        if generated_refmap is not None:
            compiled_refmap = class_root / generated_refmap
            require(compiled_refmap.is_file(), "Missing authoritative compiled refmap")
            require(jar.read(generated_refmap) == compiled_refmap.read_bytes(), "Packaged generated refmap differs")
    with jar_path.open("rb") as stream:
        jar_hash = hashlib.file_digest(stream, "sha256").hexdigest()
    return {"ok": True, "version": metadata["version"], "resources": len(source),
            "loomMixinConfigs": sorted(expected_mixins),
            "manifestSha256": hashlib.sha256("".join(entries).encode()).hexdigest(),
            "jarSha256": jar_hash}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path)
    args = parser.parse_args()
    # Matches archivesName='bloodborne-blocks' in this project's pinned build.
    print(json.dumps(validate(args.jar, ROOT / "src/main/resources", ROOT / "build/classes/java/main",
                              generated_refmap="bloodborne-blocks-refmap.json")))


if __name__ == "__main__":
    main()
