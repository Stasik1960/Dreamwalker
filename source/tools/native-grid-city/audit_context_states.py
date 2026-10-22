"""Finite post-compose input gate. Read-only SQLite and saved authoring files.

Never discovers/reads a world, writes the plan, or launches an exporter/game.
The output enumerates exact missing decisions/exports for root, without any
registry wildcard fallback or assumption that initial census covers activation.
"""
import argparse,collections,hashlib,importlib.util,json,sqlite3,time,traceback
from pathlib import Path
root=Path(__file__).resolve().parents[3]
spec=importlib.util.spec_from_file_location('context_final_migration',root/'source/tools/native-grid-city/migrate.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--plan',type=Path,default=root/'research/native-grid-plan');parser.add_argument('--append-lineage',type=Path);args=parser.parse_args()
plan=m.checked(args.plan);out=plan/'context-extension/final-state-coverage.json'

def main():
    global out
    db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix()+'?mode=ro',uri=True)
    try:
        db.execute('BEGIN');generation=m.meta(db,'compositionGeneration');raw=plan/'compositions.jsonl'
        m.require(generation and m.sha(raw)==generation['compositionsSha256'],'Await a completed immutable composition generation before this read-only gate')
        full=not m.meta(db,'scope') and bool(m.meta(db,'fullNativeFunctionGateRequired'))
        if full:out=plan/'full-native-input-coverage.json'
        gate=m.meta(db,'fullNativeFunctionGate' if full else 'contextNativeFunctionGate');m.require(gate and m.sha(gate['path'])==gate['sha256'],'Finite actual native-function decisions are missing/changed')
        document=m.read(gate['path']);m.require(document['status']=='PASS' and document.get('mapWrites',0)==0,'Native function decision evidence did not pass')
        source_binding=m.meta(db,'binding');m.require(document.get('sourceBinding')==source_binding and m.sha(source_binding['copyManifest'])==source_binding['copyManifestSha256'],'Finite native function proof belongs to another immutable source binding')
        m.require(m.sha(document['sourceJar'])==document['sourceJarSha256'],'Exact baseline JAR of the finite function proof changed')
        provenance={}
        for key in ('baseGate','functionalPreflight','sourceCapabilities','priorReport'):
            value=document.get(key)
            if value:
                m.require(m.sha(value['path'])==value['sha256'],'Finite function proof reference changed: '+key);provenance[key]=value
        decisions={m.canonical(row['state']):row for row in document['states']}
        m.require(len(decisions)==len(document['states'])==gate['states'],'Ambiguous duplicate or incomplete exact function states')
        needed_path=plan/'native-states-needed.json';needed=m.read(needed_path)['nativeStates'];needed_states={m.canonical({'registryId':v['registryId'],'properties':v.get('properties',{})}):{'registryId':v['registryId'],'properties':v.get('properties',{})} for v in needed}
        fragment_binding=m.meta(db,'poseFragments');m.require(fragment_binding and m.sha(fragment_binding['path'])==fragment_binding['sha256'],'The imported exact fragment database changed after composition')
        lineage=None
        if generation['sourceFragments']['sha256']!=fragment_binding['sha256']:
            m.require(args.append_lineage,'An explicit strict fragment append lineage receipt is required after composition')
            append=m.read(args.append_lineage)
            m.require(append.get('status')=='PASS' and append.get('beforeFragmentsSha256')==generation['sourceFragments']['sha256'] and append.get('afterFragmentsSha256')==fragment_binding['sha256'],'Fragment append lineage does not bind historical/current exact inputs')
            m.require(all(append.get(k) is True for k in ('posesUnchanged','fragmentsUnchanged','existingNativeRowsUnchanged')),'Fragment append changed a prior SourcePose/fragment/native row')
            lineage={'path':str(m.checked(args.append_lineage)),'sha256':m.sha(args.append_lineage)}
        fluid_evidence=[]
        for filename in ('NativeGridExportClient.java','NativeGridPhysicsExport.java'):
            path=root/'source/src/nativegridexport/java/dev/dreamwalker/bloodbornedw/composite'/filename;text=path.read_text(encoding='utf-8')
            m.require('JSON=new Gson();' in text and '.serializeNulls(' not in text and 'JsonNull.INSTANCE' in text and 'fluid' in text,'Known Gson EMPTY fluid omission contract changed')
            fluid_evidence.append({'path':str(path),'sha256':m.sha(path)})
        fragments=sqlite3.connect('file:'+Path(fragment_binding['path']).as_posix()+'?mode=ro',uri=True)
        try:
            missing_function=[];nonpassive=[];missing_export=[];invalid_physics=[];physics_only=[];wide=[];row_hash=hashlib.sha256()
            for key,state in sorted(needed_states.items()):
                row=decisions.get(key)
                if row is None:missing_function.append(state)
                elif row['decision']!='static_base':nonpassive.append({'state':state,'decision':row['decision'],'reason':row.get('reason')})
                saved=fragments.execute('SELECT data FROM native WHERE state=?',(key,)).fetchone()
                if saved is None:missing_export.append(state);continue
                data=json.loads(saved[0])
                row_hash.update((key+'\t'+saved[0]+'\n').encode())
                fields=('collision','outline','culling','climbing','optics','quads')
                if any(field not in data for field in fields) or any(not isinstance(data.get(field),list) for field in ('collision','outline','culling','climbing','quads')):invalid_physics.append({'state':state,'reason':'missing_explicit_exact_native_physical_fields'})
                # Both exact Java exporters add JsonNull for EMPTY fluid. Their
                # default Gson omits null object members. Missing fluid is thus
                # the producer's EMPTY encoding, while missing shapes is not.
                fluid=data.get('fluid')
                if fluid is not None and (not isinstance(fluid,dict) or set(fluid)!=set(('id','level','falling')) or fluid['id'] not in ('minecraft:water','minecraft:lava') or type(fluid['level'])is not int or not 0<=fluid['level']<=8 or type(fluid['falling'])is not bool):invalid_physics.append({'state':state,'reason':'invalid_exact_native_fluid'})
                if not data.get('nativeVisualComplete',True):physics_only.append(state)
                cells=fragments.execute('SELECT count(*) FROM native_fragments WHERE state=?',(key,)).fetchone()[0]
                if cells:wide.append({'state':state,'clippedVisualCells':cells})
            pose_gaps=[]
            for key, in db.execute('SELECT DISTINCT o.pose FROM activated_native a JOIN objects o ON o.dim=a.dim AND o.uuid=a.uuid'):
                if not db.execute('SELECT 1 FROM pose_cells WHERE pose=?',(key,)).fetchone():pose_gaps.append(key)
            report={'schemaVersion':1,'status':'PASS' if not(missing_function or nonpassive or missing_export or invalid_physics or pose_gaps) else 'BLOCKED_FINITE_INPUTS','kind':'ACTUAL_POST_ACTIVATION_NATIVE_INPUT_GATE','scope':'FULL_CITY' if full else 'CONTEXT_EXTENSION','updatedUtc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'checkerSha256':m.sha(__file__),'sourceBinding':source_binding,'baselineJar':{'path':document['sourceJar'],'sha256':document['sourceJarSha256']},'functionProvenance':provenance,'compositionGeneration':generation,'compositionsSha256':generation['compositionsSha256'],'nativeStatesNeededSha256':m.sha(needed_path),'nativeFunctionGate':gate,'fragmentBinding':fragment_binding,'fragmentAppendLineage':lineage,'fluidEmptyEncoding':{'contract':'Gson omits JsonNull object members; only the fluid field may be absent to encode proved EMPTY','sourceEvidence':fluid_evidence},'exactPhysicalNativeRowsSha256':row_hash.hexdigest(),'actualNeededStates':len(needed_states),'functionDecisions':len(decisions),'missingFunctionDecisions':missing_function,'nonPassiveBaseStates':nonpassive,'missingNativeExports':missing_export,'invalidPhysicalNativeRows':invalid_physics,'physicsOnlyStatesRequiringExplicitSubstrateVisualRoute':physics_only,'wideNativeStates':wide,'missingActivatedSourcePoseFootprints':pose_gaps,'requiredWholeObjectTemplates':db.execute('SELECT count(DISTINCT template) FROM whole_cells').fetchone()[0],'counts':{t:db.execute('SELECT count(*) FROM '+t).fetchone()[0] for t in ('selected_objects','placements','bridge_cells','activated_native','native_visual_roots')},'worldFilesRead':0,'persistentDatabaseWrites':0,'mapWrites':0,'noDefaultsForMissingStates':True}
        finally:fragments.close()
    finally:db.close()
    m.atomic_json(out,report)

try:main()
except BaseException:
    m.atomic_json(out,{'status':'ERROR','error':traceback.format_exc(),'persistentDatabaseWrites':0,'mapWrites':0});raise
