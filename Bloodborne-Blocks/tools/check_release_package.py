"""Verify versions, runtime/source JAR closure and SHA-256 before CI artifact upload."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path

from check_packaged_resources import validate as validate_resources

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def version(root):
    gradle = (root / "build.gradle").read_text(encoding="utf-8")
    match = re.search(r"^version\s*=\s*'([0-9A-Za-z.+-]+)'", gradle, re.M)
    if not match:
        raise ValueError("Gradle version missing/invalid")
    value = match.group(1)
    if (root / "VERSION").read_text(encoding="utf-8").strip() != value:
        raise ValueError("VERSION differs from Gradle")
    source_metadata = json.loads((root / "src/main/resources/fabric.mod.json").read_bytes())
    if source_metadata.get("version") != value:
        raise ValueError("source fabric.mod.json version differs")
    return value


def validate(jar, source_jar, root=ROOT, packaged=False):
    value = version(root)
    if jar.name != f"bloodborne-blocks-{value}.jar":
        raise ValueError("Unexpected runtime JAR name")
    if source_jar.name != f"bloodborne-blocks-{value}-sources.jar":
        raise ValueError("Unexpected sources JAR name")
    if not jar.is_file() or not source_jar.is_file():
        raise ValueError("Missing release JAR artifact")
    expected_sources = {path.relative_to(root / "src/main/java").as_posix()
                        for path in (root / "src/main/java").rglob("*.java")}
    for path in (jar, source_jar):
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            if len(names) != len(set(names)) or archive.testzip() is not None:
                raise ValueError("Duplicate entries or CRC failure: " + path.name)
            metadata = json.loads(archive.read("fabric.mod.json"))
            if metadata.get("version") != value:
                raise ValueError("Packaged fabric.mod.json version differs: " + path.name)
            if path == source_jar:
                sources = {name for name in names if name.endswith(".java")}
                if not expected_sources or sources != expected_sources or any(name.endswith(".class") for name in names):
                    raise ValueError("Source JAR inventory differs from production Java sources")
                if any(not archive.read(name).strip() for name in sources):
                    raise ValueError("Empty Java source in sources JAR")
    result = {"version": value, "jar": {"path": jar.name, "sha256": sha(jar)},
              "sourceJar": {"path": source_jar.name, "sha256": sha(source_jar)},
              "sourceJavaFiles": len(expected_sources)}
    if packaged:
        result["resources"] = validate_resources(jar, root / "src/main/resources", root / "build/classes/java/main",
                                                  "bloodborne-blocks-refmap.json")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path, nargs="?")
    parser.add_argument("source_jar", type=Path, nargs="?")
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--version-only", action="store_true")
    parser.add_argument("--packaged-resources", action="store_true")
    args = parser.parse_args()
    if args.version_only:
        print(version(args.root))
        return
    if args.jar is None or args.source_jar is None:
        parser.error("runtime and sources JARs are required")
    print(json.dumps(validate(args.jar, args.source_jar, args.root, args.packaged_resources)))


if __name__ == "__main__":
    main()
