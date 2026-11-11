from hashlib import file_digest
import json
from pathlib import Path
import shutil
from zipfile import ZIP_DEFLATED, ZipFile


root = Path(__file__).resolve().parent
step2 = root.parent / "state-startup-step2" / "Seam-Repair-Startup-Step2.jar"
candidate = root / "DreamWalker-v22-Seam-Repair-Compact-Startup.jar"
expected_sha = "8e91a2dcddb80d5c4c18c677a8d988280a8b44b5c8c033c053f0cbdda735c37d"

with step2.open("rb") as stream:
    if file_digest(stream, "sha256").hexdigest() != expected_sha:
        raise ValueError("Unexpected Step 2 archive")

additions = {}
mixins = []
for name in ("core", "selectors"):
    module = root / name
    [config] = module.glob("*.mixins.json")
    mixins.append(config.name)
    classes = module / "classes"
    class_files = {
        file.relative_to(classes).as_posix(): file for file in classes.rglob("*.class")
    }
    descriptor = json.loads(config.read_bytes())
    package = descriptor["package"].replace(".", "/")
    for mixin in descriptor["client"]:
        if f"{package}/{mixin.replace('.', '/')}.class" not in class_files:
            raise ValueError(f"Missing compiled mixin {mixin}")
    for entry, file in {config.name: config, **class_files}.items():
        if entry in additions:
            raise ValueError(f"Duplicate addition {entry}")
        additions[entry] = file

pending = candidate.with_suffix(".tmp")
with ZipFile(step2) as original, ZipFile(pending, "w") as output:
    if collisions := original.namelist() & additions.keys():
        raise ValueError(f"Unexpected overwritten entries {collisions}")
    for entry in original.infolist():
        if entry.filename == "fabric.mod.json":
            manifest = json.loads(original.read(entry))
            manifest.setdefault("mixins", []).extend(mixins)
            output.writestr(entry, json.dumps(manifest, ensure_ascii=False).encode("utf-8"))
        else:
            with original.open(entry) as source, output.open(entry, "w") as destination:
                shutil.copyfileobj(source, destination, 1024 * 1024)
    for entry, file in sorted(additions.items()):
        output.write(file, entry, compress_type=ZIP_DEFLATED)

with ZipFile(step2) as original, ZipFile(pending) as output:
    for entry in original.infolist():
        if entry.filename != "fabric.mod.json":
            copied = output.getinfo(entry.filename)
            if (entry.file_size, entry.CRC) != (copied.file_size, copied.CRC):
                raise ValueError(f"Changed original entry {entry.filename}")
    if len(output.namelist()) != len(original.namelist()) + len(additions):
        raise ValueError("Unexpected archive inventory")
pending.replace(candidate)
with candidate.open("rb") as stream:
    print(file_digest(stream, "sha256").hexdigest(), candidate)
