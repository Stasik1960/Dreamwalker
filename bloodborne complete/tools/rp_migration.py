"""Context-specific, preserving Forge RP NBT migration primitives.

These functions return a copied typed NBT tree. They do not edit a world or
rewrite arbitrary strings. Callers select actual entity/player/block-entity
contexts from the world format and record the returned changes and issues.
Unknown Forge IDs stay intact and prevent a successful migration claim.
"""
from __future__ import annotations

import argparse
import copy
from dataclasses import dataclass, field
import hashlib
import json
from pathlib import Path
import re
from world_io import Tag, TAG_BYTE, TAG_COMPOUND, TAG_LIST, TAG_STRING, TAG_INT, TAG_LONG, TAG_INT_ARRAY

ROOT = Path(__file__).resolve().parents[1]
MOB_IDS = frozenset((
    "cleric_beast", "vicar_amelia", "blood_starved_beast", "scourge_beast", "executioner",
    "huntsman_a", "huntsman_b", "huntsman_c", "huntsman_d", "huntsman_wheelchair",
    "large_huntsman", "carrion_crow", "giant_rat", "small_rat", "rabid_dog", "maneater_boar",
    "brick_troll", "rotted_corpse"))
NON_ENTITY_ASSETS = frozenset(("sawcleaver_false", "sawcleaver_true", "sawspear_false",
                            "sawspear_true", "boomhammer_false", "boomhammer_true", "bullet", "blood_puddle"))
WEAPON_FAMILIES = {"sawcleaver": "saw_cleaver", "sawspear": "saw_spear", "boomhammer": "boom_hammer"}


def registry_schema() -> dict:
    catalog_path = ROOT / "src/rp/resources/assets/bloodborne_rp/catalog.json"
    catalog = json.loads(catalog_path.read_text(encoding="utf8"))
    mob_source = (ROOT / "src/rp/java/dev/dreamwalker/bloodbornerp/mob/MobRegistry.java").read_text(encoding="utf8")
    object_source = (ROOT / "src/rp/java/dev/dreamwalker/bloodbornerp/object/ObjectRegistry.java").read_text(encoding="utf8")
    mobs_actual = set(re.findall(r'"([a-z0-9_]+)"', re.search(r'IDS\s*=\s*List\.of\((.*?)\);', mob_source, re.S).group(1)))
    exclusions_actual = set(re.findall(r'"([a-z0-9_]+)"', re.search(r'NON_OBJECTS\s*=\s*Set\.of\((.*?)\);', object_source, re.S).group(1)))
    if mobs_actual != MOB_IDS or exclusions_actual != NON_ENTITY_ASSETS:
        raise ValueError("RP registry source changed; review migration schema before use")
    objects = set(catalog) - MOB_IDS - NON_ENTITY_ASSETS
    entities = MOB_IDS | objects
    items = {key + "_spawn_egg" for key in MOB_IDS} | {key + "_placer" for key in objects} | set(WEAPON_FAMILIES.values()) | {"blood_vial"}
    entity_map = {"bloodborne:" + key: "bloodborne_rp:" + key for key in sorted(entities)}
    item_map = {"bloodborne:" + key: {"target": "bloodborne_rp:" + key} for key in sorted(items)}
    # Forge registered placement tools with the entity's own suffix; the
    # source world contains these actual ItemStacks. The RP registry gives
    # those items the explicit _placer suffix.
    for key in sorted(objects):
        item_map["bloodborne:" + key] = {"target": "bloodborne_rp:" + key + "_placer"}
    for family, target in WEAPON_FAMILIES.items():
        for form in ("false", "true"):
            item_map["bloodborne:" + family + "_" + form] = {"target": "bloodborne_rp:" + target, "BloodborneRpForm": form == "true"}
    return {"namespace": "bloodborne_rp", "mobs": sorted(MOB_IDS), "objects": sorted(objects),
            "entity_ids": sorted("bloodborne_rp:" + key for key in entities),
            "item_ids": sorted("bloodborne_rp:" + key for key in items),
            "entity_map": entity_map, "item_map": item_map,
            "catalog_sha256": hashlib.sha256(catalog_path.read_bytes()).hexdigest()}


@dataclass
class MigrationResult:
    tag: Tag
    changes: list[dict] = field(default_factory=list)
    issues: list[dict] = field(default_factory=list)

    @property
    def changed(self) -> bool:
        return bool(self.changes)


def _issue(result: MigrationResult, path: str, code: str, value=None) -> None:
    result.issues.append({"path": path, "code": code, "value": value})


def _change(result: MigrationResult, path: str, before, after, rule: str) -> None:
    result.changes.append({"path": path, "before": before, "after": after, "rule": rule})


def _compound(tag: Tag, result: MigrationResult, path: str):
    if tag.type != TAG_COMPOUND:
        _issue(result, path, "EXPECTED_COMPOUND", tag.type)
        return None
    return tag.value


def _items(fields: dict, result: MigrationResult, path: str, schema: dict, keys: tuple[str, ...]) -> None:
    for key in keys:
        if key not in fields:
            continue
        items = fields[key]
        if items.type != TAG_LIST or items.list_type not in (TAG_COMPOUND, 0) or items.list_type == 0 and items.value:
            _issue(result, path + "/" + key, "EXPECTED_ITEM_LIST", items.type)
            continue
        for index, item in enumerate(items.value):
            _item(item, result, path + f"/{key}/{index}", schema)


def _item(tag: Tag, result: MigrationResult, path: str, schema: dict) -> None:
    fields = _compound(tag, result, path)
    if fields is None:
        return
    identifier = fields.get("id")
    if identifier is not None and identifier.type == TAG_STRING and identifier.value.startswith("bloodborne:"):
        mapping = schema["item_map"].get(identifier.value)
        if mapping is None:
            _issue(result, path + "/id", "UNMAPPED_FORGE_ITEM", identifier.value)
            return
        target = mapping["target"]
        if target not in schema["item_ids"]:
            raise ValueError("Migration item target is not registered: " + target)
        if "BloodborneRpForm" in mapping:
            item_tag = fields.get("tag")
            if item_tag is not None and item_tag.type != TAG_COMPOUND:
                _issue(result, path + "/tag", "WEAPON_TAG_WRONG_TYPE", item_tag.type)
                return
            previous = item_tag.value.get("BloodborneRpForm") if item_tag else None
            form = int(mapping["BloodborneRpForm"])
            if previous is not None and (previous.type != TAG_BYTE or previous.value != form):
                _issue(result, path + "/tag/BloodborneRpForm", "WEAPON_FORM_CONFLICT", previous.value)
                return
            if previous is None:
                if item_tag is None:
                    item_tag = fields["tag"] = Tag(TAG_COMPOUND, {})
                item_tag.value["BloodborneRpForm"] = Tag(TAG_BYTE, form)
                _change(result, path + "/tag/BloodborneRpForm", None, form, "explicit_legacy_weapon_form")
        before = identifier.value
        identifier.value = target
        _change(result, path + "/id", before, target, "validated_item_registry_map")
    # Only the documented ItemStack BlockEntityTag inventory context is walked.
    item_tag = fields.get("tag")
    if item_tag is not None and item_tag.type == TAG_COMPOUND:
        block_entity = item_tag.value.get("BlockEntityTag")
        if block_entity is not None and block_entity.type == TAG_COMPOUND:
            _items(block_entity.value, result, path + "/tag/BlockEntityTag", schema, ("Items",))


def _entity(tag: Tag, result: MigrationResult, path: str, schema: dict) -> None:
    fields = _compound(tag, result, path)
    if fields is None:
        return
    identifier = fields.get("id")
    if identifier is not None and identifier.type == TAG_STRING and identifier.value.startswith("bloodborne:"):
        target = schema["entity_map"].get(identifier.value)
        if target is None:
            _issue(result, path + "/id", "UNMAPPED_FORGE_ENTITY", identifier.value)
            return
        if target not in schema["entity_ids"]:
            raise ValueError("Migration entity target is not registered: " + target)
        before = identifier.value
        identifier.value = target
        _change(result, path + "/id", before, target, "validated_entity_registry_map")
        # Original EntityState is already handled by the port for main_gate and
        # small_gate. Preserve it and all original fields; do not guess aliases.
        for key in ("IsOpen", "isOpen", "is_open", "IsLocked", "isLocked"):
            if key in fields:
                _issue(result, path + "/" + key, "UNREVIEWED_LEGACY_STATE_ALIAS", fields[key].value)
    _items(fields, result, path, schema, ("HandItems", "ArmorItems", "Inventory", "Items"))
    for key in ("Item", "SelectedItem", "SaddleItem", "DecorItem"):
        if key in fields:
            _item(fields[key], result, path + "/" + key, schema)
    passengers = fields.get("Passengers")
    if passengers is not None:
        if passengers.type != TAG_LIST or passengers.list_type not in (TAG_COMPOUND, 0):
            _issue(result, path + "/Passengers", "EXPECTED_ENTITY_LIST", passengers.type)
        else:
            for index, child in enumerate(passengers.value):
                _entity(child, result, path + f"/Passengers/{index}", schema)


def migrate_entity(tag: Tag, *, path="entity", schema: dict | None = None) -> MigrationResult:
    result = MigrationResult(copy.deepcopy(tag))
    _entity(result.tag, result, path, schema or registry_schema())
    return result


def migrate_item(tag: Tag, *, path="item", schema: dict | None = None) -> MigrationResult:
    result = MigrationResult(copy.deepcopy(tag))
    _item(result.tag, result, path, schema or registry_schema())
    return result


def migrate_inventory_record(tag: Tag, *, kind: str, path="record", schema: dict | None = None) -> MigrationResult:
    """Migrate player or block-entity inventories, preserving the owner's ID."""
    if kind not in ("player", "block_entity"):
        raise ValueError("Select an explicit inventory context")
    result = MigrationResult(copy.deepcopy(tag))
    fields = _compound(result.tag, result, path)
    if fields is not None:
        _items(fields, result, path, schema or registry_schema(),
               ("Inventory", "EnderItems") if kind == "player" else ("Items", "Inventory"))
    return result


def migrate_legacy_lamp_state(tag: Tag, *, entities: list[tuple[str, Tag]]) -> MigrationResult:
    """Produce a separate RP lamp-state candidate from verified Forge records.

    Original data/hunter_lamp_manager.dat must remain as provenance. Match the
    original lamp registry Id to entity custom Id, not entity UUID, then place
    those distinct identifiers into the RP Node.Id and Node.Lamp fields.
    Directed routes are empty because this Forge schema contains none.
    """
    result = MigrationResult(copy.deepcopy(tag))
    root = _compound(result.tag, result, "lamp_state")
    if root is None:
        return result
    data = root.get("data")
    if data is None or data.type != TAG_COMPOUND:
        _issue(result, "lamp_state/data", "INVALID_LEGACY_LAMP_DATA")
        return result
    records = data.value.get("HunterLamps")
    if records is None or records.type != TAG_LIST or records.list_type != TAG_COMPOUND:
        _issue(result, "lamp_state/data/HunterLamps", "INVALID_LEGACY_LAMP_LIST")
        return result
    indexed = {}
    for dimension, entity in entities:
        fields = entity.value if entity.type == TAG_COMPOUND else {}
        identifier = fields.get("id")
        legacy_id = fields.get("Id")
        if identifier is not None and identifier.value in ("bloodborne:hunterlamp", "bloodborne_rp:hunterlamp") and legacy_id is not None and legacy_id.type == TAG_INT_ARRAY and len(legacy_id.value) == 4:
            indexed.setdefault(tuple(legacy_id.value), []).append((dimension, fields))
    nodes, seen = [], {}
    for index, record in enumerate(records.value):
        path = f"lamp_state/data/HunterLamps/{index}"
        fields = _compound(record, result, path)
        if fields is None:
            continue
        legacy = fields.get("hunterLampId")
        if legacy is None or legacy.type != TAG_INT_ARRAY or len(legacy.value) != 4:
            _issue(result, path + "/hunterLampId", "INVALID_LEGACY_LAMP_ID")
            continue
        identity = tuple(legacy.value)
        if identity in seen:
            _issue(result, path, "SOURCE_DUPLICATE_LAMP_REGISTRATION" if record == seen[identity] else "CONFLICTING_LEGACY_LAMP_REGISTRATION", list(identity))
            continue
        seen[identity] = record
        matches = indexed.get(identity, [])
        if len(matches) != 1:
            _issue(result, path, "LEGACY_LAMP_ENTITY_NOT_UNIQUE", len(matches))
            continue
        dimension, entity = matches[0]
        uuid = entity.get("UUID")
        pos = entity.get("Pos")
        coordinates = [fields.get(key) for key in ("X", "Y", "Z")]
        name = fields.get("area")
        if uuid is None or uuid.type != TAG_INT_ARRAY or len(uuid.value) != 4 or pos is None or pos.type != TAG_LIST or len(pos.value) != 3 or any(value.type not in range(1, 7) for value in pos.value) or any(value is None or value.type != TAG_INT for value in coordinates):
            _issue(result, path, "INVALID_LEGACY_LAMP_ENTITY_DATA")
            continue
        xyz = [value.value for value in coordinates]
        import math
        if xyz != [math.floor(value.value) for value in pos.value]:
            _issue(result, path, "LEGACY_LAMP_POSITION_MISMATCH", xyz)
            continue
        if name is None or name.type != TAG_STRING or not name.value.strip() or len(name.value) > 64 or not re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", dimension):
            _issue(result, path, "INVALID_LEGACY_LAMP_NAME_OR_DIMENSION")
            continue
        x, y, z = xyz
        packed = ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF)
        if packed >= 1 << 63:
            packed -= 1 << 64
        nodes.append(Tag(TAG_COMPOUND, {"Id": copy.deepcopy(legacy), "Lamp": copy.deepcopy(uuid),
            "Name": copy.deepcopy(name), "Dimension": Tag(TAG_STRING, dimension), "Pos": Tag(TAG_LONG, packed),
            "Routes": Tag(TAG_LIST, [], TAG_INT_ARRAY)}))
        _change(result, path, {"Id": list(identity), "Pos": xyz},
                {"Id": list(identity), "Lamp": list(uuid.value), "Name": name.value, "Dimension": dimension, "Routes": []},
                "verified_legacy_lamp_registry_to_rp_node")
    if any(issue["code"] != "SOURCE_DUPLICATE_LAMP_REGISTRATION" for issue in result.issues):
        # A lamp file is migrated as one operation; avoid a partial registry.
        result.tag = copy.deepcopy(tag)
        result.changes.clear()
        return result
    root["data"] = Tag(TAG_COMPOUND, {"Schema": Tag(TAG_INT, 1), "Nodes": Tag(TAG_LIST, nodes, TAG_COMPOUND)})
    return result


def write_plan(world_audit: Path | None, output: Path) -> dict:
    schema = registry_schema()
    rows = []
    if world_audit is not None:
        audit = json.loads(world_audit.read_text(encoding="utf8"))
        for identifier, count in audit["entity_counts"].items():
            if identifier.startswith("bloodborne:"):
                target = schema["entity_map"].get(identifier)
                rows.append({"source_id": identifier, "target_id": target, "count": count,
                             "status": "VALIDATED_REGISTRY_TARGET" if target else "UNMAPPED_SOURCE_ENTITY"})
    plan = {"schema": "dreamwalker-rp-migration-v1", "registries": schema, "observed_entities": rows,
            "world_audit": str(world_audit) if world_audit else None,
            "application_status": "NOT_APPLIED", "game_save_preservation": "NOT_RUN",
            "rules": ["only explicit entity and ItemStack id contexts", "preserve UUID Pos Rotation Scale Open Locked EntityState Links ConnectionId and timers",
                      "preserve Health Attributes Brain ForgeCaps AI flags and original unknown mod fields",
                      "do not add frozen state", "unknown IDs remain unchanged and are reported",
                      "main/small gates already read EntityState; ambiguous aliases require review",
                      "legacy weapon false/true suffixes collapse to family item with BloodborneRpForm byte",
                      "preserve lamp dat file bytes; route registration is separate from entity existence"],
            "limitations": ["Forge unknown fields may be discarded by Minecraft/port on game save; must verify separately",
                            "datapack commands, spawner entity snapshots, POI and ticks require context-specific analysis",
                            "transient Forge bullet and blood_puddle have no registered RP entity counterpart"]}
    if world_audit is not None:
        plan["legacy_lamp_data"] = [record for record in audit.get("nbt_files", []) if "lamp" in record["path"]]
        plan["source_lamp_entities"] = [entity for entity in audit["entities"] if entity["id"] == "bloodborne:hunterlamp"]
        plan["lamp_migration_rule"] = "Preserve original file; create separate bloodborne_rp_lamps.dat candidate by custom Id match; node Id retains original registration ID and Lamp retains entity UUID; exact duplicates reported; no invented routes."
        plan["source_mechanisms"] = [{"id": entity["id"], "uuid": entity["uuid"], "dimension": entity["dimension"], "pos": entity["pos"],
            "ConnectionId": entity["data"].get("ConnectionId"), "EntityState": entity["data"].get("EntityState"), "CustomName": entity["data"].get("CustomName")}
            for entity in audit["entities"] if entity["id"] in ("bloodborne:lever_1", "bloodborne:lever_2", "bloodborne:main_gate", "bloodborne:small_gate")]
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(plan, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    return plan


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--world-audit", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "reports/RP_MIGRATION_PLAN.json")
    arguments = parser.parse_args()
    plan = write_plan(arguments.world_audit, arguments.output)
    print(json.dumps({"entities": len(plan["registries"]["entity_ids"]), "items": len(plan["registries"]["item_ids"]),
                      "observed_source_types": len(plan["observed_entities"]), "application": "NOT_APPLIED"}))
