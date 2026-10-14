"""Small read-only native model probe list from existing dictionary/count audit."""
import json,sqlite3,zipfile
from pathlib import Path
from prepare_v1271_city_compatibility import ROOT,OUT,read,write,token
from scan_original_map_models import branches_for,normalized_model

def main():
    con=sqlite3.connect(f'file:{ROOT}/build/v1271-city-preparation/source-plan.sqlite?mode=ro',uri=True)
    decisions=[(i,json.loads(raw)) for i,raw in con.execute('SELECT id,data FROM decisions')];con.close()
    counts=read(ROOT/'docs/map-pack-absence/model-usage.json')['state_counts']
    source=zipfile.ZipFile(Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\Bloodborne-Blocks\reference-inputs\bbmc-v15-v16\bbmc_v15_resource.zip'))
    vanilla=zipfile.ZipFile(Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
    probes=[]
    for i,d in decisions:
        if d.get('reason')!='SOURCE_BLOCKSTATE_NO_APPLICABLE_BRANCH':continue
        state=d['sourceState'];carrier,_,tail=state.partition('['); count=counts.get(state,0)
        if not count:continue
        namespace,path=carrier.split(':');file=f'assets/{namespace}/blockstates/{path}.json'
        highest=json.loads(source.read(file));properties=dict(piece.split('=',1) for piece in tail.rstrip(']').split(',') if piece)
        previous=json.loads(vanilla.read(file));apps=[]
        if 'multipart' in highest:expected='SOURCE_MULTIPART_NO_MATCH_EMPTY_MODEL'
        else:
            expected='VANILLA_BLOCKSTATE_BRANCH_RETAINED_WITH_V15_MODEL_AND_TEXTURE_OVERRIDES'
            for branch,group in branches_for(previous,properties):
                apps.extend({**entry[0],'model':normalized_model(entry[0]['model'])} for entry in group)
        probes.append({'sourceState':state,'decisionId':i,'count':count,'modelCandidatesExpected':apps,'expectedNativeMergeSemantics':expected,'actualNativeBakedModel':'PENDING'})
    assert sum(row['count'] for row in probes)==11318
    write(ROOT/'build/final-city-fixes/source-fallback-probes.json',probes)
    write(OUT/'source-fallback-probe-plan.json',{'status':'READONLY_NATIVE_FALLBACK_PROBE_LIST','sourceStates':len(probes),'sourceCells':11318,'rows':probes,'nativeVisualFallbackConfirmed':False,'wholeMapRescanned':False})
    print(token({'states':len(probes),'cells':11318,'multipartEmptyCandidates':sum(p['expectedNativeMergeSemantics'].startswith('SOURCE_MULTIPART') for p in probes)}))

if __name__=='__main__':main()
