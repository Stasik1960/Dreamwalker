"""Build source-bound exact assembly definitions and addressed read-only coverage.

No runtime/catalogue/plan/world writes. SQL reads use small caches and fixed recipes;
connectivity and registry names are never evidence that something is an assembly.
"""
import argparse,collections,hashlib,itertools,json,math,shutil,sqlite3,zipfile
from functools import lru_cache
from pathlib import Path
from export_cells import canonical
from export_merge_physics import require,read,sha
ROOT=Path(__file__).resolve().parents[3];P=ROOT/'source';R=ROOT/'research'
BASE=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW-city-mesh-20261018\deliverables\block-fixes-2-20261020\game\dreamwalker-bb-materials.1-streaming.7-blocks.2.jar')
BASE_SHA='40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51'
AUDIT=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\audits\Dreamwalker-BB\2026-10-14-final-city')
PAIR_KINDS={'SOURCE_MAP_MATCHED_HALF_PAIR','SOURCE_MAP_MATCHED_GATE_PAIR','SOURCE_MAP_MATCHED_LOWER_UPPER_PAIR'}
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def hashed(value):return hashlib.sha256(canonical(value).encode()).hexdigest()
def bind(path):return {'path':str(path),'sha256':sha(path)}
def ro(path):
    db=sqlite3.connect('file:'+path.resolve().as_posix()+'?mode=ro',uri=True);db.execute('PRAGMA cache_size=-2048');db.execute('PRAGMA temp_store=FILE');db.execute('BEGIN');return db
def meta(db,key):
    row=db.execute('SELECT value FROM meta WHERE key=?',(key,)).fetchone();return json.loads(row[0]) if row else None
def effective(pose):
    props=pose.get('properties',{});value=int(props.get('variant',0))+16*int(props.get('art_bank',0))
    if props.get('trim')=='true':value+=10
    return value
def yaw(pose):return int(pose.get('properties',{}).get('rotation',0))&7
def rotate(vector,rotation):
    angle=math.pi*(rotation&7)/4;c=math.cos(angle);s=math.sin(angle)
    return [vector[0]*c-vector[2]*s,vector[1],vector[0]*s+vector[2]*c]
def part_shape(part):
    return (part['model'],part.get('altModel',part['model']),float(part.get('pitch',0)),tuple(part.get('pivot',[8,8,8])),float(part.get('extraYaw',0)),tuple(part.get('extraPivot',[8,8,8])))
def component_partition(spec,variants,target):
    complete=spec['variants'][target]['poses']['closed']['parts'];components=[]
    for index,part in enumerate(complete):
        found=[]
        for variant in variants:
            partial=spec['variants'][variant]['poses']['closed']['parts']
            if len(partial)!=1 or part_shape(partial[0])!=part_shape(part):continue
            source=partial[0]
            if part_shape(part)[2:]!=(0.0,(8,8,8),0.0,(8,8,8)):continue
            delta=((float(part.get('yaw',0))-float(source.get('yaw',0)))%360)/45
            if abs(delta-round(delta))>1e-8:continue
            delta=int(round(delta))&7;offset=rotate(source.get('offset',[0,0,0]),delta)
            relative=[(float(v)-offset[i])/16 for i,v in enumerate(part.get('offset',[0,0,0]))]
            if any(abs(v-round(v))>1e-7 for v in relative):continue
            found.append({'registryId':spec['id'],'variant':variant,'pose':variant,'relativePosition':[int(round(v)) for v in relative],'sourceShift':[0,0,0],'componentYawDelta':delta,'sourceModel':source['model'],'targetPartIndex':index})
        if not found:return None
        item=min(found,key=lambda v:v['variant']);item['equivalentSourcePartVariants']=sorted({v['variant'] for v in found});components.append(item)
    return components
def build_manifest():
    out=R/'preserved-assemblies.json';require(not out.exists(),'Preserve a bound manifest; changes require an explicit entry-preserving delta')
    db=ro(R/'native-grid-plan/plan.sqlite');source=meta(db,'binding');require(source and source['sourceHashesVerified'] and sha(source['copyManifest'])==source['copyManifestSha256'],'Immutable source binding changed')
    require(sha(BASE)==BASE_SHA,'Immutable original JAR changed');protected_path=P/'tools/native-grid/protected-v12-ids.json';protected={v['registryId'] for v in read(protected_path)['entries']}
    with zipfile.ZipFile(BASE) as jar:
        bindings={};specs={}
        def resource(name):
            raw=jar.read('bloodborne_dw/'+name);path=P/'src/architecture/resources/bloodborne_dw'/name
            require(hashlib.sha256(raw).hexdigest()==sha(path),'Current source resource differs from immutable original: '+name)
            bindings[name]={'path':str(path),'jarResource':'bloodborne_dw/'+name,'sha256':sha(path)};return json.loads(raw)
        assembly=resource('imported-assembly-v126.json');parts=resource('imported-source-parts-v127.json');aliases=resource('imported-type-aliases.json');followup=resource('imported-type-aliases-v127.json')
        def spec(rid):
            if rid not in specs:specs[rid]=resource('composite/'+rid.split(':')[1]+'.json')
            return specs[rid]
        canonical_ids={m:g['primaryRegistryId'] for g in aliases['groups'] for m in g['members']}
        for group in followup['groups']:
            for member in group['members']:canonical_ids[member]=group['primaryRegistryId']
        def canonical_id(rid):
            seen=set()
            while canonical_ids.get(rid,rid)!=rid:require(rid not in seen,'Cyclic main item aliases');seen.add(rid);rid=canonical_ids[rid]
            return rid
        defaults={}
        for recipe in assembly['recipes']:defaults.setdefault(recipe['primaryRegistryId'],recipe['installationPose'])
        partial_defaults={(v['registryId'],v['variant']):v['defaultInstallationPose'] for v in parts['parts']}
        def item(rid,variant):
            target=assembly['hiddenPartRedirects'].get(canonical_id(rid),canonical_id(rid));install=variant
            if target!=rid:install=defaults.get(target,0)
            elif target in defaults:install=variant if variant in [v['installationPose'] for v in assembly['recipes'] if v['primaryRegistryId']==target] else defaults[target]
            install=partial_defaults.get((target,install),install)
            if target=='bloodborne_dw:source_original_tree':install=0
            main=next((v['primaryId'] for v in assembly['recipes'] if v['primaryRegistryId']==target),None)
            if main is None:main=next((v['mainId'] for v in parts['parts'] if v['registryId']==target),None)
            if target=='bloodborne_dw:prototype_double_door':main='90001'
            return {'registryId':target,'catalogueNumber':main,'installationPose':install,'hiddenPartsAreNotIndependentItems':True}
        audit_sources=[{**bind(AUDIT/'AUDIT-RU.txt'),'record':'V12.4 combined type/state equivalences; V12.6 eight complete assemblies/seven functional doors'}, {**bind(AUDIT/'decisions.csv'),'record':'Seven functional doors and eight assemblies KEEP; historical IDs/aliases KEEP'}, {**bind(AUDIT/'evidence.json'),'record':'versions[v12.4-dedup/v12.6-functional-assemblies].recordedAcceptance'}]
        entries=[];by_pose={}
        def add(key,rid,variant,recipe,decision,confidence='confirmed',matchrole='complete_assembly'):
            keypose=(rid,variant)
            if keypose in by_pose:
                existing=by_pose[keypose];existing['decisionSources'].append(decision);return existing
            definition=spec(rid);old=rid in protected;functional=definition.get('openable',False)
            policy='local_original_v12' if old else 'local_original_functional' if functional else 'local_original_due_uncertainty' if confidence!='confirmed' else 'compiled_whole_or_local_original'
            result={'key':key,'registryId':rid,'exactVariant':variant,'installationPose':variant,'matchScope':'exact_pose','matchRole':matchrole,'confidence':confidence,'displayName':definition.get('displayName',rid),'recipe':recipe,'currentMainItem':item(rid,variant),'sourceSpec':bindings['composite/'+rid.split(':')[1]+'.json'],'decisionSources':[decision,*audit_sources],'preservation':{'route':'whole','runtimePolicy':policy,'requireOriginalRegistryId':old,'v12Priority':old,'preserveOneItemAndPrimaryRedirects':True,'doNotExposeHiddenParts':True,'noBackgroundMerge':True}}
            entries.append(result);by_pose[keypose]=result;return result
        require(len(assembly['recipes'])==9,'Expected mandatory exact nine recipes')
        for recipe in assembly['recipes']:
            add('v126:'+recipe['key'],recipe['primaryRegistryId'],recipe['installationPose'],recipe,{**bindings['imported-assembly-v126.json'],'record':'recipes[key='+recipe['key']+']','proof':recipe['closedSourceVertexUvRgbaProof']})
        groups=collections.defaultdict(list)
        for part in parts['parts']:groups[part['registryId']].append(part)
        reviewed=[]
        for rid,rows in sorted(groups.items()):
            definition=spec(rid);kind=definition.get('source',{}).get('kind');targets=[]
            for target in sorted({v['defaultInstallationPose'] for v in rows}):
                partials=[v['variant'] for v in rows if v['defaultInstallationPose']==target];count=len(definition['variants'][target]['poses']['closed']['parts']);proof=component_partition(definition,partials,target) if count>=2 else None
                decision='confirmed_multi_source_assembly' if kind in PAIR_KINDS and proof else 'uncertain_exact_assembly_candidate' if kind in PAIR_KINDS else 'exact_native_condition_union_not_user_assembly' if kind=='EXACT_MULTIPART_CONDITION_UNIONS' else 'single_source_property_or_art_alias_not_spatial_assembly'
                targets.append({'installationPose':target,'partialVariants':partials,'sourceKind':kind,'targetModelParts':count,'componentPartition':proof,'decision':decision})
                if kind in PAIR_KINDS:
                    recipe={'key':'SOURCE_PARTS:'+rid+':'+str(target),'componentSpace':'exact source-model part transforms under common owner shift/mount/yaw; not an owner graph','components':proof or [],'sourceModelParts':definition['variants'][target]['poses']['closed']['parts'],'normalization':[v for v in rows if v['defaultInstallationPose']==target],'componentPartitionProof':'EXACT_MODEL_PART_TRANSFORM_PARTITION' if proof else 'UNKNOWN_KEEP_LOCAL_ORIGINAL'}
                    add('source-parts:'+rid+':'+str(target),rid,target,recipe,{**bindings['imported-source-parts-v127.json'],'record':'exact parts/defaultInstallationPose; sourceSpec.source.kind='+kind},'confirmed' if proof else 'uncertain_candidate_local')
            reviewed.append({'registryId':rid,'targets':targets})
        # Existing complete source tree has an explicit one-item whole block;
        # partial variants are not all whole instances just because they share ID.
        tree=spec('bloodborne_dw:source_original_tree');require(tree['source']['kind']=='SOURCE_ORIGINAL_TREE_UNALTERED' and 'sourcePartialCompatibility' in tree,'Whole original tree evidence changed')
        treeparts=component_partition(tree,range(1,len(tree['variants'])),0)
        add('source-tree:bloodborne_dw:source_original_tree:0',tree['id'],0,{'key':'EXPLICIT_ORIGINAL_WHOLE_TREE','components':treeparts or [],'sourceModelParts':tree['variants'][0]['poses']['closed']['parts'],'componentSpace':'exact source-model part transforms; only complete variant0'}, {**bindings['composite/source_original_tree.json'],'record':'sourcePartialCompatibility + SourceOriginalTreeBlock.art canonical whole variant0'},'confirmed' if treeparts else 'uncertain_candidate_local')
        # Exact seven V12.6 functional descriptor IDs, finite meaningful poses.
        doors=('source_acacia_pressure_plate','source_allium','source_birch_grille_door','source_crimson_top','source_dark_arch_door','source_oak_prison_door','source_smooth_quartz_top')
        for path in doors:
            rid='bloodborne_dw:'+path;definition=spec(rid);require(definition.get('openable') is True and 'v126DoorPolicy' in definition,'A declared seven-door descriptor lost its explicit function policy')
            for variant,value in enumerate(definition['variants']):
                add('functional-door:'+rid+':'+str(variant),rid,variant,{'key':'V126_FUNCTIONAL_DOOR:'+rid+':'+str(variant),'components':[{'registryId':rid,'variant':variant,'relativePosition':[0,0,0],'sourceShift':[0,0,0],'componentYawDelta':0,'existingWholeDescriptor':True}],'sourceModelParts':value['poses']['closed']['parts']},{**bindings['composite/'+path+'.json'],'record':'exact v126DoorPolicy; meaningful variant '+str(variant)},matchrole='functional_existing_descriptor_or_compatibility_part')
        replacement=assembly['explicitDoorReplacement'];rid=replacement['targetRegistryId'];definition=spec(rid)
        add('explicit-door:90479-to-90001',rid,0,{'key':'EXPLICIT_90479_TO_90001','components':[{'registryId':rid,'variant':0,'relativePosition':[0,0,0],'existingWholeDescriptor':True}],'explicitDoorReplacement':replacement,'sourceModelParts':definition['variants'][0]['poses']['closed']['parts']},{**bindings['imported-assembly-v126.json'],'record':'explicitDoorReplacement + originalV12 UNCHANGED'})
        selectors=collections.defaultdict(list)
        for entry in entries:selectors[(entry['registryId'],entry['exactVariant'])].append(entry)
        matches=[];unresolved=[]
        for key,encoded in db.execute('SELECT key,data FROM poses ORDER BY key'):
            pose=json.loads(encoded);selected=selectors.get((pose['registryId'],effective(pose)),[])
            for entry in selected:
                if pose.get('legacyStairArt'):
                    unresolved.append({'assemblyKey':entry['key'],'pose':key,'poseDataSha256':hashlib.sha256(encoded.encode()).hexdigest(),'reason':'exact variant selector has separate legacyStairArt; preserve this candidate locally until exact legacy geometry is proved'});continue
                matches.append({'assemblyKey':entry['key'],'pose':key,'poseDataSha256':hashlib.sha256(encoded.encode()).hexdigest(),'matchEvidence':{'kind':'EXACT_EFFECTIVE_VARIANT_OF_CONFIRMED_DESCRIPTOR','registryId':pose['registryId'],'effectiveVariant':effective(pose),'sourceSpecSha256':entry['sourceSpec']['sha256'],'decisionSource':entry['decisionSources'][0]},'sourcePose':pose})
        plan_bindings={k:meta(db,k) for k in ('binding','catalog','runtime','compositionGeneration','contextExtension','offlineApplied')};db.close()
    report={'schemaVersion':1,'status':'CONFIRMED_FINITE_ASSEMBLY_MANIFEST','sourceBinding':source,'sourceBindings':{'immutableBaseJar':{**bind(BASE)},'currentProduction':bind(P/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-native-grid.1.jar'),'protectedV12':bind(protected_path),'initialNineAudit':bind(R/'function-qa/preserved-assembly-addressed-input-audit.json'),'resources':bindings},'entries':entries,'poseMatches':matches,'unresolvedCandidates':unresolved,'hiddenPartRedirects':assembly['hiddenPartRedirects'],'explicitDoorReplacement':replacement,'sourcePartsReview':reviewed,'v124IdentityRelations':{'resource':bindings['imported-type-aliases.json'],'followupResource':bindings['imported-type-aliases-v127.json'],'groups':len(aliases['groups']),'exactMappings':len(aliases['mappings']),'scope':aliases['scope'],'policy':'Exact one-item/type/variant/ID equivalences remain authoritative. Type dedup alone is not a spatial multi-component assembly; do not turn all variants or families into whole objects.'},'planBindings':plan_bindings,'policy':{'onlyExactPosesOrFiniteComponentRecipes':True,'noRegistryFamilyBlacklist':True,'noOwnerGraphClosure':True,'v12Priority':True,'nonV12MayUseNewWholeTemplateAndPrimaryMappedItem':True,'unknownExactCandidateKeepsLocalWholeMechanism':True},'databaseWrites':0,'worldFilesRead':0,'mapWrites':0,'catalogueWrites':0,'gameProcessesStarted':0}
    write(out,report);print(json.dumps({'status':report['status'],'manifest':str(out),'sha256':sha(out),'entries':len(entries),'poseMatches':len(matches),'unresolvedCandidates':len(unresolved),'sourcePartsGroupsReviewed':len(reviewed)}))
def evidence_bindings():
    manifest=read(R/'preserved-assemblies.json');folder=R/'preserved-assembly-evidence';folder.mkdir(exist_ok=True);records={}
    for entry in manifest['entries']:
        for decision in entry['decisionSources']:
            original=Path(decision['path'])
            if original.parent!=AUDIT:continue
            require(sha(original)==decision['sha256'],'Assigned historical decision changed after manifest binding')
            local=folder/original.name
            if local.exists():require(sha(local)==decision['sha256'],'Existing staged historical decision changed')
            else:shutil.copyfile(original,local)
            records[str(original)]={'originalPath':str(original),'path':str(local),'sha256':sha(local)}
    out=R/'preserved-assembly-evidence-bindings.json';write(out,{'schemaVersion':1,'status':'PASS','manifest':bind(R/'preserved-assemblies.json'),'records':list(records.values()),'byteIdenticalCopies':True,'originalWrites':0,'databaseWrites':0,'mapWrites':0})
    print(json.dumps({'status':'PASS','path':str(out),'sha256':sha(out),'records':len(records)}))
def partition_options(spec,partials,target):
    """Finite exact model-part options, including explicit fractional SourceShift.

    These are coverage evidence only; an uncertain manifest entry stays local.
    Each alternative has its own root/shift/yaw, never a broad variant alias.
    """
    options=[]
    for index,part in enumerate(spec['variants'][target]['poses']['closed']['parts']):
        found=[]
        for variant in partials:
            pieces=spec['variants'][variant]['poses']['closed']['parts']
            if len(pieces)!=1 or part_shape(pieces[0])!=part_shape(part):continue
            source=pieces[0]
            if part_shape(part)[2:]!=(0.0,(8,8,8),0.0,(8,8,8)):continue
            delta=((float(part.get('yaw',0))-float(source.get('yaw',0)))%360)/45
            if abs(delta-round(delta))>1e-8:continue
            delta=int(round(delta))&7;offset=rotate(source.get('offset',[0,0,0]),delta)
            relative=[(float(v)-offset[i])/16 for i,v in enumerate(part.get('offset',[0,0,0]))]
            cell=[int(round(v)) for v in relative];residual=[relative[i]-cell[i] for i in range(3)];shift=rotate(residual,-delta)
            found.append({'registryId':spec['id'],'pose':variant,'variant':variant,'relativePosition':cell,'sourceShift':shift,'componentYawDelta':delta,'targetPartIndex':index,'sourceModel':source['model']})
        if not found:return []
        options.append(found)
    require(math.prod(map(len,options))<=128,'Finite source-part partition alternatives exceed the addressed bound')
    return list(itertools.product(*options))
def coverage():
    manifest_path=R/'preserved-assemblies.json';manifest_sha=sha(manifest_path);manifest=read(manifest_path)
    out=R/'preserved-assemblies-coverage.json';groups_path=R/'preserved-assembly-component-matches.jsonl';require(not out.exists() and not groups_path.exists(),'Preserve existing read-only coverage evidence')
    db=ro(R/'native-grid-plan/plan.sqlite');require(meta(db,'binding')==manifest['sourceBinding'],'Assembly manifest belongs to another immutable plan source')
    tables={row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")};poses={};by_selector=collections.defaultdict(list)
    for key,encoded in db.execute('SELECT key,data FROM poses'):
        pose=json.loads(encoded);poses[key]=pose;by_selector[(pose['registryId'],effective(pose))].append(key)
    # One addressed aggregation per fragment table; no world or chunk scans.
    matched={row['pose'] for row in manifest['poseMatches']};question=','.join('?' for _ in matched);fragment_stats={}
    for table in ('fragments','substrate_fragments'):
        fragment_stats[table]={pose:{'owners':owners,'cells':cells} for pose,owners,cells in db.execute('SELECT pose,count(DISTINCT uuid),count(*) FROM '+table+' WHERE pose IN ('+question+') GROUP BY pose',tuple(matched))} if matched else {}
    @lru_cache(maxsize=256)
    def flags(dim,uid,x,y,z,kind):
        current=db.execute('SELECT kind FROM selected_objects WHERE dim=? AND uuid=?',(dim,uid)).fetchone();activated=db.execute('SELECT 1 FROM activated_native WHERE dim=? AND uuid=?',(dim,uid)).fetchone()
        previous=db.execute('SELECT 1 FROM stage1_selected_objects WHERE dim=? AND uuid=?',(dim,uid)).fetchone() if 'stage1_selected_objects' in tables else None
        placement=db.execute('SELECT composition,type_index FROM placements WHERE dim=? AND x=? AND y=? AND z=?',(dim,x,y,z)).fetchone()
        prior=db.execute('SELECT composition,type_index FROM stage1_placements WHERE dim=? AND x=? AND y=? AND z=?',(dim,x,y,z)).fetchone() if 'stage1_placements' in tables else None
        bridge=db.execute('SELECT host_state FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',(dim,x,y,z)).fetchone()
        return {'currentSelectedKind':current[0] if current else None,'currentActivatedNative':bool(activated),'stage1Selected':bool(previous),'currentPlacementAtRoot':{'composition':placement[0],'typeIndex':placement[1]} if placement else None,'stage1PlacementAtRoot':{'composition':prior[0],'typeIndex':prior[1]} if prior else None,'retainedHostBridgeAtRoot':json.loads(bridge[0]) if bridge else None,'rootKind':kind}
    direct=[];total=collections.Counter()
    for match in manifest['poseMatches']:
        pose=match['pose'];entry=next(v for v in manifest['entries'] if v['key']==match['assemblyKey']);kinds=dict(db.execute('SELECT kind,count(*) FROM objects WHERE pose=? GROUP BY kind',(pose,)))
        selected=dict(db.execute('SELECT s.kind,count(*) FROM objects o JOIN selected_objects s ON s.dim=o.dim AND s.uuid=o.uuid WHERE o.pose=? GROUP BY s.kind',(pose,)))
        active=db.execute('SELECT count(*) FROM objects o JOIN activated_native a ON a.dim=o.dim AND a.uuid=o.uuid WHERE o.pose=?',(pose,)).fetchone()[0]
        witnesses=[];overlap=collections.Counter()
        for dim,uid,x,y,z,kind in db.execute('SELECT dim,uuid,x,y,z,kind FROM objects WHERE pose=?',(pose,)):
            value=flags(dim,uid,x,y,z,kind)
            if value['stage1Selected'] or value['stage1PlacementAtRoot']:overlap['stage1OwnerOrRootTouches']+=1
            if value['currentSelectedKind'] or value['currentActivatedNative'] or value['currentPlacementAtRoot']:overlap['currentOwnerOrRootTouches']+=1
            unsafe=(kind=='static' and bool(value['currentSelectedKind'] or value['currentActivatedNative'])) or (kind=='substrate' and value['currentPlacementAtRoot'] is not None)
            if unsafe:overlap['currentPotentialIndependentCellRoute']+=1
            if len(witnesses)<6 and (unsafe or value['stage1Selected']):witnesses.append({'dimension':dim,'uuid':uid,'root':[x,y,z],**value})
        row={'assemblyKey':entry['key'],'pose':pose,'globalKinds':kinds,'currentSelectedKinds':selected,'currentActivatedNative':active,'fragmentRefs':{t:fragment_stats[t].get(pose,{'owners':0,'cells':0}) for t in fragment_stats},'overlap':dict(overlap),'fullStaticObjectsRequiringWholeRoute':kinds.get('static',0),'runtimePolicy':entry['preservation']['runtimePolicy'],'boundedWitnesses':witnesses}
        direct.append(row);total['fullStaticObjectsRequiringWholeRoute']+=row['fullStaticObjectsRequiringWholeRoute'];total.update(overlap)
    # Fixed component tuples only. No recursive neighbour/owner graph traversal.
    tuple_summary=[];seen=set();gaps=[]
    with groups_path.open('w',encoding='utf-8',newline='\n') as stream:
        for entry in manifest['entries']:
            key=entry['key'];recipe=entry['recipe'];variants=[]
            if key.startswith('v126:'):variants=[recipe['components']]
            elif key.startswith('source-parts:') or key.startswith('source-tree:'):
                spec=read(Path(entry['sourceSpec']['path']));require(sha(entry['sourceSpec']['path'])==entry['sourceSpec']['sha256'],'Exact source spec changed')
                partials=[v['variant'] for v in recipe.get('normalization',[])] if key.startswith('source-parts:') else list(range(1,len(spec['variants'])))
                variants=partition_options(spec,partials,entry['exactVariant'])
            if not variants or all(len(v)<2 for v in variants):continue
            counts=collections.Counter();examples=[]
            for components in variants:
                first=components[0];first_variant=first.get('pose',first.get('variant'));anchors=by_selector.get((first['registryId'],first_variant),[])
                for anchor_pose in anchors:
                    first_pose=poses[anchor_pose]
                    if first_pose.get('legacyStairArt'):continue
                    base_yaw=(yaw(first_pose)-first.get('componentYawDelta',0))&7;anchor_shift=rotate(first_pose.get('shift',[0,0,0]),yaw(first_pose));anchor_mount=float(first_pose.get('mountY',0));first_shift=rotate(first.get('sourceShift',[0,0,0]),first.get('componentYawDelta',0))
                    for dim,uid,x,y,z,kind in db.execute("SELECT dim,uuid,x,y,z,kind FROM objects WHERE pose=? AND kind!='substrate'",(anchor_pose,)):
                        counts['actualAnchorObjectsInspected']+=1;members=[];valid=True
                        for component in components:
                            delta=[component['relativePosition'][i]-first['relativePosition'][i] for i in range(3)];delta=rotate(delta,base_yaw)
                            if any(abs(v-round(v))>1e-7 for v in delta):valid=False;counts['nonIntegerSourceRootPose']+=1;break
                            at=[x+int(round(delta[0])),y+int(round(delta[1])),z+int(round(delta[2]))];wanted_variant=component.get('pose',component.get('variant'));expected_yaw=(base_yaw+component.get('componentYawDelta',0))&7
                            expected_shift=rotate(component.get('sourceShift',[0,0,0]),component.get('componentYawDelta',0));expected_shift=rotate([expected_shift[i]-first_shift[i] for i in range(3)],base_yaw);candidates=[]
                            for other,encoded_pose,other_kind in db.execute("SELECT uuid,pose,kind FROM objects WHERE dim=? AND x=? AND z=? AND y=? AND kind!='substrate'",(dim,at[0],at[2],at[1])):
                                p=poses[encoded_pose];props=p.get('properties',{});first_props=first_pose.get('properties',{})
                                if p['registryId']!=component['registryId'] or effective(p)!=wanted_variant or yaw(p)!=expected_yaw or p.get('legacyStairArt'):continue
                                if props.get('profile','base')!=first_props.get('profile','base') or props.get('open','false')!=first_props.get('open','false'):continue
                                shift=rotate(p.get('shift',[0,0,0]),yaw(p));mount=float(p.get('mountY',0))
                                if any(abs(shift[i]-anchor_shift[i]-expected_shift[i]+(mount-anchor_mount if i==1 else 0))>1e-6 for i in range(3)):continue
                                candidates.append({'uuid':other,'pose':encoded_pose,'root':at,'kind':other_kind,'component':component,'planRoute':flags(dim,other,*at,other_kind)})
                            if len(candidates)!=1:valid=False;counts['missingOrAmbiguousExactComponent']+=1;break
                            members.append(candidates[0])
                        if not valid:continue
                        if len({v['uuid'] for v in members})!=len(members):counts['reusedOwnerRejected']+=1;continue
                        signature=hashed([key,dim,sorted(v['uuid'] for v in members)])
                        if signature in seen:continue
                        seen.add(signature);pilot=any(v['planRoute']['stage1Selected'] or v['planRoute']['stage1PlacementAtRoot'] for v in members);current=any(v['planRoute']['currentSelectedKind'] or v['planRoute']['currentActivatedNative'] or v['planRoute']['currentPlacementAtRoot'] for v in members)
                        value={'groupKey':signature,'assemblyKey':key,'dimension':dim,'anchor':[x,y,z],'baseYaw':base_yaw,'components':members,'stage1PilotOverlap':pilot,'currentPlanOverlap':current,'decisionConfidence':entry['confidence'],'preservation':entry['preservation'],'proof':'FINITE_EXACT_SAVED_POSE_ROOT_YAW_PROFILE_OPEN_SHIFT_MOUNT_COMPONENT_TUPLE','residentNbtReRead':False,'applyAuthorization':False}
                        stream.write(canonical(value)+'\n');counts['exactGroups']+=1;counts['stage1PilotOverlapGroups']+=pilot;counts['currentPlanOverlapGroups']+=current
                        if len(examples)<4:examples.append({'groupKey':signature,'anchor':[x,y,z],'dimension':dim,'stage1PilotOverlap':pilot,'currentPlanOverlap':current})
            tuple_summary.append({'assemblyKey':key,'finiteTransformAlternatives':len(variants),'counts':dict(counts),'boundedExamples':examples})
    require(sha(manifest_path)==manifest_sha,'Bound manifest changed during addressed coverage')
    report={'schemaVersion':1,'status':'READ_ONLY_FINITE_ASSEMBLY_PLAN_COVERAGE_NOT_APPLY_GATE','manifest':bind(manifest_path),'sourceBinding':manifest['sourceBinding'],'planBindings':{k:meta(db,k) for k in ('catalog','runtime','compositionGeneration','offlineApplied','contextExtension','contextRegionPhaseComplete','contextRegionBinding')},'directPoseCoverage':direct,'directTotals':dict(total),'componentTupleCoverage':tuple_summary,'componentGroups':{'path':str(groups_path),'sha256':sha(groups_path),'groups':len(seen)},'unresolvedCandidates':manifest['unresolvedCandidates'],'limits':['Component evidence uses immutable saved objects/SourcePoses and fixed recipe transforms, not name/adjacency/owner-graph guesses.','Raw resident NBT and whole transfer physics must still be validated by the migration apply gate; this report cannot authorize a write.','stage1 is archived originally applied pilot; current is the current staged plan. Their overlap counts are reported separately.','Source-part alternatives each retain their own exact root/shift/yaw, never a broad partial-pose override.'],'memoryPolicy':{'sqliteCacheKiB':2048,'tempStore':'FILE','streamedGroupJsonl':True,'objectFlagLru':256},'databaseWrites':0,'worldFilesRead':0,'mapWrites':0,'catalogueWrites':0,'gameProcessesStarted':0}
    db.close();write(out,report);print(json.dumps({'status':report['status'],'coverage':str(out),'sha256':sha(out),'componentGroups':len(seen),'directTotals':dict(total)}))
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=['manifest','evidence-bindings','coverage']);a=p.parse_args()
    {'manifest':build_manifest,'evidence-bindings':evidence_bindings,'coverage':coverage}[a.mode]()
if __name__=='__main__':main()
