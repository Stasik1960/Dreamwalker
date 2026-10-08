"""Independently audit a saved source-review copy across the Minecraft load/save boundary.

Logical terrain, typed foreign BEs, DW ownership and live RP entities have separate
verdicts. Metadata/DFU/tick changes are listed, never hidden by a blanket whitelist.
"""
from __future__ import annotations
import argparse
import base64
from collections import defaultdict
import hashlib
import json
import math
from pathlib import Path
import uuid
import world_io as wi

ROOT = Path(__file__).resolve().parents[1]
AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
ENGINE_ENTITY_FIELDS = {
    "id", "UUID", "Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround", "Invulnerable", "PortalCooldown",
    "CustomName", "CustomNameVisible", "Silent", "NoGravity", "Glowing", "Tags", "Passengers", "HasVisualFire", "TicksFrozen",
    "Health", "HurtTime", "HurtByTimestamp", "DeathTime", "AbsorptionAmount", "Attributes", "ActiveEffects", "FallFlying",
    "SleepingX", "SleepingY", "SleepingZ", "Brain", "HandItems", "HandDropChances", "ArmorItems", "ArmorDropChances",
    "Leash", "PersistenceRequired", "CanPickUpLoot", "LeftHanded", "NoAI", "DeathLootTable", "DeathLootTableSeed",
    "Patrolling", "PatrolTarget", "PatrolLeader", "Inventory", "AngerTime", "AngryAt"
}


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def summary(tag):
    if tag is None:
        return None
    encoded = wi.encode_nbt(wi.NbtFile("", tag))
    value = tag.value
    return {"type": tag.type, "sha256": hashlib.sha256(encoded).hexdigest(),
            **({"value": value} if tag.type <= 6 or tag.type == 8 else {"size": len(value)})}


def typed_diff(a, b, path, result):
    if a == b:
        return
    if a is None or b is None or a.type != b.type:
        result.append({"path": path, "before": summary(a), "after": summary(b)})
    elif a.type == 10:
        for key in sorted(a.value.keys() | b.value.keys()):
            typed_diff(a.value.get(key), b.value.get(key), path + "/" + key, result)
    elif a.type == 9 and a.list_type == b.list_type and len(a.value) == len(b.value):
        for i, (old, new) in enumerate(zip(a.value, b.value)):
            typed_diff(old, new, path + "/" + str(i), result)
    else:
        result.append({"path": path, "before": summary(a), "after": summary(b)})


class World:
    def __init__(self, path):
        self.path = Path(path)
        self.chunks = {}
        self.entities = {}
        self.block_entities = {}
        self.section_cache = {}
        self.empty_region_files = []
        for folder in ("region", "entities"):
            for file in sorted((self.path / folder).glob("*.mca")):
                raw = file.read_bytes()
                if not raw:
                    self.empty_region_files.append(file.relative_to(self.path).as_posix())
                    continue
                for stored in wi.RegionFile(raw).chunks():
                    root = wi.compound(stored.nbt().root)
                    if folder == "region":
                        key = (root["xPos"].value, root["zPos"].value)
                        self.chunks[key] = root
                        for tag in root.get("block_entities", wi.Tag(9, [], 10)).value:
                            row = wi.compound(tag)
                            self.block_entities[tuple(row[k].value for k in ("x", "y", "z"))] = tag
                    else:
                        for tag in root.get("Entities", wi.Tag(9, [], 10)).value:
                            row = wi.compound(tag)
                            if "UUID" in row:
                                identity = tuple(row["UUID"].value)
                                if identity in self.entities:
                                    raise ValueError("Duplicate saved entity UUID: " + str(identity))
                                self.entities[identity] = tag

    def sections(self, chunk):
        return {wi.compound(t)["Y"].value: t for t in self.chunks.get(chunk, {}).get("sections", wi.Tag(9, [], 10)).value}

    def states(self, chunk, sy):
        key = (*chunk, sy)
        if key not in self.section_cache:
            tag = self.sections(chunk).get(sy)
            values = wi.section_blocks(tag) if tag else None
            if values:
                palette, indices = values
                keys = [wi.block_state_key(t) for t in palette]
                self.section_cache[key] = [keys[i] for i in indices]
            else:
                self.section_cache[key] = ["minecraft:air"] * 4096
        return self.section_cache[key]

    def state(self, pos):
        x, y, z = pos
        return self.states((x // 16, z // 16), y // 16)[y % 16 * 256 + z % 16 * 16 + x % 16]


def uuid_array(text):
    value = uuid.UUID(text).int
    return tuple(((value >> shift) & 0xffffffff) - (1 << 32) if ((value >> shift) & 0xffffffff) >= 1 << 31
                 else ((value >> shift) & 0xffffffff) for shift in (96, 64, 32, 0))


def state_key(name, properties):
    return name + ("[" + ",".join(k + "=" + str(v) for k, v in sorted(properties.items())) + "]" if properties else "")


def expected_root(row, review_revision="V7"):
    kind = row["kind"]
    if kind == "source_ladder_pair":
        physical = next(m for m in row["members"] if m["pos"] == row["physical"])
        properties = {"facing": physical["properties"]["facing"],
            "diagonal": "false", "profile": row["profile"], "source_clone": "true",
            "variant": row["variant"], "waterlogged": physical["properties"]["waterlogged"]}
        if review_revision == "V9":
            properties["freestanding"] = "false"
        return state_key("bloodborne_dw:prototype_ladder", properties)
    if kind == "prototype_wall":
        return state_key("bloodborne_dw:" + kind, row["art"])
    properties = {"rotation": row["rotation"], "variant": row["variant"],
                  "profile": row["profile"], "open": str(row["open"]).lower()}
    if "mount" in row:
        if kind != "prototype_thin_window" or row["mount"] != "vertical":
            raise ValueError("Bounded source migration preserves only original vertical glazing; other mount modes require separate source proof")
        properties["mount"] = row["mount"]
    return state_key("bloodborne_dw:" + kind, properties)


def footprint(row):
    spec = json.loads((ROOT / "src/architecture/resources/bloodborne_dw/composite" / (row["kind"] + ".json")).read_text(encoding="utf-8"))
    physical_yaw = collision_yaw(spec, row["rotation"])
    if row["rotation"] != 0:
        raise ValueError("This independently reviewed source subset expects north0; do not silently approximate another yaw")
    pose = spec["variants"][row["variant"]]["poses"]["open" if row["open"] else "closed"]
    shift = row["sourceShift"]
    result = defaultdict(lambda: {"collision": [], "selection": []})
    for kind in ("collision", "selection"):
        for box in pose.get(kind, pose.get("collision", [])):
            lo, hi = axis_box(box, physical_yaw if kind == "collision" else row["rotation"], shift)
            for x in range(math.floor(lo[0] + 1e-8), math.ceil(hi[0] - 1e-8)):
                for y in range(math.floor(lo[1] + 1e-8), math.ceil(hi[1] - 1e-8)):
                    for z in range(math.floor(lo[2] + 1e-8), math.ceil(hi[2] - 1e-8)):
                        offset = (x, y, z)
                        bounds = tuple(max(0, lo[i] - offset[i]) for i in range(3)) + tuple(min(1, hi[i] - offset[i]) for i in range(3))
                        if all(bounds[i + 3] - bounds[i] > 1e-10 for i in range(3)):
                            result[tuple(row["root"][i] + offset[i] for i in range(3))][kind].append(bounds)
    result[tuple(row["root"])]
    return dict(result)


def collision_yaw(spec, visual_yaw):
    """Physical orientation is a declared contract, separate from visual/outline yaw."""
    if type(visual_yaw) is not int or not 0 <= visual_yaw <= 7:
        raise ValueError("Expected reviewed 45-degree orientation0..7")
    rule = spec.get("collisionRotation", "SOURCE_ALIGNED")
    if rule == "GRID_ALIGNED":
        return 0
    if rule == "SOURCE_ALIGNED":
        return visual_yaw
    raise ValueError("Unsupported explicit collisionRotation: " + str(rule))


def axis_box(box, yaw, shift):
    """Exact cardinal/grid box; diagonal source masks need their own reviewed proof."""
    if yaw not in (0, 2, 4, 6):
        raise ValueError("Independent bounded source proof does not approximate diagonal boxes")
    c, s = ((1, 0), (0, 1), (-1, 0), (0, -1))[yaw // 2]
    corners = [(box[x][0] / 16 - .5, box[z][2] / 16 - .5)
               for x in ("from", "to") for z in ("from", "to")]
    points = [(.5 + c*x - s*z, .5 + s*x + c*z) for x, z in corners]
    lo = [min(p[0] for p in points) + shift[0], box["from"][1]/16 + shift[1], min(p[1] for p in points) + shift[2]]
    hi = [max(p[0] for p in points) + shift[0], box["to"][1]/16 + shift[1], max(p[1] for p in points) + shift[2]]
    return lo, hi


def rp_v9_role_differences(original, saved, *, reopening=False):
    """Validate added persistent roles without relaxing any original-payload check."""
    result = []
    identity = original["id"].value.removeprefix("bloodborne_rp:")
    version = saved.get("RpBehaviourVersion")
    if version != wi.Tag(wi.TAG_INT, 1):
        result.append({"path": "RpBehaviourVersion", "expected": "INT1", "actual": summary(version)})
    if identity in {"cage_obj_1", "cage_obj_2", "cage_obj_3"}:
        expected = original.get("DogsVisible", wi.Tag(wi.TAG_BYTE, 1))
        if expected.type != wi.TAG_BYTE or expected.value not in (0, 1) or saved.get("DogsVisible") != expected:
            result.append({"path": "DogsVisible", "expected": summary(expected), "actual": summary(saved.get("DogsVisible"))})
    if identity == "ladder":
        remaining = saved.get("LadderDeployTicks")
        if remaining is None or remaining.type != wi.TAG_INT or not 0 <= remaining.value <= 48:
            result.append({"path": "LadderDeployTicks", "expected": "INT0..48", "actual": summary(remaining)})
        elif reopening:
            old = original.get("LadderDeployTicks")
            if old is None or old.type != wi.TAG_INT or remaining.value > old.value:
                result.append({"path": "LadderDeployTicks", "expected": "Existing deployment countdown cannot restart on load", "before": summary(old), "actual": summary(remaining)})
    # The pulse is tracker-only. Opaque legacy fields with the same spelling remain
    # subject to the exact source passthrough contract rather than being discarded.
    if identity == "wood_gate":
        for key in ("WoodGatePulseTicks", "GateRequests"):
            if key not in original and key in saved:
                result.append({"path": key, "expected": "ABSENT_TRANSIENT_PULSE_NOT_SAVED", "actual": summary(saved[key])})
    return result


def unhandled_source_differences(original, saved):
    result = []
    for key in sorted(original.keys() - ENGINE_ENTITY_FIELDS - {"SourceLegacyPayload"}):
        typed_diff(original[key], saved.get(key), "live-source-passthrough/" + key, result)
    return result


def boxes(tag):
    return sorted(tuple(float(t.value) for t in row.value) for row in tag.value)


def same_boxes(a, b):
    a, b = sorted(a), sorted(b)
    if len(a) == len(b) and all(all(abs(x - y) <= 1e-8 for x, y in zip(old, new)) for old, new in zip(a, b)):
        return True
    # The engine clips canonical cells before fractional reprojection; an
    # independent world-space construction can partition the same union differently.
    # Compare exact coverage, not cuboid-list identity or a coarser voxel raster.
    def intervals(values, y, z):
        segments = sorted((round(v[0], 9), round(v[3], 9)) for v in values if v[1] < y < v[4] and v[2] < z < v[5])
        merged = []
        for start, end in segments:
            if merged and start <= merged[-1][1] + 1e-8:
                merged[-1] = (merged[-1][0], max(end, merged[-1][1]))
            else:
                merged.append((start, end))
        return merged
    y_edges = sorted({round(v[i], 9) for v in a + b for i in (1, 4)})
    z_edges = sorted({round(v[i], 9) for v in a + b for i in (2, 5)})
    for y0, y1 in zip(y_edges, y_edges[1:]):
        for z0, z1 in zip(z_edges, z_edges[1:]):
            if intervals(a, (y0 + y1) / 2, (z0 + z1) / 2) != intervals(b, (y0 + y1) / 2, (z0 + z1) / 2):
                return False
    return True


def source_members(tag, row):
    records = {tuple(wi.compound(t)["Pos"].value): wi.compound(t) for t in tag.value}
    if len(records) != len(row["members"]):
        return False
    for member in row["members"]:
        value = records.get(tuple(member["pos"]))
        if value is None or wi.block_state_key(value["State"]) != member["state"]:
            return False
        original = None if member["sourceNbt"] is None else wi.decode_nbt(base64.b64decode(member["sourceNbt"])).root
        if value.get("OriginalTypedBlockEntity") != original:
            return False
    return True


def movement_audit(doc, runtime, failures):
    reference = doc.get("sourcePhysicsMovements", [])
    reference_path = doc.get("sourcePhysicsReference")
    if not reference_path or sha(reference_path) != doc.get("sourcePhysicsReferenceSha256"):
        failures.append("Corrected original source movement reference SHA is missing or changed")
        return [], False, False
    measured = json.loads(Path(reference_path).read_text(encoding="utf-8"))
    if measured.get("actual_fake_player_movements") != reference or len(reference) != 7 or not all(row.get("allSweptMovementChunksPreloaded") for row in reference):
        failures.append("Original source reference is not the exact seven fully preloaded sweep measurements")
        return [], False, False
    def equal(a, b):
        return len(a) == len(b) == 3 and all(abs(x-y) <= 1e-7 for x, y in zip(a, b))
    phases = {name: {row["purpose"]: row for row in runtime.get(key, [])}
              for name, key in (("control", "sourceNativeFabricMovements"), ("closed", "convertedClosedMovements"), ("open", "convertedOpenMovements"))}
    rows = []
    control_ok = True
    required_ok = True
    for source in reference:
        purpose = source["purpose"]
        row = {"purpose": purpose, "original_forge_displacement": source["actual_displacement"], "start": source["start"], "requested_delta": source["delta"]}
        for phase, values in phases.items():
            value = values.get(purpose)
            valid = value is not None and value.get("start") == source["start"] and value.get("delta") == source["delta"] and value.get("actual_displacement") == source["actual_displacement"]
            displacement = value.get("fabricDisplacement", []) if value else []
            row[phase + "_fabric_displacement"] = displacement
            row[phase + "_same_exact_source_start_and_request"] = valid
            if not valid:
                failures.append("Source-coordinate movement fixture differs: " + purpose + "/" + phase)
                control_ok = required_ok = False
            if phase == "control" and not equal(displacement, source["actual_displacement"]):
                control_ok = False
                failures.append("Unconverted Fabric source movement differs from original Forge sweep: " + purpose)
            if phase != "control" and purpose in {"tree_foliage_gap_west", "source_window_center", "source_ladder_entry"} and not equal(displacement, source["actual_displacement"]):
                required_ok = False
                failures.append("Required unchanged source passage/collision differs: " + purpose + "/" + phase)
            if phase == "open" and purpose in {"source_door_side", "source_door_center"} and not equal(displacement, source["delta"]):
                required_ok = False
                failures.append("Proposed open door blocks exact source passage: " + purpose)
        row["proposal_changes_from_original"] = [phase for phase in ("closed", "open") if not equal(row[phase+"_fabric_displacement"], source["actual_displacement"])]
        if row["proposal_changes_from_original"]:
            row["physics_acceptance"] = "PROPOSED_REQUIRES_USER_ACCEPTANCE"
        rows.append(row)
    return rows, control_ok, required_ok


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before", type=Path, required=True)
    parser.add_argument("--after", type=Path, required=True)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--runtime-report", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    doc = json.loads(args.input.read_text(encoding="utf-8"))
    runtime = json.loads(args.runtime_report.read_text(encoding="utf-8"))
    if doc.get("reviewRevision", "V7") != "V7":
        assert runtime.get("productionDescriptorGuard") == "PASS_BEFORE_QA_WORLD_CELL_READS_AND_MUTATIONS", "Source QA did not verify exact production descriptor bytes"
        assert runtime.get("exactProductionDescriptorSha256") == doc["descriptorSha256"], "Classpath/input descriptor hashes differ"
        for kind, expected in doc["descriptorSha256"].items():
            path = ROOT / "src/architecture/resources/bloodborne_dw/composite" / (kind + ".json")
            assert sha(path) == expected, "Independent verifier descriptor differs from reviewed runtime: " + kind
    before, after = World(args.before), World(args.after)
    converted = runtime.get("status", "").startswith("PASS_")
    members = {tuple(m["pos"]) for row in doc["objects"] for m in row["members"]}
    roots = {tuple(row["root"]): row for row in doc["objects"]}
    expected_masks = {row["ownerUuid"]: footprint(row) for row in doc["objects"]
                      if row["kind"].startswith("prototype_") and row["kind"] != "prototype_wall"}
    allowed = set(members) | roots.keys() | {p for mask in expected_masks.values() for p in mask}
    failures = []
    metadata = []
    state_changes = []
    unexpected_states = []
    for chunk, old_root in before.chunks.items():
        if chunk not in after.chunks:
            failures.append("Original terrain chunk missing: " + str(chunk))
        new_root = after.chunks.get(chunk, {})
        for key in old_root.keys() | new_root.keys():
            if key not in {"sections", "block_entities"}:
                typed_diff(old_root.get(key), new_root.get(key), f"chunk/{chunk}/{key}", metadata)
        old_sections, new_sections = before.sections(chunk), after.sections(chunk)
        for sy in old_sections.keys() | new_sections.keys():
            old_fields = wi.compound(old_sections[sy]) if sy in old_sections else {}
            new_fields = wi.compound(new_sections[sy]) if sy in new_sections else {}
            for key in old_fields.keys() | new_fields.keys():
                if key not in {"block_states", "Y"}:
                    typed_diff(old_fields.get(key), new_fields.get(key), f"chunk/{chunk}/section/{sy}/{key}", metadata)
            old, new = before.states(chunk, sy), after.states(chunk, sy)
            for i, (a, b) in enumerate(zip(old, new)):
                if a == b:
                    continue
                pos = (chunk[0] * 16 + i % 16, sy * 16 + i // 256, chunk[1] * 16 + (i // 16) % 16)
                row = {"pos": list(pos), "before": a, "after": b, "declared_architecture_cell": converted and pos in allowed}
                state_changes.append(row)
                if not row["declared_architecture_cell"] or (pos not in members and pos not in roots and a not in AIR):
                    unexpected_states.append(row)
    if unexpected_states:
        failures.append("Unplanned nonmember logical block-state changes across load/save: " + str(len(unexpected_states)))
    native_be_changes = []
    native_be_normalizations = []
    for pos, original in before.block_entities.items():
        if converted and pos in members:
            continue
        saved = after.block_entities.get(pos)
        normalized = wi.Tag(10, {k: v for k, v in wi.compound(original).items() if k != "keepPacked"})
        if saved == normalized:
            if original != saved:
                native_be_normalizations.append({"pos": list(pos), "removed": ["keepPacked"], "reason": "Chunk-loader packed flag; actual live before/after guard checked other fields"})
        elif original != saved:
            fields = []
            typed_diff(original, saved, str(pos), fields)
            native_be_changes.append({"pos": list(pos), "typed_differences": fields})
    if native_be_changes:
        failures.append("Foreign native block-entity typed data changed: " + str(len(native_be_changes)))
    light_checks = []
    parity = {tuple(row["pos"]): row for row in runtime.get("technicalLights", [])}
    for row in doc.get("technicalLightCells", []):
        pos = tuple(row["pos"])
        target = wi.decode_nbt(base64.b64decode(row["targetTypedState"])).root
        ok = pos not in members and pos not in roots
        ok &= before.state(pos) == after.state(pos) == wi.block_state_key(target) == "bloodborne_dw:source_hunter_lamp_light"
        ok &= pos not in before.block_entities and pos not in after.block_entities
        ok &= parity.get(pos, {}).get("runtimeParity") == "PASS_LIGHT9_EMPTY_COLLISION_EMPTY_SELECTION_REPLACEABLE_NOT_ISAIR_NO_BE"
        light_checks.append({"pos": list(pos), "state": after.state(pos), "status": "PASS" if ok else "FAIL",
            "live_parity_evidence": parity.get(pos), "saved_state_verifier_limit": "Palette proves identity; live runtime parity separately proves emission9 and empty native collision/selection"})
        if not ok:
            failures.append("Source technical light identity/live parity not retained: " + str(pos))
    owner_checks = []
    if converted:
        for pos, row in roots.items():
            if after.state(pos) != expected_root(row, doc.get("reviewRevision", "V7")):
                failures.append("Wrong final root state: " + row["key"])
        for pos in members - roots.keys():
            ladder = next((r for r in doc["objects"] if r["kind"] == "source_ladder_pair" and tuple(r["visual"]) == pos), None)
            expected = "bloodborne_dw:source_ladder_backing" if ladder else None
            actual = after.state(pos)
            if expected is not None and actual != expected or expected is None and actual not in {"minecraft:air", "bloodborne_dw:composite_cell"}:
                failures.append("Unconsumed/wrong source member state: " + str(pos))
        ledger_path = args.after / "data/bloodborne_dw_composite_owners.dat"
        ledger = wi.compound(wi.compound(wi.read_nbt(ledger_path).root)["data"])["cells"]
        found = defaultdict(dict)
        rows_by_uuid = {uuid_array(r["ownerUuid"]): r for r in doc["objects"]}
        for cell in ledger.value:
            fields = wi.compound(cell)
            pos = tuple(fields["pos"].value)
            for tag in fields["owners"].value:
                contribution = wi.compound(tag)
                identity = wi.compound(contribution["owner"])
                owner = tuple(identity["uuid"].value)
                request = rows_by_uuid.get(owner)
                if request is None or request["ownerUuid"] not in expected_masks:
                    failures.append("Unknown/unexpected source ledger owner: " + str(owner))
                    continue
                found[request["ownerUuid"]][pos] = contribution
                if tuple(identity["root"].value) != tuple(request["root"]) or identity["id"].value != "bloodborne_dw:" + request["kind"]:
                    failures.append("Owner registry/root mismatch: " + request["key"])
        for row in doc["objects"]:
            if row["kind"] == "prototype_wall":
                continue
            entity = after.block_entities.get(tuple(row["root"]))
            if entity is None:
                failures.append("Missing source owner BE: " + row["key"])
                continue
            fields = wi.compound(entity)
            if row["kind"] == "source_ladder_pair":
                ok = True
                for pos in (tuple(row["visual"]), tuple(row["physical"])):
                    be = after.block_entities.get(pos)
                    if be is None:
                        ok = False
                        continue
                    data = wi.compound(be)
                    provenance = wi.compound(data["Original"])
                    ok &= tuple(data["Owner"].value) == uuid_array(row["ownerUuid"])
                    ok &= provenance["ReviewMigrationSignature"].value == row["migrationSignature"]
                    ok &= source_members(provenance["RawSourceMembers"], row)
            else:
                payload = wi.compound(fields["payload"])
                mask = expected_masks[row["ownerUuid"]]
                entries = found[row["ownerUuid"]]
                ok = mask.keys() == entries.keys() and source_members(payload["SourceMembers"], row)
                ok &= payload["ReviewMigrationSignature"].value == row["migrationSignature"]
                ok &= payload["ReviewMigrationKey"].value == row["key"]
                ok &= [t.value for t in payload["SourceShift"].value] == row["sourceShift"]
                for pos, shape in mask.items():
                    if pos not in entries:
                        continue
                    value = entries[pos]
                    ok &= same_boxes(shape["collision"], boxes(value["collision"])) and same_boxes(shape["selection"], boxes(value["selection"]))
                    root_data = wi.compound(value["rootData"])
                    cached_payload = wi.decode_nbt(root_data["payload"].value).root
                    ok &= cached_payload == fields["payload"]
                    props = {k: v.value for k, v in wi.compound(root_data["properties"]).items()}
                    ok &= state_key(root_data["id"].value, props) == expected_root(row, doc.get("reviewRevision", "V7"))
            owner_checks.append({"key": row["key"], "status": "PASS" if ok else "FAIL"})
            if not ok:
                failures.append("Owner exact mask/art/typed provenance mismatch: " + row["key"])
    entity_rows = []
    missing = []
    changed_ids = []
    legacy_failures = []
    for identity, original in before.entities.items():
        saved = after.entities.get(identity)
        fields = wi.compound(original)
        if not fields["id"].value.startswith("bloodborne_rp:"):
            continue
        if saved is None:
            missing.append(list(identity))
            continue
        if wi.compound(saved)["id"] != fields["id"]:
            changed_ids.append(list(identity))
        diffs = []
        typed_diff(original, saved, "entity/" + str(identity), diffs)
        saved_fields = wi.compound(saved)
        dropped = [k for k in fields if k not in saved_fields]
        envelope = saved_fields.get("SourceLegacyPayload")
        provenance_diffs = []
        passthrough_diffs = []
        if envelope is None or envelope.type != wi.TAG_COMPOUND:
            provenance_diffs.append({"path": "SourceLegacyPayload", "before": "Schema1/Original typed source snapshot", "after": summary(envelope)})
        else:
            envelope_fields = wi.compound(envelope)
            if envelope_fields.get("Schema") != wi.Tag(wi.TAG_INT, 1):
                provenance_diffs.append({"path": "SourceLegacyPayload/Schema", "before": {"type": wi.TAG_INT, "value": 1}, "after": summary(envelope_fields.get("Schema"))})
            typed_diff(original, envelope_fields.get("Original"), "SourceLegacyPayload/Original", provenance_diffs)
        passthrough_diffs = unhandled_source_differences(fields, saved_fields)
        role_diffs = rp_v9_role_differences(fields, saved_fields) if doc.get("reviewRevision") == "V9" else []
        legacy_ok = not provenance_diffs and not passthrough_diffs and not role_diffs
        if not legacy_ok:
            legacy_failures.append({"id": fields["id"].value, "uuid_int_array": list(identity),
                "typed_original_envelope_differences": provenance_diffs, "unhandled_source_field_differences": passthrough_diffs,
                "v9_persistent_role_schema_differences": role_diffs})
        entity_rows.append({"id": fields["id"].value, "uuid_int_array": list(identity), "typed_field_changes": diffs,
                            "dropped_top_level_source_fields": dropped,
                            "legacy_live_provenance_and_unhandled_fields_preserved": legacy_ok,
                            "typed_original_envelope_differences": provenance_diffs,
                            "unhandled_source_field_differences": passthrough_diffs,
                            "v9_persistent_role_schema_differences": role_diffs,
                            "all_typed_nbt_preserved": not diffs})
    if missing or changed_ids:
        failures.append("Source RP entity UUID or ID lost/changed on save")
    if legacy_failures:
        failures.append("Live RP typed original envelope or unhandled source fields differ: " + str(len(legacy_failures)))
    movement_checks, source_control_ok, required_passages_ok = movement_audit(doc, runtime, failures)
    result = {"schema": "dreamwalker-source-review-postsave-independent-v2",
              "status": "PASS_ARCHITECTURE_AND_FOREIGN_DATA_WITH_EXPLICIT_RUNTIME_DIFFS" if converted and not failures else "FAIL_OR_NOT_RUN_SEE_LAYER_VERDICTS",
              "before": str(args.before), "after": str(args.after), "input_sha256": sha(args.input),
              "runtime_report_status": runtime.get("status"), "runtime_report_sha256": sha(args.runtime_report),
              "shape_comparison": "Exact boundary-partitioned union; equivalent cuboid decompositions after fractional reprojection are accepted",
              "empty_created_region_files": after.empty_region_files,
              "layer_verdicts": {"architecture_conversion": "AUDITED" if converted else "NOT_APPLIED_RUNTIME_AUTHOR_FAILED",
                  "nonmember_block_states": "PASS" if not unexpected_states else "FAIL_UNPLANNED_SAVE_BOUNDARY_CHANGES",
                  "foreign_typed_block_entities": "PASS" if not native_be_changes else "FAIL",
                  "source_technical_light_identity_and_live_parity": "PASS" if len(light_checks) == 2 and all(row["status"] == "PASS" for row in light_checks) else "FAIL_OR_NOT_SUPPLIED",
                  "source_rp_uuid_and_registry_ids": "PASS" if not missing and not changed_ids and len(entity_rows) == 15 else "FAIL",
                  "source_rp_live_typed_original_and_unhandled_fields": "PASS" if len(entity_rows) == 15 and not legacy_failures else "FAIL",
                  "source_rp_all_live_typed_nbt": "PASS" if all(r["all_typed_nbt_preserved"] for r in entity_rows) else "CHANGED_BY_GAME_ENTITY_LOAD_SAVE; RAW_ORIGINAL_IN_IMMUTABLE_PROVENANCE",
                  "original_vs_unconverted_fabric_source_movements": "PASS" if source_control_ok else "FAIL",
                  "required_source_passages_and_open_door": "PASS" if required_passages_ok else "FAIL",
                  "proposed_changed_object_physics_acceptance": "PENDING_USER_REVIEW" if any(row["proposal_changes_from_original"] for row in movement_checks) else "NO_MEASURED_CHANGE",
                  "metadata_dfu_lighting_tick_equivalence": "DIFF_RECORDED_SEPARATELY; NOT_AN_EXACT_SAVE_IDENTITY_CLAIM"},
              "counts": {"original_terrain_chunks": len(before.chunks), "saved_extra_chunks": len(after.chunks.keys() - before.chunks.keys()),
                  "logical_state_changes": len(state_changes), "unplanned_nonmember_state_changes": len(unexpected_states),
                  "typed_chunk_metadata_differences": len(metadata), "foreign_be_data_changes": len(native_be_changes),
                  "packed_be_flag_normalizations": len(native_be_normalizations), "source_rp_entities_checked": len(entity_rows),
                  "source_technical_light_cells_checked": len(light_checks), "source_rp_live_legacy_payload_failures": len(legacy_failures)},
              "failures": failures, "all_logical_state_changes": state_changes, "unplanned_nonmember_state_changes": unexpected_states,
              "typed_chunk_metadata_differences": metadata, "foreign_be_data_changes": native_be_changes,
              "foreign_be_packed_flag_normalizations": native_be_normalizations, "owner_mask_art_provenance_checks": owner_checks,
              "source_technical_light_checks": light_checks, "source_rp_live_legacy_payload_failures": legacy_failures,
              "actual_source_coordinate_movement_checks": movement_checks,
              "source_rp_entity_save_differences": entity_rows, "missing_source_rp_uuids": missing, "changed_source_rp_registry_ids": changed_ids,
              "limits": ["Source runtime author reports its loaded before/after guard separately; this tool audits immutable pre-load versus saved world",
                         "Original snapshot means exact typed prepared entity input after explicit registry/item migration; immutable ZIP retains unconverted source bytes",
                         "Vanilla engine/handled role changes remain separately recorded; preserved original envelope and unhandled source fields do not imply all live entity NBT is byte-identical",
                         "Actual source-coordinate movement and first-set user acceptance remain separate verdicts"]}
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "layers": result["layer_verdicts"], "counts": result["counts"]}))


if __name__ == "__main__":
    main()
