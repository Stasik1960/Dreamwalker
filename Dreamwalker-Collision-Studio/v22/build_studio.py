"""Build the offline V22 Collision Studio from the release JAR and the V4 editor."""

import argparse
import base64
import hashlib
import io
from itertools import product
import json
from math import cos, radians, sin
from pathlib import Path
import re
import struct
from zipfile import ZIP_DEFLATED, ZipFile


ROOT = Path(__file__).resolve().parent
UPSTREAM_SHA256 = "d40bb2a4a6c03f4b7c2029b5fd17673e7fff95f775e407846e741ca26c53bd66"
REQUESTED = set("""
    91093 91075 91074 91060 91057 91056 91055 91047 91046 91045 91044 91043
    91042 91041 91040 91039 91037 91035 91034 91010 91012 91006 91005 91003 91001
    93154 93153 93152 93147 90031 90266 93113 93117 93116 93115 90477 90869
    90873 90870 90841 90960 90986 93068 93066 93001 90797 90373 90001 93241
""".split())
PLAIN_DOOR = "bloodborne_dw:door_90960_plain"
WHOLE_WINDOW = "bloodborne_dw:source_pressure_plate_window_bottom_4"
SHUTTER_WINDOW = "bloodborne_dw:source_shutter_window"
SHUTTER_LAYOUT_SOURCES = {
    "bloodborne_dw:source_spruce_trapdoor_bottom": {
        "variant=0,open=false": "variant=0,open=false",
        "variant=1,open=false": "variant=0,open=true",
    },
    "bloodborne_dw:source_spruce_trapdoor_open": {
        "variant=0,open=false": "variant=0,open=true",
    },
}
DOORS_WITH_SOURCE_PARTS = {"bloodborne_dw:source_birch_grille_door", "bloodborne_dw:source_dark_arch_door"}
RETIRED_EDITOR_MODELS = {
    "bloodborne_dw:source_pressure_plate_window_end_6",
    "bloodborne_dw:source_pressure_plate_window_top_2",
    "bloodborne_dw:source_shutter_window_surround",
    *SHUTTER_LAYOUT_SOURCES,
}
WINDOW = re.compile(r"window|остекл|витраж|окн[аоеу]|окон|подокон", re.IGNORECASE)
ZERO = [0, 0, 0]
PIVOT = [8, 8, 8]
FACES = {"east", "west", "up", "down", "south", "north"}


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def js_json(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def id_parts(identifier):
    return identifier.split(":", 1) if ":" in identifier else ("minecraft", identifier)


def resource(identifier, folder, extension):
    namespace, path = id_parts(identifier)
    return f"assets/{namespace}/{folder}/{path}.{extension}"


def default_uv(face, low, high):
    x, y, z = low
    X, Y, Z = high
    return {
        "down": [x, 16 - Z, X, 16 - z], "up": [x, z, X, Z],
        "north": [16 - X, 16 - Y, 16 - x, 16 - y], "south": [x, 16 - Y, X, 16 - y],
        "west": [z, 16 - Y, Z, 16 - y], "east": [16 - Z, 16 - Y, 16 - z, 16 - y],
    }[face]


class Assets:
    def __init__(self, jar, vanilla, data, resources=None):
        self.jar = jar
        self.vanilla = vanilla
        self.resources = resources
        self.patches = ({path.relative_to(resources).as_posix(): path for path in resources.rglob("*") if path.is_file()}
                        if resources else {})
        self.files = set(jar.namelist()) | self.patches.keys()
        self.models = data["models"]
        self.textures = data["textures"]

    def read(self, path):
        if path in self.patches:
            return self.patches[path].read_bytes()
        return self.jar.read(path)

    def patch_digest(self):
        if not self.patches:
            return None
        index = {name: digest(path) for name, path in sorted(self.patches.items())}
        return hashlib.sha256(js_json(index).encode("utf-8")).hexdigest()

    def load(self, path, profile):
        overlay = f"dw_city_visuals/{profile}/{path}"
        if overlay in self.files:
            return self.read(overlay)
        if path in self.files:
            return self.read(path)
        return self.vanilla.read(path)

    def resolve_model(self, identifier, profile, chain=()):
        if identifier in chain:
            raise ValueError(f"Circular model inheritance: {chain + (identifier,)}")
        doc = json.loads(self.load(resource(identifier, "models", "json"), profile))
        if doc.get("parent"):
            parent = self.resolve_model(doc["parent"], profile, chain + (identifier,))
            return {**parent, **doc, "textures": {**parent.get("textures", {}), **doc.get("textures", {})}}
        return doc

    def register_texture(self, name, profile):
        key = f"v22:{profile}:{name}"
        if key not in self.textures:
            raw = self.load(resource(name, "textures", "png"), profile)
            width, height = struct.unpack(">II", raw[16:24])
            self.textures[key] = {
                "source": "data:image/png;base64," + base64.b64encode(raw).decode("ascii"),
                "width": width, "height": height, "name": name,
            }
        return key

    def register_model(self, identifier, profile):
        key = f"v22:{profile}:{identifier}"
        if key in self.models:
            return key
        doc = self.resolve_model(identifier, profile)
        cubes = []
        for element in doc.get("elements", []):
            low, high = element["from"], element["to"]
            pivot = element.get("rotation", {})
            rotation = [0, 0, 0]
            if pivot:
                rotation["xyz".index(pivot["axis"])] = pivot["angle"]
            faces = {}
            for direction, face in element["faces"].items():
                if direction not in FACES:
                    raise ValueError(f"Unknown face {identifier}: {direction}")
                name = face["texture"]
                seen = set()
                while name.startswith("#"):
                    if name in seen:
                        raise ValueError(f"Circular texture reference: {identifier}: {name}")
                    seen.add(name)
                    name = doc["textures"][name[1:]]
                texture = self.register_texture(name, profile)
                image = self.textures[texture]
                uv = face.get("uv", default_uv(direction, low, high))
                uv = [value * image["width" if i % 2 == 0 else "height"] / 16 for i, value in enumerate(uv)]
                faces[direction] = {"texture": texture, "uv": uv, "rotation": face.get("rotation", 0)}
            cubes.append({"from": low, "to": high, "origin": pivot.get("origin", ZERO),
                          "rotation": rotation, "rescale": pivot.get("rescale", False), "faces": faces})
        if not cubes:
            raise ValueError(f"Model has no renderable elements: {identifier}")
        self.models[key] = {"kind": "java", "groups": [], "cubes": cubes}
        return key

    def part(self, part, profile):
        identifier = part.get("altModel", part["model"]) if profile == "alt" else part["model"]
        # Match the game's placement pitch, yaw and extraYaw signs.
        return {
            "model": self.register_model(identifier, profile),
            "offset": part.get("offset", ZERO),
            "pivot": part.get("pivot", PIVOT),
            "rotation": [-part.get("pitch", 0), -part.get("yaw", 0), 0],
            **({"extraRotation": [0, part["extraYaw"], 0],
                "extraPivot": part.get("extraPivot", PIVOT)} if part.get("extraYaw") else {}),
        }


def boxes(bounds, mode):
    result = []
    for box in bounds:
        low, high = box["from"], box["to"]
        size = [b - a for a, b in zip(low, high)]
        if min(size) <= 0:
            continue
        result.append({"id": f"baseline-{mode}-{len(result)}", "type": "box", "mode": mode,
                       "name": {"players": "Игроки", "blocks": "Блоки", "outline": "Обводка"}[mode]
                       + f" {len(result) + 1}",
                       "position": [(a + b) / 2 for a, b in zip(low, high)],
                       "size": size, "rotation": ZERO})
    return result


def baseline_objects(player, placement, selection):
    return boxes(player, "players") + boxes(placement, "blocks") + boxes(selection, "outline")


def shape_bounds(pose, profile):
    collision = pose.get("collision", [])
    player = pose.get("simpleCollisionByRotation", [collision])[0]
    player = pose.get("altCollision", player) if profile == "alt" else player
    return baseline_objects(player, player, pose.get("selection", collision))


def composite_entry(row, recipe, assets, installation_poses=(), legacy_component=False):
    states = []
    for variant, value in enumerate(recipe["variants"]):
        for position in ("closed", "open"):
            pose = value["poses"].get(position)
            if pose is None or position == "open" and not recipe["openable"]:
                continue
            opened = position == "open"
            parts = pose["parts"]
            if (not opened and row["registryId"] == "bloodborne_dw:source_allium"
                    and [part["model"] for part in parts] ==
                    ["bloodborne_dw:remaining/source/minecraft/block/allium"]):
                # The closed recipe points at a short decorative source panel.
                # Use the full gate components with the hinges at their rest angles.
                parts = [
                    {**part, "yaw": 0} if part["model"].rsplit("/", 1)[-1].startswith(("left_", "right_")) else part
                    for part in value["poses"]["open"]["parts"]
                ]
            label = "открыто" if opened else "закрыто" if recipe["openable"] else "исходное"
            if installation_poses or legacy_component:
                if variant in installation_poses:
                    label = "цельное окно" if row["registryId"] == WHOLE_WINDOW else "сборка"
                else:
                    label = "старый фрагмент"
                if recipe["openable"]:
                    label += " · открыто" if opened else " · закрыто"
            states.append({
                "key": f"variant={variant},open={str(opened).lower()}",
                "label": f"Вариант {variant + 1} · {label}",
                "selector": {"variant": variant, "open": opened, "rotation": 0},
                "parts": [assets.part(part, "original") for part in parts],
                "baseline": shape_bounds(pose, "original"),
            })
    if installation_poses:
        priority = {variant: index for index, variant in enumerate(installation_poses)}
        states.sort(key=lambda state: priority.get(state["selector"]["variant"], len(priority)))
    return {"id": row["registryId"], "number": row["temporaryId"], "name": row["name"],
            "kind": "composite", "aliases": row.get("aliases", []), "states": states}


def rp_cube_bounds(model, scale):
    def around(point, pivot, rotation):
        point = [value - center for value, center in zip(point, pivot)]
        for angle, (a, b) in zip(rotation, ((1, 2), (2, 0), (0, 1))):
            if angle:
                cosine, sine = cos(radians(angle)), sin(radians(angle))
                x, y = point[a], point[b]
                point[a], point[b] = x * cosine - y * sine, x * sine + y * cosine
        return [value + center for value, center in zip(point, pivot)]

    groups = {group["name"]: group for group in model["groups"]}
    bounds = []
    for cube in model["cubes"]:
        corners = [around(point, cube["origin"], cube["rotation"])
                   for point in product(*zip(cube["from"], cube["to"]))]
        group = groups.get(cube.get("group"))
        while group is not None:
            corners = [around(point, group["origin"], group["rotation"]) for point in corners]
            group = groups.get(group.get("parent"))
        axes = list(zip(*corners))
        bounds.append({"from": [min(axis) * scale for axis in axes], "to": [max(axis) * scale for axis in axes]})
    return bounds


def rp_entry(row, entry, jar, data):
    identifier = row["registryId"].split(":", 1)[1]
    reviewed = {
        "npc_window": [{"from": [-16.5, -30, 12], "to": [16.5, 34.5, 15]}],
        "wood_gate": [{"from": [-119, 16, -8], "to": [119, 171, 8]}],
    }
    # The unrotated placement uses the registered EntityDimensions for V12
    # props. Passive props are bounded by their visual envelope, and passages
    # keep the full closed collider until opened (RpObjectEntity.geometry).
    passages = {"door_1", "door_2", "main_gate", "small_gate"}
    non_colliding = {"curtainsmall", "curtain_half", "curtains", "door_empty", "gate_empty", "trapdoor", "chandelier_small"}
    info = entry.get("asset", {})
    part = entry["states"][0]["parts"][0]
    visual = rp_cube_bounds(data["models"][part["model"]], info["scale"])
    half_width, height = max(.1, info["width"]) * 8, max(.1, info["height"]) * 16
    low, high = [-half_width, 0, -half_width], [half_width, height, half_width]
    mechanisms = identifier.startswith("lever_") or identifier in {"chest", "ladder", "cage_obj_1", "cage_obj_2", "cage_obj_3"}
    if identifier not in passages and not mechanisms and not info["clips"]:
        clipped_low = [max(value, min(box["from"][axis] for box in visual)) for axis, value in enumerate(low)]
        clipped_high = [min(value, max(box["to"][axis] for box in visual)) for axis, value in enumerate(high)]
        if all(a < b for a, b in zip(clipped_low, clipped_high)):
            low, high = clipped_low, clipped_high
    for state in entry["states"]:
        player = reviewed.get(identifier, [{"from": low, "to": high}])
        if identifier in non_colliding or (identifier in passages and state["selector"].get("Open")):
            player = []
        # RP placement may overlap blocks. Targeting uses movement boxes if
        # present, otherwise it follows the actual individual source parts.
        state["baseline"] = baseline_objects(player, [], player or visual)
    entry["name"] = row["name"]
    entry["number"] = row["temporaryId"]
    if info.get("animation"):
        raw = json.loads(jar.read("assets/bloodborne_rp/" + info["animation"]))
        data["animations"][info["animation"]] = raw["animations"]
    return entry


def plain_door_entry(row, assets):
    registry = row["registryId"]
    states_doc = json.loads(assets.load(resource(registry, "blockstates", "json"), "original"))["variants"]
    states = []
    for hinge in ("left", "right"):
        for opened in ("false", "true"):
            parts = []
            for height, half in enumerate(("lower", "upper")):
                selector = {"facing": "north", "half": half, "hinge": hinge, "open": opened}
                key = next(key for key in states_doc
                           if dict(item.split("=") for item in key.split(",")) == selector)
                model = states_doc[key]
                parts.append(assets.part({"model": model["model"], "yaw": model.get("y", 0),
                                         "pitch": model.get("x", 0), "offset": [0, 16 * height, 0]}, "original"))
            # DoorBlock's north-facing closed plate and opened hinge-specific plates.
            if opened == "false":
                low, high = [0, 0, 13], [16, 32, 16]
            elif hinge == "left":
                low, high = [0, 0, 0], [3, 32, 16]
            else:
                low, high = [13, 0, 0], [16, 32, 16]
            shape = [{"from": low, "to": high}]
            states.append({
                "key": f"facing=north,hinge={hinge},open={opened}",
                "label": ("Левая петля" if hinge == "left" else "Правая петля")
                + (" · открыто" if opened == "true" else " · закрыто"),
                "selector": {"facing": "north", "hinge": hinge, "open": opened == "true"},
                "parts": parts, "baseline": baseline_objects(shape, shape, shape),
            })
    return {"id": registry, "number": row["temporaryId"], "name": row["name"], "kind": "door", "states": states}


def build_data(upstream, jar, vanilla, jar_sha, resources=None):
    script = upstream.read("data.js").decode("utf-8")
    data, end = json.JSONDecoder().raw_decode(script[script.index("=") + 1:])
    extra = script[script.index("=") + 1 + end:]
    assets = Assets(jar, vanilla, data, resources)
    assembly_path = "bloodborne_dw/imported-assembly-v126.json"
    assembly = json.loads(assets.read(assembly_path)) if assembly_path in assets.files else {}
    installation_poses = {}
    for item in assembly.get("recipes", []):
        installation_poses.setdefault(item["primaryRegistryId"], []).append(item["installationPose"])
    hidden_components = assembly.get("hiddenPartRedirects", {})
    source_parts_path = "bloodborne_dw/imported-source-parts-v127.json"
    source_parts = json.loads(assets.read(source_parts_path))["parts"] if source_parts_path in assets.files else []
    door_parts = {registry: {part["variant"] for part in source_parts if part["registryId"] == registry}
                  for registry in DOORS_WITH_SOURCE_PARTS}
    current = json.loads(jar.read("bloodborne_dw/debug_catalogue.json"))["entries"]
    if not any(row["registryId"] == "bloodborne_dw:source_shutter_window" for row in current):
        current.append({"registryId": "bloodborne_dw:source_shutter_window", "temporaryId": "93241",
                        "name": "Узкое окно со ставнями", "kind": "architecture"})
    known = {row["registryId"] for row in current}
    for recipe_path in sorted(assets.files):
        if not recipe_path.startswith("bloodborne_dw/composite/") or not recipe_path.endswith(".json"):
            continue
        name = Path(recipe_path).stem
        registry = "bloodborne_dw:" + name
        if registry in known or resource(registry, "blockstates", "json") not in assets.files:
            continue
        recipe = json.loads(assets.read(recipe_path))
        if WINDOW.search(registry + " " + recipe.get("displayName", "")):
            current.append({"registryId": registry, "temporaryId": None,
                            "name": recipe["displayName"], "kind": "architecture"})
    old = {entry["id"]: entry for entry in data["entries"]}
    entries = []
    retired_states = {}
    for row in current:
        registry = row["registryId"]
        if registry in RETIRED_EDITOR_MODELS:
            continue
        if registry != PLAIN_DOOR and row["temporaryId"] not in REQUESTED and not WINDOW.search(registry + " " + row["name"]):
            continue
        name = registry.split(":", 1)[1]
        recipe_path = f"bloodborne_dw/composite/{name}.json"
        if registry == PLAIN_DOOR:
            entry = plain_door_entry(row, assets)
        elif recipe_path in assets.files:
            entry = composite_entry(row, json.loads(assets.read(recipe_path)), assets,
                                    installation_poses.get(registry, ()), registry in hidden_components)
        elif row["kind"] == "rp_object" and registry in old:
            entry = rp_entry(row, old[registry], jar, data)
        elif registry in old:
            entry = old[registry]
        else:
            raise ValueError(f"Missing recipe and source model for {registry}")
        if registry == WHOLE_WINDOW:
            obsolete = {state["selector"]["variant"] for state in entry["states"]} - set(installation_poses.get(registry, (2,)))
        elif registry == SHUTTER_WINDOW:
            obsolete = {1}
        else:
            obsolete = door_parts.get(registry, set())
        if obsolete:
            retired_states[registry] = [f"variant={variant},open={opened}"
                                        for variant in sorted(obsolete) for opened in ("false", "true")]
            entry["states"] = [state for state in entry["states"] if state["selector"]["variant"] not in obsolete]
        entries.append(entry)
    found = {entry["number"] for entry in entries}
    if missing := REQUESTED - found:
        raise ValueError(f"Missing catalogue IDs: {', '.join(sorted(missing))}")
    data["entries"] = sorted(entries, key=lambda entry: (entry.get("number") or "99999", entry["id"]))
    data["retiredLayouts"] = {"models": sorted(RETIRED_EDITOR_MODELS), "states": retired_states}
    data["shutterLayoutMigration"] = {"target": SHUTTER_WINDOW, "sources": SHUTTER_LAYOUT_SOURCES}
    data["source"] = {"repository": "https://github.com/Stasik1960/Dreamwalker",
                      "commit": "v22:" + jar_sha + ":shared-profiles", "jarSha256": jar_sha,
                      "release": json.loads(jar.read("fabric.mod.json"))["version"],
                      "studioBaseCommit": "0ae6314a3e5b02f58cfaf05c265251c19b3803af"}
    if patch_sha := assets.patch_digest():
        data["source"]["modelPatchSha256"] = patch_sha
        data["source"]["commit"] += ":" + patch_sha
    return "window.DW_DATA=" + js_json(data) + extra, data


def write_studio(upstream, output, data, catalogue):
    with ZipFile(output, "x", ZIP_DEFLATED, compresslevel=6) as archive:
        for info in upstream.infolist():
            path = info.filename
            if path in {"data.js", "app.js", "assembly.js", "index.html", "collision-schema.json", "APPLY_IN_CODEX_RU.md",
                        "README_RU.txt", "preview.png", "pack-export.js", "style.css"} or path.endswith("-results.json") or path == "extraction-report.json":
                continue
            archive.writestr(path, upstream.read(info))
        from patch_editor import patch_app, patch_assembly, patch_index, patch_schema
        archive.writestr("app.js", patch_app(upstream.read("app.js").decode("utf-8")))
        archive.writestr("assembly.js", patch_assembly(upstream.read("assembly.js").decode("utf-8")))
        archive.writestr("index.html", patch_index(upstream.read("index.html").decode("utf-8")))
        archive.writestr("style.css", upstream.read("style.css") + b"\n" + (ROOT / "studio-v22.css").read_bytes())
        schema = patch_schema(upstream.read("collision-schema.json"))
        archive.writestr("collision-schema.json", schema)
        instructions = (ROOT / "APPLY_IN_CODEX_RU.md").read_text(encoding="utf-8")
        archive.writestr("data.js", data + "\nwindow.DW_V22_SCHEMA=" + schema.strip()
                         + ";\nwindow.DW_V22_INSTRUCTIONS=" + js_json(instructions) + ";\n")
        for script in ("studio-project.js", "studio-resize.js", "studio-v22.js"):
            archive.write(ROOT / script, script)
        archive.write(ROOT / "README-RU.md", "README-RU.md")
        archive.write(ROOT / "APPLY_IN_CODEX_RU.md", "APPLY_IN_CODEX_RU.md")
        archive.write(ROOT / "check_export.py", "check_export.py")
        archive.writestr("catalogue.json", js_json({
            "source": catalogue["source"],
            "models": [{"id": item["id"], "number": item["number"],
                        "states": {state["key"]: state["selector"] for state in item["states"]}}
                       for item in catalogue["entries"]],
        }))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--vanilla", required=True, type=Path)
    parser.add_argument("--upstream", type=Path, default=ROOT.parent / "Dreamwalker-V10-Collision-Studio-v4.zip")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--resources", type=Path, help="Loose model edits for preview before rebuilding the game JAR")
    args = parser.parse_args()
    if digest(args.upstream) != UPSTREAM_SHA256:
        raise ValueError("Studio source does not match the pinned V4 archive")
    with ZipFile(args.upstream) as upstream, ZipFile(args.jar) as jar, ZipFile(args.vanilla) as vanilla:
        data, catalogue = build_data(upstream, jar, vanilla, digest(args.jar), args.resources)
        write_studio(upstream, args.output, data, catalogue)
    print(f"{len(catalogue['entries'])} models, {sum(len(x['states']) for x in catalogue['entries'])} states")


if __name__ == "__main__":
    main()
