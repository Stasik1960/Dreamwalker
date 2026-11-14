"""Publish the playable map while leaving live player saves and chat on the server."""

import argparse
import gzip
import json
import stat
import sys
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "bloodborne complete/tools"))
from world_io import compound, encode_nbt, read_nbt


def write_map(world: Path, target: Path) -> None:
    private = {"playerdata", "stats", "advancements", "rpchat", "session.lock", "level.dat_old"}
    required = {"level.dat", "region/r.0.-2.mca", "region/r.0.-3.mca", "data/fabricRegistry.dat"}
    included = set()
    target.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(target, "x", compression=ZIP_DEFLATED, compresslevel=6) as output:
        for file in sorted(world.rglob("*")):
            if getattr(file.lstat(), "st_file_attributes", 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT:
                raise ValueError(f"Unexpected reparse point: {file}")
            if not file.is_file():
                continue
            relative = file.relative_to(world)
            if private.intersection(relative.parts):
                continue
            name = relative.as_posix()
            included.add(name)
            archive_name = "bloodborne/" + name
            if name == "level.dat":
                level = read_nbt(file)
                compound(compound(level.root)["Data"]).pop("Player", None)
                output.writestr(archive_name, gzip.compress(encode_nbt(level), mtime=0))
            elif name == "data/dw_gm_warps.dat":
                warps = read_nbt(file)
                compound(compound(warps.root)["data"]).pop("Back", None)
                output.writestr(archive_name, gzip.compress(encode_nbt(warps), mtime=0))
            elif name == "data/cpm.json":
                cpm = json.loads(file.read_text(encoding="utf-8"))
                for player_field in ("skins", "player_scaling", "player_scaling_settings"):
                    if player_field in cpm:
                        cpm[player_field] = {}
                output.writestr(archive_name, json.dumps(cpm, ensure_ascii=False).encode("utf-8"))
            else:
                output.write(file, archive_name)
    if not required <= included:
        raise ValueError(f"Missing required world files: {required - included}")
    with ZipFile(target) as verify:
        if verify.testzip() is not None or len(verify.namelist()) != len(included):
            raise ValueError("Release map failed archive verification")
    print(f"Playable map: {target}, {len(included)} files")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world", type=Path)
    parser.add_argument("target", type=Path)
    args = parser.parse_args()
    write_map(args.world, args.target)
