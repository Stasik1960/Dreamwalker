"""Rebase City Original Pick 1.0.0 to the published Bloodborne Bush Restoration world."""

import argparse
from collections import Counter, defaultdict
from hashlib import file_digest, sha256
from io import BytesIO
import json
from pathlib import Path
from shutil import copyfileobj
import struct
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo


ADDON_SHA = "09d029e6eba4931b76186a1daf4ef4999ed6c3a0b178282e37ad2dd0ddae6abf"
INDEX = "city_original_pick/"
NATIVE = "assets/bloodborne_dw/native_grid/"
LEGACY_CATALOG = "fbefe5684ae68a9659fdf0b3eaf62a4e9936d0c5c3cf07f662e5fac8339eb112"
BUSH_CATALOG = "2bd740ff6ad95102f4c04521f2587a7516b432bd26ff6203dc10cd4d4fb88574"
ROUTES_PER_FILE = 64
QUAD_BYTES = 139


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_utf(stream):
    size, = struct.unpack(">H", stream.read(2))
    return stream.read(size)


def write_utf(value):
    return struct.pack(">H", len(value)) + value


def read_poses(addon):
    stream = BytesIO(addon.read(INDEX + "poses.bin"))
    require(stream.read(4) == b"DWP1", "Unexpected source pose table")
    require(read_utf(stream).decode("ascii") == LEGACY_CATALOG, "Unexpected catalog in source index")
    read_utf(stream)  # The original index pinned an earlier static-architecture release.
    count, = struct.unpack(">I", stream.read(4))
    poses = []
    for _ in range(count):
        registry, name = read_utf(stream), read_utf(stream)
        properties = {read_utf(stream): read_utf(stream) for _ in range(stream.read(1)[0])}
        poses.append((registry, name, properties))
    require(not stream.read(1), "Trailing data in source poses")
    return poses


def write_poses(poses, target_version, split_slabs):
    result = BytesIO()
    result.write(b"DWP1" + write_utf(BUSH_CATALOG.encode("ascii")) + write_utf(target_version.encode("ascii")))
    result.write(struct.pack(">I", len(poses)))
    split = 0
    for registry, name, properties in poses:
        suffix = {b"bottom": b"_half", b"top": b"_upper", b"double": b"_full"}.get(properties.get(b"type"))
        if registry in split_slabs and suffix:
            registry += suffix
            properties = {key: value for key, value in properties.items()
                          if key != b"type" and (suffix != b"_full" or key != b"waterlogged")}
            split += 1
        result.write(write_utf(registry) + write_utf(name) + bytes([len(properties)]))
        for key, value in properties.items():
            result.write(write_utf(key) + write_utf(value))
    return result.getvalue(), split


def edits_by_chunk(report):
    edits = defaultdict(dict)
    for namespace, dimension in (("minecraft", "overworld"), ("bloodborne_dw", "bloodborne_old")):
        for edit in report[dimension]["edits"]:
            x, y, z = edit["pos"]
            name = INDEX + f"chunks/{namespace}/{dimension}/{x >> 4}.{z >> 4}.bin"
            cell = ((y + 2048) << 8) | ((z & 15) << 4) | (x & 15)
            require(cell not in edits[name], f"Repeated report position {dimension} {edit['pos']}")
            edits[name][cell] = edit
    return edits


def read_meshes(archive, wanted):
    result = {}
    with archive.open(NATIVE + "meshes.bin") as stream:
        require(stream.read(4) == b"NGQ1", "Unexpected mesh table")
        count, = struct.unpack(">I", stream.read(4))
        for index in range(count):
            quads, = struct.unpack(">I", stream.read(4))
            blob = stream.read(quads * QUAD_BYTES)
            if index in wanted:
                result[index] = tuple(blob[i:i + QUAD_BYTES] for i in range(0, len(blob), QUAD_BYTES))
    require(result.keys() == wanted, "Missing residual mesh data")
    return result


def quad_key(coordinates):
    return tuple(sorted(zip(coordinates[::3], coordinates[1::3], coordinates[2::3])))


def mesh_keys(faces):
    def quantize(value):
        return round(max(0, min(1, value)) * 65535)

    return [quad_key(tuple(quantize(value) for vertex in range(4)
                           for value in struct.unpack_from(">3f", face, 11 + 32 * vertex)))
            for face in faces]


def verified_residuals(base, target, report):
    original_raw, target_raw = (archive.read(NATIVE + "catalog.json") for archive in (base, target))
    require(sha256(original_raw).hexdigest() == report["input_catalog"] == LEGACY_CATALOG, "Incorrect Seam Repair catalog")
    require(sha256(target_raw).hexdigest() == report["catalog"] == BUSH_CATALOG, "Incorrect Bush Restoration catalog")
    original, repaired = json.loads(original_raw), json.loads(target_raw)
    for table in ("cells", "meshes", "shapes", "materials", "optics", "wholeObjects"):
        require(original[table] == repaired[table][:len(original[table])], f"Existing {table} indices changed")
    for resource in ("bloodborne_dw/static-architecture-v1274.json", "bloodborne_dw/studio-layouts-v22.json"):
        require(base.read(resource) == target.read(resource), f"Static fragment numbering changed in {resource}")
    old_layout, new_layout = (json.loads(archive.read(NATIVE + "visual_physics.json")) for archive in (base, target))
    old_physics, new_physics = (layout["cellToPhysics"] for layout in (old_layout, new_layout))
    require(old_physics == new_physics[:len(old_physics)], "Existing native physics mapping changed")
    changes = {int(new): (old, tuple(removed)) for new, (old, removed) in report["residual_catalog"].items()}
    require(set(changes) == set(range(len(original["cells"]), len(repaired["cells"]))), "Incomplete residual provenance")
    meshes = read_meshes(base, {original["cells"][old]["mesh"] for old, _ in changes.values()})
    retained_meshes = read_meshes(target, {repaired["cells"][new]["mesh"] for new in changes})
    keys = {}
    for new, (old, removed) in changes.items():
        before, after = original["cells"][old], repaired["cells"][new]
        require(old_physics[old] == new_physics[new], f"Residual {new} changes its native carrier")
        require(all(after[key] == value for key, value in before.items() if key not in {"mesh", "outline", "key"}),
                f"Residual {new} changed other cell data")
        faces = meshes[before["mesh"]]
        removed = set(removed)
        require(removed <= set(range(len(faces))), f"Invalid removed faces for {new}")
        remaining = tuple(face for i, face in enumerate(faces) if i not in removed)
        require(remaining == retained_meshes[after["mesh"]], f"Residual {new} does not match the report")
        all_keys = mesh_keys(faces)
        kept_keys = {key for i, key in enumerate(all_keys) if i not in removed}
        removed_keys = {key for i, key in enumerate(all_keys) if i in removed}
        # Coincident removed and retained faces have no distinct owner in the quantized index.
        keys[new] = (old, frozenset(all_keys), frozenset(kept_keys - removed_keys))
    return keys


class Routes:
    def __init__(self, archive):
        self.archive = archive
        self.pages = {}
        self.added = {}
        self.first = (max(int(Path(name).stem) for name in archive.namelist()
                          if name.startswith(INDEX + "routes/") and name.endswith(".bin")) + 1) * ROUTES_PER_FILE

    def read(self, route):
        page, index = divmod(route, ROUTES_PER_FILE)
        if page not in self.pages:
            stream = BytesIO(self.archive.read(INDEX + f"routes/{page}.bin"))
            require(stream.read(4) == b"DWPR", f"Invalid route page {page}")
            rows = []
            for _ in range(stream.read(1)[0]):
                candidates = []
                for _ in range(stream.read(1)[0]):
                    pose, count = struct.unpack(">HH", stream.read(4))
                    faces = tuple(struct.iter_unpack(">12H", stream.read(count * 24)))
                    candidates.append((pose, faces))
                rows.append(tuple(candidates))
            require(not stream.read(1), f"Trailing data in route page {page}")
            self.pages[page] = rows
        return self.pages[page][index]

    def residual(self, route, original, retained):
        candidates = []
        for pose, faces in self.read(route):
            face_keys = [quad_key(face) for face in faces]
            require(set(face_keys) <= original, "Indexed source face is absent from its original city mesh")
            remaining = tuple(face for face, key in zip(faces, face_keys) if key in retained)
            if remaining:
                candidates.append((pose, remaining))
        if not candidates:
            return None
        if len(candidates) == 1:
            return -1 - candidates[0][0]
        return self.added.setdefault(tuple(candidates), self.first + len(self.added))

    def resources(self):
        rows = list(self.added)
        for start in range(0, len(rows), ROUTES_PER_FILE):
            chunk = rows[start:start + ROUTES_PER_FILE]
            stream = BytesIO()
            stream.write(b"DWPR" + bytes([len(chunk)]))
            for candidates in chunk:
                stream.write(bytes([len(candidates)]))
                for pose, faces in candidates:
                    stream.write(struct.pack(">HH", pose, len(faces)))
                    for face in faces:
                        stream.write(struct.pack(">12H", *face))
            yield INDEX + f"routes/{(self.first + start) // ROUTES_PER_FILE}.bin", stream.getvalue()


def rebase_chunks(addon, edits, residuals, routes):
    replacements = {}
    counts = Counter()
    names = set(addon.namelist())
    for name, updates in sorted(edits.items()):
        if name not in names:
            counts["no indexed row"] += len(updates)
            continue
        raw = addon.read(name)
        magic, size = struct.unpack_from(">4sI", raw)
        require(magic == b"DWPC" and len(raw) == 8 + size * 12, f"Invalid chunk index {name}")
        output = []
        matched = set()
        for cell, visual, route in struct.iter_unpack(">iii", raw[8:]):
            edit = updates.get(cell)
            if edit is None:
                output.append((cell, visual, route))
                continue
            matched.add(cell)
            new = edit["after"]
            if visual != edit["visual"]:
                counts["outdated row removed"] += 1
                continue
            if new == visual:
                output.append((cell, visual, route))
                continue
            if new is None:
                counts["replaced cell removed"] += 1
                continue
            old, original, retained = residuals[new]
            require(old == visual, f"Unexpected original visual at {edit['pos']}")
            # A direct pose does not store per-face attribution. Its remaining geometry cannot be identified.
            recovered = routes.residual(route, original, retained) if route >= 0 else None
            if recovered is None:
                counts["no proven retained source"] += 1
            else:
                output.append((cell, new, recovered))
                counts["residual reindexed"] += 1
        counts["no indexed row"] += len(updates) - len(matched)
        replacements[name] = struct.pack(">4sI", b"DWPC", len(output)) + b"".join(
            struct.pack(">iii", *row) for row in output)
    return replacements, counts


def package(addon, base, target, report, output, addon_version):
    residuals = verified_residuals(base, target, report)
    poses = read_poses(addon)
    version = json.loads(target.read("fabric.mod.json"))["version"]
    slabs_path = "bloodborne_dw/distinct-slabs.json"
    split_slabs = ({b"bloodborne_dw:" + name.encode("ascii") for name in json.loads(target.read(slabs_path))}
                   if slabs_path in target.namelist() else set())
    poses_bytes, split_count = write_poses(poses, version, split_slabs)
    routes = Routes(addon)
    replacements, counts = rebase_chunks(addon, edits_by_chunk(report), residuals, routes)
    replacements[INDEX + "poses.bin"] = poses_bytes
    manifest = json.loads(addon.read("fabric.mod.json"))
    manifest["version"] = addon_version
    replacements["fabric.mod.json"] = (json.dumps(manifest, indent=2) + "\n").encode("utf-8")
    with ZipFile(output, "x", ZIP_DEFLATED) as result:
        for info in addon.infolist():
            if info.filename in replacements:
                result.writestr(info, replacements[info.filename])
            else:
                with addon.open(info) as source, result.open(info, "w") as destination:
                    copyfileobj(source, destination, 1024 * 1024)
        for name, contents in routes.resources():
            result.writestr(ZipInfo(name), contents, compress_type=ZIP_DEFLATED)
    return {"target_version": version, "indexed_slab_forms": split_count, "new_routes": len(routes.added), **counts}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original_pick", type=Path)
    parser.add_argument("seam_repair_jar", type=Path)
    parser.add_argument("target_dreamwalker_jar", type=Path)
    parser.add_argument("bush_report", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--addon-version", default="1.0.1")
    args = parser.parse_args()
    with args.original_pick.open("rb") as stream:
        require(file_digest(stream, "sha256").hexdigest() == ADDON_SHA, "Expected the original City Original Pick 1.0.0 JAR")
    with ZipFile(args.original_pick) as addon, ZipFile(args.seam_repair_jar) as base, ZipFile(args.target_dreamwalker_jar) as target:
        counts = package(addon, base, target, json.loads(args.bush_report.read_bytes()), args.output, args.addon_version)
    print(json.dumps(counts, indent=2))


if __name__ == "__main__":
    main()
