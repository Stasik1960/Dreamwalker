#!/usr/bin/env python3
"""Read-only occurrence report for the bounded manual-review family batch.

This is deliberately a matcher, not a converter or a source-pack interpreter.
Only vanilla source states named by the review manifest can produce a count.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import zipfile
from collections import OrderedDict, defaultdict
from pathlib import Path
from typing import Any

from world_io import TAG_COMPOUND, TAG_INT, TAG_LIST, TAG_STRING, RegionFile, Tag, compound, section_blocks

EXPECTED_SHA256 = "4353737d536677469d3b895e3515496ab64fab7b224e43428eb96e8c09724a51"
EXPECTED_PACK_SHA256 = "0f2c3d64a1d60734ae0786d128b522ea6bbd175f26f5d164bed5522c46898308"
SCANNER_VERSION = "4"
DEFAULT_SOURCEPACK = Path(__file__).resolve().parents[1] / 'reference-inputs/source-resource-pack.zip'
DEFAULT_VANILLA = Path.home() / '.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'

def _model_id(value: str) -> str:
    value = value.split(":", 1)[-1]
    return value.removeprefix("block/")

def _refs(value):
    if isinstance(value, dict):
        if "model" in value: return [(_model_id(str(value["model"])), bool(value.get("weight")))]
        return sum((_refs(x) for x in value.values()), [])
    if isinstance(value, list):
        refs = sum((_refs(x) for x in value), [])
        alternatives = len({model for model, _ in refs}) > 1
        return [(model, weighted or alternatives) for model, weighted in refs]
    return []

def _load_blockstates(sourcepack: Path | None, vanilla_jar: Path | None):
    """Original pack overrides vanilla; return id -> raw blockstate JSON."""
    result = {}
    for path in (vanilla_jar, sourcepack):
        if path is None or not path.is_file(): continue
        with zipfile.ZipFile(path) as archive:
            for name in archive.namelist():
                marker = "assets/minecraft/blockstates/"
                if name.startswith(marker) and name.endswith(".json"):
                    result["minecraft:" + name[len(marker):-5]] = json.loads(archive.read(name))
    return result

def _active_models(raw, props):
    def condition(where):
        if 'OR' in where: return any(condition(x) for x in where['OR'])
        if 'AND' in where: return all(condition(x) for x in where['AND'])
        return all(props.get(k) in str(v).split('|') for k, v in where.items())
    refs = []
    for key, value in raw.get("variants", {}).items():
        conditions = dict(x.split("=", 1) for x in key.split(",") if "=" in x)
        if condition(conditions): refs += _refs(value)
    for entry in raw.get("multipart", []):
        when = entry.get("when", {})
        if condition(when): refs += _refs(entry.get("apply"))
    return refs

def _record_models(record):
    return {_model_id(str(c.get("app", {}).get("model"))) for c in record.get("components", []) if isinstance(c, dict) and c.get("app", {}).get("model")}


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for piece in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(piece)
    return digest.hexdigest()


def _body_hash(value):
    """Detect accidental edits/corruption, not a signature against hostile edits."""
    body = {k: v for k, v in value.items() if k != 'body_sha256'}
    return hashlib.sha256(json.dumps(body, sort_keys=True, separators=(',', ':')).encode('utf-8')).hexdigest()


def _write_sparse(path, data):
    data['body_sha256'] = _body_hash(data)
    temporary = path.with_suffix('.gz.tmp')
    with gzip.open(temporary, 'wt', encoding='utf-8') as stream: json.dump(data, stream)
    temporary.replace(path)

def config_hash(records, sourcepack: Path, vanilla_jar: Path) -> str:
    """Public cache key: rules plus exact visual resolver inputs/version."""
    for label, path in (("source pack", sourcepack), ("vanilla jar", vanilla_jar)):
        if not path.is_file(): raise ValueError(f"required {label} is missing: {path}")
    pack_hash = _sha256(sourcepack)
    if pack_hash != EXPECTED_PACK_SHA256: raise ValueError(f"unexpected source-pack SHA-256: {pack_hash}")
    value = {"scannerVersion": SCANNER_VERSION, "records": records, "sourcepackSha256": pack_hash, "vanillaJarSha256": _sha256(vanilla_jar)}
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")).hexdigest()


def _dimension(name: str) -> str:
    name = name.split("/", 1)[1] if "/" in name and not name.startswith(("region/", "DIM-1/", "DIM1/", "dimensions/")) else name
    if name.startswith("region/"): return "minecraft:overworld"
    if name.startswith("DIM-1/"): return "minecraft:the_nether"
    if name.startswith("DIM1/"): return "minecraft:the_end"
    prefix = name.rsplit("/region/", 1)[0]
    if "/dimensions/" in "/" + name:
        bits = prefix.split("dimensions/", 1)[1].split("/")
        if len(bits) >= 2: return bits[0] + ":" + "/".join(bits[1:])
    return prefix or "unknown"


def _state(tag: Tag) -> tuple[str, tuple[tuple[str, str], ...]]:
    value = compound(tag)
    props = compound(value["Properties"]).items() if "Properties" in value else ()
    return str(value["Name"].value), tuple(sorted((str(k), str(v.value)) for k, v in props))


def _spec(value: dict[str, Any]) -> tuple[tuple[int, int, int], tuple[str, tuple[tuple[str, str], ...]]]:
    raw = value.get("offset", [0, 0, 0])
    if not isinstance(raw, list) or len(raw) != 3 or not all(isinstance(v, int) for v in raw):
        raise ValueError("source component offset must be three integers")
    name = str(value.get("id", value.get("name", "")))
    if ":" not in name: name = "minecraft:" + name
    props = value.get("properties", {})
    if not isinstance(props, dict): raise ValueError("source component properties must be an object")
    return tuple(raw), (name, tuple(sorted((str(k), str(v)) for k, v in props.items())))


def compile_record_patterns(record: dict[str, Any], sourcepack: Path | None = None,
                            vanilla_jar: Path | None = None, known_props: dict[str, set[str]] | None = None) -> list[list[tuple[tuple[int, int, int], tuple[str, tuple[tuple[str, str], ...]]]]]:
    """Compile original legacy rules; component models are visual evidence only."""
    result, target = [], record["logical_id"].split(":")[-1]
    # The accepted POC carries the direct, authoritative contract.  It is
    # exact by design and does not use legacy/provenance component fields.
    for state in record.get("source_patterns_v2", {}).values():
        for pattern in state.get("migration_source_pattern", []) if isinstance(state, dict) else ():
            parts = pattern.get("components", []) if isinstance(pattern, dict) else []
            if parts: result.append([_spec(value) for value in parts if isinstance(value, dict)])
    if result: return result
    for rule in record.get("legacy_rules", []):
        if not isinstance(rule, dict) or str(rule.get("target", {}).get("id", "")).split(":")[-1] != target: continue
        values = [rule.get("source")] + list(rule.get("members", []))
        if any(not isinstance(value, dict) or str(value.get("id", "")).startswith("asset_") for value in values): continue
        parts = []
        for value in values:
            item = dict(value); props = dict(item.get("properties", {})); props.pop("assembled", None)
            if str(item.get("id", "")).endswith("_wool"): props.pop("facing", None)
            if known_props is not None:
                ident = str(item.get("id", "")); ident = ident if ":" in ident else "minecraft:" + ident
                # A property absent from every original-world palette entry of
                # this vanilla id is legacy synthetic, never a match criterion.
                props = {k: v for k, v in props.items() if k in known_props.get(ident, set())}
            item["properties"] = props; parts.append(_spec(item))
        if parts: result.append(parts)
    return result


def _load_records(manifest: Path, batch: int) -> list[dict[str, Any]]:
    raw = json.loads(manifest.read_text(encoding="utf-8"))
    entries = raw.get("records", raw.get("families", raw if isinstance(raw, list) else []))
    if not isinstance(entries, list): raise ValueError("manual-family manifest has no records list")
    chosen = [x for x in entries if isinstance(x, dict) and (x.get("batch") == batch or x.get("architecture_status") == "POC_ACCEPTED")]
    if not chosen: raise ValueError(f"manual-family manifest has no batch {batch} records")
    for x in chosen:
        if not isinstance(x.get("review_id"), str) or not isinstance(x.get("logical_id"), str):
            raise ValueError("every review record needs review_id and logical_id")
    return chosen


def _context(get, dim: str, anchor: tuple[int, int, int], radius: int = 2) -> list[dict[str, Any]]:
    cells = []
    for y in range(anchor[1] - radius, anchor[1] + radius + 1):
        for z in range(anchor[2] - radius, anchor[2] + radius + 1):
            for x in range(anchor[0] - radius, anchor[0] + radius + 1):
                value = get(dim, x, y, z)
                if value is not None:
                    cells.append({"relative": [x-anchor[0], y-anchor[1], z-anchor[2]],
                                  "state": {"id": value[0], "properties": dict(value[1])}})
    return cells


def scan(records: list[dict[str, Any]], output_path: Path | str, *, source: Path | str,
         sourcepack: Path | None = None, vanilla_jar: Path | None = None) -> dict[str, Any]:
    """Scan one verified source ZIP and atomically write a JSON evidence report."""
    source, output_path = Path(source).resolve(), Path(output_path).resolve()
    if output_path == source or output_path.suffix != '.json': raise ValueError('Output must be a separate JSON report')
    source_hash = _sha256(source)
    if source_hash != EXPECTED_SHA256: raise ValueError(f"unexpected source SHA-256: {source_hash}")
    if sourcepack is None or vanilla_jar is None: raise ValueError("sourcepack and vanilla_jar are required")
    config_digest = config_hash(records, sourcepack, vanilla_jar)
    blockstates = _load_blockstates(sourcepack, vanilla_jar)
    if output_path.is_file():
        try:
            cached = json.loads(output_path.read_text(encoding="utf-8"))
            if (cached.get("source", {}).get("sha256") == source_hash
                    and cached.get("config_sha256") == config_digest
                    and cached.get('body_sha256') == _body_hash(cached)):
                return cached["reviews"]
        except (OSError, ValueError, KeyError, TypeError):
            pass
    compiled = {r["review_id"]: compile_record_patterns(r) for r in records}
    record_models = {r["review_id"]: _record_models(r) for r in records}
    wanted_names = {state[0] for patterns in compiled.values() for pattern in patterns for _, state in pattern}
    # This reverse lookup is ONLY visual provenance, never object discovery.
    all_models = set().union(*record_models.values())
    model_carriers = {name: {model for model, _ in _refs(raw)} for name, raw in blockstates.items()}
    wanted_names.update(name for name, refs in model_carriers.items() if refs & all_models)
    candidates: dict[tuple[str, tuple[str, tuple[tuple[str,str], ...]]], list[tuple[int,int,int]]] = defaultdict(list)
    known_props: dict[str, set[str]] = defaultdict(set)
    region_names: dict[tuple[str,int,int], str] = {}
    sparse_path = output_path.with_name('source-candidates.json.gz')
    sparse_key = hashlib.sha256(json.dumps([3, source_hash, sorted(wanted_names)]).encode()).hexdigest()
    sparse = None
    if sparse_path.exists():
        with gzip.open(sparse_path, 'rt', encoding='utf-8') as stream: sparse = json.load(stream)
        if sparse.get('key') != sparse_key: sparse = None
        elif sparse.get('body_sha256') != _body_hash(sparse):
            raise ValueError('Sparse source index integrity check failed; remove only the disposable source-candidates.json.gz and rescan')
    if sparse:
        for dim, name, props, positions in sparse['candidates']:
            candidates[(dim, (name, tuple(tuple(x) for x in props)))] = [tuple(p) for p in positions]
        region_names.update({tuple(k): v for k, v in sparse['regions']})
        known_props.update({k: set(v) for k, v in sparse['known_props'].items()})
        print('manual-review: reused source-bound, checksum-checked sparse index', flush=True)
    with zipfile.ZipFile(source) as archive:
        names = sorted(n for n in archive.namelist() if n.endswith(".mca") and "/region/r." in "/" + n)
        for number, name in enumerate([] if sparse else names, 1):
            dim, region = _dimension(name), RegionFile(archive.read(name))
            for stored in region.chunks():
                root = compound(stored.nbt().root)
                cx, cz = int(root.get("xPos", Tag(TAG_INT, stored.x)).value), int(root.get("zPos", Tag(TAG_INT, stored.z)).value)
                region_names[(dim, cx >> 5, cz >> 5)] = name
                sections = root.get("sections") or root.get("Sections")
                for section in sections.value if sections and sections.type == TAG_LIST else ():
                    fields = compound(section)
                    block_states = fields.get("block_states")
                    palette_tag = compound(block_states).get("palette") if block_states else None
                    if palette_tag is None or palette_tag.type != TAG_LIST: continue
                    matching = {}
                    for i, entry in enumerate(palette_tag.value):
                        state = _state(entry)
                        if state[0] in wanted_names:
                            known_props[state[0]].update(key for key, _ in state[1]); matching[i] = state
                    if not matching: continue  # palette filter: do not inspect 4096 positions.
                    unpacked = section_blocks(section)
                    if unpacked is None: continue
                    _, indices = unpacked
                    sy = int(fields["Y"].value)
                    for cell, index in enumerate(indices):
                        state = matching.get(index)
                        if state is not None:
                            candidates[(dim, state)].append((cx*16+(cell&15), sy*16+(cell>>8), cz*16+((cell>>4)&15)))
            if number % 32 == 0:
                print(f"manual-review: scanned {number}/{len(names)} regions", flush=True)
        if not sparse:
            sparse_path.parent.mkdir(parents=True, exist_ok=True)
            sparse_data = {'key': sparse_key, 'candidates': [[dim, state[0], state[1], pos] for (dim, state), pos in candidates.items()],
                           'regions': list(region_names.items()), 'known_props': {k: sorted(v) for k, v in known_props.items()}}
            _write_sparse(sparse_path, sparse_data)
            print('manual-review: sparse source index saved', flush=True)
        compiled = {r["review_id"]: compile_record_patterns(r, known_props=known_props) for r in records}
        observed_states = {state for dim, state in candidates}
        for record in records:
            # A one-model candidate may have asset_* legacy provenance. Resolve
            # ONLY known visual applications of observed original states.
            if (len(record.get('components', [])) == 1 and not record.get('source_patterns_v2')
                    and not compiled[record['review_id']]):
                wanted = record_models[record['review_id']]
                compiled[record['review_id']] = [[((0, 0, 0), state)] for state in observed_states
                    if any(model in wanted for model, _ in _active_models(blockstates.get(state[0], {}), dict(state[1])))]
        positions = {key: set(values) for key, values in candidates.items()}
        cache: OrderedDict[tuple[str,int,int], dict[int, tuple[list[Tag], list[int]]]] = OrderedDict()
        region_cache = OrderedDict()
        def get(dim, x, y, z):
            key = (dim, x >> 4, z >> 4)
            if key not in cache:
                sections = {}; name = region_names.get((dim, x >> 9, z >> 9))
                if name:
                    if name not in region_cache:
                        region_cache[name] = RegionFile(archive.read(name))
                        if len(region_cache) > 4: region_cache.popitem(last=False)
                    region_cache.move_to_end(name)
                    region = region_cache[name]
                    chunk = region.get_chunk((x >> 4) & 31, (z >> 4) & 31)
                    if chunk:
                        root = compound(chunk.nbt().root); listed = root.get("sections") or root.get("Sections")
                        for sec in listed.value if listed and listed.type == TAG_LIST else ():
                            unpacked = section_blocks(sec)
                            if unpacked:
                                sections[int(compound(sec)["Y"].value)] = unpacked
                cache[key] = sections
                if len(cache) > 64: cache.popitem(last=False)
            else: cache.move_to_end(key)
            unpacked = cache[key].get(y >> 4)
            if unpacked is None: return None
            palette, indices = unpacked
            cell = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
            return _state(palette[indices[cell]])
        report = {}
        for record in records:
            patterns = compiled[record["review_id"]]; matches = []
            for pattern in patterns:
                first_offset, first = pattern[0]
                for dim, state in list(candidates):
                    if state != first: continue
                    for pos in candidates[(dim, state)]:
                        anchor = tuple(pos[i] - first_offset[i] for i in range(3))
                        active = _active_models(blockstates.get(state[0], {}), dict(state[1]))
                        visual_ok = not record_models[record["review_id"]] or any(model in record_models[record["review_id"]] for model, _ in active)
                        if visual_ok and all(tuple(anchor[i]+offset[i] for i in range(3)) in positions.get((dim, expected), set()) for offset, expected in pattern):
                            matches.append((dim, anchor, pattern))
            unique = {(dim, anchor): pattern for dim, anchor, pattern in matches}
            examples = []
            for (dim, anchor), pattern in sorted(unique.items())[:3]:
                examples.append({"dimension": dim, "anchor": list(anchor), "source_blocks": [
                    {"relative": list(offset), "state": {"id": state[0], "properties": dict(state[1])}} for offset, state in pattern],
                    "neighborhood_radius": 2, "neighborhood": _context(get, dim, anchor)})
            weighted = any(weight for pattern in patterns for _, state in pattern for _, weight in _active_models(blockstates.get(state[0], {}), dict(state[1])))
            possible_carriers = [name for name, refs in model_carriers.items() if refs & record_models[record['review_id']]]
            visual_unverifiable = bool(record_models[record['review_id']]) and not possible_carriers
            status = "visual_unverifiable" if visual_unverifiable else ("not_mapped" if not patterns and not possible_carriers else ("ambiguous_candidate_count" if weighted or record.get("ambiguous") or record.get("weighted_alternatives") else "matched"))
            report[record["review_id"]] = {"logical_id": record["logical_id"], "status": status,
                "candidate_count": None if status in ('not_mapped', 'visual_unverifiable') else len(unique), "examples": examples,
                "visual_carriers": possible_carriers,
                "legacy_rules": record.get("legacy_rules", []), "component_models": record.get("components", [])}
    if _sha256(source) != source_hash: raise ValueError('Source archive changed during read-only scan')
    payload = {"format": "bloodborne-manual-review-occurrences-v1", "scanner_version": SCANNER_VERSION, "source": {"path": str(source), "sha256": source_hash, "sha256_after": source_hash}, "sourcepack_sha256": _sha256(sourcepack), "config_sha256": config_digest, "reviews": report}
    payload['body_sha256'] = _body_hash(payload)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = output_path.with_suffix(output_path.suffix + ".tmp")
    temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    temporary.replace(output_path)
    return report


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=root / "docs/manual-families.json")
    parser.add_argument("--batch", type=int, default=1); parser.add_argument("--output", type=Path, default=root / "build/manual-review/occurrences.json")
    parser.add_argument("--source", type=Path, default=root / "reference-inputs/source-world.zip")
    parser.add_argument("--sourcepack", type=Path, default=DEFAULT_SOURCEPACK)
    parser.add_argument("--vanilla-jar", type=Path, default=DEFAULT_VANILLA)
    args = parser.parse_args(); scan(_load_records(args.manifest, args.batch), args.output, source=args.source, sourcepack=args.sourcepack, vanilla_jar=args.vanilla_jar)


if __name__ == "__main__": main()
