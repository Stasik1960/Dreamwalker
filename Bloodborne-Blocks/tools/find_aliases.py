"""Prove palette aliases by equal textured faces at integer-translated positions.

Distinct art, transparent materials and partial matches remain separate blocks.
The migration table records a target state AND root offset, preserving map pixels.
"""
from catalog_geometry import *
from generate_collision import texname, POLICIES
from render_catalog import FACE_INDEX
import hashlib

@functools.lru_cache(None)
def fingerprint(packed):
    apps=json.loads(packed);faces=[];allpoints=[]
    for app in apps:
        md=model(app['model'])
        for e in elements(app):
            verts=corners(e,app)
            for side,face in e.get('faces',{}).items():
                pts=verts[FACE_INDEX[side]];allpoints.extend(pts)
                uv=face.get('uv')
                if uv is None:
                    x,y,z=e['from'];X,Y,Z=e['to']
                    uv={'down':[x,16-Z,X,16-z],'up':[x,z,X,Z],
                        'north':[16-X,16-Y,16-x,16-y],'south':[x,16-Y,X,16-y],
                        'west':[z,16-Y,Z,16-y],'east':[16-Z,16-Y,16-z,16-y]}[side]
                u,v,U,V=uv
                coords=np.roll(np.array([[U,V],[u,V],[u,v],[U,v]]),-face.get('rotation',0)//90,axis=0)
                faces.append((pts,coords,texname(md,face['texture']),face.get('tintindex',-1)))
    if not allpoints:return None,(0,0,0)
    origin=np.floor(np.min(allpoints,axis=0)+1e-6).astype(int)
    flat=[]
    for pts,uv,texture,tint in faces:
        vertices=sorted(tuple(np.round(np.r_[p-origin,t],5)) for p,t in zip(pts,uv))
        flat.append((texture,tint,vertices))
    return hashlib.sha256(repr(sorted(flat)).encode()).hexdigest(),tuple(int(x) for x in origin)

def main():
    defs={b['id']:b for b in DATA['blocks']};index={};aliases={};report=[]
    # Prefer the original palette IDs over orphan asset entries and wax copies.
    order=sorted(DATA['blocks'],key=lambda b:(b['id'].startswith('asset_'),b['id'].startswith('waxed_'),len(b['id']),b['id']))
    for b in order:
        ident=b['id']
        if ident=='spruce_button':continue
        bs=json.loads((ASSETS/'blockstates'/f'{ident}.json').read_text())
        rows={};candidates=None
        for sk in b['states']:
            state=dict(x.split('=') for x in sk.split(',') if x)
            groups=applications(bs,state)
            # Keep genuinely random palette variants out of automatic deduplication.
            if any(len(g)>1 for g in groups):candidates=set();break
            apps=[g[0] for g in groups]
            sig,origin=fingerprint(json.dumps(apps,sort_keys=True))
            contract=(sig,b['kind'],b['layer'],b['emissive'],POLICIES[ident]['collision'],
                      state.get('waterlogged'),state.get('open'),state.get('assembled'),b['states'][sk][2])
            matches=index.get(contract,{}) if sig else {}
            rows[sk]=(contract,origin)
            candidates=set(matches) if candidates is None else candidates.intersection(matches)
        if candidates:
            target=next(x['id'] for x in order if x['id'] in candidates)
            table={}
            for sk,(sig,origin) in rows.items():
                targetkey,targetorigin=index[sig][target]
                table[sk]={'state':targetkey,'offset':[origin[a]-targetorigin[a] for a in range(3)]}
            aliases[ident]={'target':target,'states':table};report.append({'alias':ident,'target':target,'states':len(table)})
        else:
            for sk,(sig,origin) in rows.items():index.setdefault(sig,{}).setdefault(ident,(sk,origin))
    dest=RES/'bloodborne_blocks/aliases.json'
    dest.write_text(json.dumps({'removed':['spruce_button'],'aliases':aliases},separators=(',',':'))+'\n')
    (ROOT/'docs/palette-aliases.json').write_text(json.dumps(report,indent=2)+'\n')
    print('Verified aliases:',len(aliases));print(json.dumps(report))

if __name__=='__main__':main()
