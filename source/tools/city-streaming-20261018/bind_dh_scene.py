"""Bind one exported pilot scene to an explicitly selected, closed world.

Adds only dreamwalker-dh-pilot/; never edits regions, player data or settings.
Requires Python 3.10+ on Windows. --check performs read-only validation.
"""
import argparse
import hashlib
import json
import msvcrt
import os
from pathlib import Path
import shutil
import tempfile
import zipfile


def ordinary(path):
    path = path.absolute()
    for part in (path, *path.parents):
        if part.is_symlink() or (part.exists() and part.stat().st_file_attributes & 1024):
            raise ValueError("Reparse point is not supported: " + str(part))
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scene", required=True, type=Path)
    parser.add_argument("--world", required=True, type=Path)
    parser.add_argument("--main-jar", required=True, type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    world = ordinary(args.world)
    if not (world / "level.dat").is_file() or not (world / "region").is_dir():
        raise ValueError("Select the existing Overworld save folder, containing level.dat and region/")
    destination = world / "dreamwalker-dh-pilot"
    if destination.exists():
        raise ValueError("Scene is already bound; preserve/remove that folder explicitly before replacing it")
    manifest_path = ordinary(args.scene / "scene.json")
    data_path = ordinary(args.scene / "city-boxes.bin.gz")
    if manifest_path.stat().st_size > 65536 or data_path.stat().st_size > 16_000_000:
        raise ValueError("Scene exceeds the pilot size limit")
    manifest = json.loads(manifest_path.read_text(encoding="utf8"))
    if manifest["schema"] != 1 or not 0 < manifest["totalBoxes"] <= 250000:
        raise ValueError("Unsupported pilot scene")
    data_hash = hashlib.sha256(data_path.read_bytes()).hexdigest()
    if manifest["dataSha256"] != data_hash:
        raise ValueError("Exported scene hash mismatch")
    with zipfile.ZipFile(ordinary(args.main_jar)) as jar:
        mod = json.loads(jar.read("fabric.mod.json"))
        catalog = jar.read("bloodborne_dw/static-architecture-v1274.json")
        if mod["id"] != "bloodborne_dw" or hashlib.sha256(catalog).hexdigest() != manifest["catalogSha256"]:
            raise ValueError("Main mod and scene catalog differ")
    binding = {key: manifest[key] for key in (
        "schema", "dataSha256", "catalogSha256", "readOnlyDhTerrain", "bounds", "totalBoxes", "exporterSha256"
    )}
    binding["worldDirectory"] = str(world)
    lock_path = ordinary(world / "session.lock")
    with lock_path.open("r+b") as lock:
        lock.seek(0)
        # Minecraft owns this byte while the selected world is open.
        msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        try:
            result = {"status": "CHECKED", "world": str(world), "destination": str(destination),
                      "boxes": manifest["totalBoxes"], "bounds": manifest["bounds"], "dataSha256": data_hash,
                      "note": "Use the same converted city and coordinates as this frozen scene; edits require a new export."}
            if not args.check:
                stage = Path(tempfile.mkdtemp(prefix=".dreamwalker-dh-bind-", dir=world))
                try:
                    shutil.copyfile(data_path, stage / "city-boxes.bin.gz")
                    shutil.copyfile(manifest_path, stage / "export-provenance.json")
                    (stage / "scene.json").write_text(json.dumps(binding, indent=2) + "\n", encoding="utf8")
                    # os.rename on Windows refuses an existing destination.
                    os.rename(stage, destination)
                except Exception:
                    # Only these three newly created ordinary files are ours.
                    for name in ("city-boxes.bin.gz", "export-provenance.json", "scene.json"):
                        path = ordinary(stage / name)
                        if path.exists():
                            path.unlink()
                    stage.rmdir()
                    raise
                result["status"] = "BOUND"
            print(json.dumps(result, indent=2))
        finally:
            lock.seek(0)
            msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)


if __name__ == "__main__":
    main()
