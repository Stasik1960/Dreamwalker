"""Record only actual passed native cases, keeping other suite failures visible."""
import hashlib,json,xml.etree.ElementTree as ET
from v127_visual_export import ROOT,OUT,write_json
def main():
    result=json.loads((ROOT/'build/v127-validation/tests7/result.json').read_text(encoding='utf-8'))
    xml=ROOT/'build/test-results/gametest/TEST-tests7.xml';cases=ET.parse(xml).getroot().findall('.//testcase')
    selected=[r for r in cases if r.attrib['name'].startswith(('compactstatetransitionsv127gametests.','shardedledgerv127gametests.','importedfollowupv127gametests.'))]
    assert len(selected)==9 and all(r.find('failure') is None and r.find('error') is None for r in selected)
    source=ROOT/'build/gametest-tests7/world/v127-state-storage-native-proof.json';proof=json.loads(source.read_text(encoding='utf-8'))
    assert proof['status']=='PASS_EXHAUSTIVE_NATIVE_STATE_IDENTITIES' and proof['originalCompositePrototypeStates']==6656
    metrics=proof['storageMetrics'];old=metrics['oldCartesianReferenceSlots'];new=metrics['compactPropertyReferenceSlots']
    data={'status':'PASS_OWNED_STORAGE_AND_ALIAS_NATIVE_CASES','sourceCommit':result['sourceCommit'],'nativeRun':'tests7 WITHOUT optional WorldEdit','wholeSuiteStatus':result['status'],
        'passedCases':[{'name':r.attrib['name'],'seconds':float(r.attrib['time'])} for r in selected],
        'xmlSha256':hashlib.sha256(xml.read_bytes()).hexdigest(),'stateProofSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'nativeStateProof':proof,
        'referenceSlotsReduction':old-new,'referenceSlotsPercentReduction':100*(old-new)/old,
        'limitations':'PASS applies only to the nine named actual native cases and source checkpoint. The suite had other failures; WorldEdit optional integration and later metadata need their own final run. The detached Table contract copy adds one to converted-table/reference-slot metrics; actual startup histogram showed951812 attached compact tables.'}
    write_json(OUT/'storage-native-validation.json',data);print(json.dumps({k:v for k,v in data.items() if k not in ['passedCases','nativeStateProof']}))
if __name__=='__main__':main()
