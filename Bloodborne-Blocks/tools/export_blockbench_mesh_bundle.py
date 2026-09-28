#!/usr/bin/env python3
"""Stream the complete editable Blockbench representation of custom meshes."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import tempfile
import zipfile
import subprocess
from collections import defaultdict
from pathlib import Path

from blockbench_mesh_bridge import json_bytes, mesh_to_bbmodel

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
STAMP = (1980, 1, 1, 0, 0, 0)


def add_zip(archive: zipfile.ZipFile, name: str, data: bytes) -> dict:
    info = zipfile.ZipInfo(name, STAMP)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o100644 << 16
    archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    return {"path": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}


def texture_path(resources: Path, identifier: str) -> Path | None:
    namespace, separator, value = identifier.partition(":")
    if not separator:
        raise ValueError("invalid texture identifier: " + identifier)
    path = resources / "assets" / namespace / "textures" / (value + ".png")
    return path if path.is_file() else None


def iter_meshes(path: Path):
    """Decode a gzip JSON object one mesh at a time (city is too large for json.load)."""
    decoder, buffer, offset, done = json.JSONDecoder(), "", 0, False
    with gzip.open(path, "rt", encoding="utf-8") as stream:
        def more():
            nonlocal buffer, done
            piece = stream.read(1024 * 1024)
            buffer += piece
            done = not piece
        def token():
            nonlocal offset
            whitespace()
            while True:
                try:
                    return decoder.raw_decode(buffer, offset)
                except json.JSONDecodeError:
                    if done: raise
                    more()
        def whitespace():
            nonlocal offset
            while True:
                while offset < len(buffer) and buffer[offset].isspace():
                    offset += 1
                if offset < len(buffer):
                    return
                if done:
                    raise ValueError('truncated mesh JSON')
                more()
        more()
        whitespace()
        if buffer[offset] != '{':
            raise ValueError('mesh file is not a JSON object')
        offset += 1
        seen = set()
        while True:
            whitespace()
            if buffer[offset] == "}":
                if seen:
                    raise ValueError('trailing comma in mesh JSON')
                break
            mesh_id, offset = token()
            if not isinstance(mesh_id, str) or mesh_id in seen:
                raise ValueError('invalid or duplicate mesh ID')
            seen.add(mesh_id)
            whitespace()
            if buffer[offset] != ":": raise ValueError("invalid mesh map")
            offset += 1
            mesh, offset = token()
            yield mesh_id, mesh
            buffer, offset = buffer[offset:], 0
            whitespace()
            if buffer[offset] == ",": offset += 1; continue
            if buffer[offset] == "}": break
            raise ValueError("invalid mesh separator")
        if (buffer[offset + 1:] + stream.read()).strip():
            raise ValueError('trailing data after mesh JSON')


def export(resources: Path, output: Path, force: bool = False) -> dict:
    if output.exists() and not force:
        raise ValueError(f"output already exists: {output} (pass --force to replace it)")
    resources = resources.resolve()
    sources = {"logical": resources / "bloodborne_blocks/logical/meshes.json.gz", "city": resources / "bloodborne_blocks/city/meshes.json.gz", "owner": resources / "bloodborne_blocks/city/owner-meshes.json.gz"}
    uses = defaultdict(list)
    for kind in ('logical', 'city'):
        definition_path = resources / f'bloodborne_blocks/{kind}/definitions.json'
        for definition in json.loads(definition_path.read_bytes())['blocks']:
            for state, mesh_id in (definition.get('models') or {}).items():
                layer = 'owner' if kind == 'city' and mesh_id.startswith('owner_') else kind
                uses[(layer, mesh_id)].append({'family': definition['id'], 'state': state,
                    'visual_model': (definition.get('visual_models') or {}).get(state),
                    'definition': definition_path.relative_to(resources).as_posix()})
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, prefix=output.name + ".", suffix=".tmp", delete=False) as handle:
        temporary = Path(handle.name)
    manifest_rows, texture_ids = [], set()
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for layer, source in sources.items():
                for mesh_id, mesh in iter_meshes(source):
                    document = mesh_to_bbmodel(mesh_id, mesh)
                    for texture in document["textures"]:
                        texture["path"] = "../../" + texture["relative_path"]
                        texture["relative_path"] = texture["path"]
                    name = f"models/{layer}/{mesh_id}.bbmodel"
                    row = add_zip(archive, name, json_bytes(document))
                    row.update({'mesh_id': mesh_id, 'runtime': source.relative_to(resources).as_posix(),
                                'uses': uses.get((layer, mesh_id), []), 'bridge': name.replace('.bbmodel', '.bridge.json')})
                    manifest_rows.append(row)
                    manifest_rows.append(add_zip(archive, name.replace('.bbmodel', '.bridge.json'), json_bytes(document['bloodborne_mesh_bridge'])))
                    texture_ids.update(polygon["texture"] for polygon in mesh["polygons"])
            missing = []
            for identifier in sorted(texture_ids):
                path = texture_path(resources, identifier)
                if path is None:
                    if not identifier.startswith('minecraft:'):
                        raise ValueError('missing bundled texture: ' + identifier)
                    missing.append(identifier)
                    continue
                namespace, _, value = identifier.partition(":")
                manifest_rows.append(add_zip(archive, f"textures/{namespace}/{value}.png", path.read_bytes()))
                metadata = path.with_suffix('.png.mcmeta')
                if metadata.is_file():
                    manifest_rows.append(add_zip(archive, f"textures/{namespace}/{value}.png.mcmeta", metadata.read_bytes()))
            for tool in ('blockbench_mesh_bridge.py',):
                manifest_rows.append(add_zip(archive, 'tools/' + tool, (ROOT / 'tools' / tool).read_bytes()))
            for relative in ('ASSET-NOTICE.md', 'docs/whole-models-handoff/BLOCKBENCH.md'):
                path = ROOT / relative
                if path.is_file():
                    manifest_rows.append(add_zip(archive, path.name, path.read_bytes()))
            commit = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=ROOT, check=True, capture_output=True, text=True).stdout.strip()
            dirty = bool(subprocess.run(['git', 'status', '--porcelain'], cwd=ROOT, check=True, capture_output=True, text=True).stdout.strip())
            manifest_rows.append(add_zip(archive, "mesh-manifest.json", json_bytes({"schema_version": 2,
                "source_commit": commit, "source_dirty": dirty, "position_scale": 16, "files": manifest_rows[:],
                "sources": {layer: {'path': path.relative_to(resources).as_posix(), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()} for layer,path in sources.items()},
                "unbundled_vanilla_textures": missing, "runtime_import": "tools/blockbench_mesh_bridge.py", "editor_qa": "See BLOCKBENCH.md; export alone is not client acceptance"})))
        temporary.replace(output)
    finally:
        if temporary.exists():
            temporary.unlink()
    return {"output": str(output), "files": len(manifest_rows), "sha256": hashlib.sha256(output.read_bytes()).hexdigest()}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--resources", type=Path, default=RESOURCES)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()
    print(json.dumps(export(args.resources, args.output, args.force), ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
