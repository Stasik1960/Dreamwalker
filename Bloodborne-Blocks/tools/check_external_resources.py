"""Validate model, blockstate, and mesh resource references against Minecraft 1.20.1.

This is a static ZIP/resource-graph audit. It neither starts Minecraft nor
rewrites generated resources; its only output is an ignored JSON report.
"""
from __future__ import annotations

import argparse
import gzip
import json
import re
import tempfile
import zipfile
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
DEFAULT_JAR = Path.home() / ".gradle/caches/fabric-loom/1.20.1/minecraft-client.jar"
BUILTIN_PARENTS = {"minecraft:builtin/generated", "minecraft:builtin/entity"}


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def ident(value: str, default: str) -> str:
    return value if ":" in value else f"{default}:{value}"


def model_file(reference: str) -> str:
    namespace, path = reference.split(":", 1)
    return f"assets/{namespace}/models/{path}.json"


def texture_file(reference: str) -> str:
    namespace, path = reference.split(":", 1)
    return f"assets/{namespace}/textures/{path}.png"


def applications(blockstate: dict):
    for value in blockstate.get("variants", {}).values():
        yield from (value if isinstance(value, list) else [value])
    for part in blockstate.get("multipart", []):
        value = part.get("apply", {})
        yield from (value if isinstance(value, list) else [value])


class Resources:
    def __init__(self, resources: Path, archive: zipfile.ZipFile):
        self.resources, self.archive = resources, archive
        self.archive_names = set(archive.namelist())
        self.models, self.model_origins = {}, {}
        self.issues = {"json": [], "models": set(), "parents": set(), "textures": set(),
                       "variables": set(), "required_variables": set(), "cycles": set()}
        self.counts = Counter()

    def load_model(self, reference: str):
        if reference in self.models:
            return self.models[reference]
        path = model_file(reference)
        local = self.resources / path
        try:
            if local.is_file():
                data, origin = read_json(local), "local"
            elif path in self.archive_names:
                data, origin = json.loads(self.archive.read(path).decode("utf-8-sig")), "minecraft"
            else:
                self.issues["models"].add(reference)
                return None
        except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
            self.issues["json"].append({"model": reference, "error": str(error)})
            return None
        self.models[reference], self.model_origins[reference] = data, origin
        self.counts[f"models_{origin}"] += 1
        return data

    def parent(self, reference: str):
        data = self.load_model(reference)
        if data is None or not isinstance(data.get("parent"), str):
            return None
        parent = ident(data["parent"], "minecraft")
        if parent in BUILTIN_PARENTS:
            self.counts["builtin_parents"] += 1
            return None
        if self.load_model(parent) is None:
            self.issues["parents"].add(parent)
            return None
        return parent

    def merged_textures(self, reference: str, seen=()):
        if reference in seen:
            self.issues["cycles"].add(" -> ".join(seen + (reference,)))
            return {}
        data = self.load_model(reference)
        if data is None:
            return {}
        parent = self.parent(reference)
        result = self.merged_textures(parent, seen + (reference,)) if parent else {}
        result.update({name: value for name, value in data.get("textures", {}).items() if isinstance(name, str)})
        return result

    def resolve_texture(self, reference: str, value: str, textures: dict, seen=(), required=False):
        if not value.startswith("#"):
            return ident(value, "minecraft")
        variable = value[1:]
        if variable in seen:
            self.issues["required_variables" if required else "variables"].add(f"{reference}#{variable} (cycle)")
            return None
        target = textures.get(variable)
        if not isinstance(target, str):
            self.issues["required_variables" if required else "variables"].add(f"{reference}#{variable}")
            return None
        return self.resolve_texture(reference, target, textures, seen + (variable,), required)

    def check_texture(self, reference: str):
        path = texture_file(reference)
        if not (self.resources / path).is_file() and path not in self.archive_names:
            self.issues["textures"].add(reference)
        else:
            self.counts["textures_checked"] += 1

    def effective_faces(self, reference: str, seen=()):
        if reference in seen:
            self.issues["cycles"].add(" -> ".join(seen + (reference,)))
            return []
        data = self.load_model(reference)
        if data is None:
            return []
        if "elements" not in data:
            parent = self.parent(reference)
            return self.effective_faces(parent, seen + (reference,)) if parent else []
        return [face for element in data.get("elements", []) for face in element.get("faces", {}).values()]

    def walk_model(self, reference: str, walked: set[str]):
        if reference in walked:
            return
        walked.add(reference)
        data = self.load_model(reference)
        if data is None:
            return
        parent = self.parent(reference)
        if parent:
            self.walk_model(parent, walked)
        textures = self.merged_textures(reference)
        for value in textures.values():
            if isinstance(value, str):
                texture = self.resolve_texture(reference, value, textures)
                if texture:
                    self.check_texture(texture)
        for element in data.get("elements", []):
            for face in element.get("faces", {}).values():
                value = face.get("texture")
                if isinstance(value, str) and not value.startswith("#"):
                    self.check_texture(ident(value, "minecraft"))
    def validate_active_model(self, reference: str):
        textures = self.merged_textures(reference)
        for face in self.effective_faces(reference):
            value = face.get("texture")
            if isinstance(value, str):
                texture = self.resolve_texture(reference, value, textures, required=True)
                if texture:
                    self.check_texture(texture)
        for name, value in textures.items():
            if name == "particle" or re.fullmatch(r"layer\d+", name):
                if isinstance(value, str):
                    texture = self.resolve_texture(reference, value, textures, required=True)
                    if texture:
                        self.check_texture(texture)


def audit(resources: Path, vanilla: Path):
    with zipfile.ZipFile(vanilla) as archive:
        graph = Resources(resources, archive)
        model_refs, mesh_textures = set(), set()
        blockstates = 0
        for path in sorted((resources / "assets").glob("*/blockstates/**/*.json")):
            try:
                data = read_json(path)
            except (OSError, json.JSONDecodeError) as error:
                graph.issues["json"].append({"blockstate": str(path.relative_to(resources)), "error": str(error)})
                continue
            blockstates += 1
            for app in applications(data):
                if isinstance(app, dict) and isinstance(app.get("model"), str):
                    model_refs.add(ident(app["model"], "minecraft"))
        for path in (resources / "assets").glob("*/models/**/*.json"):
            namespace = path.relative_to(resources / "assets").parts[0]
            local = path.relative_to(resources / "assets" / namespace / "models").with_suffix("").as_posix()
            graph.load_model(f"{namespace}:{local}")
        for relative in ("bloodborne_blocks/v2/meshes.json.gz", "bloodborne_blocks/logical/meshes.json.gz"):
            path = resources / relative
            try:
                with gzip.open(path, "rt", encoding="utf-8") as stream:
                    meshes = json.load(stream)
                for mesh in meshes.values():
                    for polygon in mesh.get("polygons", []):
                        value = polygon.get("texture")
                        if isinstance(value, str):
                            mesh_textures.add(ident(value, "minecraft"))
            except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
                graph.issues["json"].append({"mesh": relative, "error": str(error)})
        local_roots = set(graph.models)
        item_roots = {reference for reference in local_roots if reference.split(":", 1)[1].startswith("item/")}
        walked = set()
        for reference in sorted(local_roots | model_refs):
            graph.walk_model(reference, walked)
        for reference in sorted(model_refs | item_roots):
            graph.validate_active_model(reference)
        for reference in sorted(mesh_textures):
            graph.check_texture(reference)
        missing = {name: sorted(values) for name, values in graph.issues.items() if isinstance(values, set)}
        return {
            "format": "bloodborne-external-resource-audit-v1", "minecraft_jar": str(vanilla),
            "scope": "asset blockstates/models plus v2/logical gzip mesh texture references",
            "counts": {"blockstates": blockstates, "blockstate_model_references": len(model_refs),
                       "models_loaded": len(graph.models), "mesh_texture_references": len(mesh_textures), **dict(sorted(graph.counts.items()))},
            "ok": not any((missing["models"], missing["parents"], missing["textures"], missing["required_variables"],
                            missing["cycles"], graph.issues["json"])),
            "missing": missing, "json_errors": graph.issues["json"],
            "limits": ["Builtin generated/entity parents are accepted without ZIP membership checks.",
                       "Only unresolved variables on active faces/particle/layerN fail; unused template aliases are informational.",
                       "Only JSON model/blockstate and gzip mesh reference graphs are checked, not runtime registry semantics."],
        }


def self_test():
    """Small graph guards for child texture inheritance and missing namespaces."""
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        model_dir = root / "assets/test/models"
        model_dir.mkdir(parents=True)
        (model_dir / "parent.json").write_text(json.dumps({"textures": {"side": "#all"},
            "elements": [{"faces": {"north": {"texture": "#side"}}}]}), encoding="utf-8")
        (model_dir / "child.json").write_text(json.dumps({"parent": "test:parent", "textures": {"all": "minecraft:block/dirt"}}), encoding="utf-8")
        (model_dir / "required.json").write_text(json.dumps({"elements": [{"faces": {"north": {"texture": "#missing"}}}]}), encoding="utf-8")
        (model_dir / "cycle_a.json").write_text(json.dumps({"parent": "test:cycle_b"}), encoding="utf-8")
        (model_dir / "cycle_b.json").write_text(json.dumps({"parent": "test:cycle_a"}), encoding="utf-8")
        archive_path = root / "vanilla.zip"
        with zipfile.ZipFile(archive_path, "w") as archive:
            archive.writestr("assets/minecraft/textures/block/dirt.png", b"x")
            archive.writestr("assets/minecraft/textures/block/stone.png", b"x")
        with zipfile.ZipFile(archive_path) as archive:
            graph = Resources(root, archive)
            graph.walk_model("test:child", set())
            graph.validate_active_model("test:child")
            if graph.issues["textures"] or graph.issues["required_variables"]:
                raise AssertionError("child texture override was not resolved")
            graph.load_model("missing:example")
            if "missing:example" not in graph.issues["models"]:
                raise AssertionError("unknown namespace was not reported")
            graph.validate_active_model("test:required")
            if "test:required#missing" not in graph.issues["required_variables"]:
                raise AssertionError("missing required variable was not reported")
            graph.effective_faces("test:cycle_a")
            if not graph.issues["cycles"]:
                raise AssertionError("parent cycle was not reported")
            (model_dir / "item").mkdir()
            (model_dir / "item/only.json").write_text(
                json.dumps({"textures": {"layer0": "#missing"}}), encoding="utf-8"
            )
            graph.validate_active_model("test:item/only")
            if "test:item/only#missing" not in graph.issues["required_variables"]:
                raise AssertionError("item-only missing texture variable was not reported")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", type=Path, default=RES)
    parser.add_argument("--minecraft-jar", type=Path, default=DEFAULT_JAR)
    parser.add_argument("--output", type=Path, default=ROOT / "build/external-resource-audit.json")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        print("self-test ok")
        return
    report = audit(args.resources, args.minecraft_jar)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report["counts"], sort_keys=True))
    print(json.dumps({name: len(value) for name, value in report["missing"].items()}, sort_keys=True))
    if not report["ok"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
