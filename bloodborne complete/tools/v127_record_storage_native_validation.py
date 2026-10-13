"""Record only actual passed native cases, keeping other suite failures visible."""
import argparse,hashlib,json,re,subprocess,xml.etree.ElementTree as ET
from v127_visual_export import ROOT,OUT,write_json
def main(run,final):
    result_path=ROOT/'build/v127-validation'/run/'result.json';result=json.loads(result_path.read_text(encoding='utf-8'))
    xml=ROOT/f'build/test-results/gametest/TEST-{run}.xml';cases=ET.parse(xml).getroot().findall('.//testcase')
    selected=[r for r in cases if r.attrib['name'].startswith(('compactstatetransitionsv127gametests.','shardedledgerv127gametests.','importedfollowupv127gametests.','ownedstateentriesv127gametests.')) or r.attrib['name']=='worldeditv127gametests.numericwholeobjectdeleteundocopyandcompact']
    assert len(selected)==11 and all(r.find('failure') is None and r.find('error') is None and r.find('skipped') is None for r in selected)
    world=ROOT/f'build/gametest-{run}/world';source=world/'v127-state-storage-native-proof.json';proof=json.loads(source.read_text(encoding='utf-8'))
    assert proof['status']=='PASS_EXHAUSTIVE_NATIVE_STATE_IDENTITIES' and proof['originalCompositePrototypeStates']==6656
    metrics=proof['storageMetrics'];old=metrics['oldCartesianReferenceSlots'];new=metrics['compactPropertyReferenceSlots']
    entry_path=world/'v127-state-entries-native-proof.json';entries=json.loads(entry_path.read_text(encoding='utf-8'));assert entries['status']=='PASS_EXHAUSTIVE_NATIVE_PROPERTY_MAP_IDENTITIES' and entries['idempotentRepeat']
    log_path=result_path.parent/'gradle.log';log=log_path.read_text(encoding='utf-8',errors='replace')
    assert result.get('worldEditOptionalRuntime') is True and 'V127 WorldEdit native parser/whole-delete/undo/clipboard/compact: PASS' in log
    assert 'V127 WorldEdit shared state transitions: PASS' in log
    hist_path=result_path.parent/'startup-retained-heap-histogram.txt';hist=hist_path.read_text(encoding='utf-8');match=re.search(r'^Total\s+(\d+)\s+(\d+)\s*$',hist,re.M);assert match
    actual_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT.parent,text=True).strip()
    if final:
        assert result['status']=='PASS' and result['exitCode']==0 and result.get('failures')==0 and result.get('skipped')==0,'Final storage evidence requires an entirely passing actual native suite'
        assert result['sourceCommit']==actual_commit,'Final storage evidence must match the current frozen source checkpoint'
        assert all(r.find('failure') is None and r.find('error') is None and r.find('skipped') is None for r in cases)
    data={'status':'PASS_FINAL_OWNED_STORAGE_ALIAS_AND_ACTUAL_WORLDEDIT_SUITE' if final else 'PASS_NAMED_OWNED_STORAGE_ALIAS_AND_WORLDEDIT_NATIVE_CASES','sourceCommit':result['sourceCommit'],'nativeRun':run+' WITH actual optional WorldEdit','wholeSuiteStatus':result['status'],'finalAcceptance':final,
        'passedCases':[{'name':r.attrib['name'],'seconds':float(r.attrib['time'])} for r in selected],
        'xmlSha256':hashlib.sha256(xml.read_bytes()).hexdigest(),'stateProofSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'nativeStateProof':proof,'entriesProofSha256':hashlib.sha256(entry_path.read_bytes()).hexdigest(),'nativeEntriesProof':entries,
        'startupRetainedHeap':{'objects':int(match.group(1)),'bytes':int(match.group(2)),'histogramSha256':hashlib.sha256(hist_path.read_bytes()).hexdigest(),'scope':'Actual whole-process live histogram after explicit startup GC; timing, spawn and source revision may differ from older baselines. This is not a claim of memory attributed solely to the mod.'},
        'worldEditTransitionMetricsLine':next(line for line in log.splitlines() if 'V127 WorldEdit shared state transitions: PASS' in line),'actualWorldEditNativeOperationsPassed':True,
        'referenceSlotsReduction':old-new,'referenceSlotsPercentReduction':100*(old-new)/old,
        'limitations':'The eleven named cases cover owned/OriginalV12 table contracts and exhaustive transitions, immutable property-map interning, sharded disk/eviction/cache bounds, exact alias saved data, and actual WorldEdit table/parser/delete/undo/clipboard/compact behavior. Final acceptance additionally requires the complete suite PASS with WorldEdit present and the current same source checkpoint. Later code/resource edits invalidate final evidence until rerun. The detached Table contract copy adds one to converted-table/reference-slot metrics.'}
    write_json(OUT/'storage-native-validation.json',data);print(json.dumps({k:v for k,v in data.items() if k not in ['passedCases','nativeStateProof']}))
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--run-name',required=True);parser.add_argument('--final',action='store_true');args=parser.parse_args();main(args.run_name,args.final)
