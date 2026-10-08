"""Read-only complete RP type comparison, with positive and unresolved scopes separate.

Identifiers, JSON formatting and proven bone-name correspondence do not establish
art differences. Existing aliases require the complete resources and runtime
contract to match. Static selection witnesses are actual source-derived query
differences; a missing witness is never treated as equality.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import itertools
import json
import math
from pathlib import Path
import re
import struct
import xml.etree.ElementTree as ET
import zipfile
import zlib

from audit_rp_motion_envelopes import static_cubes, chain, vector, mirror, around, mul, union

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/rp/resources/assets/bloodborne_rp"
ALIASES = {"furniture_8": "furniture_1", "furniture_3": "furniture_10",
           "furniture_4": "furniture_11", "furniture_5": "furniture_12",
           "furniture_6": "furniture_13", "furniture_7": "furniture_14",
           "furniture_9": "furniture_2"}
NON_COLLIDING = {"curtainsmall", "curtain_half", "curtains", "door_empty", "gate_empty", "trapdoor", "wood_gate"}
PASSAGES = {"door_1", "door_2", "main_gate", "small_gate"}
CUSTOM = {"stairs", "ladder", "npc_window", "chandelier_small", "wood_gate"}
DOGS = {"cage_obj_1", "cage_obj_2", "cage_obj_3"}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def semantic(value):
    if isinstance(value, dict):
        return {k: semantic(v) for k, v in sorted(value.items())}
    if isinstance(value, list):
        return [semantic(v) for v in value]
    if isinstance(value, (float, int)) and not isinstance(value, bool):
        return round(float(value), 10)
    return value


def fingerprint(value):
    return sha(json.dumps(semantic(value), ensure_ascii=True, sort_keys=True, separators=(",", ":")).encode())


def png_pixels(data):
    """Exact noninterlaced 8-bit RGB/RGBA decoding of the supplied RP PNGs."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("Invalid PNG signature")
    chunks = collections.defaultdict(list)
    offset = 8
    while offset < len(data):
        size = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset+4:offset+8]
        payload = data[offset+8:offset+8+size]
        if (zlib.crc32(kind+payload) & 0xffffffff) != struct.unpack_from(">I", data, offset+8+size)[0]:
            raise ValueError("PNG chunk CRC mismatch")
        chunks[kind].append(payload)
        offset += size+12
        if kind == b"IEND":
            break
    width, height, depth, color, compression, filters, interlace = struct.unpack(">IIBBBBB", chunks[b"IHDR"][0])
    if (depth, color, compression, filters, interlace) not in ((8, 2, 0, 0, 0), (8, 6, 0, 0, 0)):
        raise ValueError("Explicit decoder scope exceeded")
    channels = 3 if color == 2 else 4
    stride = width*channels
    raw = zlib.decompress(b"".join(chunks[b"IDAT"]))
    if len(raw) != (stride+1)*height:
        raise ValueError("PNG decompressed size mismatch")
    previous = bytearray(stride)
    rgba = bytearray()
    for y in range(height):
        start = y*(stride+1)
        mode = raw[start]
        row = bytearray(raw[start+1:start+1+stride])
        for i in range(stride):
            left = row[i-channels] if i >= channels else 0
            above = previous[i]
            upper_left = previous[i-channels] if i >= channels else 0
            if mode == 1:
                delta = left
            elif mode == 2:
                delta = above
            elif mode == 3:
                delta = (left+above)//2
            elif mode == 4:
                p = left+above-upper_left
                distances = (abs(p-left), abs(p-above), abs(p-upper_left))
                delta = (left, above, upper_left)[distances.index(min(distances))]
            elif mode == 0:
                delta = 0
            else:
                raise ValueError("Unsupported PNG filter")
            row[i] = (row[i]+delta) & 255
        if channels == 4:
            rgba.extend(row)
        else:
            for i in range(0, len(row), 3):
                rgba.extend(row[i:i+3]); rgba.append(255)
        previous = row
    visible = bytearray(rgba)
    for i in range(0, len(visible), 4):
        if visible[i+3] == 0:
            visible[i:i+3] = b"\x00\x00\x00"
    return {"width": width, "height": height, "rgbaSha256": sha(rgba),
            "visibleRgbaSha256": sha(visible), "transparentRgbNormalizedOnly": True,
            "allAtlasPixelsDecoded": width*height}, bytes(rgba)


def model_contract(document):
    geometries = document["minecraft:geometry"]
    if len(geometries) != 1:
        raise ValueError("Single source geometry expected")
    g = geometries[0]
    bones = g["bones"]
    by_name = {b["name"]: b for b in bones}
    if len(by_name) != len(bones):
        raise ValueError("Duplicate source bone name")
    children = collections.defaultdict(list)
    for bone in bones:
        children[bone.get("parent")].append(bone)
    memo = {}
    def tree(bone):
        if bone["name"] not in memo:
            own = {k: v for k, v in bone.items() if k not in ("name", "parent")}
            memo[bone["name"]] = {"properties": own, "children": sorted((tree(v) for v in children[bone["name"]]), key=fingerprint)}
        return memo[bone["name"]]
    names = {}
    ambiguous = []
    def assign(siblings, parent):
        ordered = sorted(siblings, key=lambda b: fingerprint(tree(b)))
        for i, bone in enumerate(ordered):
            path = parent+"/"+str(i)
            names[bone["name"]] = path
            if sum(fingerprint(tree(v)) == fingerprint(tree(bone)) for v in ordered) > 1:
                ambiguous.append(bone["name"])
            assign(children[bone["name"]], path)
    roots = children[None]
    assign(roots, "root")
    if len(names) != len(bones):
        raise ValueError("Missing parent or cyclic source model")
    description = {k: v for k, v in g.get("description", {}).items() if k != "identifier"}
    normalized = {"format_version": document.get("format_version"), "description": description,
                  "hierarchy": sorted((tree(b) for b in roots), key=fingerprint)}
    lookup, cubes = static_cubes(document)
    vertices = []
    for bone in bones:
        for cube in bone.get("cubes", []):
            inflate = float(cube.get("inflate", 0))
            origin = tuple(x-inflate for x in vector(cube, "origin"))
            size = tuple(x+2*inflate for x in vector(cube, "size"))
            points = []
            for bits in itertools.product((0, 1), repeat=3):
                p = mirror(tuple(origin[i]+size[i]*bits[i] for i in range(3)))
                p = around(p, mirror(vector(cube, "pivot")), vector(cube, "rotation"))
                for ancestor in chain(bone, lookup):
                    p = around(p, mirror(vector(ancestor, "pivot")), vector(ancestor, "rotation"))
                points.append(mul(p, 1/16))
            vertices.append({"vertices": points, "uv": cube.get("uv"),
                             "mirror": cube.get("mirror", bone.get("mirror", False)),
                             "unknownCubeFlags": {k: v for k, v in cube.items() if k not in ("origin", "size", "pivot", "rotation", "uv", "mirror", "inflate")}})
    static = [box for _, box in cubes]
    return normalized, names, ambiguous, vertices, static


def animations_contract(spec, document, names):
    clips = {}
    missing = []
    for suffix, declared in spec["clips"].items():
        clip = document.get("animations", {}).get(declared["name"])
        if clip is None:
            missing.append(declared["name"])
        else:
            clip = dict(clip)
            if "bones" in clip:
                clip["bones"] = {names.get(k, "UNBOUND:"+k): v for k, v in clip["bones"].items()}
        clips[suffix] = {"declaration": {k: v for k, v in declared.items() if k != "name"},
                         "sourceClip": clip}
    return clips, missing


def behavior(asset, spec, names):
    canonical = ALIASES.get(asset, asset)
    custom = canonical in CUSTOM
    role = "lever70tick" if canonical.startswith("lever_") else "pulse32tick" if canonical == "wood_gate" else "ladder48tick" if canonical == "ladder" else "lampGraphGuiTravel" if canonical == "hunterlamp" else "booleanPassage" if canonical in PASSAGES else "booleanChest" if canonical == "chest" else "decoration"
    if canonical == "chandelier_small" or canonical in NON_COLLIDING and not custom:
        physical = {"kind": "none", "boxes": []}
    elif canonical == "stairs":
        _, parts = static_cubes(json.loads((RES/spec["model"]).read_text("utf-8")))
        physical = {"kind": "sourceStairTreads", "boxes": [b for bone, b in parts if bone == "stairs" and all(b[i+3]-b[i] > 1e-8 for i in range(3))]}
    elif canonical == "npc_window":
        physical = {"kind": "fixedPlane", "boxes": [[-16.5/16, -30/16, 12/16, 16.5/16, 34.5/16, 15/16]]}
    elif canonical == "wood_gate":
        physical = {"kind": "fixedPlane", "boxes": [[-119/16, 16/16, -8/16, 119/16, 171/16, 8/16]]}
    elif canonical == "ladder":
        physical = {"kind": "fixedMovingZonesAndPlatform", "fixed": [-9/16, -192/16, -25.75/16, 9/16, 14/16, -23.75/16],
                    "movingDeployed": [-9/16, -473/16, -25.75/16, 9/16, -192/16, -23.75/16],
                    "movingOffsets": "Y=ladderOffsetY/16,Z=ladderOffsetZ/16; authored48ticks", "platform": [-11/16, 11/16, -24/16, 11/16, 12/16, 24/16]}
    else:
        physical = {"kind": "legacySquare", "width": spec["width"], "height": spec["height"], "openRemovesPhysics": canonical in PASSAGES}
    return {"runtimeRole": role, "physical": physical,
            "selection": "ladderWorkingZonesPlatform" if canonical == "ladder" else "visualBounds" if custom else "sourceCubeBoxesPlusMotionEnvelopes",
            "climbing": "fixedAndMovingLadderZones" if canonical == "ladder" else "none",
            "mount": "custom:"+canonical if custom else "legacyYminus5.5" if canonical == "chandelier_large" else "integerOrigin",
            "yawSnapDegrees": 45, "scale": spec["scale"], "scaleEdit": "shared0..8",
            "supportOrNeighborDelete": False, "permanentRpAndNativeOverlapAllowed": True,
            "ordinaryCreativeSelectedPartDelete": "sharedRealPacketWithinReachPermissionNoTool",
            "survivalAndEnvironmentDamageDelete": False,
            "dogVisibility": canonical in DOGS,
            "linkSource": canonical.startswith("lever_"), "linkTarget": canonical in PASSAGES or canonical in ("chest", "ladder", "wood_gate"),
            "legacyConnectionResolution": canonical in ("main_gate", "small_gate") or canonical.startswith("lever_"),
            "customRenderBones": [names.get("bottom")] if canonical == "ladder" else "authoredGateBones" if canonical == "wood_gate" else [],
            "persistAndSync": "sharedUUIDRegistryCustomNameOpenLockedScaleVerticalOffsetLinksSourceLegacyPayload",
            "config": "sharedConfig; mechanism maxMechanismLinks and lamp travel config role-specific",
            "ordinaryPick": "canonicalNewPlacerWithoutWorldUUIDLinksProvenanceOffset",
            "ordinaryCreativeDrop": "no ItemEntity; removal discards by common policy"}


def contains(box, point):
    return all(box[i]-1e-10 <= point[i] <= box[i+3]+1e-10 for i in range(3))


def selection_witness(a, b):
    """Find a closed-state point selected by one source union and not the other.

    A witness proves a difference; exhausted candidates never prove equality.
    Boundary and nearby interior probes also cover source-authored flat quads.
    """
    def selected(boxes, point):
        return any(contains(box, point) for box in boxes)
    for left, right, label in ((a, b, "leftOnly"), (b, a, "rightOnly")):
        overall = union(right)
        for box in left:
            center = tuple((box[i]+box[i+3])/2 for i in range(3))
            candidates = [center]
            for axis in range(3):
                for end in (box[axis], box[axis+3]):
                    p = list(center); p[axis] = end; candidates.append(tuple(p))
                    p = list(center); p[axis] = end+(center[axis]-end)*1e-5; candidates.append(tuple(p))
            candidates.extend(itertools.product(*[(box[i], (box[i]+box[i+3])/2, box[i+3]) for i in range(3)]))
            for point in candidates:
                if not contains(overall, point) or not selected(right, point):
                    return {"side": label, "localPoint": point, "sourceClosedSelectionOnly": True,
                            "notMovementPhysicsOrManualArtClaim": True}
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--native-xml", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError("Historical report is immutable")
    catalogue_path = RES/"catalog.json"
    catalogue = json.loads(catalogue_path.read_text("utf-8"))
    object_source = ROOT/"src/rp/java/dev/dreamwalker/bloodbornerp/object/ObjectRegistry.java"
    mob_source = ROOT/"src/rp/java/dev/dreamwalker/bloodbornerp/mob/MobRegistry.java"
    excluded = set(re.findall(r'"([^"]+)"', re.search(r"NON_OBJECTS=Set\.of\((.*?)\)", object_source.read_text("utf-8"), re.S)[1]))
    mobs = set(re.findall(r'"([^"]+)"', re.search(r"IDS\s*=\s*(?:List|Set)\.of\((.*?)\)", mob_source.read_text("utf-8"), re.S)[1]))
    objects = {k: v for k, v in catalogue.items() if k not in excluded|mobs}
    assert len(objects) == 76 and len(set(objects)-set(ALIASES)) == 69
    entity_source = ROOT/"src/rp/java/dev/dreamwalker/bloodbornerp/object/RpObjectEntity.java"
    text = entity_source.read_text("utf-8")
    for field, expected in (("NON_COLLIDING", NON_COLLIDING), ("PASSAGES", PASSAGES)):
        actual = set(re.findall(r'"([^"]+)"', re.search(field+r"=java\.util\.Set\.of\((.*?)\)", text, re.S)[1]))
        assert actual == expected
    table = json.loads((ROOT/"src/architecture/resources/bloodborne_dw/debug_catalogue.json").read_text("utf-8"))["entries"]
    ids = {r["registryId"]: r for r in table if r["kind"] == "rp_object"}
    pixels = {}; rows = {}; shapes = {}; normalized = {}
    with zipfile.ZipFile(args.jar) as jar:
        resources_match = []
        for asset, spec in sorted(objects.items()):
            raw = {key: (RES/spec[key]).read_bytes() for key in ("model", "texture", "animation")}
            for key, data in raw.items():
                entry = "assets/bloodborne_rp/"+spec[key]
                if jar.read(entry) != data:
                    raise AssertionError("Frozen JAR source resource mismatch: "+entry)
                resources_match.append(entry)
            if spec["texture"] not in pixels:
                pixels[spec["texture"]] = png_pixels(raw["texture"])[0]
            document = json.loads(raw["model"])
            model, names, ambiguous, vertices, boxes = model_contract(document)
            animation, missing = animations_contract(spec, json.loads(raw["animation"]), names)
            contract = behavior(asset, spec, names)
            canonical = ALIASES.get(asset, asset)
            row_id = ids.get("bloodborne_rp:"+asset)
            canonical_id = ids.get("bloodborne_rp:"+canonical)
            selection = [union(boxes)] if canonical in CUSTOM and canonical != "ladder" else boxes
            if canonical == "ladder":
                selection = [] # its distinct runtime working zones are represented by the behavior role
            shapes[asset] = selection
            values = {"modelHierarchy": model, "verticesUv": sorted(vertices, key=fingerprint), "texturePixels": pixels[spec["texture"]], "animations": animation, "behavior": contract}
            normalized[asset] = values
            rows[asset] = {"asset": asset, "registryId": "bloodborne_rp:"+asset, "placerId": "bloodborne_rp:"+asset+"_placer", "canonicalAsset": canonical,
                           "offeredCanonical": asset not in ALIASES, "temporaryIdReserved": row_id["temporaryId"] if row_id else None,
                           "temporaryIdDisplayed": canonical_id["temporaryId"] if canonical_id else None,
                           "source": {key: {"path": spec[key], "sha256": sha(data)} for key, data in raw.items()},
                           "dimensions": [spec["width"], spec["height"]], "scale": spec["scale"],
                           "fullAtlas": pixels[spec["texture"]], "sourceCubeCount": len(boxes), "sourceStaticBounds": union(boxes),
                           "normalizedModelSha256": fingerprint(model), "transformedVerticesUvSha256": fingerprint(values["verticesUv"]),
                           "declaredAnimationContractSha256": fingerprint(animation), "behaviorSha256": fingerprint(contract), "behavior": contract,
                           "clipCount": len(animation), "missingDeclaredSourceClips": missing,
                           "dogVisibilitySourceBone": "main_2" if canonical == "cage_obj_2" else "main" if canonical in DOGS else None,
                           "dogVisibilityNormalizedBone": names.get("main_2" if canonical == "cage_obj_2" else "main") if canonical in DOGS else None,
                           "ambiguousIdenticalSiblingBoneNames": ambiguous,
                           "semanticComparisonSha256": fingerprint(values)}
    pairs = []
    for a, b in itertools.combinations(rows, 2):
        aa, bb = rows[a], rows[b]
        exact = aa["semanticComparisonSha256"] == bb["semanticComparisonSha256"]
        expected_alias = ALIASES.get(a) == b or ALIASES.get(b) == a
        if exact:
            status = "CONFIRMED_FULL_SOURCE_AND_SHARED_RUNTIME_CONTRACT_DUPLICATE"
            blocker = None
        elif aa["behavior"] != bb["behavior"]:
            status = "REFUTED_RUNTIME_CONTRACT_DIFFERENCE"
            changed = [k for k in aa["behavior"] if aa["behavior"][k] != bb["behavior"][k]]
            blocker = {"fields": changed, "left": {k: aa["behavior"][k] for k in changed}, "right": {k: bb["behavior"][k] for k in changed}}
        else:
            witness = selection_witness(shapes[a], shapes[b]) if shapes[a] and shapes[b] else None
            if witness:
                status = "REFUTED_SOURCE_DERIVED_CLOSED_SELECTION_UNION_DIFFERENCE"
                blocker = witness
                blocker["queryScope"] = "actual unexpanded selectionBoxes/BuilderServer ray contract"
                blocker["state"] = {"open": False, "transitionTicks": 0, "woodGatePulseTicks": 0,
                                    "leftDogsVisible": False if a in DOGS else "not_applicable", "rightDogsVisible": False if b in DOGS else "not_applicable"}
            else:
                status = "UNRESOLVED_FULL_ART_STATE_EQUIVALENCE_NOT_CONFIRMED"
                blocker = {"componentHashesDiffer": [key for key in normalized[a] if fingerprint(normalized[a][key]) != fingerprint(normalized[b][key])],
                           "reason": "Unequal normalized resource content is not alone a rendered-art inequivalence proof. No closed selection witness found."}
        if expected_alias and not exact:
            raise AssertionError("Existing alias full contract mismatch: "+a+"/"+b)
        pairs.append({"left": a, "right": b, "bothOfferedCanonical": a not in ALIASES and b not in ALIASES,
                      "status": status, "expectedExistingAlias": expected_alias, "evidence": blocker})
    xml = ET.parse(args.native_xml).getroot()
    alias_cases = [x for x in xml.iter("testcase") if "exactfurniturealiasespreserve" in x.attrib.get("name", "").lower()]
    assert len(alias_cases) == 1 and not list(alias_cases[0])
    confirmations = [p for p in pairs if p["status"].startswith("CONFIRMED")]
    unresolved = [p for p in pairs if p["status"].startswith("UNRESOLVED")]
    source_files = [object_source, mob_source, entity_source,
                    ROOT/"src/rp/java/dev/dreamwalker/bloodbornerp/object/RpObjectCompatibility.java",
                    ROOT/"src/rp/java/dev/dreamwalker/bloodbornerp/object/RpObjectGeometry.java",
                    ROOT/"src/rp/java/dev/dreamwalker/bloodbornerp/client/CatalogEntityModel.java",
                    ROOT/"src/rp/java/dev/dreamwalker/bloodbornerp/client/CatalogObjectRenderer.java",
                    ROOT/"src/architecture/java/dev/dreamwalker/bloodbornedw/debug/DebugCatalogue.java",
                    ROOT/"src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/RpV9BehaviourGameTests.java"]
    report = {"schema": "dw-rp-complete-type-duplicate-audit-v1", "status": "READ_ONLY_COMPLETE_ROSTER_AUDIT_WITH_EXPLICIT_UNRESOLVED_PAIRS" if unresolved else "PASS_COMPLETE_ROSTER_SEMANTIC_AND_CONTRACT_AUDIT",
              "productionJar": str(args.jar.resolve()), "productionJarSha256": sha(args.jar.read_bytes()),
              "scope": {"registryTypes": 76, "canonicalOfferedTypes": 69, "hiddenOldAliases": 7, "allPairs": len(pairs), "offeredPairs": 69*68//2,
                        "sourceResourcesByteExactInFrozenJar": len(set(resources_match)), "wholeTextureAtlasPixelsDecoded": sum(v["allAtlasPixelsDecoded"] for v in pixels.values()),
                        "allReferencedModelsAnimationsTextures": True, "allClipDeclarationsIncludingEvents": True,
                        "runtimeAllStateVisualInspection": "NOT_RUN", "manualAcceptance": "NOT_RUN"},
              "comparisonPolicy": ["JSON whitespace/key order/model identifier are normalized away.", "Bone names are mapped through their complete structural hierarchy and clips; identical ambiguous siblings are declared, not assumed interchangeable under arbitrary animation remapping.",
                                   "Complete decoded RGBA atlases and UV mappings are fingerprinted. Full atlas mismatch is not asserted to be visible if no used-region proof exists.", "Different source-selected closed union points refute full type equivalence even for invisible cubes; selection is part of the required contract and remains separate from movement physics.",
                                   "No pair is confirmed by a closed model, PNG path or JSON byte inequality alone. All-state runtime/manual appearance proof remains explicitly NOT_RUN."],
              "countsByStatus": dict(collections.Counter(p["status"] for p in pairs)),
              "confirmedGroups": confirmations, "newConfirmedGroups": [p for p in confirmations if not p["expectedExistingAlias"]],
              "unresolvedPairs": unresolved, "rows": list(rows.values()), "pairs": pairs,
              "existingAliasImplementation": {"mapping": ALIASES, "meaning": "One canonical implementation and hidden registry resolvers; old EntityTypes and placer registries remain loadable.",
                  "actualNative": {"xml": str(args.native_xml.resolve()), "sha256": sha(args.native_xml.read_bytes()), "testcase": alias_cases[0].attrib, "status": "PASS",
                                  "asserted": ["old registry remains loadable", "same UUID and CustomName", "canonical asset/render/geometry lookup", "typed UUID Links and unknown source field", "exact full typed SourceLegacyPayload.Original", "canonical Pick item without cloning world UUID/Links"]},
                  "debug": "canonical TEMP prefix resolves through explicit canonicalRegistryId, retired TEMP rows remain reserved; raw entity registry is still shown by debug",
                  "presentationDifference": "Old default plain fallback translation can retain Furniture8 versus Furniture1 text; CustomName is preserved. Numeric prefix is canonical.",
                  "drop": "There is no RP ItemEntity drop implementation. Ordinary Creative deletion intentionally discards all RP types; alias Pick resolves to the canonical item.",
                  "notRun": ["real chunk/disk reentry for all seven alias entities", "ordinary actual-client placement from all seven hidden old alias items", "separate alias Scale/Locked/VerticalOffset native assertions", "all-pose manual visual acceptance"]},
              "frameworkSource": [{"path": str(p.relative_to(ROOT)), "sha256": sha(p.read_bytes())} for p in source_files],
              "limits": ["No new catalogue merge or production mutation performed.", "No cross-catalogue architecture equivalence assumed; RP ladder and static gate/pulse gate behavior remains distinct.", "Missing source clip declarations and unresolved pairs are disclosed; report does not claim the full catalogue is accepted."]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "counts": report["countsByStatus"], "confirmed": confirmations, "unresolved": [(r["left"], r["right"]) for r in unresolved], "path": str(args.output)}, ensure_ascii=True))


if __name__ == "__main__":
    main()
