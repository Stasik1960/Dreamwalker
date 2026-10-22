"""Small authoring-only boundary fixture; no census, world, compiler or DB writes."""
import json,sys
from pathlib import Path
from types import SimpleNamespace
ROOT=Path(__file__).resolve().parents[4];P=ROOT/'source';R=ROOT/'research'
sys.path.insert(0,str(P/'tools/native-grid/compiler'));sys.path.insert(0,str(P/'tools/native-grid'))
import catalog_prefix as prefix
import catalog_normalize_carriers as normal
from export_merge_physics import sha,read,require
from export_cells import canonical,read_lines,state_key
CAT='638d84a879a7823fd4ed866b2109fbe864cda4690383745fbc5a53bc940fa550'
MESH='bf3745f38ab455b0630682e4286ae7327e54a8ee77067da169d144d077d37447'
MAP='3ed78e5e6a6b7f1c1f010b89bed041919b447905e1da2bd6701f5c640962a90c'
def rejected(call):
    try:call()
    except (ValueError,KeyError):return True
    return False
def write(path,value):path.write_text(json.dumps(value,sort_keys=True,indent=2)+'\n',encoding='utf-8')
class PlanStub:
    def __init__(self,metadata):self.metadata=metadata
    def execute(self,sql):
        if sql.startswith('SELECT key,value'):return [(k,json.dumps(v)) for k,v in self.metadata.items()]
        require(sql.startswith('SELECT count(*) FROM objects'),'Fixture refuses any unexpected SQL')
        return SimpleNamespace(fetchone=lambda:(2,))
def main():
    out=R/'full-authoring-helper-fixture';require(not out.exists(),'Preserve the existing one-shot fixture evidence');out.mkdir()
    hashes=SimpleNamespace(baseline_catalog_sha256=None,baseline_meshes_sha256=None,baseline_mapping_sha256=None)
    require(prefix.baseline_hashes(hashes)==(prefix.CATALOG,prefix.MESHES,prefix.MAPPING),'Legacy default baseline changed')
    hashes.baseline_catalog_sha256=CAT;require(rejected(lambda:prefix.baseline_hashes(hashes)),'Partial explicit baseline accepted')
    hashes.baseline_meshes_sha256=MESH;hashes.baseline_mapping_sha256=MAP;require(prefix.baseline_hashes(hashes)==(CAT,MESH,MAP),'Exact explicit638 baseline failed')
    preserved=prefix.verify(SimpleNamespace(snapshot=R/'catalog-prefix-4306e64f',resources=P/'src/architecture/resources/assets/bloodborne_dw/native_grid',database=R/'catalog-state.sqlite',mapping=R/'type-mapping.jsonl',out=None))
    require(preserved['status']=='PASS' and preserved['priorCatalogSha256']==prefix.CATALOG and preserved['catalogSha256']==CAT,'Legacy4306 evidence is no longer valid against current638')
    air={**normal.EMPTY_OPTICS,'sidedTransparency':False};ordinary=normal.effective_optics({}, {'optics':air}, [{'optics':{'opacity':7,'luminance':0}}]);observed={'opaque':False,'sidedTransparency':False}
    row={'key':'fixture_ONLY','baseState':{'registryId':'minecraft:air','properties':{}},'optics':None,'wrapper':'NativeGridBase','fragments':[{'pose':'fixture_ref','cell':[0,0,0]}]}
    fixed=normal.materialize_observed_carrier(row,ordinary,observed)
    require(fixed['key']==row['key'] and fixed['fragments']==row['fragments'] and fixed['optics']['opacity']==7 and fixed['optics']['sidedTransparency'] is False,'FULL raw-without-partial AIR lost source optics or observed host flags')
    require({k:v for k,v in fixed['optics'].items() if k not in normal.FLAGS}=={k:v for k,v in ordinary.items() if k not in normal.FLAGS},'A non-overridden exact optical field changed')
    host=state_key({'registryId':'bloodborne_dw:composite_cell','properties':{}})
    normal.full_host_decision({host:'bridge_host_only'},host)
    require(all(rejected(lambda d=d:normal.full_host_decision(d,host)) for d in ({},{host:'retain_local'},{host:'static_base'})),'Unknown/nonbridge FULL host was admitted')
    source=read(R/'native-grid-plan/source-binding.json');evidence=out/'input-evidence.fixture.json';gate=out/'function-gate.fixture.json'
    write(evidence,{'status':'READ_ONLY_NATIVE_BASE_INPUT_EVIDENCE_NOT_APPLY_GATE','scope':'FULL_CITY','sourceBinding':source,'coverage':{'candidateObjects':2,'candidateCells':2,'candidateCellsInspected':2,'cachedChunksRequired':1,'cachedChunksInspected':1,'missingCachedChunks':[]},'nativeBaseCandidates':[{'state':row['baseState']}],'blockEntityHostCandidates':[{'state':json.loads(host)}],'fixtureOnly':True})
    document={'status':'PASS','scope':'FULL_CITY','mapWrites':0,'sourceBinding':source,'sourceJar':r'C:\Users\vakir\.codex\worktrees\3f07\DW-city-mesh-20261018\deliverables\block-fixes-2-20261020\game\dreamwalker-bb-materials.1-streaming.7-blocks.2.jar','sourceJarSha256':normal.SOURCE_JAR,'inputEvidence':{'path':str(evidence),'sha256':sha(evidence)},'states':[{'state':row['baseState'],'decision':'static_base'},{'state':json.loads(host),'decision':'bridge_host_only'}],'fixtureOnly':True};write(gate,document)
    metadata={'scope':None,'fullNativeFunctionGateRequired':True,'binding':source,'fullNativeFunctionGate':{'path':str(gate),'sha256':sha(gate),'states':2}}
    require(rejected(lambda:normal.full_function_states(None,PlanStub(metadata))),'Missing fresh FULL gate accepted')
    decisions,binding=normal.full_function_states(gate,PlanStub(metadata));require(decisions[host]=='bridge_host_only' and binding['candidateObjects']==2,'Bound FULL gate fixture failed')
    bad=dict(metadata);bad['fullNativeFunctionGate']={**metadata['fullNativeFunctionGate'],'sha256':'0'*64};require(rejected(lambda:normal.full_function_states(gate,PlanStub(bad))),'Changed FULL gate hash accepted')
    old={r['key']:r for r in read_lines(R/'native-grid-plan/context-extension/compositions.normalized.jsonl')};count=0
    for raw in read_lines(R/'native-grid-plan/compositions.jsonl'):
        if raw.get('wrapper')!='NativeGridBase':continue
        require(canonical(normal.materialize_observed_carrier(raw,old[raw['key']]['optics'],raw['carrierPredicates']))==canonical(old[raw['key']]),'Existing64 context partial carrier output changed');count+=1
    require(count==64,'Frozen context fixture set changed')
    result={'status':'PASS','kind':'AUTHORING_HELPER_BOUNDARY_FIXTURE_NOT_FULL_INPUT_GATE','checks':['legacy4306 default baseline retained','partial explicit baseline rejected','explicit638 triple selected exactly','legacy snapshot verification still PASS all945 old mappings','FULL AIR without raw partial fields materializes exact observed flags','all other source/native optics and raw key/refs retained','unknown/static/retain_local bridge hosts rejected','fresh bound FULL source input evidence required','changed FULL gate hash rejected','all64 frozen context rows byte-equivalent after materialization'],'files':{str(path):sha(path) for path in (Path(prefix.__file__),Path(normal.__file__))},'compilerAlgorithmSha256':normal.compiler_fingerprint(),'frozenCatalogSha256':CAT,'frozenMeshesSha256':MESH,'frozenMappingSha256':MAP,'fullInputsRead':False,'fixtureSourceInputsSyntheticAndMarked':True,'persistentDatabaseWrites':0,'worldFilesRead':0,'mapWrites':0,'gameProcessesStarted':0,'catalogueWrites':0}
    require(result['compilerAlgorithmSha256']==normal.ALGORITHM,'Pinned compiler changed');write(out/'result.json',result);print(json.dumps(result))
if __name__=='__main__':main()
