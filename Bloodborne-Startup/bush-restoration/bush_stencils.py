"""Look for repeated whole grass models where no historical coordinate was available."""
from collections import Counter, defaultdict
import hashlib
import json
import struct
import sys
from zipfile import ZipFile

from complete_bush_repair import BASE, xyz_add, xyz_sub
from plan_bush_repair import selected_meshes
from probe_bush import JAR,NATIVE


def quad_key(raw):
    # Canonical cyclic starting vertex. Material, winding, and all vertex data
    # remain part of the signature.
    vertices=tuple(raw[11+32*i:11+32*(i+1)] for i in range(4))
    return hashlib.blake2s(raw[:11]+b''.join(min(vertices[i:]+vertices[:i] for i in range(4))),
                           digest_size=16).digest()


def grass_uv(raw,materials):
    mat,=struct.unpack_from('>I',raw)
    if not materials[mat]['sprite'].endswith('/spirelamp_0046'):
        return False
    uvs=[struct.unpack_from('>2f',raw,23+i*32) for i in range(4)]
    return all(13.9999<=u<=16.0001 and 13.3749<=v<=16.0001 for u,v in uvs)


def main():
    audits={'overworld':json.loads((BASE/'grass-audit.json').read_bytes())['visuals']}
    extra=BASE/'old-dimension-visuals.json'
    if extra.exists():
        audits['bloodborne_old']=json.loads(extra.read_bytes())
    p=json.loads((BASE/'bush-quad-provenance.json').read_bytes())
    historical=json.loads((BASE/'grass-audit.json').read_bytes())['old']
    roots={tuple(pos):(o['id'],tuple(o['payload'].get('SourceShift',(0,0,0)))) for pos,o,_ in historical}
    per_instance=defaultdict(list)
    with ZipFile(JAR) as jar:
        cat=json.loads(jar.read(NATIVE+'catalog.json'))
        cell_mesh={i:cell['mesh'] for i,cell in enumerate(cat['cells'])}
        needed={i for rows in audits.values() for _,i in rows}|{i for _,i,_ in p}
        meshes=dict(selected_meshes(jar,cat,{cell_mesh[i] for i in needed}))
    mapped={}
    for pos,index,groups in p:
        pos=tuple(pos)
        native=meshes[cell_mesh[index]]
        mapped[pos]={i for i,_ in groups}
        for i,origins in groups:
            for origin in map(tuple,origins):
                per_instance[origin].append((xyz_sub(pos,origin),quad_key(native[i])))
    # Stencils seen at 3+ old placements of the same complete model and shift.
    # Use the maximum face count per model to exclude clipped subsets.
    by_shape=defaultdict(Counter)
    example={}
    for origin,parts in per_instance.items():
        stencil=tuple(sorted(parts))
        by_shape[roots[origin]][stencil]+=1
        example.setdefault((roots[origin],stencil),origin)
    templates=[]
    for kind,counts in by_shape.items():
        limit=max(map(len,counts))
        for stencil,count in counts.items():
            if count>=3 and len(stencil)==limit:
                templates.append((kind,stencil,example[kind,stencil]))
    print('Repeated complete source stencils',len(templates),
          [(kind,len(stencil)) for kind,stencil,_ in templates],flush=True)
    out={}
    for dim,rows in audits.items():
        available={}
        for p0,idx in rows:
            pos=tuple(p0)
            ignored=mapped.get(pos,set()) if dim=='overworld' else set()
            hit=[(quad_key(quad),j) for j,quad in enumerate(meshes[cell_mesh[idx]])
                 if j not in ignored and grass_uv(quad,cat['materials'])]
            if hit:
                available[pos]=(idx,hit)
        by_key=defaultdict(set)
        for p0,(_,quads) in available.items():
            for key,_ in quads:
                by_key[key].add(p0)
        accepted=defaultdict(list)
        for kind,stencil,sample in templates:
            offset,key=min(stencil,key=lambda item:len(by_key[item[1]]))
            requirements=Counter(stencil)
            for p0 in by_key[key]:
                root=xyz_sub(p0,offset)
                if dim=='overworld' and root in roots:
                    continue
                if all(sum(k==signature for k,_ in available.get(xyz_add(root,delta),(None,[]))[1])>=count
                       for (delta,signature),count in requirements.items()):
                    exact=[]
                    for (delta,signature),count in sorted(requirements.items()):
                        p1=xyz_add(root,delta)
                        visual,quads=available[p1]
                        matches=[i for k,i in quads if k==signature]
                        exact.append((p1,visual,matches[:count]))
                    accepted[root].append((kind,sample,exact))
        filtered={pos:options[0] for pos,options in accepted.items() if len({x[0] for x in options})==1}
        assigned=defaultdict(set)
        for origin,(_,_,faces) in filtered.items():
            for p0,_,indices in faces:
                for i in indices:
                    assigned[p0,i].add(origin)
        collisions={o for group in assigned.values() if len(group)>1 for o in group}
        proved=[[pos,kind,sample,exact] for pos,(kind,sample,exact) in sorted(filtered.items()) if pos not in collisions]
        unmatched=sum(len(quads) for _,quads in available.values())-sum(
            len(indices) for _,_,_,faces in proved for _,_,indices in faces)
        print(dim,'unclassified UV cells',len(available),'complete additional roots',len(proved),
              'unattributed atlas quads',unmatched,flush=True)
        out[dim]={'instances':proved,'unattributed_atlas_quads':unmatched,'atlas_cells':len(available)}
    (BASE/'additional-bush-stencils.json').write_text(json.dumps(out,indent=1),encoding='utf-8')


if __name__=='__main__':
    main()
