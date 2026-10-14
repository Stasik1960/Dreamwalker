"""Add hidden compatibility for retired visible and invisible source carriers."""
import collections, copy, gzip, json, shutil, sqlite3, traceback, zlib
from pathlib import Path
import numpy as np
from prepare_v1271_city_compatibility import ROOT, RES, OUT, COMPAT, resources, read, write, token, sha

SOURCE=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete')
DEST=ROOT/'build/v1271-city-preparation'

def main():
    plan=DEST/'source-plan.sqlite'; con=sqlite3.connect(plan)
    decisions={i:json.loads(raw) for i,raw in con.execute('SELECT id,data FROM decisions')}
    invisible=sorted({d['sourceState'] for d in decisions.values() if d.get('reason') in {'SOURCE_ALL_PIXEL_ALPHA_ZERO','SOURCE_EMPTY_GEOMETRY'}})
    assert len(invisible)==36
    write(RES/'bloodborne_dw/invisible-source-carriers-v1271.json',{'schemaVersion':1,'sourceStates':invisible,'solidSourceStates':[s for s in invisible if s.startswith('minecraft:polished_diorite_slab[')],'policy':'Original native source collision/outline/emission/opacity/fluid, finite saved states; invisible render; no UUID/BE/owner ledger/ticker','originalSourceModelVisibility':'Proven entirely alpha zero or empty geometry; never used to replace a drawable source model'})
    write(RES/'assets/bloodborne_dw/blockstates/source_invisible_carrier.json',{'variants':{'':{'model':'bloodborne_dw:block/source_invisible_carrier_empty'}}})
    write(RES/'assets/bloodborne_dw/models/block/source_invisible_carrier_empty.json',{'textures':{'particle':'minecraft:block/stone'},'elements':[]})
    debug=read(RES/'bloodborne_dw/debug_catalogue.json');existing=[e for e in debug['entries'] if e['temporaryId']=='93235' or e['registryId']=='bloodborne_dw:source_invisible_carrier']
    if existing:assert len(existing)==1 and existing[0]['temporaryId']=='93235' and existing[0]['registryId']=='bloodborne_dw:source_invisible_carrier'
    else:debug['entries'].append({'temporaryId':'93235','finalId':None,'kind':'compatibility','name':'Невидимый исходный носитель','registryId':'bloodborne_dw:source_invisible_carrier','aliases':['bloodborne_dw:source_invisible_carrier']});write(RES/'bloodborne_dw/debug_catalogue.json',debug)
    mappings=resources(SOURCE,include_catalogue_exclusions=True)
    parts=read(RES/'bloodborne_dw/imported-source-parts-v127.json')
    parts['parts'].append({'registryId':'bloodborne_dw:source_door_fragment','variant':2,'mainId':'93233','sourceApplications':[{'model':'minecraft:block/addon/iron_door_top','y':180}],'defaultInstallationPose':0,'status':'PASS_HIDDEN_EXACT_ORIGINAL_SOURCE_UPPER_FRAGMENT'})
    parts['parts'].append({'registryId':'bloodborne_dw:source_assembly_diorite_wall','variant':7,'mainId':'93189','sourceApplications':[{'model':'minecraft:block/diorite_wall_post'},{'model':'minecraft:block/diorite_wall_side_tall','uvlock':True}],'defaultInstallationPose':0,'status':'PASS_HIDDEN_EXACT_SOURCE_TALL_CONNECTION'})
    write(RES/'bloodborne_dw/imported-source-parts-v127.json',parts)
    changes={}
    for i,d in decisions.items():
        key=token([d.get('sourceState'),d.get('sourceApplications')]); replacement=mappings.get(key)
        if d.get('reason') in {'SOURCE_ALL_PIXEL_ALPHA_ZERO','SOURCE_EMPTY_GEOMETRY'}:
            replacement={'action':'VANILLA','registryId':'bloodborne_dw:source_invisible_solid_carrier' if d['sourceState'].startswith('minecraft:polished_diorite_slab[') else 'bloodborne_dw:source_invisible_carrier','properties':{'source':str(invisible.index(d['sourceState'])),'profile':'base'},'reason':'PRESERVE_NATIVE_INVISIBLE_SOURCE_CARRIER_PHYSICS','sourceState':d['sourceState'],'sourceApplications':d.get('sourceApplications',[])}
        elif d.get('reason')=='USER_REQUEST_SYSTEM_OR_INVISIBLE_CARRIER':
            assert d['sourceState'].startswith(('minecraft:barrier','minecraft:light['))
            replacement={**d,'action':'KEEP','reason':'PRESERVE_ORIGINAL_INVISIBLE_NATIVE_BARRIER_OR_LIGHT'}
        elif d.get('reason')=='UNKNOWN_SOURCE_REGISTRY_OR_SYSTEM_SERVICE' and d.get('sourceRegistryId')=='bloodborne:hunter_lamp_light_source':
            replacement={'action':'VANILLA','registryId':'bloodborne_dw:source_hunter_lamp_light','properties':{},'reason':'PRESERVE_ORIGINAL_HUNTER_LAMP_NATIVE_LIGHT','sourceState':d['sourceState'],'sourceApplications':[]}
        if d.get('registryId')==COMPAT:
            # Appending source art banks adds the saved bank0 field for all
            # existing four repaired hidden source component states.
            d['properties']['art_bank']='0'
            if d.get('sourceState')=='minecraft:dandelion':d['sourceNativeModelOffsetState']='minecraft:dandelion'
            replacement=d
        if replacement:
            replacement=copy.deepcopy(replacement); replacement.setdefault('previousPreparationReason',d.get('reason'))
            changes[i]=replacement
    for i,d in changes.items():con.execute('UPDATE decisions SET data=? WHERE id=?',(token(d),i))
    con.commit(); decisions.update(changes)
    oldmanifest=read(DEST/'runtime/manifest.json'); summary=json.loads(con.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0]);counts=collections.Counter();reasons=collections.Counter();matches=[]
    # Scan prepared uint16 arrays only, with bounded section memory. This does
    # not read source chunks or recompute any original weighted choices.
    for dim,cx,cz,sy,packed in con.execute('SELECT dimension,cx,cz,sy,decision_indices FROM sections'):
        index=np.frombuffer(zlib.decompress(packed),dtype='<u2')
        for i,n in zip(*np.unique(index,return_counts=True)):
            d=decisions[int(i)];counts[d['action']]+=int(n);reasons[d.get('reason',d.get('resolution',''))]+=int(n)
        hit=np.intersect1d(np.unique(index),list(changes))
        if len(hit):matches.append((dim,cx,cz,sy))
    summary.update(counts=dict(counts),reasonCounts=dict(reasons),knownVisibleGaps=0,knownVisibleGapsMeaning='Unresolved decision dictionary entries only; native original-resource fallback reference remains separate',restoredRetiredSourceCells=1854,hiddenInvisibleSourceStates=len(invisible),nativeInvisibleSourceCells=reasons['PRESERVE_NATIVE_INVISIBLE_SOURCE_CARRIER_PHYSICS'],preservedOriginalBarrierLightCells=reasons['PRESERVE_ORIGINAL_INVISIBLE_NATIVE_BARRIER_OR_LIGHT'],preservedOriginalHunterLightCells=reasons['PRESERVE_ORIGINAL_HUNTER_LAMP_NATIVE_LIGHT'])
    ref=SOURCE.parent/'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16';summary['sourceArchivePaths']={'map':str(ref/'bbmc_v16_map.zip'),'pack':str(ref/'bbmc_v15_resource.zip')}
    con.execute('UPDATE metadata SET data=? WHERE key=?',(token(summary),'summary'));con.commit()
    # Restart from the immutable original owner stream, not an accumulated
    # previous delta, so UUIDs are never duplicated on correction/review runs.
    runtime=DEST/'runtime'; source_runtime=SOURCE/'build/v127-runtime-conversion-final2';shutil.copyfile(source_runtime/'owners.jsonl.gz',runtime/'owners.jsonl.gz')
    from v125_map_resolve import source_uuid
    repaired={i:d for i,d in decisions.items() if d.get('resolution')=='EXACT_DRAWABLE_SOURCE_COMPONENT_COMPATIBILITY'}
    affected=set(changes)|{i for i,d in decisions.items() if d.get('reason')=='EXACT_HIDDEN_COMPACT_SOURCE_FILLER'}|set(repaired)
    original_con=sqlite3.connect(f'file:{SOURCE}/build/v127-full-conversion-first.sqlite?mode=ro',uri=True)
    target=runtime/'sections.updated.jsonl.gz';restored=0;changed_sections=0
    with gzip.open(runtime/'owners.jsonl.gz','at',encoding='utf8') as owner,gzip.open(source_runtime/'sections.jsonl.gz','rt',encoding='utf8') as src,gzip.open(target,'wt',encoding='utf8') as out:
        for line in src:
            row=json.loads(line);dim=row['dimension'];cx,cz=row['chunk'];sy=row['sectionY']
            packed=con.execute('SELECT decision_indices FROM sections WHERE dimension=? AND cx=? AND cz=? AND sy=?',(dim,cx,cz,sy)).fetchone()[0]
            index=np.frombuffer(zlib.decompress(packed),dtype='<u2');ids={token(s):j for j,s in enumerate(row['palette'])};changed=False
            for i in np.intersect1d(np.unique(index),list(affected)):
                d=decisions[int(i)];positions=np.flatnonzero(index==i)
                if d['action']=='KEEP':
                    old=json.loads(original_con.execute('SELECT data FROM decisions WHERE id=?',(int(i),)).fetchone()[0]);text=old['sourceState'];carrier,_,tail=text.partition('[');props=dict(piece.split('=',1) for piece in tail.rstrip(']').split(',') if piece);state={'registryId':carrier,'properties':props}
                elif d['action'] in {'COMPACT','VANILLA'}:state={'registryId':d['registryId'],'properties':d['properties']}
                else:assert d['action']=='OWNER';state={'registryId':'minecraft:air','properties':{}}
                key=token(state)
                if key not in ids:ids[key]=len(row['palette']);row['palette'].append(state)
                for at in positions:
                    row['indices'][int(at)]=ids[key]
                    if int(i) in repaired:
                        pos=[cx*16+(int(at)&15),sy*16+(int(at)>>8),cz*16+((int(at)>>4)&15)]
                        item={'dimension':dim,'position':pos,'instanceUuid':source_uuid(dim,pos),'registryId':d['registryId'],'properties':d['properties'],'payload':d['payload']}
                        if d.get('sourceNativeModelOffsetState'):item.update(sourceNativeModelOffsetState=d['sourceNativeModelOffsetState'],sourceNativeModelOffsetPosition=pos)
                        owner.write(token(item)+'\n');restored+=1
                changed=True
            if changed:changed_sections+=1
            out.write(token(row)+'\n' if changed else line)
    assert restored==2084,restored
    target.replace(runtime/'sections.jsonl.gz');original_con.close();con.close()
    manifest={**summary,'sourcePlanSha256':sha(plan),'runtimeFiles':{p.name:sha(p) for p in runtime.glob('*.jsonl.gz')},'changedRuntimeSections':changed_sections,'restoredDrawableSourceCellsTotal':restored}
    write(runtime/'manifest.json',manifest)
    write(OUT/'extended-prepared-bundle-verification.json',{'status':'PASS_REUSED_PLAN_WITH_HIDDEN_VISIBLE_AND_INVISIBLE_SOURCE_COMPATIBILITY','counts':dict(counts),'restoredDrawableSourceCellsTotal':restored,'invisibleCarrierStates':len(invisible),'sourcePlanSha256':sha(plan),'runtimeManifestSha256':sha(runtime/'manifest.json'),'sourceChunkRescans':0,'nativeVerification':'PENDING','originalAuthorMalformedBranchCells':reasons['SOURCE_BLOCKSTATE_NO_APPLICABLE_BRANCH']})

if __name__=='__main__':
    result=ROOT/'build/v1271-city-extension-result.json'
    try:main();write(result,{'status':'PASS','exitCode':0})
    except BaseException:write(result,{'status':'FAIL','exitCode':1,'error':traceback.format_exc()});raise
