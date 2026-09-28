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
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACCEPTED = "41c20ee8b730c2d1567b2ab5a3d579d51bdbea1a"
RC1 = "90a4e051a71ecc7b3156802c54dbfd8219789469"
PROJECT = "Bloodborne-Blocks"
LOGICAL = "src/main/resources/bloodborne_blocks/logical"
CITY = "src/main/resources/bloodborne_blocks/city"
# The accepted continuation has two narrowly reviewed Java semantic patches.
# Every other runtime source remains byte-for-byte pinned to TEST3.
ALLOWED_JAVA_AMENDMENTS = {
    "src/main/java/dev/dreamwalker/bloodborneblocks/LogicalContractV2.java",
    "src/main/java/dev/dreamwalker/bloodborneblocks/LogicalVariantProperty.java",
    "src/main/java/dev/dreamwalker/bloodborneblocks/ReviewedWallConnections.java",
}


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
    # Reconstruct the narrowly amended baseline; no current-file hash can
    # silently bless changes to another family or to TEST3 render/selection.
    palette = _json_file(root, f"{LOGICAL}/production-palette.json")
    has_grass = any(row['id']=='o_grass_0' for row in palette['objects'])
    grass = None
    if has_grass:
        from build_accepted_bush_extension import build
        with tempfile.TemporaryDirectory() as temporary:
            grass = build(Path(temporary)/'grass.json')
    for path in expected:
        baseline = _semantic(_git(repo, ACCEPTED, path), path)
        if path.endswith(('.json','.json.gz')):
            baseline = _canonical(_logical_amendment(json.loads(baseline),Path(path).name,grass))
        _require(_semantic((root / path).read_bytes(), path) == baseline,
                 "ACCEPTED_LOGICAL_RESOURCE_CHANGED: " + path)
    _require(len(palette.get("objects", ())) == 49+int(has_grass), "ACCEPTED_LOGICAL_PALETTE_COUNT_CHANGED")
    ids = [row.get("id") for row in palette["objects"]]
    _require(len(ids) == len(set(ids)) and {"o_c001", "o_books", "o_shuttered_window"} <= set(ids),
             "ACCEPTED_LOGICAL_PALETTE_INVALID")
    return expected


def _logical_amendment(value, name, grass):
    window='o_shuttered_window'; ident='o_grass_0'
    if name=='contracts-v2.json':
        for family in value['families']:
            if family['id']==window:
                for key,spec in family['states'].items():
                    if 'open=true' in key:spec['collision_footprint']['boxes']=[]
        if grass:value['families'].append(grass['currentContracts']['families'][0]);value['families'].sort(key=lambda r:r['id'])
    elif name=='physical-footprints.json':
        for key,spec in value['families'].get(window,{}).items():
            if 'open=true' in key:spec['boxes']=[]
        if grass:value['families'][ident]=grass['physical']['families'][ident]
    elif name=='geometry.json':
        for key,spec in value['blocks'].get(window,{}).get('states',{}).items():
            if 'open=true' in key:
                for cell in spec['cells'].values():cell['collision']=[]
        if grass:
            value['blocks'].update(grass['geometry']['blocks']);value['profiles'].update(grass['geometry']['profiles'])
    elif grass and name=='definitions.json':
        value['blocks'].extend(grass['definitions']['blocks']);value['blocks'].sort(key=lambda r:r['id'])
    elif grass and name=='meshes.json.gz':value.update(grass['meshes'])
    elif grass and name=='production-palette.json':
        value['objects'].append(grass['productionPaletteEntry']);value['objects'].sort(key=lambda r:r['id'])
    elif grass and name=='visual-slots.json':
        value['families'].append({'id':ident,'states':8,'base_states':4});value['families'].sort(key=lambda r:r['id'])
    return value


def _accepted_java(root: Path, repo: Path) -> list[str]:
    prefix = "src/main/java"
    expected = _tree(repo, ACCEPTED, prefix)
    actual = [path.relative_to(root).as_posix() for path in (root / prefix).rglob("*.java")]
    _require(set(actual) == set(expected), "RUNTIME_JAVA_INVENTORY_CHANGED")
    for path in expected:
        if path in ALLOWED_JAVA_AMENDMENTS:
            _require((root / path).is_file(), "RUNTIME_JAVA_AMENDMENT_MISSING: " + path)
            digest = hashlib.sha256(_text_semantic((root / path).read_bytes())).hexdigest()
            amendment = _json_file(root,'docs/accepted-restore/runtime-amendments.json')
            _require(digest == amendment['java'][path], "REVIEWED_RUNTIME_JAVA_CHANGED: " + path)
            continue
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
        old=json.loads(_semantic(_git(repo,ACCEPTED,path),path))
        now=json.loads(_semantic((root/path).read_bytes(),path))
        if name=='owner-runtime-mappings.json':
            _require({k:v for k,v in now.items() if k not in ('states','rejected')}=={k:v for k,v in old.items() if k not in ('states','rejected')}
                and sorted(map(_canonical,now.get('rejected',[])))==sorted(map(_canonical,old.get('rejected',[]))),'OWNER_MAPPING_METADATA_CHANGED')
            _require(all(now['states'].get(k)==v for k,v in old['states'].items()),'ACCEPTED_OWNER_MAPPING_CHANGED')
        elif name=='owner-meshes.json.gz':
            _require(all(now.get(k)==v for k,v in old.items()),'ACCEPTED_OWNER_MESH_CHANGED')
        else:
            _require(all(now.get(k)==v for k,v in old.items() if k not in ('connections','scope','sourceMappingsSha256')),
                'ACCEPTED_WALL_BASELINE_CHANGED')
            _require(all(now['connections'].get(k)==v for k,v in old.get('connections',{}).items()),'ACCEPTED_CANONICAL_WALL_CHANGED')
    return list(names)


def _accepted_added_city(root: Path, repo: Path) -> dict:
    rc1_definitions = _by_id(json.loads(_git(repo, RC1, f"{CITY}/definitions.json"))["blocks"])
    accepted_definitions = _by_id(json.loads(_git(repo, ACCEPTED, f"{CITY}/definitions.json"))["blocks"])
    current_definitions = _by_id(_json_file(root, f"{CITY}/definitions.json")["blocks"])
    added = {ident: value for ident, value in accepted_definitions.items() if ident not in rc1_definitions}
    for ident, value in added.items():
        if ident=='building_stone_brick_wall':continue
        _require(current_definitions.get(ident) is not None, "ACCEPTED_OWNER_DEFINITION_MISSING: " + ident)
        _require(_canonical(current_definitions[ident]) == _canonical(value), "ACCEPTED_OWNER_DEFINITION_CHANGED: " + ident)
    accepted_geometry = json.loads(_git(repo, ACCEPTED, f"{CITY}/geometry.json"))
    current_geometry = _json_file(root, f"{CITY}/geometry.json")
    profiles = _compare_city_geometry(set(added)-{'building_stone_brick_wall'}, accepted_geometry, current_geometry, "ACCEPTED_OWNER")
    return {"definitions": len(added), "profiles": profiles}


def _reviewed_extensions(root, repo):
    current=_by_id(_json_file(root,f'{CITY}/definitions.json')['blocks'])
    wall='building_stone_brick_wall'
    if wall not in current:
        return {}  # Small baseline-only unit fixture.
    amendment=_json_file(root,'docs/accepted-restore/runtime-amendments.json')
    _require(amendment.get('acceptedBaseline')==ACCEPTED,'AMENDMENT_BASELINE_CHANGED')
    digest=lambda value:hashlib.sha256(_canonical(value)).hexdigest()
    baseline=_by_id(json.loads(_git(repo,ACCEPTED,f'{CITY}/definitions.json'))['blocks'])
    old_mapping=json.loads(_git(repo,ACCEPTED,f'{CITY}/owner-runtime-mappings.json'))['states']
    now_mapping=_json_file(root,f'{CITY}/owner-runtime-mappings.json')['states']
    added=amendment['ownerAdditions']
    _require(set(now_mapping)-set(old_mapping)==set(added),'REVIEWED_OWNER_ADDITIONS_CHANGED')
    from complete_owner_bridge import load_additional_proof
    traces=load_additional_proof(root/'docs/accepted-restore/complete-owner-closures.json.gz')
    proved={o['state'] for t in traces if t.get('result') not in ('UNRESOLVED','REFER_TO_PROVEN_CLOSURE') for o in t['objects']}
    _require(set(added)<=proved,'NEW_OWNER_WITHOUT_SOURCE_PROOF')
    geometry=_json_file(root,f'{CITY}/geometry.json')
    old_geometry=json.loads(_git(repo,ACCEPTED,f'{CITY}/geometry.json'))
    meshes=json.loads(gzip.decompress((root/f'{CITY}/owner-meshes.json.gz').read_bytes()))
    old_meshes=json.loads(gzip.decompress(_git(repo,ACCEPTED,f'{CITY}/owner-meshes.json.gz')))
    new_ids=set();new_meshes=set()
    for source,record in added.items():
        ident='owner_'+hashlib.sha256(source.encode()).hexdigest()[:20]
        new_ids.add(ident);mapping=now_mapping[source]
        _require(mapping['id']=='bloodborne_blocks:'+ident and mapping['properties']=={'facing':'north'},'NEW_OWNER_IDENTITY_CHANGED')
        _require(digest(mapping)==record['mapping'] and digest(current[ident])==record['definition'] and
            digest(geometry['blocks'][ident])==record['geometry'],'REVIEWED_OWNER_CONTRACT_CHANGED: '+source)
        for name,wanted in record['meshes'].items():
            _require(name in meshes and digest(meshes[name])==wanted,'REVIEWED_NEW_OWNER_MESH_CHANGED: '+name)
            new_meshes.add(name)
    _require(set(current)-set(baseline)==new_ids,'UNREVIEWED_CITY_DEFINITION_ADDED')
    _require(set(geometry['blocks'])-set(old_geometry['blocks'])==new_ids,'UNREVIEWED_CITY_GEOMETRY_ADDED')
    _require(set(meshes)-set(old_meshes)==new_meshes,'UNREVIEWED_OWNER_MESH_ADDED')
    _require(digest(current[wall])==amendment['wall'],'REVIEWED_WALL_DEFINITION_CHANGED')
    manifest=_json_file(root,f'{CITY}/reviewed-wall-family.json')
    aliases=manifest['aliases']
    sides=['north','east','south','west']
    def signature(ident,key):
        spec=geometry['blocks'][ident]['states'][key]
        return current[ident]['models'][key],current[ident]['states'][key],geometry['profiles'].get(spec.get('ref'),spec)
    for key,spec in old_geometry['blocks'][wall]['states'].items():
        _require(geometry['blocks'][wall]['states'].get(key)==spec,'CANONICAL_WALL_PHYSICS_CHANGED')
    for connection,row in manifest['connections'].items():
        _require(row['owner'] in aliases,'WALL_ALIAS_NOT_REVIEWED')
        turn=sides.index(row['facing'])
        for n,facing in enumerate(sides):
            _require(signature(wall,'connection='+connection+',facing='+facing)==
                signature(row['owner'],'facing='+sides[(turn+n)%4]),'WALL_SUCCESSOR_ART_OR_PHYSICS_CHANGED')
    _require(set(manifest['aliasStates'])=={ident+'|facing='+f for ident in aliases for f in sides},'WALL_ALIAS_COVERAGE_CHANGED')
    for alias,target in manifest['aliasStates'].items():
        ident,key=alias.split('|')
        _require(signature(ident,key)==signature(wall,'connection='+target['connection']+',facing='+target['facing']),
            'WALL_ALIAS_SUCCESSOR_CHANGED')
    return {'addedOwners':len(added),'wallAliasStates':len(manifest['aliasStates'])}


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
    extensions = _reviewed_extensions(root, repo)
    return {"result": "PASS", "acceptedCheckpoint": ACCEPTED, "rc1PhysicsBaseline": RC1,
            "logicalResources": len(logical), "runtimeJava": len(java), "rc1City": rc1_city,
            "acceptedOwnerContracts": owner_contracts, "acceptedOwners": accepted_owners, "reviewedExtensions":extensions}


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
