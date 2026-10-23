"""Finite old-native whole-item inputs from already accepted exact pose sets.

Inspection reads indexed plan rows and at most one saved root palette per exact
pose/source-status group. It never discovers a world/owner/component or changes
the plan. Materialize ONCE after final FULL compose, into a fresh file only.
"""
import argparse, collections, hashlib, json, sqlite3, sys, time
from pathlib import Path
sys.dont_write_bytecode=True
SOURCE=Path(__file__).resolve().parents[2];ROOT=SOURCE.parent;R=ROOT/'research'
sys.path.insert(0,str(SOURCE/'tools/native-grid/qa'))
import full_native_inputs as saved

def require(v,m):
    if not v:raise RuntimeError(m)
def canonical(v):return json.dumps(v,ensure_ascii=False,sort_keys=True,separators=(',',':'))
def read(p):return json.loads(Path(p).read_text(encoding='utf-8-sig'))
def sha(p):
    h=hashlib.sha256()
    with Path(p).open('rb')as f:
        for b in iter(lambda:f.read(1<<20),b''):h.update(b)
    return h.hexdigest()
def ref(p):return {'path':str(Path(p).resolve()),'sha256':sha(p)}
def readonly(p):
    db=sqlite3.connect('file:'+Path(p).resolve().as_posix()+'?mode=ro',uri=True);db.row_factory=sqlite3.Row
    db.execute('PRAGMA query_only=ON');db.execute('PRAGMA cache_size=-2048');db.execute('PRAGMA temp_store=FILE');db.execute('BEGIN');return db
def meta(db,k):
    row=db.execute('SELECT value FROM meta WHERE key=?',(k,)).fetchone();return json.loads(row[0])if row else None
def records(path,array):
    active=False;capturing=False;pending=[]
    for raw in Path(path).open(encoding='utf-8'):
        line=raw.rstrip('\r\n')
        if not active:
            if line=='  "'+array+'": [':active=True
            continue
        if line=='  ],':return
        if line=='    {':capturing=True;pending=[]
        if capturing:
            pending.append(line)
            if line in('    },','    }'):
                yield json.loads('\n'.join(pending).rstrip(','));capturing=False
    require(active and not capturing,'Unexpected finite catalog JSON layout: '+array)
def jsonlines(path):
    return [json.loads(line)for line in Path(path).read_text(encoding='utf-8-sig').splitlines()if line.strip()]
def full_props(pose,caps):
    cap=caps.get(pose['registryId']);require(cap and cap.get('registryPresent')is True and isinstance(cap.get('defaultProperties'),dict),'Missing actual registered property defaults: '+pose['registryId'])
    props=dict(cap['defaultProperties']);require(set(pose.get('properties',{}))<=set(props),'Unknown canonical property in accepted pose')
    props.update(pose.get('properties',{}));return dict(sorted(props.items()))
def identity(pose,caps):
    return canonical({'registryId':pose['registryId'],'properties':full_props(pose,caps),'shift':[float(v)for v in pose.get('shift',[0,0,0])],'mountY':float(pose.get('mountY',0))})
def write(path,value):
    path=Path(path);require(not path.exists(),'Preserve immutable output: '+str(path));path.parent.mkdir(parents=True,exist_ok=True)
    with path.open('x',encoding='utf-8',newline='\n')as stream:
        json.dump(value,stream,ensure_ascii=False,indent=2);stream.write('\n')

def inspect(a):
    started=time.monotonic();db=readonly(a.plan/'plan.sqlite');old=None
    try:
        binding=meta(db,'binding');require(binding and binding.get('sourceHashesVerified')and binding.get('originalPlanReadOnly'),'Verified immutable source binding required')
        require(not meta(db,'scope')and meta(db,'fullNativeFunctionGateRequired')is True,'This finite item producer is for the promoted FULL plan only')
        oldpath=Path(binding['oldPlan'])/'plan.sqlite';stat=oldpath.stat();require(stat.st_size==binding['oldDatabaseBytes']and stat.st_mtime_ns==binding['oldDatabaseMtimeNs'],'Old discovery binding changed')
        require(sha(binding['copyManifest'])==binding['copyManifestSha256'],'Immutable source manifest changed');old=readonly(oldpath)
        caps_path=Path(binding['oldPlan']).parents[2]/'tools/ordinary-stage1/source-capabilities.json'
        require(sha(caps_path)=='4631f03fe354733a230dab9d5bbffc339a3cbf3fefb9362544199569685dc461','Actual registered source capability/default-state evidence changed')
        caps={v['registryId']:v for v in read(caps_path)['states']}
        manifest=read(a.manifest);require(manifest['sourceBinding']==binding,'Assembly manifest source binding differs')
        require(sha(a.manifest)=='affb003bfdb9a417e76707af9b25e74164fc257c06d5d11375bc78953f229229','Accepted assembly manifest changed')
        tree=read(a.tree_proof);require(sha(a.tree_proof)=='7de51902eb54e31e9473fec40274dfb3587f3b67a82065629c9c89b5f0cf6724'and tree['status']=='PASS'and len(tree['poses'])==17,'Accepted finite seventeen-tree proof changed')
        require(sha(a.protected_v12)=='78d4cfafbb201e737c6d2cab05f91cc2d1b5b54acb64d36b426e26f900c2347f','Original-v12 protection evidence changed')
        require(sha(a.whole_objects)=='c1823f609bb59f5647213bee7c6b1c46a4d7636c6ae3535ebd0b6f260c848055','Accepted 72-pose whole request bytes changed')
        require(sha(a.assembly_requests)=='58e4fa04b11f01244f42e7cf955dd67aac05fda314e5b232993962988a642d51','Accepted 26-pose assembly whole request bytes changed')
        v12={v['registryId']for v in read(a.protected_v12)['entries']};entries={v['key']:v for v in manifest['entries']}
        templates=jsonlines(a.whole_objects)+jsonlines(a.assembly_requests);scope=collections.defaultdict(list)
        for row in templates:scope[row['pose']].append({'kind':'accepted_whole_template_request','key':row['key']})
        for row in manifest['poseMatches']:
            entry=entries[row['assemblyKey']];scope[row['pose']].append({'kind':'accepted_assembly_exact_pose','entryKey':entry['key'],'preservation':entry['preservation'],'confidence':entry.get('confidence'),'sourceMainItem':entry.get('currentMainItem'),'poseDataSha256':row['poseDataSha256']})
        legacy=SOURCE/'src/architecture/resources/bloodborne_dw/static-architecture-v1274.json'
        require(sha(legacy)=='3a9d3325e31818484689e2cc4d22fa63c6168338fa721ee3315935ddee9124f2','Accepted original native recipe/pose resource changed')
        poses={v['key']:v for v in records(legacy,'poses')};legacy_identity=collections.defaultdict(list)
        for key,value in poses.items():legacy_identity[identity(value,caps)].append(key)
        # The seventeen existing exact keys are translated only to their already saved SourcePose; no new pose product.
        for row in tree['poses']:
            lp=row['legacyPose'];value={'registryId':lp['registryId'],'properties':lp['properties'],'shift':[float(v)for v in lp['shift']],'mountY':float(lp['mountY']),'legacyStairArt':''}
            key='sp_'+hashlib.sha256(canonical(value).encode()).hexdigest()[:24]
            scope[key].append({'kind':'accepted_exact_sprite_tree_pose','legacyPose':row['poseKey']})
        overlays={v['id']:v['overlayRecipe']for v in read(SOURCE/'src/architecture/resources/bloodborne_dw/restored-material-slabs.json')['materials']if 'overlayRecipe'in v}
        candidates=[];summary=collections.Counter();excluded=[];missing=[];cache_proofs=[]
        # object_pose is mandatory; no whole objects/native census is performed.
        for key,evidence in sorted(scope.items()):
            encoded=db.execute('SELECT data FROM poses WHERE key=?',(key,)).fetchone();require(encoded is not None,'Accepted exact SourcePose disappeared: '+key);value=json.loads(encoded[0]);data_sha=hashlib.sha256(encoded[0].encode()).hexdigest()
            for authority in evidence:
                if authority.get('poseDataSha256'):require(authority['poseDataSha256']==data_sha,'Accepted exact pose bytes changed')
            if value['registryId']in v12:excluded.append({'pose':key,'reason':'original_protected_v12'});continue
            allowed=any(v['kind']in('accepted_whole_template_request','accepted_exact_sprite_tree_pose')or(v['kind']=='accepted_assembly_exact_pose'and v['confidence']=='confirmed'and v['preservation'].get('runtimePolicy')=='compiled_whole_or_local_original')for v in evidence)
            if not allowed:excluded.append({'pose':key,'reason':'manifest_functional_or_uncertain_local_original_not_auto_promoted_to_native_whole_item'});continue
            native_rows=[];counts=collections.Counter();identity_hash=hashlib.sha256();representatives={}
            sql='''SELECT o.*,s.kind AS selected_kind FROM objects o INDEXED BY object_pose LEFT JOIN selected_objects s ON s.dim=o.dim AND s.uuid=o.uuid WHERE o.pose=? ORDER BY o.dim,o.uuid'''
            for raw in db.execute(sql,(key,)):
                obj=dict(raw);counts['observed']+=1
                if obj['selected_kind']is not None:counts['selected_'+obj['selected_kind']]+=1;continue
                if obj['kind']not in('whole','preserve'):counts['not_whole_policy_'+obj['kind']]+=1;continue
                owner=(obj['dim'],obj['uuid'])
                if old.execute('SELECT 1 FROM stored_owners WHERE dim=? AND uuid=? LIMIT 1',owner).fetchone()or old.execute('SELECT 1 FROM be_owners WHERE dim=? AND uuid=? LIMIT 1',owner).fetchone():counts['original_owned_be_mechanism']+=1;continue
                counts['old_native_unselected_candidates']+=1;token=(obj['kind'],obj['reason'],obj['source_status']);identity_hash.update((canonical(obj)+'\n').encode());representatives.setdefault(token,obj)
            summary.update(counts)
            if not representatives:excluded.append({'pose':key,'reason':'no_unselected_old_native_whole_fallback','counts':dict(counts)});continue
            keys=legacy_identity.get(identity(value,caps),[])
            if not keys:missing.append({'pose':key,'kind':'missing_canonical_legacy_pose'});continue
            if value.get('legacyStairArt',''):missing.append({'pose':key,'kind':'unsupported_legacy_stair_art_in_whole_item'});continue
            for token,obj in sorted(representatives.items(),key=lambda x:str(x[0])):
                root=(obj['x'],obj['y'],obj['z']);cached=old.execute('SELECT region_path,region_hash,compression,payload FROM chunk_cache WHERE dim=? AND x=? AND z=?',(obj['dim'],root[0]>>4,root[2]>>4)).fetchone()
                require(cached is not None,'Missing saved representative root chunk');actual={'registryId':'minecraft:air','properties':{}}
                for section in saved.saved_sections(cached['payload'],cached['compression']):
                    if saved.compound(section)['Y'].value!=root[1]>>4:continue
                    block=saved.section_blocks(section)
                    if block is not None:pal,index=block;actual=saved.state(pal[index[(root[0]&15)+16*(root[2]&15)+256*(root[1]&15)]])
                    break
                rid=actual['registryId'];recipe=int(rid.rsplit('_',1)[1])*128+int(actual['properties']['part'])if rid.startswith('bloodborne_dw:static_architecture_')else overlays.get(rid)
                witness={'object':obj,'root':list(root),'sourceState':actual,'recipeIndex':recipe,'provenanceGroup':list(token),'cachedSource':{'dimension':obj['dim'],'chunk':[root[0]>>4,root[2]>>4],'boundRegion':cached['region_path'],'boundRegionSha256':cached['region_hash'],'cachedCompressedPayloadSha256':hashlib.sha256(cached['payload']).hexdigest()}}
                cache_proofs.append(witness['cachedSource'])
                if recipe is None:missing.append({'pose':key,'kind':'old_unowned_whole_root_not_exact_native_recipe','witness':witness});continue
                native_rows.append(witness)
            candidates.append({'pose':key,'poseDataSha256':data_sha,'sourcePose':value,'scopeEvidence':evidence,'counts':dict(counts),'candidateIdentitySha256':identity_hash.hexdigest(),'legacyPoseCandidates':keys,'nativeRootWitnesses':native_rows})
        required={w['recipeIndex']for row in candidates for w in row['nativeRootWitnesses']};recipes={i:v for i,v in enumerate(records(legacy,'recipes'))if i in required};runtime={}
        for row in candidates:
            found=set()
            for witness in row['nativeRootWitnesses']:
                recipe=recipes[witness['recipeIndex']];rootparts=[v for v in recipe['fragments']if v['origin']==[0,0,0]and v['pose']in row['legacyPoseCandidates']]
                if len(rootparts)!=1 or recipe['resident']<0 or recipe['fragments'][recipe['resident']]!=rootparts[0]:missing.append({'pose':row['pose'],'kind':'representative_has_no_exact_canonical_native_whole_resident','witness':witness,'recipe':recipe});continue
                key=rootparts[0]['pose'];found.add(key);witness['canonicalLegacyPose']=key;witness['rootRecipe']=recipe
                lp=poses[key];runtime[key]={'legacyPose':key,'registryId':lp['registryId'],'properties':full_props(lp,caps),'shift':[float(v)for v in lp['shift']],'mountY':float(lp['mountY'])}
                if float(lp['mountY'])!=0:missing.append({'pose':row['pose'],'legacyPose':key,'kind':'nonzero_mount_y_not_encoded_by_existing_whole_art_item','mountY':lp['mountY']})
            row['observedCanonicalLegacyPoses']=sorted(found)
            row['normalizationAuthority']=[v for v in row['scopeEvidence']if v['kind']=='accepted_assembly_exact_pose']
        document={'schemaVersion':1,'poses':[runtime[k]for k in sorted(runtime)]}
        report={'schemaVersion':1,'status':'PROVISIONAL_FINITE_NATIVE_WHOLE_ITEM_INPUTS'if not missing else'BLOCKED_FINITE_INPUTS','sourceBinding':binding,'sourceCatalog':ref(legacy),'sourceCapabilities':ref(caps_path),'scopeInputs':{'wholeObjects':ref(a.whole_objects),'assemblyRequests':ref(a.assembly_requests),'preservedAssemblies':ref(a.manifest),'accepted17TreeProof':ref(a.tree_proof),'originalV12':ref(a.protected_v12)},'scopeExactPoses':len(scope),'summary':dict(summary)|{'nativeWholeFallbackSourcePoses':len(candidates),'sharedLegacyItemPoses':len(runtime),'rootPaletteRepresentatives':len(cache_proofs),'missing':len(missing)},'poses':candidates,'excluded':excluded,'missing':missing,'provisionalRuntimeDocument':document,'runtimeDocumentSha256':hashlib.sha256((json.dumps(document,ensure_ascii=False,indent=2)+'\n').encode()).hexdigest(),'capabilityDefaultResolution':'Recorded actual registered defaultProperties merged with canonical legacy pose overrides, exactly as CompositeData.state; all runtime property keys are emitted.','normalizationPolicy':'Runtime must invoke existing pose.block().art(pose.state(),pose.payload()); accepted source-parts/assembly primary normalization is allowed only by the bound exact manifest authority, never assumed to keep the raw variant. Nonzero MountY is explicitly blocked.','coverageLimit':'Only the fixed accepted exact-pose sets, indexed object_pose rows and one old native root representative per pose/source-status policy group. Stored_owners/be_owners presence excludes existing owned roots. No new census, classes, owners or graph traversal.','compositionGeneration':meta(db,'compositionGeneration'),'currentNativeRetention':meta(db,'nativeLocalRetention'),'applyAuthorization':False,'worldFilesRead':0,'databaseWrites':0,'mapWrites':0,'runtimeResourceWrites':0,'gameProcessesStarted':0,'generator':ref(Path(__file__)),'seconds':time.monotonic()-started}
        return report,db
    except BaseException:
        db.close();raise
    finally:
        if old:old.close()

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=('inspect','materialize'));p.add_argument('--plan',type=Path,default=R/'native-grid-plan');p.add_argument('--manifest',type=Path,default=R/'preserved-assemblies.json');p.add_argument('--whole-objects',type=Path,default=R/'whole-objects.jsonl');p.add_argument('--assembly-requests',type=Path,default=R/'preserved-assembly-plan-preview.whole-objects.jsonl');p.add_argument('--tree-proof',type=Path,default=R/'native-functional-bases/unknown-native-tree-footprints.json');p.add_argument('--protected-v12',type=Path,default=SOURCE/'tools/native-grid/protected-v12-ids.json');p.add_argument('--out',type=Path,required=True);p.add_argument('--receipt',type=Path);p.add_argument('--compositions',type=Path);p.add_argument('--compositions-sha256');a=p.parse_args()
    report,db=inspect(a)
    try:
        if a.mode=='inspect':
            require(a.out.resolve().is_relative_to(R),'Provisional inspection writes only research');write(a.out,report);print(canonical({'status':report['status'],'out':ref(a.out),'summary':report['summary']}));return
        require(a.receipt and a.compositions and a.compositions_sha256,'Final materialization requires fresh resource+receipt and explicit final raw bytes')
        require(not report['missing'],'Resolve the concrete finite item input gaps before final materialization')
        require(not meta(db,'applyStarted')and meta(db,'successfulPilotGate'),'Final materialization is after accepted pilot and before FULL apply')
        require(sha(a.compositions)==a.compositions_sha256 and meta(db,'compositionsSha256')==a.compositions_sha256,'Completed FULL raw binding differs')
        generation=meta(db,'compositionGeneration');require(generation and generation['compositionsSha256']==a.compositions_sha256,'Complete final FULL compose before materializing the shared item table')
        require(generation.get('nativeLocalRetention')==meta(db,'nativeLocalRetention'),'Native retention changed after composition; refresh the completed compose first')
        require(not a.out.exists()and not a.receipt.exists(),'Final runtime resource and provenance receipt must both be fresh')
        document=report['provisionalRuntimeDocument'];write(a.out,document)
        require(sha(a.out)==report['runtimeDocumentSha256'],'Final runtime resource bytes differ from the exact declared LF document')
        final={'schemaVersion':1,'status':'PASS_EXACT_RETAINED_NATIVE_WHOLE_ITEM_RESOURCE_NOT_GAMEPLAY','kind':'FINITE_OBSERVED_RETAINED_NATIVE_WHOLE_ITEMS','sourceBinding':report['sourceBinding'],'compositionGeneration':generation,'finalCompositions':{'path':str(a.compositions.resolve()),'sha256':a.compositions_sha256},'resource':ref(a.out),'observedLegacyPoses':[v['legacyPose']for v in document['poses']],'originalV12Excluded':True,'resourcePoseCount':len(document['poses']),'report':report,'gameplayPass':False,'applyAuthorization':False,'worldFilesRead':0,'databaseWrites':0,'gameProcessesStarted':0}
        write(a.receipt,final);print(canonical({'status':final['status'],'out':ref(a.out),'receipt':ref(a.receipt),'poses':len(document['poses'])}))
    finally:db.close()
if __name__=='__main__':main()
