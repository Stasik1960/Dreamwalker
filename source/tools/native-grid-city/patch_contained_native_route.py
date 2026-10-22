"""One journalled authoring-only native visual route correction.

Requires an actual complete/contained native model and the exact frozen row.
Never reads/writes a map or changes a placement coordinate, owner, or shape.
"""
import argparse,copy,hashlib,importlib.util,json,os,shutil
from pathlib import Path

def patch(a,m):
    plan=m.checked(a.plan);proof_path=m.checked(a.proof);proof=m.read(proof_path);db=m.connect(plan)
    directory=m.checked(plan/'context-extension'/'route-patches'/'contained-native');directory.mkdir(parents=True,exist_ok=True)
    intent_path=directory/'intent.json';completion=directory/'completion.json';raw=plan/'compositions.jsonl'
    try:
        m.require(m.meta(db,'contextExtension') and not m.meta(db,'applyStarted'),'Only the prepared unapplied context authoring stage may patch a finite route')
        proof_binding={'path':str(proof_path),'sha256':m.sha(proof_path)}
        if completion.exists():
            done=m.read(completion);m.require(done['proof']==proof_binding and done['rawAfterSha256']==m.sha(raw) and m.meta(db,'compositionGeneration')==done['newGeneration'],'Completed finite route patch changed');return done
        m.require(not m.meta(db,'catalog'),'Patch the exact finite visual route before catalogue import')
        if intent_path.exists():
            intent=m.read(intent_path);m.require(intent['proof']==proof_binding,'Interrupted route patch belongs to a different actual export proof')
        else:
            m.require(proof.get('status')=='PASS' and proof.get('nativeVisualComplete') is True and proof.get('fromMode')=='substrateFragments' and proof.get('toMode')=='nativePlusSubstrateFragments','Only the approved complete contained native-plus-foreign route is supported')
            key=proof['compositionKey'];oldrow=db.execute('SELECT data,type_index FROM compositions WHERE key=?',(key,)).fetchone();m.require(oldrow and oldrow[1] is None,'Exact uncompiled source composition required');olddata=json.loads(oldrow[0])
            generation=m.meta(db,'compositionGeneration');m.require(generation and generation['compositionsSha256']==proof['inputCompositionsSha256']==m.sha(raw),'Freeze the exact completed raw generation before route correction')
            m.require(olddata.get('baseState')==proof['baseState'] and olddata.get('baseVisualMode')==proof['fromMode'] and olddata.get('substrateFragments',[])==proof['retainedForeignRefs'],'Finite proof does not describe this exact native base and foreign refs')
            m.require(not olddata.get('substrateNativeFragments') and not olddata.get('wrapper'),'This finite correction cannot change a wide native route or existing functional bridge')
            fragments=m.meta(db,'poseFragments');m.require(fragments and m.sha(fragments['path'])==fragments['sha256']==proof['fragmentsSha256'],'Actual native export proof must bind the imported exact fragment snapshot')
            import sqlite3
            exported=sqlite3.connect('file:'+Path(fragments['path']).as_posix()+'?mode=ro',uri=True)
            try:
                row=exported.execute('SELECT data FROM native WHERE state=?',(m.canonical(proof['baseState']),)).fetchone();m.require(row and hashlib.sha256(row[0].encode()).hexdigest()==proof['nativeRowSha256'],'Exact queried native model row differs from the finite proof');native=json.loads(row[0])
                m.require(native.get('nativeVisualComplete') is True and native.get('quads'),'A real complete nonempty native model is required')
                vertices=[v for q in native['quads'] for v in q['vertices']];bounds=[min(v[i] for v in vertices) for i in range(3)]+[max(v[i] for v in vertices) for i in range(3)]
                m.require(bounds==proof['nativeBounds'] and all(-1e-6<=v[i]<=1.000001 for v in vertices for i in range(3)),'Native model is not proven contained; footprint closure requires regular composition')
                m.require(not exported.execute('SELECT 1 FROM native_fragments WHERE state=? AND (x!=0 OR y!=0 OR z!=0) LIMIT 1',(m.canonical(proof['baseState']),)).fetchone(),'A wide native model cannot use this contained-only patch')
            finally:exported.close()
            function=m.meta(db,'contextNativeFunctionDecisions',);m.require(function.get(m.canonical(proof['baseState']),{}).get('decision')=='static_base','Actual native class/state must have its finite passive function proof')
            affected=db.execute('SELECT dim,x,y,z,source_state FROM placements WHERE composition=? ORDER BY dim,x,y,z',(key,)).fetchall();m.require(affected,'Finite native correction has no placements')
            for dim,x,y,z,source in affected:
                m.require(json.loads(source)==proof['baseState'],'Finite route correction meets a different actual source state')
                m.require(not db.execute('SELECT 1 FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',(dim,x,y,z)).fetchone(),'Finite native correction cannot alter a functional bridge')
            newdata=copy.deepcopy(olddata);newdata['baseVisualMode']=proof['toMode'];newkey='cell_'+m.digest(newdata)
            existing=db.execute('SELECT data FROM compositions WHERE key=?',(newkey,)).fetchone();m.require(existing is None,'This single finite native route patch cannot merge a preexisting corrected composition')
            before=directory/'raw.before.jsonl';after=directory/'raw.after.jsonl'
            if before.exists():m.require(m.sha(before)==m.sha(raw),'Immutable pre-patch raw archive changed')
            else:
                with raw.open('rb') as inp,before.open('wb') as out:shutil.copyfileobj(inp,out);out.flush();os.fsync(out.fileno())
            rows=[(k,json.loads(data)) for k,data in db.execute('SELECT key,data FROM compositions') if k!=key and k!=newkey];rows.append((newkey,newdata));rows.sort(key=lambda row:row[0])
            with after.open('w',encoding='utf-8',newline='\n') as out:
                for k,data in rows:out.write(m.canonical({'key':k,**data})+'\n')
                out.flush();os.fsync(out.fileno())
            receipt={'schemaVersion':1,'status':'PREPARED_ATOMIC_AUTHORING_PATCH','proof':proof_binding,'rawBeforeSha256':m.sha(before),'rawAfterSha256':m.sha(after),'oldKey':key,'newKey':newkey,'fromMode':proof['fromMode'],'toMode':proof['toMode'],'affectedPlacements':len(affected),'placementCoordinatesAndSourceStatesSha256':m.digest(affected),'foreignRefsUnchanged':True,'sourceFragmentsUnchanged':True,'physicalBaseStateUnchanged':True,'catalogPrefixUnchanged':True,'sourceBinding':m.meta(db,'binding'),'fragmentBinding':fragments,'historicalGeneration':generation,'mapWrites':0}
            receipt_path=directory/'patch-receipt.json'
            if receipt_path.exists():m.require(m.read(receipt_path)==receipt,'Immutable prepared route patch receipt changed')
            else:m.atomic_json(receipt_path,receipt)
            newgen=copy.deepcopy(generation);newgen['compositionsSha256']=receipt['rawAfterSha256'];newgen['containedNativeRoutePatch']={'path':str(receipt_path),'sha256':m.sha(receipt_path)}
            intent={'schemaVersion':1,'proof':proof_binding,'oldKey':key,'newKey':newkey,'oldData':olddata,'newData':newdata,'oldGeneration':generation,'newGeneration':newgen,'before':str(before),'beforeSha256':m.sha(before),'after':str(after),'afterSha256':m.sha(after),'affectedPlacements':len(affected),'placementCoordinatesAndSourceStatesSha256':m.digest(affected),'receipt':newgen['containedNativeRoutePatch']};m.atomic_json(intent_path,intent)
        m.require(m.sha(intent['before'])==intent['beforeSha256'] and m.sha(intent['after'])==intent['afterSha256'] and m.sha(intent['receipt']['path'])==intent['receipt']['sha256'],'Finite route patch immutable journal changed')
        m.require(m.sha(raw) in (intent['beforeSha256'],intent['afterSha256']) and m.meta(db,'compositionGeneration') in (intent['oldGeneration'],intent['newGeneration']),'Interrupted native route patch has unrelated raw/generation bytes')
        current=db.execute('SELECT dim,x,y,z,source_state FROM placements WHERE composition IN (?,?) ORDER BY dim,x,y,z',(intent['oldKey'],intent['newKey'])).fetchall()
        m.require(len(current)==intent['affectedPlacements'] and m.digest(current)==intent['placementCoordinatesAndSourceStatesSha256'],'Finite native route patch placements changed')
        db.execute('BEGIN IMMEDIATE')
        prior=db.execute('SELECT data FROM compositions WHERE key=?',(intent['oldKey'],)).fetchone();m.require(prior is None or json.loads(prior[0])==intent['oldData'],'Pre-patch native route row changed')
        db.execute('INSERT OR IGNORE INTO compositions VALUES(?,?,NULL)',(intent['newKey'],m.canonical(intent['newData'])));newrow=db.execute('SELECT data FROM compositions WHERE key=?',(intent['newKey'],)).fetchone();m.require(json.loads(newrow[0])==intent['newData'],'Post-patch native route row changed')
        db.execute('UPDATE placements SET composition=? WHERE composition=?',(intent['newKey'],intent['oldKey']));db.execute('DELETE FROM compositions WHERE key=?',(intent['oldKey'],))
        m.meta(db,'compositionsSha256',intent['afterSha256']);m.meta(db,'compositionGeneration',intent['newGeneration'])
        if m.sha(raw)!=intent['afterSha256']:
            temporary=raw.with_name(raw.name+'.contained-route-writing')
            with Path(intent['after']).open('rb') as inp,temporary.open('wb') as out:shutil.copyfileobj(inp,out);out.flush();os.fsync(out.fileno())
            os.replace(temporary,raw)
        db.commit()
        done={'schemaVersion':1,'status':'PASS','proof':proof_binding,'rawBeforeSha256':intent['beforeSha256'],'rawAfterSha256':intent['afterSha256'],'oldKey':intent['oldKey'],'newKey':intent['newKey'],'affectedPlacements':intent['affectedPlacements'],'newGeneration':intent['newGeneration'],'mapWrites':0};m.atomic_json(completion,done);m.progress(plan,db,'CONTAINED_NATIVE_VISUAL_ROUTE_PATCHED',receipt=str(completion));return done
    finally:db.close()

if __name__=='__main__':
    spec=importlib.util.spec_from_file_location('native_route_migration',Path(__file__).with_name('migrate.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--plan',type=Path,default=m.DEFAULT_PLAN);parser.add_argument('--proof',type=Path,required=True);args=parser.parse_args();m.control_check();patch(args,m)
