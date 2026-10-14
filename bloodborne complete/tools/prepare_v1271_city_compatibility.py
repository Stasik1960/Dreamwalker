"""Reuse the complete V12.7 plan, restoring drawable source fragments only.

The original databases, streams, map and pack are read-only inputs. This tool
does not rescan/extract the map or author a world. Existing section decision
arrays and original weighted model choices are retained unchanged.
"""
from __future__ import annotations
import argparse, collections, copy, gzip, hashlib, json, math, shutil, sqlite3, time, zlib
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/architecture/resources'
OUT = ROOT / 'docs/v12.7.1/conversion'
STYLE = 'APPROVED_EXACT_VISIBLE_AND_HIDDEN_COMPACT'
COMPAT = 'bloodborne_dw:source_compatibility_fragment'
NUMBER = '93234'

def read(path): return json.loads(path.read_text(encoding='utf-8-sig'))
def token(value): return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':'))
def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for data in iter(lambda: source.read(1 << 20), b''): digest.update(data)
    return digest.hexdigest()
def box(low, high): return {'from':[round(float(v),8) for v in low], 'to':[round(float(v),8) for v in high]}

def resources(source_project, include_catalogue_exclusions=False, additional_decisions=None):
    # Bind immutable references explicitly; never fall back to paused E copies.
    import v125_map_index as index
    ref = source_project.parent / 'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16'
    index.MAP, index.PACK = ref/'bbmc_v16_map.zip', ref/'bbmc_v15_resource.zip'
    from import_remaining_assets import Source, key, uvlocked_face
    from v124_visual_audit import Visuals, rot
    from v125_map_door_projections import SourceVisuals
    from v127_apply_full_map_poses import simple_boxes, bounds_for_quads
    vanilla = Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'
    exporter = Source(index.PACK, vanilla)
    evidence = []

    def resolved(model, chain=()):
        raw = copy.deepcopy(exporter.raw(model)); parent = raw.get('parent')
        inherited = {}
        if parent and not parent.startswith('builtin/'):
            try: inherited = resolved(parent, chain+(model,))
            except (FileNotFoundError,json.JSONDecodeError):
                # The author explicitly supplied this model's entire mesh.
                assert raw.get('elements'), ('Missing geometry cannot be invented', model, parent)
                evidence.append({'model':model, 'repair':'MISSING_UNUSED_PARENT_WITH_EXPLICIT_ELEMENTS', 'parent':parent})
        textures = {**inherited.get('textures',{}), **raw.get('textures',{})}
        inherited.update(raw); inherited.pop('parent',None); inherited['textures'] = textures
        return inherited

    def export_valid(model, app):
        document = resolved(model); textures = document['textures']; invalid = []
        if model == 'minecraft:block/potted_spruce_sapling' and 'dirt' not in textures:
            # The original vanilla template names the same interior face #dirt;
            # use v15's already authored dirt PNG, without changing any UV/mesh.
            native = json.loads(exporter.vanilla.read('assets/minecraft/models/block/flower_pot_cross.json'))
            textures['dirt'] = native['textures']['dirt']
            evidence.append({'model':model,'repair':'RESTORE_TEMPLATE_DIRT_ALIAS_USING_EXISTING_V15_TEXTURE', 'texture':'minecraft:block/dirt', 'face':'pot interior UP', 'authoredGeometryUnchanged':True})
        for n,element in enumerate(document.get('elements',[])):
            for side,face in list(element.get('faces',{}).items()):
                try: exporter.resolve_texture(textures,face['texture'])
                except ValueError:
                    axis = 1 if side in {'up','down'} else 2 if side in {'north','south'} else 0
                    extent = np.abs(np.asarray(element['to'])-element['from'])
                    area = float(np.prod(np.delete(extent,axis)))
                    assert area == 0, ('Cannot silently discard a visible invalid face',model,n,side)
                    invalid.append({'element':n,'face':side,'reference':face['texture'],'area':area})
                    del element['faces'][side]
        if invalid:evidence.append({'model':model,'repair':'REMOVE_ONLY_ZERO_AREA_UNDEFINED_TEXTURE_FACES','faces':invalid})
        if app.get('uvlock') and (app.get('x',0) or app.get('y',0)):
            for element in document.get('elements',[]):
                element['faces']={side:uvlocked_face(face,side,element,app) for side,face in element.get('faces',{}).items()}
        source_document = copy.deepcopy(document)
        used={exporter.resolve_texture(textures,face['texture']) for element in document.get('elements',[]) for face in element.get('faces',{}).values()}
        for name,value in list(textures.items()):
            try:textures[name] = exporter.texture(value)
            except FileNotFoundError:
                assert key(value) not in used, ('Missing drawable source texture',model,name,value)
                del textures[name]
                evidence.append({'model':model,'repair':'OMIT_ONLY_UNUSED_MISSING_TEXTURE_DECLARATION','alias':name,'reference':value,'drawableFacesUnchanged':True})
        relative = model.split(':')[1]
        if app.get('uvlock') and (app.get('x',0) or app.get('y',0)):relative+=f'_x{app.get("x",0)}_y{app.get("y",0)}'
        target = 'bloodborne_dw:remaining/compatibility/minecraft/'+relative
        write(RES/'assets/bloodborne_dw/models/remaining/compatibility/minecraft'/f'{relative}.json',document)
        return target,source_document

    # Sanitized source oracle retains original coordinates and texture aliases.
    source = SourceVisuals(); native = Visuals(vanilla_jar=vanilla)
    source_documents = {}
    original_document = source.document
    source.document = lambda model: source_documents.get(model) or original_document(model)
    full_path = RES/'bloodborne_dw/imported-full-map-v127.json'; full = read(full_path)
    cat_path = RES/'bloodborne_dw/remaining-catalogue.json'; catalogue = read(cat_path)
    debug_path = RES/'bloodborne_dw/debug_catalogue.json'; debug = read(debug_path)
    optics_path = RES/'bloodborne_dw/imported-source-lighting-v127.json'; optics = read(optics_path)
    retired_path = RES/'bloodborne_dw/imported-style-retirements.json'; retired = read(retired_path)
    mappings = {}

    def append_pose(registry, name, applications, source_state, physical, descriptor=None):
        path = registry.split(':')[1]; file = RES/'bloodborne_dw/composite'/f'{path}.json'
        spec = descriptor if descriptor is not None else read(file)
        pose_index = len(spec['variants']); parts = []; quads = []
        for app in applications:
            target, source_document = export_valid(app['model'],app); source_documents[app['model']] = source_document
            part = {'model':target, 'offset':[0,0,0], 'yaw':app.get('y',0), 'pitch':app.get('x',0)}
            parts.append(part); quads.extend(native.quads(part))
        low,high = bounds_for_quads(quads); offset = np.array([0.,-low[1],0.])
        for part in parts:part['offset'] = offset.tolist()
        simple = simple_boxes(quads,offset,physical)
        visible = box(low+offset,high+offset)
        shape = {'parts':parts,'collision':simple[0], 'selection':simple[0] or [visible]}
        spec['variants'].append({'poses':{'closed':shape, 'open':copy.deepcopy(shape)},'visibleBounds':visible,'sourcePlacementOffset':offset.tolist(),'sourceClosedApplications':applications,'sourceOpenApplications':[],'sourceVariant':name,'hiddenSourceCompatibility':True})
        write(file,spec)
        existing = next((row for row in catalogue['objects'] if row['path']==path),None)
        if existing is None:
            existing = {'path':path,'displayName':spec['displayName'],'variants':[]}; catalogue['objects'].append(existing)
        if registry==COMPAT:existing['conversionOnly']=True
        existing['variants'].append({'variant':pose_index,'name':name,'sourceModels':[a['model'] for a in applications]})
        rotations = []
        for yaw in range(8):
            turned = rot(np.asarray([q[0] for q in quads]),1,-yaw*45,[8,8,8])+offset
            aim = simple[yaw] or [box(turned.reshape(-1,3).min(0),turned.reshape(-1,3).max(0))]
            rotations.append({'collision':simple[yaw],'aim':aim})
        full['objects'].append({'registryId':registry,'variant':pose_index,'rotations':rotations,'sourceVariant':name})
        optics['objects'].append({'registryId':registry,'variant':pose_index,'open':False,'stateProperties':{},'sources':[{'sourceState':source_state,'occurrences':0,'caseIds':[]}]})
        properties={'variant':str(pose_index%16),'rotation':'0','profile':'base','open':'false'}
        if pose_index>=16 or include_catalogue_exclusions:properties['art_bank']=str(pose_index//16)
        target = {'action':'OWNER','registryId':registry,'properties':properties,'payload':{'ImportedTypeVersion':1},'sourceShift':(-offset/16).tolist(),'mountY':0.,'resolution':'EXACT_DRAWABLE_SOURCE_COMPONENT_COMPATIBILITY','sourceState':source_state,'sourceApplications':applications}
        if any(offset):target['payload']['SourceShift']=target['sourceShift']
        if source_state=='minecraft:dandelion':target['sourceNativeModelOffsetState']=source_state
        evidence.append({'registryId':registry,'variant':pose_index,'sourceState':source_state,'sourceApplications':applications,'drawableSourceFaces':len(quads),'allAuthoredVerticesUvUnchanged':True,'newArtwork':False})
        return target

    descriptor = {'schemaVersion':1,'units':16,'id':COMPAT,'displayName':'Исходный фрагмент совместимости','openable':False,'layer':'cutout','support':{'required':False},'essentialMask':[[0,0,0]],'variants':[],'simplePhysicalCollision':True,'source':{'kind':'HIDDEN_EXISTING_V15_DRAWABLE_FRAGMENTS'},'sourcePolicy':'Compatibility only; never offered in Creative. No authored model coordinates or UV changed.'}
    if additional_decisions is not None:
        descriptor=read(RES/'bloodborne_dw/composite/source_compatibility_fragment.json')
        for decision in additional_decisions:
            apps=decision['sourceApplications']; source_state=decision['sourceState']
            # Resume only this appended two-pose delta, without duplicating
            # the accepted 113-pose compatibility prefix on a failed run.
            existing=[i for i,v in enumerate(descriptor['variants']) if i>=113 and v['sourceClosedApplications']==apps]
            assert len(existing)<=1
            if existing:
                i=existing[0];v=descriptor['variants'][i];shift=(-np.asarray(v['sourcePlacementOffset'])/16).tolist()
                payload={'ImportedTypeVersion':1}
                if any(shift):payload['SourceShift']=shift
                replacement={'action':'OWNER','registryId':COMPAT,'properties':{'variant':str(i%16),'art_bank':str(i//16),'rotation':'0','profile':'base','open':'false'},'payload':payload,'sourceShift':shift,'mountY':0.,'resolution':'EXACT_DRAWABLE_SOURCE_COMPONENT_COMPATIBILITY','sourceState':source_state,'sourceApplications':apps}
            else:
                replacement=append_pose(COMPAT,'Скрытая исходная конструкция '+str(len(descriptor['variants'])+1),apps,source_state,True,descriptor)
                replacement['properties']['art_bank']=str((len(descriptor['variants'])-1)//16)
            mappings[token([source_state,apps])]=replacement
        for group in full['groups']:
            if group['registryId']==COMPAT:group['poses']=len(descriptor['variants']);group['bankMax']=(len(descriptor['variants'])-1)//16
        assert len(descriptor['variants'])<=128
        for path,value in [(full_path,full),(cat_path,catalogue),(optics_path,optics)]:write(path,value)
        write(OUT/'protected-beehive-ladder-compatibility.json',{'status':'PASS_HIDDEN_EXACT_SOURCE_NATIVE_OPACITY','rows':evidence,'creativeOffersAdded':0,'newArtwork':False,'pairedSourceCloneEnabled':False})
        return mappings
    if include_catalogue_exclusions:
        descriptor=read(RES/'bloodborne_dw/composite/source_compatibility_fragment.json')
        catalogue_pose_count=len(next(row for row in catalogue['objects'] if row['path']=='source_compatibility_fragment')['variants'])
        # An interrupted export can leave only this generated descriptor ahead
        # of the catalogue. Retain the accepted prefix when resuming that pass.
        descriptor['variants']=descriptor['variants'][:catalogue_pose_count]
        original=sqlite3.connect(f'file:{source_project}/build/v127-full-conversion-first.sqlite?mode=ro',uri=True)
        reasons={'USER_REMOVED_DAMAGED_SOURCE_TYPE','PROTECTED_V12_REMOVED_OR_GEOMETRY_INCOMPATIBLE_WALL_STATE','PREEXISTING_INTENTIONAL_SOURCE_VARIANT_EXCLUSION'}
        decisions=[json.loads(raw) for raw, in original.execute('SELECT data FROM decisions')]
        decisions=[d for d in decisions if d.get('reason') in reasons]
        poses={}
        for decision in decisions:
            apps=decision['sourceApplications']; source_state=decision['sourceState']; application_key=token(apps)
            if application_key not in poses:poses[application_key]=append_pose(COMPAT,'Скрытая исходная конструкция '+str(len(descriptor['variants'])+1),apps,source_state,True,descriptor)
            replacement=copy.deepcopy(poses[application_key]);replacement['sourceState']=source_state;replacement['sourceApplications']=apps
            mappings[token([source_state,apps])]=replacement
        original.close()
        full['groups'].append({'registryId':COMPAT,'poses':len(descriptor['variants']),'reason':'Hidden source catalogue-exclusion compatibility; no offered styles restored'})
        assert len(descriptor['variants'])<=128
        for path,value in [(full_path,full),(cat_path,catalogue),(optics_path,optics)]:write(path,value)
        write(OUT/'catalogue-exclusion-compatibility.json',{'status':'PASS_OFFLINE_HIDDEN_SOURCE_COMPATIBILITY','rows':evidence,'sourceDecisions':len(decisions),'hiddenPoses':len(poses),'creativeOffersAdded':0,'nativeVerification':'PENDING'})
        return mappings
    assert not any(row['registryId']==COMPAT or row['temporaryId']==NUMBER for row in debug['entries'])
    debug['entries'].append({'temporaryId':NUMBER,'finalId':None,'kind':'architecture','name':descriptor['displayName'],'registryId':COMPAT,'aliases':[COMPAT]})
    full['additionalRegistryIds'].append(COMPAT)
    for model in ('dandelion','dandelion_1','dandelion_3'):
        apps = [{'model':'minecraft:block/'+model}]
        mappings[token(['minecraft:dandelion',apps])] = append_pose(COMPAT,'Исходный шпиль '+model,apps,'minecraft:dandelion',False,descriptor)
    apps = [{'model':'minecraft:block/potted_spruce_sapling'}]
    mappings[token(['minecraft:potted_spruce_sapling',apps])] = append_pose(COMPAT,'Исходный горшок с елью',apps,'minecraft:potted_spruce_sapling',True,descriptor)
    apps = [{'model':'minecraft:block/diorite_wall_post'},{'model':'minecraft:block/diorite_wall_side_tall','uvlock':True}]
    state = 'minecraft:diorite_wall[east=none,north=tall,south=none,up=true,waterlogged=false,west=none]'
    mappings[token([state,apps])] = append_pose('bloodborne_dw:source_assembly_diorite_wall','Исходное высокое соединение',apps,state,True)
    apps = [{'model':'minecraft:block/addon/iron_door_top','y':180}]
    state = 'minecraft:iron_door[facing=west,half=upper,hinge=right,open=false,powered=false]'
    mappings[token([state,apps])] = append_pose('bloodborne_dw:source_door_fragment','Исходная верхняя часть железной двери',apps,state,True)
    write(RES/'assets/bloodborne_dw/blockstates/source_compatibility_fragment.json',{'variants':{'':{'model':'bloodborne_dw:block/source_compatibility_fragment_empty'}}})
    write(RES/'assets/bloodborne_dw/models/block/source_compatibility_fragment_empty.json',{'textures':{'particle':'minecraft:block/stone'},'elements':[]})
    write(RES/'assets/bloodborne_dw/models/item/source_compatibility_fragment.json',{'parent':'builtin/entity'})
    retired['retirements'].append({'registryId':COMPAT,'numericId':NUMBER,'reason':'Hidden exact source compatibility fragments; no additional Creative artwork','aliases':[COMPAT]})
    for path,value in [(full_path,full),(cat_path,catalogue),(debug_path,debug),(optics_path,optics),(retired_path,retired)]:write(path,value)
    write(OUT/'drawable-source-component-repairs.json',{'status':'PASS_OFFLINE_VALID_SOURCE_COMPONENT_RESTORATION','rows':evidence,'nativeVerification':'PENDING','sourceArchivesWritten':False,'newTextureArtwork':False})
    return mappings

def prepare(source_project, mappings, dest):
    original = source_project/'build/v127-full-conversion-first.sqlite'
    source_runtime = source_project/'build/v127-runtime-conversion-final2'
    previous = read(source_runtime/'manifest.json'); assert sha(original)==previous['sourcePlanSha256']
    for name,digest in previous['runtimeFiles'].items():assert sha(source_runtime/name)==digest
    assert not dest.exists(), 'Derived preparation must use a fresh directory'
    dest.mkdir(parents=True); plan = dest/'source-plan.sqlite'; shutil.copyfile(original,plan)
    con = sqlite3.connect(plan); dictionary = {i:json.loads(d) for i,d in con.execute('SELECT id,data FROM decisions')}
    remapped = {}; counts = collections.Counter(); reason_counts = collections.Counter(); changed_sections = 0
    for i,decision in dictionary.items():
        signature = token([decision.get('sourceState'),decision.get('sourceApplications')])
        replacement = mappings.get(signature) if decision.get('action')=='REMOVE' else None
        if decision.get('reason')=='USER_APPROVED_ENCLOSED_VANILLA_SOURCE_FILLER':
            choices = [v for v in dictionary.values() if v.get('action')=='COMPACT' and v.get('sourceState')==decision['sourceState'] and v.get('sourceApplications')==decision['sourceApplications']]
            assert len(choices)==1, (i,len(choices)); replacement = copy.deepcopy(choices[0])
            replacement['reason'] = 'EXACT_HIDDEN_COMPACT_SOURCE_FILLER'; replacement['previousPreparationDecision']=i
        if replacement:
            replacement = copy.deepcopy(replacement); replacement['previousPreparationReason']=decision.get('reason')
            con.execute('UPDATE decisions SET data=? WHERE id=?',(token(replacement),i)); remapped[i]=replacement
    assert len(remapped)==10, ('Expected four filler and six fragment decisions',len(remapped))
    con.commit(); dictionary.update(remapped)
    # Count existing compressed decision arrays. No world NBT is decoded, no
    # weights or adjacency are recomputed and section rows remain byte-identical.
    for (packed,) in con.execute('SELECT decision_indices FROM sections'):
        indices = np.frombuffer(zlib.decompress(packed),dtype='<u2')
        for i,n in zip(*np.unique(indices,return_counts=True)):
            d = dictionary[int(i)]; counts[d['action']]+=int(n); reason_counts[d.get('reason',d.get('resolution',''))]+=int(n)
    restored = counts['OWNER']-previous['counts']['OWNER']; assert restored==230,restored
    assert counts['COMPACT']==previous['counts']['COMPACT']+8433855
    assert counts['REMOVE']==previous['counts']['REMOVE']-230
    policy = {'status':STYLE,'sourceStylePolicy':STYLE,'humanAnswer':'Да: чистый каталог, точная старая карта','visibleAuthoredContent':'Hidden exact compatibility poses retain source vertices, UV, original v15 PNG bytes and original XYZ weighted choices','internalFillers':'Previously prepared enclosed masks reuse existing compact source poses; no additional block entities','fullWorldConverted':False}
    write(OUT/'source-style-policy.json',policy)
    ref = source_project.parent/'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16'
    summary = {**previous,'counts':dict(counts),'reasonCounts':dict(reason_counts),'sourceStylePolicy':STYLE,'sourceStylePolicySha256':sha(OUT/'source-style-policy.json'),'enclosedVanillaFillerCells':0,'exactHiddenCompactFillerCells':8433855,'restoredDrawableSourceCells':restored,'sourcePlanReusedWithoutWorldRescan':True,'sourcePreparationParentSha256':sha(original),'sourceArchivePaths':{'map':str(ref/'bbmc_v16_map.zip'),'pack':str(ref/'bbmc_v15_resource.zip')},'nativeValidation':'PENDING_FINAL_MOD_NATIVE_ACCEPTANCE','runtimeExecution':'Prepared for derived full conversion; original archives remain immutable','planFile':str(plan),'knownVisibleGaps':0,'knownVisibleGapsMeaning':'This field counts unresolved dictionary entries only; it does not certify visual equality. Remaining original author defects are listed separately.'}
    summary.pop('runtimeFiles',None); summary.pop('sourcePlanSha256',None)
    con.execute('UPDATE metadata SET data=? WHERE key=?',(token(summary),'summary')); con.commit()
    runtime = dest/'runtime'; runtime.mkdir()
    for name in ('owners.jsonl.gz','whole-objects.jsonl.gz','entities.jsonl.gz'):shutil.copyfile(source_runtime/name,runtime/name)
    # Preserve all already authored owner rows; append only the restored 230
    # source fragments. Native compact cells never enter the owner stream.
    from v125_map_resolve import source_uuid
    with gzip.open(runtime/'owners.jsonl.gz','at',encoding='utf8') as owners, gzip.open(source_runtime/'sections.jsonl.gz','rt',encoding='utf8') as src, gzip.open(runtime/'sections.jsonl.gz','wt',encoding='utf8') as dst:
        for line in src:
            row = json.loads(line); dim=row['dimension']; cx,cz=row['chunk']; sy=row['sectionY']
            raw = con.execute('SELECT decision_indices FROM sections WHERE dimension=? AND cx=? AND cz=? AND sy=?',(dim,cx,cz,sy)).fetchone()
            assert raw; decisions=np.frombuffer(zlib.decompress(raw[0]),dtype='<u2'); changed=False
            ids={token(state):n for n,state in enumerate(row['palette'])}
            for i in np.intersect1d(np.unique(decisions),list(remapped)):
                d=remapped[int(i)]; positions=np.flatnonzero(decisions==i)
                state={'registryId':d['registryId'],'properties':d['properties']} if d['action']=='COMPACT' else {'registryId':'minecraft:air','properties':{}}
                key=token(state)
                if key not in ids:ids[key]=len(row['palette']);row['palette'].append(state)
                for at in positions:
                    row['indices'][int(at)]=ids[key]
                    if d['action']=='OWNER':
                        pos=[cx*16+(int(at)&15),sy*16+(int(at)>>8),cz*16+((int(at)>>4)&15)]
                        owner={'dimension':dim,'position':pos,'instanceUuid':source_uuid(dim,pos),'registryId':d['registryId'],'properties':d['properties'],'payload':d['payload']}
                        if d.get('sourceNativeModelOffsetState'):owner.update(sourceNativeModelOffsetState=d['sourceNativeModelOffsetState'],sourceNativeModelOffsetPosition=pos)
                        owners.write(token(owner)+'\n')
                changed=True
            if changed:changed_sections+=1
            dst.write(token(row)+'\n' if changed else line)
    manifest={**summary,'sourcePlanSha256':sha(plan),'runtimeFiles':{p.name:sha(p) for p in runtime.glob('*.jsonl.gz')},'reusedRuntimeParentManifestSha256':sha(source_runtime/'manifest.json'),'changedRuntimeSections':changed_sections}
    write(runtime/'manifest.json',manifest)
    input_states = collections.Counter(d.get('reason') for d in dictionary.values() if d.get('action')=='REMOVE')
    unresolved = {'status':'ORIGINAL_SOURCE_EDGE_CASES_REQUIRE_NATIVE_REFERENCE','remainingRemovalReasons':{r:reason_counts[r] for r in input_states},'sourceBlockstateNoApplicableBranchCells':reason_counts['SOURCE_BLOCKSTATE_NO_APPLICABLE_BRANCH'],'invisibleAlphaZeroNotArchitectureHole':reason_counts['SOURCE_ALL_PIXEL_ALPHA_ZERO'],'noArbitraryHoleFillerAdded':True}
    write(OUT/'remaining-source-edge-cases.json',unresolved)
    verification={'status':'PASS_REUSED_COMPLETE_PLAN_AND_EXACT_COMPACT_FILLER_REMAP','originalPlanSha256':sha(original),'derivedPlanSha256':sha(plan),'originalRuntimeManifestSha256':sha(source_runtime/'manifest.json'),'derivedRuntimeManifestSha256':sha(runtime/'manifest.json'),'decisionArraysChanged':0,'sectionSourcePaletteArraysChanged':0,'sourceChunksRescanned':0,'weightedSourceChoicesRecomputed':False,'counts':dict(counts),'restoredDrawableCells':restored,'additionalBlockEntitiesFromFillers':0,'runtimeDirectory':str(runtime),'sourcePlan':str(plan),'nativeVerification':'PENDING'}
    write(OUT/'prepared-bundle-verification.json',verification); con.close()
    print(token(verification),flush=True)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--source-project',required=True,type=Path);parser.add_argument('--output',required=True,type=Path);args=parser.parse_args()
    source=args.source_project.absolute();assert source.drive.upper()=='C:' and source.is_dir()
    for ancestor in [source,*source.parents]:assert not ancestor.is_symlink(),ancestor
    mappings=resources(source);prepare(source,mappings,args.output.absolute())

if __name__=='__main__':main()
