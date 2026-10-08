"""Report owned first-set evidence from preserved actual coordinated test XML.

No world/resource scanning, numeric IDs or source conversion. A PASS is scoped to
the listed server cases, never inferred client/manual acceptance.
"""
from pathlib import Path
import argparse, hashlib, json, xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser();parser.add_argument('xml',type=Path);parser.add_argument('--test-only-change-pending',action='store_true');args=parser.parse_args()
    source=args.xml.resolve();document=ET.fromstring(source.read_bytes());cases=list(document.iter('testcase'))
    scopes={}
    for label,prefix,expected in [('wall','prototypewallgametests.',7),('ordinary_ladder','prototypeladdergametests.',11),('roof','prototyperoofgametests.',4),('source_ladder','sourceladdergametests.',7),
            ('source_technical_light','sourcetechnicallightgametests.originalairphysicslightnineandnativepalettepersistwithoutanitem',1),
            ('worldless_native_compatibility','sourcetechnicallightgametests.nativeworldlessqueriespreserveoriginalshapeswithoutanowner',1)]:
        selected=[case for case in cases if case.attrib['name'].startswith(prefix)]
        failures=[{'name':case.attrib['name'],'kind':failure.tag,'failure':failure.attrib.get('message',failure.text)} for case in selected for failure in list(case) if failure.tag in ('failure','error','skipped')]
        scopes[label]={'expected':expected,'observed':len(selected),'passed':len(selected)-len(failures),'failures':failures,'status':'PASS' if len(selected)==expected and not failures else 'FAIL_OR_INCOMPLETE','cases':[case.attrib['name'] for case in selected]}
    files={}
    package=ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/architecture'
    for path in sorted((package/'ladder_source').glob('*.java')):files[str(path.relative_to(ROOT))]=digest(path)
    for name in ['PrototypeLadderBlock.java','PrototypeLadderItem.java','PrototypeArchitectureClient.java','DiagonalBakedModel.java']:
        path=package/name;files[str(path.relative_to(ROOT))]=digest(path)
    path=ROOT/'src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/SourceLadderGameTests.java';files[str(path.relative_to(ROOT))]=digest(path)
    for path in [package/'compat/SourceTechnicalLight.java',ROOT/'src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/SourceTechnicalLightGameTests.java']:
        files[str(path.relative_to(ROOT))]=digest(path)
    for path in sorted((package/'wall').glob('*.java')):files[str(path.relative_to(ROOT))]=digest(path)
    selectors=json.loads((ROOT/'src/architecture/resources/assets/bloodborne_dw/blockstates/prototype_ladder.json').read_text(encoding='utf-8'))['variants']
    report={'schema':'dreamwalker-owned-first-set-server-evidence-v1','technical_scope':'Specific owned server GameTests and source geometry evidence; not full mod acceptance',
        'xml':str(source),'xml_sha256':digest(source),'coordinated_case_count':len(cases),'coordinated_failures':[c.attrib['name'] for c in cases if any(v.tag in ('failure','error','skipped') for v in c)],
        'scopes':scopes,'status':'OWNED_SERVER_CHECKS_PASS' if all(v['status']=='PASS' for v in scopes.values()) else 'FAIL_OR_INCOMPLETE',
        'ladder_schema':{'total':len(selectors),'ordinary':sum('source_clone=false' in s for s in selectors),'source_clone':sum('source_clone=true' in s for s in selectors),'same_registry_id':'bloodborne_dw:prototype_ladder','numeric_id_frozen':False},
        'source_art':{'report':'reports/LADDER_SOURCE_ANCHOR_CONTRACT.json','recognized_pairs':34,'vertices':1360,'initial_cardinal_world_vertex_equivalence':'PASS_PROVEN','clone_art_transform':'Cardinal baked model;180 degrees about0.5 then canonical[0,0,1] shift rotated by physical cardinal facing; optional global45 after both.'},
        'source_physics':{'reference':'reports/SOURCE_PHYSICS_CLIENT_ACTUAL.json','initial_climber_depth_blocks':3/16,'fixed_backing':'Original beehive full cube at recognized original cell; no new cube follows rotations','caps_consumed':0},
        'source_code_snapshot_sha256':files,'snapshot_test_only_change_pending_rerun':args.test_only_change_pending,'snapshot_note':'Current file hashes are an inventory; the XML proves only the executed server revision. Client wall model/atlas/resource results are not inferred from server tests. Pending revisions are not certified by a prior XML; final artifact freeze is managed by the parent.',
        'wall_client_selection_evidence':{'report':'reports/WALL_SHARED_MODEL_AUDIT.json','server_state_count':32768,'native_primitive_bake_settings':160,'visual_keys':4608,'actual_client':'SEPARATE_CURRENT_NATIVE_CLIENT_RESOURCE_AND_RERENDER_REPORT_REQUIRED'},
        'source_technical_light':{'source_evidence':'reports/SOURCE_LAMP_BLOCK_AUDIT.json','original_id':'bloodborne:hunter_lamp_light_source','target_id':'bloodborne_dw:source_hunter_lamp_light','light':9,'source_cells':[[228,69,-932],[229,69,-931]],'isAir':False,'item_registered':False,'catalog_numeric_id':None,'bounded_source_saved_preservation':'SEPARATE_INDEPENDENT_SOURCE_REOPEN_REPORT_REQUIRED'},
        'actual_bounded_source_installation':'SEPARATE_SOURCE_REVIEW_REPORT_REQUIRED','minecraft_client_visual':'NOT_INFERRED_FROM_SERVER_TESTS','manual_gameplay':'NOT_ACCEPTED','shaders':'SEPARATE_CLIENT_REPORT_REQUIRED',
        'historical_failures':['Attempt4 root replacement produced0drops; original callback state fix verified inAttempt5.','Attempt6 CHUNK_LOAD synchronous neighbor query deadlocked startup; supplied-chunk-only/deferred nonblocking checks require current reload test PASS.']}
    output=ROOT/'reports/OWNED_FIRST_SET_SERVER_CHECKS.json';output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':report['status'],'scopes':{k:v['passed'] for k,v in scopes.items()},'coordinated':len(cases),'coordinated_failures':len(report['coordinated_failures'])}))
if __name__=='__main__':main()
