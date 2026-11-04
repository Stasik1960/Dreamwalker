"""Resolve actual source ALT faces and compare their visible footprints with reviewed masks."""
from collections import Counter
from functools import cache
from hashlib import sha256
from io import BytesIO
import json
import math
from pathlib import Path
from zipfile import ZipFile

from PIL import Image, ImageChops

MODULE = Path(__file__).resolve().parents[1]
RESOURCES = MODULE / "src/main/resources"
INPUTS = MODULE / "tools/editor2-palette-inputs"
CATALOGUE = MODULE / "tools/rgb-catalogue-input.json"
KEEP = MODULE / "tools/rgb-material-keep.json"


def resource(identifier, folder, extension):
    domain, key = identifier.split(":", 1) if ":" in identifier else ("minecraft", identifier)
    return f"assets/{domain}/{folder}/{key}{extension}"


def json_bytes(value):
    return json.loads(value)


def default_uv(direction, low, high):
    x0, y0, z0 = low
    x1, y1, z1 = high
    return {
        "down": (x0, 16-z1, x1, 16-z0),
        "up": (x0, z0, x1, z1),
        "north": (16-x1, 16-y1, 16-x0, 16-y0),
        "south": (x0, 16-y1, x1, 16-y0),
        "west": (z0, 16-y1, z1, 16-y0),
        "east": (16-z1, 16-y1, 16-z0, 16-y0),
    }[direction]


def footprint(uv, size):
    width, height = size
    if len(uv) != 4 or any(not math.isfinite(v) for v in uv):
        raise ValueError(f"invalid UV {uv}")
    a, b = sorted((uv[0] * width / 16, uv[2] * width / 16))
    c, d = sorted((uv[1] * height / 16, uv[3] * height / 16))
    left = min(width-1, max(0, math.floor(a)))
    top = min(height-1, max(0, math.floor(c)))
    right = max(left+1, min(width, math.ceil(b)))
    bottom = max(top+1, min(height, math.ceil(d)))
    return left, top, right, bottom


class Inspector:
    def __init__(self, baseline):
        self.baseline = baseline
        self.policies = json_bytes((INPUTS / "classification.json").read_bytes())["sprites"]
        self.keep = {}
        for row in json_bytes(KEEP.read_bytes())["rules"]:
            self.keep.setdefault((row["block"], row["sprite"]), []).append(row)

    @cache
    def read(self, path):
        for tree in (RESOURCES / "dw_city_visuals/alt", RESOURCES):
            candidate = tree / path
            if candidate.is_file():
                return candidate.read_bytes()
        try:
            return self.baseline.read(path)
        except KeyError as error:
            raise FileNotFoundError(path) from error

    @cache
    def model(self, identifier):
        path = resource(identifier, "models", ".json")
        data = json_bytes(self.read(path))
        elements, textures, parents = [], {}, []
        parent = data.get("parent")
        if parent:
            elements, textures, parents = self.model(parent)
        return data.get("elements", elements), {**textures, **data.get("textures", {})}, [*parents, path]

    @cache
    def image(self, sprite):
        path = resource(sprite, "textures", ".png")
        data = self.read(path)
        image = Image.open(BytesIO(data)).convert("RGBA")
        return image, image.getchannel("A").point(lambda alpha: 255 if alpha else 0), sha256(data).hexdigest()

    @cache
    def mask(self, path):
        return Image.open(INPUTS / path).convert("L")

    def resolve_sprite(self, textures, value):
        visited = set()
        while value.startswith("#"):
            if value in visited:
                raise ValueError(f"cyclic texture reference {value}")
            visited.add(value)
            value = textures[value[1:]]
        return value if ":" in value else "minecraft:" + value

    @cache
    def classify(self, block, sprite, uv, tint, explicit_glass):
        if tint != -1:
            return "KEEP", "vanilla tintindex is semantic", None
        image, alpha, digest = self.image(sprite)
        box = footprint(uv, image.size)
        visible = alpha.crop(box)
        if not visible.getbbox():
            return "KEEP", "no visible texels in sampled UV", box
        if explicit_glass:
            return "KEEP", "glazing model and transparent window geometry", box
        for row in self.keep.get((block, sprite), ()):
            region = row.get("texels")
            if region is None or all(box[k] < region[k+2] and region[k] < box[k+2] for k in (0, 1)):
                return "KEEP", row["reason"], box
        alpha_values = image.getchannel("A").crop(box).histogram()
        if sum(alpha_values[1:255]):
            return "KEEP", "visible partial-alpha texels are protected", box
        if any(v < 0 or v > 16 for v in uv):
            return "UNKNOWN", "UV outside normal sprite coordinates", box
        policy = self.policies.get(sprite)
        if not policy:
            return "UNKNOWN", "sprite absent from historical classifier", box
        kind = policy["classification"]
        if kind == "nonStone":
            return "KEEP", "protected material per atlas inventory", box
        if kind == "stone":
            return "PAINT", "whole approved material in historical classifier", box
        if kind == "mixed":
            mask = self.mask(policy["mask"])
            if mask.size != image.size:
                return "UNKNOWN", "mask size differs from current ALT sprite", box
            samples = mask.crop(box)
            paint = ImageChops.multiply(visible, samples).getbbox() is not None
            keep = ImageChops.multiply(visible, ImageChops.invert(samples)).getbbox() is not None
            if paint and keep:
                return "KEEP", "face crosses paint and protected texels", box
            return ("PAINT", "all visible texels in stone mask", box) if paint else ("KEEP", "protected mask texels", box)
        return "UNKNOWN", f"unknown classification {kind}", box

    @cache
    def faces(self, block, model, explicit_glass=False):
        elements, textures, parents = self.model(model)
        result = []
        for index, element in enumerate(elements):
            for side, face in element["faces"].items():
                sprite = self.resolve_sprite(textures, face["texture"])
                uv = tuple(face.get("uv", default_uv(side, element["from"], element["to"])))
                zone, reason, box = self.classify(block, sprite, uv, face.get("tintindex", -1), explicit_glass)
                result.append({"element": index, "side": side, "sprite": sprite, "uv": uv,
                               "zone": zone, "reason": reason, "texels": box})
        return parents, result

    def directional(self, poses):
        """Use outline directions only for an exact untransformed cube."""
        table = None
        for parts in poses.values():
            if len(parts) != 1 or "error" in parts[0]:
                return {}
            part = parts[0]
            transform = part["poseTransform"]
            if any(transform.get(key, 0) for key in ("yaw", "pitch", "extraYaw")):
                return {}
            if transform.get("offset", [0, 0, 0]) != [0, 0, 0]:
                return {}
            elements, _, _ = self.model(part["model"])
            if len(elements) != 1:
                return {}
            element = elements[0]
            if (element["from"] != [0, 0, 0] or element["to"] != [16, 16, 16]
                    or element.get("rotation") or len(part["faces"]) != 6):
                return {}
            found = {face["side"]: face["sprite"] for face in part["faces"] if face["zone"] == "PAINT"}
            table = found if table is None else {side: sprite for side, sprite in table.items() if found.get(side) == sprite}
        return table or {}

    def inspect(self, row, scan_all):
        identifier = row["id"]
        path = "bloodborne_dw/composite/" + identifier.split(":", 1)[1] + ".json"
        spec = json_bytes(self.read(path))
        tested = range(len(spec["variants"])) if scan_all or "variant" not in row else [row["variant"]]
        variants = []
        for variant_id in tested:
            variant = spec["variants"][variant_id]
            poses = {}
            for pose_name, pose in variant["poses"].items():
                parts = []
                for part in pose["parts"]:
                    model = part.get("altModel", part["model"])
                    try:
                        parents, faces = self.faces(identifier, model, row.get("reviewStatus") == "QUARANTINE_GLAZING")
                        parts.append({"model": model, "sourceModel": part["model"], "parents": parents,
                                      "poseTransform": {key: value for key,value in part.items() if key not in ("model","altModel")},
                                      "faces": faces})
                    except (FileNotFoundError, KeyError, ValueError, RecursionError) as error:
                        parts.append({"model": model, "sourceModel": part["model"], "error": str(error), "faces": []})
                poses[pose_name] = parts
            counts = Counter(face["zone"] for parts in poses.values() for part in parts for face in part["faces"])
            failed = any("error" in part for parts in poses.values() for part in parts)
            variants.append({"variant": variant_id, "poses": poses, "faceCounts": counts,
                             "directional": self.directional(poses),
                             "review": "UNRESOLVED" if failed or counts["UNKNOWN"] else
                             "CANDIDATE_PAINT" if counts["PAINT"] else "NO_PAINT_ZONE"})
        return {"id": identifier, "displayName": row["displayName"], "catalogueKey": row["catalogueKey"],
                "sourceVariantCount": len(spec["variants"]), "specPath": path, "variants": variants}



def candidates():
    table = json_bytes(CATALOGUE.read_bytes())
    rows = table["items"]
    if table["schema"] != 1 or len(rows) != 416 or len({row["id"] for row in rows}) != len(rows):
        raise ValueError("Expected the complete unique 416-row candidate catalogue")
    return rows
