#!/usr/bin/env python3
"""Export a deterministic, read-only current-art reference bundle.

The bundle contains every bundled block-model JSON and PNG/PNG.mcmeta, the
unpacked logical/city/owner meshes, retained registry definitions and geometry
alignment references.  It never writes game resources or invokes converters.
"""
from __future__ import annotations

import argparse
import csv
import gc
import gzip
import hashlib
import io
import json
import tempfile
from pathlib import Path

from export_alt_visual_starter import json_bytes, write_zip
from export_alt_artist_kit import export_artist
from blockbench_mesh_bridge import json_bytes as bbmodel_bytes, mesh_to_bbmodel

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def add(entries: dict[str, bytes], name: str, data: bytes) -> None:
    if name in entries and entries[name] != data:
        raise ValueError("conflicting export entry: " + name)
    entries[name] = data


def read_json(path: Path) -> object:
    return json.loads(path.read_text(encoding="utf-8"))


def unpack(path: Path) -> object:
    with gzip.open(path, "rt", encoding="utf-8") as stream:
        return json.load(stream)


def model_path(resources: Path, identifier: str) -> Path:
    namespace, separator, value = identifier.partition(":")
    if not separator:
        raise ValueError("invalid model identifier: " + identifier)
    return resources / "assets" / namespace / "models" / (value + ".json")


def texture_path(resources: Path, identifier: str) -> Path | None:
    namespace, separator, value = identifier.partition(":")
    if not separator:
        raise ValueError("invalid texture identifier: " + identifier)
    candidate = resources / "assets" / namespace / "textures" / (value + ".png")
    if candidate.is_file():
        return candidate
    return None if namespace == "minecraft" else candidate


def validate(definitions: list[dict], meshes: dict, resources: Path, label: str) -> dict:
    model_count = texture_count = 0
    referenced_meshes: set[str] = set()
    visual_models: set[str] = set()
    textures: set[str] = set()
    for definition in definitions:
        ident = definition["id"]
        for state, mesh_id in (definition.get("models") or {}).items():
            if mesh_id not in meshes:
                raise ValueError(f"{label} {ident}[{state}] missing mesh {mesh_id}")
            referenced_meshes.add(mesh_id)
        visual_models.update((definition.get("visual_models") or {}).values())
    for model in visual_models:
        if not model_path(resources, model).is_file():
            raise ValueError(f"{label} missing model {model}")
    model_count = len(visual_models)
    for mesh_id, mesh in meshes.items():
        for polygon in mesh.get("polygons", []):
            texture = polygon.get("texture")
            if not isinstance(texture, str):
                raise ValueError(f"{label} mesh {mesh_id} has invalid texture")
            texture_count += 1
            textures.add(texture)
    for texture in textures:
        candidate = texture_path(resources, texture)
        if candidate is not None and not candidate.is_file():
            raise ValueError(f"{label} missing texture {texture}")
    return {"families": len(definitions), "state_models": model_count, "referenced_meshes": len(referenced_meshes), "mesh_texture_links": texture_count, "unique_textures": len(textures)}


def runtime_entries(resources: Path) -> dict[str, bytes]:
    entries: dict[str, bytes] = {}
    assets = resources / "assets"
    for namespace in sorted(path for path in assets.iterdir() if path.is_dir()):
        for folder, suffixes in (("models", {".json"}), ("textures", {".png", ".png.mcmeta"})):
            root = namespace / folder
            if root.is_dir():
                for path in sorted(item for item in root.rglob("*") if item.is_file() and (path_suffix(item) in suffixes)):
                    add(entries, "runtime/" + path.relative_to(resources).as_posix(), path.read_bytes())
    return entries


def blockbench_entries(entries: dict[str, bytes], resources: Path, collections: dict[str, dict]) -> dict:
    """Add one generic Blockbench mesh per runtime custom mesh and shared PNGs.

    The generated documents deliberately contain only visual mesh data.  Their
    accompanying bridge manifest points back to collision/placement references
    instead of pretending that a Blockbench file is a gameplay definition.
    """
    texture_ids: set[str] = set()
    models = []
    for layer, meshes in collections.items():
        for mesh_id in sorted(meshes):
            document = mesh_to_bbmodel(mesh_id, meshes[mesh_id])
            model_path = f"blockbench/models/{layer}/{mesh_id}.bbmodel"
            # Paths are relative to a model two directories below blockbench/.
            for texture in document["textures"]:
                relative = "../../" + texture["relative_path"]
                texture["path"] = relative
                texture["relative_path"] = relative
            add(entries, model_path, bbmodel_bytes(document))
            identifiers = sorted({polygon["texture"] for polygon in meshes[mesh_id]["polygons"]})
            texture_ids.update(identifiers)
            models.append({"layer": layer, "mesh_id": mesh_id, "file": model_path, "textures": identifiers})
    missing = []
    for identifier in sorted(texture_ids):
        source = texture_path(resources, identifier)
        if source is None:
            missing.append(identifier)
            continue
        if not source.is_file():
            raise ValueError("Blockbench missing texture " + identifier)
        namespace, _, value = identifier.partition(":")
        add(entries, f"blockbench/textures/{namespace}/{value}.png", source.read_bytes())
    add(entries, "blockbench/mesh-manifest.json", json_bytes({"schema_version": 1, "position_scale": 16, "models": models, "unbundled_vanilla_textures": missing, "runtime_import": "tools/blockbench_mesh_bridge.py:bbmodel_to_mesh"}))
    return {"models": len(models), "textures": len(texture_ids) - len(missing), "unbundled_vanilla_textures": missing}


def path_suffix(path: Path) -> str:
    return ".png.mcmeta" if path.name.endswith(".png.mcmeta") else path.suffix


def export_bundle(resources: Path, output: Path, force: bool = False) -> dict:
    resources = resources.resolve()
    logical = resources / "bloodborne_blocks/logical"
    city = resources / "bloodborne_blocks/city"
    logical_definitions = read_json(logical / "definitions.json")["blocks"]
    city_definitions = read_json(city / "definitions.json")["blocks"]
    logical_meshes, city_meshes, owner_meshes = unpack(logical / "meshes.json.gz"), unpack(city / "meshes.json.gz"), unpack(city / "owner-meshes.json.gz")
    # City whole-owner mappings can reference either the shared city mesh set or owner meshes.
    combined_city_meshes = dict(city_meshes)
    combined_city_meshes.update(owner_meshes)
    logical_coverage = validate(logical_definitions, logical_meshes, resources, "logical")
    city_coverage = validate(city_definitions, combined_city_meshes, resources, "city")
    entries = runtime_entries(resources)
    blockbench_coverage = blockbench_entries(entries, resources, {"logical": logical_meshes, "city": city_meshes, "owner": owner_meshes})
    del combined_city_meshes, logical_meshes, city_meshes, owner_meshes
    gc.collect()
    reference_files = [
        logical / "definitions.json", logical / "contracts-v2.json", logical / "geometry.json", logical / "physical-footprints.json", logical / "transform-v2.json", logical / "visual-slots.json", logical / "production-palette.json",
        city / "definitions.json", city / "geometry.json", city / "owner-runtime-mappings.json", city / "reviewed-wall-family.json",
    ]
    included_references = []
    for path in reference_files:
        if not path.is_file():
            continue
        add(entries, "reference/" + path.relative_to(resources).as_posix(), path.read_bytes())
        included_references.append(path.relative_to(resources).as_posix())
    for source, destination in ((logical / "meshes.json.gz", "reference/bloodborne_blocks/logical/meshes.unpacked.json"),
                                (city / "meshes.json.gz", "reference/bloodborne_blocks/city/meshes.unpacked.json"),
                                (city / "owner-meshes.json.gz", "reference/bloodborne_blocks/city/owner-meshes.unpacked.json")):
        add(entries, destination, gzip.decompress(source.read_bytes()))
    with tempfile.TemporaryDirectory(prefix="bloodborne-art-export-") as temporary:
        temporary_path = Path(temporary)
        template = temporary_path / "ALT-ResourcePack-Template.zip"
        artist = temporary_path / "ALT-Art-Kit.zip"
        artist_summary = export_artist(resources, artist, template)
        add(entries, "artist-kit/ALT-ResourcePack-Template.zip", template.read_bytes())
        add(entries, "artist-kit/ALT-Art-Kit.zip", artist.read_bytes())
    owner_definitions = [definition for definition in city_definitions if definition.get("whole_owner")]
    canonical_wall = [definition for definition in city_definitions if definition["id"] == "building_stone_brick_wall"]
    if len(canonical_wall) != 1:
        raise ValueError("missing canonical building_stone_brick_wall definition")
    coverage = {
        "logical": logical_coverage,
        "city": city_coverage,
        "city_whole_owner_families": len(owner_definitions),
        "canonical_wall_families": len(canonical_wall),
        "artist_kit": artist_summary,
        "blockbench": blockbench_coverage,
        "runtime_assets": {"models": sum(1 for name in entries if "/models/" in name), "textures_or_metadata": sum(1 for name in entries if "/textures/" in name)},
        "reference_files": included_references,
    }
    manifest_rows = [{"path": name, "bytes": len(data), "sha256": digest(data)} for name, data in sorted(entries.items())]
    manifest = {"schema_version": 1, "purpose": "current runtime art reference; geometry is read-only alignment data", "coverage": coverage, "files": manifest_rows}
    add(entries, "manifest.json", json_bytes(manifest))
    csv_out = io.StringIO(newline="")
    writer = csv.DictWriter(csv_out, fieldnames=["path", "bytes", "sha256"])
    writer.writeheader(); writer.writerows(manifest_rows)
    add(entries, "SHA256SUMS.csv", csv_out.getvalue().encode("utf-8-sig"))
    add(entries, "README_RU.md", README.encode("utf-8"))
    write_zip(output.resolve(), entries, force)
    return {"output": str(output.resolve()), "files": len(entries), "coverage": coverage, "sha256": digest(output.read_bytes())}


README = """# Bloodborne Blocks — current art reference

Это снимок всех сохранённых logical/city/whole-owner/canonical-wall предметов, а не ресурс-пак для прямой установки. `runtime/assets/**` содержит точные текущие JSON-модели, PNG и PNG.mcmeta. В `reference/**` находятся definitions и распакованные meshes для чтения художником.

`blockbench/models/**` — редактируемые generic `.bbmodel` для каждого custom mesh, а `blockbench/textures/**` — их общие PNG. Мост сохраняет позиции (масштаб ×16) и UV без преобразования. `blockbench/mesh-manifest.json` связывает файл с mesh ID и перечисляет vanilla-текстуры, которые намеренно не копируются из Minecraft. Обратно в runtime-форму mesh документ читает `tools/blockbench_mesh_bridge.py:bbmodel_to_mesh`; collision, state/placement и owner mappings остаются в `reference/**` и не импортируются из Blockbench.

## Важно

`reference/**/geometry.json`, `physical-footprints.json`, `contracts-v2.json`, `transform-v2.json`, owner mappings и meshes — **только для выравнивания**. Не редактируйте их как игровой контент: они описывают состояние, placement, collision и offsets. Изменение встроенного art требует отдельного обновления runtime assets и повторной проверки, а не правки reference JSON.

`artist-kit/ALT-Art-Kit.zip` и `artist-kit/ALT-ResourcePack-Template.zip` собраны существующими экспортёрами для текущих 49 production family. ALT может выбраться NBT `BlockStateTag` с `visual=alt`; если BASE и ALT пока совпадают, это ожидаемо. После изменения встроенного art перегенерируйте creative-equivalence proof перед выпуском.

`manifest.json` и `SHA256SUMS.csv` перечисляют SHA-256 каждого payload-файла. Экспорт намеренно сохраняет технический art независимо от текущего использования в мире.
"""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path, help="new current-art ZIP")
    parser.add_argument("--resources", type=Path, default=RESOURCES)
    parser.add_argument("--force", action="store_true", help="explicitly replace output")
    args = parser.parse_args()
    print(json.dumps(export_bundle(args.resources, args.output, args.force), ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
