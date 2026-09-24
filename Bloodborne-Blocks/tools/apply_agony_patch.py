"""Explicit cumulative patch from the immutable 56-family checkpoint.

No discovery/world access or global palette regeneration. Only PATCHED families
are re-authored; every other family and its resources retain their existing data.
"""
from __future__ import annotations
import argparse, copy, gzip, hashlib, json, math
from itertools import product
from pathlib import Path
from build_visual_slots import key, state_name, write
from sync_reviewed_geometry import profile
from logical_contract_v2 import box_cells

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'; LOGICAL=RES/'bloodborne_blocks/logical'
DOC=ROOT/'docs/agony-patch'
FACINGS=('north','east','south','west')
MERGES={'o_c561':'o_c046','o_c1319':'o_books','o_c1962_a':'o_bag','o_c1962_b':'o_bag','o_bench_rotate':'o_bench'}
CONTEXT={'o_iron_railing','o_ladder_02'}
STATUES=('o_c008_1','o_c008_2','o_c008_3','o_c008_5')
PATCHED=set(MERGES)|set(MERGES.values())|CONTEXT|set(STATUES)|{'o_ladder_01','o_ladder_03','o_candles_0','o_shuttered_window'}
def read(p):
    raw=p.read_bytes();return json.loads(gzip.decompress(raw) if p.suffix=='.gz' else raw)
def props(k):return dict(p.split('=',1) for p in k.split(','))
def bounds(polys):
    vs=[v for p in polys for v in p['vertices']]
    return [min(v[i] for v in vs) for i in range(3)]+[max(v[i] for v in vs) for i in range(3)]
def point(v,yaw=0,offset=(0,0,0)):
    r=math.radians(yaw);x,z=v[0]-.5,v[2]-.5
    return [round(.5+x*math.cos(r)-z*math.sin(r)+offset[0],6),round(v[1]+offset[1],6),round(.5+x*math.sin(r)+z*math.cos(r)+offset[2],6),*v[3:]]
def rotate(polys,yaw=0,offset=(0,0,0)):
    return [{**p,'vertices':[point(v,yaw,offset) for v in p['vertices']]} for p in polys]
def box(b,yaw=0):
    vs=[point(list(v),yaw) for v in product(*[(b[i],b[i+3]) for i in range(3)])]
    return [min(v[i] for v in vs) for i in range(3)]+[max(v[i] for v in vs) for i in range(3)]
def cells(boxes):return [list(c) for c in sorted({(0,0,0)}|{c for b in boxes for c in box_cells(b)})]
def state_mesh(d,f,k,polys,meshes,collision=None,selection=None,footprint=None):
    mid='agony_'+hashlib.sha256(json.dumps(polys,sort_keys=True,separators=(',',':')).encode()).hexdigest()[:24]
    meshes[mid]={'polygons':polys};d['models'][k]=mid
    s=f['states'][k];s['render_mesh']={'id':mid,'bounds':bounds(polys),'offset':[0,0,0]}
    if collision is not None:s['collision_footprint']={'boxes':collision}
    if selection is not None:s['selection_footprint']={'boxes':selection}
    if footprint is not None:s['interaction_footprint']={'cells':footprint}

def patch_geometry(ds,fs,meshes,baseline):
    # Landing: mirror the two authored side rails including UVs, not PNG edits.
    d,f=ds['o_ladder_01'],fs['o_ladder_01'];north='facing=north,visual=base'
    source=copy.deepcopy(meshes[d['models'][north]]['polygons'])
    assert len(source)==11
    for target,origin in ((9,8),(10,7)):
        source[target]=copy.deepcopy(source[origin])
        source[target]['vertices']=[[1-v[0],*v[1:]] for v in reversed(source[target]['vertices'])]
    f['collision_policy']='SIMPLE_BOX';d['behavior']='static';d['semantic']='platform'
    f['collision_justification']='One thin standable deck at authored y=.8125; side rails render-only.'
    for k in f['states']:
        yaw=FACINGS.index(props(k)['facing'])*90
        c=[box([-.1875,.75,-1,1.1875,.8125,2],yaw)]
        pol=rotate(source,yaw)
        state_mesh(d,f,k,pol,meshes,c,[bounds(pol)],cells(c))
    # Ladder: only the actual climbing plane owns cells; no side overhang helpers.
    d,f=ds['o_ladder_03'],fs['o_ladder_03'];f['collision_policy']='SIMPLE_BOX'
    f['collision_justification']='Thin wall-adjacent climbing slab; two-cell interaction height, decorative side rails do not reserve neighboring columns.'
    for k,s in f['states'].items():
        yaw=FACINGS.index(props(k)['facing'])*90
        c=[box([0,-1,.875,1,1,1],yaw)]
        s['collision_footprint']={'boxes':c};s['selection_footprint']={'boxes':c};s['interaction_footprint']={'cells':cells(c)}
    # Explicit source element groups, in the original 52-element model order.
    groups=[list(range(4)),[4,5],[6,7],[8,9,40,41],[10,11,38,39],[12,13],
            [14,15,16,17],[18,19,32,33],[20,21,28,29],[22,23],[24,25],[26,27],
            [30,31],[34,35],[36,37],[42,43],[44,45],[46,47],[48,49,50,51]]
    assert sorted(i for g in groups for i in g)==list(range(52))
    d,f=ds['o_candles_0'],fs['o_candles_0'];k=key({**d['default'],'facing':'north','lit':'false','visual':'base'})
    source=copy.deepcopy(meshes[d['models'][k]]['polygons']);assert len(source)==104
    support=[]
    for group in groups:
        indexes=[p for e in group for p in (2*e,2*e+1)]
        minimum=min(v[1] for i in indexes for v in source[i]['vertices'])
        for i in indexes:
            for v in source[i]['vertices']:v[1]=round(v[1]-minimum,6)
        support.append({'source_elements':group,'mesh_y_shift':-minimum})
    f['collision_policy']='NONE';f['collision_justification']='Decorative candles: no large invisible collision volume.'
    f['support_groups']=support
    for k in f['states']:
        pol=rotate(source,FACINGS.index(props(k)['facing'])*90)
        state_mesh(d,f,k,pol,meshes,[],[bounds(pol)],[[0,0,0]])
    # Window: use the frame/shutter assembly, never the embedded opaque carrier.
    # Back panel 14 previously sampled an opaque atlas region: replace with the
    # reverse of transparent-aperture front panel 10. Keep authored hinge poses.
    d,f=ds['o_shuttered_window'],fs['o_shuttered_window']
    old=copy.deepcopy(f['states']);oldmodels=copy.deepcopy(d['models'])
    for k in f['states']:
        p=props(k);sourcekey=key({**p,'embedded':'false','facing':'north'})
        pol=copy.deepcopy(meshes[oldmodels[sourcekey]]['polygons']);assert len(pol)==15
        back=copy.deepcopy(pol[10]);back['vertices']=[[*v[:2],.25,*v[3:]] for v in reversed(back['vertices'])];pol[14]=back
        yaw=FACINGS.index(p['facing'])*90;pol=rotate(pol,yaw)
        c=copy.deepcopy(old[key({**p,'embedded':'false'})]['collision_footprint']['boxes'])
        # Reserve aperture plane in closed/open alike, plus open wings; in-wall
        # replacement only changes the clicked block, all other cells must be air.
        plane=box([-.625,-.875,0,1.6875,1.5,1],yaw)
        state_mesh(d,f,k,pol,meshes,c,[bounds(pol)],cells([plane,*c]))
    f['placement_notes']='Safe clicked solid-cell replacement only; pre-existing larger aperture required wherever helpers overlap other wall cells. No BE/unbreakable replacement.'
    # Merge source-authored diagonal bench states into the one item. Preserve
    # diagonal artwork rather than assuming it is an exact 45-degree copy.
    d,f=ds['o_bench'],fs['o_bench'];d['properties']['diagonal']=['false','true'];d['default']['diagonal']='false'
    d['seat_anchors']=[[-.45,1,.337202],[.5,1,.337202],[1.45,1,.337202]]
    oldd,oldf=copy.deepcopy(d),copy.deepcopy(f);d['models']={};d['states']={};f['states']={}
    for diagonal,srcd,srcf in [('false',oldd,oldf),('true',ds['o_bench_rotate'],fs['o_bench_rotate'])]:
        for k,s in srcf['states'].items():
            nk=key({**props(k),'diagonal':diagonal})
            f['states'][nk]=copy.deepcopy(s);d['models'][nk]=srcd['models'][k];d['states'][nk]=copy.deepcopy(srcd['states'][k])
    f['orientation_evidence']='Eight baked states: source bench and source diagonal bench, four facing values each. Diagonal artwork preserved, no invented exact-equivalence claim.'
    # Each statue has an explicit hand attachment in canonical local space.
    # Positions are authored hand sockets, not mesh bounds or automatic collision.
    # Distal forearm/end-cap evidence: mesh faces 29..34, 43..48,
    # 37..42, 25..30 respectively. Smaller low-hand mounts clear the floor.
    sockets={'o_c008_1':[.8144,2.20,.30],'o_c008_2':[1.0019,1.10,.14],
             'o_c008_3':[.63,.79,-.28],'o_c008_5':[1.09,.56,.124]}
    scales={'o_c008_1':.4,'o_c008_2':.4,'o_c008_3':.35,'o_c008_5':.25}
    lamp=ds['o_lantern'];lampk=key({**lamp['default'],'facing':'north','lit':'false','visual':'base'})
    # Top suspension pivot (.5,1.125,.5); handheld scale .5, not a second block.
    lamp_pol=meshes[lamp['models'][lampk]]['polygons']
    for ident in STATUES:
        d,f=ds[ident],fs[ident];oldd,oldf=copy.deepcopy(d),copy.deepcopy(f)
        canonical=oldf['states'][key({**oldd['default'],'facing':'north','visual':'base'})]
        oldoffset=canonical['render_mesh'].get('offset',[0,0,0])
        socket=[round(sockets[ident][i]+oldoffset[i],6) for i in range(3)]
        d['properties']['hand_lantern']=['none','unlit','lit'];d['default']['hand_lantern']='none'
        d.setdefault('placement_properties',{})['hand_lantern']='none';d['attachment_item']='o_lantern'
        f['hand_lantern']={'position':socket,'rotation':[0,0,0],'scale':scales[ident],'source_pivot':[.5,1.125,.5],'facing_bound':True}
        d['states']={};d['models']={};f['states']={}
        for k,s in oldf['states'].items():
            p=props(k);yaw=FACINGS.index(p['facing'])*90
            for mode in ['none','unlit','lit']:
                nk=key({**p,'hand_lantern':mode});f['states'][nk]=copy.deepcopy(s);d['states'][nk]=copy.deepcopy(oldd['states'][k]);d['states'][nk][2]=15 if mode=='lit' else 0
                if mode!='none':f['states'][nk]['migration_source_pattern']=[]
                if mode=='none':d['models'][nk]=oldd['models'][k];continue
                mounted=[]
                for poly in lamp_pol:
                    vertices=[[socket[i]+(v[i]-[.5,1.125,.5][i])*scales[ident] for i in range(3)]+v[3:] for v in poly['vertices']]
                    mounted.append({**poly,'vertices':vertices})
                pol=rotate(meshes[oldd['models'][k]]['polygons'],offset=s['render_mesh'].get('offset',[0,0,0]))+rotate(mounted,yaw)
                # Visual attachment never expands gameplay/helper ownership.
                state_mesh(d,f,nk,pol,meshes)
    return support

def publish_models(d,f,meshes,emissive):
    ident=d['id'];visual={};variants={};expected=set()
    for k,mid in d['models'].items():
        p=props(k);mode=p.pop('visual');stem=state_name(key(p));ref=f'bloodborne_blocks:block/logical/{ident}/{mode}/{stem}'
        visual[k]=ref;variants[k]={'model':ref}
        target=RES/f'assets/bloodborne_blocks/models/block/logical/{ident}/{mode}/{stem}.json';expected.add(target.resolve())
        if mode=='alt' and mid==d['models'][key({**p,'visual':'base'})]:
            data={'parent':f'bloodborne_blocks:block/logical/{ident}/base/{stem}'}
        else:
            textures=sorted({p['texture'] for p in meshes[mid]['polygons']});slots={f't{i}':t for i,t in enumerate(textures)}
            data={'parent':'minecraft:block/block','bloodborne_mesh':mid,'bloodborne_texture_slots':{t:f'#t{i}' for i,t in enumerate(textures)},'textures':{'particle':textures[0],**slots}}
            for n,(base,glow) in enumerate(emissive.items()):
                if base in textures:data['textures']['emissive_'+str(n)]=glow
        write(target,data)
    folder=(RES/f'assets/bloodborne_blocks/models/block/logical/{ident}').resolve()
    for path in folder.rglob('*.json'):
        if path.resolve() not in expected:
            assert path.resolve().is_relative_to(folder);path.unlink()
    d['visual_models']=visual;write(RES/f'assets/bloodborne_blocks/blockstates/{ident}.json',{'variants':variants})
    path=RES/f'assets/bloodborne_blocks/models/item/{ident}.json';item=read(path)
    item['parent']=visual[key({**d['default'],**d.get('placement_properties',{})})];write(path,item)

def apply():
    baseline=read(DOC/'baseline-fingerprints.json.gz')['fingerprints'];records=baseline['families']
    definitions=read(LOGICAL/'definitions.json');contracts=read(LOGICAL/'contracts-v2.json');geometry=read(LOGICAL/'geometry.json');meshes=read(LOGICAL/'meshes.json.gz')
    ds={d['id']:d for d in definitions['blocks']};fs={f['id']:f for f in contracts['families']}
    for ident in PATCHED:
        core=records[ident]['core'];ds[ident]=copy.deepcopy(core['definition']);fs[ident]=copy.deepcopy(core['contract'])
        for evidence in core['state_evidence'].values():meshes[evidence['mesh_id']]=copy.deepcopy(evidence['mesh'])
    write(DOC/'allowlist.json',{'families':sorted(PATCHED),'display_names':sorted(records)})
    support=patch_geometry(ds,fs,meshes,baseline)
    # C561 shares actual mesh IDs with C046 in every state; retain its exact raw
    # migration layouts under C046, without merging different C003 case artwork.
    for k,s in fs['o_c561']['states'].items():
        assert ds['o_c561']['models'][k]==ds['o_c046']['models'][k]
        for pattern in s['migration_source_pattern']:
            if pattern not in fs['o_c046']['states'][k]['migration_source_pattern']:fs['o_c046']['states'][k]['migration_source_pattern'].append(copy.deepcopy(pattern))
    # Authored C046/C561 art differs from C003 CASES, but their original raw
    # carrier is identical. Keep the checkpoint's authoritative CASES matcher;
    # preserve all review observations without inventing a competing matcher.
    for pattern in fs['o_c561'].get('review_source_patterns',[]):
        if pattern not in fs['o_c046']['review_source_patterns']:
            fs['o_c046']['review_source_patterns'].append(copy.deepcopy(pattern))
    fs['o_c046']['migration_redirect']={'successor':'o_cases_0','reason':'C046/C561 remain one distinct manually placeable authored prefab. Their raw carrier is indistinguishable from authoritative C003 CASES, so original raw occurrences retain the checkpoint CASES matcher; no competing C046 rule is invented.'}
    fs['o_bag']['review_id']='C1962_a';fs['o_bag']['semantic_provenance']=['C1962_a','C1962_b','C003 component 4']
    # The authoritative C003 source matcher already covers all weighted bags and
    # books. Retired review cards have explicit redirects, not extra competing
    # copies of the same raw rules.
    decisions=[]
    for retired in sorted(set(MERGES)|CONTEXT):
        f=fs[retired];patterns=[{'state':k,'pattern':p} for k,s in f['states'].items() for p in s['migration_source_pattern']]
        decisions.append({'id':retired,'status':'CONTEXT_ONLY' if retired in CONTEXT else 'MERGED_WITH_SUCCESSOR','ids':[] if retired in CONTEXT else [MERGES[retired]],
             'reason':('User Agony verdict: not a standalone production item. No evidenced full-assembly successor; preserve raw source cells unconsumed, do not invent a replacement.' if retired in CONTEXT else 'User Agony semantic correction; one surviving production item, exact raw or authoritative weighted source rules.'),
             'source_patterns':patterns,'review_source_patterns':copy.deepcopy(f.get('review_source_patterns',[])),
             'previous_redirect':f.get('migration_redirect')})
    for retired in sorted(set(MERGES)|CONTEXT):
        ds.pop(retired);fs.pop(retired);geometry['blocks'].pop(retired,None)
        paths=[RES/f'assets/bloodborne_blocks/blockstates/{retired}.json',RES/f'assets/bloodborne_blocks/models/item/{retired}.json',RES/f'data/bloodborne_blocks/loot_tables/blocks/{retired}.json']
        folder=(RES/f'assets/bloodborne_blocks/models/block/logical/{retired}').resolve()
        paths.extend(folder.rglob('*.json'))
        for path in paths:
            assert path.resolve().is_relative_to(RES.resolve())
            if path.is_file():path.unlink()
    # Update references in target-scoped transactions only. Untouched families
    # must not have any reference to retired IDs (gate will reject otherwise).
    for ident in PATCHED&set(fs):
        for s in fs[ident]['states'].values():
            for p in s['migration_source_pattern']:
                for output in p.get('split_transaction',{}).get('outputs',[]):
                    if output.get('family') in MERGES:output['family']=MERGES[output['family']]
    for ident in sorted(PATCHED&set(ds)):
        geometry['blocks'][ident]=profile(fs[ident]);publish_models(ds[ident],fs[ident],meshes,definitions.get('emissive_textures',{}))
    definitions['blocks']=[ds[i] for i in sorted(ds)];contracts['families']=[fs[i] for i in sorted(fs)]
    # Prune only mesh payload no longer referenced by *any* production family.
    used={mid for d in ds.values() for mid in d['models'].values()}
    write(LOGICAL/'definitions.json',definitions);write(LOGICAL/'contracts-v2.json',contracts);write(LOGICAL/'geometry.json',geometry)
    (LOGICAL/'meshes.json.gz').write_bytes(gzip.compress(json.dumps({i:meshes[i] for i in sorted(used)},separators=(',',':')).encode(),mtime=0))
    slots=read(LOGICAL/'visual-slots.json');slots['families']=[{'id':i,'states':len(d['states']),'base_states':len(d['states'])//2} for i,d in sorted(ds.items())];write(LOGICAL/'visual-slots.json',slots)
    required=read(ROOT/'docs/required-production-families.json')
    required['retained_reviewed_ids']=[i for i in required['retained_reviewed_ids'] if i in ds]
    required['restorations']=[r for r in required['restorations'] if r['id'] in ds]
    required['expected_production_count']=49;required['agony_cumulative_decisions']='docs/agony-patch/decisions.json'
    required['forbidden_production_ids']=sorted(set(required['forbidden_production_ids'])|set(MERGES)|CONTEXT)
    for dec in decisions:required['successors'][dec['id']]={k:dec[k] for k in ('status','ids','reason')}
    write(ROOT/'docs/required-production-families.json',required);write(DOC/'decisions.json',{'authority':'Agony user correction','baseline_commit':'3d78c873278451756b2d950606163ce5f93d5933','decisions':decisions,'candlestick_support':support})
    manifest=read(ROOT/'docs/production-logical-palette.json');manifest['objects']=[r for r in manifest['objects'] if r['id'] in ds]
    excluded={r['id']:r for r in manifest['excluded']}
    for dec in decisions:excluded[dec['id']]={k:dec[k] for k in ('id','status','ids','reason')}
    manifest['excluded']=list(excluded.values())
    for row in manifest['objects']:
        i=row['id']
        if i not in PATCHED:continue
        f=fs[i];row.update(placement_policy=f['placement_policy'],collision_policy=f['collision_policy'],migration_redirect=f.get('migration_redirect'),model_variant_relationship=ds[i]['properties'])
        row['compiled_source_patterns']=[{'target_state':k,'pattern':p} for k,s in f['states'].items() for p in s['migration_source_pattern']]
        if i=='o_bag':row['source_reviews']=['C1962','C1962_a','C1962_b','C003','C003 component 4']
        if i=='o_c046':
            row['source_reviews']=['C046','C561'];row['migration_target']='o_cases_0'
            row['migration_policy']=f['migration_redirect']['reason']
    # Display labels are explicitly in scope, unlike unrelated geometry/schema.
    labels=read(RES/'assets/bloodborne_blocks/lang/en_us.json')
    for row in manifest['objects']:row['semantic_label']=labels['block.bloodborne_blocks.'+row['id']]
    write(ROOT/'docs/production-logical-palette.json',manifest)
    write(LOGICAL/'production-palette.json',{'schemaVersion':1,'objects':[{k:r[k] for k in ('id','semantic_label','source_reviews')} for r in manifest['objects']]})
    print(json.dumps({'production_before':56,'production_after':len(ds),'retired':sorted(set(MERGES)|CONTEXT),'targeted':sorted(PATCHED)}))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    if not args.apply:raise SystemExit('Use --apply after reading docs/agony-patch/PATCH-PLAN.md; no implicit mutation.')
    apply()
