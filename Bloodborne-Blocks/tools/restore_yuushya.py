#!/usr/bin/env python3
from __future__ import annotations
import argparse, copy, json, shutil, tempfile, zipfile
from pathlib import Path
from collections import Counter
from world_io import RegionFile, Tag, TAG_COMPOUND, TAG_LIST, TAG_LONG_ARRAY, block_state_key, compound, section_blocks, invalidate_chunk_lighting, pack_palette_indices, read_nbt

def _world(path):
    p=Path(path).resolve()
    if p.is_dir(): return p,None
    if p.suffix.lower()!='.zip': raise ValueError(f'world must be a directory or zip: {path}')
    t=Path(tempfile.mkdtemp(prefix='yuushya-'))
    try:
        with zipfile.ZipFile(p) as z:
            for i in z.infolist():
                d=(t/i.filename).resolve()
                if d!=t and t not in d.parents: raise ValueError('unsafe zip member')
                if i.is_dir(): d.mkdir(parents=True,exist_ok=True)
                else: d.parent.mkdir(parents=True,exist_ok=True); d.write_bytes(z.read(i))
    except BaseException:
        shutil.rmtree(t,ignore_errors=True)
        raise
    roots=[x for x in t.iterdir() if x.is_dir() and (x/'level.dat').exists()]
    return (roots[0] if len(roots)==1 else t),t
def dimensions(w):
    cs=[w,w/'DIM-1',w/'DIM1']
    if (w/'dimensions').exists(): cs += [p for p in (w/'dimensions').glob('*/*') if p.is_dir()]
    for d in cs:
        if not d.is_dir() or not (d/'region').is_dir(): continue
        r=d.relative_to(w).as_posix()
        if r=='.': n='minecraft:overworld'
        elif r in ('DIM-1','DIM1'): n={'DIM-1':'minecraft:the_nether','DIM1':'minecraft:the_end'}[r]
        elif r.startswith('dimensions/'): b=r.split('/'); n=b[1]+':'+ '/'.join(b[2:])
        else: n=r
        yield n,d
def _regions(w):
    for n,d in dimensions(w):
        for f in sorted((d/'region').glob('r.*.*.mca')): yield n,d,f
def _ns(k): return k.split(':',1)[0] if ':' in k else 'minecraft'
def _entries(root):
    for sec in root.get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
        p=section_blocks(sec)
        if not p: continue
        pal,idx=p; sy=compound(sec).get('Y',Tag(1,0)).value
        for i,v in enumerate(idx):
            k=block_state_key(pal[v])
            if _ns(k).startswith('yuushya'): yield i%16,sy*16+i//256,i//16%16,k,pal[v]
def scan(world):
    out=[]
    for n,_,f in _regions(world):
        rx,rz=(int(x) for x in f.stem.split('.')[1:]); rf=RegionFile.open(f)
        for c in rf.chunks():
            if b'yuushya' not in c.raw_nbt().lower(): continue
            for x,y,z,k,_ in _entries(compound(c.nbt().root)): out.append({'dimension':n,'x':(rx*32+c.x)*16+x,'y':y,'z':(rz*32+c.z)*16+z,'state':k})
    return out
def _dv(w):
    p=w/'level.dat'
    if not p.exists(): return None
    return compound(compound(read_nbt(p).root)['Data']).get('DataVersion',Tag(3,0)).value
def _copy_lists(src,dst,coords):
    for field in ('block_entities','block_ticks','fluid_ticks'):
        s=src.get(field,Tag(TAG_LIST,[],TAG_COMPOUND))
        d=dst.setdefault(field,Tag(TAG_LIST,[],TAG_COMPOUND))
        def pos(e):
            c=compound(e); return tuple(c.get(k,Tag(3,0)).value for k in ('x','y','z'))
        d.value[:]=[e for e in d.value if pos(e) not in coords]
        d.value.extend(copy.deepcopy(e) for e in s.value if pos(e) in coords)
def restore(donor,target,out,dimension_map=None):
    op=Path(out).resolve(); dp=Path(donor).resolve(); tp=Path(target).resolve()
    if op in (dp,tp) or dp in op.parents or tp in op.parents: raise ValueError('output must be outside inputs')
    if op.exists(): raise ValueError('output must be new')
    op.parent.mkdir(parents=True,exist_ok=True)
    dd=td=dt=tt=stage=None
    try:
        dd,dt=_world(donor); td,tt=_world(target)
        donor_dims=dict(dimensions(dd)); target_dims=dict(dimensions(td))
        if dimension_map:
            if any(a not in donor_dims or b not in target_dims for a,b in dimension_map.items()):
                raise ValueError('dimension mapping references an absent dimension')
            if len(set(dimension_map.values()))!=len(dimension_map):
                raise ValueError('dimension mapping must not merge donor dimensions')
        a,b=_dv(dd),_dv(td)
        if a is not None and b is not None and a!=b: raise ValueError(f'DataVersion mismatch: {a} != {b}')
        stage=Path(tempfile.mkdtemp(prefix='yuushya-out-',dir=op.parent)); shutil.rmtree(stage); shutil.copytree(td,stage)
        mp=dimension_map or {}; trf={(n,f.name):f for n,_,f in _regions(stage)}; changed=[]
        for sn,_,sf in _regions(dd):
            if mp and sn not in mp: continue
            tn=mp.get(sn,sn); tf=trf.get((tn,sf.name)); sr=RegionFile.open(sf)
            if tf is None:
                if any(b'yuushya' in c.raw_nbt().lower() and next(_entries(compound(c.nbt().root)),None) is not None for c in sr.chunks()): raise ValueError(f'missing target region {tn} {sf.name}')
                continue
            tr=RegionFile.open(tf); rx,rz=(int(x) for x in sf.stem.split('.')[1:])
            for sc in sr.chunks():
                if b'yuushya' not in sc.raw_nbt().lower(): continue
                root=compound(sc.nbt().root); ent=list(_entries(root))
                if not ent: continue
                tc=tr.get_chunk(sc.x,sc.z)
                if tc is None: raise ValueError(f'missing target chunk {tn} {sf.name} {sc.x},{sc.z}')
                nbt=tc.nbt(); dst=compound(nbt.root); secs=dst.get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value; dirty=False; coords=set()
                for x,y,z,k,e in ent:
                    sec=next((s for s in secs if compound(s).get('Y',Tag(1,0)).value==y//16),None)
                    if sec is None: raise ValueError(f'missing target section {tn} {sf.name} chunk {sc.x},{sc.z} section {y//16}')
                    p=section_blocks(sec)
                    if not p: raise ValueError(f'invalid target section {sf.name} section {y//16}')
                    pal,idx=p; off=(y%16)*256+z*16+x; old=pal[idx[off]]
                    if _ns(block_state_key(old)).startswith('yuushya'): continue
                    if e not in pal: pal.append(copy.deepcopy(e))
                    idx[off]=pal.index(e); bs=compound(compound(sec)['block_states']); bs['palette']=Tag(TAG_LIST,pal,TAG_COMPOUND); bs['data']=Tag(TAG_LONG_ARRAY,pack_palette_indices(idx,len(pal))); dirty=True
                    ax=(rx*32+sc.x)*16+x; az=(rz*32+sc.z)*16+z; coords.add((ax,y,az)); changed.append((tn,ax,y,az,k))
                if dirty: _copy_lists(root,dst,coords); invalidate_chunk_lighting(dst); tr.set_chunk(sc.x,sc.z,nbt,timestamp=tc.timestamp)
            if tr.to_bytes()!=tf.read_bytes(): tr.save(tf)
        stage.replace(op); stage=None; return {'changed':len(changed),'positions':changed}
    finally:
        for t in (dt,tt):
            if t: shutil.rmtree(t,ignore_errors=True)
        if stage: shutil.rmtree(stage,ignore_errors=True)
def main():
    ap=argparse.ArgumentParser(); sub=ap.add_subparsers(dest='mode',required=True); a=sub.add_parser('audit'); a.add_argument('world'); a.add_argument('-o','--output',type=Path); a.add_argument('--summary',action='store_true'); a.add_argument('--dimension-map',action='append',default=[]); r=sub.add_parser('restore'); r.add_argument('donor'); r.add_argument('target'); r.add_argument('output'); r.add_argument('--dimension-map',action='append',default=[]); ns=ap.parse_args(); mp=dict(x.split('=',1) for x in ns.dimension_map)
    if ns.mode=='audit':
        w,t=_world(ns.world)
        try:
            data=scan(w)
            for x in data:x['dimension']=mp.get(x['dimension'],x['dimension'])
            if ns.summary:
                c=Counter((x['dimension'],x['state']) for x in data); out={'count':len(data),'by_dimension':dict(Counter(x['dimension'] for x in data)),'states':{f'{d}|{s}':n for (d,s),n in c.items()}}
            else: out={'count':len(data),'blocks':data}
            text=json.dumps(out,ensure_ascii=False); print(text)
            if ns.output: ns.output.write_text(text+'\n',encoding='utf-8')
        finally:
            if t: shutil.rmtree(t,ignore_errors=True)
    else: print(json.dumps(restore(ns.donor,ns.target,ns.output,mp),ensure_ascii=False))
if __name__=='__main__': main()
