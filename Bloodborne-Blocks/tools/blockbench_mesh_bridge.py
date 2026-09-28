#!/usr/bin/env python3
"""Lossless bridge between Bloodborne polygon meshes and Blockbench .bbmodel.

The game keeps a mesh as independent polygons with positions in block units and
UVs in texture pixels.  Blockbench's generic ``free`` format can represent the
same data as mesh faces.  This module deliberately keeps the game format out
of the editor: it writes a documented bridge payload and can read it back.
"""
from __future__ import annotations

import hashlib
import json
import uuid
from pathlib import PurePosixPath

POSITION_SCALE = 16.0
SCHEMA_VERSION = 1
_NAMESPACE = uuid.UUID("f477d44c-6bda-5f58-b6d5-9ccb8d38ed4d")


def _uuid(*parts: str) -> str:
    return str(uuid.uuid5(_NAMESPACE, ":".join(parts)))


def _texture_file(identifier: str) -> str:
    namespace, sep, value = identifier.partition(":")
    if not sep or not namespace or not value:
        raise ValueError("invalid texture identifier: " + identifier)
    return (PurePosixPath("textures") / namespace / (value + ".png")).as_posix()


def mesh_to_bbmodel(mesh_id: str, mesh: dict) -> dict:
    """Create a deterministic generic Blockbench mesh document.

    Texture paths are relative to ``blockbench/`` in the exported bundle.
    They are intentionally not embedded so a bundle has one editable copy of
    each PNG instead of tens of thousands of base64 duplicates.
    """
    polygons = mesh.get("polygons")
    if not isinstance(polygons, list) or not polygons:
        raise ValueError(f"mesh {mesh_id} has no polygons")
    texture_ids = sorted({polygon.get("texture") for polygon in polygons})
    if not all(isinstance(texture, str) for texture in texture_ids):
        raise ValueError(f"mesh {mesh_id} has invalid texture")
    texture_uuid = {texture: _uuid(mesh_id, "texture", texture) for texture in texture_ids}
    vertices: dict[str, list[float]] = {}
    faces: dict[str, dict] = {}
    vertex_keys: dict[tuple[float, float, float], str] = {}
    for polygon_index, polygon in enumerate(polygons):
        runtime_vertices = polygon.get("vertices")
        if not isinstance(runtime_vertices, list) or len(runtime_vertices) < 3:
            raise ValueError(f"mesh {mesh_id} polygon {polygon_index} is not a face")
        face_vertices = []
        uv = {}
        for vertex_index, raw in enumerate(runtime_vertices):
            if not isinstance(raw, list) or len(raw) != 5 or not all(isinstance(value, (int, float)) for value in raw):
                raise ValueError(f"mesh {mesh_id} polygon {polygon_index} has invalid vertex")
            x, y, z, u, v = raw
            coordinate = (x, y, z)
            key = vertex_keys.get(coordinate)
            if key is None:
                key = f"v{len(vertex_keys)}"
                vertex_keys[coordinate] = key
                vertices[key] = [x * POSITION_SCALE, y * POSITION_SCALE, z * POSITION_SCALE]
            face_vertices.append(key)
            uv[key] = [u, v]
        faces[f"face{polygon_index}"] = {"vertices": face_vertices, "uv": uv, "texture": texture_uuid[polygon["texture"]]}
    element_uuid = _uuid(mesh_id, "mesh")
    return {
        "meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
        "name": mesh_id,
        "model_identifier": "bloodborne_blocks_mesh_bridge",
        "resolution": {"width": 16, "height": 16},
        "elements": [{"uuid": element_uuid, "type": "mesh", "name": mesh_id, "origin": [0, 0, 0], "rotation": [0, 0, 0], "vertices": vertices, "faces": faces}],
        "outliner": [element_uuid],
        "textures": [{"id": str(index), "uuid": texture_uuid[texture], "name": PurePosixPath(_texture_file(texture)).name, "path": _texture_file(texture), "relative_path": _texture_file(texture), "mode": "bitmap", "source": ""} for index, texture in enumerate(texture_ids)],
        "bloodborne_mesh_bridge": {"schema_version": SCHEMA_VERSION, "mesh_id": mesh_id, "position_scale": POSITION_SCALE, "texture_identifiers": {texture_uuid[texture]: texture for texture in texture_ids}, "runtime_sha256": mesh_digest(mesh)},
    }


def mesh_digest(mesh: dict) -> str:
    def normalize(value):
        if isinstance(value, dict):
            return {key: normalize(item) for key, item in value.items()}
        if isinstance(value, list):
            return [normalize(item) for item in value]
        if isinstance(value, float):
            rounded = round(value, 9)
            return int(rounded) if rounded.is_integer() else rounded
        return value
    return hashlib.sha256(json.dumps(normalize(mesh), ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")).hexdigest()


def bbmodel_to_mesh(document: dict) -> tuple[str, dict]:
    """Read an exported/editable bridge document into the runtime mesh shape.

    The bridge metadata identifies the original mesh but geometry is rebuilt
    from the Blockbench mesh, so a controlled editor change is observable.
    """
    bridge = document.get("bloodborne_mesh_bridge")
    if not isinstance(bridge, dict) or bridge.get("schema_version") != SCHEMA_VERSION:
        raise ValueError("not a Bloodborne Blockbench bridge document")
    mesh_id = bridge.get("mesh_id")
    scale = bridge.get("position_scale")
    if not isinstance(mesh_id, str) or not isinstance(scale, (int, float)) or scale == 0:
        raise ValueError("invalid bridge metadata")
    texture_by_uuid = {texture.get("uuid"): texture.get("relative_path") or texture.get("path") for texture in document.get("textures", []) if isinstance(texture, dict)}
    texture_id_by_uuid = dict(bridge.get("texture_identifiers") or {})
    for texture_uuid, path in texture_by_uuid.items():
        parts = PurePosixPath(path).parts
        if len(parts) >= 3 and parts[0] == "textures" and parts[-1].endswith(".png"):
            texture_id_by_uuid[texture_uuid] = parts[1] + ":" + "/".join(parts[2:])[:-4]
    polygons = []
    for element in document.get("elements", []):
        if not isinstance(element, dict) or element.get("type") != "mesh":
            continue
        vertices = element.get("vertices", {})
        for face in element.get("faces", {}).values():
            if not isinstance(face, dict):
                continue
            texture = texture_id_by_uuid.get(face.get("texture"))
            keys, uv = face.get("vertices"), face.get("uv")
            if texture is None or not isinstance(keys, list) or not isinstance(uv, dict) or len(keys) < 3:
                raise ValueError("edited Blockbench face is incomplete")
            result_vertices = []
            for key in keys:
                point, point_uv = vertices.get(key), uv.get(key)
                if not isinstance(point, list) or len(point) != 3 or not isinstance(point_uv, list) or len(point_uv) != 2:
                    raise ValueError("edited Blockbench vertex is incomplete")
                result_vertices.append([point[0] / scale, point[1] / scale, point[2] / scale, point_uv[0], point_uv[1]])
            polygons.append({"texture": texture, "vertices": result_vertices})
    if not polygons:
        raise ValueError("bridge document has no mesh faces")
    return mesh_id, {"polygons": polygons}


def json_bytes(document: dict) -> bytes:
    return (json.dumps(document, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")
