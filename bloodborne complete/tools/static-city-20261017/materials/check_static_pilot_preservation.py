#!/usr/bin/env python3
"""Bounded read-only preservation check for the portable static pilot.

This is an adaptation of audit_city_static_short_preservation.py.  It reads
only the chunks touched by the first 128 planned components and the owner
ledger shards directly associated with those chunks/owners.  It never starts
Java and never writes either world.  Game6 OwnerDataV1 payloads are decoded to
their logical typed ``owners`` list in memory before comparison.
"""
from __future__ import annotations

from pathlib import Path
import argparse
import collections
import contextlib
import copy
import gzip
import hashlib
import itertools
import json
import os
import sys
import traceback
import uuid

ROOT = Path(__file__).resolve().parents[2]
PROJECT = ROOT / "project"
OLD_BUILD = PROJECT / "build" / "v1274-city-completion-20261015"
TOOLS = PROJECT / "tools"
sys.path[:0] = [str(OLD_BUILD), str(TOOLS)]

import audit_empty_flat_preservation as prior  # noqa: E402
from world_io import Tag, compound, decode_nbt  # noqa: E402

CP = "dw-static-architecture-checkpoint.json"
RESULT = "dw-static-architecture-result.json"
LEGACY = ("dw-conversion-checkpoint.json", "dw-conversion-result.json")
PACKED_KEY = "OwnerDataV1"
LOGICAL_KEY = "owners"
PILOT_LIMIT = 128


def need(value, message):
    if not value:
        raise AssertionError(message)


def ordinary(path: Path) -> Path:
    path = Path(path).resolve()
    need(not path.is_symlink(), f"Symlink input is not allowed: {path}")
    return path


def read(path: Path):
    return json.loads(ordinary(path).read_text(encoding="utf-8-sig"))


def sha(path: Path) -> str:
    digest = hashlib.sha256()
    with ordinary(path).open("rb") as stream:
        while block := stream.read(1024 * 1024):
            digest.update(block)
    return digest.hexdigest()


def canon(value) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def typed(tag):
    return prior.typed(tag) if tag is not None else None


def uid(tag) -> str:
    value = compound(tag)["uuid"]
    need(value.type == 11 and len(value.value) == 4, "Owner UUID must be four typed INTs")
    raw = b"".join((part & 0xFFFFFFFF).to_bytes(4, "big") for part in value.value)
    return str(uuid.UUID(bytes=raw))


def unpack_owner_data(tag: Tag) -> Tag:
    need(tag.type == 7, "OwnerDataV1 must be a typed BYTE_ARRAY")
    nbt = decode_nbt(bytes(tag.value))
    root = compound(nbt.root)
    need(set(root) == {LOGICAL_KEY}, "OwnerDataV1 envelope must contain only owners")
    owners = root[LOGICAL_KEY]
    need(owners.type == 9 and owners.list_type == 10, "OwnerDataV1 owners must be LIST<COMPOUND>")
    return owners


def logical_root(root):
    """Return a detached graph with packed BE ownership represented canonically."""
    if root is None:
        return None
    result = copy.deepcopy(root)
    for block_entity in prior.bes(result).values():
        fields = compound(block_entity)
        packed = fields.get(PACKED_KEY)
        if packed is None:
            continue
        need(LOGICAL_KEY not in fields, "BE contains both owners and OwnerDataV1")
        fields[LOGICAL_KEY] = unpack_owner_data(packed)
        del fields[PACKED_KEY]
    return result


class Decoder:
    decode_nbt = staticmethod(decode_nbt)


class Reader:
    """Direct Anvil-slot reader retaining at most one logical decoded chunk."""
    def __init__(self, world: Path):
        self.world = ordinary(world)
        self.hashes = {}
        self.clear()

    def track(self, path: Path) -> Path:
        path = ordinary(path)
        if path not in self.hashes:
            self.hashes[path] = sha(path)
        return path

    def root(self, cx: int, cz: int):
        if self.key == (cx, cz):
            return self.value
        self.clear()
        path = self.world / "region" / f"r.{cx >> 5}.{cz >> 5}.mca"
        root = None
        if path.exists():
            self.track(path)
            with path.open("rb") as stream:
                header = stream.read(8192)
                need(len(header) == 8192, "Short region header")
                raw = prior.payload(stream, path, header, (cz & 31) * 32 + (cx & 31),
                                    cx >> 5, cz >> 5, self.hashes)
                root = prior.decode(raw, Decoder)
            root = logical_root(root)
        self.key = (cx, cz)
        self.value = root
        self.sections = prior.sections(root or {})
        self.entities = prior.bes(root or {})
        return root

    def block(self, position):
        x, y, z = position
        self.root(x >> 4, z >> 4)
        sy = y >> 4
        if sy not in self.keys:
            self.keys[sy] = prior.block_keys(self.sections.get(sy))
        index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
        return self.keys[sy][index], self.entities.get(tuple(position))

    def counts(self, cx: int, cz: int):
        root = self.root(cx, cz) or {}
        owners = 0
        for be in prior.bes(root).values():
            value = compound(be).get(LOGICAL_KEY)
            if value is not None:
                need(value.type == 9 and value.list_type == 10, "Logical BE owners must be LIST<COMPOUND>")
                owners += len(value.value)
        return {"blockEntities": len(prior.bes(root)), "blockEntityOwners": owners}

    def clear(self):
        self.key = None
        self.value = None
        self.sections = {}
        self.entities = {}
        self.keys = {}

    def recheck(self):
        for path, digest in self.hashes.items():
            need(sha(path) == digest, f"Direct-read input changed: {path}")


def recipe_state(recipe: int, recipe_count: int) -> str:
    need(type(recipe) is int and 0 <= recipe < recipe_count, "Recipe ID outside generated catalog")
    return f"bloodborne_dw:static_architecture_{recipe // 128}[part={recipe % 128}]"


def load_rows(stream: Path, manifest: dict):
    need(sha(stream) == manifest["runtimeFiles"][stream.name], "Generated component stream changed")
    with gzip.open(stream, "rt", encoding="utf-8") as source:
        rows = [json.loads(line) for line in itertools.islice(source, PILOT_LIMIT)]
    need(len(rows) == PILOT_LIMIT and len({r["componentId"] for r in rows}) == PILOT_LIMIT,
         "Exact first 128 distinct components required")
    return rows


def load_skips(world: Path, operation_id: str):
    result = {}
    path = world / "dw-static-architecture-skips.jsonl"
    if not path.exists():
        return result
    with ordinary(path).open(encoding="utf-8") as source:
        for raw in source:
            row = json.loads(raw)
            if row.get("operationId") != operation_id or row.get("phase") != 3:
                continue
            line = row.get("line")
            if type(line) is not int or not 0 <= line < PILOT_LIMIT:
                continue
            need(row.get("writes") == row.get("rollbackSnapshots") == 0,
                 "SKIP claims writes or rollback snapshots")
            component = row["componentId"]
            need(line not in result or result[line] == component, "Conflicting duplicate skip")
            result[line] = component
    return result


def classify(rows, skipped, before: Reader, after: Reader, recipe_count: int):
    expected = {}
    removed = set()
    affected = set()
    counts = collections.Counter()
    selected_owner_roots = {}
    for index, row in enumerate(rows):
        need(row["dimension"] == "minecraft:overworld", "Only overworld static components are covered")
        placements = {tuple(cell["position"]): recipe_state(cell["recipe"], recipe_count)
                      for cell in row["cells"]}
        need(len(placements) == len(row["cells"]), "Repeated planned static cell")
        affected.update((x >> 4, z >> 4) for x, _y, z in placements)
        a = [before.block(position) for position in placements]
        b = [after.block(position) for position in placements]
        am = [state == wanted and be is None for (state, be), wanted in zip(a, placements.values())]
        bm = [state == wanted and be is None for (state, be), wanted in zip(b, placements.values())]
        if index in skipped:
            need(skipped[index] == row["componentId"], "Durable skip names a different component")
            need(not all(bm), "Skipped component appears as a successful complete replacement")
            counts["architectureSkipped"] += 1
            continue
        need(all(bm), "Non-skipped component is not exact static state/no-BE")
        if all(am):
            counts["alreadyPresent"] += 1
            continue
        need(not any(am), "Partially static baseline component")
        need(all(not state.startswith("bloodborne_dw:static_architecture_") for state, _be in a),
             "Mismatched static state already present in baseline")
        need(not (expected.keys() & placements.keys()), "Committed closed components overlap")
        expected.update(placements)
        counts["committed"] += 1
        for owner in row["owners"]:
            identity = owner["instanceUuid"]
            need(identity not in removed, "Repeated selected owner UUID")
            root = tuple(owner["position"])
            need(root in placements, "Selected owner root is outside its committed component cells")
            removed.add(identity)
            selected_owner_roots[identity] = root
            affected.add((root[0] >> 4, root[2] >> 4))
    return expected, removed, affected, counts, selected_owner_roots


def verify_chunk(before_root, after_root, cx, cz, expected):
    sections = prior.sections(after_root or {})
    entities = prior.bes(after_root or {})
    local = [(p, wanted) for p, wanted in expected.items() if p[0] >> 4 == cx and p[2] >> 4 == cz]
    decoded = {}
    for position, wanted in local:
        sy = position[1] >> 4
        if sy not in decoded:
            decoded[sy] = prior.block_keys(sections.get(sy))
        index = ((position[1] & 15) << 8) | ((position[2] & 15) << 4) | (position[0] & 15)
        need(decoded[sy][index] == wanted, f"Wrong static recipe/part at {position}")
        need(position not in entities, f"Static cell retained/created BlockEntity at {position}")
    coverage = {(cx, cz): {"serializedBlendingDataPresent": True}} \
        if before_root and "blending_data" in before_root else None
    return prior.compare_chunk(before_root, after_root, cx, cz, expected, coverage)


def shard_path(world: Path, coord):
    return world / "data" / "bloodborne_dw_owner_shards" / f"owners-{coord[0]}.{coord[1]}.nbt"


def shard_input(path: Path):
    path = ordinary(path)
    need(path.stat().st_size <= 64 * 1024 ** 2, "Compressed shard exceeds bound")
    raw = gzip.decompress(path.read_bytes())
    need(len(raw) <= 128 * 1024 ** 2, "Uncompressed shard exceeds bound")
    return decode_nbt(raw)


def decoded_shard(path, coord, removed, allowed, baseline):
    if path is None:
        return {"cells": {}, "members": {}, "extra": {}}, set(), {"owners": 0, "cells": 0, "memberships": 0}
    root = compound(shard_input(path).root)
    need(root["Schema"] == Tag(3, 1) and root["Dictionary"] == Tag(1, 1)
         and root["ChunkX"] == Tag(3, coord[0]) and root["ChunkZ"] == Tag(3, coord[1]),
         "Exact typed shard header differs")
    owners = root["ownerTable"].value
    datas = root["dataTable"].value
    shapes = root["shapeTable"].value
    ids = [uid(owner) for owner in owners]
    need(len(ids) == len(set(ids)), "Duplicate owner dictionary UUID")
    seen = set(ids) & removed
    need(baseline or not seen, f"Removed owner remains in candidate shard {coord}")
    out = {"cells": {}, "members": {}}
    for tag in root["cells"].value:
        row = compound(tag)
        position = tuple(row["pos"].value)
        need(row["pos"].type == 11 and len(position) == 3 and (position[0] >> 4, position[2] >> 4) == coord,
             "Shard cell belongs to another chunk")
        values = []
        for ref in row["refs"].value:
            need(ref.type == 11 and len(ref.value) == 3, "Typed ledger dictionary reference required")
            oi, di, si = ref.value
            need(0 <= oi < len(owners) and 0 <= di < len(datas) and 0 <= si < len(shapes),
                 "Ledger dictionary reference outside bounds")
            if ids[oi] in removed:
                need(baseline and position in allowed, "Selected contribution outside committed footprint")
                continue
            values.append([typed(owners[oi]), typed(datas[di]), typed(shapes[si])])
        if values:
            need(position not in out["cells"], "Repeated shard cell")
            out["cells"][position] = sorted(values, key=canon)
    membership_count = 0
    seen_members = set()
    for tag in root["ownerMembership"].value:
        row = compound(tag)
        oi = row["ownerRef"].value
        need(row["ownerRef"].type == 3 and 0 <= oi < len(owners), "Typed membership owner ref required")
        identity = ids[oi]
        need(identity not in seen_members, "Repeated membership owner")
        seen_members.add(identity)
        owner_root = tuple(compound(owners[oi])["root"].value)
        need((owner_root[0] >> 4, owner_root[2] >> 4) == coord, "Membership belongs to another root chunk")
        members = []
        for item in row["members"].value:
            value = compound(item)
            si = value["shapeRef"].value
            need(value["offset"].type == 11 and len(value["offset"].value) == 3
                 and value["shapeRef"].type == 3 and 0 <= si < len(shapes), "Typed membership entry required")
            position = tuple(owner_root[j] + value["offset"].value[j] for j in range(3))
            if identity in removed:
                need(baseline and position in allowed, "Removed membership outside committed writes")
            members.append([typed(value["offset"]), typed(shapes[si])])
        if identity not in removed:
            out["members"][identity] = [typed(owners[oi]), sorted(members, key=canon)]
            membership_count += 1
    known = {"Schema", "Dictionary", "ChunkX", "ChunkZ", "ownerTable", "dataTable", "shapeTable",
             "cells", "ownerMembership"}
    out["extra"] = {key: typed(value) for key, value in root.items()
                    if key not in known and prior.nonempty(value)}
    actual = {"owners": len(owners), "cells": len(root["cells"].value), "memberships": membership_count}
    return out, seen, actual


def ledger_meta(world: Path):
    path = ordinary(world / "data" / "bloodborne_dw_composite_owners.dat")
    root = compound(decode_nbt(path.read_bytes(), compressed="gzip").root)
    data = compound(root["data"])
    need(data["OwnerShardSchema"] == Tag(3, 1) and not data["cells"].value,
         "Only shard schema1 with empty legacy cells is covered")
    return path, data


def compare_ledger(before, after, affected, removed, allowed, output):
    old_meta_path, old_meta = ledger_meta(before)
    new_meta_path, new_meta = ledger_meta(after)
    for key in old_meta.keys() | new_meta.keys():
        if key != "OwnerShardChunks":
            need(prior.protected(old_meta.get(key)) == prior.protected(new_meta.get(key)),
                 f"Global owner ledger metadata changed: {key}")
    counts = collections.Counter()
    old_seen = set()
    actual_before = collections.Counter()
    actual_after = collections.Counter()
    evidence = []
    for coord in sorted(affected):
        a = shard_path(before, coord)
        b = shard_path(after, coord)
        a = ordinary(a) if a.exists() else None
        b = ordinary(b) if b.exists() else None
        ah = sha(a) if a else None
        bh = sha(b) if b else None
        decoded_a, seen, ac = decoded_shard(a, coord, removed, allowed, True)
        decoded_b, _candidate_seen, bc = decoded_shard(b, coord, removed, allowed, False)
        old_seen.update(seen)
        need(decoded_a == decoded_b, f"Unselected typed owner ledger changed at {coord}")
        counts["byteExactShards" if ah == bh else "changedShardsTypedVerified"] += 1
        actual_before.update(ac)
        actual_after.update(bc)
        need((a is None or sha(a) == ah) and (b is None or sha(b) == bh), "Shard changed during audit")
        evidence.append({"chunk": list(coord), "beforeSha256": ah, "afterSha256": bh,
                         "beforeCounts": ac, "afterCounts": bc})
    need(old_seen == removed, "Not every committed selected owner was present in affected baseline shards")
    output.write_text("\n".join(canon(row) for row in evidence) + "\n", encoding="utf-8")
    return dict(counts), dict(actual_before), dict(actual_after), {
        "before": {"path": str(old_meta_path), "sha256": sha(old_meta_path)},
        "after": {"path": str(new_meta_path), "sha256": sha(new_meta_path)},
    }


def fully_drained(document):
    lifecycle = document.get("chunkLifecycle", {})
    totals = lifecycle.get("totals", {})
    keys = {"loadedWorldChunks", "holders", "readableHolders", "pendingHolders",
            "chunksToUnload", "unloadQueue"}
    return (set(totals) == keys and all(type(totals[key]) is int and totals[key] == 0 for key in keys)
            and lifecycle.get("heldDirectTicketChunks") == 0
            and lifecycle.get("loadInFlight") is False)


def verify_controls(candidate, plan, prepared, evidence):
    checkpoint_path = ordinary(candidate / CP)
    result_path = ordinary(candidate / RESULT)
    checkpoint = read(checkpoint_path)
    result = read(result_path)
    need(evidence["status"] == "PASS_BOUNDED_EXPERIMENTAL_STATIC_PILOT_GAMEPLAY_UNVERIFIED",
         "Matching PASS pilot evidence required")
    need(evidence.get("exitCode", 0) == 0, "Pilot process did not exit cleanly")
    need(evidence["pilotCheckpointSha256"] == sha(checkpoint_path), "Pilot checkpoint hash changed")
    need(evidence["checkpoint"] == checkpoint and evidence["nativeResult"] == result,
         "Pilot embedded checkpoint/result changed")
    console_pin = evidence["console"]
    console_path = ordinary(Path(console_pin["path"]))
    need(sha(console_path) == console_pin["sha256"], "Pilot console pin changed")
    console = console_path.read_text(encoding="utf-8", errors="replace")
    need("All dimensions are saved" in console and "OutOfMemoryError" not in console
         and "Crash report saved to" not in console, "Pilot did not record a clean native save")
    marker = read(Path(prepared["marker"]["path"]))
    manifest = read(plan / "manifest.json")
    manifest_sha = sha(plan / "manifest.json")
    for document in (checkpoint, result):
        need(document["operationId"] == evidence["operationId"] == marker["operationId"],
             "Operation identity changed")
        need(document["productionJarSha256"] == marker["productionGameSha256"], "Production game pin changed")
        need(document["runtimeManifestSha256"] == manifest_sha, "Runtime manifest pin changed")
        need(Path(document["targetWorldPath"]).resolve() == candidate.resolve(), "Target world identity changed")
        need(document["cityStaticArchitecture"] and document["staticArchitecture"]
             and not document["staticGrassMasonry"] and document["boundedChunkLoading"],
             "Static-only bounded operation flags changed")
        need(document["heapMaxBytes"] == 10 * 1024 ** 3, "Declared native heap differs from 10 GiB")
        need(fully_drained(document), "Pilot checkpoint/result is not fully drained")
    for key in ("operationId", "productionJarSha256", "runtimeManifestSha256", "targetWorldPath",
                "phase", "line", "committed", "alreadyPresent", "architectureSkipped",
                "architectureOwners", "architectureCells", "status", "originalRules",
                "cityStaticArchitecture", "staticArchitecture", "staticGrassMasonry",
                "boundedChunkLoading", "boundedChunkLimits"):
        need(checkpoint[key] == result[key], f"Clean-save checkpoint/result identity differs: {key}")
    delta = evidence["processedComponentDelta"]
    completed = (result["status"] == "PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED"
                 and result["phase"] == result["phaseCount"] == 4 and result["line"] == 0)
    need(delta == PILOT_LIMIT or completed, "Pilot did not reach first128 or complete the stream")
    need(evidence["counterDeltas"]["committed"] > 0
         and evidence["counterDeltas"]["architectureOwners"] > 0
         and evidence["counterDeltas"]["architectureCells"] > 0,
         "Pilot has no positive committed owner/cell delta")
    return checkpoint, result, manifest, manifest_sha


@contextlib.contextmanager
def stopped_world(world: Path):
    """Fail closed if the standard Java world lock is currently held (Windows)."""
    lock = ordinary(world / "session.lock")
    handle = lock.open("rb")
    try:
        if os.name == "nt":
            import msvcrt
            try:
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
            except OSError as failure:
                raise AssertionError(f"World is open: {world}") from failure
        yield
    finally:
        if os.name == "nt":
            handle.seek(0)
            msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
        handle.close()


def main():
    default_work = ROOT / ".handoff-local" / "work"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-world", type=Path,
                        default=ROOT / ".handoff-local/checkpoints/completed-v4-20261017/world")
    parser.add_argument("--candidate-world", type=Path, default=default_work / "static-city/world")
    parser.add_argument("--generated", type=Path, default=default_work / "catalog-expansion/generated")
    parser.add_argument("--plan", type=Path, default=default_work / "static-city/plan")
    parser.add_argument("--prepared-receipt", type=Path,
                        default=default_work / "static-city/portable-static-prepared.json")
    parser.add_argument("--pilot-evidence", type=Path,
                        default=default_work / "static-city/pilot-evidence/result.json")
    parser.add_argument("--output", type=Path,
                        default=default_work / "static-city/pilot-preservation")
    parser.add_argument("--execute", action="store_true")
    args = parser.parse_args()

    before = ordinary(args.baseline_world)
    after = ordinary(args.candidate_world)
    generated = ordinary(args.generated)
    plan = ordinary(args.plan)
    output = args.output.resolve()
    need(before != after and before.is_relative_to(ROOT / ".handoff-local/checkpoints")
         and after == (default_work / "static-city/world").resolve(),
         "Expected immutable checkpoint baseline and independent static-city candidate")
    need(not output.exists() and not output.is_relative_to(before) and not output.is_relative_to(after),
         "Fresh evidence directory outside both worlds required")
    if not args.execute:
        print(canon({"status": "PLAN_ONLY_NO_WORLD_READ", "components": PILOT_LIMIT,
                     "scope": "Only first128 touched chunk slots and corresponding owner shards",
                     "ownerDataV1": "Decoded to canonical typed owners in memory",
                     "javaLaunched": False, "worldWritten": False}))
        return

    prepared = read(args.prepared_receipt)
    pilot = read(args.pilot_evidence)
    need(prepared["status"] == "PASS_PORTABLE_STATIC_PREPARED_NOT_RUN", "Prepared receipt changed")
    need(Path(prepared["world"]).resolve() == after, "Prepared receipt names another candidate")
    checkpoint, native_result, manifest, manifest_sha = verify_controls(after, plan, prepared, pilot)
    catalog = ordinary(generated / "static-architecture-v1274.json")
    components = ordinary(generated / "static-architecture-components.jsonl.gz")
    catalog_sha, components_sha = sha(catalog), sha(components)
    need(catalog_sha == prepared["operationCatalog"]["sha256"], "Generated operation catalog changed")
    need(components_sha == prepared["componentStream"]["sha256"], "Generated component stream changed")
    need(manifest["staticArchitectureCatalogSha256"] == catalog_sha
         and sha(plan / components.name) == components_sha,
         "Native-bound plan differs from generated catalog/stream")
    rows = load_rows(components, manifest)
    recipe_count = len(read(catalog)["recipes"])
    need(0 < recipe_count <= 32768, "Generated recipe count outside configured operation bound")

    output.mkdir(parents=True)
    report = {"status": "RUNNING", "checkerSha256": sha(Path(__file__)),
              "javaLaunched": False, "worldWritten": False}
    try:
        with stopped_world(before), stopped_world(after):
            legacy = prepared["legacyControls"]
            for name in LEGACY:
                need(sha(before / name) == legacy[name] == sha(after / name),
                     f"Completed legacy control changed: {name}")
            skip = load_skips(after, checkpoint["operationId"])
            old_reader = Reader(before)
            new_reader = Reader(after)
            expected, removed, affected, counts, owner_roots = classify(
                rows, skip, old_reader, new_reader, recipe_count)
            deltas = pilot["counterDeltas"]
            for key in ("committed", "alreadyPresent", "architectureSkipped"):
                need(counts[key] == deltas[key], f"Actual first128 accounting differs: {key}")
            need(sum(counts.values()) == PILOT_LIMIT and counts["committed"] > 0,
                 "First128 accounting has no committed migration")

            chunk_before = collections.Counter()
            chunk_after = collections.Counter()
            chunk_evidence = []
            for cx, cz in sorted(affected):
                old_root = old_reader.root(cx, cz)
                old_counts = old_reader.counts(cx, cz)
                new_root = new_reader.root(cx, cz)
                new_counts = new_reader.counts(cx, cz)
                facts = verify_chunk(old_root, new_root, cx, cz, expected)
                chunk_before.update(old_counts)
                chunk_after.update(new_counts)
                chunk_evidence.append({"chunk": [cx, cz], "beforeCounts": old_counts,
                                       "afterCounts": new_counts, "comparison": facts})
                old_reader.clear()
                new_reader.clear()
            (output / "affected-chunks.jsonl").write_text(
                "\n".join(canon(row) for row in chunk_evidence) + "\n", encoding="utf-8")

            ledger_counts, ledger_before, ledger_after, ledger_meta_pins = compare_ledger(
                before, after, affected, removed, set(expected), output / "affected-ledger-shards.jsonl")
            old_reader.recheck()
            new_reader.recheck()
            need(sha(after / CP) == pilot["pilotCheckpointSha256"]
                 and read(after / RESULT) == native_result, "Pilot controls changed during audit")
            for name in LEGACY:
                need(sha(before / name) == legacy[name] == sha(after / name),
                     f"Legacy control changed during audit: {name}")

            report.update(
                status="PASS_BOUNDED_STATIC_PILOT_TYPED_PRESERVATION",
                baselineWorld=str(before), candidateWorld=str(after),
                operationId=checkpoint["operationId"], pilotCheckpointSha256=pilot["pilotCheckpointSha256"],
                planManifestSha256=manifest_sha, catalogSha256=catalog_sha, componentStreamSha256=components_sha,
                componentCounts=dict(counts), committedStaticCells=len(expected), selectedOwnersRemoved=len(removed),
                selectedOwnerRoots=owner_roots, affectedChunks=len(affected), affectedChunkCoordinates=sorted(affected),
                affectedChunkCountsBefore=dict(chunk_before), affectedChunkCountsAfter=dict(chunk_after),
                affectedLedgerCountsBefore=ledger_before, affectedLedgerCountsAfter=ledger_after,
                ledgerVerificationCounts=ledger_counts, ledgerMetadataPins=ledger_meta_pins,
                expectedStaticStatesHaveNoBlockEntity=True, selectedOwnerMembershipAbsent=True,
                unselectedStatesTypedBlockEntitiesAndDecodedLedgerExact=True,
                ownerDataV1={"decodedReadOnly": True, "canonicalField": "owners",
                             "rawBaselineAndCandidateUnmodified": True},
                scope={"components": PILOT_LIMIT, "onlyDirectAnvilSlots": True,
                       "onlyAffectedOwnerShards": True, "wholeWorldScan": False,
                       "wholeWorldRehash": False, "gameplayClaim": False})
    except BaseException:
        report.update(status="FAIL_BOUNDED_STATIC_PILOT_PRESERVATION_REVIEW_REQUIRED",
                      failure=traceback.format_exc())
    (output / "result.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n",
                                        encoding="utf-8")
    need(report["status"].startswith("PASS_"), "Pilot preservation check failed")


if __name__ == "__main__":
    main()
