"""Read-only conservative block occupancy audit for a flight route.

All non-air saved block states count as full solid blocks; native shapes can
extend beyond their saved block, so a clear result is not a collision proof.
"""
import argparse, json, math, sys
from pathlib import Path
from collections import Counter

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'map_io'))
import world_io as w

AIR = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'}

class World:
    def __init__(self, path):
        self.path = path
        self.regions = {}
        self.chunks = {}
        self.missing = set()
    def load(self, cx, cz):
        key = cx, cz
        if key in self.chunks:
            return self.chunks[key]
        rp = self.path / 'region' / f'r.{cx//32}.{cz//32}.mca'
        if rp not in self.regions:
            self.regions[rp] = w.RegionFile.open(rp) if rp.is_file() else None
        region = self.regions[rp]
        chunk = region.get_chunk(cx%32, cz%32) if region else None
        if chunk is None:
            self.missing.add(key)
            self.chunks[key] = None
            return None
        nbt = chunk.nbt()
        sections = w.child(nbt.root, 'sections', w.TAG_LIST)
        out = {}
        if sections:
            for section in sections.value:
                values = w.compound(section)
                y = values.get('Y')
                if y is None or y.value < 6:
                    continue
                data = w.section_blocks(section)
                if data:
                    palette, indices = data
                    out[int(y.value)] = ([w.block_state_key(p) for p in palette], indices)
        self.chunks[key] = out
        return out
    def at(self, x, y, z):
        chunk = self.load(x//16, z//16)
        if chunk is None:
            return '<missing>'
        section = chunk.get(y//16)
        if not section:
            return 'minecraft:air'
        names, data = section
        i = ((y%16)<<8) | ((z%16)<<4) | (x%16)
        return names[data[i]]

def positions(points, spacing):
    for i, a in enumerate(points):
        b = points[(i+1)%len(points)]
        length = math.dist([a[k] for k in 'xyz'], [b[k] for k in 'xyz'])
        count = math.ceil(length/spacing)
        for j in range(count+1):
            f = j/count
            yield i, tuple(a[k]+f*(b[k]-a[k]) for k in 'xyz')

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--world',type=Path,required=True)
    ap.add_argument('--plan',type=Path,required=True)
    ap.add_argument('--output',type=Path,required=True)
    ap.add_argument('--horizontal-half-width',type=float,default=.31)
    ap.add_argument('--vertical-margin',type=float,default=0)
    args=ap.parse_args()
    world=World(args.world)
    plan=json.loads(args.plan.read_text(encoding='utf-8-sig'))
    hits={}
    columns={}
    samples=0
    for seg,(x,y,z) in positions(plan['points'],0.5):
        samples += 1
        for bx in range(math.floor(x-args.horizontal_half_width), math.floor(x+args.horizontal_half_width)+1):
            for bz in range(math.floor(z-args.horizontal_half_width), math.floor(z+args.horizontal_half_width)+1):
                columns.setdefault((seg,bx,bz),[]).append(y)
                for by in range(math.floor(y-args.vertical_margin),math.floor(y+1.8+args.vertical_margin)+1):
                    state=world.at(bx,by,bz)
                    if state.split('[',1)[0] not in AIR:
                        hits[(seg,bx,by,bz,state)] = 1
    # Nearby exact block list aids diagnosis of the observed failed segment.
    nearby=[]
    for x in range(112,124):
        for y in range(136,146):
            for z in range(-714,-694):
                state=world.at(x,y,z)
                if state.split('[',1)[0] not in AIR:
                    nearby.append({'x':x,'y':y,'z':z,'state':state})
    segment_profiles=[]
    for seg in range(len(plan['points'])):
        heights=Counter(); highest=None; missing=0
        for (s,x,z),ys in columns.items():
            if s!=seg: continue
            for y in range(255,99,-1):
                state=world.at(x,y,z)
                name=state.split('[',1)[0]
                if name == '<missing>':
                    missing+=1; break
                if name not in AIR:
                    heights[y]+=1
                    if highest is None or y>highest['y']:
                        highest={'x':x,'y':y,'z':z,'state':state}
                    break
        segment_profiles.append({'segment':seg,'columns':sum(s==seg for s,_,_ in columns),
            'highestSavedNonAir':highest,'topHeightDistribution':dict(sorted(heights.items(),reverse=True)),
            'missingColumns':missing})
    result={'world':str(args.world),'plan':str(args.plan),'samples':samples,'spacingBlocks':.5,
            'horizontalHalfWidth':args.horizontal_half_width,'bodyHeightBlocks':1.8,
            'verticalMargin':args.vertical_margin,'occupancyHits':[
                {'segment':s,'x':x,'y':y,'z':z,'state':n} for s,x,y,z,n in sorted(hits)],
            'nearObservedFailure':nearby,'segments':segment_profiles,
            'missingChunks':sorted(world.missing),
            'limitations':['All non-air saved blocks are treated as full blocks, including passable ones.',
                'Native and composite shapes may extend beyond their saved block; this does not prove collision clearance.',
                'Missing chunks are not assumed empty.']}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'samples':samples,'occupancyHits':len(hits),'missingChunks':len(world.missing),
                      'nearObservedFailure':len(nearby),'segments':segment_profiles}))

if __name__=='__main__': main()
