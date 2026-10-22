"""Bind the explicitly authorized phase-two append to its final exact inputs."""
import hashlib,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from export_merge_physics import require,sha,read
from catalog import compiler_fingerprint
ROOT=Path(__file__).resolve().parents[4];SOURCE=ROOT/'source';R=ROOT/'research';C=R/'native-grid-plan/context-extension'
RAW='3333ec6d9f6bab318971fd5fcd182813937a3eb17bfb077dcd889b57c828d087'
FINAL='b52da3beb2e14b40777ae058e145ba5f7a379b8c4524b10af6f11823ac18b8c1'
FRAG='96b0ac0fb72481b6c59c83167e5649a494da3a6ccafedccf3907b219bc424bca'
ALGORITHM='111b89688889fa961f2ae19bc5e0f0683d2ec4c9bfa8fbcf0b36117a5e96e13f'
def main():
    final=read(C/'final-state-coverage.json');normal=read(C/'normalizer-receipt.json');rawpath=R/'native-grid-plan/compositions.jsonl';normalized=C/'compositions.normalized.jsonl'
    require(sha(C/'final-state-coverage.json')==FINAL and final['status']=='PASS','Authorized exact final coverage changed or failed')
    require(final['actualNeededStates']==248 and final['functionDecisions']==260,'Authorized exact state coverage differs')
    require(final['compositionsSha256']==sha(rawpath)==RAW,'Authorized final raw composition changed')
    require(final['fragmentBinding']['sha256']==sha(R/'fragments.sqlite')==FRAG,'Authorized exact fragment database changed')
    require(normal['status']=='PASS' and normal['rawCompositionsSha256']==RAW and normal['normalizedCompositionsSha256']==sha(normalized),'Final normalizer lineage differs')
    require(normal['normalizerSha256']==sha(Path(__file__).with_name('catalog_normalize_carriers.py')),'Authoring normalizer changed after final materialization')
    require(normal['compositionKeysPreserved'] and normal['onlyOpaqueAndSidedTransparencyOverridden'] and normal['physicalGeometryFluidLocalFunctionUnchanged'],'Normalized authoring semantics changed')
    require(normal['bridgeCompositions']==normal['normalizedPartialCarrierRows']==64,'Final actual carrier coverage is incomplete')
    require(compiler_fingerprint()==normal['compilerAlgorithmSha256']==ALGORITHM,'Pinned compiler fingerprint changed')
    bindings={}
    for key in ('nativeFunctionGate','fragmentAppendLineage'):
        proof=final[key];require(sha(proof['path'])==proof['sha256'] and read(proof['path'])['status']=='PASS','Final source proof changed: '+key);bindings[key]=proof
    for name,wanted in (('preflight-raw-final.json',RAW),('preflight-normalized.json',sha(normalized))):
        proof=read(C/name);require(proof['status']=='PASS' and proof['problemCount']==0 and proof['compositionsSha256']==wanted,'Final preflight differs: '+name)
        bindings[name]={'path':str(C/name),'sha256':sha(C/name)}
    whole=R/'whole-objects.jsonl';require(len(whole.read_text(encoding='utf-8').splitlines())==72,'Expected exact whole templates changed')
    result={'status':'PASS','mode':'EXACT_AUTHORIZED_PHASE2_APPEND_INPUT_BINDING','rawCompositionsSha256':RAW,'normalizedCompositionsSha256':sha(normalized),'finalCoverageSha256':FINAL,'fragmentsSha256':FRAG,'normalizerReceiptSha256':sha(C/'normalizer-receipt.json'),'compilerAlgorithmSha256':ALGORITHM,'wholeObjectsSha256':sha(whole),'wholeTemplates':72,'proofs':bindings,'catalogueWrites':0,'databaseWrites':0,'mapWrites':0}
    (C/'append-input-binding.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps(result))
if __name__=='__main__':main()
