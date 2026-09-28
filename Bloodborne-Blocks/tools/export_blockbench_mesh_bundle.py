#!/usr/bin/env python3
"""Stream the complete editable Blockbench representation of custom meshes."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import tempfile
import zipfile
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
            while True:
                try:
                    return decoder.raw_decode(buffer, offset)
                except json.JSONDecodeError:
                    if done: raise
                    more()
        more()
        while True:
            while offset >= len(buffer) or buffer[offset].isspace():
                if offset >= len(buffer):
                    if done: return
                    more()
                else: offset += 1
            if buffer[offset] == "{": offset += 1; break
            raise ValueError("mesh file is not a JSON object")
        while True:
            while offset >= len(buffer) or buffer[offset].isspace():
                if offset >= len(buffer): more()
                else: offset += 1
            if buffer[offset] == "}": return
            mesh_id, offset = token()
            while offset >= len(buffer): more()
            if buffer[offset] != ":": raise ValueError("invalid mesh map")
            offset += 1
            mesh, offset = token()
            yield mesh_id, mesh
            buffer, offset = buffer[offset:], 0
            while offset >= len(buffer) or buffer[offset].isspace():
                if offset >= len(buffer): more()
                else: offset += 1
            if buffer[offset] == ",": offset += 1; continue
            if buffer[offset] == "}": return
            raise ValueError("invalid mesh separator")


def export(resources: Path, output: Path, force: bool = False) -> dict:
    if output.exists() and not force:
        raise ValueError(f"output already exists: {output} (pass --force to replace it)")
    resources = resources.resolve()
    sources = {"logical": resources / "bloodborne_blocks/logical/meshes.json.gz", "city": resources / "bloodborne_blocks/city/meshes.json.gz", "owner": resources / "bloodborne_blocks/city/owner-meshes.json.gz"}
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
                    manifest_rows.append(add_zip(archive, name, json_bytes(document)))
                    texture_ids.update(polygon["texture"] for polygon in mesh["polygons"])
            missing = []
            for identifier in sorted(texture_ids):
                path = texture_path(resources, identifier)
                if path is None:
                    missing.append(identifier)
                    continue
                namespace, _, value = identifier.partition(":")
                manifest_rows.append(add_zip(archive, f"textures/{namespace}/{value}.png", path.read_bytes()))
            manifest_rows.append(add_zip(archive, "mesh-manifest.json", json_bytes({"schema_version": 1, "position_scale": 16, "models": manifest_rows[:], "unbundled_vanilla_textures": missing, "runtime_import": "tools/blockbench_mesh_bridge.py:bbmodel_to_mesh"})))
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
