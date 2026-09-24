"""Read-only full-Yharnam usage graph. Spatial evidence never grants semantic approval.

All source positions are retained in an ignored numpy sidecar. Compact graph,
exact reviewed occurrences and positional model choices are published separately.
No registry, candidate ID, source archive or Minecraft world is written.
"""
from __future__ import annotations
import argparse, gzip, hashlib, itertools, json, zipfile
from collections import Counter, defaultdict
from pathlib import Path
import numpy as np
from source_assembly_index import _resource_carriers, _pack_evidence, _state, sha256
from source_variant_rng import resolve_choices, guards_match
from manual_review_world import DEFAULT_SOURCEPACK, DEFAULT_VANILLA, EXPECTED_SHA256, EXPECTED_PACK_SHA256, _load_blockstates
from world_io import RegionFile, compound, child, TAG_COMPOUND, TAG_LIST, TAG_LONG_ARRAY, unpack_palette_indices

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'build/production-usage'
DOC=ROOT/'docs/production-source-usage.json.gz'
BIAS=1<<20

def dump(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    data=(json.dumps(value,ensure_ascii=False,separators=(',',':'),sort_keys=True)+'\n').encode()
    path.write_bytes(gzip.compress(data,mtime=0) if path.suffix=='.gz' else data)

def encode(xyz):
    xyz=np.asarray(xyz,dtype=np.int64)
    if (np.any(np.abs(xyz[:,0])>=BIAS) or np.any(np.abs(xyz[:,2])>=BIAS)
            or np.any(xyz[:,1]<-2048) or np.any(xyz[:,1]>2047)):
        raise ValueError('coordinate outside exact analysis encoding')
    return ((xyz[:,0]+BIAS)<<33)|((xyz[:,2]+BIAS)<<12)|(xyz[:,1]+2048)

def weighted_indices(weights,xyz,multipart=False):
    """Vectorized equivalent of verified Java 1.20.1 positional RNG."""
    xyz=np.asarray(xyz,dtype=np.int64)
    x=(xyz[:,0]*3129871).astype(np.int32).astype(np.int64)
    with np.errstate(over='ignore'):
        v=x^(xyz[:,2]*116129781)^xyz[:,1]
        seed=(v*v*42317861+v*11)>>16
    def nextlong(s):
        mask=np.uint64((1<<48)-1)
        s=(s.astype(np.uint64)^np.uint64(25214903917))&mask
        s=(s*np.uint64(25214903917)+np.uint64(11))&mask
        hi=(s>>np.uint64(16)).astype(np.uint32).view(np.int32).astype(np.int64)
        s=(s*np.uint64(25214903917)+np.uint64(11))&mask
        lo=(s>>np.uint64(16)).astype(np.uint32).view(np.int32).astype(np.int64)
        return (hi<<32)+lo
    if multipart:seed=nextlong(seed)
    value=nextlong(seed).astype(np.int32).astype(np.int64)
    ticket=np.abs(value)%sum(weights)
    ticket[value==-(1<<31)]=0
    return np.searchsorted(np.cumsum(weights),ticket,side='right')

def scan(source,out):
    if sha256(source)!=EXPECTED_SHA256 or sha256(DEFAULT_SOURCEPACK)!=EXPECTED_PACK_SHA256:
        raise ValueError('wrong authoritative source archives')
    evidence=_pack_evidence(DEFAULT_SOURCEPACK,DEFAULT_VANILLA,_resource_carriers(ROOT))
    carriers=_resource_carriers(ROOT)|set(evidence)
    state_numbers={};states=[];coords=[];numbers=[];regions=[];chunks=0
    with zipfile.ZipFile(source) as archive:
        names=sorted(n for n in archive.namelist() if n.startswith('ether/dimensions/eh_s2/yharnam/region/') and n.endswith('.mca'))
        for index,name in enumerate(names,1):
            print(f'Full source scan {index}/{len(names)} {name}',flush=True)
            regions.append(name)
            for stored in RegionFile(archive.read(name)).chunks():
                data=compound(stored.nbt().root);cx=int(data['xPos'].value);cz=int(data['zPos'].value);chunks+=1
                for section in data.get('sections',data.get('Sections')).value:
                    sec=compound(section);blockstates=child(section,'block_states',TAG_COMPOUND)
                    if not blockstates:continue
                    palette=child(blockstates,'palette',TAG_LIST)
                    if not palette:continue
                    mapping=np.full(len(palette.value),-1,dtype=np.int32)
                    for i,entry in enumerate(palette.value):
                        key,descriptor=_state(entry)
                        if descriptor['id'] not in carriers:continue
                        if key not in state_numbers:
                            state_numbers[key]=len(states);states.append({'key':key,**descriptor})
                        mapping[i]=state_numbers[key]
                    if np.all(mapping<0):continue
                    packed=child(blockstates,'data',TAG_LONG_ARRAY)
                    values=np.asarray(unpack_palette_indices(packed.value if packed else [],len(mapping)),dtype=np.int32)
                    sid=mapping[values];at=np.flatnonzero(sid>=0)
                    if not len(at):continue
                    coords.append(np.column_stack((cx*16+(at&15),int(sec['Y'].value)*16+(at>>8),cz*16+((at>>4)&15))).astype(np.int32))
                    numbers.append(sid[at].astype(np.uint16))
    xyz=np.concatenate(coords);ids=np.concatenate(numbers);del coords,numbers
    order=np.argsort(encode(xyz));xyz=xyz[order];ids=ids[order]
    keys=encode(xyz)
    if np.any(keys[1:]==keys[:-1]):raise ValueError('duplicate source cells')
    out.mkdir(parents=True,exist_ok=True)
    np.savez_compressed(out/'positions.npz',xyz=xyz,state=ids)
    meta={'source_sha256':EXPECTED_SHA256,'pack_sha256':EXPECTED_PACK_SHA256,
          'dimension':'eh_s2:yharnam','regions':regions,'chunks':chunks,'carrier_cells':len(ids),
          'states':states,'carrier_evidence':evidence,'coordinates_sha256':sha256(out/'positions.npz')}
    dump(out/'scan.json.gz',meta)
    return meta,xyz,ids

def lookup(keys,ids,query):
    at=np.searchsorted(keys,query);inside=at<len(keys);safe=np.minimum(at,len(keys)-1)
    return np.where(inside&(keys[safe]==query),ids[safe].astype(np.int32),-1)

def pattern_occurrences(meta,xyz,ids,keys):
    contracts=json.loads((ROOT/'src/main/resources/bloodborne_blocks/logical/contracts-v2.json').read_text())
    patterns={}
    for family in contracts['families']:
        for state,row in family['states'].items():
            for p in row['migration_source_pattern']:
                raw={k:p[k] for k in ('components','variant_guards') if k in p}
                signature=hashlib.sha256(json.dumps(raw,sort_keys=True).encode()).hexdigest()
                rec=patterns.setdefault(signature,{'pattern':raw,'targets':[]})
                rec['targets'].append({'id':family['id'],'state':state,'disabled':family.get('migration_disabled',False)})
    byid=defaultdict(list)
    for i,state in enumerate(meta['states']):byid[state['id']].append((i,state['properties']))
    totals=np.bincount(ids,minlength=len(meta['states']))
    result=[]
    for signature,record in patterns.items():
        p=record['pattern'];parts=p['components'];allowed=[]
        for part in parts:
            allowed.append([i for i,props in byid[part['id']] if all(props.get(k)==v for k,v in part.get('properties',{}).items())])
        if not allowed or any(not a for a in allowed): origins=np.empty((0,3),dtype=np.int32)
        else:
            rare=min(range(len(parts)),key=lambda i:sum(totals[j] for j in allowed[i]))
            origins=xyz[np.isin(ids,allowed[rare])]-np.asarray(parts[rare]['offset'])
            for part,choices in zip(parts,allowed):
                if not len(origins):break
                hit=lookup(keys,ids,encode(origins+part['offset']))
                origins=origins[np.isin(hit,choices)]
            if p.get('variant_guards'):
                origins=np.asarray([p0 for p0 in origins if guards_match(p['variant_guards'],p0.tolist())],dtype=np.int32).reshape(-1,3)
        result.append({'signature':signature,**record,'count':len(origins),'origins':origins.tolist()})
    return result

def analyze(meta,xyz,ids,out):
    from source_assembly_pipeline import active_choices
    keys=encode(xyz);count=len(meta['states']);counts=np.bincount(ids,minlength=count)
    rawstates=_load_blockstates(DEFAULT_SOURCEPACK,DEFAULT_VANILLA)
    applications=[];state_rows=[]
    for sid,descriptor in enumerate(meta['states']):
        positions=xyz[ids==sid];raw=rawstates.get(descriptor['id'],{})
        groups=active_choices(raw,descriptor['properties']);weighted=[g for g in groups if len(g)>1]
        combinations=np.zeros(len(positions),dtype=np.int32);stride=1
        for group in weighted:
            selected=weighted_indices([o[0].get('weight',1) for o in group],positions,'multipart' in raw)
            combinations+=selected*stride;stride*=len(group)
        choices=[]
        for token,number in zip(*np.unique(combinations,return_counts=True)):
            at=int(np.flatnonzero(combinations==token)[0]);pos=positions[at].tolist()
            apps=resolve_choices({'source':descriptor,'model_choices':groups},pos)
            choices.append({'choice_code':int(token),'count':int(number),'apps':apps,'example':pos})
        state_rows.append({**descriptor,'count':int(counts[sid]),'examples':positions[:3].tolist(),
                           'orientation':{k:v for k,v in descriptor['properties'].items() if k in ('axis','facing','rotation','hinge')},
                           'positional_variants':choices})
    edges=[];exposed=np.zeros((count,6),dtype=np.int64)
    for direction,delta in enumerate(((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1))):
        print('Neighbor graph direction '+str(delta),flush=True)
        found=Counter()
        for start in range(0,len(ids),500000):
            local=ids[start:start+500000];query=xyz[start:start+500000]+delta
            neighbor=lookup(keys,ids,encode(query));present=neighbor>=0
            pair=local[present].astype(np.int64)*count+neighbor[present]
            values,amount=np.unique(pair,return_counts=True);found.update(dict(zip(map(int,values),map(int,amount))))
            exposed[:,direction]+=np.bincount(local[~present],minlength=count)
        edges.extend({'a':pair//count,'b':pair%count,'offset':list(delta),'count':n,
                      'conditional_frequency':round(n/int(counts[pair//count]),8)} for pair,n in sorted(found.items()))
    print('Matching existing exact reviewed source patterns',flush=True)
    patterns=pattern_occurrences(meta,xyz,ids,keys)
    result={k:v for k,v in meta.items() if k not in ('states','carrier_evidence')}
    result.update({'format':'production-usage-evidence-v1','states':state_rows,'carrier_evidence':meta['carrier_evidence'],
        'directed_neighbor_edges':edges,'carrier_boundary_faces_by_state':exposed.tolist(),'exact_reviewed_patterns':patterns,
        'interpretation':{'adjacency':'Evidence only, never an assembly or ownership decision.',
            'boundary_faces':'Carrier-grid faces without another indexed carrier, NOT proof of visible/opaque render-face exposure.',
            'coordinates':'All carrier XYZ/state IDs retained in build/production-usage/positions.npz; no sampled region scope.',
            'pattern_count':'Exact raw states plus positional RNG guards; overlapping historical patterns remain evidence, not accepted migration.',
            'semantic_authority':'Latest user corrections then manual decisions; graph and geometry never grant publication.'}})
    dump(DOC,result)
    summary={'regions':len(meta['regions']),'chunks':meta['chunks'],'carrier_cells':len(ids),'observed_states':count,
             'directed_neighbor_edges':len(edges),'reviewed_exact_patterns':len(patterns),
             'matched_patterns':sum(p['count']>0 for p in patterns),'matched_occurrences':sum(p['count'] for p in patterns),
             'source_sha256':EXPECTED_SHA256,'pack_sha256':EXPECTED_PACK_SHA256,'report_sha256':sha256(DOC)}
    dump(ROOT/'docs/production-source-usage-summary.json',summary)
    print(json.dumps(summary),flush=True)
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--reuse-scan',action='store_true');args=parser.parse_args()
    source=ROOT/'reference-inputs/source-world.zip'
    if args.reuse_scan:
        with gzip.open(OUT/'scan.json.gz','rt',encoding='utf8') as f:meta=json.load(f)
        if sha256(source)!=meta['source_sha256'] or sha256(DEFAULT_SOURCEPACK)!=meta['pack_sha256'] or sha256(OUT/'positions.npz')!=meta['coordinates_sha256']:
            raise ValueError('stale/tampered source scan')
        data=np.load(OUT/'positions.npz');xyz,ids=data['xyz'],data['state']
    else:meta,xyz,ids=scan(source,OUT)
    analyze(meta,xyz,ids,OUT)
    if sha256(source)!=EXPECTED_SHA256 or sha256(DEFAULT_SOURCEPACK)!=EXPECTED_PACK_SHA256:raise ValueError('source archives changed')

if __name__=='__main__':main()
