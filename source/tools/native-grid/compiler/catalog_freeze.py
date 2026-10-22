"""Save the completed authoring pipeline's exact frozen build handoff."""
import json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from export_merge_physics import require,sha,read
from export_cells import read_lines
ROOT=Path(__file__).resolve().parents[4];R=ROOT/'research';C=R/'native-grid-plan/context-extension';RES=ROOT/'source/src/architecture/resources/assets/bloodborne_dw/native_grid'
def main():
    prefix=read(C/'prefix-after-append.json');stats=read(C/'catalog-stats.json');bridge=read(C/'catalog-bridge-proof.json');inputs=read(C/'append-input-binding.json');mapping=R/'type-mapping.jsonl'
    require(all(doc['status']=='PASS' for doc in (prefix,stats,bridge,inputs)),'A completed pipeline proof did not pass')
    require(prefix['catalogSha256']==stats['catalogSha256']==bridge['catalogSha256']==sha(RES/'catalog.json'),'Frozen catalogue binding differs')
    require(prefix['meshesSha256']==stats['meshesSha256']==sha(RES/'meshes.bin'),'Frozen mesh binding differs')
    require(prefix['mappingSha256']==sha(mapping),'Frozen type mapping changed')
    rows={row['key']:row['typeIndex'] for row in read_lines(mapping)};current={r['key'] for r in read_lines(C/'compositions.normalized.jsonl')}
    require(current<=rows.keys(),'A final actual composition has no frozen type mapping')
    report={'status':'FROZEN_RESOURCES_READY','kind':'COMPLETED_PHASE2_CATALOG_AUTHORING_HANDOFF','catalog':{'path':str(RES/'catalog.json'),'sha256':prefix['catalogSha256']},'meshes':{'path':str(RES/'meshes.bin'),'sha256':prefix['meshesSha256']},'mapping':{'path':str(mapping),'sha256':prefix['mappingSha256'],'allKeys':len(rows),'finalActualCompositionKeys':len(current)},'inputBindings':inputs,'prefixProof':{'path':str(C/'prefix-after-append.json'),'sha256':sha(C/'prefix-after-append.json')},'resourceStatistics':stats,'bridgeProof':{'path':str(C/'catalog-bridge-proof.json'),'sha256':sha(C/'catalog-bridge-proof.json'),'wrapperTypes':bridge['uniqueBackgroundCellTypes'],'allDryLuminanceZeroObservedPredicatesMatch':True},'compilerReceiptSha256':sha(RES/'compiler-receipt.json'),'productionBuildExecutedByThisStep':False,'newJarNativeBridgeAuditStillRequired':True,'mapReads':0,'mapWrites':0,'databaseWrites':0,'resourceWrites':0}
    out=C/'frozen-catalog.json';out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps({'status':report['status'],'receipt':str(out),'sha256':sha(out),'allMappingKeys':len(rows),'finalActualCompositionKeys':len(current)}))
if __name__=='__main__':main()
