"""Deterministic, offline PNG evidence for logical-family manual review.

Assembled views use the current complete logical mesh. Exploded views use
numbered authored source applications. This is a visual aid, not game QA.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
from typing import Any, Iterable

# catalog_geometry historically has a developer-local fallback.  Set the
# portable, installed Loom location before importing it, while allowing CI or a
# caller to provide its own jar.
os.environ.setdefault(
    "BLOODBORNE_VANILLA_JAR",
    str(Path.home() / '.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'),
)

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from catalog_geometry import ROOT, RES, rotation
from build_logical_objects import object_polys
import build_logical_objects as logical_builder
import render_modular_preview as modular_preview


BACKGROUND = (27, 30, 36)
RENDERER_VERSION = 4
logical_builder.ORIGINAL_PACK = ROOT / 'reference-inputs/source-resource-pack.zip'
_input_hashes = None


def input_hashes():
    global _input_hashes
    if _input_hashes is None:
        def file_hash(path):
            result = hashlib.sha256()
            with Path(path).open('rb') as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b''): result.update(block)
            return result.hexdigest()
        _input_hashes = {'pack': file_hash(logical_builder.ORIGINAL_PACK),
                         'vanilla': file_hash(os.environ['BLOODBORNE_VANILLA_JAR'])}
        if _input_hashes['pack'] != '0f2c3d64a1d60734ae0786d128b522ea6bbd175f26f5d164bed5522c46898308':
            raise ValueError('Unexpected source resource pack hash')
    return _input_hashes


def _font(size: int) -> ImageFont.ImageFont:
    path = Path(r"C:\Windows\Fonts\arial.ttf")
    return ImageFont.truetype(str(path), size) if path.is_file() else ImageFont.load_default()


def _safe_name(value: str) -> str:
    return "".join(char if char.isalnum() or char in "-_" else "_" for char in value)


def _resource_hash(record: dict) -> str:
    """Hash the checked-in source model and texture bytes used by exploded cards."""
    wanted: set[Path] = set()
    seen: set[str] = set()

    def model_files(name: str) -> None:
        if name in seen:
            return
        seen.add(name)
        namespace, local = name.split(":", 1) if ":" in name else ("minecraft", name)
        path = RES / "assets" / namespace / "models" / f"{local}.json"
        if not path.is_file():
            # Vanilla parents are not curation inputs; source apps must still
            # be rendered by the established geometry helper.
            return
        wanted.add(path)
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        if isinstance(data.get("parent"), str):
            model_files(data["parent"])
        for texture in data.get("textures", {}).values():
            if not isinstance(texture, str) or texture.startswith("#"):
                continue
            ns, local_texture = texture.split(":", 1) if ":" in texture else (namespace, texture)
            texture_path = RES / "assets" / ns / "textures" / f"{local_texture}.png"
            if texture_path.is_file():
                wanted.add(texture_path)

    for component in record.get("components", []):
        app = component.get("app", {}) if isinstance(component, dict) else {}
        if isinstance(app, dict) and isinstance(app.get("model"), str):
            model_files(app["model"])
    digest = hashlib.sha256()
    for path in sorted(wanted):
        digest.update(str(path.relative_to(ROOT)).replace("\\", "/").encode())
        digest.update(path.read_bytes())
    return digest.hexdigest()


def _json_hash(record: dict, meshes: dict) -> str:
    preview = meshes.get(record.get("preview_mesh"), {})
    payload = {"renderer": RENDERER_VERSION, "record": record, "preview": preview,
               "source_resources": _resource_hash(record), 'original_inputs': input_hashes()}
    raw = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def _bounds(polys: list[dict]) -> list[list[float]] | None:
    if not polys:
        return None
    points = np.concatenate([np.asarray(poly["vertices"], float)[:, :3] for poly in polys])
    return [points.min(axis=0).round(5).tolist(), points.max(axis=0).round(5).tolist()]


def _dimensions(bounds: list[list[float]] | None) -> list[float] | None:
    if bounds is None:
        return None
    return (np.asarray(bounds[1]) - np.asarray(bounds[0])).round(5).tolist()


def _render(polys: list[dict], camera: np.ndarray, size: int) -> Image.Image:
    """Reuse the checked-in textured software rasterizer with a fixed camera."""
    old_camera = modular_preview.CAM
    try:
        modular_preview.CAM = camera
        return modular_preview.draw_polys(polys, size=size)
    finally:
        modular_preview.CAM = old_camera


def _camera(yaw: float) -> np.ndarray:
    return rotation(0, 20) @ rotation(1, yaw)


def _label(image: Image.Image, text: str, *, number: int | None = None) -> Image.Image:
    canvas = Image.new("RGB", (image.width, image.height + 30), BACKGROUND)
    canvas.paste(image.convert("RGB"), (0, 0))
    draw = ImageDraw.Draw(canvas)
    if number is not None:
        draw.rounded_rectangle((6, image.height + 4, 31, image.height + 27), radius=5, fill=(220, 161, 47))
        draw.text((14, image.height + 6), str(number), font=_font(16), fill=(20, 20, 20))
        draw.text((38, image.height + 8), text, font=_font(13), fill="white")
    else:
        draw.text((7, image.height + 8), text, font=_font(14), fill="white")
    return canvas


def _wrapped_caption(image: Image.Image, review_id: str, name_ru: str) -> Image.Image:
    """Use the whole 240px cell for a readable Russian curator caption."""
    words, lines, current = name_ru.split(), [], ""
    draw = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    font = _font(14)
    for word in words:
        proposal = (current + " " + word).strip()
        if current and draw.textlength(proposal, font=font) > image.width - 12:
            lines.append(current)
            current = word
        else:
            current = proposal
    lines.append(current)
    lines = lines[:2]
    canvas = Image.new("RGB", (image.width, image.height + 22 + 18 * len(lines)), BACKGROUND)
    canvas.paste(image, (0, 0))
    draw = ImageDraw.Draw(canvas)
    draw.text((6, image.height + 4), review_id, font=_font(16), fill=(235, 181, 60))
    for index, line in enumerate(lines):
        draw.text((6, image.height + 23 + index * 18), line, font=font, fill="white")
    return canvas


def _paste_grid(images: list[Image.Image], columns: int, title: str) -> Image.Image:
    columns = max(1, columns)
    cell_w, cell_h = max(image.width for image in images), max(image.height for image in images)
    rows = (len(images) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * cell_w, 34 + rows * cell_h), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    draw.text((8, 8), title, font=_font(18), fill="white")
    for index, image in enumerate(images):
        x, y = (index % columns) * cell_w, 34 + (index // columns) * cell_h
        sheet.paste(image, (x + (cell_w - image.width) // 2, y))
    return sheet


def _component_data(record: dict) -> list[tuple[dict, list[dict]]]:
    components = record.get("components", [])
    if not isinstance(components, list) or not components:
        raise ValueError("record.components must be a non-empty list")
    numbers = [component.get("number") for component in components]
    if any(not isinstance(number, int) or number < 1 for number in numbers) or len(numbers) != len(set(numbers)):
        raise ValueError("components require unique positive stable numbers")
    result = []
    for component in sorted(components, key=lambda value: value["number"]):
        app = component.get("app")
        if not isinstance(app, dict) or not isinstance(app.get("model"), str):
            raise ValueError(f"component {component['number']} needs app.model")
        polys, _boxes = object_polys([app])
        result.append((component, polys))
    return result


def render_family(record: dict, meshes: dict, output_dir: Path | str) -> dict[str, Any]:
    """Render final preview mesh plus individually reconstructed source apps."""
    for required in ("review_id", "logical_id", "name_ru", "preview_state", "preview_mesh"):
        if not isinstance(record.get(required), str) or not record[required]:
            raise ValueError(f"record.{required} must be a non-empty string")
    if record["preview_mesh"] not in meshes:
        raise ValueError(f"preview mesh is absent: {record['preview_mesh']}")
    output = Path(output_dir).resolve()
    output.mkdir(parents=True, exist_ok=True)
    stem = f"{_safe_name(record['review_id'])}-{_safe_name(record['logical_id'])}"
    metadata_path = output / f"{stem}.json"
    digest = _json_hash(record, meshes)
    if metadata_path.is_file():
        cached = json.loads(metadata_path.read_text(encoding="utf-8"))
        if cached.get("input_hash") == digest and all(Path(path).is_file() and Path(path).resolve().parent == output for path in cached.get("paths", {}).values()):
            return cached

    components = _component_data(record)
    reconstructed = [poly for _component, polys in components for poly in polys]
    assembled = meshes[record["preview_mesh"]]["polygons"]
    angles = (-25, -145, 95)
    assembled_views = [_label(_render(assembled, _camera(angle), 330), f"Сборка: вид {index + 1}")
                       for index, angle in enumerate(angles)]
    assembled_sheet = _paste_grid(assembled_views, 3, f"{record['review_id']}  {record['name_ru']}")
    assembled_path = output / f"{stem}-assembled.png"
    assembled_sheet.save(assembled_path)

    # Components are independently framed: no overlap can hide a small member.
    cards = []
    for component, polys in components:
        sources = component.get("source_blocks", [])
        source_note = f"{len(sources)} исходн. блок." if isinstance(sources, list) else ""
        cards.append(_label(_render(polys, _camera(-25), 240), source_note, number=component["number"]))
    exploded_sheet = _paste_grid(cards, min(4, len(cards)), "Разобранные компоненты — номера как в манифесте")
    exploded_path = output / f"{stem}-exploded.png"
    exploded_sheet.save(exploded_path)

    thumbnail_path = output / f"{stem}-thumb.png"
    _render(assembled, _camera(-25), 240).convert("RGB").save(thumbnail_path)
    source_block_count = sum(len(component.get("source_blocks", [])) for component, _polys in components
                             if isinstance(component.get("source_blocks", []), list))
    bounds = _bounds(assembled)
    reconstructed_bounds = _bounds(reconstructed)
    metadata = {
        "review_id": record["review_id"], "logical_id": record["logical_id"], "preview_state": record["preview_state"],
        "name_ru": record["name_ru"],
        "input_hash": digest, "bounds": bounds, "dimensions": _dimensions(bounds),
        "reconstructed_bounds": reconstructed_bounds,
        "reconstructed_dimensions": _dimensions(reconstructed_bounds),
        "render_dimensions": [assembled_sheet.width, assembled_sheet.height],
        "component_model_count": len(components), "component_block_count": source_block_count,
        "paths": {"assembled": str(assembled_path), "exploded": str(exploded_path), "thumbnail": str(thumbnail_path)},
    }
    metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return metadata


def render_contact_sheet(metadata: Iterable[dict], output_path: Path | str, columns: int = 5) -> Path:
    """Build a deterministic thumbnail contact sheet for a rendered batch."""
    rows = list(metadata)
    thumbs = []
    for row in rows:
        image = Image.open(row["paths"]["thumbnail"]).convert("RGB")
        thumbs.append(_wrapped_caption(image, row["review_id"], row.get("name_ru", "")))
    if not thumbs:
        raise ValueError("contact sheet needs at least one rendered family")
    path = Path(output_path).resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    _paste_grid(thumbs, columns, "Ручная проверка logical families").save(path)
    return path
