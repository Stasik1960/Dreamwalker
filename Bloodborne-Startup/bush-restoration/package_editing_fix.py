"""Apply the editing mixins to the exact published Bush Restoration JAR."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
from zipfile import ZIP_DEFLATED, ZipFile


RELEASE_SHA = "4e8e7f4bd460982d7e15d722f8c7210d4d4f6dda22d6e8523fa79d8ff6a4406b"
ROOT = "dev/dreamwalker/bloodbornedw/"
WORLDEDIT_CONFIG = "bloodborne_dw.worldedit.mixins.json"
CREATIVE_CONFIG = "bloodborne_dw.creative-removal.mixins.json"
CREATIVE_REFMAP = "bloodborne_dw.creative-removal.refmap.json"
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
        added = {}
        for stem in CLASSES:
            path = ROOT + stem
            added[path + ".class"] = build.read(path + ".class")
            for name in build.namelist():
                if name.startswith(path + "$") and name.endswith(".class"):
                    added[name] = build.read(name)
        if added.keys() & set(release.namelist()):
            parser.error("patch overlaps classes in the published JAR")

        manifest = json.loads(release.read("fabric.mod.json"))
        manifest["mixins"].append(CREATIVE_CONFIG)
        manifest["entrypoints"]["main"].append("dev.dreamwalker.bloodbornedw.compat.worldedit.SelectionAxe")
        worldedit = json.loads(release.read(WORLDEDIT_CONFIG))
        worldedit["mixins"].extend(WORLDEDIT_MIXINS)
        creative = json.loads(build.read(CREATIVE_CONFIG))
        build_refmap = json.loads(build.read(creative["refmap"]))
        mixin_paths = {ROOT + "composite/mixin/" + name for name in (*creative["mixins"], *creative["client"])}
        refmap = {
            "mappings": {name: mappings for name, mappings in build_refmap["mappings"].items() if name in mixin_paths},
            "data": {
                namespace: {name: mappings for name, mappings in entries.items() if name in mixin_paths}
                for namespace, entries in build_refmap.get("data", {}).items()
            },
        }
        creative["refmap"] = CREATIVE_REFMAP
        replacements = {"fabric.mod.json": manifest, WORLDEDIT_CONFIG: worldedit}

        args.output.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(args.output, "x", compression=ZIP_DEFLATED) as output:
            for entry in release.infolist():
                if entry.filename in replacements:
                    output.writestr(entry, json.dumps(replacements[entry.filename], indent=2) + "\n")
                else:
                    with release.open(entry) as source, output.open(entry, "w") as target:
                        shutil.copyfileobj(source, target, 1024 * 1024)
            output.writestr(CREATIVE_CONFIG, json.dumps(creative, indent=2) + "\n")
            output.writestr(CREATIVE_REFMAP, json.dumps(refmap, indent=2) + "\n")
            for name, contents in added.items():
                output.writestr(name, contents)
    print(f"{digest(args.output)}  {args.output}")


if __name__ == "__main__":
    main()
