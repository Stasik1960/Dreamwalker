#!/usr/bin/env python3
"""Index source-world carrier blocks without expanding the world in memory.

The SQLite sidecar is deliberately a build cache: it contains map coordinates
and is ignored by git.  The returned summary is small enough for planning code.
"""
from __future__ import annotations

import gc, gzip, hashlib, json, os, sqlite3, tempfile, zipfile
from collections import Counter, defaultdict
from pathlib import Path

from world_io import (RegionFile, TAG_COMPOUND, TAG_INT, TAG_LIST, TAG_LONG_ARRAY,
                      Tag, block_state_key, child, compound, unpack_palette_indices)

FORMAT = "source-carrier-index-v1"
EXPECTED_SOURCE_SHA256 = "4353737d536677469d3b895e3515496ab64fab7b224e43428eb96e8c09724a51"

def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as f:
        for part in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(part)
    return digest.hexdigest()

def _canonical(name, props):
    return name + ("[" + ",".join(f"{k}={props[k]}" for k in sorted(props)) + "]" if props else "")

def _state(entry):
    item = compound(entry); name = item["Name"].value
    properties = compound(item["Properties"]).copy() if item.get("Properties") else {}
    props = {key: str(value.value) for key, value in properties.items()}
    return _canonical(name, props), {"id": name, "properties": props}

def _resource_carriers(root):
    """All vanilla source IDs referenced by the current mapping resources."""
    root = Path(root)
    ids = set()
    # These are mapping definitions; scanning meshes/geometry is both unrelated
    # and needlessly expensive (their large numeric arrays contain no sources).
    paths = [root / "src/main/resources/bloodborne_blocks/definitions.json",
             root / "src/main/resources/bloodborne_blocks/v2/definitions.json",
             root / "src/main/resources/bloodborne_blocks/logical/definitions.json"]
    for path in paths:
        if not path.exists(): continue
        try: data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError): continue
        stack = [data]
        while stack:
            value = stack.pop()
            if isinstance(value, dict):
                source = value.get("source")
                if isinstance(source, str) and source.startswith("minecraft:"): ids.add(source)
                stack.extend(value.values())
            elif isinstance(value, list): stack.extend(value)
    # v2 sources maps generated IDs to bare Minecraft path names.
    source_file = root / "src/main/resources/bloodborne_blocks/v2/sources.json"
    if source_file.exists():
        for values in json.loads(source_file.read_text(encoding="utf-8")).values():
            for value in values if isinstance(values, list) else []: ids.add("minecraft:" + value if ":" not in value else value)
    return ids

def _mapping_sha256(root):
    root = Path(root)
    paths = [root / "src/main/resources/bloodborne_blocks/definitions.json",
             root / "src/main/resources/bloodborne_blocks/v2/definitions.json",
             root / "src/main/resources/bloodborne_blocks/logical/definitions.json",
             root / "src/main/resources/bloodborne_blocks/v2/sources.json"]
    digest = hashlib.sha256()
    for path in paths:
        digest.update(str(path.relative_to(root)).encode()); digest.update(b"\0")
        digest.update(path.read_bytes() if path.exists() else b"<missing>"); digest.update(b"\0")
    return digest.hexdigest()

def _pack_evidence(pack, vanilla, carriers):
    """Return carrier -> changed resource paths, including parent/model textures."""
    if not pack or not vanilla: return {}
    result = defaultdict(set)
    with zipfile.ZipFile(pack) as p, zipfile.ZipFile(vanilla) as v:
        names = set(p.namelist()); vanilla_names = set(v.namelist())
        changed = {n for n in names if n not in vanilla_names or p.read(n) != v.read(n)}
        def ref(value, default="minecraft"):
            return value.split(":", 1) if ":" in value else (default, value)
        def read(path):
            if path in names: return p.read(path)
            if path in vanilla_names: return v.read(path)
            raise KeyError(path)
        def model_deps(path, seen):
            if path in seen: return set()
            seen.add(path); out = {path} if path in changed else set()
            try: data = json.loads(read(path))
            except (KeyError, json.JSONDecodeError): return out
            parent = data.get("parent")
            if isinstance(parent, str):
                namespace, name = ref(parent)
                out |= model_deps(f"assets/{namespace}/models/{name}.json", seen)
            for texture in data.get("textures", {}).values():
                if isinstance(texture, str) and not texture.startswith("#"):
                    namespace, name = ref(texture)
                    for suffix in (".png", ".png.mcmeta"):
                        candidate = f"assets/{namespace}/textures/{name}{suffix}"
                        if candidate in changed: out.add(candidate)
            return out
        # Every vanilla blockstate is a possible carrier: changed resources can
        # alter its geometry even when no current mapping names it yet.
        ids = set(carriers)
        for entry in names | vanilla_names:
            if entry.startswith("assets/minecraft/blockstates/") and entry.endswith(".json"):
                ids.add("minecraft:" + Path(entry).stem)
        for carrier in ids:
            namespace, name = carrier.split(":", 1)
            blockstate = f"assets/{namespace}/blockstates/{name}.json"
            evidence = {blockstate} if blockstate in changed else set()
            if blockstate in names or blockstate in vanilla_names:
                try: data = json.loads(p.read(blockstate))
                except KeyError: data = json.loads(v.read(blockstate))
                except json.JSONDecodeError: data = {}
                def walk(value):
                    if isinstance(value, dict):
                        if isinstance(value.get("model"), str):
                            ns, mn = ref(value["model"], namespace)
                            evidence.update(model_deps(f"assets/{ns}/models/{mn}.json", set()))
                        for item in value.values(): walk(item)
                    elif isinstance(value, list):
                        for item in value: walk(item)
                walk(data)
            if evidence: result[carrier].update(evidence)
    return {key: sorted(value) for key, value in result.items()}

def _inputs(source, pack, vanilla, root, dimension):
    return {"format": FORMAT, "source_sha256": sha256(source), "pack_sha256": sha256(pack) if pack else None,
            "vanilla_sha256": sha256(vanilla) if vanilla else None,
            "mapping_sha256": _mapping_sha256(root), "dimension": dimension.strip("/")}

def _load_cache(cache, inputs):
    meta = Path(str(cache) + ".json.gz")
    if not Path(cache).is_file() or not meta.is_file(): return None
    try:
        with gzip.open(meta, "rt", encoding="utf-8") as f: data = json.load(f)
        body = {"inputs": data.get("inputs"), "database_sha256": data.get("database_sha256"), "index": data.get("index")}
        digest = hashlib.sha256(json.dumps(body, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
        if data.get("inputs") != inputs or data.get("database_sha256") != sha256(cache) or data.get("body_sha256") != digest: return None
        return data["index"]
    except (OSError, ValueError, KeyError): return None

def build_index(source, cache_path, *, pack=None, vanilla=None, dimension="ether/dimensions/eh_s2/yharnam", expected_sha256=EXPECTED_SOURCE_SHA256):
    """Build or verify the coordinate database; only ``region/`` MCA files count."""
    source, cache = Path(source), Path(cache_path); root = Path(__file__).resolve().parents[1]
    inputs = _inputs(source, pack, vanilla, root, dimension)
    if expected_sha256 and inputs["source_sha256"] != expected_sha256:
        raise ValueError("unexpected source SHA-256")
    cached = _load_cache(cache, inputs)
    if cached: return cached
    carriers = _resource_carriers(root); evidence = _pack_evidence(pack, vanilla, carriers)
    carriers.update(evidence)
    cache.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=cache.name + ".", suffix=".sqlite", dir=cache.parent); os.close(fd)
    counts, examples, details, regions = Counter(), defaultdict(list), {}, []
    chunks = carrier_cells = 0
    db = None
    try:
        db = sqlite3.connect(temporary); db.execute("CREATE TABLE positions(state_id TEXT,x INTEGER,y INTEGER,z INTEGER)"); db.execute("CREATE INDEX positions_state ON positions(state_id)")
        with zipfile.ZipFile(source) as archive:
            dimension = dimension.strip("/")
            prefix = (dimension + "/" if dimension else "") + "region/"
            entries = sorted((info for info in archive.infolist() if info.filename.startswith(prefix) and info.filename.endswith(".mca")), key=lambda info: info.filename)
            for number, info in enumerate(entries, 1):
                print(f"source-carrier-index {number}/{len(entries)} {info.filename}", flush=True)
                regions.append(info.filename); region = RegionFile(archive.read(info))
                for stored in region.chunks():
                    root_nbt = compound(stored.nbt().root); cx = int(root_nbt.get("xPos", Tag(TAG_INT, stored.x)).value); cz = int(root_nbt.get("zPos", Tag(TAG_INT, stored.z)).value); chunks += 1
                    sections = root_nbt.get("sections") or root_nbt.get("Sections")
                    if not sections or sections.type != TAG_LIST: continue
                    for section in sections.value:
                        sec = compound(section); sy = int(sec["Y"].value); states = child(section, "block_states", TAG_COMPOUND)
                        if not states: continue
                        palette_tag = child(states, "palette", TAG_LIST)
                        if not palette_tag or not palette_tag.value: continue
                        palette = [_state(item) for item in palette_tag.value]
                        selected = {i for i, (_, descriptor) in enumerate(palette) if descriptor["id"] in carriers}
                        if not selected: continue
                        data = child(states, "data", TAG_LONG_ARRAY); indices = unpack_palette_indices(data.value if data else [], len(palette_tag.value))
                        rows = []
                        for offset, index in enumerate(indices):
                            key, descriptor = palette[index]
                            if descriptor["id"] not in carriers: continue
                            counts[key] += 1; carrier_cells += 1; details[key] = descriptor
                            x, y, z = cx * 16 + (offset & 15), sy * 16 + (offset >> 8), cz * 16 + ((offset >> 4) & 15)
                            if len(examples[key]) < 3: examples[key].append([x, y, z])
                            rows.append((key, x, y, z))
                        db.executemany("INSERT INTO positions VALUES(?,?,?,?)", rows)
        db.commit(); db.close(); db = None
        # CPython may retain temporary sqlite cursors until collection; Windows
        # refuses an atomic replacement while that handle is still live.
        gc.collect()
        os.replace(temporary, cache)
        states = [{"key": key, "state_id": key, "source": details[key], "count": counts[key], "examples": examples[key], "carrier_evidence": evidence.get(details[key]["id"], [])} for key in sorted(counts)]
        index = {"format": FORMAT, "regions": regions, "states": states, "carrier_cells": carrier_cells, "chunks": chunks,
                 "source_sha256": inputs["source_sha256"], "pack_sha256": inputs["pack_sha256"], "vanilla_sha256": inputs["vanilla_sha256"], "database": str(cache)}
        body = {"inputs": inputs, "database_sha256": sha256(cache), "index": index}
        body_sha256 = hashlib.sha256(json.dumps(body, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
        body["body_sha256"] = body_sha256
        with open(str(cache) + ".json.gz", "wb") as raw:
            with gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as zipped:
                zipped.write(json.dumps(body, sort_keys=True, separators=(",", ":")).encode("utf-8"))
        return index
    finally:
        if db is not None:
            try: db.close()
            except sqlite3.Error: pass
        gc.collect()
        if os.path.exists(temporary): os.unlink(temporary)

def positions(index, state_ids):
    """Fetch only requested state coordinates as {position_tuple: state_id}."""
    wanted = list(state_ids)
    if not wanted: return {}
    with sqlite3.connect(index["database"]) as db:
        marks = ",".join("?" for _ in wanted)
        return {(x, y, z): state_id for state_id, x, y, z in db.execute(f"SELECT state_id,x,y,z FROM positions WHERE state_id IN ({marks})", wanted)}
