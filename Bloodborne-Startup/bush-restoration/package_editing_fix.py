"""Package editing fixes and distinct form IDs over the published Bush Restoration JAR."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
from zipfile import ZIP_DEFLATED, ZipFile
from generate_form_assets import encoded, form_resources


RELEASE_SHA = "4e8e7f4bd460982d7e15d722f8c7210d4d4f6dda22d6e8523fa79d8ff6a4406b"
ROOT = "dev/dreamwalker/bloodbornedw/"
WORLDEDIT_CONFIG = "bloodborne_dw.worldedit.mixins.json"
CREATIVE_CONFIG = "bloodborne_dw.creative-removal.mixins.json"
CREATIVE_REFMAP = "bloodborne_dw.creative-removal.refmap.json"
FORMS_CONFIG = "bloodborne_dw.distinct-forms.mixins.json"
FORMS_REFMAP = "bloodborne_dw.distinct-forms.refmap.json"
WORLDEDIT_MIXINS = (
    "WorldEditSelectionRefreshMixin",
    "WorldEditAirEditMixin",
    "WorldEditSnapshotMixin",
    "WorldEditNativeWriteMixin",
    "WorldEditNativeCarrierMixin",
)
CLASSES = (
    "composite/ForcedBlockRemoval",
    "composite/mixin/CreativeRemovalMixin",
    "composite/mixin/CreativeRemovalWriteAccess",
    "composite/mixin/SelectionAxeClientMixin",
    "composite/mixin/SelectionAxeItemMixin",
    "composite/mixin/SelectionAxeProgressMixin",
    "compat/worldedit/SelectionAxe",
    "compat/worldedit/WorldEditNativeWrites",
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("release", type=Path)
    parser.add_argument("remapped_build", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    if digest(args.release) != RELEASE_SHA:
        parser.error("input is not the published v22-bloodborne-bush-restoration.1 JAR")
    if args.output.exists():
        parser.error("output already exists")

    with ZipFile(args.release) as release, ZipFile(args.remapped_build) as build:
        build_refmap = json.loads(build.read(json.loads(build.read(CREATIVE_CONFIG))["refmap"]))
        creative, _ = mixin_resources(build, CREATIVE_CONFIG, CREATIVE_REFMAP, build_refmap)
        forms, form_mixins = mixin_resources(build, FORMS_CONFIG, FORMS_REFMAP, build_refmap)
        assets, catalogue_resources = form_resources(release)
        added = {**creative, **forms, **assets}
        for path in [*(ROOT + stem for stem in CLASSES), ROOT + "composite/DistinctForms", *sorted(form_mixins)]:
            added[path + ".class"] = build.read(path + ".class")
            for name in build.namelist():
                if name.startswith(path + "$") and name.endswith(".class"):
                    added[name] = build.read(name)
        if added.keys() & set(release.namelist()):
            parser.error("patch overlaps classes in the published JAR")

        manifest = json.loads(release.read("fabric.mod.json"))
        manifest["version"] += ".forms.1"
        manifest["mixins"].extend((CREATIVE_CONFIG, FORMS_CONFIG))
        manifest["entrypoints"]["main"].append("dev.dreamwalker.bloodbornedw.compat.worldedit.SelectionAxe")
        worldedit = json.loads(release.read(WORLDEDIT_CONFIG))
        worldedit["mixins"].extend(WORLDEDIT_MIXINS)
        replacements = {"fabric.mod.json": manifest, WORLDEDIT_CONFIG: worldedit, **catalogue_resources}

        args.output.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(args.output, "x", compression=ZIP_DEFLATED) as output:
            for entry in release.infolist():
                if entry.filename in replacements:
                    output.writestr(entry, encoded(replacements[entry.filename]))
                else:
                    with release.open(entry) as source, output.open(entry, "w") as target:
                        shutil.copyfileobj(source, target, 1024 * 1024)
            for name, contents in added.items():
                output.writestr(name, contents)
    print(f"{digest(args.output)}  {args.output}")


if __name__ == "__main__":
    main()
