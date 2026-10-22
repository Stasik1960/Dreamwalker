"""Intersect the existing finite native-base census with exported native assets."""
import argparse,hashlib,json,sqlite3
from pathlib import Path
from export_cells import state_key
PROJECT=Path(__file__).resolve().parents[2];ROOT=PROJECT.parent
def read(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--evidence',type=Path,default=ROOT/'research/native-functional-bases/input-evidence.json');p.add_argument('--groups',type=Path,default=ROOT/'research/native-functional-bases/native-id-groups.json');p.add_argument('--fragments',type=Path,default=ROOT/'research/fragments.sqlite');p.add_argument('--out',type=Path,default=ROOT/'research/native-grid-plan/context-extension/native-initial-export-request.json');a=p.parse_args()
    source=read(a.evidence);groups={r['registryId']:r for r in read(a.groups)};store=sqlite3.connect('file:'+a.fragments.absolute().as_posix()+'?mode=ro',uri=True)
    requested={};complete=[];excluded=[];upgrades=[];missing=[];unsupported=[]
    for occurrence in source['nativeBaseCandidates']:
        state=occurrence['state'];key=state_key(state);rid=state['registryId'];group=groups[rid];clazz=group['className'] or ''
        if group['compositeRoot'] and state['properties'].get('compact')!='true':
            excluded.append({'state':state,'cells':occurrence['cells'],'reason':'Existing required noncompact BE host; must remain outside native background export'});continue
        record=store.execute('SELECT data FROM native WHERE state=?',(key,)).fetchone();native=json.loads(record[0]) if record else None
        if native and native.get('nativeVisualComplete',True):complete.append(state);continue
        supported=(rid.startswith('minecraft:') or 'MaterialSlabs$' in clazz or 'SourceFallbackCarrierV1271' in clazz or ('ImportedCompactBlock' in clazz and state['properties'].get('compact')=='true' and int(state['properties'].get('rotation','-1')) in (0,2,4,6)))
        if not supported:unsupported.append({'state':state,'className':clazz,'reason':'Finite exporter route must be proved before launch'});continue
        requested[key]={**state,'physicsOnly':False}
        (missing if native is None else upgrades).append({'state':state,'className':clazz,'cells':occurrence['cells']})
    request={'schemaVersion':1,'poses':[],'nativeStates':[requested[k] for k in sorted(requested)],'sourceBinding':source['sourceBinding'],'nativeCensusSha256':sha(a.evidence),'source':'Exact initial actual native states at existing eligible pose/root coordinates; no hypothetical products. Final activated closure requires another coverage gate.'}
    a.out.parent.mkdir(parents=True,exist_ok=True);a.out.write_text(json.dumps(request,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    report={'status':'REQUEST_PREPARED_NOT_LAUNCHED' if not unsupported else 'UNSUPPORTED_EXACT_NATIVE_ROUTE','censusSha256':sha(a.evidence),'groupingSha256':sha(a.groups),'actualCensusStates':len(source['nativeBaseCandidates']),'alreadyComplete':len(complete),'requestedStates':len(requested),'missingPhysicsAndVisuals':missing,'upgradePhysicsOnlyToVisual':upgrades,'retainedBEHostsExcluded':excluded,'unsupportedExactStates':unsupported,'request':str(a.out),'requestSha256':sha(a.out),'worldReads':0,'exportsLaunched':0,'databaseWrites':0,'coverageScope':source['scopeLimit']}
    receipt=a.out.with_suffix('.receipt.json');receipt.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k not in ('missingPhysicsAndVisuals','upgradePhysicsOnlyToVisual','retainedBEHostsExcluded','unsupportedExactStates')}|{'missingPhysicsAndVisuals':len(missing),'upgradePhysicsOnlyToVisual':len(upgrades),'retainedBEHostsExcluded':len(excluded),'unsupportedExactStates':len(unsupported)}))
if __name__=='__main__':main()
