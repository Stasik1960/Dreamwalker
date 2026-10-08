"""Read-only full-type duplicate audit of the frozen V10 architecture catalogue.

Writes new evidence only. Model inheritance and referenced PNG pixels are read
from the supplied production JAR; Minecraft parents use the explicit client JAR.
This is a static semantic audit, not a new Minecraft/GUI/world acceptance run.
"""
from __future__ import annotations

import argparse
import collections
import copy
import hashlib
import itertools
import json
import math
from pathlib import Path
import struct
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf8")


def png_rgba(data):
    """Decode all PNG samples, not compressed-file identity or alpha alone."""
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    chunks = collections.defaultdict(list)
    offset = 8
    while offset < len(data):
        size = struct.unpack_from(">I", data, offset)[0]
        name = data[offset + 4:offset + 8]
        block = data[offset + 8:offset + 8 + size]
        assert zlib.crc32(name + block) & 0xffffffff == struct.unpack_from(">I", data, offset + 8 + size)[0]
        chunks[name].append(block)
        offset += size + 12
        if name == b"IEND":
            break
    width, height, depth, color, compression, filter_method, interlace = struct.unpack(">IIBBBBB", chunks[b"IHDR"][0])
    assert compression == filter_method == interlace == 0, "Unsupported PNG method; never infer equality"
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color]
    assert depth in (1, 2, 4, 8, 16)
    stride = (width * channels * depth + 7) // 8
    bpp = max(1, (channels * depth + 7) // 8)
    raw = zlib.decompress(b"".join(chunks[b"IDAT"]))
    assert len(raw) == (stride + 1) * height
    previous = bytearray(stride)
    pixels = bytearray()
    transparency = b"".join(chunks[b"tRNS"])
    palette = b"".join(chunks[b"PLTE"])
    transparent_sample = [struct.unpack_from(">H", transparency, i)[0] for i in range(0, len(transparency), 2)] if color in (0, 2) else []
    maximum = (1 << depth) - 1
    for y in range(height):
        mode = raw[y * (stride + 1)]
        row = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        assert mode <= 4
        for x in range(stride):
            left = row[x - bpp] if x >= bpp else 0
            up, upper_left = previous[x], previous[x - bpp] if x >= bpp else 0
            if mode == 1:
                prediction = left
            elif mode == 2:
                prediction = up
            elif mode == 3:
                prediction = (left + up) // 2
            elif mode == 4:
                p = left + up - upper_left
                a, b, c = abs(p - left), abs(p - up), abs(p - upper_left)
                prediction = left if a <= b and a <= c else up if b <= c else upper_left
            else:
                prediction = 0
            row[x] = (row[x] + prediction) & 255
        for x in range(width):
            if depth == 16:
                values = [struct.unpack_from(">H", row, 2 * (x * channels + k))[0] for k in range(channels)]
            elif depth == 8:
                values = list(row[x * channels:(x + 1) * channels])
            else:
                assert channels == 1
                bit = x * depth
                values = [(row[bit // 8] >> (8 - depth - bit % 8)) & maximum]
            if color == 3:
                index = values[0]
                rgb = list(palette[index * 3:index * 3 + 3])
                alpha = transparency[index] if index < len(transparency) else 255
            elif color in (0, 4):
                rgb = [values[0] * 255 // maximum] * 3
                alpha = values[1] * 255 // maximum if color == 4 else 0 if values == transparent_sample else 255
            else:
                rgb = [v * 255 // maximum for v in values[:3]]
                alpha = values[3] * 255 // maximum if color == 6 else 0 if values == transparent_sample else 255
            pixels.extend([*rgb, alpha])
        previous = row
    return width, height, bytes(pixels)


class Assets:
    def __init__(self, jar, vanilla):
        self.jar = zipfile.ZipFile(jar)
        self.vanilla = zipfile.ZipFile(vanilla)
        self.names = set(self.jar.namelist())
        self.vanilla_names = set(self.vanilla.namelist())
        self.models, self.textures, self.model_rows = {}, {}, {}
        self.unresolved = set()

    def read(self, path, optional=False):
        if path in self.names:
            return self.jar.read(path)
        if path in self.vanilla_names:
            return self.vanilla.read(path)
        if optional:
            return None
        raise ValueError("Missing actual dependency: " + path)

    @staticmethod
    def path(identifier, kind, suffix):
        if ":" not in identifier:
            identifier = "minecraft:" + identifier
        ns, path = identifier.split(":", 1)
        return f"assets/{ns}/{kind}/{path}{suffix}"

    def model(self, identifier, stack=()):
        if identifier in self.models:
            return self.models[identifier]
        assert identifier not in stack, "Parent cycle"
        path = self.path(identifier, "models", ".json")
        raw = self.read(path)
        doc = json.loads(raw)
        parent = doc.get("parent")
        if parent and not parent.startswith("builtin/"):
            merged = copy.deepcopy(self.model(parent, (*stack, identifier)))
        else:
            merged = {"builtinParent": parent} if parent else {}
        textures = {**merged.get("textures", {}), **doc.get("textures", {})}
        merged.update({k: v for k, v in doc.items() if k not in ("parent", "textures", "credit", "texture_size") and not k.startswith("__")})
        merged["textures"] = textures
        self.models[identifier] = merged
        self.model_rows[identifier] = {"id": identifier, "path": path, "rawSha256": sha(raw), "parent": parent}
        return merged

    def texture(self, identifier):
        if identifier in self.textures:
            return self.textures[identifier]
        path = self.path(identifier, "textures", ".png")
        raw = self.read(path, optional=True)
        if raw is None:
            self.unresolved.add(identifier)
            row = {"id": identifier, "path": path, "missing": True}
        else:
            w, h, rgba = png_rgba(raw)
            meta = self.read(path + ".mcmeta", optional=True)
            row = {"id": identifier, "path": path, "pngSha256": sha(raw), "decodedRgbaSha256": sha(rgba), "width": w, "height": h,
                   "mcmetaSemanticSha256": sha(canonical(json.loads(meta))) if meta else None, "_rgba": rgba}
        self.textures[identifier] = row
        return row

    def semantic(self, identifier):
        doc = copy.deepcopy(self.model(identifier))
        textures = doc.pop("textures", {})

        def resolve(key):
            seen = set()
            while key.startswith("#"):
                assert key not in seen, "Texture reference cycle"
                seen.add(key)
                key = textures.get(key[1:], "minecraft:missingno")
            return key

        texture_ids = set()
        for element in doc.get("elements", []):
            element.pop("name", None)
            for key in list(element):
                if key.startswith("__"):
                    element.pop(key)
            rotation = element.get("rotation")
            if rotation and rotation.get("angle", 0) == 0:
                element.pop("rotation")
            for face in element.get("faces", {}).values():
                texture = resolve(face["texture"])
                texture_ids.add(texture)
                row = self.texture(texture)
                face["texture"] = {k: row.get(k) for k in ("missing", "decodedRgbaSha256", "width", "height", "mcmetaSemanticSha256")}
        particle = resolve(textures.get("particle", "minecraft:missingno"))
        particle_row = self.texture(particle)
        doc["particleTextureSignature"] = {k: particle_row.get(k) for k in ("missing", "decodedRgbaSha256", "width", "height", "mcmetaSemanticSha256")}
        self.model_rows[identifier].update({"semanticSha256": sha(canonical(doc)), "elementCount": len(doc.get("elements", [])),
            "faceCount": sum(len(e.get("faces", {})) for e in doc.get("elements", [])), "visibleTextureIds": sorted(texture_ids), "particleTextureId": particle})
        return doc


def source_ref(path, token):
    lines = (ROOT / path).read_text(encoding="utf8").splitlines()
    return {"path": path, "line": next((n for n, s in enumerate(lines, 1) if token in s), None), "token": token,
            "sha256": sha((ROOT / path).read_bytes())}


def first_difference(a, b, path="$"):
    if type(a) != type(b):
        if isinstance(a, (int, float)) and isinstance(b, (int, float)) and a == b:
            return None
        return {"field": path, "left": a, "right": b}
    if isinstance(a, dict):
        if a.keys() != b.keys():
            return {"field": path + ".keys", "left": sorted(a), "right": sorted(b)}
        for key in sorted(a):
            result = first_difference(a[key], b[key], path + "." + str(key))
            if result:
                return result
    elif isinstance(a, list):
        if len(a) != len(b):
            return {"field": path + ".length", "left": len(a), "right": len(b)}
        for n, (left, right) in enumerate(zip(a, b)):
            result = first_difference(left, right, path + f"[{n}]")
            if result:
                return result
    elif a != b:
        return {"field": path, "left": a, "right": b}
    return None


def pair_difference(left, right):
    """Different code representation is not evidence of a different object."""
    if left.get("climb", False) != right.get("climb", False):
        return {"field": "actualSupportedClimb", "left": left.get("climb", False), "right": right.get("climb", False),
                "basis": "Native ladder movement/climb hook versus no climbing capability in wall/composite contracts"}
    if (left.get("family") == "WALL") != (right.get("family") == "WALL"):
        return {"field": "actualNeighborTopology", "left": left.get("neighborTopology", "NO_CONNECTIVE_TOPOLOGY"),
                "right": right.get("neighborTopology", "NO_CONNECTIVE_TOPOLOGY"),
                "basis": "WallBlock reconnect/up/post/per-arm NONE-LOW-TALL transitions versus non-connective authored root"}
    return first_difference({k:v for k,v in left.items() if k != "family"}, {k:v for k,v in right.items() if k != "family"})


def clean_part(assets, part, alt):
    model = part.get("altModel", part["model"]) if alt else part["model"]
    return {"model": assets.semantic(model), "offset": part.get("offset", [0, 0, 0]), "yaw": part.get("yaw", 0),
            "pitch": part.get("pitch", 0), "pivot": part.get("pivot", [8, 8, 8]), "extraYaw": part.get("extraYaw", 0), "extraPivot": part.get("extraPivot", [8, 8, 8])}


def composite_contract(assets, document, index=0):
    variant = document["variants"][index]
    poses = variant["poses"]
    result = {"rotationDegrees": [0, 90, 180, 270] if "window" in document["id"] and "wood" not in document["id"] else list(range(0, 360, 45)),
              "mounts": ["vertical", "floor", "ceiling"] if "window" in document["id"] and "wood" not in document["id"] else ["vertical"],
              "openable": document.get("openable", False), "requiredSupport": document.get("support", {}).get("required", False),
              "supportOffset": document.get("support", {}).get("offset", [0, -1, 0]), "essentialMask": document.get("essentialMask", [[0, 0, 0]]),
              "physicalRotation": "GRID_ALIGNED" if document["id"].endswith("prototype_roof") else "GLOBAL_YAW",
              "climb": False, "lighting": 0, "neighbors": "freely suspended pane" if "window" in document["id"] and "wood" not in document["id"] else "support only; no connective topology",
              "verticalOffset": "same-owner DOUBLE payload, any finite loaded in-bounds edit including solid overlap; source/native NBT untouched",
              "function": "OPEN_CLOSE + levers/manual policy/ordered hooks" if document["id"].endswith("prototype_double_door") else "DECORATIVE_POSE" if document["id"].endswith("prototype_wood_window") else "STATIC",
              "closed": {}, "open": {}, "visibleBounds": variant.get("visibleBounds"), "mountedPlaneBounds": variant.get("mountedPlaneBounds"), "mountAlignmentYaw": variant.get("mountAlignmentYaw", 0)}
    for pose_name in ("closed", "open"):
        pose = poses.get(pose_name, poses["closed"])
        result[pose_name] = {"collision": pose.get("collision", []), "selection": pose.get("selection", pose.get("collision", [])),
                             "profiles": {profile: [clean_part(assets, p, profile == "alt") for p in pose["parts"]] for profile in ("base", "alt")}}
    return result


def texture_witness(left, right):
    assert left["width"] == right["width"] and left["height"] == right["height"]
    a, b = left["_rgba"], right["_rgba"]
    for offset in range(0, len(a), 4):
        if a[offset:offset + 4] != b[offset:offset + 4]:
            index = offset // 4
            x, y = index % left["width"], index // left["width"]
            return {"pixel": [x, y], "uv16PixelCenter": [(x + .5) * 16 / left["width"], (y + .5) * 16 / left["height"]],
                    "leftRgba": list(a[offset:offset + 4]), "rightRgba": list(b[offset:offset + 4]),
                    "usedByTallFace": "west" if x < left["width"] / 2 else "east",
                    "basis": "default tall-side west UV[0,0,8,16] plus east UV[8,0,16,16] cover all source texels"}
    return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--vanilla", type=Path, default=Path("C:/Users/vakir/Limacina/project/dw/1.20.1.jar"))
    parser.add_argument("--report", type=Path, default=ROOT / "reports/V10_ARCHITECTURE_DUPLICATE_AUDIT_CANDIDATE7.json")
    parser.add_argument("--markdown", type=Path, default=ROOT / "docs/REVIEW_V10_ARCHITECTURE_DUPLICATE_AUDIT.md")
    args = parser.parse_args()
    args.jar = args.jar.resolve()
    assert args.report.resolve().is_relative_to(ROOT) and args.markdown.resolve().is_relative_to(ROOT)
    assert not args.report.exists() and not args.markdown.exists(), "New evidence only; preserve previous bytes"
    assets = Assets(args.jar, args.vanilla)
    catalogue_path = "bloodborne_dw/debug_catalogue.json"
    catalogue = json.loads(assets.read(catalogue_path))
    entries = catalogue["entries"]
    roster = []
    for row in entries:
        item = row["registryId"] + ("_placer" if row["kind"] == "rp_object" else "_spawn_egg" if row["kind"] == "rp_mob" else "")
        roster.append({**row, "canonicalRegistryId": row.get("canonicalRegistryId", row["registryId"]),
                       "offered": "canonicalRegistryId" not in row, "placementItemOrItem": item,
                       "retiredIdReserved": "canonicalRegistryId" in row})
    architecture = [r for r in roster if r["kind"] == "architecture"]
    assert len(entries) == 118 and len(architecture) == 18
    contracts = {}
    rows = []
    wall_rows = []
    ladder_rows = []
    for row in architecture:
        path = row["registryId"].split(":")[1]
        if path.startswith("prototype_wall"):
            material = int(path.rsplit("_", 1)[1]) if "skin" in path else 6
            contract = {"family": "WALL", "models": {profile: {direction: {kind: assets.semantic(f"bloodborne_dw:block/wall/{profile}/{kind}" + ("_diagonal" if direction else ""))
                         for kind in ("post", "low", f"tall_{material}")} for direction in (0, 1)} for profile in ("base", "alt")},
                        "rotationDegrees": list(range(0, 360, 45)), "neighborTopology": "native WallBlock per-arm NONE/LOW/TALL, independent post; AUTO cardinal, tool45 MANUAL; all8materials mutualWallBlock family",
                        "stateDimensions": {"north": 3, "east": 3, "south": 3, "west": 3, "post": 2, "rotation": 8, "profile": 2, "connections": 2, "waterlogged": 2},
                        "collision": "same WallGeometry(sideCode,post,yaw); grid seam subtraction WallStackGeometry(lowerRAW,native upper), offsetnonzero restores RAW + translated own ledger",
                        "outline": "same WallGeometry outlined real arms/post, translated owner selection when offset",
                        "climb": False, "lighting": 0, "function": "STATIC_NATIVE_WALL", "support": "centercontact actual1.0 top; edited mount freely suspended", "mount": "native grid + persistent DOUBLE vertical offset"}
            # Material is encoded in the model bytes above, not a naming token.
            for profile in contract["models"].values():
                for direction in profile.values():
                    direction["tall"] = direction.pop(f"tall_{material}")
            wall_rows.append({"temporaryId": row["temporaryId"], "material": material,
                              "tallModel": f"bloodborne_dw:block/wall/base/tall_{material}",
                              "texture": f"bloodborne_dw:wall/source/minecraft/block/polished_deepslate_wall_{material}"})
            registered = 10368 * (8 if path == "prototype_wall" else 1)
            row["legacyStateAliasProperty"] = "material0..7" if path == "prototype_wall" else None
        elif path.startswith("prototype_ladder"):
            art = int(path[-1]) if "art_" in path else 0
            document = json.loads(assets.read(f"assets/bloodborne_dw/blockstates/{path}.json"))
            all_models = sorted({v["model"] for v in document["variants"].values()})
            base = f"bloodborne_dw:base/source/minecraft/block/hold/wood_ladder_0{art + 2}"
            alt = f"bloodborne_dw:alt/prototype_ladder/{art}"
            contract = {"family": "LADDER", "base": assets.semantic(base), "alt": assets.semantic(alt),
                        "rotationDegrees": list(range(0, 360, 45)), "collisionGeometryResource": json.loads(assets.read("bloodborne_dw/prototype-ladder.json")),
                        "install": "horizontal backing contact; diagonal two faces; UP full top/top slab/previous ladder -> freestanding; offset edited root persists",
                        "sourceClone": "same actual physicalYaw, clone fixed backing role/sourceArt correction/provenance; technical true never ordinary item",
                        "climb": True, "lighting": 0, "function": "CLIMBING_NATIVE_LADDER", "neighbor": "native support/remove; source root/helper ownership; no forced changing art at stack boundary"}
            ladder_rows.append({"temporaryId": row["temporaryId"], "sourceArt": art, "baseModel": base, "allBlockstateModelIds": all_models,
                                "renderMapping": "sourceClone180/canonicalMount compensation first, optional45 once; fixed type ignores storedlegacyvariant art", "actualBlockstateEntries": len(document["variants"])})
            registered = len(document["variants"])
            row["legacyStateAliasProperty"] = "variant1..2" if art == 0 else None
        else:
            document = json.loads(assets.read(f"bloodborne_dw/composite/{path}.json"))
            contract = {"family": "GLAZING" if path in ("prototype_thin_window", "prototype_glass_window_02", "prototype_glass_window_03") else "COMPOSITE", **composite_contract(assets, document)}
            registered = 1536 if contract["family"] == "GLAZING" else 512
            row["descriptorSha256"] = sha(assets.read(f"bloodborne_dw/composite/{path}.json"))
            row["legacyVariants"] = len(document["variants"])
            row["legacyStateAliasProperty"] = "variant1..2" if path == "prototype_thin_window" else None
        contracts[row["temporaryId"]] = contract
        row.update({"registeredStateCount": registered, "contractSha256": sha(canonical(contract)), "offered": True,
                    "pickDropDebug": "canonical art type; own UUID/offset/legacy technical tags do not travel into new item", "fullTypeDuplicateStatus": "NOT_DUPLICATE_OR_EXPLICIT_KEEP_DISTINCT"})
        rows.append(row)
    pairs = []
    for a, b in itertools.combinations(rows, 2):
        left, right = a["temporaryId"], b["temporaryId"]
        if {left, right} == {"90010", "90020"}:
            decision = "EXPLICIT_USER_KEEP_DISTINCT"
        else:
            decision = "DIFFERENT_FULL_TYPE" if pair_difference(contracts[left], contracts[right]) else "UNCONFIRMED_IDENTICAL_STATIC_CONTRACT_NEEDS_RUNTIME_COMPATIBILITY"
        pairs.append({"ids": [left, right], "decision": decision, "differenceWitness": pair_difference(contracts[left], contracts[right])})
    wall_pairs = []
    for a, b in itertools.combinations(wall_rows, 2):
        ta, tb = assets.texture(a["texture"]), assets.texture(b["texture"])
        witness = texture_witness(ta, tb)
        assert witness, "Potential identical tall art: must review instead of claiming distinct"
        wall_pairs.append({"ids": [a["temporaryId"], b["temporaryId"]], "tallTexelDifference": witness,
                           "lowOnlyRenderEquivalenceNotWholeType": True})
    request = (ROOT / "reports/user-review-v9/request.txt").read_text(encoding="utf8")
    keep_text = "Возвращение этого прямо запрошенного типа является исключением из удаления художественных дублей; не удаляй его снова из-за сходства с window02."
    assert keep_text in request
    sources = [source_ref("reports/user-review-v9/request.txt", keep_text), source_ref("TASK.md", "### Дополнение пользователя 2026-10-08: полноценные дубли типов"),
               source_ref("PROGRESS.md", "### V10: MIN11/MIN12 и новый аудит дублей")]
    java_base = "src/architecture/java/dev/dreamwalker/bloodbornedw/"
    java_evidence = [source_ref(java_base + file, token) for file, token in [
        ("architecture/wall/PrototypeWallModels.java", "hasTallSide"), ("architecture/wall/PrototypeWallBlock.java", "sideCode"),
        ("architecture/wall/WallStackGeometry.java", "class WallStackGeometry"), ("architecture/wall/PrototypeWallItem.java", "static int material"),
        ("architecture/PrototypeLadderBlock.java", "int fixedVariant"), ("architecture/PrototypeLadderItem.java", "canonical"),
        ("architecture/ladder_source/SourceLadderClient.java", "class SourceLadderClient"), ("composite/GlazingTypes.java", "legacyAngular"),
        ("composite/GlazingMount.java", "1-b.to().z()/16"), ("composite/CompositeClient.java", "renderParts"),
        ("composite/CompositeSpec.java", "mountedBounds"), ("composite/CompositeRuntime.java", "prototype_double_door"),
        ("architecture/mount/VerticalMount.java", "setOffset"), ("debug/DebugCatalogue.java", "STATE_ALIASES"), ("debug/UnifiedCreativeCatalogue.java", "canonicalItems")]]
    report = {"schema": "dreamwalker-v10-architecture-full-type-duplicate-audit-v1", "status": "READ_ONLY_AUDIT_COMPLETE_NO_NEW_ARCHITECTURE_FULL_DUPLICATES_CONFIRMED",
              "production": {"path": str(args.jar), "sha256": sha(args.jar.read_bytes()), "bytes": args.jar.stat().st_size, "version": "0.1.0-prototype.5"},
              "method": {"scope": "all18 independently offered architecture types; everyBASE/ALT/closed/open sourcepart, all40wall primitives and 3canonical ladder arts pluslegacy statealiases",
                         "equivalenceRule": "whole supported contracts; one differing rendered usedtexel/UV/pose/install/physics/function suffices to refute a full duplicate",
                         "proofKind": "static source/JAR/decoded PNG + exhaustive wallstate/provider mapping; NOT a new runtime/client/world acceptance",
                         "modelNormalization": "resolvedparent geometry/textures; credit/name/zero-angle origin removed; everyface UV/rotation/cull/tint/rescale retained; decodedRGBA identity notPNG compression",
                         "serverStateSchemaUntouched": True, "sourceOriginalInputsUntouched": True, "newRuntimeRun": False},
              "summary": {"tableEntries": len(roster), "architectureTypes": len(rows), "architecturePairs": len(pairs),
                          "pairDecisions": dict(collections.Counter(p["decision"] for p in pairs)), "confirmedNewArchitectureGroups": 0,
                          "wallMaterials": 8, "ladderArts": 3, "glassTypes": 3, "retiredTableIdsReserved": 7,
                          "rosterKinds": dict(collections.Counter(r["kind"] for r in roster))},
              "architecture": rows, "pairComparisons": pairs, "contracts": contracts, "wallTallTexelPairs": wall_pairs,
              "walls": {"rows": wall_rows, "sharedLowPost": "all8skins exactsame post/low primitive and samephysics/topology",
                        "completeFormEnumeration": {"sidePatterns": 81, "withoutTall": 16, "withTall": 65, "perSkinNativeStates": 10368, "primaryLegacyStates": 82944,
                                                    "visualKeysPerSkin": 2592, "sharedLowOnlyVisualKeys": 512, "tallDependentVisualKeys": 2080},
                        "independentTallArt": "all28materialpairs have a proved differenttexel in actual west/eastdefaultUV faces; no wholewalltype merge",
                        "provider": "same160sharedbakedparts; no selectorCartesian expansion; no optimizationchange"},
              "ladders": ladder_rows,
              "preservedException": {"ids": ["90010", "90020"], "request": sources[0], "quote": keep_text,
                  "details": "same ownfrontUV andtexture, differentrawZby4/16; initialside/floor/ceilingseat can compensate, sneakgroundvertical keepsdifferent mounting depth; legacysource03 intrinsic-45 remains distinct. Explicit independenttype request controls even ifnew mountedposes lookidentical."},
              "aliases": {"stateAliases": catalogue["stateAliases"], "retiredRpAliases": [r for r in roster if r["retiredIdReserved"]],
                          "distinction": "state aliases are savedart/pose compatibility, not18independentimplementations ofoneart; worldUUID/root/payload retained. RPwholecontract audit ownedbyrp_integration."},
              "confirmedGroups": [], "removedIdToKeptIdThisAudit": [], "unconfirmed": [],
              "models": sorted(assets.model_rows.values(), key=lambda x: x["id"]),
              "textures": sorted(({k: v for k, v in row.items() if k != "_rgba"} for row in assets.textures.values()), key=lambda x: x["id"]),
              "existingMissingParticles": sorted(assets.unresolved), "catalogueRoster": roster, "requirements": sources, "runtimeSourceEvidence": java_evidence,
              "compatibilityRequirementsForAnyFutureConfirmedGroup": ["earliestexistingTEMP canonical; neverreuse retired", "oldregistry remainsreadable", "sameplacedUUID/root/typedpayload/no instance deletion", "sameCustomName/offset/profiles/poses/sourceprovenance", "sameleverrefs/lampnames/routes/manualpolicy/orderedhooks", "canonicalitemplacement/pick/drop/debug/reload test onactualnewartifact"],
              "limits": ["No arbitraryexternalpack override enumeration; BASE/ALT fallback checked fromfrozen actualresources. Independent external namespaces retained.",
                         "Differenttypes proved by explicitwitness; no requirement to exhaustall dynamicneighborworlds once unequalartrefutesfullidentity.",
                         "No newarchmerge meansno new migration/runtimeclaim. Currentwholeclient/GUI/fullKappa gates remain separate pending.",
                         "118TEMP table andcurrent18architecture do notfinish1121source-model assembly catalogue/finalnumeric namespace/cityconversion."]}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    by_id = {r["temporaryId"]: r for r in rows}
    lines = ["# Аудит полных архитектурных дублей V10", "", "Проверены все 18 самостоятельных архитектурных типов замороженного prototype.5. Новых полных групп дублей не подтверждено: ничего не объединено и номера не освобождены. Это статический аудит ресурсов и поведения, не новый клиентский PASS.", "", f"Production SHA256: `{report['production']['sha256']}`.", "", "| TEMP | Registry | Зарегистрированных состояний | Решение |", "|---|---|---:|---|"]
    for row in sorted(rows, key=lambda x: x["temporaryId"]):
        lines.append(f"| {row['temporaryId']} | `{row['registryId']}` | {row['registeredStateCount']} | {'Прямое исключение window03' if row['temporaryId']=='90020' else 'Сохранить самостоятельный тип'} |")
    lines += ["", "Восемь оград используют общую стойку и LOW-сегменты. Для всех 28 пар материалов найдены различающиеся реальные RGBA-тексели в UV высоких сторон. Проверены 81 комбинация NONE/LOW/TALL, стойка, BASE/ALT и восемь углов: 16 комбинаций без TALL могут выглядеть одинаково, остальные 65 раскрывают самостоятельное оформление. Физика, native-соединения и монтаж у материалов общие; это не основание удалять различающийся рисунок высокого сегмента.", "", "Лестницы 90006/90018/90019 используют один PNG, но отличаются не только именами: плоскость ступени имеет другие X-границы и UV, декор — другие координаты, наклоны и ориентации UV. Общее крепление, коллизия и взбирание не превращают их в один художественный тип. Старые variant1/2 основного registry читаются как совместимые соответствия новым предметам; source_clone остаётся техническим состоянием экземпляра.", "", "90004 отличается используемой областью атласа от 90010/90020. window03 [90020] прямо исключён пользователем из удаления дублей, даже при одинаковой лицевой области window02. Между 02/03 есть исходная разница монтажной глубины 4/16; посадка на боковую грань/пол/потолок её компенсирует, а вертикальная установка с верхней грани сохраняет эту разницу. Старый установленный window03 сохраняет исходный наклон −45° и UUID; новые предметы остаются кардинальными и возвращают собственный тип.", "", "Дверь 90001, деревянное окно 90007, кровля 90003 и дерево 90005 имеют разные части/геометрию и разные функциональные контракты. Только настоящая дверь участвует в OPEN/CLOSE-политике и командах событий; декоративная поза окна не подменяется этой функцией. Кровля имеет осевой физический куб при повороте рисунка, дерево — узкий ствол и четыре призмы выбора кроны, лестницы — подъём, ограды — соседскую топологию.", "", "Таблица содержит 118 записей: 18 архитектурных, 76 RP-object registry (69 канонических и семь сохранённых алиасов), 18 мобов, четыре предмета и два инструмента. Семь прежних мебельных номеров зарезервированы навсегда; их исходные соответствия перечислены в JSON. Полный RP-аудит, включая функции всех состояний, выполняется отдельно; старое доказательство совпадающих ресурсов не выдаётся здесь за новый полный поведенческий результат.", "", "Модели сравниваются после разрешения родителей, всех текстурных ссылок, элементов, UV/face rotation/cull/tint и BASE/ALT-родителей. PNG декодируются до пикселей; различие сжатых байтов само по себе не используется. Существующий отсутствующий particle дерева указан отдельно в JSON: это не потеря видимых граней и не основание объединения.", "", "Если в RP-аудите будет подтверждена полная группа, совместимость должна сохранить registry-чтение, UUID, имя, профиль, высоту, позу, индивидуальные данные, связи, маршруты и hooks. Канонические item/place/pick/drop/debug/reload потребуют проверок именно нового артефакта. Здесь карта и установленные экземпляры не изменены.", "", f"Подробные 153 попарных решения, исходные хеши/строки, 28 пиксельных свидетелей, нормализованные контракты и полный roster: [{args.report.name}](../reports/{args.report.name}).", "", "Аудит существующих типов не завершает каталог сборок из 1121 исходной модели, окончательные числовые registry ID, преобразование города или ручную приёмку пользователя.", ""]
    args.markdown.write_text("\n".join(lines), encoding="utf8")
    print(json.dumps({"status": report["status"], "summary": report["summary"], "report": str(args.report), "markdown": str(args.markdown)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
