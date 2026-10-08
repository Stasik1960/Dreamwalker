"""Independent actual middle-key evidence validation; JSON-only, no game or world IO."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import uuid

from verify_v10_partial_client import read, require, sha

PICK_STATUS = "PASS_ACTUAL_MIDDLE_KEY_CANONICAL_ITEM_SETTINGS_SOURCE_UNCHANGED"
PICK_INPUT = "actual pickItemKey queued once; MinecraftClient native doItemPick and Creative inventory packet"
FORBIDDEN = {"UUID", "Links", "VerticalOffset", "SourceLegacyPayload", "SourceShift", "MountY"}


def compound(value, label):
    require(isinstance(value, dict) and value.get("type") == 10 and isinstance(value.get("value"), dict), "Typed compound missing: " + label)
    return value["value"]


def contains_instance_data(value):
    if not isinstance(value, dict):
        return False
    children = value.get("value")
    if value.get("type") == 10 and isinstance(children, dict):
        return bool(FORBIDDEN & children.keys()) or any(contains_instance_data(v) for v in children.values())
    return value.get("type") == 9 and isinstance(children, list) and any(contains_instance_data(v) for v in children)


def valid_uuid(value):
    try:
        return isinstance(value, str) and str(uuid.UUID(value)) == value
    except (ValueError, TypeError, AttributeError):
        return False


def check_pick(pick, instance, source, canonical, model):
    require(pick.get("status") == PICK_STATUS and valid_uuid(instance), "Actual completed middle-key proof/UUID missing")
    require(pick.get("sourceUuid") == instance and pick.get("sourceRegistry") == "bloodborne_rp:" + source, "Middle source raw registry/UUID differ")
    require(pick.get("canonicalAsset") == canonical and pick.get("canonicalClientModel") == model and pick.get("pickedItem") == "bloodborne_rp:" + canonical + "_placer", "Middle canonical item/model differs")
    require(pick.get("inputPath") == PICK_INPUT, "Middle proof lacks actual key/native inventory packet")
    require(pick.get("authoritativePrePickItem") == pick.get("clientPrePickItem") == "minecraft:stick", "Middle input was not independently observed to change a non-placer sentinel")
    hit = pick.get("actualSourceHit", {})
    require(hit.get("type") == "ENTITY" and hit.get("entityUuid") == instance and hit.get("entityRegistry") == "bloodborne_rp:" + source, "Middle input selected another source entity")
    before, after = pick.get("sourceStableTypedBefore"), pick.get("sourceStableTypedAfter")
    require(isinstance(before, dict) and before and before == after and before.get("actualUuid") == instance and before.get("actualRegistry") == "bloodborne_rp:" + source, "Source stable identity/settings changed during middle input")
    require(all(pick.get(k) is True for k in ("serverClientSettingsMatch", "noInstanceIdentityCopied", "sourceUnchanged")), "Source/client/server matching flags missing")
    tag = compound(pick.get("pickedTypedNbt"), "actual picked stack")
    state = compound(tag.get("bloodborne_rp_object"), "actual picked object settings")
    for key, kind in (("Open", 1), ("Locked", 1), ("Scale", 5)):
        require(key in before and state.get(key) == {"type": kind, "value": before[key]}, "Typed picked source setting changed: " + key)
    if "DogsVisible" in before:
        require(state.get("DogsVisible") == {"type": 1, "value": before["DogsVisible"]}, "Typed picked dog visibility changed")
    display = compound(tag["display"], "actual picked custom name") if "display" in tag else {}
    if "CustomName" in before:
        require(display.get("Name") == {"type": 8, "value": before["CustomName"]}, "Typed picked source CustomName changed")
    else:
        require("Name" not in display, "Middle input added an unobserved custom name")
    require(not contains_instance_data(pick["pickedTypedNbt"]), "Picked item clones instance UUID/links/height/source provenance")
    return state


def check_inventory_readback(value, label):
    expected = value.get("originalInventorySnapshot")
    require(isinstance(expected, dict) and type(expected.get("selectedSlot")) is int and 0 <= expected["selectedSlot"] < 9, "Original inventory typed snapshot missing: " + label)
    slots = expected.get("slots")
    require(isinstance(slots, list) and len(slots) == expected.get("size") and len(slots) > 0, "Original inventory slot population differs: " + label)
    for i, slot in enumerate(slots):
        require(slot.get("slot") == i and isinstance(slot.get("item"), str) and type(slot.get("count")) is int and slot["count"] >= 0, "Typed inventory slot identity differs: " + label)
        compound(slot.get("typedStack"), label + " slot " + str(i))
    for side in ("server", "client"):
        require(value.get(side + "InventorySnapshot") == expected, "Actual " + side + " typed inventory is not the original: " + label)
        diff = value.get(side + "InventoryDifferences", {})
        require(diff.get("changedSlots") == [] and diff.get("selectedSlotMatches") is True and diff.get("expectedSelectedSlot") == diff.get("actualSelectedSlot") == expected["selectedSlot"], "Actual " + side + " inventory difference report is not empty: " + label)


def check_middle_picks(actual, audit, mode):
    require(mode in {"AUTHOR", "REENTER"} and actual.get("mode") == mode, "Actual client mode differs")
    rows = {r["asset"]: r for r in audit["rows"] if r["offeredCanonical"]}
    aliases = audit["existingAliasImplementation"]["mapping"]
    require(len(rows) == 69 and len(aliases) == 7, "Fresh semantic roster differs")
    primary = [r for r in actual["ordinaryRpCases"] if r["part"] == "authored-first-part"]
    require(len(primary) == 69 and {r["asset"] for r in primary} == set(rows) and len({r["uuid"] for r in primary}) == 69, "Exact 69 distinct canonical sources missing")
    require(actual.get("actualCanonicalMiddlePickCount") == 69, "Actual canonical middle-key count differs")
    for row in primary:
        check_pick(row.get("actualMiddlePick", {}), row["uuid"], row["asset"], row["asset"], rows[row["asset"]]["source"]["model"]["path"])
    old = actual.get("ordinaryAliasPickCases")
    count = 7 if mode == "AUTHOR" else 0
    require(isinstance(old, list) and len(old) == count and actual.get("actualOldAliasMiddlePickCount") == count, "Actual old-registry client pick count differs")
    replacement = {"status": "NOT_APPLICABLE_REENTER_DOES_NOT_CREATE_OLD_FIXTURES"}
    if mode == "AUTHOR":
        require({r.get("alias") for r in old} == set(aliases) and len({r.get("uuid") for r in old}) == 7, "Seven distinct old-registry sources missing")
        for row in old:
            source, canonical, instance = row["alias"], aliases[row["alias"]], row["uuid"]
            require(row.get("canonical") == canonical and row.get("status") == "PASS_ACTUAL_OLD_REGISTRY_CLIENT_MIDDLE_KEY_CANONICAL_SETTINGS_AND_CREATIVE_CLEANUP", "Old-registry key/cleanup failed")
            check_pick(row.get("actualMiddlePick", {}), instance, source, canonical, rows[canonical]["source"]["model"]["path"])
            setup, cleanup = row.get("technicalSetupServerThread", {}), row.get("actualCleanupServerThread", {})
            require(setup.get("readThread") == cleanup.get("readThread") == "ACTUAL_SERVER_THREAD" and setup.get("uuid") == instance and setup.get("registry") == "bloodborne_rp:" + source, "Actual old-registry technical setup identity differs")
            require(setup.get("stable") == row["actualMiddlePick"]["sourceStableTypedBefore"], "Actual old-registry setup payload differs from picked source")
            require(setup.get("nativeBefore") and cleanup.get("nativeAfter") == setup["nativeBefore"] and cleanup.get("present") is False and cleanup.get("creativeDropCount") == 0, "Old-registry ordinary cleanup/native support/zero-drop evidence missing")
        placed = actual.get("actualPickedItemReinstallation", {})
        require(placed.get("status") == "PASS_ACTUAL_MIDDLE_PICK_CANONICAL_STACK_ORDINARY_REPLACE_NEW_UUID_SETTINGS_AND_CLEANUP", "Picked-item ordinary re-placement missing")
        source = next((r for r in old if r["uuid"] == placed.get("sourceOldUuid")), None)
        require(source is not None and placed.get("canonicalAsset") == source["canonical"] and placed.get("actualPickedItem") == source["actualMiddlePick"]["pickedItem"] and placed.get("actualPickedTypedNbt") == source["actualMiddlePick"]["pickedTypedNbt"], "Re-placement did not use the actual old-source picked stack")
        new = placed.get("newUuid")
        server, cleanup = placed.get("actualServerThreadPlacement", {}), placed.get("actualCleanupServerThread", {})
        require(valid_uuid(new) and new not in {r["uuid"] for r in old + primary} and server.get("uuid") == new and server.get("registry") == "bloodborne_rp:" + source["canonical"], "Picked re-placement recreated a prior identity")
        state = compound(compound(placed["actualPickedTypedNbt"], "re-placed item")["bloodborne_rp_object"], "re-placed settings")
        require(server.get("open") == (state["Open"]["value"] == "1b") and server.get("locked") == (state["Locked"]["value"] == "1b") and server.get("scale") == float(str(state["Scale"]["value"]).rstrip("fF")) and server.get("name") == source["actualMiddlePick"]["sourceStableTypedBefore"].get("CustomName", ""), "Actual picked re-placement settings/CustomName changed")
        require(server.get("linksEmpty") is True and server.get("offset") == 0, "Picked re-placement copied source links/height")
        require(placed.get("inputPath") == "actual picked client stack copied only for actor inventory setup; ordinary use-key ItemUsageContext and attack-key packets", "Picked re-placement lacks actual ordinary input path")
        require(placed.get("nativeBefore") and cleanup.get("readThread") == "ACTUAL_SERVER_THREAD" and cleanup.get("nativeAfter") == placed["nativeBefore"] and cleanup.get("present") is False and cleanup.get("creativeDropCount") == 0, "Picked ordinary re-placement cleanup/native/zero-drop evidence missing")
        replacement = {"status": placed["status"], "sourceOldUuid": source["uuid"], "newUuid": new, "canonicalAsset": source["canonical"], "offsetReset": True, "sourceLinksNotCopied": True}
    restored = actual.get("actualOriginalInventoryRestorationAtExit", {})
    require(restored.get("readThread") == "ACTUAL_SERVER_THREAD" and restored.get("serverInventoryRestored") is True and restored.get("clientInventoryRestored") is True and type(restored.get("selectedSlot")) is int and 0 <= restored["selectedSlot"] < 9, "Actual vanilla inventory/hotbar restoration missing")
    check_inventory_readback(restored, "final saved inventory")
    if mode == "AUTHOR":
        before_menus = actual.get("originalActorRestorationBeforeMenus", {})
        require(before_menus.get("readThread") == "ACTUAL_SERVER_THREAD" and before_menus.get("serverInventoryRestored") is True and before_menus.get("clientInventoryRestored") is True, "Middle-key inventory changes were not restored before actual menus")
        check_inventory_readback(before_menus, "before actual menus")
    return {"status": "PASS_ACTUAL_MIDDLE_KEY_CANONICAL_SETTINGS_SOURCE_PRESERVATION_AND_CLEANUP", "mode": mode, "canonicalCount": 69, "oldRegistryCount": count, "ordinaryPickedItemReinstallation": replacement, "vanillaInventoryRestored": True, "actualCreativeTabEntries": "NOT_OBSERVED", "droppedRpPlacerItemEntity": "NOT_APPLICABLE_CREATIVE_DELETION_PRODUCES_ZERO_DROPS"}


def check_wrapper(path, audit, mode):
    wrapper = read(path)
    require(wrapper.get("status") == "PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT" and wrapper.get("exit_code") == 0, "Actual client normal exit/PASS required")
    raw = Path(wrapper["client_review_output"]["path"])
    actual = read(raw)
    require(sha(raw) == wrapper["client_review_output"]["sha256"] and actual == wrapper["client_review_output"]["result"], "Actual raw wrapper binding differs")
    require(actual.get("status") == "PASS_ACTUAL_CLIENT_V10_MENUS_ORDINARY_RP_PARTS_HEIGHT_NATIVE_OVERLAP_AND_SAVE" and actual.get("actualIntegratedSave") is True, "Actual whole client input/save stage failed")
    require(wrapper.get("artifact_sha256") == actual.get("productionJarSha256") == audit["productionJarSha256"], "Fresh current artifact binding differs")
    checked = check_middle_picks(actual, audit, mode)
    checked.update({"wrapper": str(path.resolve()), "wrapperSha256": sha(path), "raw": str(raw), "rawSha256": sha(raw)})
    return checked


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--wrapper", type=Path, required=True)
    p.add_argument("--catalogue-audit", type=Path, required=True)
    p.add_argument("--reenter-wrapper", type=Path)
    p.add_argument("--full-wrapper", type=Path)
    p.add_argument("--output", type=Path, required=True)
    a = p.parse_args()
    require(not a.output.exists(), "Never overwrite prior actual evidence")
    audit = read(a.catalogue_audit)
    results = [check_wrapper(a.wrapper, audit, "AUTHOR")]
    if a.reenter_wrapper:
        results.append(check_wrapper(a.reenter_wrapper, audit, "REENTER"))
    if a.full_wrapper:
        results.append(check_wrapper(a.full_wrapper, audit, "AUTHOR"))
    out = {"schema": "dw-v10-independent-actual-middle-key-v1", "status": "PASS_CURRENT_ACTUAL_CLIENT_MIDDLE_KEY_SCOPED_PROOFS_PENDING_USER_REVIEW", "productionJarSha256": audit["productionJarSha256"], "clients": results, "catalogueAudit": str(a.catalogue_audit.resolve()), "catalogueAuditSha256": sha(a.catalogue_audit), "limits": ["Technical old EntityType setup is not ordinary placement proof.", "Actual middle key complements, never replaces, independent saved old-entity/item/debug compatibility checks.", "Creative cleanup produces no ItemEntity; no dropped-placer proof is claimed.", "No manual visual acceptance, actual Creative tab membership or whole task completion claim."]}
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"status": out["status"], "path": str(a.output)}))


if __name__ == "__main__":
    main()
