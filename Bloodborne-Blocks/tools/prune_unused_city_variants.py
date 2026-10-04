"""Remove private variants using complete world usage and exact art/physics proofs.

Public IDs, defaults, four yaws, native functionality and document references
survive. All changed resource tables are staged before modifying live sources.
"""
from __future__ import annotations
import argparse, copy, gzip, hashlib, json, shutil
from collections import defaultdict
from pathlib import Path
from build_unified_registry import stream_members, serial

FACINGS=('north','east','south','west');NS='bloodborne_blocks:'
def load(path):return json.loads(Path(path).read_bytes())
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def digest_or_missing(path):return sha(path)if Path(path).is_file()else None
def input_name(root,path):
    path=Path(path).resolve()
    try:return path.relative_to(Path(root).resolve()).as_posix()
    except ValueError:return path.as_posix()
def verify_inputs(root,inputs):
    for relative,digest in inputs.items():
        if digest_or_missing(Path(root)/relative)!=digest:raise ValueError('input changed: '+relative)
def token(value):return hashlib.sha256(serial(value)).hexdigest()
def write(path,value):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(serial(value)+b'\n')
def props(text):
    if '['in text:text=text.split('[',1)[1].rstrip(']')
    return dict(p.split('=',1)for p in text.split(',')if '='in p)
def key(values):return ','.join(k+'='+str(v)for k,v in sorted(values.items()))
def full(ident,state):return NS+ident+'['+state+']'

def document_variants(value):
    result=defaultdict(set)
    def walk(item):
        if isinstance(item,dict):
            ident=item.get('id','').split(':')[-1]
            if ident:
                state=item.get('properties',{})
                if 'variant'in state:result[ident].add(str(state['variant']))
                for candidate in item.get('candidates',[]):
                    state=props(candidate)
                    if 'variant'in state:result[ident].add(state['variant'])
            for child in item.values():walk(child)
        elif isinstance(item,list):
            for child in item:walk(child)
    walk(value);return result

def signatures(definition,geometry,profiles,mesh_hashes,geometry_hashes):
    variants=defaultdict(list)
    for state,values in definition['states'].items():
        p=props(state);variant=p.pop('variant');physical=geometry['states'][state]
        if 'ref'in physical:
            ref=physical['ref']
            if ref not in geometry_hashes:geometry_hashes[ref]=token(profiles[ref])
            physical_hash=geometry_hashes[ref]
        else:physical_hash=token(physical)
        variants[variant].append((key(p),mesh_hashes[definition['models'][state]],physical_hash,values))
    for variant,rows in variants.items():
        if {props(r[0]).get('facing')for r in rows}!=set(FACINGS):raise ValueError('incomplete rotation coverage')
    return {v:token(sorted(rows,key=lambda row:row[0]))for v,rows in variants.items()}

def build_plan(root,usage_path,mesh_hashes=None):
    root=Path(root);city=root/'src/main/resources/bloodborne_blocks/city'
    data=load(city/'definitions.json');geometry=load(city/'geometry.json');usage=load(usage_path)
    if usage.get('variantAudit',{}).get('definitionsSha256')!=sha(city/'definitions.json'):raise ValueError('usage definition hash mismatch')
    counts=usage.get('inventory',{}).get('blocks',{}).get('stateCounts')
    if not isinstance(counts,dict)or not counts:raise ValueError('complete packed-world stateCounts required')
    persisted_path=usage.get('persistedAudit')
    if not persisted_path:raise ValueError('complete persisted NBT audit required')
    persisted_path=root/persisted_path
    persisted=load(persisted_path)
    if persisted.get('complete')is not True:raise ValueError('incomplete persisted NBT audit')
    verify_inputs(root,persisted.get('hashInputs',{}))
    for relative,digest in usage.get('hashInputMap',{}).items():
        if sha(root/relative.replace('\\','/'))!=digest:raise ValueError('usage input changed: '+relative)
    used=defaultdict(set);actual=defaultdict(set)
    for text,count in counts.items():
        if not text.startswith(NS)or count<=0:continue
        ident=text[len(NS):].split('[',1)[0];p=props(text);actual[ident].add(key(p))
        if 'variant'in p:used[ident].add(p['variant'])
    for ident,variants in persisted.get('variantReferences',{}).items():
        if ident.startswith(NS):used[ident[len(NS):]].update(str(v)for v in variants)
    manual=document_variants(load(city/'document-final.json'))
    owner_mappings=city/'owner-runtime-mappings.json'
    if owner_mappings.is_file():
        for ident,values in document_variants(load(owner_mappings)).items():manual[ident].update(values)
    if mesh_hashes is None:
        mesh_hashes={}
        for name in ('meshes.json.gz','owner-meshes.json.gz'):
            for ident,mesh in stream_members(city/name):mesh_hashes[ident]=token(mesh)
    geometry_hashes={};rows=[];before=sum(len(d['states'])for d in data['blocks']);after=before
    for d in data['blocks']:
        if not d.get('unified')or d.get('document_item')or d.get('behavior')not in(None,'static'):continue
        ident=d['id'];preserve=used[ident]|manual[ident]|{d['default']['variant'],d.get('placement_properties',{}).get('variant','0')}
        groups=defaultdict(list)
        for variant,digest in signatures(d,geometry['blocks'][ident],geometry.get('profiles',{}),mesh_hashes,geometry_hashes).items():groups[digest].append(variant)
        replacements={};duplicate_sources=set()
        for group in groups.values():
            ordered=sorted(group,key=lambda v:(v!=d['default']['variant'],v not in preserve,int(v)))
            for variant in ordered[1:]:
                if variant in manual[ident]:continue
                replacements[variant]=ordered[0];duplicate_sources.add(variant)
        for variant in d['properties']['variant']:
            if variant not in preserve and variant not in replacements:replacements[variant]=d['default']['variant']
        if not replacements:continue
        for variant in replacements:
            target=replacements[variant];seen={variant}
            while target in replacements:
                if target in seen:raise ValueError('cyclic replacement')
                seen.add(target);target=replacements[target]
            replacements[variant]=target
        removed_states=sum(props(s)['variant']in replacements for s in d['states']);after-=removed_states
        rows.append({'id':ident,'removed':replacements,'exactDuplicateSources':sorted(duplicate_sources,key=int),
            'kept':[v for v in d['properties']['variant']if v not in replacements],
            'usedVariants':len(used[ident]),'removedStates':removed_states})
    byid={d['id']:d for d in data['blocks']};byrow={r['id']:r for r in rows}
    for ident,states in actual.items():
        if ident not in byid:continue
        d=byid[ident];row=byrow.get(ident)
        for state in states:
            p={**d['default'],**props(state)}
            if key(p)not in d['states']:raise ValueError('real state missing from source: '+full(ident,key(p)))
            if row and p.get('variant')in row['removed'] and p['variant']not in row['exactDuplicateSources']:raise ValueError('attempted deletion of used city form')
    inputs={str(p.relative_to(root)).replace('\\','/'):sha(p)for p in[city/n for n in('definitions.json','geometry.json','document-final.json','unified-migration.json','meshes.json.gz','owner-meshes.json.gz')]}
    inputs[input_name(root,owner_mappings)]=digest_or_missing(owner_mappings)
    inputs.update({relative.replace('\\','/'):digest for relative,digest in usage.get('hashInputMap',{}).items()})
    inputs.update(persisted.get('hashInputs',{}))
    for path in(usage_path,persisted_path):inputs[input_name(root,path)]=sha(path)
    # Read and destination assets need preimage guards too, including absence.
    for row in rows:
        for path in(root/f'src/main/resources/assets/bloodborne_blocks/blockstates/{row["id"]}.json',
                     root/f'src/main/resources/assets/bloodborne_blocks/models/block/city/{row["id"]}.json'):
            inputs[input_name(root,path)]=digest_or_missing(path)
    inputs[input_name(root,city/'variant-pruning-migration.json')]=digest_or_missing(city/'variant-pruning-migration.json')
    return {'schemaVersion':1,'hashInputs':inputs,'usageSha256':sha(usage_path),'counts':{'cityIds':len(data['blocks']),
        'statesBefore':before,'statesAfter':after,'removedVariants':sum(len(r['removed'])for r in rows),
        'exactDuplicateVariants':sum(len(r['exactDuplicateSources'])for r in rows),
        'usedVariantRemaps':sum(len(set(r['removed'])&used[r['id']])for r in rows),'affectedIds':len(rows)},'blocks':rows,
        'policy':'Keep public IDs, defaults, all four yaws, manual references and all nonduplicate city forms. Unused private states fall back to the canonical default.'}

def stage_plan(root,plan):
    root=Path(root);resources=root/'src/main/resources';city=resources/'bloodborne_blocks/city'
    verify_inputs(root,plan['hashInputs'])
    stage=root/'build/variant-pruned-stage'
    if stage.exists():
        if stage.resolve().parent!=(root/'build').resolve():raise ValueError('unsafe stage path')
        shutil.rmtree(stage)
    stage.mkdir(parents=True);target=stage/'resources/bloodborne_blocks/city';target.mkdir(parents=True)
    data=load(city/'definitions.json');geometry=load(city/'geometry.json');byrow={r['id']:r for r in plan['blocks']};migration={}
    for d in data['blocks']:
        ident=d['id'];row=byrow.get(ident)
        if row is None:continue
        old_states=copy.deepcopy(d['states']);d['properties']['variant']=row['kept']
        for state in old_states:
            p=props(state);v=p['variant']
            if v not in row['removed']:continue
            p['variant']=row['removed'][v]
            if key(p)not in old_states:raise ValueError('replacement state missing')
            migration[full(ident,state)]={'id':NS+ident,'properties':p,'rootOffset':[0,0,0],
                'reason':'exact duplicate'if v in row['exactDuplicateSources']else'unused private variant'}
        for field in ('states','models','visual_models'):
            if isinstance(d.get(field),dict):d[field]={s:v for s,v in d[field].items()if props(s).get('variant')not in row['removed']}
        geometry['blocks'][ident]['states']={s:v for s,v in geometry['blocks'][ident]['states'].items()if s in d['states']}
        asset=resources/f'assets/bloodborne_blocks/blockstates/{ident}.json';blockstate=load(asset);variants={}
        for state,value in blockstate['variants'].items():
            p={**d['default'],**props(state)}
            if key(p)not in d['states']:continue
            variants[key({k:v for k,v in p.items()if len(d['properties'][k])>1})]=value
        blockstate['variants']=variants;write(stage/'resources'/asset.relative_to(resources),blockstate)
    referenced={g['ref']for block in geometry['blocks'].values()for g in block['states'].values()if 'ref'in g}
    geometry['profiles']={k:v for k,v in geometry.get('profiles',{}).items()if k in referenced}
    needed={mid for d in data['blocks']for mid in d.get('models',{}).values()}
    needed.update(mid for d in data['blocks']for mid in d.get('visual_models',{}).values());emitted=set()
    mesh_owners=defaultdict(set);owner_textures=defaultdict(set)
    for d in data['blocks']:
        if d['id']in byrow:
            for mid in d.get('models',{}).values():mesh_owners[mid].add(d['id'])
    for name in ('meshes.json.gz','owner-meshes.json.gz'):
        with (target/name).open('wb')as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0)as out:
            out.write(b'{');first=True
            for ident,mesh in stream_members(city/name):
                if ident not in needed:continue
                for owner in mesh_owners.get(ident,()):owner_textures[owner].update(p['texture']for p in mesh['polygons'])
                if not first:out.write(b',')
                first=False;out.write(serial(ident)+b':'+serial(mesh));emitted.add(ident)
            out.write(b'}\n')
    if needed-emitted:raise ValueError('mesh closure missing')
    for ident,textures in owner_textures.items():
        path=resources/f'assets/bloodborne_blocks/models/block/city/{ident}.json'
        if not path.is_file():continue
        model=load(path);particle=model.get('textures',{}).get('particle')
        model['textures']={'particle':particle or sorted(textures)[0],**{str(i):texture for i,texture in enumerate(sorted(textures))}}
        write(stage/'resources'/path.relative_to(resources),model)
    original=load(city/'unified-migration.json')
    for source,value in original['states'].items():
        destination=full(value['id'].split(':')[-1],key(value['properties']))
        if destination in migration:value.update(migration[destination])
    write(target/'definitions.json',data);write(target/'geometry.json',geometry);write(target/'unified-migration.json',original)
    write(target/'variant-pruning-migration.json',{'schemaVersion':1,'states':migration,'counts':plan['counts'],'policy':plan['policy']})
    write(stage/'plan.json',plan);write(stage/'files.json',{str(p.relative_to(stage/'resources')).replace('\\','/'):sha(p)for p in(stage/'resources').rglob('*')if p.is_file()})
    return stage

def apply_stage(root,stage):
    root=Path(root);stage=Path(stage);plan=load(stage/'plan.json')
    verify_inputs(root,plan['hashInputs'])
    files=load(stage/'files.json')
    for relative,digest in files.items():
        if sha(stage/'resources'/relative)!=digest:raise ValueError('staged file changed')
    for relative in files:
        destination=root/'src/main/resources'/relative;destination.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(stage/'resources'/relative,destination)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1]);parser.add_argument('--usage',type=Path);parser.add_argument('--stage',action='store_true');parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    root=args.root.resolve();usage=args.usage or root/'build/variant-usage-2026-10-04.json'
    if args.apply:apply_stage(root,root/'build/variant-pruned-stage');print('APPLIED VERIFIED VARIANT STAGE');return
    plan=build_plan(root,usage);write(root/'build/variant-pruning-plan.json',plan);print(json.dumps(plan['counts']),flush=True)
    if args.stage:print(stage_plan(root,plan),flush=True)
if __name__=='__main__':main()
