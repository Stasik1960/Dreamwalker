"""Bundle the rebased City Original Pick add-on with DreamWalker."""

from hashlib import sha256
from io import BytesIO
import json
from struct import pack, unpack
from zipfile import ZIP_DEFLATED, ZipFile


ADDON_ID = "dw_city_original_pick"
ADDON_VERSION = "1.0.4"
ADDON_FILE = f"META-INF/jars/dreamwalker-city-original-pick-{ADDON_VERSION}.jar"
POSE_TABLE = "city_original_pick/poses.bin"
VISUAL_SOURCES = "city_original_pick/compatibility-visuals.json"
LAYOUTS = {
    "assets/bloodborne_dw/native_grid/catalog.json":
        "2bd740ff6ad95102f4c04521f2587a7516b432bd26ff6203dc10cd4d4fb88574",
    "assets/bloodborne_dw/native_grid/visual_physics.json":
        "6d5df24582514d3e2f8e38d42e85d51d9f32a9b613a0de9ada7adf6b439fabba",
    "bloodborne_dw/static-architecture-v1274.json":
        "adc741055795a187c7a4497abebed371eeed5b3d6082dc770cee6e77ff6fdd0d",
    "bloodborne_dw/studio-layouts-v22.json":
        "8dddd89b544ebd7ce5422e217d0808868450c6a07d5d66516e010913e11e914a",
}


def read_utf(stream):
    size, = unpack(">H", stream.read(2))
    return stream.read(size).decode("ascii")


def write_utf(value):
    data = value.encode("ascii")
    return pack(">H", len(data)) + data


def nested_pick(release, addon, target_version):
    """Retarget the verified city index and return a Fabric nested JAR entry."""
    for name, expected in LAYOUTS.items():
        if sha256(release.read(name)).hexdigest() != expected:
            raise ValueError(f"Original Pick requires the Bush Restoration layout at {name}")

    manifest = json.loads(addon.read("fabric.mod.json"))
    if (manifest["id"], manifest["version"], manifest["environment"]) != (ADDON_ID, "1.0.2", "client"):
        raise ValueError("Expected the client City Original Pick 1.0.2 JAR")

    poses = BytesIO(addon.read(POSE_TABLE))
    catalog = LAYOUTS["assets/bloodborne_dw/native_grid/catalog.json"]
    if poses.read(4) != b"DWP1" or read_utf(poses) != catalog:
        raise ValueError("City Original Pick 1.0.2 contains an incompatible poses table")
    if not read_utf(poses).endswith(".forms.2"):
        raise ValueError("City Original Pick 1.0.2 was rebased to a different source version")
    fallback = json.loads(addon.read(VISUAL_SOURCES))
    if (fallback.get("schema"), fallback.get("catalogSha256")) != (2, catalog) or not fallback.get("visuals"):
        raise ValueError("City Original Pick is missing the verified native visual sources")

    manifest["version"] = ADDON_VERSION
    manifest["depends"]["bloodborne_dw"] = "=" + target_version
    updated = {
        "fabric.mod.json": (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf-8"),
        POSE_TABLE: b"DWP1" + write_utf(catalog) + write_utf(target_version) + poses.read(),
    }
    result = BytesIO()
    with ZipFile(result, "w", ZIP_DEFLATED) as nested:
        for entry in addon.infolist():
            data = updated[entry.filename] if entry.filename in updated else addon.read(entry)
            nested.writestr(entry, data)
    return ADDON_FILE, result.getvalue()
