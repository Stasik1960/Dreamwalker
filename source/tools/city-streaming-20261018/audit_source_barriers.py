#!/usr/bin/env python3
"""Prove remaining source barriers from the immutable vanilla archive.

Read-only audit. Coordinate provenance is rebuilt from the supplied original
archive; no invisible-carrier class is treated as a removable barrier.
"""
import argparse, gzip, hashlib, json, re, sys, zipfile
from collections import Counter, defaultdict
from pathlib import Path
from world_io import RegionFile, Tag, section_blocks, block_state_key, compound, decode_nbt, _plain

def sha(data): return hashlib.sha256(data).hexdigest()
def file_sha(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024), b''): h.update(b)
    return h.hexdigest()
def dim_for(name):
    p=name.replace('\\','/').split('/region/')[0]
    if p.endswith('/DIM-1') or p=='DIM-1': return 'minecraft:the_nether'
    if p.endswith('/DIM1') or p=='DIM1': return 'minecraft:the_end'
    if '/dimensions/' in p:
        p=p.split('/dimensions/',1)[1].split('/',1)
        return ':'.join(p)
    return 'minecraft:overworld'
def dim_dir(dim):
    return {'minecraft:overworld':'', 'minecraft:the_nether':'DIM-1', 'minecraft:the_end':'DIM1'}[dim]
def sections(root):
    r=root.get('Level',Tag(10,root)).value
    return r,(r.get('sections') or r.get('Sections') or Tag(9,[],10)).value
def safe_tree(path):
    path=path.absolute()
    for p in (path, *path.parents):
        if p.exists() and p.stat().st_file_attributes & 0x400: raise ValueError('reparse point: '+str(p))
    return path
def source_coordinates(archive):
    coords=[];proof=[];chunks=0;versions=Counter()
    with zipfile.ZipFile(archive) as z:
        names=sorted(n for n in z.namelist() if re.search(r'(?:^|/)region/r\.-?\d+\.-?\d+\.mca$',n))
        for n in names:
            data=z.read(n); region=RegionFile(data); count=0
            for c in region.chunks():
                nbt=c.nbt();root=compound(nbt.root);versions[root.get('DataVersion',Tag(3,0)).value]+=1
                root,secs=sections(root);cx=root['xPos'].value;cz=root['zPos'].value;chunks+=1
                for s in secs:
                    fields=compound(s);bs=section_blocks(s)
                    if bs is None: continue
                    palette,indices=bs; barriers={i for i,p in enumerate(palette) if compound(p)['Name'].value=='minecraft:barrier'}
                    if not barriers: continue
                    sy=fields['Y'].value
                    for i,p in enumerate(indices):
                        if p in barriers:
                            coords.append((dim_for(n),cx*16+(i&15),sy*16+(i>>8),cz*16+((i>>4)&15)));count+=1
            proof.append({'entry':n,'sha256':sha(data),'barriers':count})
            print('source region',n,'barriers',count,flush=True)
    return sorted(coords,key=lambda p:(p[0],p[1]>>4,p[3]>>4,p[2]>>4,((p[2]&15)<<8)|((p[3]&15)<<4)|(p[1]&15))),{'regionFiles':proof,'chunkCount':chunks,'dataVersions':dict(versions)}
def inspect_world(world,coords):
    by_region=defaultdict(list)
    for dim,x,y,z in coords: by_region[(dim,x>>9,z>>9)].append((x,y,z))
    counts=Counter();details=[];bes=[];file_proof=[]
    for (dim,rx,rz),positions in sorted(by_region.items()):
        path=world/dim_dir(dim)/'region'/f'r.{rx}.{rz}.mca'
        if not path.exists():
            for p in positions: details.append({'dimension':dim,'position':list(p),'state':'<missing-region>'});counts['<missing-region>']+=1
            continue
        data=path.read_bytes();region=RegionFile(data);by_chunk=defaultdict(list)
        for p in positions: by_chunk[(p[0]>>4,p[2]>>4)].append(p)
        for (cx,cz),pp in sorted(by_chunk.items()):
            c=region.get_chunk(cx&31,cz&31)
            if c is None:
                for p in pp: details.append({'dimension':dim,'position':list(p),'state':'<missing-chunk>'});counts['<missing-chunk>']+=1
                continue
            root,secs=sections(compound(c.nbt().root));states={compound(s)['Y'].value:section_blocks(s) for s in secs}
            entities={(compound(b)['x'].value,compound(b)['y'].value,compound(b)['z'].value):b for b in root.get('block_entities',Tag(9,[],10)).value}
            for x,y,z in pp:
                bs=states.get(y>>4)
                state='minecraft:air' if not bs else block_state_key(bs[0][bs[1][((y&15)<<8)|((z&15)<<4)|(x&15)]])
                counts[state]+=1; row={'dimension':dim,'position':[x,y,z],'state':state}
                if (x,y,z) in entities:
                    row['blockEntity']=_plain(entities[x,y,z]);bes.append(row)
                details.append(row)
        file_proof.append({'file':str(path.relative_to(world)),'sha256':sha(data)})
    return dict(sorted(counts.items())),details,bes,file_proof
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--original-zip',type=Path,required=True);ap.add_argument('--world',type=Path,required=True);ap.add_argument('--out',type=Path,required=True);a=ap.parse_args()
    original=safe_tree(a.original_zip);world=safe_tree(a.world);out=safe_tree(a.out);out.mkdir(parents=True,exist_ok=True)
    coords,proof=source_coordinates(original)
    rows=[{'dimension':dim,'position':[x,y,z]} for dim,x,y,z in coords]
    payload=b''.join((json.dumps(r,separators=(',',':'),sort_keys=True)+'\n').encode() for r in rows)
    # Reproduce the historical coordinate gzip header for byte-level identity.
    import io
    b=io.BytesIO()
    with gzip.GzipFile(filename='source-barriers.jsonl',mode='wb',fileobj=b,mtime=0) as gz: gz.write(payload)
    phase=b.getvalue();(out/'source-barriers-from-original.jsonl.gz').write_bytes(phase)
    counts,details,bes,world_proof=inspect_world(world,coords)
    (out/'source-barrier-current-objects.json').write_text(json.dumps(details,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
    report={'schema':1,'mode':'READ_ONLY','originalZip':str(original),'originalZipSha256':file_sha(original),'world':str(world),'sourceBarrierCount':len(coords),'originalCoordinatePayloadSha256':sha(payload),'reproducedHistoricalPhaseSha256':sha(phase),'historical14855PhaseMatches':sha(phase)=='665989a9b97740dbc360daac6c8e857e537de9d12a15e0c1beff12edf11bad86','currentStatesAtOriginalBarrierCoordinates':counts,'blockEntityCountAtOriginalBarrierCoordinates':len(bes),'sourceRegionProof':proof,'worldRegionProof':world_proof}
    (out/'source-barrier-original-audit.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
    print(json.dumps({k:v for k,v in report.items() if k not in ['sourceRegionProof','worldRegionProof']},indent=2),flush=True)
if __name__=='__main__':main()
