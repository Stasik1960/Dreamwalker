"""Read-only, reproducible structural audit for the Bloodborne Blocks palette.

It deliberately does not require a Minecraft jar: ``minecraft:`` references are
reported as external contracts, while missing references in this repository are
errors.  The report is evidence for a later registry/migration redesign, not a
generator and it never changes resources or worlds.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/bloodborne_blocks"
NS = "bloodborne_blocks"


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def dump(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def qualified(ref: str) -> tuple[str, str]:
    return tuple(ref.split(":", 1)) if ":" in ref else (NS, ref)


def model_path(ref: str) -> Path | None:
    namespace, local = qualified(ref)
    return ASSETS / "models" / f"{local}.json" if namespace == NS else None


def texture_path(ref: str) -> Path | None:
    namespace, local = qualified(ref)
    return ASSETS / "textures" / f"{local}.png" if namespace == NS else None


def state_apps(blockstate: dict) -> list[dict]:
    """All direct model applications, including every multipart alternative."""
    found = []
    for value in blockstate.get("variants", {}).values():
        found.extend(value if isinstance(value, list) else [value])
    for part in blockstate.get("multipart", []):
        value = part.get("apply", {})
        found.extend(value if isinstance(value, list) else [value])
    return [app for app in found if isinstance(app, dict) and "model" in app]


def direct_fingerprint(data: dict) -> str:
    # Names and display metadata do not identify a separate mesh family.
    material = {k: v for k, v in data.items() if k not in {"parent", "display", "gui_light"}}
    return hashlib.sha256(json.dumps(material, sort_keys=True, separators=(",", ":")).encode()).hexdigest()[:16]


def model_flags(data: dict) -> list[dict]:
    flags = []
    for index, element in enumerate(data.get("elements", [])):
        low, high = element.get("from"), element.get("to")
        if not (isinstance(low, list) and isinstance(high, list) and len(low) == len(high) == 3):
            flags.append({"element": index, "flag": "invalid_bounds"})
            continue
        zero = [axis for axis in range(3) if abs(float(high[axis]) - float(low[axis])) < 1e-8]
        if zero:
            flags.append({"element": index, "flag": "planar_element", "axes": zero})
        if any(float(high[a]) < float(low[a]) for a in range(3)):
            flags.append({"element": index, "flag": "inverted_bounds"})
        for side, face in element.get("faces", {}).items():
            uv = face.get("uv")
            if uv and (len(uv) != 4 or any(not isinstance(value, (int, float)) or value < 0 or value > 16 for value in uv)):
                flags.append({"element": index, "face": side, "flag": "uv_outside_0_16"})
            # This is only suspicious.  Non-boundary cull faces can be authored
            # intentionally, so the audit never declares it a defect.
            if "cullface" in face:
                flags.append({"element": index, "face": side, "flag": "cullface_review"})
    return flags


def original_sprite_regressions(original: Path | None, models: dict[str, dict]) -> list[dict]:
    """Evidence for extrusion artifacts: compare authored planar elements only."""
    if original is None or not original.exists():
        return []
    rows = []
    with zipfile.ZipFile(original) as archive:
        names = set(archive.namelist())
        for ref, current in models.items():
            namespace, local = qualified(ref)
            # The source pack used minecraft namespace, while imported copies
            # live in bloodborne_blocks.  Generated v2 models have no source.
            source = f"assets/{namespace}/models/{local}.json"
            if source not in names and namespace == NS:
                source = f"assets/minecraft/models/{local}.json"
            if source not in names:
                continue
            try:
                authored = json.loads(archive.read(source).decode("utf-8-sig"))
            except (UnicodeDecodeError, json.JSONDecodeError):
                continue
            old = authored.get("elements", [])
            new = current.get("elements", [])
            for index, element in enumerate(old):
                low, high = element.get("from"), element.get("to")
                if not (isinstance(low, list) and isinstance(high, list) and len(low) == len(high) == 3):
                    continue
                if not any(abs(float(high[a]) - float(low[a])) < 1e-8 for a in range(3)) or index >= len(new):
                    continue
                candidate = new[index]
                current_low, current_high = candidate.get("from"), candidate.get("to")
                if not (isinstance(current_low, list) and isinstance(current_high, list) and len(current_low) == len(current_high) == 3):
                    continue
                # Bounded evidence: only call this an importer regression when
                # every non-planar axis is unchanged and the new sliver remains
                # centred on the original planar coordinate.  This excludes
                # intentionally remodelled art and transformed copies.
                exact = all(
                    (abs(float(high[a]) - float(low[a])) < 1e-8 and abs((float(current_low[a]) + float(current_high[a])) / 2 - float(low[a])) < 1e-8)
                    or (abs(float(current_low[a]) - float(low[a])) < 1e-8 and abs(float(current_high[a]) - float(high[a])) < 1e-8)
                    for a in range(3)
                )
                if not exact:
                    continue
                old_faces = set(element.get("faces", {}))
                new_faces = set(candidate.get("faces", {}))
                added = sorted(new_faces - old_faces)
                if added:
                    rows.append({"model": ref, "element": index, "authored_faces": sorted(old_faces), "added_faces": added,
                                 "criterion": "same non-planar bounds and same planar centre",
                                 "reason": "post-import face expansion on authored planar artwork; inspect before removing"})
    return rows


def classify_legacy(blocks: list[dict], semantics: dict, applications: dict[str, list[str]]) -> tuple[list[dict], list[dict]]:
    whole, variants = [], []
    object_words = ("bush", "tree", "tomb", "grave", "books", "book", "wooden_box", "statue", "spire", "lamp", "lantern", "shutter")
    connection_words = ("fence", "wall", "pane", "slab", "stairs", "corner", "side", "end", "cap", "tile", "floor")
    for block in blocks:
        ident = block["id"]
        semantic = semantics.get(ident, {})
        category = semantic.get("category", "unclassified")
        models = applications.get(ident, [])
        # Do not trust coarse semantic labels alone: v2 module categorisation
        # intentionally reuses labels such as column/bush for unrelated art.
        haystack = f"{ident} {' '.join(models)}".lower()
        record = {"legacy_id": ident, "kind": block.get("kind"), "semantic_category": category,
                  "states": len(block.get("states", {})), "source": block.get("source"), "models": models}
        if any(word in haystack for word in object_words):
            whole.append(record)
        if block.get("kind") in {"fence", "wall", "pane", "slab", "stairs", "door", "gate", "trapdoor"} or any(word in haystack for word in connection_words):
            variants.append(record)
    return whole, variants


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "docs/logical-audit-v3.json")
    parser.add_argument("--original-pack", type=Path, default=Path.home() / "Downloads" / "bloodborne.zip")
    args = parser.parse_args()

    legacy = load(RES / "bloodborne_blocks/definitions.json")
    v2 = load(RES / "bloodborne_blocks/v2/definitions.json")
    migration = load(RES / "bloodborne_blocks/v2/migration.json")
    semantics = load(ROOT / "docs/semantic-catalog-v2.json")
    overrides = load(ROOT / "docs/semantic-model-overrides-v2.json")

    json_errors, models, blockstates = [], {}, {}
    for path in ASSETS.rglob("*.json"):
        try:
            data = load(path)
        except (OSError, json.JSONDecodeError) as error:
            json_errors.append({"path": str(path.relative_to(ROOT)), "error": str(error)})
            continue
        relative = path.relative_to(ASSETS)
        if relative.parts[0] == "models":
            models[f"{NS}:" + str(relative.relative_to("models")).replace("\\", "/")[:-5]] = data
        elif relative.parts[0] == "blockstates":
            blockstates[path.stem] = data

    missing_models, missing_parents, missing_textures = [], [], []
    applications = collections.defaultdict(list)
    for ident, state in blockstates.items():
        for app in state_apps(state):
            applications[ident].append(app["model"])
            path = model_path(app["model"])
            if path is not None and app["model"] not in models:
                missing_models.append({"blockstate": ident, "model": app["model"]})
    for ref, data in models.items():
        parent = data.get("parent")
        if isinstance(parent, str):
            path = model_path(parent)
            if path is not None and parent not in models:
                missing_parents.append({"model": ref, "parent": parent})
        for value in data.get("textures", {}).values():
            if not isinstance(value, str) or value.startswith("#"):
                continue
            path = texture_path(value)
            if path is not None and not path.exists():
                missing_textures.append({"model": ref, "texture": value})
        for element in data.get("elements", []):
            for face in element.get("faces", {}).values():
                value = face.get("texture")
                if isinstance(value, str) and not value.startswith("#"):
                    path = texture_path(value)
                    if path is not None and not path.exists():
                        missing_textures.append({"model": ref, "texture": value})

    flags = {ref: model_flags(data) for ref, data in models.items()}
    flags = {ref: value for ref, value in flags.items() if value}
    direct_groups = collections.defaultdict(list)
    for ref, data in models.items():
        direct_groups[direct_fingerprint(data)].append(ref)
    duplicate_models = [sorted(group) for group in direct_groups.values() if len(group) > 1]

    legacy_ids, v2_ids = {b["id"] for b in legacy["blocks"]}, {b["id"] for b in v2["blocks"]}
    item_models = {path.stem for path in (ASSETS / "models/item").glob("*.json")}
    registry_item = {
        "legacy_without_blockstate": sorted(legacy_ids - set(blockstates)),
        "v2_without_blockstate": sorted(v2_ids - set(blockstates)),
        "legacy_without_item_model": sorted(legacy_ids - item_models),
        "v2_creative_without_item_model": sorted(b["id"] for b in v2["blocks"] if b.get("creative") and b["id"] not in item_models),
        "orphan_item_models": sorted(item_models - legacy_ids - v2_ids),
    }

    reverse_components = collections.defaultdict(list)
    migration_rows = []
    for old_id, spec in migration.items():
        for old_state, target in spec.get("states", {}).items():
            if isinstance(target, list):
                components = []
                for part in target:
                    component = {"id": part.get("id"), "offset": part.get("offset", [0, 0, 0]), "properties": part.get("properties", {})}
                    components.append(component)
                    if component["id"]:
                        reverse_components[component["id"]].append({"legacy_id": old_id, "legacy_state": old_state, **component})
                migration_rows.append({"legacy_id": old_id, "legacy_state": old_state, "components": components})
            elif isinstance(target, dict):
                migration_rows.append({"legacy_id": old_id, "legacy_state": old_state, "retained": target})

    whole, variants = classify_legacy(legacy["blocks"], semantics, applications)
    semantic_counts = collections.Counter(block.get("semantic", "unclassified") for block in v2["blocks"])
    report = {
        "format": "bloodborne-logical-object-audit-v3",
        "read_only": True,
        "scope": "repository assets only; minecraft namespace is external and intentionally not resolved",
        "counts": {"legacy_blocks": len(legacy_ids), "v2_blocks": len(v2_ids), "models": len(models), "blockstates": len(blockstates),
                   "item_models": len(item_models), "migration_legacy_states": len(migration_rows), "component_ids": len(reverse_components),
                   "semantic_v2": dict(sorted(semantic_counts.items()))},
        "resource_graph": {"json_errors": json_errors, "missing_model_refs": missing_models, "missing_parent_refs": missing_parents,
                           "missing_texture_refs": missing_textures, "external_minecraft_refs_not_resolved": True},
        "legacy_state_model_applications": {key: sorted(set(value)) for key, value in sorted(applications.items())},
        "migration": {"legacy_state_targets": migration_rows, "component_to_legacy_reverse": dict(sorted(reverse_components.items()))},
        "families": {"identical_direct_model_groups": duplicate_models,
                     "rotation_stateful_blockstates": sorted({ident for ident, apps in applications.items() if any("y" in app for app in state_apps(blockstates[ident]))})},
        "normalization_candidates": {"obvious_whole_objects_review": whole, "architectural_or_connection_variants_review": variants,
                                     "v2_semantic_groups": dict(sorted(semantic_counts.items()))},
        "mapping_item_discrepancies": registry_item,
        "model_review_flags": flags,
        "original_pack_planar_face_expansion": original_sprite_regressions(args.original_pack, models),
        "semantic_override_models": sorted(overrides),
        "notes": ["Candidate lists are triage, not automatic migration instructions.",
                  "UV/cull/planar flags identify a source to inspect; no texture or model is modified.",
                  "The original-pack comparison isolates added faces on planar artwork, a concrete suspect for protruding sprite artifacts."],
    }
    dump(args.output, report)
    counts = report["counts"]
    print("LOGICAL AUDIT", f"models={counts['models']}", f"blockstates={counts['blockstates']}", f"legacy={counts['legacy_blocks']}", f"v2={counts['v2_blocks']}")
    print("GRAPH", f"json={len(json_errors)}", f"model={len(missing_models)}", f"parent={len(missing_parents)}", f"texture={len(missing_textures)}")
    print("TRIAGE", f"whole={len(whole)}", f"connection={len(variants)}", f"planar-expansion={len(report['original_pack_planar_face_expansion'])}")


if __name__ == "__main__":
    main()
