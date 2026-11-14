from argparse import ArgumentParser
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).parent))
import numpy as np

from audit_lighting import block_state_key, load_emissions, nibble, section_blocks
from relight_map import read_baked, RegionFile, Tag, compound

POINTS = {
    "minecraft:overworld": [
        (96,100,-769), (96,100,-768), (97,100,-768), (97,100,-767),
        (47,8,-1105), (95,22,-1117), (-113,25,-768), (-112,25,-768),
        (348,97,-992),
    ],
    "bloodborne_dw:bloodborne_old": [
        (-241,-63,-288), (-240,-63,-288), (31,87,136), (32,87,136),
    ],
}


def region_path(source, dimension):
    return source/"region" if dimension == "minecraft:overworld" else source/"dimensions/bloodborne_dw/bloodborne_old/region"


def find_old(source, dimension, x,y,z):
    folder = region_path(source, dimension)
    file = folder/f"r.{x//512}.{z//512}.mca"
    c = RegionFile.open(file).get_chunk(x//16%32,z//16%32)
    root = compound(c.nbt().root)
    section = next((compound(s) for s in root["sections"].value if compound(s)["Y"].value==y//16),{})
    return section


def point_value(section,x,y,z):
    xyz = y%16,z%16,x%16
    return tuple(int(nibble(section[key])[xyz]) if key in section else None
                 for key in ("SkyLight","BlockLight"))


def nearest_sources(source, dimension, point, emissions, cutoff=14):
    x,y,z = point
    folder = region_path(source, dimension)
    regions = {}
    results = []
    for cx in range((x-cutoff)//16,(x+cutoff)//16+1):
        for cz in range((z-cutoff)//16,(z+cutoff)//16+1):
            key = cx//32,cz//32
            if key not in regions:
                p = folder/f"r.{key[0]}.{key[1]}.mca"
                if not p.is_file(): continue
                regions[key] = RegionFile.open(p)
            c = regions[key].get_chunk(cx%32,cz%32)
            if not c: continue
            chunk = compound(c.nbt().root)
            for section in chunk["sections"].value:
                sy = compound(section)["Y"].value
                if sy < (y-cutoff)//16 or sy > (y+cutoff)//16:
                    continue
                read = section_blocks(section)
                if not read: continue
                palette, data = read
                states = [block_state_key(p) for p in palette]
                values = np.asarray([emissions.get(state,0) for state in states], dtype=np.uint8)
                if not values.any(): continue
                structure = np.asarray(data).reshape((16,16,16))
                for dy,dz,dx in np.argwhere(values[structure]):
                    dx,dy,dz=map(int,(dx,dy,dz))
                    loc = cx*16+dx,sy*16+dy,cz*16+dz
                    dist = sum(abs(a-b) for a,b in zip(loc,point))
                    level = int(values[structure[dy,dz,dx]])
                    if level>dist:
                        results.append((level-dist, loc, level, states[structure[dy,dz,dx]]))
    return sorted(results,reverse=True)


def main():
    p=ArgumentParser()
    p.add_argument("run", type=Path)
    p.add_argument("--source", type=Path, required=True)
    p.add_argument("--sources", action="store_true")
    opts=p.parse_args()
    emissions = load_emissions(opts.run/"registry.tsv") if opts.sources else {}
    for dimension,points in POINTS.items():
        for x,y,z in points:
            path = opts.run/"relit"/dimension.replace(":","_")/f"{x//16}.{z//16}.bin"
            if not path.is_file():
                continue
            prior = find_old(opts.source,dimension,x,y,z)
            fresh = read_baked(path).get(y//16,{})
            before,after = point_value(prior,x,y,z),point_value(fresh,x,y,z)
            print(f"{dimension} ({x},{y},{z}) sky,block: {before} -> {after}",flush=True)
            if opts.sources and before[1]:
                print("  Emitting blocks within range:", nearest_sources(opts.source,dimension,(x,y,z),emissions)[:5])


if __name__ == "__main__":
    main()
