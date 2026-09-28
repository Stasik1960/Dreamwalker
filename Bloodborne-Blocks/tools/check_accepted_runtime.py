"""Fail closed unless a release JAR is the accepted TEST3/runtime continuation.

The accepted runtime is checkpoint 41c20ee (TEST3 plus the preserved catalog/GUI
continuation).  City compatibility states deliberately use rc.1 as their
physics baseline; accepted repair may add owner IDs but may not alter an rc.1
state.  ``physical-footprint`` metadata is descriptive and is not a city
compatibility physics input, so it is intentionally outside that comparison.

The source argument is the named-Yarn ``sourcesJar`` provenance artifact from
``build/devlibs``.  Loom remaps the distributable source archive to
intermediary names, which cannot be compared byte-for-byte with the accepted
Yarn source checkpoint.  The runtime JAR remains the normal remapped archive.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACCEPTED = "41c20ee8b730c2d1567b2ab5a3d579d51bdbea1a"
RC1 = "90a4e051a71ecc7b3156802c54dbfd8219789469"
PROJECT = "Bloodborne-Blocks"
LOGICAL = "src/main/resources/bloodborne_blocks/logical"
CITY = "src/main/resources/bloodborne_blocks/city"


def _canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def _semantic(data: bytes, name: str) -> bytes:
    if name.endswith(".json.gz"):
        data = gzip.decompress(data)
        name = name[:-3]
    return _canonical(json.loads(data)) if name.endswith((".json", ".mcmeta")) else data


def _text_semantic(data: bytes) -> bytes:
    """Git objects are LF while the Windows checkout may be CRLF."""
    return data.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n").encode("utf-8")


def _git(repo: Path, commit: str, path: str) -> bytes:
    return subprocess.run(["git", "show", f"{commit}:{PROJECT}/{path}"], cwd=repo,
                          check=True, capture_output=True).stdout


def _tree(repo: Path, commit: str, path: str) -> list[str]:
    output = subprocess.run(["git", "ls-tree", "-r", "--name-only", commit, "--", f"{PROJECT}/{path}"],
                            cwd=repo, check=True, capture_output=True, text=True).stdout
    return [name.removeprefix(f"{PROJECT}/") for name in output.splitlines()]


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def _json_file(root: Path, path: str):
    return json.loads((root / path).read_bytes())


def _accepted_logical(root: Path, repo: Path) -> list[str]:
    expected = _tree(repo, ACCEPTED, LOGICAL)
    actual = [path.relative_to(root).as_posix() for path in (root / LOGICAL).rglob("*") if path.is_file()]
    _require(set(actual) == set(expected), "LOGICAL_RESOURCE_INVENTORY_CHANGED")
    for path in expected:
        _require(_semantic((root / path).read_bytes(), path) == _semantic(_git(repo, ACCEPTED, path), path),
                 "ACCEPTED_LOGICAL_RESOURCE_CHANGED: " + path)
    palette = _json_file(root, f"{LOGICAL}/production-palette.json")
    _require(len(palette.get("objects", ())) == 49, "ACCEPTED_LOGICAL_PALETTE_MUST_HAVE_49_OBJECTS")
    ids = [row.get("id") for row in palette["objects"]]
    _require(len(ids) == len(set(ids)) and {"o_c001", "o_books", "o_shuttered_window"} <= set(ids),
             "ACCEPTED_LOGICAL_PALETTE_INVALID")
    return expected


def _accepted_java(root: Path, repo: Path) -> list[str]:
    prefix = "src/main/java"
    expected = _tree(repo, ACCEPTED, prefix)
    actual = [path.relative_to(root).as_posix() for path in (root / prefix).rglob("*.java")]
    _require(set(actual) == set(expected), "RUNTIME_JAVA_INVENTORY_CHANGED")
    for path in expected:
        _require(_text_semantic((root / path).read_bytes()) == _text_semantic(_git(repo, ACCEPTED, path)),
                 "ACCEPTED_RUNTIME_JAVA_CHANGED: " + path)
    return expected


def _by_id(rows):
    return rows if isinstance(rows, dict) else {row["id"]: row for row in rows}


def _without_footprints(value):
    if isinstance(value, dict):
        return {key: _without_footprints(item) for key, item in value.items() if key != "physical_footprint"}
    if isinstance(value, list):
        return [_without_footprints(item) for item in value]
    return value


def _profile_refs(value, profiles):
    found = set()
    if isinstance(value, dict):
        for item in value.values(): found.update(_profile_refs(item, profiles))
    elif isinstance(value, list):
        for item in value: found.update(_profile_refs(item, profiles))
    elif isinstance(value, str) and value in profiles:
        found.add(value)
    return found


def _compare_city_geometry(ids, baseline_geometry, current_geometry, label):
    baseline_blocks, current_blocks = baseline_geometry["blocks"], current_geometry["blocks"]
    baseline_profiles, current_profiles = baseline_geometry["profiles"], current_geometry["profiles"]
    references = set()
    for ident in ids:
        _require(ident in baseline_blocks and ident in current_blocks, label + "_GEOMETRY_BLOCK_MISSING: " + ident)
        _require(_canonical(_without_footprints(current_blocks[ident])) ==
                 _canonical(_without_footprints(baseline_blocks[ident])),
                 label + "_GEOMETRY_STATE_CHANGED: " + ident)
        references.update(_profile_refs(baseline_blocks[ident], baseline_profiles))
    for profile in references:
        _require(profile in current_profiles, label + "_PROFILE_MISSING: " + profile)
        _require(_canonical(_without_footprints(current_profiles[profile])) ==
                 _canonical(_without_footprints(baseline_profiles[profile])),
                 label + "_PROFILE_PHYSICS_CHANGED: " + profile)
    return len(references)


def _rc1_city_physics(root: Path, repo: Path) -> int:
    baseline_definitions = _by_id(json.loads(_git(repo, RC1, f"{CITY}/definitions.json"))["blocks"])
    current_definitions = _by_id(_json_file(root, f"{CITY}/definitions.json")["blocks"])
    for ident, value in baseline_definitions.items():
        _require(ident in current_definitions, "RC1_CITY_ID_MISSING: " + ident)
        _require(_canonical(current_definitions[ident]) == _canonical(value), "RC1_CITY_DEFINITION_CHANGED: " + ident)
    baseline_geometry = json.loads(_git(repo, RC1, f"{CITY}/geometry.json"))
    current_geometry = _json_file(root, f"{CITY}/geometry.json")
    profiles = _compare_city_geometry(baseline_definitions, baseline_geometry, current_geometry, "RC1_CITY")
    return {"definitions": len(baseline_definitions), "profiles": profiles}


def _accepted_owner_contracts(root: Path, repo: Path) -> list[str]:
    # New owner IDs are the accepted continuation.  Pin their mapping, mesh and
    # reviewed-wall contracts separately from the rc.1 compatibility IDs above.
    names = ("owner-runtime-mappings.json", "owner-meshes.json.gz", "reviewed-wall-family.json")
    for name in names:
        path = f"{CITY}/{name}"
        _require((root / path).is_file(), "ACCEPTED_OWNER_CONTRACT_MISSING: " + name)
        _require(_semantic((root / path).read_bytes(), path) == _semantic(_git(repo, ACCEPTED, path), path),
                 "ACCEPTED_OWNER_CONTRACT_CHANGED: " + name)
    return list(names)


def _accepted_added_city(root: Path, repo: Path) -> dict:
    rc1_definitions = _by_id(json.loads(_git(repo, RC1, f"{CITY}/definitions.json"))["blocks"])
    accepted_definitions = _by_id(json.loads(_git(repo, ACCEPTED, f"{CITY}/definitions.json"))["blocks"])
    current_definitions = _by_id(_json_file(root, f"{CITY}/definitions.json")["blocks"])
    added = {ident: value for ident, value in accepted_definitions.items() if ident not in rc1_definitions}
    for ident, value in added.items():
        _require(current_definitions.get(ident) is not None, "ACCEPTED_OWNER_DEFINITION_MISSING: " + ident)
        _require(_canonical(current_definitions[ident]) == _canonical(value), "ACCEPTED_OWNER_DEFINITION_CHANGED: " + ident)
    accepted_geometry = json.loads(_git(repo, ACCEPTED, f"{CITY}/geometry.json"))
    current_geometry = _json_file(root, f"{CITY}/geometry.json")
    profiles = _compare_city_geometry(added, accepted_geometry, current_geometry, "ACCEPTED_OWNER")
    return {"definitions": len(added), "profiles": profiles}


def _jar_resources(jar: Path, root: Path) -> None:
    resources = root / "src/main/resources"
    source = [path for path in resources.rglob("*") if path.is_file()]
    metadata = json.loads((resources / 'fabric.mod.json').read_bytes())
    mixins = {v if isinstance(v, str) else v['config'] for v in metadata.get('mixins', [])}
    with zipfile.ZipFile(jar) as archive:
        names = archive.namelist()
        _require(len(names) == len(set(names)) and archive.testzip() is None, "RUNTIME_JAR_CORRUPT_OR_DUPLICATE")
        for path in source:
            name = path.relative_to(resources).as_posix()
            _require(name in names, "RUNTIME_JAR_RESOURCE_MISSING: " + name)
            if name in mixins:
                expected = json.loads(path.read_bytes())
                expected.setdefault('refmap', 'bloodborne-blocks-refmap.json')
                _require(json.loads(archive.read(name)) == expected, 'RUNTIME_JAR_MIXIN_CHANGED: ' + name)
                _require(expected['refmap'] in names, 'RUNTIME_JAR_REFMAP_MISSING')
            elif name == "fabric.mod.json":
                packaged, local = json.loads(archive.read(name)), json.loads(path.read_bytes())
                _require({k: v for k, v in packaged.items() if k != "version"} == {k: v for k, v in local.items() if k != "version"},
                         "RUNTIME_JAR_FABRIC_METADATA_CHANGED")
                _require(packaged.get("version") == local.get("version"), "RUNTIME_JAR_VERSION_MISMATCH")
            else:
                _require(_semantic(archive.read(name), name) == _semantic(path.read_bytes(), name),
                         "RUNTIME_JAR_RESOURCE_CHANGED: " + name)


def _source_jar(source_jar: Path, root: Path, java_paths: list[str]) -> None:
    """Require the unremapped named source artifact, not Loom's published copy."""
    with zipfile.ZipFile(source_jar) as archive:
        names = {name for name in archive.namelist() if name.endswith(".java")}
        _require(names == {path.removeprefix("src/main/java/") for path in java_paths}, "SOURCES_JAR_INVENTORY_CHANGED")
        for path in java_paths:
            name = path.removeprefix("src/main/java/")
            _require(_text_semantic(archive.read(name)) == _text_semantic((root / path).read_bytes()),
                     "SOURCES_JAR_PROVENANCE_CHANGED: " + name)


def validate_sources(root: Path = ROOT, repo: Path | None = None) -> dict:
    """Verify source provenance before a Gradle build creates release archives."""
    root, repo = Path(root), Path(repo or Path(root).parent)
    logical = _accepted_logical(root, repo)
    java = _accepted_java(root, repo)
    rc1_city = _rc1_city_physics(root, repo)
    owner_contracts = _accepted_owner_contracts(root, repo)
    accepted_owners = _accepted_added_city(root, repo)
    return {"result": "PASS", "acceptedCheckpoint": ACCEPTED, "rc1PhysicsBaseline": RC1,
            "logicalResources": len(logical), "runtimeJava": len(java), "rc1City": rc1_city,
            "acceptedOwnerContracts": owner_contracts, "acceptedOwners": accepted_owners}


def validate(runtime_jar: Path, named_sources_jar: Path, root: Path = ROOT, repo: Path | None = None) -> dict:
    root, repo = Path(root), Path(repo or root.parent)
    _require(runtime_jar.is_file() and named_sources_jar.is_file(), "MISSING_RUNTIME_OR_NAMED_SOURCES_JAR")
    sources = validate_sources(root, repo)
    java = _tree(repo, ACCEPTED, "src/main/java")
    _jar_resources(runtime_jar, root)
    _source_jar(named_sources_jar, root, java)
    with runtime_jar.open("rb") as stream:
        runtime_hash = hashlib.file_digest(stream, "sha256").hexdigest()
    return {**sources, "runtimeJarSha256": runtime_hash}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("runtime_jar", type=Path, nargs="?")
    parser.add_argument("named_sources_jar", type=Path, nargs="?", metavar="named_sources_jar")
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--repo", type=Path)
    args = parser.parse_args()
    if args.runtime_jar is None and args.named_sources_jar is None:
        result = validate_sources(args.root, args.repo)
    elif args.runtime_jar is not None and args.named_sources_jar is not None:
        result = validate(args.runtime_jar, args.named_sources_jar, args.root, args.repo)
    else:
        parser.error("runtime and sources JARs must be supplied together")
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
