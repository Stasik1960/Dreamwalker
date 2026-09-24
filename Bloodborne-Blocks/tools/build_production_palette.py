"""Compile the reconciled production palette, not carriers, from frozen reviewed input.

Default is a staging/dry run. --apply publishes the validated, explicit file plan.
Original world/pack and historical review documents are never modified.
"""
from __future__ import annotations
import argparse, copy, gzip, hashlib, json, shutil, zipfile
from collections import Counter
from pathlib import Path
from build_visual_slots import build as visual_slots, key, write
from sync_reviewed_geometry import profile
from production_door import compile_c282

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
LOGICAL=RES/'bloodborne_blocks/logical'
FROZEN=ROOT/'docs/production-authoring-inputs.json.gz'
STAGE=ROOT/'build/production-resource-stage'
FACINGS=('north','east','south','west')

def semantic_label(identifier,locale):
    """Latest reviewed meaning supersedes the source-carrier-era item names."""
    labels={
        'o_c1491':('Grave','Могила'),
        'o_c1962':('Sack','Мешок'),
        'o_c1979':('Reviewed composition','Композиция по разметке'),
        'o_c471':('Spire','Шпиль'),
        'o_c282':('Collective double door','Двустворчатая дверь'),
    }
    for prefix,translations in labels.items():
        if identifier==prefix or identifier.startswith(prefix+'_'):
            base=translations[1 if locale=='ru_ru' else 0]
            suffix=identifier[len(prefix):].strip('_').upper()
            return base+(' — '+suffix if suffix else '')
    return None

def read(path):
    return json.loads(gzip.decompress(path.read_bytes()) if path.suffix=='.gz' else path.read_bytes())

def gzwrite(path,value):
    raw=(json.dumps(value,sort_keys=True,separators=(',',':'),ensure_ascii=False)+'\n').encode()
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(gzip.compress(raw,mtime=0))

def freeze():
    if FROZEN.exists():return read(FROZEN)
    if (LOGICAL/'production-palette.json').exists():raise ValueError('missing frozen reviewed input')
    data={'baseline_commit':'62346a93595a1ce8fa1bc453658e1d0bebe0f6a0',
          'definitions':read(LOGICAL/'definitions.json'), 'contracts':read(LOGICAL/'contracts-v2.json'),
          'meshes':read(LOGICAL/'meshes.json.gz'), 'geometry':read(LOGICAL/'geometry.json'),
          'hidden':read(LOGICAL/'hidden-items.json'), 'migration':read(LOGICAL/'migration.json'),
          'old_migrations':read(LOGICAL/'old-logical-migrations.json'),
          'languages':{f.stem:read(f) for f in (RES/'assets/bloodborne_blocks/lang').glob('*.json')},
          'items':{b['id']:read(RES/f"assets/bloodborne_blocks/models/item/{b['id']}.json") for b in read(LOGICAL/'definitions.json')['blocks']}}
    # Freeze just source mapping (not modular meshes/geometry) outside runtime.
    mapping={str(p.relative_to(RES/'bloodborne_blocks')):read(p) for p in [
        RES/'bloodborne_blocks/definitions.json', RES/'bloodborne_blocks/v2/definitions.json',
        RES/'bloodborne_blocks/v2/sources.json',RES/'bloodborne_blocks/v2/migration.json']}
    gzwrite(ROOT/'docs/pre-production-source-mapping.json.gz',mapping)
    gzwrite(FROZEN,data)
    return data

def remap_output(output):
    value=copy.deepcopy(output)
    if value['family']=='o_c008_4':value['family']='o_c008_2'
    return value

def assemble(data):
    defs={b['id']:copy.deepcopy(b) for b in data['definitions']['blocks']}
    families={f['id']:copy.deepcopy(f) for f in data['contracts']['families']}
    # Earlier POC/manual approvals remain, except explicit later supersession.
    selected=set(families)-set(data['hidden'])-{'o_c282_a','o_c282_b','o_c008_4'}
    selected.add('o_c282')
    meshes=copy.deepcopy(data['meshes'])
    from nightmare_geometry_recipes import tagged_polys
    c282=families['o_c282']
    apps=c282['review_source_patterns'][0]['components'][0]['source_apps']
    door=compile_c282(defs['o_c282'],c282,tagged_polys(apps))
    # Raw source root is one block above the floor origin of the authored panel.
    # Preserve raw positions, move the target origin down one instead of shifting art up.
    for state in door['contract']['states'].values():
        for pattern in state['migration_source_pattern']:
            for part in pattern['components']:part['offset'][1]+=1
            for guard in pattern.get('variant_guards',[]):guard['offset'][1]+=1
            pattern['master_from_review_origin']=[0,-1,0]
            pattern['matching_evidence']='whole C282; source unchanged, floor master one below source carrier; outer-edge hinges'
    defs['o_c282']=door['definition'];families['o_c282']=door['contract'];meshes.update(door['meshes'])
    # Same statue at a second root: retain atomic transaction and its placement,
    # use a state override rather than a second registry family.
    for family in families.values():
        for statekey,state in family['states'].items():
            values=dict(p.split('=',1) for p in statekey.split(','))
            for pattern in state['migration_source_pattern']:
                for output in pattern.get('split_transaction',{}).get('outputs',[]):
                    if output['family']=='o_c008_4':
                        output['family']='o_c008_2'
                        output['properties']={'facing':FACINGS[(FACINGS.index(values['facing'])-1)%4]}
    blocks=[defs[i] for i in sorted(selected)]
    contract={**data['contracts'],'families':[families[i] for i in sorted(selected)]}
    for block in blocks:block['creative']=True
    return blocks,contract,meshes,door['evidence']

def texture_resource(name):
    ns,path=name.split(':',1)
    return f'assets/{ns}/textures/{path}.png'

def textures_for_stage(meshes):
    """Share only byte-identical PNG + animation metadata, never similar pixels."""
    old_aliases={}
    prior=ROOT/'docs/production-resource-pruning.json'
    if prior.exists():old_aliases=read(prior).get('texture_aliases',{})
    by_content={}; aliases={}; files={}
    pack=zipfile.ZipFile(ROOT/'reference-inputs/source-resource-pack.zip')
    for texture in sorted({p['texture'] for m in meshes.values() for p in m['polygons']}):
        source=old_aliases.get(texture,texture)
        if source != texture and not (RES/texture_resource(source)).is_file():
            source=texture  # Previous report may describe a dry run, not applied files.
        if source.startswith('minecraft:'):
            name=texture_resource(source)
            if name not in pack.namelist():aliases[texture]=source;continue
            payload=pack.read(name);extra=pack.read(name+'.mcmeta') if name+'.mcmeta' in pack.namelist() else b''
            source='bloodborne_blocks:block/production-source/'+source.split(':',1)[1].replace('/','-')
        else:
            path=RES/texture_resource(source)
            if not path.is_file():raise ValueError('missing reviewed texture '+texture)
            meta=path.with_suffix('.png.mcmeta'); payload=path.read_bytes();extra=meta.read_bytes() if meta.exists() else b''
        h=hashlib.sha256(payload+b'\0'+extra).hexdigest()
        canonical=by_content.setdefault(h,source);aliases[texture]=canonical
        files[texture_resource(canonical)]=payload
        if extra:files[texture_resource(canonical)+'.mcmeta']=extra
    pack.close()
    return aliases,files

def manifest(data,blocks,contracts,door):
    usage=read(ROOT/'docs/production-source-usage.json.gz')
    summary=read(ROOT/'docs/production-source-usage-summary.json')
    families={f['id']:f for f in contracts['families']}; keep=set(families)
    patterns=usage['exact_reviewed_patterns']
    objects=[]
    for b in blocks:
        f=families[b['id']];review=f.get('review_id')
        reviews=['C001','C009'] if b['id']=='o_c001' else [review] if review else ['POC']
        evidence=[p for p in patterns if any(t['id']==b['id'] or (b['id']=='o_c008_2' and t['id']=='o_c008_4') for t in p['targets'])]
        label=data['languages']['en_us'].get('block.bloodborne_blocks.'+b['id'],b['id'])
        label=semantic_label(b['id'],'en_us') or label
        objects.append({'id':b['id'],'status':'PRODUCTION','semantic_label':label+' ['+b['id']+']',
            'source_reviews':reviews,'provenance':['latest user corrections','NightmareRunning QA','reviewed batch-02' if review else 'approved Contract V2 POC'],
            'placement_policy':f['placement_policy'],'collision_policy':f['collision_policy'],
            'source_patterns':[{'signature':p['signature'],'count':p['count'],'origins':p['origins'],'raw':p['pattern']} for p in evidence],
            'source_occurrences':sum(p['count'] for p in evidence),
            'compiled_source_patterns':[{'target_state':k,'pattern':p} for k,s in f['states'].items() for p in s['migration_source_pattern']],
            'occurrence_scope':'exact source matches before placement-conflict checks, not converted count',
            'model_variant_relationship':b['properties'],'visual':{'base':'authored baked mesh','alt':'BASE fallback; same gameplay'},
            'migration_target':b['id'],'migration_policy':'exact raw source + guards; atomic split; fail closed on foreign occupancy'})
    old_splits={'o_c008','o_c1491','o_c1962','o_c1979','o_c471','o_c654'}
    fragments={'o_c001_a','o_c001_b','o_c009_a','o_c009_b','o_c282_a','o_c282_b'}
    exclusions=[]
    for b in data['definitions']['blocks']:
        i=b['id']
        if i in keep:continue
        reason='REMOVED_DUPLICATE' if i=='o_c008_4' else 'REMOVED_FRAGMENT' if i in fragments else 'REMOVED_SUPERSEDED' if i in old_splits else 'REMOVED_COMPATIBILITY' if i in data['hidden'] else 'UNRESOLVED'
        exclusions.append({'id':i,'status':reason,'reason':'not independently approved after current corrections; retain evidence offline, no runtime ID' if reason=='UNRESOLVED' else 'latest manual semantic correction supersedes historical QA representation'})
    return {'schemaVersion':1,'authority':'latest user corrections; no authoritative modded world requires compatibility',
        'source_world_sha256':summary['source_sha256'],'source_pack_sha256':summary['pack_sha256'],
        'source_scan':summary,'objects':objects,'excluded':exclusions,'c282':door,
        'dedup':[{'removed':'o_c008_4','canonical':'o_c008_2','facing_delta_degrees':-90,'max_textured_vertex_error':0.0000010000000000287557,
                 'proof':'66 same-winding textured polygons, cyclic vertex starts; collision/selection/placement also yaw-equivalent'}],
        'source_only_registry_categories':{'legacy_carriers':503,'modular_fragments':46236,'reason':'source mapping is evidence, not approved independent objects'}}

def build(apply=False):
    from manual_review_world import EXPECTED_SHA256, EXPECTED_PACK_SHA256
    for name,expected in [('source-world.zip',EXPECTED_SHA256),('source-resource-pack.zip',EXPECTED_PACK_SHA256)]:
        if hashlib.sha256((ROOT/'reference-inputs'/name).read_bytes()).hexdigest()!=expected:
            raise ValueError('incorrect authoritative input '+name)
    data=freeze();blocks,contracts,all_meshes,door=assemble(data)
    referenced={m for b in blocks for m in b['models'].values()}
    meshes={i:all_meshes[i] for i in sorted(referenced)}
    # Normalize provenance-free exact mesh identities; do not approximate geometry.
    texture_aliases,texturefiles=textures_for_stage(meshes)
    metadata=read(ROOT/'docs/pre-production-source-mapping.json.gz')['definitions.json']
    emissive={}
    used_textures=set(texture_aliases.values())
    with zipfile.ZipFile(ROOT/'reference-inputs/source-resource-pack.zip') as pack:
        for base,glow in metadata.get('emissive_textures',{}).items():
            base=texture_aliases.get(base,base)
            if base not in used_textures:continue
            emissive[base]=glow
            path=texture_resource(glow)
            source_path=path.replace('assets/bloodborne_blocks/','assets/minecraft/',1)
            if path not in texturefiles:
                texturefiles[path]=pack.read(source_path)
                if source_path+'.mcmeta' in pack.namelist():texturefiles[path+'.mcmeta']=pack.read(source_path+'.mcmeta')
    unique={};mesh_aliases={};canonical={}
    for ident,mesh in meshes.items():
        body={'polygons':[{'texture':texture_aliases[p['texture']],'vertices':p['vertices']} for p in mesh['polygons']]}
        h=hashlib.sha256(json.dumps(body,sort_keys=True,separators=(',',':')).encode()).hexdigest()
        target=unique.setdefault(h,ident);mesh_aliases[ident]=target;canonical[target]=body
    for b in blocks:b['models']={k:mesh_aliases[v] for k,v in b['models'].items()}
    for f in contracts['families']:
        for s in f['states'].values():s['render_mesh']['id']=mesh_aliases[s['render_mesh']['id']]
    # Fresh staging directory, never a world or workspace root.
    if STAGE.exists():
        if STAGE.resolve()!= (ROOT/'build/production-resource-stage').resolve():raise ValueError('unsafe stage')
        shutil.rmtree(STAGE)
    logical=STAGE/'bloodborne_blocks/logical'
    write(logical/'definitions.json',{'blocks':blocks,'emissive_textures':emissive,'compat_layers':{},'shapes':[]});write(logical/'contracts-v2.json',contracts)
    write(logical/'geometry.json',{'blocks':{f['id']:profile(f) for f in contracts['families']},'profiles':{}})
    gzwrite(logical/'meshes.json.gz',canonical)
    write(logical/'hidden-items.json',[])
    write(logical/'migration.json',{'schemaVersion':1,'rules':[],'item_aliases':{}})
    write(logical/'old-logical-migrations.json',{'schemaVersion':1,'rules':[]})
    write(STAGE/'bloodborne_blocks/aliases.json',{'removed':[],'aliases':{}})
    shutil.copyfile(LOGICAL/'transform-v2.json',logical/'transform-v2.json')
    for b in blocks:
        ident=b['id'];write(STAGE/f'assets/bloodborne_blocks/models/item/{ident}.json',data['items'][ident])
        write(STAGE/f'data/bloodborne_blocks/loot_tables/blocks/{ident}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'bloodborne_blocks:'+ident}]}]})
    visual_slots(STAGE)
    # Stitch emissive companions through explicit model texture slots.
    for model_path in (STAGE/'assets/bloodborne_blocks/models/block/logical').rglob('*.json'):
        model=read(model_path)
        if 'bloodborne_mesh' not in model:continue
        for index,(base,glow) in enumerate(emissive.items()):
            if base in model['textures'].values():model['textures']['emissive_'+str(index)]=glow
        write(model_path,model)
    # PART is the only internal registered block, no item.
    for relative in ('assets/bloodborne_blocks/blockstates/architecture_part.json','assets/bloodborne_blocks/models/block/architecture_part.json'):
        if (RES/relative).exists():
            target=STAGE/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(RES/relative,target)
    for path,payload in texturefiles.items():
        target=STAGE/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(payload)
    ids={b['id'] for b in blocks}
    for tag_path in (RES/'data/minecraft/tags/blocks').rglob('*.json'):
        tag=read(tag_path)
        def keep_entry(entry):
            name=entry if isinstance(entry,str) else entry['id']
            return not name.startswith('bloodborne_blocks:') or name.split(':',1)[1] in ids
        tag['values']=[entry for entry in tag['values'] if keep_entry(entry)]
        write(STAGE/tag_path.relative_to(RES),tag)
    for locale,language in data['languages'].items():
        filtered={k:v for k,v in language.items() if not k.startswith(('block.bloodborne_blocks.','item.bloodborne_blocks.')) or k.rsplit('.',1)[-1] in ids}
        for ident in ids:
            label=semantic_label(ident,locale)
            if label:filtered['block.bloodborne_blocks.'+ident]=label
        write(STAGE/f'assets/bloodborne_blocks/lang/{locale}.json',filtered)
    final=manifest(data,blocks,contracts,door)
    write(logical/'production-palette.json',{'schemaVersion':1,'objects':[{k:o[k] for k in ('id','semantic_label','source_reviews')} for o in final['objects']]})
    # Validate before the first runtime deletion.
    from logical_contract_v2 import load_contracts
    load_contracts(logical)
    managed=[RES/'assets/bloodborne_blocks',RES/'data/bloodborne_blocks',RES/'bloodborne_blocks',RES/'data/minecraft/tags/blocks']
    existing={str(p.relative_to(RES)) for root in managed for p in root.rglob('*') if p.is_file()}
    staged={str(p.relative_to(STAGE)) for p in STAGE.rglob('*') if p.is_file()}
    # Preserve non-model, non-generated assets (icon, sounds, etc.).
    removable={p for p in existing if p.startswith(('bloodborne_blocks\\','bloodborne_blocks/')) or
               any(x in p.replace('\\','/') for x in ('/models/','/blockstates/','/loot_tables/','/textures/','/lang/','/tags/'))}
    obsolete=sorted(removable-staged)
    plan={'schemaVersion':1,'objects':len(blocks),'staged_files':len(staged),'removed_files':len(obsolete),
          'removed_paths':obsolete,'mesh_aliases':{k:v for k,v in mesh_aliases.items() if k!=v},
          'texture_aliases':{k:v for k,v in texture_aliases.items() if k!=v},
          'deletion_boundary':'only generated resources under project src/main/resources; originals immutable'}
    write(ROOT/'docs/production-logical-palette.json',final)
    ledger=ROOT/'docs/production-resource-pruning.json'
    if ledger.exists():
        previous=read(ledger)
        plan['initial_removed_resource_files']=previous.get('initial_removed_resource_files',previous['removed_files'])
        plan['texture_aliases']={**previous.get('texture_aliases',{}),**plan['texture_aliases']}
    else:plan['initial_removed_resource_files']=len(obsolete)
    write(ledger,plan)
    if apply:
        for relative in obsolete:
            path=(RES/relative).resolve()
            if not any(path.is_relative_to(root.resolve()) for root in managed):raise ValueError('unsafe deletion '+str(path))
            path.unlink()
        for relative in sorted(staged):
            target=RES/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(STAGE/relative,target)
    print(json.dumps({'production':len(blocks),'mesh_count':len(canonical),'removed_resource_files':len(obsolete),'applied':apply}))
    return final

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--apply',action='store_true');build(parser.parse_args().apply)
