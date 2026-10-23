"""Read-only 26-pose whole-template readiness and later fresh-file extension.

Never invokes CatalogCompiler or modifies a plan, fragment bank, current template
file or runtime resources.  The 72 existing JSONL records remain a byte prefix.
"""
import argparse,collections,hashlib,json,math,sys,zipfile
from pathlib import Path
from export_preserved_assemblies import P,R,BASE,BASE_SHA,ro,meta,read,sha,bind,write,canonical,require
from export_cells import FragmentStore,read_lines,canonical_boxes,area
sys.path.insert(0,str(P/'tools/native-grid/compiler'))
from catalog import compiler_fingerprint

MANIFEST_SHA='affb003bfdb9a417e76707af9b25e74164fc257c06d5d11375bc78953f229229'
REQUESTS_SHA='58e4fa04b11f01244f42e7cf955dd67aac05fda314e5b232993962988a642d51'
ALGORITHM='111b89688889fa961f2ae19bc5e0f0683d2ec4c9bfa8fbcf0b36117a5e96e13f'
CATALOG_SHA='638d84a879a7823fd4ed866b2109fbe864cda4690383745fbc5a53bc940fa550'
REQUESTS=R/'preserved-assembly-plan-preview.whole-objects.jsonl'
WHOLES=R/'whole-objects.jsonl'
RESOURCES=P/'src/architecture/resources'

def hashed(value):return hashlib.sha256(canonical(value).encode()).hexdigest()
def pose_identity(value):
    return {k:value.get(k,default) for k,default in [('registryId',None),('properties',{}),('shift',[0,0,0]),('mountY',0),('legacyStairArt',''),('sourceCarrierContextVersion',None)]}
def inspect():
    manifest_path=R/'preserved-assemblies.json';preview_path=R/'preserved-assembly-plan-preview.json'
    require(sha(manifest_path)==MANIFEST_SHA and sha(REQUESTS)==REQUESTS_SHA,'Frozen assembly manifest/request changed')
    require(compiler_fingerprint()==ALGORITHM,'Pinned compiler source changed')
    require(sha(RESOURCES/'assets/bloodborne_dw/native_grid/catalog.json')==CATALOG_SHA,'Use the explicit frozen 638 catalogue until this continuation is consumed')
    preview=read(preview_path);manifest=read(manifest_path);requests=list(read_lines(REQUESTS));old=list(read_lines(WHOLES))
    require(preview['status']=='FINITE_PREVIEW_PASS' and preview['manifest']['sha256']==MANIFEST_SHA and preview['wholeTemplateRequests']['sha256']==REQUESTS_SHA,'Exact accepted source-pose preview changed')
    require(len(requests)==26 and len(old)==72 and len({r['pose'] for r in requests})==26 and len({r['key'] for r in requests})==26,'Expected exactly 26 unique observed request poses and 72 old templates')
    require(not {r['pose'] for r in old}&{r['pose'] for r in requests},'An added pose is already an old template')
    require(not {r['key'] for r in old}&{r['key'] for r in requests},'An added key conflicts with the old prefix')
    require(WHOLES.read_bytes().endswith(b'\n'),'Old template bytes must already end with a JSONL newline')
    db=ro(R/'native-grid-plan/plan.sqlite');binding=meta(db,'binding');require(binding==manifest['sourceBinding'],'Assembly inputs belong to another immutable source')
    matches={row['pose']:row for row in manifest['poseMatches']};entries={row['key']:row for row in manifest['entries']};observed={row['pose']:row for row in preview['poses']}
    store=FragmentStore(R/'fragments.sqlite');fdb=store.database;fdb.execute('PRAGMA cache_size=-2048');fdb.execute('PRAGMA temp_store=FILE');fdb.execute('BEGIN')
    require(not fdb.execute("SELECT 1 FROM metadata WHERE name='nativePhysicsQuarantine'").fetchone(),'Fragment inputs are quarantined')
    fragment_before=sha(R/'fragments.sqlite');rows=[];missing=[];textures={};total=collections.Counter();total['owners']=sum(observed[r['pose']]['objectsByIntendedKind'].get('whole',0) for r in requests)
    require(total['owners']==507,'Observed finite request set no longer binds the exact 507 whole candidates')
    require(sha(BASE)==BASE_SHA,'Original geometry source JAR changed')
    with zipfile.ZipFile(BASE) as jar:
        for request in requests:
            key=request['pose'];match=matches.get(key);require(match is not None,'Whole request is absent from the exact manifest pose matches')
            entry=entries[match['assemblyKey']];require(entry['confidence']=='confirmed' and entry['preservation']['runtimePolicy']=='compiled_whole_or_local_original','A functional/v12/uncertain entry cannot become a new compiled whole')
            require(request['key']=='wo_'+key and request.get('displayName') and request['sourceMainItem']==entry['currentMainItem'],'Whole item/name/primary provenance differs from its accepted request')
            encoded=db.execute('SELECT data FROM poses WHERE key=?',(key,)).fetchone();require(encoded and hashlib.sha256(encoded[0].encode()).hexdigest()==match['poseDataSha256'],'Exact saved pose changed')
            pose=json.loads(encoded[0]);source=fdb.execute('SELECT source,proof FROM poses WHERE key=?',(key,)).fetchone();record={'key':request['key'],'pose':key,'assemblyKey':entry['key'],'registryId':pose['registryId'],'poseDataSha256':match['poseDataSha256'],'observedOwners':observed[key]['objectsByIntendedKind']['whole'],'sourceMainItem':request['sourceMainItem']}
            if not source:
                missing.append({'pose':key,'kind':'missing_actual_source_pose_export','sourcePose':pose});rows.append(record);continue
            provenance,proof=map(json.loads,source)
            if pose_identity(provenance.get('sourcePose',{}))!=pose_identity(pose):missing.append({'pose':key,'kind':'exported_source_pose_identity_mismatch'})
            if provenance.get('openable') or provenance.get('functionalDoor'):missing.append({'pose':key,'kind':'functional_source_cannot_be_flattened'})
            if proof.get('relativeError',1)>2e-5:missing.append({'pose':key,'kind':'source_clipping_area_proof_failed'})
            counts=collections.Counter();layers=collections.Counter();flags=collections.Counter();alphas=set();uv=[math.inf,math.inf,-math.inf,-math.inf];bounds=[math.inf]*3+[-math.inf]*3;bankhash=hashlib.sha256();cells=[];sprites=set()
            for x,y,z,digest,data in fdb.execute('SELECT x,y,z,digest,data FROM fragments WHERE pose=? ORDER BY x,y,z',(key,)):
                coordinate=[x,y,z];cells.append(coordinate);fragment=json.loads(data);counts['cells']+=1
                if hashed(fragment)!=digest:missing.append({'pose':key,'cell':coordinate,'kind':'stored_fragment_digest_mismatch'})
                bankhash.update((canonical([coordinate,digest])+'\n').encode())
                if any(abs(v)>127 for v in coordinate):missing.append({'pose':key,'cell':coordinate,'kind':'whole_role_outside_127_budget'})
                for field in ('collision','outline','climbing'):
                    boxes=fragment.get(field)
                    if not isinstance(boxes,list) or canonical_boxes(boxes)!=boxes:missing.append({'pose':key,'cell':coordinate,'kind':'invalid_exact_'+field});continue
                    counts[field+'Boxes']+=len(boxes)
                    if any(not all(math.isfinite(v) and -1e-6<=v<=1.000001 for v in b) for b in boxes):missing.append({'pose':key,'cell':coordinate,'kind':'physical_box_outside_cell','field':field})
                for quad in fragment['quads']:
                    counts['quads']+=1;material=quad['material'];layer=material['layer'];layers[layer]+=1;sprites.add(material['sprite']);vertices=quad['vertices']
                    flags[canonical({'shade':quad.get('shade',True),'ao':quad.get('ao',True),'emissive':quad.get('emissive',False),'tint':quad.get('tint',-1)})]+=1
                    if layer not in ('solid','cutout','cutout_mipped','translucent') or len(vertices)!=4 or area(vertices)<=0:missing.append({'pose':key,'cell':coordinate,'kind':'invalid_quad'})
                    if any(len(v)!=8 or not all(math.isfinite(n) for n in v) for v in vertices):missing.append({'pose':key,'cell':coordinate,'kind':'nonfinite_or_incomplete_vertex'});continue
                    for v in vertices:
                        alphas.add((int(v[5])>>24)&255);uv=[min(uv[0],v[3]),min(uv[1],v[4]),max(uv[2],v[3]),max(uv[3],v[4])]
                        for axis in range(3):bounds[axis]=min(bounds[axis],coordinate[axis]+v[axis]);bounds[axis+3]=max(bounds[axis+3],coordinate[axis]+v[axis])
                        if any(v[axis]<-1e-6 or v[axis]>1.000001 for axis in range(3)):missing.append({'pose':key,'cell':coordinate,'kind':'visual_outside_cell'})
                    if any(max(v[a] for v in vertices)-min(v[a] for v in vertices)<1e-8 for a in range(3)):counts['axisPlaneQuads']+=1
            if [0,0,0] not in cells:missing.append({'pose':key,'kind':'whole_base_origin_absent'})
            if not counts['quads']:missing.append({'pose':key,'kind':'empty_whole_visual'})
            if proof.get('cells')!=counts['cells']:missing.append({'pose':key,'kind':'clipping_proof_cell_count_mismatch'})
            for sprite in sprites:
                if sprite in textures:continue
                namespace,name=sprite.split(':',1);relative='assets/'+namespace+'/textures/'+name+'.png';path=RESOURCES/relative
                if not path.is_file() or relative not in jar.namelist():missing.append({'pose':key,'kind':'missing_exact_source_texture','sprite':sprite});continue
                actual_sha=sha(path);source_sha=hashlib.sha256(jar.read(relative)).hexdigest()
                if actual_sha!=source_sha:missing.append({'pose':key,'kind':'source_texture_bytes_changed','sprite':sprite})
                with path.open('rb') as stream:header=stream.read(26)
                textures[sprite]={'path':str(path),'sha256':actual_sha,'originalJarResource':relative,'originalJarResourceSha256':source_sha,'pngColorType':header[25] if header[:8]==b'\x89PNG\r\n\x1a\n' else None,'alphaEvidence':'Exact original PNG bytes and exact per-vertex ARGB/material layers; no mask conversion, hull, voxelization or alpha rewrite'}
            record.update(sourcePoseExportSha256=hashed(provenance),clippingProof=proof,fragmentBankSha256=bankhash.hexdigest(),counts=dict(counts),layers=dict(layers),flags=dict(flags),vertexAlphaValues=sorted(alphas),uvBounds=uv if counts['quads'] else None,visualBounds=bounds if counts['quads'] else None,textureSprites=sorted(sprites),hasOrigin=[0,0,0] in cells,roleBudget=127)
            rows.append(record);total.update(counts)
    require(sha(R/'fragments.sqlite')==fragment_before and sha(REQUESTS)==REQUESTS_SHA,'Frozen fragment/request inputs changed during inspection');store.close();db.close()
    report={'schemaVersion':1,'status':'READY_FOR_LATER_FULL_COMPOSE_APPEND' if not missing else 'BLOCKED_FINITE_INPUTS','manifest':bind(manifest_path),'acceptedPosePreview':bind(preview_path),'requests':bind(REQUESTS),'sourceBinding':binding,'baseWholeObjects':{**bind(WHOLES),'count':len(old)},'fragments':{'path':str(R/'fragments.sqlite'),'sha256':fragment_before},'compilerAlgorithmSha256':ALGORITHM,'currentCatalogSha256':CATALOG_SHA,'prefixSnapshot':bind(R/'catalog-prefix-638d84/manifest.json'),'counts':dict(total),'requestPoses':len(requests),'poses':rows,'textures':textures,'missing':missing,'missingCount':len(missing),'api':{'class':'CatalogCompiler.intern_whole','implicitCells':'store.pose_cells(pose); each own offset has exactly one source fragment reference','roleSalt':'{template:key,offset:offset}; independent from architecture cells','displayNameRequiredHere':True,'runtimeBehavior':'Existing finite WholeObjectTemplate/one whole item; category is not restricted to statues','sourceMainItem':'Source primary/redirect provenance; deliberately outside pinned compiler content signature'},'appendPolicy':'After promoted overrides and final FULL composition, materialize to a NEW output. Existing 72 JSONL bytes remain an exact prefix; only actually selected confirmed request poses are added. Overlap-retained whole candidates remain local. No geometry re-export or fragment merge is required when missing=[] .','databaseWrites':0,'mapWrites':0,'currentTemplateFileWrites':0,'catalogueWrites':0,'compilerRuns':0,'gameProcessesStarted':0}
    return report,requests

def materialize(a,report,requests):
    require(report['missingCount']==0,'Resolve exact finite geometry gaps before preparing whole input')
    db=ro(R/'native-grid-plan/plan.sqlite')
    try:
        require(not meta(db,'applyStarted') and not meta(db,'scope') and meta(db,'successfulPilotGate') and meta(db,'fullNativeFunctionGateRequired'),'Only after successful-pilot promotion and before FULL map apply')
        bound=meta(db,'preservedAssemblyOverrides');require(bound and bound['manifest']==report['manifest'],'Import this exact confirmed assembly override first')
        receipt=read(bound['receipt']['path']);require(sha(bound['receipt']['path'])==bound['receipt']['sha256'] and receipt['status']=='PASS' and receipt['sourceBinding']==report['sourceBinding'],'Passed source-bound assembly override receipt required')
        require(receipt['wholeTemplateRequests']==(report['requests']|{'count':26}),'Override request set differs')
        require(a.compositions and a.compositions_sha256 and sha(a.compositions)==a.compositions_sha256,'Explicit final FULL composition bytes are required')
        generation=meta(db,'compositionGeneration');require(generation and generation.get('compositionsSha256')==a.compositions_sha256,'Completed final composition generation must bind these exact raw bytes')
        require(meta(db,'compositionsSha256')==a.compositions_sha256,'Promotion clears the active raw binding: a completed fresh FULL compose is required')
        templates=meta(db,'wholeTemplates') or {};selected=[];omitted=[]
        for request in requests:
            amount=db.execute("SELECT count(*) FROM selected_objects WHERE pose=? AND kind='whole'",(request['pose'],)).fetchone()[0]
            if amount:
                require(templates.get(request['pose'])==request['key'],'Actual selected whole uses another template key')
                selected.append(request|{'observedSelectedWholeOwners':amount})
            else:omitted.append({'pose':request['pose'],'template':request['key'],'reason':'No actual selected whole instance in final composition; overlapping/local-retained candidates need no new compiled template'})
        require(not a.out.exists() and not a.receipt.exists(),'Whole continuation outputs must be fresh')
        require(a.out.resolve()!=WHOLES.resolve(),'Never overwrite the current 72-template file with this helper')
        a.out.parent.mkdir(parents=True,exist_ok=True);prefix=WHOLES.read_bytes()
        with a.out.open('wb') as stream:
            stream.write(prefix)
            for row in selected:stream.write((canonical({k:v for k,v in row.items() if k!='observedSelectedWholeOwners'})+'\n').encode())
        require(a.out.read_bytes().startswith(prefix),'Old whole-template byte prefix changed')
        write(a.receipt,{'schemaVersion':1,'status':'PREPARED_NOT_COMPILED','sourceBinding':report['sourceBinding'],'manifest':report['manifest'],'assemblyOverrides':bound,'requests':report['requests'],'readiness':report,'composition':{'path':str(a.compositions),'sha256':a.compositions_sha256},'baseWholeObjects':report['baseWholeObjects'],'extendedWholeObjects':bind(a.out),'previousCount':72,'addedPoses':len(selected),'totalCount':72+len(selected),'old72BytePrefixExact':True,'selectedConfirmedTemplates':selected,'locallyRetainedOrUnselected':omitted,'fragmentBankUnchanged':report['fragments'],'compilerAlgorithmSha256':ALGORITHM,'requiresSameCatalogStateDatabase':str(R/'catalog-state.sqlite'),'requiresStrictPrefixSnapshot':report['prefixSnapshot'],'databaseWrites':0,'mapWrites':0,'catalogueWrites':0,'compilerRuns':0})
        return {'status':'PREPARED_NOT_COMPILED','out':bind(a.out),'receipt':bind(a.receipt),'addedPoses':len(selected),'oldPrefixExact':True}
    finally:db.close()

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=('inspect','materialize'));p.add_argument('--out',type=Path,required=True);p.add_argument('--receipt',type=Path);p.add_argument('--compositions',type=Path);p.add_argument('--compositions-sha256');a=p.parse_args()
    report,requests=inspect()
    if a.mode=='inspect':
        require(not a.out.exists(),'Readiness output must be fresh');write(a.out,report);print(json.dumps({'status':report['status'],'report':bind(a.out),'missing':report['missing'],'counts':report['counts']}));require(not report['missing'],'Finite whole-template geometry inputs are incomplete')
    else:
        require(a.receipt is not None,'Materialization requires its separate append-lineage receipt');print(json.dumps(materialize(a,report,requests)))

if __name__=='__main__':main()
