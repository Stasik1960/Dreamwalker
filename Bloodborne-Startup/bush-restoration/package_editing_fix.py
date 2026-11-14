"""Package Distinct Forms and the building fixes over the published Bush Restoration JAR."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
from zipfile import ZIP_DEFLATED, ZipFile
from generate_form_assets import encoded, form_resources
from bundle_original_pick import nested_pick
from fence_assets import fence_replacements
from repair_screenshot_assets import screenshot_replacements


RELEASE_SHA = "4e8e7f4bd460982d7e15d722f8c7210d4d4f6dda22d6e8523fa79d8ff6a4406b"
ROOT = "dev/dreamwalker/bloodbornedw/"
PROTOTYPE_CONNECTIONS = ROOT + "architecture/wall/PrototypeWallBlock$Connections.class"
NATIVE_SHIFT_MIXIN = ROOT + "tool/mixin/NativePhysicalShiftMixin.class"
WORLDEDIT_CONFIG = "bloodborne_dw.worldedit.mixins.json"
CREATIVE_CONFIG = "bloodborne_dw.creative-removal.mixins.json"
CREATIVE_REFMAP = "bloodborne_dw.creative-removal.refmap.json"
FORMS_CONFIG = "bloodborne_dw.distinct-forms.mixins.json"
FORMS_REFMAP = "bloodborne_dw.distinct-forms.refmap.json"
BUILDING_CONFIG = "bloodborne_dw.building-fixes.mixins.json"
BUILDING_REFMAP = "bloodborne_dw.building-fixes.refmap.json"
WORLDEDIT_MIXINS = (
    "WorldEditSelectionRefreshMixin",
    "WorldEditAirEditMixin",
    "WorldEditSnapshotMixin",
    "WorldEditNativeWriteMixin",
    "WorldEditNativeCarrierMixin",
)
CLASSES = (
    "composite/ForcedBlockRemoval",
    "compat/worldedit/SelectionAxe",
    "compat/worldedit/WorldEditNativeWrites",
    "composite/DiagonalRpCollision",
    "composite/BuildingNeighbors",
    "building/CompositeGlassPass",
    "building/BbModelClickScope",
    "building/ConnectedFenceBlock",
    "building/ConnectedFenceColumnBlock",
    "building/ConnectedFences",
    "building/ConnectedFencesClient",
    "building/FenceWallMode",
    "building/DoorSounds",
    "building/CreativeOnlyUtilities",
    "building/ShutterFloorPlacement",
    "tool/QuarterBlockShift",
    *("compat/worldedit/mixin/" + name for name in WORLDEDIT_MIXINS),
)


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def mixin_resources(build, config_name, refmap_name, build_refmap):
    config = json.loads(build.read(config_name))
    package = config["package"].replace(".", "/") + "/"
    classes = {package + name for name in config["mixins"] + config.get("client", [])}
    refmap = {
        "mappings": {name: mappings for name, mappings in build_refmap["mappings"].items() if name in classes},
        "data": {
            namespace: {name: mappings for name, mappings in entries.items() if name in classes}
            for namespace, entries in build_refmap.get("data", {}).items()
        },
    }
    config["refmap"] = refmap_name
    return {config_name: encoded(config), refmap_name: encoded(refmap)}, classes


def connected_fence_resources(release, replacements):
    generated = fence_replacements(release)
    fences = generated["bloodborne_dw/unified-fences.json"]["fences"]
    catalogue = replacements["bloodborne_dw/debug_catalogue.json"]
    used = {row["temporaryId"] for row in catalogue["entries"] + catalogue.get("retiredEntries", [])}
    for row in fences:
        number = row["number"]
        if number in used:
            raise ValueError("Duplicate connected fence number " + number)
        used.add(number)
        identifier = "bloodborne_dw:" + row["id"]
        catalogue["entries"].append({"temporaryId": number, "finalId": None, "kind": "architecture",
                                     "name": row["name"], "registryId": identifier, "aliases": [identifier]})
        # Native loot handling also works when the player breaks the column's upper half.
        generated[f"data/bloodborne_dw/loot_tables/blocks/{row['id']}.json"] = {
            "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": identifier}],
                                                  "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        }
    for language in ("ru_ru", "en_us"):
        path = f"assets/bloodborne_dw/lang/{language}.json"
        replacements[path].update({key: value for key, value in generated.pop(path).items()
                                   if ".connected_fence_" in key})
    tag = "data/minecraft/tags/blocks/fences.json"
    generated[tag] = json.loads(release.read(tag)) if tag in release.namelist() else {"replace": False, "values": []}
    generated[tag]["values"].extend("bloodborne_dw:" + row["id"] for row in fences)
    return generated


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("release", type=Path)
    parser.add_argument("remapped_build", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--original-pick", type=Path, required=True, help="Original Pick 1.0.2 built from this checkout")
    parser.add_argument("--window-placement", type=Path, required=True, help="Remapped DW-City-Visuals window/placement slice")
    parser.add_argument("--version-suffix", default=".forms.5")
    args = parser.parse_args()
    if digest(args.release) != RELEASE_SHA:
        parser.error("input is not the published v22-bloodborne-bush-restoration.1 JAR")
    if args.output.exists():
        parser.error("output already exists")

    with ZipFile(args.release) as release, ZipFile(args.remapped_build) as build:
        build_refmap = json.loads(build.read(json.loads(build.read(CREATIVE_CONFIG))["refmap"]))
        creative, creative_mixins = mixin_resources(build, CREATIVE_CONFIG, CREATIVE_REFMAP, build_refmap)
        forms, form_mixins = mixin_resources(build, FORMS_CONFIG, FORMS_REFMAP, build_refmap)
        building, building_mixins = mixin_resources(build, BUILDING_CONFIG, BUILDING_REFMAP, build_refmap)
        assets, catalogue_resources = form_resources(release)
        added = {**creative, **forms, **building, **assets}
        for path in [*(ROOT + stem for stem in CLASSES), ROOT + "composite/DistinctForms",
                     *sorted(creative_mixins | form_mixins | building_mixins)]:
            added[path + ".class"] = build.read(path + ".class")
            for name in build.namelist():
                if name.startswith(path + "$") and name.endswith(".class"):
                    added[name] = build.read(name)
        if added.keys() & set(release.namelist()):
            parser.error("patch overlaps classes in the published JAR")

        manifest = json.loads(release.read("fabric.mod.json"))
        manifest["version"] += args.version_suffix
        with ZipFile(args.original_pick) as addon:
            archive, bundled_pick = nested_pick(release, addon, manifest["version"])
        manifest.setdefault("jars", []).append({"file": archive})
        added[archive] = bundled_pick
        manifest["mixins"].extend((CREATIVE_CONFIG, FORMS_CONFIG, BUILDING_CONFIG))
        manifest["entrypoints"]["main"].extend(("dev.dreamwalker.bloodbornedw.compat.worldedit.SelectionAxe",
                                                "dev.dreamwalker.bloodbornedw.building.ConnectedFences"))
        manifest["entrypoints"]["client"].append("dev.dreamwalker.bloodbornedw.building.ConnectedFencesClient")
        worldedit = json.loads(release.read(WORLDEDIT_CONFIG))
        worldedit["mixins"].extend(WORLDEDIT_MIXINS)
        replacements = {"fabric.mod.json": manifest, WORLDEDIT_CONFIG: worldedit, **catalogue_resources}
        replacements.update(screenshot_replacements(release))
        replacements.update(connected_fence_resources(release, replacements))
        visual_resources = Path(__file__).resolve().parents[2] / "DW-City-Visuals/src/main/resources"
        binary_replacements = {PROTOTYPE_CONNECTIONS: build.read(PROTOTYPE_CONNECTIONS)}
        with ZipFile(args.window_placement) as overlay:
            for name in overlay.namelist():
                if name.endswith(".class"):
                    if name in release.namelist():
                        binary_replacements[name] = overlay.read(name)
                    elif name in (NATIVE_SHIFT_MIXIN, ROOT + "tool/mixin/ImportedFenceWallModeMixin.class",
                                  ROOT + "tool/mixin/RpMobPatrolMixin.class", ROOT + "tool/DebugModels$Pose.class",
                                  ROOT + "tool/DebugToolModes$PatrolSelection.class") \
                            or name.startswith((ROOT + "composite/AutoStoneFence$", ROOT + "tool/ActorPatrol")):
                        added[name] = overlay.read(name)
                    else:
                        parser.error("unexpected window/placement overlay class " + name)
            studio_layouts = "bloodborne_dw/studio-layouts-v22.json"
            replacements[studio_layouts] = json.loads(overlay.read(studio_layouts))
        horizontal_config = "dw-bb-horizontal.mixins.json"
        horizontal = json.loads(release.read(horizontal_config))
        horizontal["mixins"].extend(("ImportedFenceWallModeMixin", "RpMobPatrolMixin"))
        replacements[horizontal_config] = horizontal
        client_tool_config = "dw-bb-tool-client.mixins.json"
        client_tool = json.loads(release.read(client_tool_config))
        client_tool["client"].append("NativePhysicalShiftMixin")
        replacements[client_tool_config] = client_tool
        for language in ("ru_ru", "en_us"):
            path = f"assets/bloodborne_dw/lang/{language}.json"
            updated = json.loads((visual_resources / path).read_bytes())
            for prefix in ("block", "item"):
                key = f"{prefix}.bloodborne_dw.dw_whiteness"
                replacements[path][key] = updated[key]
        for name in ("dragon_immune", "wither_immune"):
            path = f"data/minecraft/tags/blocks/{name}.json"
            tag = json.loads(release.read(path)) if path in release.namelist() else {"replace": False, "values": []}
            for identifier in json.loads(build.read(path))["values"]:
                if identifier not in tag["values"]:
                    tag["values"].append(identifier)
            replacements[path] = tag
        for name in replacements.keys() - set(release.namelist()):
            if name in added:
                parser.error("replacement overlaps an added path " + name)
            added[name] = encoded(replacements[name])

        args.output.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(args.output, "x", compression=ZIP_DEFLATED) as output:
            for entry in release.infolist():
                if entry.filename in replacements:
                    output.writestr(entry, encoded(replacements[entry.filename]))
                elif entry.filename in binary_replacements:
                    output.writestr(entry, binary_replacements[entry.filename])
                else:
                    with release.open(entry) as source, output.open(entry, "w") as target:
                        shutil.copyfileobj(source, target, 1024 * 1024)
            for name, contents in added.items():
                output.writestr(name, contents)
    print(f"{digest(args.output)}  {args.output}")


if __name__ == "__main__":
    main()
