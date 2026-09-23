#!/usr/bin/env python3
"""Export editable ALT logical visuals as a deterministic Minecraft 1.20.1 pack.

The exporter is deliberately read-only with respect to generated mod resources.
Run it only after ``build_visual_slots.py`` has produced ``visual_models``.
"""
from __future__ import annotations

import argparse
import gzip
import json
import math
import tempfile
import zipfile
from pathlib import Path

PACK_FORMAT = 15
STAMP = (1980, 1, 1, 0, 0, 0)


def state_stem(model: str) -> str:
    parts = model.split("/")
    if len(parts) < 2 or parts[-2] != "alt" or not parts[-1]:
        raise ValueError(f"visual model is not an ALT logical path: {model}")
    return parts[-1]


def texture_file(resources: Path, texture: str) -> Path | None:
    namespace, separator, path = texture.partition(":")
    if not separator or not namespace or not path:
        raise ValueError(f"invalid texture identifier: {texture}")
    candidate = resources / "assets" / namespace / "textures" / (path + ".png")
    if candidate.is_file():
        return candidate
    if namespace == "minecraft":
        return None  # Explicitly recorded as a stock vanilla dependency.
    raise ValueError(f"missing non-vanilla texture dependency: {texture}")


def model_file(resources: Path, model: str) -> Path:
    namespace, separator, path = model.partition(":")
    if not separator or not namespace or not path:
        raise ValueError(f"invalid model identifier: {model}")
    return resources / "assets" / namespace / "models" / (path + ".json")


def json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")


def read_json(path: Path) -> object:
    return json.loads(path.read_text(encoding="utf-8"))


def add_file(entries: dict[str, bytes], name: str, value: bytes) -> None:
    if name in entries and entries[name] != value:
        raise ValueError(f"conflicting output entry: {name}")
    entries[name] = value


def validate_entries(entries: dict[str, bytes]) -> None:
    if any("/base/" in name for name in entries):
        raise ValueError("ALT starter pack must not contain BASE paths")
    for name, value in entries.items():
        if not name.endswith(".json"):
            continue
        parsed = json.loads(value.decode("utf-8"))
        if isinstance(parsed, dict) and "/base/" in str(parsed):
            raise ValueError(f"BASE model reference in {name}")
        if name.startswith("assets/") and "/models/" in name and isinstance(parsed, dict):
            textures = parsed.get("textures", {})
            if not isinstance(textures, dict):
                raise ValueError(f"invalid textures mapping in {name}")
            for value in textures.values():
                if not isinstance(value, str):
                    raise ValueError(f"invalid texture reference in {name}")
                if value.startswith('#'):continue
                namespace,path=value.split(':',1)
                if namespace!='minecraft' and f'assets/{namespace}/textures/{path}.png' not in entries:
                    raise ValueError(f'missing exported texture {value} in {name}')


def write_zip(output: Path, entries: dict[str, bytes], force: bool) -> None:
    if output.exists() and not force:
        raise ValueError(f"output already exists: {output} (pass --force to replace it)")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, prefix=output.name + ".", suffix=".tmp", delete=False) as handle:
        temporary = Path(handle.name)
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name in sorted(entries):
                info = zipfile.ZipInfo(name, STAMP)
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                archive.writestr(info, entries[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
        temporary.replace(output)
    finally:
        if temporary.exists():
            temporary.unlink()


def export(resources: Path, output: Path, *, force: bool = False) -> dict:
    resources = resources.resolve()
    logical = resources / "bloodborne_blocks" / "logical"
    definitions = read_json(logical / "definitions.json")
    contracts = read_json(logical / "contracts-v2.json")
    geometry = read_json(logical / "geometry.json")
    with gzip.open(logical / "meshes.json.gz", "rt", encoding="utf-8") as stream:
        meshes = json.load(stream)
    families = {family["id"]: family for family in contracts["families"]}
    entries: dict[str, bytes] = {}
    manifest = {"format": 1, "pack_format": PACK_FORMAT, "families": {}, "vanilla_textures": []}
    vanilla: set[str] = set()

    for definition in sorted(definitions["blocks"], key=lambda item: item["id"]):
        ident = definition["id"]
        visual_models = definition.get("visual_models", {})
        if not visual_models:
            continue
        family = families.get(ident)
        shapes={state:geometry.get('profiles',{}).get(value.get('ref'),value)
                for state,value in geometry['blocks'][ident]['states'].items()}
        family_manifest = {"canonical_anchor": family["canonical_anchor"] if family else
            {'legacy_placement_anchors_by_state':{state:value.get('anchor',[0,0,0]) for state,value in shapes.items()},
             'render_origin':'master block origin; geometry is already oriented'},
            "contract": {key:family[key] for key in ('collision_policy','placement_policy','mirror_policy')} if family else
            {'schema':'legacy authored logical geometry; not newly reviewed Contract V2'},
            "geometry": shapes, "states": {}}
        for full_state, model in sorted(visual_models.items()):
            if "/alt/" not in model:
                continue
            mesh_id = definition.get("models", {}).get(full_state)
            if not isinstance(mesh_id, str) or mesh_id not in meshes:
                raise ValueError(f"missing mesh for {ident}[{full_state}]")
            stem = state_stem(model)
            polygons = meshes[mesh_id].get("polygons")
            if not isinstance(polygons, list):
                raise ValueError(f"invalid mesh polygons: {mesh_id}")
            textures = sorted({polygon["texture"] for polygon in polygons})
            slots = {texture: f"t{index}" for index, texture in enumerate(textures)}
            model_path = f"assets/bloodborne_blocks/models/block/logical/{ident}/alt/{stem}.json"
            custom_polygons = []
            for polygon in polygons:
                texture = polygon.get("texture")
                vertices = polygon.get("vertices")
                if texture not in slots or not isinstance(vertices, list) or len(vertices) < 3:
                    raise ValueError(f"invalid polygon in {mesh_id}")
                converted = []
                for vertex in vertices:
                    if not isinstance(vertex, list) or len(vertex) != 5 or not all(isinstance(value, (int, float)) and math.isfinite(value) for value in vertex):
                        raise ValueError(f"invalid vertex in {mesh_id}")
                    converted.append([float(value) for value in vertex])
                custom_polygons.append({"texture": "#" + slots[texture], "vertices": converted})
            textures_json = {"particle": textures[0] if textures else "minecraft:block/stone"}
            files = []
            for texture, slot in slots.items():
                texture_stem=stem if len(slots)==1 else stem+'__'+slot
                destination_id = f"bloodborne_blocks:block/logical_alt/{ident}/{texture_stem}"
                source = texture_file(resources, texture)
                if source is None:
                    # A stock Minecraft texture is available from the game;
                    # never point its slot at a PNG this pack does not contain.
                    textures_json[slot] = texture
                    vanilla.add(texture)
                    continue
                textures_json[slot] = destination_id
                destination = f"assets/bloodborne_blocks/textures/block/logical_alt/{ident}/{texture_stem}.png"
                add_file(entries, destination, source.read_bytes())
                animation=source.with_suffix('.png.mcmeta')
                if animation.is_file():
                    add_file(entries,destination+'.mcmeta',animation.read_bytes())
                    files.append(destination+'.mcmeta')
                files.append(destination)
            if textures:textures_json['particle']='#'+slots[textures[0]]
            model_json = {"parent": "minecraft:block/block", "bloodborne_polygons": custom_polygons,
                          "textures": textures_json}
            existing = model_file(resources, model)
            if existing.is_file():
                display = read_json(existing).get("display")
                if display is not None:
                    model_json["display"] = display
            add_file(entries, model_path, json_bytes(model_json))
            family_manifest["states"][full_state] = {"model": model,
                "mesh": mesh_id, "texture_slots": {slot:{'source':texture,'resource':textures_json[slot],
                    'file':None if textures_json[slot].startswith('minecraft:') else
                    'assets/bloodborne_blocks/textures/'+textures_json[slot].split(':',1)[1]+'.png'} for texture,slot in slots.items()},
                "files": [model_path, *files]}
        if family_manifest["states"]:
            manifest["families"][ident] = family_manifest
    manifest["vanilla_textures"] = sorted(vanilla)
    add_file(entries, "pack.mcmeta", json_bytes({"pack": {"pack_format": PACK_FORMAT,
             "description": "Bloodborne logical ALT visual starter pack"}}))
    add_file(entries, "manifest.json", json_bytes(manifest))
    add_file(entries, "README.md", README.encode("utf-8"))
    validate_entries(entries)
    write_zip(output.resolve(), entries, force)
    return manifest


README = """# Bloodborne logical ALT visual starter\n\nUse this as a Minecraft 1.20.1 resource pack (`pack_format: 15`). Install the ZIP above the mod's bundled resources.\n\nEvery exported ALT model contains editable `bloodborne_polygons`; vertices are `[x, y, z, u, v]` in block coordinates and UV 0..16. Texture slot names (`#t0`, `#t1`, …) map to copied PNGs under `textures/block/logical_alt/`.\n\nTo use standard vanilla model elements instead, remove `bloodborne_polygons` and add normal vanilla `elements` to the model JSON. Normal vanilla elements override the custom polygon extension when present. Do not add BASE model paths: this pack intentionally contains ALT-only models.\n"""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path, help="new resource-pack ZIP")
    parser.add_argument("--resources", type=Path, default=Path(__file__).resolve().parents[1] / "src/main/resources")
    parser.add_argument("--force", action="store_true", help="explicitly replace an existing output ZIP")
    args = parser.parse_args()
    result = export(args.resources, args.output, force=args.force)
    print(json.dumps({"families": len(result["families"]), "vanilla_textures": len(result["vanilla_textures"])}, sort_keys=True))


if __name__ == "__main__":
    main()
