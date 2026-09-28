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
    result = validate(args.jar, args.source_jar, args.root, args.packaged_resources)
    if args.packaged_resources:
        from check_accepted_runtime import validate as accepted_runtime
        result['acceptedRuntime'] = accepted_runtime(args.jar, args.source_jar, args.root)
        result['acceptedCity'] = validate_accepted_city(args.root)
    print(json.dumps(result))


def validate_accepted_city(root):
    """An old runtime renamed as a newer candidate cannot pass release checks.

    The supplied full city is independently verified on every package check;
    a stale PASS report, registry census or protected-ID list is insufficient.
    """
    import gzip
    from verify_accepted_restore import verify, RC1
    manifest = json.loads((root / 'docs/accepted-restore/delivery.json').read_bytes())
    if manifest.get('version') != version(root):
        raise ValueError('Accepted city version differs from runtime')
    paths = {}
    for name in ('jar', 'sourcesJar', 'world', 'conversion', 'independent', 'secondConversion', 'secondIndependent'):
        item = manifest[name]
        path = (root / item['path']).resolve()
        if not path.is_relative_to(root.resolve()) or sha(path) != item['sha256']:
            raise ValueError('Accepted delivery artifact changed: ' + name)
        paths[name] = path
    from check_accepted_runtime import validate as accepted_runtime
    accepted_runtime(paths['jar'], paths['sourcesJar'], root)
    def read(path):
        raw = path.read_bytes()
        return json.loads(gzip.decompress(raw) if path.suffix == '.gz' else raw)
    report = read(paths['conversion'])
    result = verify(RC1, paths['world'], report, root/'src/main/resources/bloodborne_blocks/logical')
    if result['result'] != 'PASS' or read(paths['independent']) != result:
        raise ValueError('Supplied city differs from independently accepted restoration')
    second, proof = read(paths['secondConversion']), read(paths['secondIndependent'])
    if (second.get('result') != 'PASS' or second['counts']['restored'] != 0 or second.get('ledger') or
        second['outputFiles'] != report['outputFiles'] or second['source']['hashes'] != report['outputFiles'] or
        proof.get('result') != 'PASS' or proof.get('outputTreeSha256') != result['outputTreeSha256']):
        raise ValueError('Accepted city idempotence proof failed')
    return {k: v for k, v in result.items() if k != 'families'}


if __name__ == "__main__":
    main()
