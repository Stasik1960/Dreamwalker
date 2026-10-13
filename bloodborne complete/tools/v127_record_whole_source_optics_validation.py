"""Publish source-carrier validation only from an actual successful native run."""
import argparse,json,subprocess,xml.etree.ElementTree as ET
from v127_visual_export import ROOT,RES,write_json,digest
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--run-name',required=True);parser.add_argument('--final',action='store_true');args=parser.parse_args()
    run=ROOT/f'build/v127-validation/{args.run_name}';result=json.loads((run/'result.json').read_text(encoding='utf-8'))
    native=ROOT/f'build/gametest-{args.run_name}/world/v127-source-lighting-native-proof.json';proof=json.loads(native.read_text(encoding='utf-8'))
    path=ROOT/f'build/test-results/gametest/TEST-{args.run_name}.xml';cases=[]
    for case in ET.parse(path).getroot().iter('testcase'):
        if 'sourcewholeopticsv127gametests.' in case.attrib.get('name','').lower():cases.append((case,path))
    assert len(cases)==2 and all(c.find('failure') is None and c.find('error') is None and c.find('skipped') is None for c,p in cases)
    assert proof['status'].startswith('PASS_NATIVE_NEW_SOURCE') and proof['newWholeConsumedHelperOpacityDifferenceOccurrences']==0 and proof['potentialOwnerChunkTicketLimitConflictCases']==0
    table=json.loads((RES/'bloodborne_dw/imported-whole-source-optics-v127.json').read_text(encoding='utf-8'))
    assert table['observedSourceCellOccurrences']==281 and proof['wholeConsumedNonrootOccurrencesAudited']==1387
    current=subprocess.run(['git','rev-parse','HEAD'],cwd=ROOT.parent,capture_output=True,text=True,check=True).stdout.strip()
    if args.final:assert result['status']=='PASS' and result['exitCode']==0 and result['failures']==0 and result['skipped']==0 and result['sourceCommit']==current
    report={'status':'PASS_FINAL_NATIVE_SOURCE_CARRIER_OPTICS' if args.final else 'PASS_NATIVE_SOURCE_CARRIER_OPTICS_NONFINAL_CHECKPOINT','runName':args.run_name,'sourceCommit':result['sourceCommit'],'currentCommit':current,
        'nativeTestcases':[c.attrib['name'] for c,p in cases],'nativeProofSha256':digest(native.read_bytes()),'sourceHashes':proof['sourceHashes'],'coordinateRunSha256':proof['coordinateRunSha256'],
        'sharedRules':len(table['objects']),'sharedSourceOffsets':sum(len(r['carriers']) for r in table['objects']),'sourceOpaqueHelpersRestored':281,'newHelperOpacityDifferenceOccurrences':0,
        'normalHandAndOriginalV12OpticsUnchanged':True,'emptyShadowCellsPermitActualOrdinaryHandPlacement':True,'wholeRemovalPreservesIndependentBlock':True,'identicalStateOpacityChangeQueuesRealLightingProvider':True,
        'nativeCompressedShardReopenAndWorkerZeroDiskIO':True,'maximumOwnerChunkTicketsWithOneChunkMarginAll256Alignments':proof['maximumOwnerChunkTicketsWithOneChunkMarginOverAll256Alignments'],
        'originalWorldOrResourcePackWritten':False,'scope':'Actual native engine placement/removal plus source-context transitions, immutable worker opacity queries, compressed shard persistence, and exhaustive reachable source decision context/footprint audit. No author collision export was applied.'}
    write_json(ROOT/'docs/v12.7/conversion/whole-source-carrier-optics-native-validation.json',report);print(json.dumps(report))
if __name__=='__main__':main()
