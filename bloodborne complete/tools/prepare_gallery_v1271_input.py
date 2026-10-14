"""Reuse the reviewed v12.7 gallery plan with the exact current city dictionary.

No map extraction, weighted-choice recalculation or source preparation occurs.
Only JAR-derived display metadata/bounds and previously omitted source cells
are updated. Run after the prepared city extension and production build.
"""
from __future__ import annotations
import argparse, collections, copy, hashlib, json, math, sqlite3, traceback, zipfile
from pathlib import Path
import numpy as np
from prepare_gallery_v126_input import ArchiveGeometry, assembly_contract, composite_visual, native_visual, component_geometry, union
from v1271_prepared_city import PreparedCity
from v125_map_resolve import source_uuid, rotation
from gallery_platform_plan import bounds_of_points

ROOT=Path(__file__).resolve().parents[1]
SCHEMA='dreamwalker-flat-gallery-v1271'
OLD_INPUT=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\v127-new-gallery-input2.json')
VANILLA=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'

def read(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def sha(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as stream:
        for b in iter(lambda:stream.read(1<<20),b''):h.update(b)
    return h.hexdigest()
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def signed(value,bits):
    value &= (1<<bits)-1
    return value-(1<<bits) if value&(1<<(bits-1)) else value
def planned_flower_offset(pos):
    # Exact MC1.20.1 XZ flower-offset arithmetic. The author also checks this
    # preview against the REAL source BlockState.getModelOffset before using it.
    x,_,z=pos;h=signed(x*3129871,32)^signed(z*116129781,64)
    seed=signed(h*h*42317861+h*11,64)>>16
    component=lambda n:(float(np.float32(n)/np.float32(15))-.5)*.5
    return [component(seed&15),0.,component((seed>>8)&15)]

def refresh_known_source_owner(row,decision,numbers):
    """Only apply the two append-only, native-proven dictionary deltas.

    Frozen whole constructions may have CONSUMED decisions at their root; a
    generic replacement would destroy them. Source UUID and XYZ stay stable.
    """
    reason=decision.get('reason')
    if reason not in {'EXACT_SOURCE_NATIVE_OPTICAL_CARRIER_POSE','EXACT_HIDDEN_SINGLE_BEEHIVE_SOURCE_LADDER'}:return None
    assert decision['action'] in {'OWNER','COMPACT'}
    if reason=='EXACT_SOURCE_NATIVE_OPTICAL_CARRIER_POSE':
        assert row['registryId']==decision['registryId']=='bloodborne_dw:source_snow_height12'
        assert int(row['properties'].get('variant','0'))+16*int(row['properties'].get('art_bank','0'))==3
    else:
        assert row['registryId']=='bloodborne_dw:prototype_ladder'
        assert decision['registryId']=='bloodborne_dw:source_compatibility_fragment'
    properties=copy.deepcopy(decision['properties']);properties.pop('compact',None)
    payload=copy.deepcopy(row.get('payload',{}));payload.pop('SourceShift',None)
    payload.update(copy.deepcopy(decision.get('payload',{})))
    row.update(registryId=decision['registryId'],mainId=numbers[decision['registryId']]['temporaryId'],properties=properties,payload=payload,
               sourceShift=decision.get('sourceShift',payload.get('SourceShift',[0,0,0])),mountY=decision.get('mountY',0),
               sourceState=decision['sourceState'],sourceApplications=decision.get('sourceApplications',[]),
               resolution=reason,preparedDecisionAction=decision['action'],galleryPreparedDelta=reason)
    return reason

def passthrough_bounds(row,jar,models):
    """Reserve native MODEL fallback geometry, including source overhangs."""
    position=row['relativePosition'];registry=row['registryId']
    if registry not in {'bloodborne_dw:source_fallback_carrier','bloodborne_dw:source_fallback_translucent_carrier','bloodborne_dw:source_fallback_multipart_carrier','bloodborne_dw:source_fallback_solid_multipart_carrier'}:
        return position+[v+1 for v in position]
    state=row['properties'];state.setdefault('source_bank','0');selector=f"profile={state.get('profile','base')},source={state['source']},source_bank={state['source_bank']}"
    document=json.loads(jar.read('assets/bloodborne_dw/blockstates/'+registry.split(':')[1]+'.json'))
    branches=[r['apply'] for r in document['multipart'] if r['when']=={'source':state['source'],'source_bank':state['source_bank']}] if 'multipart' in document else [document['variants'][selector]]
    config=json.loads(jar.read('bloodborne_dw/source-fallback-carriers-v1271.json'))
    context=int(state['source'])+128*int(state['source_bank'])
    assert config['targetRegistryIds'][context]==registry and len(branches)==len(config['sourceApplicationSets'][context]),('Native union must retain every fixed source application',registry,state)
    points=[p for branch in branches for q in models.quads({'model':branch['model'],'pitch':branch.get('x',0),'yaw':branch.get('y',0)}) for p in q[0]]
    assert points,('Drawable native fallback has no model geometry',registry,state)
    local=bounds_of_points(points);row['localVisualBounds']=local
    row['relativeVisualBounds']=[local[i]+position[i%3] for i in range(6)]
    return row['relativeVisualBounds']

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--production-jar',type=Path,required=True)
    p.add_argument('--previous-input',type=Path,default=OLD_INPUT)
    p.add_argument('--preparation',type=Path,default=ROOT/'build/v1271-city-preparation')
    p.add_argument('--output',type=Path,default=ROOT/'build/v1271-gallery/input.json')
    args=p.parse_args();report=args.output.with_suffix('.preparation.json')
    try:
        marker=read(args.previous_input)
        assert marker['guard']=='FRESH_ISOLATED_CATALOGUE_GALLERY_ONLY' and marker['gallerySchema']=='dreamwalker-flat-gallery-v127'
        assert not args.output.exists(), 'Refusing to overwrite an existing gallery plan'
        runtime=read(args.preparation/'runtime/manifest.json')
        plan=args.preparation/'source-plan.sqlite';plan_sha=sha(plan)
        assert runtime['sourcePlanSha256']==plan_sha
        assert runtime['sourcePlanReusedWithoutWorldRescan']
        assert runtime['runtimeExecution']=='READY_REUSED_NATIVE_REFERENCE_VERIFIED_PREPARATION' and runtime['counts']['REMOVE']==0, 'Native-reference-verified final preparation required'
        assert runtime['restoredRetiredSourceCells']==1854 and runtime['restoredDrawableSourceCellsTotal']==2084, 'Run the full hidden/invisible preparation extension first'
        native_proof=read(ROOT/'docs/v12.7/native-evidence/final31/captured-build/gametest-tests28/world/v127-source-model-offset-native-proof.json')
        assert native_proof['status']=='PASS_NATIVE_EXACT_ORIGINAL_SOURCE_MODEL_OFFSETS' and native_proof['sourceCells']==64
        for row in native_proof['rows']:
            assert max(abs(a-b) for a,b in zip(planned_flower_offset(row['position']),row['nativeSourceModelOffset']))<1e-12
        parent_sha=sha(args.previous_input);digest=sha(args.production_jar)
        reused_owner_count=len(marker['city']['owners']);restored=[];services=[];not_restored=[];refreshed=collections.Counter()
        with zipfile.ZipFile(args.production_jar) as jar, PreparedCity(plan) as prepared:
            assert jar.testzip() is None
            meta=json.loads(jar.read('fabric.mod.json'))
            assert meta['id']=='bloodborne_dw' and 'v12.7.1' in meta['version']
            remaining=json.loads(jar.read('bloodborne_dw/remaining-catalogue.json'))
            debug=json.loads(jar.read('bloodborne_dw/debug_catalogue.json'))
            retired=json.loads(jar.read('bloodborne_dw/imported-style-retirements.json'))['retirements']
            aliases=json.loads(jar.read('bloodborne_dw/imported-type-aliases.json'))['groups']+json.loads(jar.read('bloodborne_dw/imported-type-aliases-v127.json'))['groups']
            assembly,defaults,active=assembly_contract(jar)
            hidden={r['registryId'] for r in retired}|{a for r in retired for a in r['aliases']}|set(assembly['hiddenPartRedirects'])
            hidden.update(m for g in aliases for m in g['members'] if m!=g['primaryRegistryId'])
            names={'bloodborne_dw:'+r['path']:r for r in remaining['objects']}
            hidden.update(registry for registry,row in names.items() if row.get('conversionOnly'))
            numbers={r['registryId']:r for r in debug['entries']}
            rows=marker['catalogue']['exhibits'];new=[r for r in rows if r['origin']!='V12' and r['kind']=='architecture']
            assert len(new)==539 and {r['registry'] for r in new}==set(names)-hidden
            assert len(rows)==621 and len(marker['reviewPlatforms'])==22
            assert defaults['bloodborne_dw:source_smooth_quartz_top']==2
            models=ArchiveGeometry(jar,VANILLA);geometries={}
            for row in rows:
                registry=row['registry'];assert registry not in hidden
                assert numbers[registry]['temporaryId']==row['id']
                if row['kind']=='architecture':
                    if row['origin']!='V12':
                        definition=names[registry];pose=defaults.get(registry,row['variant'])
                        row.update(name=definition['displayName'],variant=pose,availablePoseCount=len(definition['variants']),sourceModels=definition['variants'][pose]['sourceModels'])
                        for key in ('state','blockStateTag'):
                            row[key]['variant']=str(pose%16)
                            if 'art_bank' in row[key]:row[key]['art_bank']=str(pose//16)
                        if registry in active:row['workingPoseIndices']=active[registry]
                    spec='bloodborne_dw/composite/'+registry.split(':')[1]+'.json'
                    geometry=composite_visual(json.loads(jar.read(spec)),row,models) if spec in jar.namelist() else native_visual(row,jar,models)
                    assert all(math.isfinite(v) for v in geometry)
                    row['localVisualBounds']=geometry;geometries[row['key']]=geometry
                elif row['kind']=='utility':geometries[row['key']]=row['localVisualBounds']
            for group in marker['reviewPlatforms']:
                group['key']=group['key'].replace('V127-','V1271-')
                for component in group['components']:
                    spec='bloodborne_dw/composite/'+component['registryId'].split(':')[1]+'.json'
                    component['descriptorSha256']=hashlib.sha256(jar.read(spec)).hexdigest()
                group['localVisualBounds']=union([component_geometry(c,jar,models) for c in group['components']])
                if group['primaryId']=='90923':
                    group['russianLabel']='Высокая дверь · '+('исходный монтаж' if group['reviewPoseIndex']==2 else 'прежний прямой монтаж')
            city=marker['city'];origin=city['sourceOrigin'];dimension=city['dimension']
            assert runtime['sourceHashes']=={'map':city['mapSha256'],'pack':city['packSha256']}
            old_exceptions=copy.deepcopy(city['sourceExceptions'])
            exception_positions={tuple(r['sourcePosition']) for r in old_exceptions}
            owned={tuple(r['relativePosition'][i]+origin[i] for i in range(3)) for r in city['owners']}
            native={tuple(r['relativePosition'][i]+origin[i] for i in range(3)) for r in city['passThroughBlocks']}
            def annotate_native_offset(row,decision,pos):
                state=decision['sourceNativeModelOffsetState'];assert state in {'minecraft:azure_bluet','minecraft:blue_orchid','minecraft:orange_tulip','minecraft:poppy','minecraft:red_tulip','minecraft:dandelion'}
                preview=planned_flower_offset(pos);row.update(sourceNativeModelOffsetState=state,sourceNativeModelOffsetPosition=pos,plannedNativeSourceModelOffset=preview)
                row['sourceShiftBeforeNativeOffset']=copy.deepcopy(row.get('sourceShift',row.get('payload',{}).get('SourceShift',[0,0,0])))
                added=rotation(preview,-int(row['properties'].get('rotation','0')))
                row['sourceShift']=[row['sourceShiftBeforeNativeOffset'][i]+added[i] for i in range(3)]
                row.setdefault('payload',{})['SourceShift']=row['sourceShift']
            retained_owners=[];replaced_optical=[]
            for row in city['owners']:
                pos=[row['relativePosition'][i]+origin[i] for i in range(3)]
                decision=prepared.decision_at(dimension,*pos)
                if decision and decision.get('reason') in {'EXACT_NATIVE_GLASS_OPTICAL_COMPATIBILITY','EXACT_FIXED_SOURCE_NATIVE_OPTICAL_CONTEXT'}:
                    assert decision['action']=='VANILLA' and tuple(pos) not in native
                    assert not any(decision.get('sourceShift',[0,0,0])) and not decision.get('mountY',0)
                    native_row={'sourcePosition':pos,'relativePosition':row['relativePosition'],'registryId':decision['registryId'],'properties':copy.deepcopy(decision['properties']),
                                'sourceState':decision['sourceState'],'sourceApplications':decision['sourceApplications'],'resolution':decision['reason'],'preparedDecisionAction':'VANILLA',
                                'previousSourceOwnerUuid':row['instanceUuid'],'galleryRepresentation':'FIXED_NATIVE_SOURCE_APPLICATIONS_AND_ORIGINAL_OPTICS'}
                    city['passThroughBlocks'].append(native_row);native.add(tuple(pos));owned.remove(tuple(pos));services.append(native_row)
                    replaced_optical.append(copy.deepcopy(row));refreshed[decision['reason']]+=1;continue
                if decision:
                    reason=refresh_known_source_owner(row,decision,numbers)
                    if reason:refreshed[reason]+=1
                if decision and decision.get('sourceNativeModelOffsetState') and decision.get('registryId')==row['registryId']:
                    assert not row.get('sourceNativeModelOffsetState'), 'Frozen gallery already includes this source offset'
                    annotate_native_offset(row,decision,pos)
                retained_owners.append(row)
            city['owners']=retained_owners
            city['previousOwnedNativeOpticalRows']=replaced_optical
            for old in city['sourceExceptions']+city['sourceInvisibleServices']:
                pos=old['sourcePosition'];decision=prepared.decision_at(dimension,*pos)
                assert decision is not None, ('Missing prepared section',pos)
                if decision['action'] in ('OWNER','COMPACT'):
                    assert tuple(pos) not in owned and tuple(pos) not in native, ('Repeated source root',pos)
                    properties=copy.deepcopy(decision['properties']);properties.pop('compact',None)
                    payload=copy.deepcopy(decision.get('payload',{}));payload.setdefault('ImportedTypeVersion',1)
                    row={'sourcePosition':pos,'relativePosition':[pos[i]-origin[i] for i in range(3)],'registryId':decision['registryId'],'mainId':numbers[decision['registryId']]['temporaryId'],'properties':properties,'payload':payload,'sourceShift':decision.get('sourceShift',payload.get('SourceShift',[0,0,0])),'mountY':decision.get('mountY',0),'instanceUuid':source_uuid(dimension,pos),'sourceState':decision['sourceState'],'sourceApplications':decision.get('sourceApplications',[]),'resolution':decision.get('resolution',decision.get('reason','EXACT_PREPARED_SOURCE_COMPATIBILITY')),'galleryPreviousClassification':old.get('status',old.get('resolution')),'preparedDecisionAction':decision['action'],'galleryRepresentation':'OWNED_EXACT_SOURCE_POSE_NO_NEW_ARTWORK'}
                    if decision.get('sourceNativeModelOffsetState'):
                        annotate_native_offset(row,decision,pos)
                    component_geometry(row,jar,models)
                    city['owners'].append(row);owned.add(tuple(pos));restored.append(row)
                elif decision['action']=='VANILLA':
                    assert tuple(pos) not in owned and tuple(pos) not in native
                    row={**copy.deepcopy(old),'registryId':decision['registryId'],'properties':decision['properties'],'resolution':decision.get('reason','EXACT_PREPARED_NATIVE_SOURCE_CARRIER'),'preparedDecisionAction':'VANILLA'}
                    city['passThroughBlocks'].append(row);native.add(tuple(pos));services.append(row)
                else:
                    assert decision['action'] in {'REMOVE','KEEP','CONSUMED'}, decision
                    not_restored.append({**copy.deepcopy(old),'preparedDecisionAction':decision['action'],'preparedDecisionReason':decision.get('reason')})
            city['sourceExceptions']=[r for r in not_restored if tuple(r['sourcePosition']) in exception_positions]
            city['previousSourceExceptions']=old_exceptions
            city['remainingPreparedOmissions']=not_restored
            assert not not_restored, 'Final source fragment still contains unrepresented prepared decisions'
            city['restoredCompatibilitySourceCells']=restored
            city['restoredNativeInvisibleSourceCarriers']=services
            city['plannedOwnerCount']=len(city['owners'])
            archived_by_root=collections.defaultdict(list)
            for entry in runtime.get('originalSourceBlockEntities',[]):
                if entry['decisionAction']=='OWNER':archived_by_root[(entry['dimension'],tuple(entry['ownerPosition']))].append(entry)
            archived=0
            for row in city['owners']:
                pos=tuple(row['relativePosition'][i]+origin[i] for i in range(3))
                entries=archived_by_root.get((dimension,pos),[])
                if entries:
                    row['originalSourceBlockEntitiesBase64']=[entry['nbtBase64'] for entry in entries]
                    row['originalSourceBlockEntityTypedHashes']=[entry['typedNbtSha256'] for entry in entries]
                    archived+=len(entries)
            city['originalTypedSourceBlockEntitiesArchived']=archived
            city['galleryPurpose']='Reuse the reviewed v12.7 source clip with exact hidden compatibility decisions from the current full-city preparation; native visual acceptance is separate'
            bounds=[component_geometry(row,jar,models) for row in city['owners']]
            bounds += [passthrough_bounds(r,jar,models) for r in city['passThroughBlocks']]
            city['localVisualBounds']=union(bounds)
            marker['localVisualBounds']=geometries
            marker['russianItemNames']={k:v for entry in jar.namelist() if entry.endswith('/lang/ru_ru.json') for k,v in json.loads(jar.read(entry)).items()}
        marker.update(gallerySchema=SCHEMA,displayRevision='V12.7.1',productionJarSha256=digest)
        marker['catalogue'].update(productionJar=str(args.production_jar.absolute()),productionJarSha256=digest,displayPolicy='Original V12 plus539 canonical NEW and2 utility items; hidden compatibility states remain source-city only')
        marker['catalogue']['summary'].update(newWorkingTypes=541,newArchitectureTypes=539,utilityTypes=2)
        marker['galleryPreparation']={'status':'PASS_STATIC_REUSED_GALLERY_PLAN_REQUIRES_NATIVE_AUTHOR_AND_REOPEN','previousGalleryInput':str(args.previous_input.absolute()),'previousGalleryInputSha256':parent_sha,'sourcePlanPath':str(plan.absolute()),'sourcePlanSha256':plan_sha,'runtimeManifestSha256':sha(args.preparation/'runtime/manifest.json'),'sourcePreparationReused':True,'sourceWorldRescanned':False,'reusedCityOwners':reused_owner_count,'refreshedNativeProvenOwnerDecisions':dict(refreshed),'restoredDrawableCityCells':len(restored),'nativeSourceCarrierCells':len(services),'remainingPreparedOmissions':len(not_restored),'originalTypedSourceBlockEntitiesArchived':archived,'hiddenCompatibilityMainPlatforms':0,'defaultDoor90923Pose':2,'altPolicy':'Persisted switching slot, BASE visuals; no alternate artwork loaded','productionJarSha256':digest}
        write(args.output,marker);write(report,marker['galleryPreparation'])
    except BaseException:
        write(report,{'status':'FAIL','error':traceback.format_exc()});raise

if __name__=='__main__':main()
