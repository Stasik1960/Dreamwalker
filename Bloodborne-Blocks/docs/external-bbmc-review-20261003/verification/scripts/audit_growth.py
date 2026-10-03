"""Read-only terrain footprint proxy; skip non-heightmap NBT payloads."""
import argparse,hashlib,json,math,re,struct,sys,time,zipfile
from pathlib import Path
import numpy as np
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--inputs',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--project',type=Path,default=Path(__file__).resolve().parents[4])
args=parser.parse_args()
sys.path.insert(0,str(args.project.resolve()/'tools'))
from world_io import RegionFile,decode_nbt
ROOT=args.output.resolve()
ROOT.mkdir(parents=True,exist_ok=True)
INPUTS=args.inputs.resolve()
CACHE={};CHECKED=0
AUDIT_ROOT=Path(__file__).resolve().parents[2]
EXPECTED_INPUTS={m['file']:m for m in json.loads((AUDIT_ROOT/'input-manifest.json').read_text(encoding='utf-8'))}

def verify_input(filename):
    path=INPUTS/filename;expected=EXPECTED_INPUTS[filename]
    if path.stat().st_size!=expected['bytes']:raise ValueError('input size mismatch: '+filename)
    digest=hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda:f.read(8*1024*1024),b''):digest.update(block)
    if digest.hexdigest()!=expected['sha256']:raise ValueError('input SHA-256 mismatch: '+filename)

class SelectNBT:
    def __init__(self,raw):self.raw=raw;self.pos=0
    def take(self,n):
        if n<0 or self.pos+n>len(self.raw):raise ValueError('NBT bounds')
        p=self.pos;self.pos+=n;return p
    def byte(self):return self.raw[self.take(1)]
    def count(self):
        value=struct.unpack_from('>i',self.raw,self.take(4))[0]
        if value<0:raise ValueError('negative count')
        return value
    def name(self):
        n=struct.unpack_from('>H',self.raw,self.take(2))[0]
        p=self.take(n);return self.raw[p:p+n]
    def skip(self,tag,depth=0):
        if depth>64:raise ValueError('NBT depth')
        if tag in (1,2,3,4,5,6):self.take({1:1,2:2,3:4,4:8,5:4,6:8}[tag])
        elif tag in (7,11,12):self.take(self.count()*{7:1,11:4,12:8}[tag])
        elif tag==8:self.take(struct.unpack_from('>H',self.raw,self.take(2))[0])
        elif tag==9:
            item=self.byte();count=self.count()
            if item==0 and count:raise ValueError('nonempty End list')
            if item in (1,2,3,4,5,6):self.take(count*{1:1,2:2,3:4,4:8,5:4,6:8}[item])
            else:
                for _ in range(count):self.skip(item,depth+1)
        elif tag==10:
            while True:
                item=self.byte()
                if item==0:break
                self.name();self.skip(item,depth+1)
        elif tag!=0:raise ValueError('unknown tag')
    def heightmaps(self):
        if self.byte()!=10:raise ValueError('root not compound')
        self.name()
        while True:
            tag=self.byte()
            if not tag:return None,None
            name=self.name()
            if name==b'Heightmaps' and tag==10:
                candidates={}
                while True:
                    item=self.byte()
                    if not item:break
                    key=self.name()
                    if item==12 and key in (b'WORLD_SURFACE',b'MOTION_BLOCKING'):
                        count=self.count();p=self.take(count*8)
                        candidates[key]=np.frombuffer(self.raw,dtype='>u8',count=count,offset=p).astype(np.uint64)
                    else:self.skip(item)
                if b'WORLD_SURFACE' in candidates:return candidates[b'WORLD_SURFACE'],'WORLD_SURFACE'
                if b'MOTION_BLOCKING' in candidates:return candidates[b'MOTION_BLOCKING'],'MOTION_BLOCKING'
                return None,None
            self.skip(tag)

def get_heights(chunk):
    global CHECKED
    key=hashlib.sha256(bytes([chunk.compression])+chunk.compressed_payload).digest()
    if key in CACHE:return CACHE[key]
    raw=chunk.raw_nbt();words,source=SelectNBT(raw).heightmaps()
    if words is None:return None,None
    if CHECKED<12:
        d=decode_nbt(raw).root.value
        expected=d['Heightmaps'].value[source].value
        assert words.tolist()==np.asarray(expected,dtype=np.int64).view(np.uint64).tolist()
        CHECKED+=1
    if len(words)!=math.ceil(256/7):raise ValueError('heightmap long count')
    heights=((words[:,None]>>(np.arange(7,dtype=np.uint64)*9))&511).reshape(-1)[:256].astype(np.int16)-65
    CACHE[key]=(heights,source);return CACHE[key]

def run(filename):
    cells={};sources={};chunks=0;missing=0
    with zipfile.ZipFile(INPUTS/filename) as z:
        level=next(n for n in z.namelist() if n.endswith('level.dat'));prefix=level[:-9]
        for name in z.namelist():
            match=re.fullmatch(r'region/r\.(-?\d+)\.(-?\d+)\.mca',name[len(prefix):])
            if not match:continue
            rx,rz=map(int,match.groups());raw=z.read(name)
            if not raw:continue
            for chunk in RegionFile(raw).chunks():
                chunks+=1;heights,source=get_heights(chunk)
                if heights is None:missing+=1;continue
                sources[source]=sources.get(source,0)+1
                mask=heights>0
                if mask.any():cells[(rx*32+chunk.x,rz*32+chunk.z)]=mask
    coordinates=set(cells);columns=sum(int(v.sum()) for v in cells.values())
    xs=[k[0] for k in cells];zs=[k[1] for k in cells]
    bounds={'x_min':min(xs)*16,'x_max_exclusive':(max(xs)+1)*16,'z_min':min(zs)*16,'z_max_exclusive':(max(zs)+1)*16}
    bounds['width_blocks']=bounds['x_max_exclusive']-bounds['x_min'];bounds['length_blocks']=bounds['z_max_exclusive']-bounds['z_min']
    result={'file':filename,'overworld_terrain_chunks':chunks,'heightmap_missing_chunks':missing,'heightmap_sources':sources,
            'chunks_with_surface_above_y0':len(cells),'columns_with_surface_above_y0':columns,'occupied_chunk_bbox':bounds}
    return result,cells

if __name__=='__main__':
    started=time.monotonic();results=[];base=None
    for filename in ['bbmc_v1_map (2).zip','bbmc_v13_map.zip','bbmc_v14_map.zip','bbmc_v15_map.zip','bbmc_v16_map (1).zip']:
        verify_input(filename)
        result,cells=run(filename)
        if base is None:base=cells
        else:
            common=set(base)&set(cells)
            result['versus_v1']={'occupied_chunk_overlap':len(common),'occupied_chunk_added':len(set(cells)-set(base)),
                'occupied_chunk_removed':len(set(base)-set(cells)),
                'column_overlap':sum(int((base[k]&cells[k]).sum()) for k in common)}
            result['versus_v1']['new_above_y0_columns']=result['columns_with_surface_above_y0']-result['versus_v1']['column_overlap']
            result['versus_v1']['lost_above_y0_columns']=results[0]['columns_with_surface_above_y0']-result['versus_v1']['column_overlap']
        results.append(result);print(json.dumps(result,ensure_ascii=False),flush=True)
        (ROOT/'map-growth-progress.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
    assert results[-1]['chunks_with_surface_above_y0']==3446
    reference=json.loads((AUDIT_ROOT/'map-growth.json').read_text(encoding='utf-8'))
    if results!=reference['archives']:raise ValueError('archive measurements differ from published reference')
    output={'valid':True,'method':'Stored WORLD_SURFACE heightmaps, fallback MOTION_BLOCKING, overworld only; surface block y>0. Selective NBT reader checked against world_io on 12 chunks; v16 occupied chunk count matches previous heightmap render (same packing formula; reproducibility cross-check).',
            'limitations':'Heightmaps may be stale after external edits. Includes natural terrain and foundations, excludes low terrain/underground, model overhang and entity geometry. This is not semantic architectural area or count of locations. Chunks without stored heightmaps are excluded; coverage differs by version. Coordinates largely differ, so this does not measure enlargement of the same named locations.',
            'archives':results,'elapsed_seconds':round(time.monotonic()-started,2),'selective_parser_comparison_chunks':CHECKED,
            'input_sha256_verified':True,'archive_measurements_match_reference':True}
    (ROOT/'map-growth.json').write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding='utf-8');print('COMPLETE',output['elapsed_seconds'],flush=True)
