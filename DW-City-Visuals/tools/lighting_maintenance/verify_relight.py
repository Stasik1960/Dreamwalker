"""Verify that the staged region replacements changed only light caches."""
import argparse
import hashlib
import json
from pathlib import Path

from relight_map import RegionFile, _dimension_for_region, compound, lighting_cache_errors, preserve_fields, read_plan


def verify(source, output, run):
    targets = read_plan(run/"lighting-plan.tsv")
    summary = {}
    for patched in sorted(output.glob("**/region/r.*.*.mca")):
        relative = patched.relative_to(output)
        original = source/relative
        old_region = RegionFile.open(original)
        new_region = RegionFile.open(patched)
        rx,rz = map(int, original.stem.split(".")[1:])
        dimension = _dimension_for_region(output,patched)
        checked = 0
        present = 0
        for old in old_region.chunks():
            key = (old.x + rx*32, old.z + rz*32)
            new = new_region.get_chunk(old.x,old.z)
            if new is None:
                raise ValueError(f"Chunk removed at {dimension} {key}")
            present += 1
            if key in targets.get(dimension, ()):
                prior = old.nbt()
                after = new.nbt()
                if preserve_fields(prior.root) != preserve_fields(after.root):
                    raise ValueError(f"Authored world fields changed at {dimension} {key}")
                if compound(after.root)["isLightOn"].value != 1 or new.timestamp != old.timestamp:
                    raise ValueError(f"Light marker/timestamp incorrect at {dimension} {key}")
                if lighting_cache_errors(compound(after.root)):
                    raise ValueError(f"Invalid light arrays at {dimension} {key}")
                checked += 1
            elif new != old:
                raise ValueError(f"Unrequested region change at {dimension} {key}")
        if present != sum(1 for _ in new_region.chunks()):
            raise ValueError(f"Chunk added in {relative}")
        summary[str(relative)] = {
            "relit_chunks": checked,
            "preserved_chunk_records": present,
            "original_sha256": hashlib.sha256(original.read_bytes()).hexdigest(),
            "relit_sha256": hashlib.sha256(patched.read_bytes()).hexdigest(),
        }
    expected = sum(map(len,targets.values()))
    if sum(r["relit_chunks"] for r in summary.values()) != expected:
        raise ValueError("Not all targeted chunks were verified")
    print(f"Verified {expected} light-only edits across {len(summary)} region files.")
    return summary


if __name__=="__main__":
    cli=argparse.ArgumentParser(description=__doc__)
    cli.add_argument("source",type=Path)
    cli.add_argument("output",type=Path)
    cli.add_argument("run",type=Path)
    cli.add_argument("--report",type=Path,required=True)
    o=cli.parse_args()
    o.report.write_text(json.dumps(verify(o.source,o.output,o.run),indent=2)+"\n",encoding="utf-8")
