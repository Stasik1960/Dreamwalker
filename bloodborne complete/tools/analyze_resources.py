#!/usr/bin/env python3
"""Read-only, reproducible Bloodborne resource audit. No object IDs are assigned.

The full source state selectors, ordered alternatives, model JSON, and resource
chains are evidence. A JSON file or a vanilla carrier is never called an object.
Only the Python standard library is required, including the PNG alpha decoder.
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
import zipfile
import zlib

VERSION = 1
FACES = ("down", "up", "north", "south", "west", "east")
BUILTINS = {"minecraft:builtin/generated", "minecraft:builtin/entity", "minecraft:builtin/missing"}


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def digest(value):
    return hashlib.sha256(canonical(value).encode("utf-8")).hexdigest()


def file_sha(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1048576), b""):
            h.update(chunk)
    return h.hexdigest()


def resource_id(value):
    return value if ":" in value else "minecraft:" + value


def resource_path(value, kind):
    ns, value = resource_id(value).split(":", 1)
    ext = ".png" if kind == "textures" else ".json"
    return f"assets/{ns}/{kind}/{value}{ext}"


def state_key(name, properties):
    if not properties:
        return name
    return name + "[" + ",".join(f"{k}={v}" for k, v in sorted(properties.items())) + "]"


def parse_state_key(key):
    if "[" not in key:
        return key, {}
    name, tail = key.split("[", 1)
    return name, dict(x.split("=", 1) for x in tail.rstrip("]").split(",") if x)


def local_tree_evidence(world_report):
    """Reuse separately proved assemblies only when original archive matches."""
    if not world_report: return None
    path = Path(world_report).parent/'FIRST_SET_SOURCE_EVIDENCE.json'
    if not path.is_file(): return None
    report = json.loads(path.read_text(encoding='utf-8'))
    world = json.loads(Path(world_report).read_text(encoding='utf-8-sig'))
    if report.get('source_sha256_before') != world.get('source', {}).get('sha256_before'): return None
    return report if report.get('confirmed_local_tree_assemblies') else None


def selector_properties(selector):
    result = {}
    if not selector:
        return result
    for pair in selector.split(","):
        if "=" not in pair:
            raise ValueError(f"selector property lacks '=': {pair}")
        key, value = pair.split("=", 1)
        if not key or not value or key in result:
            raise ValueError(f"malformed or repeated selector property: {pair}")
        result[key] = value
    return result


def match_condition(condition, properties):
    """Minecraft multipart: ordinary keys are AND, OR/AND are explicit groups."""
    if condition is None:
        return True
    if not isinstance(condition, dict):
        raise ValueError("multipart when is not an object")
    values = []
    for key, expected in condition.items():
        if key in ("OR", "AND"):
            if not isinstance(expected, list):
                raise ValueError(key + " condition is not an array")
            predicates = [match_condition(x, properties) for x in expected]
            values.append(any(predicates) if key == "OR" else all(predicates))
        else:
            text = str(expected).lower() if isinstance(expected, bool) else str(expected)
            negate = text.startswith("!")
            options = text[1:].split("|") if negate else text.split("|")
            hit = str(properties.get(key)) in options
            values.append(not hit if negate else hit)
    return all(values)


def alternatives(value):
    choices = value if isinstance(value, list) else [value]
    result = []
    total = sum(x.get("weight", 1) for x in choices if isinstance(x, dict))
    for i, choice in enumerate(choices):
        if not isinstance(choice, dict):
            result.append({"index": i, "invalid": choice})
            continue
        result.append({"index": i, "model": resource_id(choice.get("model", "")),
                       "x": choice.get("x", 0), "y": choice.get("y", 0),
                       "uvlock": choice.get("uvlock", False), "weight": choice.get("weight", 1),
                       "total_weight": total, "source": choice})
    return result


class Archives:
    def __init__(self, pack, dependencies):
        self.entries = {}
        self.zips = []
        self.sources = []
        for label, path in [("source_pack", pack)] + [("dependency", p) for p in dependencies]:
            path = Path(path)
            z = zipfile.ZipFile(path)
            self.zips.append(z)
            source = {"role": label, "path": str(path.resolve()), "sha256": file_sha(path)}
            if 'version.json' in z.namelist():
                source['minecraft_version'] = json.loads(z.read('version.json'))
            self.sources.append(source)
            for n in z.namelist():
                if not n.endswith("/") and n not in self.entries:
                    self.entries[n] = (label, z, str(path.resolve()))
        self.source_names = sorted(n for n, (label, _, _) in self.entries.items() if label == "source_pack")

    def read(self, path):
        return self.entries[path][1].read(path)

    def exists(self, path):
        return path in self.entries

    def origin(self, path):
        return {"role": self.entries[path][0], "archive": self.entries[path][2]} if self.exists(path) else None


def read_json(archives, path, issues):
    duplicates = []
    def hook(pairs):
        out = {}
        for key, value in pairs:
            if key in out:
                duplicates.append(key)
            out[key] = value
        return out
    try:
        text = archives.read(path).decode("utf-8-sig")
        try:
            out = json.loads(text, object_pairs_hook=hook)
        except ValueError:
            # Gson permits comments. Strip only comment tokens outside quoted
            # strings, retain line positions, and retain the original bytes.
            cleaned = re.sub(r'("(?:\\.|[^"\\])*")|(/\*[\s\S]*?\*/|//[^\r\n]*)',
                             lambda m: m[1] if m[1] is not None else ''.join('\n' if c == '\n' else '\r' if c == '\r' else ' ' for c in m[2]), text)
            out = json.loads(cleaned, object_pairs_hook=hook)
            issues.append({'kind': 'nonstandard_json_comments', 'path': path,
                           'consequence': 'Comments stripped for analysis; original bytes retained. Runtime Gson loading not tested by this scanner.'})
        if duplicates:
            issues.append({"kind": "duplicate_json_keys", "path": path, "keys": duplicates,
                           "consequence": "parser retains last value; source byte fingerprint retained"})
        return out
    except (ValueError, UnicodeError) as exc:
        issues.append({"kind": "invalid_json", "path": path, "error": str(exc)})
        return None


def paeth(a, b, c):
    p = a + b - c
    distances = (abs(p-a), abs(p-b), abs(p-c))
    return (a, b, c)[distances.index(min(distances))]


def png_alpha(data):
    """Decode PNG alpha correctly, including palette tRNS and PNG row filters."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return {"status": "ERROR", "error": "bad PNG signature"}
    chunks = collections.defaultdict(list)
    at = 8
    while at + 12 <= len(data):
        length = struct.unpack(">I", data[at:at+4])[0]
        tag = data[at+4:at+8]
        value = data[at+8:at+8+length]
        chunks[tag].append(value)
        at += 12 + length
        if tag == b"IEND":
            break
    width, height, depth, color, comp, filt, interlace = struct.unpack(">IIBBBBB", chunks[b"IHDR"][0])
    result = {"width": width, "height": height, "bit_depth": depth, "color_type": color,
              "interlace": interlace, "sha256": hashlib.sha256(data).hexdigest()}
    if interlace or comp or filt:
        return dict(result, status="NOT_DECODED", reason="interlace or nonstandard compression/filter")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}.get(color)
    if channels is None or depth not in (1, 2, 4, 8, 16):
        return dict(result, status="NOT_DECODED", reason="unsupported PNG color/bit depth")
    trns = b"".join(chunks[b"tRNS"])
    if color in (0, 2) and not trns:
        return dict(result, status="PASS", alpha_min=255, alpha_max=255,
                    alpha_pixels={"transparent": 0, "partial": 0, "opaque": width*height},
                    alpha_histogram={"255": width*height})
    stride = (width * channels * depth + 7) // 8
    bpp = max(1, (channels * depth + 7) // 8)
    raw = zlib.decompress(b"".join(chunks[b"IDAT"]))
    if len(raw) != (stride + 1) * height:
        return dict(result, status="ERROR", error="unexpected decompressed byte length")
    prev = bytearray(stride)
    hist = collections.Counter()
    for row_index in range(height):
        offset = row_index * (stride + 1)
        mode = raw[offset]
        row = bytearray(raw[offset+1:offset+1+stride])
        if mode == 1:
            for j in range(bpp, stride): row[j] = (row[j] + row[j-bpp]) & 255
        elif mode == 2:
            for j in range(stride): row[j] = (row[j] + prev[j]) & 255
        elif mode == 3:
            for j in range(stride): row[j] = (row[j] + ((row[j-bpp] if j >= bpp else 0) + prev[j]) // 2) & 255
        elif mode == 4:
            for j in range(stride): row[j] = (row[j] + paeth(row[j-bpp] if j >= bpp else 0, prev[j], prev[j-bpp] if j >= bpp else 0)) & 255
        elif mode != 0:
            return dict(result, status="ERROR", error=f"bad PNG filter {mode}")
        if color in (4, 6) and depth == 8:
            hist.update(row[channels-1::channels])
        elif color in (4, 6) and depth == 16:
            for j in range((channels-1)*2, stride, channels*2):
                hist[(row[j]*256 + row[j+1])*255//65535] += 1
        elif color == 3:
            for pixel in range(width):
                bit_at = pixel*depth
                idx = (row[bit_at//8] >> (8-depth-bit_at%8)) & ((1<<depth)-1)
                hist[trns[idx] if idx < len(trns) else 255] += 1
        else:
            target = [struct.unpack(">H", trns[j:j+2])[0] for j in range(0, len(trns), 2)]
            for pixel in range(width):
                if depth == 8:
                    vals = list(row[pixel*channels:(pixel+1)*channels])
                elif depth == 16:
                    vals = list(struct.unpack(">" + "H"*channels, row[pixel*channels*2:(pixel+1)*channels*2]))
                else:
                    bit_at = pixel*depth
                    vals = [(row[bit_at//8] >> (8-depth-bit_at%8)) & ((1<<depth)-1)]
                hist[0 if vals == target else 255] += 1
        prev = row
    return dict(result, status="PASS", alpha_min=min(hist), alpha_max=max(hist),
                alpha_pixels={"transparent": hist[0], "partial": sum(c for a, c in hist.items() if 0 < a < 255), "opaque": hist[255]},
                alpha_histogram={str(k): v for k, v in sorted(hist.items())})


def rotate_point(point, axis, angle, origin, rescale=False):
    xyz = [point[i] - origin[i] for i in range(3)]
    r = math.radians(angle)
    c, s = math.cos(r), math.sin(r)
    a, b = {"x": (1, 2), "y": (2, 0), "z": (0, 1)}[axis]
    xyz[a], xyz[b] = c*xyz[a]-s*xyz[b], s*xyz[a]+c*xyz[b]
    if rescale and abs(c) > 1e-12:
        xyz[a] /= abs(c)
        xyz[b] /= abs(c)
    return [round(xyz[i] + origin[i], 10) for i in range(3)]


def element_vertices(element):
    low, high = element["from"], element["to"]
    points = [list(p) for p in itertools.product(*[(low[i], high[i]) for i in range(3)])]
    r = element.get("rotation")
    if r:
        points = [rotate_point(p, r["axis"], r["angle"], r["origin"], r.get("rescale", False)) for p in points]
    return points


def geometry_summary(elements):
    points, uv_outside, flats, tint_faces, culled_faces, missing_faces, rotations = [], [], [], [], [], [], []
    for i, element in enumerate(elements):
        if not isinstance(element, dict) or "from" not in element or "to" not in element:
            continue
        points.extend(element_vertices(element))
        if "rotation" in element: rotations.append({"element": i, **element["rotation"]})
        flat_axes = [a for a in range(3) if element["from"][a] == element["to"][a]]
        if flat_axes:
            pairs = [("west", "east"), ("down", "up"), ("north", "south")]
            flats.append({"element": i, "axes": flat_axes,
                          "opposite_faces_present": {str(a): all(k in element.get("faces", {}) for k in pairs[a]) for a in flat_axes}})
        faces = element.get("faces", {})
        if len(faces) < 6: missing_faces.append({"element": i, "absent": [f for f in FACES if f not in faces]})
        for face, data in faces.items():
            if "tintindex" in data: tint_faces.append({"element": i, "face": face, "tintindex": data["tintindex"]})
            if "cullface" in data: culled_faces.append({"element": i, "face": face, "cullface": data["cullface"]})
            if any(x < 0 or x > 16 for x in data.get("uv", [])):
                uv_outside.append({"element": i, "face": face, "uv": data["uv"]})
    bounds = {"min": [min(p[i] for p in points) for i in range(3)],
              "max": [max(p[i] for p in points) for i in range(3)]} if points else None
    return {"element_count": len(elements), "face_count": sum(len(e.get("faces", {})) for e in elements),
            "bounds_model_units": bounds, "outside_cell": bool(bounds and any(bounds["min"][i] < 0 or bounds["max"][i] > 16 for i in range(3))),
            "flat_elements": flats, "element_rotations": rotations, "tint_faces": tint_faces,
            "cullface_faces": culled_faces, "absent_faces": missing_faces, "uv_outside_0_16": uv_outside,
            "warning": "Bounds describe visual geometry; they are not collision or placement masks. Opposite authored faces alone do not prove in-game visibility."}


class Audit:
    def __init__(self, archives):
        self.a = archives
        self.issues = []
        self.raw = {}
        self.resolved = {}
        self.records = {}
        self.blockstates = {}
        self.referrers = collections.defaultdict(list)
        self.texture_records = {}
        self.alpha_cache = {}

    def model_json(self, model):
        if model not in self.raw:
            path = resource_path(model, "models")
            self.raw[model] = read_json(self.a, path, self.issues) if self.a.exists(path) else None
        return self.raw[model]

    def resolve_model(self, model, chain=()):
        model = resource_id(model)
        if model in self.resolved: return self.resolved[model]
        if model in BUILTINS: return {"elements": [], "textures": {}, "display": {}, "chain": [model], "builtin": model, "missing": []}
        if model in chain:
            self.issues.append({"kind": "model_parent_cycle", "chain": list(chain) + [model]})
            return {"elements": [], "textures": {}, "display": {}, "chain": [model], "missing": [model]}
        raw = self.model_json(model)
        if raw is None:
            return {"elements": [], "textures": {}, "display": {}, "chain": [model], "missing": [model]}
        parent = resource_id(raw["parent"]) if raw.get("parent") else None
        base = self.resolve_model(parent, chain + (model,)) if parent else {"elements": [], "textures": {}, "display": {}, "chain": [], "missing": []}
        out = {"elements": raw.get("elements", base["elements"]),
               "textures": {**base["textures"], **raw.get("textures", {})},
               "display": {**base["display"], **raw.get("display", {})},
               "chain": [model] + base["chain"], "missing": base["missing"]}
        for key, default in (("ambientocclusion", True), ("gui_light", "side"), ("overrides", [])):
            out[key] = raw.get(key, base.get(key, default))
        if "builtin" in base: out["builtin"] = base["builtin"]
        self.resolved[model] = out
        return out

    def texture(self, ref, textures):
        seen = []
        while ref.startswith("#"):
            key = ref[1:]
            if key in seen: return None, "texture_variable_cycle:" + "->".join(seen + [key])
            seen.append(key)
            if key not in textures: return None, "undefined_texture_variable:" + key
            ref = textures[key]
        return resource_id(ref), None

    def inspect_texture(self, texture):
        if texture in self.texture_records: return
        path = resource_path(texture, "textures")
        record = {"texture": texture, "path": path, "origin": self.a.origin(path), "present": self.a.exists(path)}
        if record["present"]:
            try:
                data = self.a.read(path)
                sha = hashlib.sha256(data).hexdigest()
                record.update(self.alpha_cache[sha] if sha in self.alpha_cache else png_alpha(data))
            except Exception as exc: record.update(status="ERROR", error=str(exc))
            meta = path + ".mcmeta"
            if self.a.exists(meta): record["mcmeta"] = {"path": meta, "source": read_json(self.a, meta, self.issues), "sha256": hashlib.sha256(self.a.read(meta)).hexdigest()}
            emissive = path[:-4] + "_e.png"
            if self.a.exists(emissive): record["emissive_companion"] = {"path": emissive, "origin": self.a.origin(emissive), "sha256": hashlib.sha256(self.a.read(emissive)).hexdigest()}
        self.texture_records[texture] = record

    def inspect_model(self, model):
        if model in self.records: return self.records[model]
        raw = self.model_json(model)
        if raw is None: return None
        resolved = self.resolve_model(model)
        refs, variable_errors = {}, []
        surface_refs = {f['texture'] for e in resolved['elements'] for f in e.get('faces', {}).values() if 'texture' in f}
        particle_ref = resolved['textures'].get('particle')
        all_refs = set(resolved["textures"].values())
        all_refs.update(surface_refs)
        for ref in sorted(all_refs):
            texture, error = self.texture(ref, resolved["textures"])
            usage = (['surface'] if ref in surface_refs else []) + (['particle'] if ref == particle_ref else [])
            if error: variable_errors.append({"ref": ref, "error": error, 'usage': usage or ['unused_declaration']})
            else:
                refs[ref] = texture
                self.inspect_texture(texture)
        render_elements = []
        for e in resolved["elements"]:
            r = dict(e)
            r["faces"] = {k: {**v, "texture": refs.get(v.get("texture"), v.get("texture"))} for k, v in e.get("faces", {}).items()}
            render_elements.append(r)
        render_data = {"elements": render_elements, "display": resolved["display"],
                       "ambientocclusion": resolved["ambientocclusion"], "gui_light": resolved["gui_light"],
                       "particle": refs.get(resolved["textures"].get("particle")), "overrides": resolved["overrides"]}
        path = resource_path(model, "models")
        record = {"model": model, "path": path, "origin": self.a.origin(path),
                  "source_sha256": hashlib.sha256(self.a.read(path)).hexdigest(),
                  "source": raw, "parent_chain": resolved["chain"], "missing_parents": resolved["missing"],
                  "resolved_textures": refs, "texture_variable_errors": variable_errors,
                  "missing_texture_paths": [resource_path(t, "textures") for t in sorted(set(refs.values())) if not self.a.exists(resource_path(t, "textures"))],
                  'surface_missing_texture_paths': sorted({resource_path(refs[r], 'textures') for r in surface_refs if r in refs and not self.a.exists(resource_path(refs[r], 'textures'))}),
                  'particle_missing_texture_path': resource_path(refs[particle_ref], 'textures') if particle_ref in refs and not self.a.exists(resource_path(refs[particle_ref], 'textures')) else None,
                  "effective_display": resolved["display"], "effective_ambientocclusion": resolved["ambientocclusion"],
                  "effective_gui_light": resolved["gui_light"], "effective_overrides": resolved["overrides"],
                  "geometry_sha256": digest(render_elements), "render_structure_sha256": digest(render_data),
                  "dependency_content_sha256": digest({t: self.texture_records[t].get("sha256") for t in sorted(set(refs.values()))}),
                  "geometry": geometry_summary(resolved["elements"]),
                  "resolution_status": "UNRESOLVED" if resolved["missing"] or any('surface' in e['usage'] for e in variable_errors) else "RESOLVED",
                  "builtin_parent": resolved.get("builtin")}
        self.records[model] = record
        return record

    def collect(self):
        source_model_ids = {p.split('/')[1] + ':' + p.split('/models/', 1)[1][:-5]
                            for p in self.a.source_names if '/models/' in p and p.endswith('.json')}
        # Some pack models override vanilla models whose blockstate JSON is not
        # in the pack. Include these carrier rules, with their source explicit.
        effective_paths = list(self.a.source_names)
        for path in sorted(self.a.entries):
            if self.a.entries[path][0] == 'source_pack' or not re.fullmatch(r'assets/[^/]+/blockstates/.+\.json', path):
                continue
            source = read_json(self.a, path, self.issues)
            if not source: continue
            referenced = set()
            for apply in source.get('variants', {}).values():
                referenced.update(a.get('model') for a in alternatives(apply))
            for part in source.get('multipart', []):
                referenced.update(a.get('model') for a in alternatives(part.get('apply', [])))
            affected = bool(referenced & source_model_ids)
            if not affected:
                for model in referenced:
                    if not model or model in BUILTINS: continue
                    resolved = self.resolve_model(model)
                    if set(resolved['chain']) & source_model_ids:
                        affected = True; break
                    surface_refs = {f['texture'] for e in resolved['elements'] for f in e.get('faces', {}).values() if 'texture' in f}
                    for ref in surface_refs:
                        tex, error = self.texture(ref, resolved['textures'])
                        if not error and self.a.exists(resource_path(tex, 'textures')) and self.a.origin(resource_path(tex, 'textures'))['role'] == 'source_pack':
                            affected = True; break
                    if affected: break
            if affected:
                effective_paths.append(path)
        for path in effective_paths:
            if re.fullmatch(r"assets/[^/]+/blockstates/.+\.json", path):
                source = read_json(self.a, path, self.issues)
                if source is None: continue
                namespace = path.split("/")[1]
                carrier = namespace + ":" + path.split("/blockstates/", 1)[1][:-5]
                rules = []
                for i, (selector, apply) in enumerate(source.get("variants", {}).items()):
                    try: props = selector_properties(selector)
                    except ValueError as exc:
                        self.issues.append({"kind": "invalid_variant_selector", "path": path, "selector": selector, "error": str(exc)})
                        props = None
                    rules.append({"type": "variant", "index": i, "selector": selector, "properties": props, "alternatives": alternatives(apply)})
                for i, part in enumerate(source.get("multipart", [])):
                    rules.append({"type": "multipart", "index": i, "when": part.get("when"), "alternatives": alternatives(part.get("apply", []))})
                self.blockstates[carrier] = {"carrier": carrier, "path": path, "origin": self.a.origin(path), "source": source, "rules": rules,
                    "source_sha256": hashlib.sha256(self.a.read(path)).hexdigest()}
                for rule in rules:
                    for alt in rule["alternatives"]:
                        if alt.get("model"):
                            self.referrers[alt["model"]].append({"kind": "blockstate", "carrier": carrier, "origin": self.a.origin(path), "rule_type": rule["type"], "rule_index": rule["index"], "selector": rule.get("selector"), "when": rule.get("when"), "alternative_index": alt["index"], "x": alt["x"], "y": alt["y"], "uvlock": alt["uvlock"], "weight": alt["weight"]})
                            for key in ("x", "y"):
                                if alt[key] not in (0, 90, 180, 270): self.issues.append({"kind": "non_quarter_blockstate_rotation", "path": path, "rule": rule["index"], "field": key, "value": alt[key]})
                            if alt["weight"] <= 0: self.issues.append({"kind": "invalid_model_weight", "path": path, "rule": rule["index"], "value": alt["weight"]})
            if re.fullmatch(r"assets/[^/]+/models/.+\.json", path):
                ns = path.split("/")[1]
                model = ns + ":" + path.split("/models/", 1)[1][:-5]
                self.model_json(model)
        # The source model graph includes models without blockstate references.
        for model, raw in list(self.raw.items()):
            if not raw: continue
            if raw.get("parent"):
                parent = resource_id(raw["parent"])
                self.referrers[parent].append({"kind": "parent", "child": model})
            for i, override in enumerate(raw.get("overrides", [])):
                if "model" in override: self.referrers[resource_id(override["model"])].append({"kind": "item_override", "child": model, "index": i, "predicate": override.get("predicate")})
        queue = list(set(self.raw) | set(self.referrers))
        while queue:
            model = queue.pop()
            if model in self.records or model in BUILTINS: continue
            record = self.inspect_model(model)
            if record:
                for dependency in record["parent_chain"][1:]:
                    if dependency not in self.records and dependency not in BUILTINS and self.a.exists(resource_path(dependency, "models")): queue.append(dependency)
        # Resolution can discover a child only after the initial graph pass.
        # Complete reverse parent/override references before classifying files.
        for model, raw in list(self.raw.items()):
            if not raw: continue
            if raw.get('parent'):
                parent = resource_id(raw['parent'])
                ref = {'kind': 'parent', 'child': model}
                if ref not in self.referrers[parent]: self.referrers[parent].append(ref)
            for i, override in enumerate(raw.get('overrides', [])):
                if 'model' in override:
                    ref = {'kind': 'item_override', 'child': model, 'index': i, 'predicate': override.get('predicate')}
                    target = resource_id(override['model'])
                    if ref not in self.referrers[target]: self.referrers[target].append(ref)
        for model in self.referrers:
            self.referrers[model].sort(key=canonical)
        for model, record in self.records.items():
            record["references"] = self.referrers.get(model, [])
            kinds = {r["kind"] for r in record["references"]}
            labels = []
            if "blockstate" in kinds: labels.append("selected_by_effective_blockstate")
            if "parent" in kinds: labels.append("used_as_parent")
            if "item_override" in kinds: labels.append("used_by_item_override")
            if model.split(":", 1)[1].startswith("item/"): labels.append("item_model")
            if not kinds and not model.split(":", 1)[1].startswith("item/"):
                labels.append("unreferenced_geometry_candidate_review" if record["geometry"]["element_count"] else "unreferenced_template_or_empty_review")
            record["evidence_classification"] = labels
            record["catalog_object_status"] = "UNCLASSIFIED_NOT_AN_OBJECT_ID"
        return self

    def special_resources(self):
        properties, other_json, mcmeta, files, cit_models = [], [], [], [], []
        for path in self.a.source_names:
            if path.endswith(".properties"):
                source = self.a.read(path).decode("utf-8-sig", errors="replace")
                pairs = {}
                for line in source.splitlines():
                    line = line.strip()
                    if line and not line.startswith(("#", "!")) and "=" in line:
                        k, v = line.split("=", 1); pairs[k.strip()] = v.strip()
                record = {"path": path, "source": source, "properties": pairs, "sha256": hashlib.sha256(self.a.read(path)).hexdigest()}
                if "/cit/" in path:
                    relative = str(Path(path).parent).replace("\\", "/")
                    deps = []
                    for k, v in pairs.items():
                        if k == "model" or k.startswith(("model.", "texture")):
                            target = relative + "/" + v
                            if not target.endswith((".json", ".png")): target += ".json" if k.startswith("model") else ".png"
                            deps.append({"property": k, "target": target, "present": self.a.exists(target)})
                    record["relative_dependencies"] = deps
                properties.append(record)
            elif path.endswith(".mcmeta"):
                mcmeta.append({"path": path, "source": read_json(self.a, path, self.issues), "sha256": hashlib.sha256(self.a.read(path)).hexdigest()})
            elif path.endswith(".json") and "/models/" not in path and "/blockstates/" not in path:
                source = read_json(self.a, path, self.issues)
                other_json.append({"path": path, "source": source, "sha256": hashlib.sha256(self.a.read(path)).hexdigest()})
                if '/optifine/cit/' in path and isinstance(source, dict):
                    resolved = self.resolve_model(resource_id(source['parent'])) if source.get('parent') else {'elements': [], 'textures': {}, 'display': {}, 'missing': []}
                    textures = {**resolved['textures'], **source.get('textures', {})}
                    refs = {}
                    for ref in sorted(set(textures.values())):
                        texture, error = self.texture(ref, textures)
                        if error: refs[ref] = {'error': error}; continue
                        if texture.startswith('minecraft:./'):
                            texture_path = str(Path(path).parent).replace('\\', '/') + '/' + texture.split(':./', 1)[1] + '.png'
                            alpha = png_alpha(self.a.read(texture_path)) if self.a.exists(texture_path) else None
                            refs[ref] = {'path': texture_path, 'present': self.a.exists(texture_path), 'png': alpha}
                        else:
                            self.inspect_texture(texture)
                            refs[ref] = self.texture_records[texture]
                    cit_models.append({'path': path, 'source': source, 'geometry': geometry_summary(source.get('elements', resolved['elements'])),
                                       'display': {**resolved['display'], **source.get('display', {})}, 'texture_dependencies': refs,
                                       'parent_missing': resolved['missing'], 'catalog_object_status': 'ITEM_CIT_NOT_ARCHITECTURE_OBJECT'})
            elif path.startswith("assets/") and not path.endswith(".png"):
                files.append({"path": path, "sha256": hashlib.sha256(self.a.read(path)).hexdigest()})
        return {"properties": properties, "cit_models": cit_models, "non_model_json": other_json, "mcmeta": mcmeta, "other_asset_files": files,
                "ctm_paths": [n for n in self.a.source_names if "/ctm/" in n],
                "emissive_png_paths": [n for n in self.a.source_names if n.endswith("_e.png")],
                "core_shader_paths": [n for n in self.a.source_names if "/shaders/core/" in n]}

    def world_join(self, path):
        source = json.loads(Path(path).read_text(encoding="utf-8-sig"))
        rows = source.get("states", source.get("block_states", source)) if isinstance(source, dict) else source
        if isinstance(rows, dict):
            expanded = []
            for key, value in rows.items():
                if isinstance(value, int): value = {"count": value}
                if isinstance(value, dict):
                    name, props = parse_state_key(key)
                    expanded.append({"name": name, "properties": props, **value})
            rows = expanded
        if not isinstance(rows, list): raise ValueError("world states must be a list or canonical state-key mapping")
        out = []
        for row in rows:
            name = row.get("name", row.get("Name", row.get("block", row.get("id"))))
            props = row.get("properties", row.get("Properties", {}))
            if not name and row.get("state"):
                name, props = parse_state_key(row["state"])
            if not name: continue
            if "[" in name: name, props = parse_state_key(name)
            if name not in self.blockstates: continue
            props = {str(k): str(v).lower() if isinstance(v, bool) else str(v) for k, v in props.items()}
            selected = []
            for rule in self.blockstates[name]["rules"]:
                if rule["type"] == "variant":
                    hit = rule["properties"] is not None and match_condition(rule["properties"], props)
                else: hit = match_condition(rule.get("when"), props)
                if hit: selected.append(rule)
            model_ids = {a['model'] for r in selected for a in r['alternatives'] if a.get('model')}
            missing = sorted({resource_path(m, "models") for m in model_ids if m not in BUILTINS and not self.a.exists(resource_path(m, "models"))})
            inherited_missing = sorted({resource_path(p, 'models') for m in model_ids if m in self.records for p in self.records[m]['missing_parents']})
            missing_textures = sorted({p for m in model_ids if m in self.records for p in self.records[m]['missing_texture_paths']})
            surface_missing = sorted({p for m in model_ids if m in self.records for p in self.records[m]['surface_missing_texture_paths']})
            invalid_model_paths = sorted({resource_path(m, 'models') for m in model_ids if m not in BUILTINS and self.a.exists(resource_path(m, 'models')) and self.model_json(m) is None})
            variable_errors = [{'model': m, **e} for m in sorted(model_ids) if m in self.records for e in self.records[m]['texture_variable_errors']]
            surface_variable_errors = [e for e in variable_errors if 'surface' in e['usage']]
            multipart_only = all(r['type'] == 'multipart' for r in self.blockstates[name]['rules'])
            out.append({"state": state_key(name, props), "name": name, "properties": props,
                        "count": row.get("count", row.get("blocks", row.get("total", 0))), "samples": row.get("samples", row.get("examples", [])),
                        "selected_rules": selected, "missing_model_paths": missing, "missing_parent_paths": inherited_missing,
                        "missing_texture_paths": missing_textures, "invalid_model_paths": invalid_model_paths, "texture_variable_errors": variable_errors,
                        'surface_missing_texture_paths': surface_missing,
                        "blockstate_origin": self.blockstates[name]['origin'],
                        "status": ('EMPTY_MULTIPART_SELECTION' if multipart_only else 'NO_VARIANT_MATCH') if not selected else "MISSING_OR_INVALID_SURFACE_RESOURCE" if missing or inherited_missing or surface_missing or invalid_model_paths or surface_variable_errors else 'PARTICLE_OR_UNUSED_DECLARATION_WARNING' if missing_textures or variable_errors else "MODEL_SELECTION_AVAILABLE",
                        "random_selection": "NOT_RESOLVED_POSITIONAL_VARIANT_REQUIRES_ORIGINAL_POSITION_AND_SOURCE_RENDERER_SEED" if any(len(r["alternatives"]) > 1 for r in selected) else "DETERMINISTIC",
                        "source_row": row})
        return {"world_states_source": str(Path(path).resolve()), "world_states_sha256": file_sha(path), "states": out}

    def target_diff(self, target_jar):
        target_archives = Archives(self.a.sources[0]['path'], [target_jar])
        target = Audit(target_archives)
        changed_models, changed_blockstates, changed_textures = [], [], []
        for model in sorted(self.records):
            source_effective = self.resolve_model(model)
            target_effective = target.resolve_model(model)
            if canonical(source_effective) != canonical(target_effective):
                changed_models.append({'model': model, 'source_effective_sha256': digest(source_effective),
                                       'target_effective_sha256': digest(target_effective), 'source': source_effective, 'target': target_effective})
        for carrier, blockstate in sorted(self.blockstates.items()):
            if blockstate['origin']['role'] == 'source_pack': continue
            path = blockstate['path']
            target_source = read_json(target_archives, path, target.issues) if target_archives.exists(path) else None
            if canonical(blockstate['source']) != canonical(target_source):
                changed_blockstates.append({'carrier': carrier, 'path': path, 'source': blockstate['source'], 'target': target_source})
        for tex, record in sorted(self.texture_records.items()):
            path = record['path']
            target_sha = hashlib.sha256(target_archives.read(path)).hexdigest() if target_archives.exists(path) else None
            if target_sha != record.get('sha256'):
                changed_textures.append({'texture': tex, 'path': path, 'source_sha256': record.get('sha256'), 'target_sha256': target_sha,
                                        'source_origin': record['origin'], 'target_origin': target_archives.origin(path)})
        return {'source_archives': self.a.sources, 'target_archives': target_archives.sources,
                'source_render_basis': 'Source map version dependency, never silently replaced by target defaults.',
                'changed_effective_models': changed_models, 'changed_external_blockstates': changed_blockstates,
                'changed_resolved_texture_bytes': changed_textures, 'target_parse_issues': target.issues,
                'interpretation': 'Byte or effective-data differences are flagged for migration closure; no target model is substituted automatically.'}

    def prototypes(self, world_join, world_source):
        groups = [
            ('door_panel_and_upper_trim', 'Дверная панель и верхняя часть', ['block/aca_door_1', 'block/aca_door_2'],
             'Source UV on roof_3 depicts an ornate double door; both panel faces are authored. Two source parts need ownership recognition; hinge and opening must be reviewed.'),
            ('ambiguous_door_1_facade', 'Фасадная модель door_1: назначение требует проверки', ['block/door_1'],
             'Filename is not proof of a functional door. Deep frame/central cuboid and nearby walls must be retained; rotating all fixed framing is not justified.'),
            ('wood_window_shutters', 'Деревянное окно с боковыми панелями', ['block/wood_window'],
             'Three authored cuboids include side pivots and +/-22.5 degree rotations. Separate static central element from any user-approved movable side parts; no authored open/closed pair proven.'),
            ('fixed_thin_window', 'Тонкое остекление', ['block/hold/window_01', 'block/hold/window_02', 'block/hold/window_03'],
             'Only north/south faces are authored, with no automatic cullface removal. Glass transparency and both sides require in-game verification.'),
            ('window_right_parts', 'Окно из верхней и нижней частей', ['block/window_bottom_right', 'block/window_top_right'],
             'Top/bottom are separate source states. Neighbor alignment and intended whole-object boundaries must be recognized before assigning an ID.'),
            ('vertical_ladder_sections_and_cap', 'Секции вертикальной лестницы и верхняя площадка', ['block/hold/wood_ladder_01', 'block/hold/wood_ladder_02', 'block/hold/wood_ladder_03', 'block/hold/wood_ladder_04'],
             'honey_level=1 is an ordered random set of thin sections; honey_level=0 is a wide cap/platform. Source adjacent vanilla ladder supplies climb physics. Neither random sections nor cap imply four user IDs.'),
            ('masonry_module', 'Модуль кладки', ['block/stone_bricks'] + [f'block/bricks/stonebrick_{i}' for i in range(8)],
             'Source stone_bricks contains a nine-choice weighted set. This is one masonry module candidate with preserved appearance alternatives; frequency is source cells, not model files.'),
            ('unrelated_jungle_roof', 'Кровельный модуль на том же носителе, что окно', ['block/stairs/jungle_stairs_roof'],
             'Same jungle_stairs carrier as wood_window, but top straight selects roof geometry. Neighbor stair shape changes must not change object purpose.'),
            ('wall_leaves', 'Настенная листва', ['block/addon/leaves'],
             'warped_button wall unpowered chooses leaves. Other states on the same carrier choose bloodviles and statues. This is not evidence of a complete independent tree.'),
            ('small_vials', 'Флаконы на том же носителе, что листва', ['block/addon/bloodviles'],
             'warped_button floor unpowered chooses bloodviles; independent placement category differs from wall foliage.'),
        ]
        candidates = []
        for key, name, model_paths, note in groups:
            ids = {'minecraft:' + m for m in model_paths}
            selected_states = []
            for row in world_join['states']:
                selected = {a['model'] for r in row['selected_rules'] for a in r['alternatives'] if a.get('model')}
                if selected & ids:
                    selected_states.append({k: row[k] for k in ('state', 'count', 'samples', 'selected_rules', 'random_selection', 'status')})
            model_records = [self.records[m] for m in sorted(ids) if m in self.records]
            candidates.append({'stable_candidate_key': key, 'name_ru': name, 'status': 'SOURCE_CANDIDATE_NOT_FINAL_CATALOG_OBJECT',
                               'numeric_id': None, 'independent_object_count': None, 'source_cell_count': sum(s['count'] for s in selected_states),
                               'source_states': selected_states, 'models': model_records, 'evidence_and_constraints': note,
                               'placement_mask': None, 'collision': None, 'object_ownership_rule': None, 'gameplay_verification': 'NOT_RUN'})
        world = json.loads(Path(world_source).read_text(encoding='utf-8-sig'))
        trees = [e for e in world.get('entities', []) if e.get('id') in ('bloodborne:tree1', 'bloodborne:tree2', 'bloodborne:tree3')]
        tree_evidence = local_tree_evidence(world_source)
        tree_requirement = {'status': 'ARCHITECTURE_TREE_NOT_YET_IDENTIFIED_BY_RESOURCE_INVENTORY_ALONE',
                    'pack_patch_note': 'v15: Removed cross texture trees; decorated central Yharnam with entity models including tree. Historical note is not proof of actual absence.',
                    'source_rp_tree_entity_counts': dict(collections.Counter(e['id'] for e in trees)), 'source_rp_tree_samples': trees[:6],
                    'rule': 'RP trees remain RP entities. Architectural tree ownership must be established from actual block-state cells and atlas UV; neither RP presence nor patch notes prove absence.'}
        if tree_evidence:
            tree_requirement.update(status='ARCHITECTURE_TREE_LOCAL_ASSEMBLIES_CONFIRMED_GLOBAL_OWNERSHIP_NOT_FINALIZED',
                superseding_evidence='FIRST_SET_SOURCE_EVIDENCE.json: two locally confirmed 18-cell tree-atlas assemblies, separate from 59 RP trees; global membership not finalized.')
        return {'schema_version': VERSION, 'resource_archives': self.a.sources, 'world_source_sha256': world_join['world_states_sha256'],
                'candidate_counts_are_objects': False, 'candidate_id_policy': 'No numeric IDs until semantic object ownership and true installation frequencies are established.',
                'candidates': candidates, 'tree_requirement': tree_requirement}

    def catalog_candidates(self, world_join, special):
        """Every model is accounted for; grouping is source-render evidence only."""
        records = [r for r in self.records.values() if r['origin']['role'] == 'source_pack']
        vanilla_path = next((s['path'] for s in self.a.sources if s['role'] == 'dependency' and s.get('minecraft_version')), None)
        baseline = Audit(Archives(vanilla_path, [])) if vanilla_path else None
        comparison_cache = {}
        image_comparison_cache = {}
        def normalized_effective(audit, model):
            resolved = audit.resolve_model(model)
            out_elements, texture_ids, errors = [], set(), []
            for element in resolved['elements']:
                out = dict(element); out['faces'] = {}
                for face, value in element.get('faces', {}).items():
                    texture, error = audit.texture(value['texture'], resolved['textures']) if 'texture' in value else (None, None)
                    if texture: texture_ids.add(texture)
                    if error: errors.append(error)
                    out['faces'][face] = {**value, 'texture': texture or value.get('texture')}
                out_elements.append(out)
            particle_ref = resolved['textures'].get('particle')
            particle, error = audit.texture(particle_ref, resolved['textures']) if particle_ref else (None, None)
            if particle: texture_ids.add(particle)
            return {'elements': out_elements, 'display': resolved['display'], 'ambientocclusion': resolved.get('ambientocclusion', True),
                    'gui_light': resolved.get('gui_light', 'side'), 'overrides': resolved.get('overrides', []),
                    'particle': particle, 'missing': resolved['missing'], 'surface_variable_errors': errors}, texture_ids
        def baseline_comparison(model):
            if model in comparison_cache: return comparison_cache[model]
            if baseline is None:
                return {'status': 'VANILLA_BASELINE_NOT_SUPPLIED'}
            path = resource_path(model, 'models')
            if not baseline.a.exists(path) and model not in BUILTINS:
                result = {'status': 'MODEL_NOT_IN_SOURCE_VANILLA', 'baseline': vanilla_path, 'effective_structure_equal': False, 'texture_bytes_equal': False}
            else:
                source_effective, source_textures = normalized_effective(self, model)
                vanilla_effective, vanilla_textures = normalized_effective(baseline, model)
                equal = canonical(source_effective) == canonical(vanilla_effective)
                different_images = []
                for texture in sorted(source_textures | vanilla_textures):
                    p = resource_path(texture, 'textures')
                    if p not in image_comparison_cache:
                        source_sha = hashlib.sha256(self.a.read(p)).hexdigest() if self.a.exists(p) else None
                        vanilla_sha = hashlib.sha256(baseline.a.read(p)).hexdigest() if baseline.a.exists(p) else None
                        image_comparison_cache[p] = (source_sha, vanilla_sha)
                    source_sha, vanilla_sha = image_comparison_cache[p]
                    if source_sha != vanilla_sha: different_images.append({'path': p, 'source_sha256': source_sha, 'vanilla_sha256': vanilla_sha})
                tint = any('tintindex' in f for e in source_effective['elements'] for f in e.get('faces', {}).values())
                status = 'EFFECTIVE_MODEL_DATA_DIFFERS' if not equal else 'TEXTURE_BYTES_DIFFER' if different_images else 'RESOURCE_EQUIVALENT_WITH_TINT_REVIEW' if tint else 'RESOURCE_EQUIVALENT_TO_SOURCE_VANILLA'
                result = {'status': status, 'baseline': vanilla_path, 'effective_structure_equal': equal,
                          'texture_bytes_equal': not different_images, 'different_texture_byte_paths': different_images,
                          'tint_provider_or_colormap_review': tint,
                          'limit': 'Different PNG bytes can also differ by encoding; no object classification follows from byte difference alone. Equal bytes prove resource equality. Source shader/client-wide effects are reviewed separately.'}
            comparison_cache[model] = result
            return result
        equivalent = collections.defaultdict(list)
        for r in records:
            if r['resolution_status'] == 'RESOLVED' and not r['surface_missing_texture_paths']:
                equivalent[r['render_structure_sha256']].append(r['model'])
        duplicate_sets = [sorted(v) for v in equivalent.values() if len(v) > 1]
        duplicate_owner = {m: group[0] for group in duplicate_sets for m in group}
        state_groups = {}
        per_model_usage = collections.defaultdict(list)
        for row in world_join.get('states', []):
            if not row['selected_rules']: continue
            actual_models = {a['model'] for r in row['selected_rules'] for a in r['alternatives'] if a.get('model')}
            for m in actual_models:
                per_model_usage[m].append({'state': row['state'], 'count': row['count'], 'samples': row['samples'], 'selection': row['random_selection']})
            yaw_signatures = []
            for yaw in (0, 90, 180, 270):
                groups = []
                for rule in row['selected_rules']:
                    choices = []
                    for alt in rule['alternatives']:
                        model = self.records.get(alt.get('model'))
                        choices.append({'render_structure': model['render_structure_sha256'] if model else 'MISSING:' + alt.get('model', ''),
                                        'x': alt.get('x', 0), 'y': (alt.get('y', 0)+yaw)%360,
                                        'uvlock': alt.get('uvlock', False), 'weight': alt.get('weight', 1), 'index': alt['index']})
                    groups.append({'rule_type': rule['type'], 'ordered_alternatives': choices})
                yaw_signatures.append((canonical(groups), yaw))
            signature, normalization = min(yaw_signatures)
            key = 'render-family:' + digest({'carrier': row['name'], 'signature': signature})[:24]
            if key not in state_groups:
                state_groups[key] = {'stable_candidate_key': key, 'source_carrier': row['name'],
                    'classification': 'source_model_set_yaw_family_candidate', 'source_cells': 0, 'logical_instances': None,
                    'numeric_id': None, 'independent_catalog_object': 'NOT_ESTABLISHED', 'models': set(), 'states': [],
                    'ordered_weighted_alternatives_preserved': True,
                    'equivalence_scope': 'Same effective model structure and ordered weights, modulo quarter yaw, within one carrier. UV-lock baking, physics, ownership and intended functional states are not established by this grouping.'}
            group = state_groups[key]; group['source_cells'] += row['count']; group['models'].update(actual_models)
            group['states'].append({'state': row['state'], 'count': row['count'], 'yaw_normalization': normalization,
                                    'samples': row['samples'], 'status': row['status'], 'selected_rules': row['selected_rules']})
        models = []
        for r in sorted(records, key=lambda v: v['model']):
            refs = r['references']; state_refs = [x for x in refs if x['kind'] == 'blockstate']
            parent_refs = [x for x in refs if x['kind'] == 'parent']
            block_parent_refs = [x for x in parent_refs if x['child'].split(':', 1)[1].startswith('block/')]
            missing_surface = r['surface_missing_texture_paths'] or r['missing_parents'] or [e for e in r['texture_variable_errors'] if 'surface' in e['usage']]
            unbound_variables = [e['error'] for e in r['texture_variable_errors'] if e['error'].startswith('undefined_texture_variable:')]
            template_proof = bool(block_parent_refs and not state_refs)
            reasons = []
            if template_proof:
                category = 'confirmed_template'; reasons.append('Actual block model children inherit this model; no direct effective blockstate selection.')
            elif missing_surface:
                category = 'missing'; reasons.append('Concrete model surface/dependency is unresolved; see missing paths and texture-variable usages.')
            elif r['model'] in duplicate_owner and duplicate_owner[r['model']] != r['model']:
                category = 'exact_duplicate'; reasons.append('Canonical effective ordered geometry, UV, textures, display and render properties match representative. Object behavior has not been merged.')
            elif state_refs and all(x['rule_type'] == 'multipart' for x in state_refs):
                category = 'part'; reasons.append('Selected by multipart subrules. This proves a render part of a carrier assembly, not an independent inventory object or gameplay ownership.')
            elif r['geometry']['element_count'] and not unbound_variables:
                if re.search(r'(^|/)(test[^/]*|dummy)(/|$)|_test($|/)', r['model']):
                    category = 'ambiguous'; reasons.append('Complete geometry has a test/dummy naming hint; intended catalogue use needs review, and the filename alone is not proof of a draft.')
                else:
                    category = 'finished_candidate'; reasons.append('Resolved visible elements, UV and surface textures. Resource completeness is only evidence of a finished-model candidate, not proof of an independent object.')
            else:
                category = 'ambiguous'; reasons.append('Empty/abstract geometry or unbound declarations; intended role not established.')
            used = per_model_usage.get(r['model'], [])
            models.append({'model_record_key': r['model'], 'path': r['path'], 'scope': 'item' if '/models/item/' in r['path'] else 'architecture_resource_evidence',
                'classification': category, 'classification_evidence': reasons,
                'independent_catalog_object': 'NOT_ESTABLISHED', 'numeric_id': None, 'logical_instances': None,
                'used_source_cells_where_model_is_possible': sum(s['count'] for s in used), 'usage_count_is_actual_chosen_random_model': False,
                'source_states_and_samples': used, 'references': refs,
                'resource_geometry_sha256': r['geometry_sha256'], 'render_structure_sha256': r['render_structure_sha256'],
                'source_sha256': r['source_sha256'], 'geometry_bounds': r['geometry']['bounds_model_units'],
                'source_vanilla_resource_comparison': baseline_comparison(r['model']),
                'duplicate_representative': duplicate_owner.get(r['model']),
                'missing_parent_paths': [resource_path(p, 'models') for p in r['missing_parents']],
                'missing_surface_texture_paths': r['surface_missing_texture_paths'], 'texture_variable_errors': r['texture_variable_errors'],
                'unreferenced_from_effective_blockstates': not state_refs, 'render_role_may_coexist_with_user_object': bool(parent_refs and state_refs),
                'source_model_details_report': 'RESOURCE_AUDIT_MODELS.json'})
        for cit in special['cit_models']:
            missing = [v['path'] for v in cit['texture_dependencies'].values() if v.get('present') is False]
            models.append({'model_record_key': 'cit:' + cit['path'], 'path': cit['path'], 'scope': 'legacy_CIT_item_not_architecture',
                           'source_sha256': hashlib.sha256(self.a.read(cit['path'])).hexdigest(),
                           'classification': 'missing' if missing or cit['parent_missing'] else 'finished_candidate',
                           'classification_evidence': ['Source OptiFine item matching rules and relative model resource exist; no architecture blockstate selection.'],
                           'independent_catalog_object': 'OUTSIDE_ARCHITECTURE_SCOPE', 'numeric_id': None, 'logical_instances': None,
                           'geometry_bounds': cit['geometry']['bounds_model_units'], 'missing_surface_texture_paths': missing,
                           'source_model_details_report': 'RESOURCE_AUDIT_SPECIAL.json'})
        final_groups = sorted(state_groups.values(), key=lambda v: (-v['source_cells'], v['stable_candidate_key']))
        for group in final_groups:
            group['models'] = sorted(group['models'])
            statuses = {m: baseline_comparison(m)['status'] for m in group['models']}
            group['source_vanilla_resource_comparison'] = statuses
            group['all_possible_models_equal_source_vanilla_resources'] = all(v == 'RESOURCE_EQUIVALENT_TO_SOURCE_VANILLA' for v in statuses.values())
        return {'schema_version': VERSION, 'status': 'EVIDENCE_DRAFT_NOT_FINAL_CATALOG', 'input_archives': self.a.sources,
                'source_models_accounted': len(models), 'normal_model_records': len(records), 'CIT_model_records': len(special['cit_models']),
                'classification_counts': dict(sorted(collections.Counter(r['classification'] for r in models).items())),
                'source_vanilla_comparison_counts': dict(sorted(collections.Counter(r.get('source_vanilla_resource_comparison', {}).get('status', 'CIT_OUTSIDE_VANILLA_COMPARISON') for r in models).items())),
                'unreferenced_geometry_model_records': sum('unreferenced_geometry_candidate_review' in r['evidence_classification'] for r in records),
                'source_model_records': models, 'source_render_families': final_groups,
                'exact_render_structure_duplicate_sets': sorted(duplicate_sets),
                'missing_dependency_records': [{'model': m, 'path': resource_path(m, 'models'), 'references': self.referrers.get(m, [])}
                                               for m in sorted(self.referrers) if m not in BUILTINS and not self.a.exists(resource_path(m, 'models'))],
                'semantic_grouping_rules': ['Model-file records never assign a registry block or inventory entry.',
                   'Ordered random alternatives are an authored selection set, not separate numeric IDs.',
                   'Quarter-yaw families expose orientation candidates; same carrier can and does contain unrelated families.',
                   'No automatic material/form or cross-carrier merging. Independent purpose, placement, interactions and object ownership require additional evidence.',
                   'Source cells are provisional usage only; weighted choice counts, multipart members and multi-cell parts are not logical object frequencies.',
                   'Exact-duplicate model structure is supporting evidence; source transformations, resource bytes, behavior and placement must still be compared before merging objects.'],
                'proven_prototype_evidence': {'door': 'aca_door_1 panel plus aca_door_2 upper trim are a source pairing candidate; see SOURCE neighborhoods and prototype constraints.',
                    'window': 'wood_window central cuboid fixed, side cuboids have authored pivots and +/-22.5 degree rotations; movement semantics still require review.',
                    'ladder': 'All 34 honey_level=1 cells pair with vanilla ladder; 2 honey_level=0 caps are offset and independently recognized. See RESOURCE_AUDIT_NEIGHBORHOODS.json.',
                    'trees': ('59 source RP tree entities remain RP. FIRST_SET_SOURCE_EVIDENCE.json confirms two local 18-cell architectural tree assemblies; global ownership is unfinished.'
                              if local_tree_evidence(world_join.get('world_states_source')) else
                              '59 source RP tree entities remain RP. Tree-atlas model files are present; resource inventory alone does not establish global architectural tree membership. V15 patch note is not proof of absence.')}}

    def ladder_closure(self):
        ids = ['minecraft:block/hold/wood_ladder_0' + str(i) for i in range(1, 5)]
        models = [self.records[m] for m in ids]
        paths = {r['path'] for r in models}
        textures = sorted({t for r in models for t in r['resolved_textures'].values()})
        for t in textures:
            paths.add(resource_path(t, 'textures'))
            meta = resource_path(t, 'textures') + '.mcmeta'
            if self.a.exists(meta): paths.add(meta)
        return {'input_archives': self.a.sources, 'status': 'RESOURCE_CHAIN_CLOSED',
                'source_models': ids, 'source_blockstate': self.blockstates['minecraft:beehive'],
                'source_parent_chains': {r['model']: r['parent_chain'] for r in models},
                'files': [{'path': p, 'sha256': hashlib.sha256(self.a.read(p)).hexdigest(), 'origin': self.a.origin(p)} for p in sorted(paths)],
                'textures': [self.texture_records[t] for t in textures],
                'source_to_target_model_drift': 'None for these four standalone models; verified separately in RESOURCE_AUDIT_TARGET_DIFF.json.',
                'random_alternative_order': ids[1:], 'random_weights': [1, 1, 1],
                'conditions': ['Copy exact source model elements/UV/rotations; namespace rewrite changes resource paths only.',
                    'Do not transform the 21-element cap into a narrow section.', 'Preserve original position-selected random variant before owner translation.',
                    'SOURCE geometry bounds are not a placement mask. Climbing collider and support must be reviewed with source adjacent ladders.'],
                'runtime_visual_status': 'NOT_RUN'}


def write_json(path, value):
    Path(path).write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")


def source_neighborhoods(world_report):
    """Read actual source cells. Prove ladder completeness with global counts."""
    import world_io as wi
    world = json.loads(Path(world_report).read_text(encoding='utf-8-sig'))
    archive = world.get('source', {}).get('path')
    if not archive or not Path(archive).is_file():
        return {'status': 'NOT_RUN', 'reason': 'world report does not name an available archive'}
    expected = sum(s['count'] for s in world['states'] if s['state'].startswith('minecraft:beehive['))
    sample_regions = set()
    for s in world['states']:
        if s['state'].startswith('minecraft:beehive['):
            for sample in s.get('samples', []):
                if sample['dimension'] == 'minecraft:overworld':
                    x, _, z = sample['pos']; sample_regions.add(f'region/r.{x//512}.{z//512}.mca')
    regions, sections = {}, {}
    with zipfile.ZipFile(archive) as z:
        def region(path):
            if path not in regions: regions[path] = wi.RegionFile(z.read(path)) if path in z.namelist() else None
            return regions[path]
        def chunk_sections(cx, cz):
            key = (cx, cz)
            if key not in sections:
                reg = region(f'region/r.{cx//32}.{cz//32}.mca')
                chunk = reg.get_chunk(cx%32, cz%32) if reg else None
                result = {}
                if chunk:
                    root = wi.compound(chunk.nbt().root)
                    for section in root.get('sections', wi.Tag(wi.TAG_LIST, [])).value:
                        sy = wi.compound(section)['Y'].value
                        result[sy] = wi.section_blocks(section)
                sections[key] = result
            return sections[key]
        def cell(pos):
            x, y, zz = pos; block_section = chunk_sections(x//16, zz//16).get(y//16)
            if not block_section: return 'minecraft:air'
            palette, indices = block_section
            return wi.block_state_key(palette[indices[(y%16)*256+(zz%16)*16+x%16]])
        found = []
        for path in sorted(sample_regions):
            reg = region(path)
            if not reg: continue
            rx, rz = map(int, Path(path).stem.split('.')[1:])
            for localz in range(32):
                for localx in range(32):
                    chunk = reg.get_chunk(localx, localz)
                    if not chunk: continue
                    raw = chunk.raw_nbt()
                    if b'minecraft:beehive' not in raw: continue
                    cx, cz = rx*32+localx, rz*32+localz
                    for sy, block_section in chunk_sections(cx, cz).items():
                        if not block_section: continue
                        palette, indices = block_section
                        wanted = {i: wi.block_state_key(p) for i, p in enumerate(palette) if wi.block_state_key(p).startswith('minecraft:beehive[')}
                        if not wanted: continue
                        for i, pi in enumerate(indices):
                            if pi in wanted:
                                pos = [cx*16+i%16, sy*16+i//256, cz*16+(i//16)%16]
                                found.append({'pos': pos, 'state': wanted[pi]})
        orientations = {'north': ([0, 0, 1], 'south'), 'west': ([1, 0, 0], 'east'),
                        'south': ([0, 0, -1], 'north'), 'east': ([-1, 0, 0], 'west')}
        ladder_rows = []
        conflicts = []
        for source in sorted(found, key=lambda v: tuple(v['pos'])):
            name, props = parse_state_key(source['state']); facing = props['facing']
            offset, vanilla_facing = orientations[facing]
            anchor = [source['pos'][i]+offset[i] for i in range(3)]
            member_state = cell(anchor)
            member_name, member_props = parse_state_key(member_state)
            pair_ok = member_name == 'minecraft:ladder' and member_props.get('facing') == vanilla_facing
            neighbors = [{'offset': delta, 'pos': [source['pos'][i]+delta[i] for i in range(3)],
                          'state': cell([source['pos'][i]+delta[i] for i in range(3)])}
                         for delta in ([-1,0,0],[1,0,0],[0,-1,0],[0,1,0],[0,0,-1],[0,0,1])]
            row = {**source, 'honey_level': props['honey_level'], 'facing': facing, 'expected_climbing_offset': offset,
                   'proposed_owner_cell': anchor, 'actual_adjacent_state': member_state, 'pair_matches_expected': pair_ok,
                   'model_local_translation_to_preserve_world_units': [-16*o for o in offset],
                   'neighbors': neighbors, 'pivot_change_authorized': False}
            if props['honey_level'] == '1' and not pair_ok: conflicts.append(row)
            ladder_rows.append(row)
        sample_points = [([-307,70,-221], 'ambiguous_door_1_wall_overlap'), ([-121,53,-241], 'thin_glass_adjacent_brick_wall'),
                         ([-32,37,-1120], 'wood_shutters_masonry_fence_overlap'), ([-93,59,-212], 'door_plane_upper_trim'),
                         ([-58,76,-113], 'door_plane_source_barriers')]
        snapshots = []
        for pos, key in sample_points:
            cells = [{'pos': [pos[0]+dx,pos[1]+dy,pos[2]+dz], 'state': cell([pos[0]+dx,pos[1]+dy,pos[2]+dz])}
                     for dx in range(-2,3) for dy in range(-2,4) for dz in range(-2,3)]
            snapshots.append({'stable_candidate_key': key, 'source_pos': pos, 'source_state': cell(pos), 'cells': cells})
    return {'status': 'SOURCE_READ_ONLY', 'source_archive': archive, 'world_report_sha256': file_sha(world_report),
            'scanned_regions': sorted(sample_regions), 'global_beehive_cell_count': expected, 'found_beehive_cells': len(found),
            'all_beehive_cells_accounted': len(found) == expected, 'ladder_pairs': ladder_rows,
            'honey1_expected_climbing_pair_conflicts': conflicts,
            'recognition_rule_candidate': {'source_carrier': 'minecraft:beehive', 'honey_level': '1',
               'conditions': ['Exact neighbor minecraft:ladder with paired facing at orientation-dependent offset.',
                              'Original source carrier remains an independently recorded occupied/support cell; do not silently delete it.',
                              'Cap honey_level=0 is analyzed separately; adjacency does not automatically create ownership.',
                              'Preserve original position-selected random model before pivot change.'],
               'orientation_offsets': {k: {'offset': v[0], 'vanilla_ladder_facing': v[1]} for k, v in orientations.items()},
               'status': 'PROPOSED_NOT_IMPLEMENTED', 'collision_and_support_decision': 'REQUIRES_SOURCE_AND_GAMEPLAY_REVIEW'},
            'overlap_neighborhoods': snapshots,
            'limitations': ['Source cell neighborhoods prove real occupied neighbors; geometry bounds alone do not establish complete surface intersections.',
                           'No source cells modified. A proposed owner translation is evidence, not permission to delete support, neighbors, or whole assemblies.']}


def locate_source_samples(world_report, wanted_states, limit=3):
    import world_io as wi
    world = json.loads(Path(world_report).read_text(encoding='utf-8-sig'))
    archive = world.get('source', {}).get('path')
    result = {state: [] for state in wanted_states}
    scanned = 0
    matched_bytes = [parse_state_key(s)[0].encode('utf8') for s in wanted_states]
    with zipfile.ZipFile(archive) as z:
        paths = sorted(p for p in z.namelist() if re.search(r'(^|/)region/r\.-?\d+\.-?\d+\.mca$', p))
        for path in paths:
            reg = wi.RegionFile(z.read(path))
            rx, rz = map(int, Path(path).stem.split('.')[1:])
            dim = wi._dimension_for_region(Path('world'), Path('world') / path)
            for cz in range(32):
                for cx in range(32):
                    chunk = reg.get_chunk(cx, cz)
                    if not chunk: continue
                    scanned += 1; raw = chunk.raw_nbt()
                    if not any(b in raw for b in matched_bytes): continue
                    root = wi.compound(wi.decode_nbt(raw).root)
                    for section in root.get('sections', wi.Tag(wi.TAG_LIST, [])).value:
                        container = wi.section_blocks(section)
                        if not container: continue
                        palette, indices = container
                        wanted = {i: wi.block_state_key(p) for i, p in enumerate(palette) if wi.block_state_key(p) in result and len(result[wi.block_state_key(p)]) < limit}
                        if not wanted: continue
                        sy = wi.compound(section)['Y'].value
                        for i, pi in enumerate(indices):
                            if pi in wanted and len(result[wanted[pi]]) < limit:
                                result[wanted[pi]].append({'dimension': dim, 'pos': [(rx*32+cx)*16+i%16, sy*16+i//256, (rz*32+cz)*16+(i//16)%16], 'region': path})
                    if all(len(samples) >= min(limit, next((s['count'] for s in world['states'] if s['state'] == state), limit)) for state, samples in result.items()):
                        return {'source_archive': archive, 'world_report_sha256': file_sha(world_report), 'samples': result, 'chunks_scanned': scanned, 'status': 'SAMPLES_FOUND_SOURCE_READ_ONLY'}
    return {'source_archive': archive, 'world_report_sha256': file_sha(world_report), 'samples': result, 'chunks_scanned': scanned, 'status': 'ARCHIVE_SEARCH_COMPLETE_SOURCE_READ_ONLY'}


def source_defects_markdown(audit, world_join, target_diff=None):
    rows = world_join['states']
    no_match = [r for r in rows if r['status'] == 'NO_VARIANT_MATCH']
    missing = [r for r in rows if r['status'] == 'MISSING_OR_INVALID_SURFACE_RESOURCE']
    lines = ['# Исходные дефекты и решения перед переносом', '',
             'Статус: адресный анализ исходника, до переноса; это не результаты игровой приёмки. Основной ресурсный эталон — Minecraft 1.18.2 и предоставленный v15. Частоты ниже — исходные клетки, а не целые объекты.', '',
             f"Варианты без подходящего selector: {len(no_match)} полных состояний, {sum(r['count'] for r in no_match)} клеток. Нерешённые видимые ресурсы: {len(missing)} состояния, {sum(r['count'] for r in missing)} клеток.", '',
             '| Носитель | Клетки без variant | Пример Overworld |', '|---|---:|---|']
    names = sorted({r['name'] for r in no_match}, key=lambda name: -sum(r['count'] for r in no_match if r['name'] == name))
    for name in names:
        group = [r for r in no_match if r['name'] == name]
        samples = [s for r in group for s in r['samples']]
        coord = str(samples[0]['pos']) if samples else 'координаты ещё не найдены'
        lines.append(f"| {name} | {sum(r['count'] for r in group)} | {coord} |")
    lines += ['', 'Полные states, отсутствующие selectors и все сохранённые образцы: RESOURCE_AUDIT_USED_DEFECTS.json. Например, acacia_planks содержит selectors с facing, но фактический блок карты не имеет такого свойства; red_sandstone_stairs определяет только нижнее straight-остекление, тогда как верхние straight-клетки также установлены.', '',
              '## Нерешённые видимые ссылки', '']
    for row in missing:
        errors = row['missing_model_paths'] + row['missing_parent_paths'] + row['surface_missing_texture_paths'] + [e['model'] + ': ' + e['error'] for e in row['texture_variable_errors'] if 'surface' in e['usage']]
        sample = row['samples'][0] if row['samples'] else None
        lines.append(f"- `{row['state']}` — {row['count']} клеток; образец {sample or 'не найден'}. Причина: " + '; '.join(errors) + '.')
    lines += ['', 'Эти ссылки требуют подтверждённого авторского ресурса, однозначного технического исправления с журналом либо согласованного исключения/реконструкции. Нельзя выдавать стандартный missing texture за законченный объект и нельзя подставлять похожую модель по имени.', '',
              '## Ресурсы для будущего каталога', '',
              '10 отсутствующих model paths и 5 texture paths перечислены в RESOURCE_AUDIT.json с непосредственными referrers. Часть не выбрана существующими состояниями карты, но остаётся проблемой полного строительного каталога: нижняя/открытая iron_door, button_on, window_end_7, pressure_plate14/15, старые родительские шаблоны и пути model_false. Отсутствие текущих установок не означает допустимое удаление объекта.', '',
              'Шаблон template_trapdoor_bottom содержит после корневого объекта только // комментарии. Сканер разбирает эквивалент без комментариев и сохраняет исходные байты; это не подтверждённая ошибка runtime Gson.', '',
              '## Отдельные предупреждения', '',
              'Пустой выбор multipart допускает авторский пустой визуал и учтён отдельно; он не назван отсутствующим variant. Нерешённые particle/неиспользуемые texture variables также отделены от поверхности. Их сырьевые записи нельзя скрывать, но число таких клеток не является числом missing-моделей.', '',
              '## Существенные решения первого набора', '',
              '- acacia_stairs: thin aca_door_1 и верхняя aca_door_2 выглядят дверной панелью/верхним обрамлением. Примеры [-93,59,-212] и верхняя часть [-93,61,-212]; ось/створки/ownership должны быть проверены. Название door_1 у другой семиэлементной фасадной модели не доказывает дверь.',
              '- wood_window имеет неподвижный центральный элемент и боковые части с собственными pivot и ±22.5°. Не следует вращать весь фасад вместе со створками. Назначение отдельных панелей и желаемое открытие требуют игровой приёмки.',
              '- Все 34 beehive honey_level=1 имеют точно соседнюю vanilla ladder: 28 north-секций [177,y33..60,-1108] → z+1, 6 west-секций [173,y60..65,-1105] → x+1. Две верхние площадки honey_level=0 стоят назад относительно секций; они не повторяют правило перемещения секции. SOURCE carrier/support и соседние блоки не удалены.',
              '- Реальные занятые соседями клетки показаны для door_1 [-307,70,-221], тонкого окна [-121,53,-241] и wood_window [-32,37,-1120]. Маска, занимающая эти стены/ограды, требует явного представления пересечения; автоматическое удаление/сдвиг запрещены.',
              ('- 59 RP tree1/tree2/tree3 остаются RP-сущностями. FIRST_SET_SOURCE_EVIDENCE.json подтверждает два отдельных 18-cell архитектурных дерева log/wool/melon с tree_1/tree_2 UV. V15 patch note не доказывает их отсутствие. Глобальное членство ещё не закреплено; docs/FIRST_SET_DECISIONS.md содержит точные примеры и пересечения.'
               if local_tree_evidence(world_join.get('world_states_source')) else
               '- 59 RP tree1/tree2/tree3 остаются RP-сущностями. Source tree-atlas models присутствуют; resource inventory ещё не доказывает их полное членство в самостоятельных деревьях. V15 patch note не доказывает отсутствие в фактической карте.'), '',
              'Точные исходники и ограничения: PROTOTYPE_SOURCE_CANDIDATES.json; все 1121 model records: CATALOG_CANDIDATES.json; реальные клетки: RESOURCE_AUDIT_NEIGHBORHOODS.json; цепочка первых лестниц: RESOURCE_AUDIT_LADDER_CLOSURE.json.', '']
    if target_diff:
        lines += ['## Отличия целевой версии', '',
                  f"При подстановке стандартных ресурсов 1.20.1 отличаются {len(target_diff['changed_effective_models'])} разрешённые model chains и {len(target_diff['changed_resolved_texture_bytes'])} текстурных файлов. Это отдельный риск переноса, а не исходный дефект карты. Сохраняйте подтверждённые исходные зависимости; полные before/after: RESOURCE_AUDIT_TARGET_DIFF.json.", '',
                  'Четыре wood_ladder_01..04 имеют собственные элементы без внешних parent и одну исходную spirelamp_0095.png. Для этой конкретной цепочки целевая версия не меняет модели; это не обобщается на остальные 61 отличающиеся цепочки.', '']
    return '\n'.join(lines)


def occupied_extreme(palette, longs, reverse=True):
    """Find an occupied index; palette membership alone is not occupancy."""
    air = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'}
    active = [entry not in air for entry in palette]
    if not any(active):
        return None
    if len(palette) == 1 and not longs:
        return (4095 if reverse else 0), 0
    bits = max(4, (len(palette)-1).bit_length())
    per_long, mask = 64//bits, (1 << bits)-1
    if len(longs) != math.ceil(4096/per_long):
        raise ValueError('Unexpected packed section length')
    indices = range(4095, -1, -1) if reverse else range(4096)
    for index in indices:
        pi = ((longs[index//per_long] & 0xffffffffffffffff) >> (index%per_long*bits)) & mask
        if pi >= len(palette):
            raise ValueError('Out-of-range packed palette index')
        if active[pi]:
            return index, pi
    return None


def height_audit(args, out):
    """Separate terrain-height pass; never calls the resource collector."""
    import subprocess
    import world_io as wi
    from positional_rng import weighted_index
    world_path = Path(args.world_states)
    world = json.loads(world_path.read_text(encoding='utf-8-sig'))
    archive = Path(world['source']['path'])
    join_path = out/'RESOURCE_AUDIT_WORLD_JOIN.json'
    models_path = out/'RESOURCE_AUDIT_MODELS.json'
    joined = json.loads(join_path.read_text(encoding='utf-8'))
    models = {r['model']: r for r in json.loads(models_path.read_text(encoding='utf-8'))['models']}
    # Actual face vertices only: missing faces do not supply visible geometry.
    face_axes = {'down': (1, 0), 'up': (1, 1), 'north': (2, 0),
                 'south': (2, 1), 'west': (0, 0), 'east': (0, 1)}
    height_cache = {}
    def model_height(choice):
        key = choice['model'], choice.get('x', 0), choice.get('y', 0)
        if key in height_cache: return height_cache[key]
        record = models.get(key[0])
        result = None
        if record and record['resolution_status'] == 'RESOLVED':
            owner = next((models[p] for p in record['parent_chain']
                          if p in models and 'elements' in models[p]['source']), None)
            if owner and owner['origin']['role'] == 'source_pack':
                points = []
                for element in owner['source']['elements']:
                    if 'from' not in element or 'to' not in element: continue
                    for face in element.get('faces', {}):
                        if face not in face_axes: continue
                        axis, side = face_axes[face]
                        ranges = [(element['from'][i], element['to'][i]) if i != axis else
                                  (element['from'][i] if side == 0 else element['to'][i],) for i in range(3)]
                        for point in itertools.product(*ranges):
                            point = list(point)
                            rotation = element.get('rotation')
                            if rotation: point = rotate_point(point, rotation['axis'], rotation['angle'], rotation['origin'], rotation.get('rescale', False))
                            point = rotate_point(point, 'x', -key[1], [8, 8, 8])
                            point = rotate_point(point, 'y', -key[2], [8, 8, 8])
                            points.append(point)
                if points:
                    result = {'max_y_units': max(p[1] for p in points), 'min_y_units': min(p[1] for p in points),
                              'geometry_owner': owner['model'], 'geometry_sha256': record['geometry_sha256'],
                              'model': key[0], 'x': key[1], 'y': key[2], 'source_sha256': owner['source_sha256']}
        height_cache[key] = result
        return result
    states = {}
    ignored_states = []
    for row in joined['states']:
        rules, bound = [], None
        for rule in row.get('selected_rules', []):
            heights = [model_height(choice) for choice in rule['alternatives']]
            if any(h is not None for h in heights):
                rules.append({'rule': rule, 'heights': heights})
                top = max(h['max_y_units']/16 for h in heights if h is not None)
                bound = top if bound is None else max(bound, top)
        if bound is not None:
            states[row['state']] = {'upper_bound': bound, 'rules': rules}
        elif row.get('selected_rules') and row['status'] in ('MISSING_OR_INVALID_SURFACE_RESOURCE',):
            ignored_states.append({'state': row['state'], 'source_cells': row['count'], 'status': row['status']})
    # Read original Mojang bytecode without starting Minecraft or writing classes.
    vanilla = Path(args.dependency[0])
    java_bin = Path(args.source_javap)
    result = subprocess.run([str(java_bin), '-p', '-c', '-cp', str(vanilla), 'cry'],
                            capture_output=True, text=True, check=True)
    bytecode = result.stdout
    (out/'HEIGHT_AUDIT_SOURCE_DIMENSION_BYTECODE.txt').write_text(bytecode, encoding='utf-8')
    mapping = (vanilla.parent/'client-mappings.txt').read_text(encoding='utf-8')
    block = mapping.split('net.minecraft.world.level.dimension.DimensionType -> cry:', 1)[1].split('\nnet.', 1)[0]
    for original, obfuscated in [('DEFAULT_OVERWORLD', 'p'), ('DEFAULT_NETHER', 'q'), ('DEFAULT_END', 'r'), ('DEFAULT_OVERWORLD_CAVES', 't')]:
        assert re.search(r'\b'+original+r' -> '+obfuscated+r'\b', block), original
    static = bytecode.split('  static {};', 1)[1]
    limits = {}
    for key, field in [('minecraft:overworld', 'p'), ('minecraft:the_nether', 'q'), ('minecraft:the_end', 'r'), ('minecraft:overworld_caves', 't')]:
        prefix = static.split('Field '+field+':Lcry;', 1)[0]
        construction = prefix.rsplit('invokestatic', 1)[0]
        pushes = re.findall(r'\d+:\s+(?:bipush\s+(-?\d+)|sipush\s+(-?\d+)|iconst_(m1|[0-5]))', construction)
        values = [int(a or b or ('-1' if c == 'm1' else c)) for a,b,c in pushes]
        minimum, height, logical = values[-3:]
        limits[key] = {'min_y': minimum, 'height': height, 'logical_height': logical,
                       'max_build_y_inclusive': minimum+height-1, 'max_geometry_boundary': minimum+height}
    assert limits['minecraft:overworld']['min_y'] == -64 and limits['minecraft:overworld']['height'] == 384
    target_limits = {}
    if args.target_vanilla:
        with zipfile.ZipFile(args.target_vanilla) as target:
            for name in ('overworld', 'the_nether', 'the_end', 'overworld_caves'):
                path = f'data/minecraft/dimension_type/{name}.json'
                data = json.loads(target.read(path))
                target_limits['minecraft:'+name] = {k: data[k] for k in ('min_y', 'height', 'logical_height')}
                target_limits['minecraft:'+name]['max_build_y_inclusive'] = data['min_y']+data['height']-1
    chunks = 0; sections_scanned = 0; versions = collections.Counter(); errors = []; dimensions = {}; regions = []
    source_before = file_sha(archive)
    with zipfile.ZipFile(archive) as source:
        paths = sorted(p for p in source.namelist() if re.search(r'(?:^|/)region/r\.-?\d+\.-?\d+\.mca$', p))
        overrides = [p for p in source.namelist() if '/dimension_type/' in p and p.endswith('.json')]
        for region_index, path in enumerate(paths):
            dimension = wi._dimension_for_region(Path('world'), Path('world')/path)
            dim = dimensions.setdefault(dimension, {'chunks': 0, 'sections': 0, 'section_y_min': None, 'section_y_max': None,
                       'max_non_air_y': None, 'min_non_air_y': None, 'max_non_air_samples': [], 'min_non_air_samples': [],
                       'chunk_max_non_air_histogram': collections.Counter(), 'max_authored_face_y': None, 'max_authored_face_samples': [],
                       'max_authored_face_upper_bound_y': None, 'upper_bound_samples': []})
            data = source.read(path); region = wi.RegionFile(data)
            region_chunks = 0
            for stored in region.chunks():
                root = wi.compound(stored.nbt().root)
                rx, rz = map(int, Path(path).stem.split('.')[1:])
                cx = root.get('xPos', wi.Tag(wi.TAG_INT, rx*32+stored.x)).value
                cz = root.get('zPos', wi.Tag(wi.TAG_INT, rz*32+stored.z)).value
                version = root.get('DataVersion', wi.Tag(wi.TAG_INT, -1)).value
                versions[str(version)] += 1; chunks += 1; dim['chunks'] += 1; region_chunks += 1
                chunk_max = None
                header = {'dimension': dimension, 'region': path, 'chunk': [cx, cz], 'region_local_chunk': [stored.x, stored.z],
                          'timestamp': stored.timestamp, 'DataVersion': version, 'Status': root.get('Status', wi.Tag(wi.TAG_STRING, 'unknown')).value}
                section_tags = sorted(root.get('sections', wi.Tag(wi.TAG_LIST, [])).value,
                                      key=lambda s: wi.compound(s)['Y'].value, reverse=True)
                for section in section_tags:
                    fields = wi.compound(section); sy = fields['Y'].value
                    states_tag = wi.child(section, 'block_states', wi.TAG_COMPOUND)
                    if states_tag is None: continue
                    palette_tag = wi.child(states_tag, 'palette', wi.TAG_LIST)
                    if palette_tag is None or not palette_tag.value: continue
                    palette = [wi.block_state_key(p) for p in palette_tag.value]
                    data_tag = wi.child(states_tag, 'data', wi.TAG_LONG_ARRAY); longs = data_tag.value if data_tag else []
                    sections_scanned += 1; dim['sections'] += 1
                    for direction in ('min', 'max'):
                        old = dim['section_y_'+direction]
                        dim['section_y_'+direction] = sy if old is None else (min(old, sy) if direction == 'min' else max(old, sy))
                    for reverse in (True, False):
                        extreme = occupied_extreme(palette, longs, reverse)
                        if extreme is None: continue
                        index, pi = extreme; y = sy*16+index//256
                        direction = 'max' if reverse else 'min'; key = direction+'_non_air_y'; old = dim[key]
                        if reverse: chunk_max = y if chunk_max is None else max(chunk_max, y)
                        hit = old is None or (y > old if reverse else y < old)
                        if hit: dim[key] = y; dim[direction+'_non_air_samples'] = []
                        if dim[key] == y and len(dim[direction+'_non_air_samples']) < 20:
                            dim[direction+'_non_air_samples'].append({**header, 'section_y': sy, 'section_index': index,
                                'pos': [cx*16+index%16, y, cz*16+(index//16)%16], 'state': palette[pi], 'palette_index': pi})
                    relevant = [states.get(state) for state in palette]
                    bound = max((r['upper_bound'] for r in relevant if r is not None), default=None)
                    if bound is None: continue
                    old = dim['max_authored_face_y']
                    if old is not None and sy*16+15+bound < old-1e-8: continue
                    bits = max(4, (len(palette)-1).bit_length()); per_long = 64//bits; mask = (1 << bits)-1
                    for index in range(4095, -1, -1):
                        y = sy*16+index//256
                        old = dim['max_authored_face_y']
                        if old is not None and y+bound < old-1e-8: break
                        pi = 0 if len(palette) == 1 and not longs else ((longs[index//per_long] & 0xffffffffffffffff) >> (index%per_long*bits)) & mask
                        if pi >= len(palette): raise ValueError('Geometry scan index outside palette')
                        state = relevant[pi]
                        if state is None: continue
                        upper = y+state['upper_bound']; upper_old = dim['max_authored_face_upper_bound_y']
                        if upper_old is None or upper > upper_old+1e-8:
                            dim['max_authored_face_upper_bound_y'] = upper; dim['upper_bound_samples'] = []
                        pos = [cx*16+index%16, y, cz*16+(index//16)%16]
                        if dim['max_authored_face_upper_bound_y'] == upper and len(dim['upper_bound_samples']) < 20:
                            dim['upper_bound_samples'].append({**header, 'pos': pos, 'state': palette[pi], 'max_possible_face_y': upper})
                        if old is not None and upper < old-1e-8: continue
                        for rule in state['rules']:
                            choices = rule['rule']['alternatives']; multipart = rule['rule']['type'] == 'multipart'
                            # Random multipart upper bounds remain possible, not source-oracle verified.
                            if multipart and len(choices) > 1: continue
                            selected = weighted_index([c.get('weight', 1) for c in choices], pos)
                            geometry = rule['heights'][selected]
                            if geometry is None: continue
                            face_y = y+geometry['max_y_units']/16; old = dim['max_authored_face_y']
                            if old is None or face_y > old+1e-8:
                                dim['max_authored_face_y'] = face_y; dim['max_authored_face_samples'] = []
                            if abs(dim['max_authored_face_y']-face_y) < 1e-8 and len(dim['max_authored_face_samples']) < 20:
                                dim['max_authored_face_samples'].append({**header, 'section_y': sy, 'section_index': index, 'pos': pos,
                                    'state': palette[pi], 'rule_index': rule['rule']['index'], 'rule_type': rule['rule']['type'],
                                    'ordered_alternative_index': selected, 'weights': [c.get('weight', 1) for c in choices],
                                    'max_face_y': face_y, **geometry})
                dim['chunk_max_non_air_histogram'][str(chunk_max)] += 1
            regions.append({'path': path, 'sha256': hashlib.sha256(data).hexdigest(), 'terrain_chunks': region_chunks})
            if (region_index+1)%5 == 0 or region_index+1 == len(paths):
                print(json.dumps({'height_regions_done': region_index+1, 'height_regions_total': len(paths), 'terrain_chunks': chunks,
                                  'max_non_air_y': {k:v['max_non_air_y'] for k,v in dimensions.items()}}, ensure_ascii=False), flush=True)
    source_after = file_sha(archive)
    assert source_before == source_after == world['source']['sha256_before']
    assert chunks == world['chunks']['region']
    for name, dim in dimensions.items():
        dim['chunk_max_non_air_histogram'] = dict(sorted(dim['chunk_max_non_air_histogram'].items(), key=lambda p: int(p[0]) if p[0] != 'None' else -100000))
        dim['non_air_source_cells_from_world_audit'] = sum(r['count'] for r in world['states'] if r['state'] not in {'minecraft:air','minecraft:cave_air','minecraft:void_air'})
        configured = world['level']['WorldGenSettings']['dimensions'].get(name, {}).get('type')
        dim['configured_dimension_type'] = configured
        dim['source_build_limits'] = limits.get(configured)
        if configured in limits:
            dim['available_y_above_highest_occupied_cell'] = limits[configured]['max_build_y_inclusive']-dim['max_non_air_y']
            dim['geometry_crosses_standard_build_boundary'] = dim['max_authored_face_y'] > limits[configured]['max_geometry_boundary']+1e-8
    report = {'schema': 'dreamwalker-height-audit-v1', 'status': 'PASS', 'source_archive': str(archive.resolve()),
              'source_sha256_before': source_before, 'source_sha256_after': source_after,
              'world_report_sha256': file_sha(world_path), 'models_report_sha256': file_sha(models_path), 'join_report_sha256': file_sha(join_path),
              'source_version': world['level']['Version'], 'terrain_regions': len(paths), 'terrain_chunks': chunks,
              'sections_with_block_palette': sections_scanned, 'chunk_DataVersions': dict(versions), 'dimensions': dimensions,
              'source_vanilla_limits': limits, 'source_dimension_limits_evidence': {'method': 'Mojang 1.18.2 DimensionType (cry) static initializer bytecode; official mappings validate p/q/r/t DEFAULT_* fields.',
                    'client_jar': str(vanilla.resolve()), 'client_sha256': file_sha(vanilla), 'mappings_sha256': file_sha(vanilla.parent/'client-mappings.txt'),
                    'bytecode_report': 'HEIGHT_AUDIT_SOURCE_DIMENSION_BYTECODE.txt', 'bytecode_sha256': hashlib.sha256(bytecode.encode()).hexdigest()},
              'target_vanilla_limits': target_limits, 'map_dimension_type_override_paths': overrides,
              'method': 'Every terrain chunk decoded read-only; every stored block-state section checked. Highest/lowest non-air come from actual packed indices. Source-authored face vertices include element rotations/rescale and blockstate x/y around (8,8,8); ordered variant model chosen at original position using verified original 1.18.2 RNG.',
              'regions': regions, 'geometry_excluded_missing_surface_states': ignored_states,
              'limitations': ['Visual face bounds are independent of placement/collision masks and do not establish rendered visibility after occlusion.',
                    'Only source-pack authored elements are geometry candidates; vanilla geometry with merely replaced textures and special block/entity renderers are outside this geometry statistic.',
                    'Random multipart alternative upper bounds are preserved as possible, not claimed source-oracle verified. If upper bound exceeds actual maximum, additional source rendering verification is required.',
                    'Unprovided source Forge runtime or mod dimension overrides could change runtime limits; saved type names and source vanilla defaults are confirmed separately.'],
              'gallery': {'status': 'NOT_GENERATED', 'numeric_ids_status': 'NOT_FROZEN',
                    'requirement': 'An above-city gallery must account for all occupied terrain, actual object geometry and installation masks. Standard source/target overworld ends at block Y=319. A higher layout requires a separate explicitly configured gallery dimension or height configuration; never silently extend the primary world.'}}
    write_json(out/'HEIGHT_AUDIT.json', report)
    lines = ['# Высота исходной карты', '', f'Проверены все **{chunks}** terrain chunks и **{len(paths)}** region-файлов. Оригинальный ZIP не изменён; SHA-256 до/после совпал. Использованы фактически занятые indices, а не максимальная запись palette.', '']
    for name, dim in dimensions.items():
        lines += [f"Измерение `{name}`: занятые клетки Y **{dim['min_non_air_y']}..{dim['max_non_air_y']}**. Наибольшая вершина исходной авторской block-model геометрии: Y **{dim['max_authored_face_y']}**; возможная верхняя граница всех alternatives: **{dim['max_authored_face_upper_bound_y']}**.",
                  f"Стандартный источник 1.18.2 и целевой 1.20.1 для overworld: min_y=-64, height=384, допустимые клетки -64..319. Над самой высокой занятой клеткой остаётся **{dim.get('available_y_above_highest_occupied_cell')}** допустимых Y-уровней.", '']
    lines += ['Геометрическая высота не является маской установки или коллизией. Source face vertices подтверждены ресурсами и исходными coordinates; отсечение соседями, специальные renderers и RP-сущности относятся к отдельной игровой проверке.', '',
              '**Галерея: NOT_GENERATED.** Числовые ID не закреплены. Галерея выше города должна учитывать полную высоту объектов и частей; размещение выше Y=319 требует отдельной явно заданной конфигурации высоты/измерения галереи. Основной мир автоматически не меняется.', '',
              'Подробные coordinates, состояния, выбранные модели, weights, SHA и chunk headers: HEIGHT_AUDIT.json. Исходные dimension defaults подтверждены оригинальным client bytecode и Mojang mappings в HEIGHT_AUDIT_SOURCE_DIMENSION_BYTECODE.txt.', '']
    (out/'HEIGHT_AUDIT.md').write_text('\n'.join(lines), encoding='utf-8')
    print(json.dumps({'height_report': str((out/'HEIGHT_AUDIT.json').resolve()), 'status': report['status'], 'terrain_chunks': chunks,
                      'dimensions': {k: {q:v[q] for q in ('min_non_air_y','max_non_air_y','max_authored_face_y','max_authored_face_upper_bound_y')} for k,v in dimensions.items()}}, ensure_ascii=False), flush=True)


def first_set_source_evidence(args, out):
    """Inspect bounded source assemblies without regenerating the catalog."""
    import world_io as wi
    from positional_rng import weighted_index
    world = json.loads(Path(args.world_states).read_text(encoding='utf-8-sig'))
    models = {r['model']: r for r in json.loads((out/'RESOURCE_AUDIT_MODELS.json').read_text(encoding='utf-8'))['models']}
    joined = json.loads((out/'RESOURCE_AUDIT_WORLD_JOIN.json').read_text(encoding='utf-8'))
    states = {r['state']: r for r in joined['states']}
    tree_models = [r for r in models.values() if r['origin']['role'] == 'source_pack' and
                   any(re.search(r'/tree_[123]$', str(v)) for v in r['resolved_textures'].values())]
    tree_model_names = {r['model'] for r in tree_models}
    tree_states = {key: row for key, row in states.items() if any(a['model'] in tree_model_names
                   for rule in row.get('selected_rules', []) for a in rule['alternatives'])}
    cache, regions = {}, {}
    archive = Path(world['source']['path']); before = file_sha(archive)
    with zipfile.ZipFile(archive) as source:
        names = set(source.namelist())
        def chunk(cx, cz):
            if (cx, cz) not in cache:
                path = f'region/r.{cx//32}.{cz//32}.mca'
                if path not in regions: regions[path] = wi.RegionFile(source.read(path)) if path in names else None
                stored = regions[path].get_chunk(cx%32, cz%32) if regions[path] else None
                result = {}
                if stored:
                    root = wi.compound(stored.nbt().root)
                    for section in root.get('sections', wi.Tag(wi.TAG_LIST, [])).value:
                        decoded = wi.section_blocks(section)
                        if decoded: result[wi.compound(section)['Y'].value] = ([wi.block_state_key(p) for p in decoded[0]], decoded[1])
                cache[cx, cz] = result
            return cache[cx, cz]
        def cell(pos):
            x,y,z = pos; section = chunk(x//16,z//16).get(y//16)
            return section[0][section[1][(y%16)*256+(z%16)*16+x%16]] if section else 'minecraft:air'
        def selections(pos, state):
            row = states.get(state); result = []
            if row:
                for rule in row['selected_rules']:
                    alts = rule['alternatives']
                    if rule['type'] == 'multipart' and len(alts) > 1:
                        result.append({'rule_index':rule['index'],'status':'RANDOM_MULTIPART_NOT_SOURCE_ORACLE_VERIFIED','alternatives':alts}); continue
                    chosen = weighted_index([a['weight'] for a in alts],pos)
                    selected = alts[chosen]
                    result.append({'rule_index':rule['index'],'rule_type':rule['type'],'selected_alternative_index':chosen,
                                   'weights':[a['weight'] for a in alts], 'model':selected['model'], 'x':selected['x'],'y':selected['y']})
            return result
        def record(pos):
            state = cell(pos)
            return {'pos':list(pos),'state':state,'source_selection':selections(list(pos),state)}
        neighborhoods = {}
        for key, center, radius, min_y, max_y in [
            ('ornate_door_north',[-93,59,-212],3,-2,5), ('ornate_door_east',[-58,76,-113],3,-2,5),
            ('wood_window_north',[-32,37,-1120],3,-2,4), ('thin_window_north',[-121,53,-241],3,-2,4),
            ('modular_wall_connections',[154,58,-976],1,-1,1)]:
            cells = []
            for y in range(center[1]+min_y,center[1]+max_y+1):
                for z in range(center[2]-radius,center[2]+radius+1):
                    for x in range(center[0]-radius,center[0]+radius+1):
                        data = record([x,y,z])
                        if data['state'] not in {'minecraft:air','minecraft:cave_air','minecraft:void_air'}: cells.append(data)
            neighborhoods[key] = {'center':center,'relative_bounds':{'x':[-radius,radius],'y':[min_y,max_y],'z':[-radius,radius]},'non_air_cells':cells}
        trees = []
        for x,z in [(-16,-504),(-14,-500)]:
            spine = [record([x,y,z]) for y in range(-64,140) if cell([x,y,z]) in tree_states]
            members = list(spine); recognized = []
            for base_y,center_state,first,second in [(125,'minecraft:orange_wool','jungle_log','oak_log'),
                                                   (128,'minecraft:magenta_wool','acacia_log','spruce_log'),
                                                   (131,'minecraft:light_blue_wool','dark_oak_log','birch_log')]:
                tier = [([x,base_y,z],center_state),([x,base_y,z-3],f'minecraft:{first}[axis=x]'),
                        ([x+3,base_y,z],f'minecraft:{first}[axis=z]'),([x,base_y,z+3],f'minecraft:{second}[axis=x]'),
                        ([x-3,base_y,z],f'minecraft:{second}[axis=z]')]
                observed = [{**record(pos),'expected_state':expected,'matches_expected':cell(pos)==expected} for pos,expected in tier]
                recognized.append({'y':base_y,'all_five_source_cells_match':all(r['matches_expected'] for r in observed),'members':observed})
                members.extend(r for r in observed if r['pos'] != [x,base_y,z])
            unique = {tuple(r['pos']):r for r in members}; members = sorted(unique.values(),key=lambda r:r['pos'])
            points = []; selected_models = []
            for member in members:
                for selection in member['source_selection']:
                    model = models.get(selection.get('model'))
                    if not model: continue
                    owner = next((models[p] for p in model['parent_chain'] if p in models and 'elements' in models[p]['source']),None)
                    if not owner: continue
                    for element in owner['source']['elements']:
                        if 'from' not in element or 'to' not in element: continue
                        for point in element_vertices(element):
                            point = rotate_point(point,'x',-selection['x'],[8,8,8]); point = rotate_point(point,'y',-selection['y'],[8,8,8])
                            points.append([round(member['pos'][i]+point[i]/16,10) for i in range(3)])
                    selected_models.append({'pos':member['pos'],'state':member['state'], **selection,'source_model_sha256':model['source_sha256'], 'geometry_sha256':model['geometry_sha256']})
            low,high = [min(p[i] for p in points) for i in range(3)], [max(p[i] for p in points) for i in range(3)]
            # Record neighboring occupied cells so broad service masks cannot erase them.
            overlaps = []
            for y in range(math.floor(low[1]),math.ceil(high[1])):
                for zz in range(math.floor(low[2]),math.ceil(high[2])):
                    for xx in range(math.floor(low[0]),math.ceil(high[0])):
                        pos = (xx,y,zz)
                        if pos in unique: continue
                        state = cell(pos)
                        if state not in {'minecraft:air','minecraft:cave_air','minecraft:void_air'}:
                            overlaps.append({'pos':list(pos),'state':state,'is_other_tree_atlas_source_cell':state in tree_states})
            minimum_spine = min(r['pos'][1] for r in spine)
            trees.append({'stable_evidence_key':f'tree_atlas_assembly_x{x}_z{z}', 'center_xz':[x,z],
                'status':'THREE_FULL_BRANCH_TIERS_AND_SPINE_CONFIRMED_IN_LOCAL_SOURCE', 'source_cell_members':members,
                'member_count_is_local_source_cells_not_global_objects':len(members),'spine':spine,'branch_tiers':recognized,
                'below_lowest_spine':record([x,minimum_spine-1,z]),'selected_original_coordinate_models':selected_models,
                'transformed_visible_element_bounds_world':{'min':low,'max':high},
                'occupied_nonmember_cells_inside_visual_AABB':overlaps,
                'ownership_limit':'Bounded example membership is confirmed by exact source state pattern and atlas alignment; it is not a complete global ownership rule. AABB is neither installation mask nor solid collision.'})
        tree_texture_manifest = []
        with zipfile.ZipFile(args.pack) as pack:
            directory = out/'FIRST_SET_SOURCE_IMAGES';directory.mkdir(exist_ok=True)
            for name in ('tree_1','tree_2','tree_3'):
                entry=f'assets/minecraft/textures/block/addon/{name}.png';data=pack.read(entry)
                (directory/(name+'.png')).write_bytes(data)
                tree_texture_manifest.append({'entry':entry,'source_sha256':hashlib.sha256(data).hexdigest(),'output':str((directory/(name+'.png')).resolve()),'method':'Exact source bytes; no image editing.'})
    after = file_sha(archive); assert before == after == world['source']['sha256_before']
    result={'schema':'dreamwalker-first-set-source-evidence-v1','status':'SOURCE_EVIDENCE_ONLY_NOT_MIGRATED',
       'source_archive':str(archive.resolve()),'source_sha256_before':before,'source_sha256_after':after,
       'resource_report_sha256':file_sha(out/'RESOURCE_AUDIT_MODELS.json'),'source_neighborhoods':neighborhoods,
       'tree_atlas_model_records':[{'model':r['model'],'source_sha256':r['source_sha256'],'geometry_sha256':r['geometry_sha256'],
          'bounds_model_units':r['geometry']['bounds_model_units'],'raw_source':r['source'],
          'selected_source_states':[{'state':s['state'],'source_cells':s['count'],'samples':s['samples']} for s in tree_states.values()
             if any(a['model']==r['model'] for rule in s['selected_rules'] for a in rule['alternatives'])]} for r in tree_models],
       'unique_tree_atlas_selected_state_source_cells':sum(r['count'] for r in tree_states.values()),
       'count_meaning':'Source cells selecting tree_1/tree_2/tree_3 atlas models, alternatives included once per state; not independent trees. Atlases also contain architecture artwork; texture path alone does not classify all source models as trees.',
       'confirmed_local_tree_assemblies':trees,'source_tree_images':tree_texture_manifest,
       'rp_tree_entity_counts':{kind:sum(e['id']==kind for e in world['entities']) for kind in ('bloodborne:tree1','bloodborne:tree2','bloodborne:tree3')},
       'rp_entity_policy':'Retain RP IDs/UUID/position/rotation/scale/data; these 59 entities are separate from the locally confirmed architectural block-tree assemblies.',
       'numeric_ids':None,'global_tree_ownership_status':'NOT_FINALIZED','game_verification':'NOT_RUN'}
    write_json(out/'FIRST_SET_SOURCE_EVIDENCE.json',result)
    print(json.dumps({'first_set_source_evidence':str((out/'FIRST_SET_SOURCE_EVIDENCE.json').resolve()),
          'tree_atlas_models':len(tree_models),'tree_atlas_selected_source_cells':result['unique_tree_atlas_selected_state_source_cells'],
          'confirmed_local_tree_assemblies':[{k:t[k] for k in ('center_xz','member_count_is_local_source_cells_not_global_objects','transformed_visible_element_bounds_world')} for t in trees]},ensure_ascii=False),flush=True)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--pack", required=True)
    ap.add_argument("--dependency", action="append", default=[], help="vanilla or mod JAR in resource precedence order after source pack")
    ap.add_argument("--world-states", help="optional full-state counts/samples to resolve exact source rules")
    ap.add_argument('--alpha-cache', help='optional previous RESOURCE_AUDIT_TEXTURES.json; only PNG decoding is cached by exact SHA')
    ap.add_argument('--source-neighborhoods', action='store_true', help='read actual source ladder pairs and prototype surroundings from world-report archive')
    ap.add_argument('--locate-defect-samples', action='store_true', help='locate source samples for used visible defects not sampled in the world report')
    ap.add_argument('--target-vanilla', help='optional target vanilla JAR for explicit comparison; source dependencies remain authoritative')
    ap.add_argument('--height-only', action='store_true', help='separate read-only all-terrain height pass; never regenerates resource/catalog reports')
    ap.add_argument('--first-set-evidence-only', action='store_true', help='bounded actual first-set source assemblies; never regenerates resource/catalog reports')
    ap.add_argument('--source-javap', default='C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/javap.exe', help='JDK javap to confirm original DimensionType defaults')
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    out = Path(args.out); out.mkdir(parents=True, exist_ok=True)
    if args.height_only:
        if not args.world_states or not args.dependency: ap.error('--height-only requires source --world-states and --dependency')
        height_audit(args, out)
        return
    if args.first_set_evidence_only:
        if not args.world_states: ap.error('--first-set-evidence-only requires --world-states')
        first_set_source_evidence(args,out)
        return
    archives = Archives(args.pack, args.dependency)
    audit = Audit(archives)
    if args.alpha_cache:
        cache = json.loads(Path(args.alpha_cache).read_text(encoding='utf-8-sig'))
        keys = {'width', 'height', 'bit_depth', 'color_type', 'interlace', 'sha256', 'status', 'alpha_min', 'alpha_max', 'alpha_pixels', 'alpha_histogram'}
        for record in list(cache.get('textures', {}).values()) + list(cache.get('source_asset_pngs', {}).values()):
            if record.get('sha256') and record.get('status') == 'PASS':
                audit.alpha_cache[record['sha256']] = {k: v for k, v in record.items() if k in keys}
    audit.collect()
    # Alpha coverage includes every asset PNG, not just texture references from models.
    pngs = {}
    for path in archives.source_names:
        if path.startswith("assets/") and path.endswith(".png"):
            match = re.fullmatch(r"assets/([^/]+)/textures/(.+)\.png", path)
            if match:
                tex = match[1] + ":" + match[2]
                audit.inspect_texture(tex)
                pngs[path] = audit.texture_records[tex]
            else:
                try: pngs[path] = png_alpha(archives.read(path))
                except Exception as exc: pngs[path] = {"status": "ERROR", "error": str(exc)}
    special = audit.special_resources()
    models = sorted(audit.records.values(), key=lambda r: r["model"])
    pack_models = [r for r in models if r["origin"]["role"] == "source_pack"]
    dupes = collections.defaultdict(list)
    raw_dupes = collections.defaultdict(list)
    for r in pack_models:
        raw_dupes[r["source_sha256"]].append(r["model"])
        if r["resolution_status"] == "RESOLVED" and not r["missing_texture_paths"]:
            dupes[r["render_structure_sha256"]].append(r["model"])
    missing_models = []
    for model in sorted(set(audit.referrers) | {p for r in models for p in r["missing_parents"]}):
        if model not in BUILTINS and not archives.exists(resource_path(model, "models")):
            missing_models.append({"model": model, "path": resource_path(model, "models"), "references": audit.referrers.get(model, []),
                                   "status": "MISSING_IN_PROVIDED_ARCHIVES" if args.dependency else "ABSENT_FROM_PACK_EXTERNAL_DEPENDENCY_UNVERIFIED"})
    model_inventory = [{'path': p, 'source_sha256': hashlib.sha256(archives.read(p)).hexdigest(),
                        'parse_status': 'INVALID_JSON' if any(i.get('kind') == 'invalid_json' and i.get('path') == p for i in audit.issues) else 'PARSED'}
                       for p in archives.source_names if '/models/' in p and p.endswith('.json')]
    coverage = {"schema_version": VERSION, "input_archives": archives.sources,
        "source_asset_counts": {"blockstates": sum(b['origin']['role'] == 'source_pack' for b in audit.blockstates.values()), "models": len(model_inventory), "parsed_models": len(pack_models), "block_models": sum("/block/" in r["path"] for r in model_inventory),
            "item_models": sum("/item/" in r["path"] for r in model_inventory), "asset_pngs": len(pngs), "properties": len(special["properties"]), "mcmeta": len(special["mcmeta"]), "cit_models": len(special['cit_models'])},
        'source_model_file_inventory': model_inventory,
        'effective_carrier_count': len(audit.blockstates),
        'external_blockstate_carriers_referencing_source_models_or_textures': sorted(b['carrier'] for b in audit.blockstates.values() if b['origin']['role'] != 'source_pack'),
        "rules": {"variants": sum(r["type"] == "variant" for b in audit.blockstates.values() for r in b["rules"]),
                  "multipart": sum(r["type"] == "multipart" for b in audit.blockstates.values() for r in b["rules"]),
                  "random_alternative_rules": sum(len(r["alternatives"]) > 1 for b in audit.blockstates.values() for r in b["rules"])},
        "model_evidence_counts": dict(collections.Counter(label for r in pack_models for label in r["evidence_classification"])),
        "missing_model_dependencies": missing_models,
        "missing_texture_dependencies": [r for r in audit.texture_records.values() if not r["present"]],
        "issues": sorted(audit.issues, key=canonical), "raw_duplicate_groups": [v for _, v in sorted(raw_dupes.items()) if len(v) > 1],
        "effective_render_duplicate_groups": [v for _, v in sorted(dupes.items()) if len(v) > 1],
        "semantic_catalog": {"status": "NOT_FINALIZED", "independent_objects_count": None,
              "reason": "Model/state inventory does not establish object ownership, purpose, behavior or assembly boundaries. No numeric registry IDs assigned."},
        "fingerprint_rules": {"source_sha256": "Exact source JSON bytes.", "geometry_sha256": "Canonical ordered effective elements with inherited geometry and resolved face textures; preserves UV, rotations, cullface and tintindex.",
             "render_structure_sha256": "Geometry plus effective display/AO/gui light/particle/overrides. Resource path equality alone is insufficient.",
             "dependency_content_sha256": "Canonical resolved texture path to exact PNG SHA; PNG .mcmeta recorded separately. No unverified parent is considered a duplicate."},
        'migration_requirements': [
            {'feature': 'Inherited elements, rotations, UV, display, texture variables and animations', 'preservation': 'Namespaced resource closure using original 1.18.2 dependencies; export changed target inheritance explicitly rather than silently use 1.20 defaults.'},
            {'feature': 'Variants/multipart/x/y/uvlock/ordered weights', 'preservation': 'Keep resource selectors and transforms as evidence; object code defines independent states and membership, never copies unrelated vanilla carrier transitions.'},
            {'feature': 'Source positional random selection', 'preservation': 'Resolve on original source coordinates with source 1.18.2 renderer behavior; prove equivalence if using 1.20.1 algorithm, then store chosen variant before changing pivot or grouping.'},
            {'feature': 'Tintindex and colormaps', 'preservation': 'Register appropriate object color providers in code after semantic classification; texture assets alone do not carry vanilla carrier tint behavior.'},
            {'feature': 'Alpha, source render layer directives and face culling', 'preservation': 'Select explicit Fabric render layers per independently classified object and verify both sides/cullface in game; partial PNG alpha does not automatically mandate a whole-carrier layer.'},
            {'feature': 'OptiFine emissive/CIT and core text shader', 'preservation': 'Record separate source visual-profile dependence. Do not introduce an obligatory shader/client mod to hide a migration defect.'},
            {'feature': 'Connected textures', 'preservation': 'No CTM paths present in this source archive. No CTM dependency inferred.'}
        ],
        "limitations": ["No model candidate is declared an independently placeable object.", "No collision, installation mask, pivot change or final object count inferred from bounding boxes.",
                        "No source positional random model selection recalculated; every ordered weight and rule preserved.", "Two-sided/cullface findings require in-game verification.",
                        "CIT, emissive properties and shader layers describe source-specific effects; they do not prove standard Fabric support.",
                        "Without supplied vanilla/mod dependencies, external resource absence remains unverified."]}
    write_json(out / "RESOURCE_AUDIT.json", coverage)
    write_json(out / "RESOURCE_AUDIT_BLOCKSTATES.json", {"schema_version": VERSION, "blockstates": audit.blockstates})
    write_json(out / "RESOURCE_AUDIT_MODELS.json", {"schema_version": VERSION, "models": models})
    write_json(out / "RESOURCE_AUDIT_TEXTURES.json", {"schema_version": VERSION, "textures": audit.texture_records, "source_asset_pngs": pngs})
    write_json(out / "RESOURCE_AUDIT_SPECIAL.json", {"schema_version": VERSION, **special})
    if args.world_states:
        world_join = audit.world_join(args.world_states)
        if args.locate_defect_samples:
            missing_samples = [r['state'] for r in world_join['states'] if r['status'] == 'MISSING_OR_INVALID_SURFACE_RESOURCE' and not r['samples']]
            located = locate_source_samples(args.world_states, missing_samples) if missing_samples else {'status': 'NO_MISSING_SAMPLES', 'samples': {}}
            write_json(out / 'RESOURCE_AUDIT_DEFECT_SAMPLES.json', located)
            for row in world_join['states']:
                if row['state'] in located['samples']:
                    row['samples'] = located['samples'][row['state']]
                    row['samples_origin'] = 'Independent read-only source archive search; WORLD_AUDIT was not edited.'
        write_json(out / "RESOURCE_AUDIT_WORLD_JOIN.json", world_join)
        write_json(out / 'PROTOTYPE_SOURCE_CANDIDATES.json', audit.prototypes(world_join, args.world_states))
        write_json(out / 'CATALOG_CANDIDATES.json', audit.catalog_candidates(world_join, special))
        write_json(out / 'RESOURCE_AUDIT_LADDER_CLOSURE.json', audit.ladder_closure())
        if args.source_neighborhoods: write_json(out / 'RESOURCE_AUDIT_NEIGHBORHOODS.json', source_neighborhoods(args.world_states))
        statuses = {status: {'states': sum(r['status'] == status for r in world_join['states']),
                             'source_cells': sum(r['count'] for r in world_join['states'] if r['status'] == status)}
                    for status in sorted({r['status'] for r in world_join['states']})}
        no_match = [r for r in world_join['states'] if r['status'] == 'NO_VARIANT_MATCH']
        visible_missing = [r for r in world_join['states'] if r['status'] == 'MISSING_OR_INVALID_SURFACE_RESOURCE']
        write_json(out / 'RESOURCE_AUDIT_USED_DEFECTS.json', {'world_states_sha256': world_join['world_states_sha256'],
                   'selection_statuses': statuses,
                   'no_variant_match_carrier_cells': {name: sum(r['count'] for r in no_match if r['name'] == name) for name in sorted({r['name'] for r in no_match})},
                   'no_variant_match': no_match, 'missing_visible_resources': visible_missing,
                   'meaning': 'Counts are source cells, not independent objects. Empty multipart selection and particle warnings are separated from missing visible models.'})
    target_diff = audit.target_diff(args.target_vanilla) if args.target_vanilla else None
    if target_diff: write_json(out / 'RESOURCE_AUDIT_TARGET_DIFF.json', target_diff)
    if args.world_states: (out / 'SOURCE_DEFECTS.md').write_text(source_defects_markdown(audit, world_join, target_diff), encoding='utf8')
    image_dir = out / 'RESOURCE_AUDIT_SOURCE_IMAGES'; image_dir.mkdir(exist_ok=True)
    image_provenance = []
    for name in ('roof_2', 'roof_3', 'door', 'spirelamp_0022', 'spirelamp_0095', 'window_4'):
        source_path = f'assets/minecraft/textures/block/addon/{name}.png'
        if archives.exists(source_path):
            data = archives.read(source_path)
            (image_dir / (name + '.png')).write_bytes(data)
            image_provenance.append({'archive_entry': source_path, 'output': str((image_dir / (name + '.png')).resolve()), 'sha256': hashlib.sha256(data).hexdigest(), 'method': 'Exact byte copy; no image editing.'})
    write_json(out / 'RESOURCE_AUDIT_SOURCE_IMAGE_PROVENANCE.json', image_provenance)
    counts = coverage["source_asset_counts"]
    md = ["# Аудит исходного ресурспака", "", "Статус: полный файловый инвентарь и ресурсные связи; семантический каталог ещё не закреплён. Номера объектов не назначены.", "",
          f"Исходный архив: `{archives.sources[0]['path']}`", f"SHA-256: `{archives.sources[0]['sha256']}`", "",
          f"Blockstates исходного пакета: **{counts['blockstates']}**; модели блока: **{counts['block_models']}**; предметные модели: **{counts['item_models']}**; отдельные CIT-модели: **{counts['cit_models']}**; PNG assets: **{counts['asset_pngs']}**.",
          f"Дополнительные vanilla carriers, чьи blockstate-файлы отсутствуют в пакете, но выбирают переопределённые модели/текстуры или наследуют источник: **{coverage['effective_carrier_count'] - counts['blockstates']}**. Их references учтены отдельно с origin=dependency. Невалидные JSON и нестандартные комментарии сохранены в файловом инвентаре и issues.",
          f"Правила variants: **{coverage['rules']['variants']}**; multipart: **{coverage['rules']['multipart']}**; правила со случайным выбором: **{coverage['rules']['random_alternative_rules']}**.", "",
          "Пять JSON-отчётов сохраняют исходные selectors/AND/OR, порядок и веса моделей, x/y/uvlock, всю геометрию и UV, наследование, display, texture variables, PNG alpha, .mcmeta, tint/cullface, CIT и шейдерные параметры.", "",
          f"Ресурсные ссылки на модели без файла в переданных архивах: **{len(missing_models)}**. Без vanilla JAR это непроверенные внешние зависимости, а не установленный дефект. Отсутствующие текстурные ссылки: **{len(coverage['missing_texture_dependencies'])}**.",
          f"Группы побайтно одинаковых JSON: **{len(coverage['raw_duplicate_groups'])}**; группы одинаковой разрешённой render structure: **{len(coverage['effective_render_duplicate_groups'])}**. Это доказательства для анализа; поведение и установка должны быть сравнены до объединения объектов.", "",
          "## Источник специфического рендера", "",
          f"CTM-пути в архиве: **{len(special['ctm_paths'])}**. Эмиссивные PNG `_e`: **{len(special['emissive_png_paths'])}**. Сохраняются точные .properties, включая `layer.translucent=flower_pot, warped_button`, `layer.solid=acacia_trapdoor,gold_block` и `suffix.emissive=_e`.",
          "CIT связан с предметами и именами NBT; его не следует объявлять архитектурой. Core shader `rendertype_text.vsh` меняет отображение текста/маркеров. Прямой перенос этих настроек в обычный Fabric не доказан.", "",
          "## Границы доказательства", "", "Геометрические bounds описывают видимый объём, но не сплошную коллизию и не маску установки. Парные грани нулевой толщины ещё требуют проверки отсечения с обеих сторон в игре. Модели без blockstate-ссылок остаются кандидатами: законченные объекты, части, шаблоны и черновики разделяются по назначению и связи с картой.", "",
          "Порядок случайных моделей и веса сохранены. Фактический выбранный вариант определяется из исходной позиции и renderer Minecraft 1.18.2 до сдвига pivot/группировки; использование алгоритма 1.20.1 требует подтверждения эквивалентности. Tint требует регистрации подходящих color providers, а прозрачность — слоя Fabric и игровой проверки. Геометрия, UV, display и анимации остаются ресурсами. Точный список решений записан в migration_requirements.", "",
          "## Воспроизведение", "", "```powershell", "& 'C:/Users/vakir/miniconda3/python.exe' 'bloodborne complete/tools/analyze_resources.py' --pack 'C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip' --dependency 'bloodborne complete/inputs/extracted/vanilla-1.18.2/client.jar' --target-vanilla 'C:/Users/vakir/Limacina/project/dw/1.20.1.jar' --world-states 'bloodborne complete/reports/WORLD_AUDIT.json' --out 'bloodborne complete/reports'", "```", "",
          "Источник карты — Minecraft 1.18.2. `--dependency` задаёт исходный vanilla client и прочие ресурсы в порядке приоритета после пакета. Целевой 1.20.1 проверяется отдельно через `--target-vanilla`; различия не подменяют исходник. `--alpha-cache` может использовать предыдущий RESOURCE_AUDIT_TEXTURES.json: сохраняются только результаты PNG alpha с совпадающим точным SHA файла. Формат статистики: массив `states` с name/properties/count/samples либо словарь canonical-state → count/record.", ""]
    (out / "RESOURCE_AUDIT.md").write_text("\n".join(md), encoding="utf-8")
    print(json.dumps({"report": str((out / "RESOURCE_AUDIT.json").resolve()), "counts": counts, "rules": coverage["rules"], "unresolved_model_links": len(missing_models), "unresolved_texture_links": len(coverage["missing_texture_dependencies"])}, ensure_ascii=False))


if __name__ == "__main__":
    main()
