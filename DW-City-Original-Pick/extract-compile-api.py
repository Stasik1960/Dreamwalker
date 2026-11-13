"""Create a class-only Fabric dependency for compiling Original Pick."""
import json
from pathlib import Path
import sys
from zipfile import ZIP_DEFLATED, ZipFile

source, destination = map(Path, sys.argv[1:])
with ZipFile(source) as archive, ZipFile(destination, "x", ZIP_DEFLATED) as target:
    metadata = json.loads(archive.read("fabric.mod.json"))
    target.writestr("fabric.mod.json", json.dumps({key: metadata[key] for key in ("schemaVersion", "id", "version")}))
    for name in archive.namelist():
        if name.endswith(".class"):
            target.writestr(name, archive.read(name))
