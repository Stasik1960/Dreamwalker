"""Count saved-light corrections and list the biggest block-light decreases."""
from argparse import ArgumentParser
from collections import defaultdict
import heapq
import json
from pathlib import Path

import numpy as np

from audit_lighting import nibble
from relight_map import RegionFile, _dimension_for_region, compound, read_plan

ZERO = np.zeros((16,16,16),dtype=np.uint8)


def layers(chunk):
    return {compound(part)["Y"].value: compound(part) for part in compound(chunk.nbt().root)["sections"].value}


def compare(source, output, work):
    target = read_plan(work/"lighting-plan.tsv")
    summary = defaultdict(lambda: defaultdict(int))
    examples = defaultdict(list)
    for patched in sorted(output.glob("**/region/r.*.*.mca")):
        before_file = source/patched.relative_to(output)
        before, after = RegionFile.open(before_file), RegionFile.open(patched)
        rx,rz = map(int, before_file.stem.split(".")[1:])
        dim = _dimension_for_region(output,patched)
        stats = summary[dim]
        for old in before.chunks():
            x,z = old.x + rx*32, old.z + rz*32
            if (x,z) not in target[dim]:
                continue
            original = layers(old)
            result = layers(after.get_chunk(old.x,old.z))
            for y in range(-4,20):
                saved, fresh = original.get(y,{}), result.get(y,{})
                if not saved and not fresh:
                    continue
                stats["sections_compared"] += 1
                a, b = [nibble(section["BlockLight"]) if "BlockLight" in section else ZERO for section in (saved,fresh)]
                stats["block_light_increased"] += int(np.count_nonzero(b>a))
                stats["block_light_decreased"] += int(np.count_nonzero(a>b))
                stats["positive_block_light_removed"] += int(np.count_nonzero((a>0)&(b==0)))
                if "SkyLight" in saved and "SkyLight" in fresh:
                    a_sky,b_sky = nibble(saved["SkyLight"]),nibble(fresh["SkyLight"])
                    stats["sky_light_increased"] += int(np.count_nonzero(b_sky>a_sky))
                    stats["sky_light_decreased"] += int(np.count_nonzero(a_sky>b_sky))
                if np.any(a>b):
                    diff = a.astype(np.int16)-b
                    highest = int(diff.max())
                    if highest > 0 and (len(examples[dim])<24 or highest>=examples[dim][0][0]):
                        dy,dz,dx = map(int,np.unravel_index(np.argmax(diff),diff.shape))
                        point = (x*16+dx,y*16+dy,z*16+dz)
                        row = (highest, point, int(a[dy,dz,dx]), int(b[dy,dz,dx]))
                        if len(examples[dim])<24:
                            heapq.heappush(examples[dim],row)
                        else:
                            heapq.heappushpop(examples[dim],row)

    return {
        dim: {"counts": stats, "largest_block_light_drops": sorted(examples[dim],reverse=True)}
        for dim,stats in summary.items()
    }


if __name__=="__main__":
    p=ArgumentParser(description=__doc__)
    p.add_argument("source",type=Path)
    p.add_argument("output",type=Path)
    p.add_argument("work",type=Path)
    p.add_argument("report",type=Path)
    args=p.parse_args()
    report=compare(args.source,args.output,args.work)
    args.report.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    for dim,result in report.items():
        print(dim,dict(result["counts"]))
