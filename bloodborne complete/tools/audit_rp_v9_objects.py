"""Read-only full RP resource/duplicate audit and measured original wood-gate evidence."""
from __future__ import annotations
import argparse
import hashlib
import json
import re
from pathlib import Path
from collections import defaultdict
from zipfile import ZipFile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path('C:/Users/vakir/Downloads/Bloodborne-RP-Fixes-1.20.1-20261006/sources/bloodborne-rp-1.0.0-rp.2-full-source.zip')
USER_JAR = Path('C:/Users/vakir/Limacina/project/dw/mods/bloodborne-rp-1.0.0-rp.2-full-local.jar')
ALIASES = {'furniture_8':'furniture_1','furniture_3':'furniture_10','furniture_4':'furniture_11',
           'furniture_5':'furniture_12','furniture_6':'furniture_13','furniture_7':'furniture_14','furniture_9':'furniture_2'}

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def audit() -> dict:
    assets = ROOT / 'src/rp/resources/assets/bloodborne_rp'
    catalog = json.loads((assets/'catalog.json').read_text(encoding='utf8'))
    numbers = json.loads((ROOT/'src/architecture/resources/bloodborne_dw/debug_catalogue.json').read_text(encoding='utf8'))
    ids = {e['registryId'].split(':',1)[-1]:e['temporaryId'] for e in numbers['entries']}
    rows = []
    signatures = defaultdict(list)
    parsed_signatures = defaultdict(list)
    texture_groups = defaultdict(list)
    alias_java=ROOT/'src/rp/java/dev/dreamwalker/bloodbornerp/object/RpObjectCompatibility.java'
    alias_body=alias_java.read_text(encoding='utf8').split('Map.of(',1)[1].split(');',1)[0]
    if dict(re.findall(r'"([^"]+)"\s*,\s*"([^"]+)"',alias_body))!=ALIASES:
        raise ValueError('Audited alias map differs from actual Java runtime map')
    for name, spec in catalog.items():
        contents = {key:sha((assets/spec[key]).read_bytes()) for key in ('model','texture','animation')}
        signature = {'resources':contents, 'width':spec['width'], 'height':spec['height'],
                     'scale':spec['scale'], 'clips':spec['clips']}
        digest = sha(json.dumps(signature,sort_keys=True,separators=(',',':')).encode('utf8'))
        signatures[digest].append(name)
        # Ignore JSON formatting/key order and the unused geometry identifier only.
        # Keep every cube/bone name, authored transform, UV, clip and loop flag.
        model_json=json.loads((assets/spec['model']).read_text(encoding='utf8'))
        for geometry in model_json.get('minecraft:geometry',[]):
            geometry.get('description',{}).pop('identifier',None)
        parsed={'model':model_json,'animation':json.loads((assets/spec['animation']).read_text(encoding='utf8')),
                'textureSha256':contents['texture'],'width':spec['width'],'height':spec['height'],
                'scale':spec['scale'],'clips':spec['clips']}
        parsed_digest=sha(json.dumps(parsed,sort_keys=True,separators=(',',':')).encode('utf8'))
        parsed_signatures[parsed_digest].append(name)
        texture_groups[contents['texture']].append(name)
        rows.append({'assetId':name,'temporaryId':ids.get(name),'canonicalAssetId':ALIASES.get(name,name),
                     'signatureSha256':digest,'parsedGeometryBehaviourSha256':parsed_digest,
                     'signature':signature,'paths':{key:spec[key] for key in contents}})
    groups = [names for names in signatures.values() if len(names)>1]
    expected = {frozenset((old,new)) for old,new in ALIASES.items()}
    if {frozenset(g) for g in groups} != expected:
        raise ValueError('Exact full behaviour/resource duplicate groups differ from reviewed seven pairs')
    # A shared texture alone is explicitly insufficient to merge geometry or behaviour.
    by_name = {row['assetId']:row for row in rows}
    partial = [{'assets':names,'textureSha256':digest,
                'distinctFullSignatures':len({by_name[n]['signatureSha256'] for n in names})}
               for digest,names in texture_groups.items()
               if len(names)>1 and len({by_name[n]['signatureSha256'] for n in names})>1]
    resource_proof = []
    with ZipFile(SOURCE) as z, ZipFile(USER_JAR) as jar:
        source_index = defaultdict(list)
        for n in z.namelist():
            for prefix in ('Bloodborne-RP/private-assets/','Bloodborne-RP/src/main/resources/'):
                if n.startswith(prefix) and not n.endswith('/'):
                    key=n[len(prefix):]
                    source_index[key].append(n)
        for file in sorted(assets.rglob('*')):
            if not file.is_file():continue
            relative='assets/bloodborne_rp/'+file.relative_to(assets).as_posix()
            if relative not in source_index:raise ValueError('Resource absent from supplied source:'+relative)
            data=file.read_bytes();matches=[n for n in source_index[relative] if data==z.read(n)]
            if not matches:raise ValueError('RP art resource changed:'+relative)
            if relative not in jar.namelist() or data!=jar.read(relative):raise ValueError('Resource differs from supplied actual full JAR:'+relative)
            resource_proof.append({'path':relative,'sourceMatchingMembers':matches,'sourceCandidates':source_index[relative],
                                   'sha256':sha(data),'byteExactSuppliedJar':True,'byteExactSource':True})
    counts=json.loads((ROOT/'reports/WORLD_AUDIT.json').read_text(encoding='utf8'))['entity_counts']
    alias_rows=[{'legacyAssetId':old,'legacyTemporaryId':ids.get(old),'canonicalAssetId':new,
                 'canonicalTemporaryId':ids.get(new),'fullSignatureSha256':by_name[old]['signatureSha256'],
                 'originalWorldLegacyInstances':counts.get('bloodborne:'+old,0),
                 'compatibility':'retain old registry, UUID, CustomName, typed NBT and UUID links; canonical new placement/pick'}
                for old,new in ALIASES.items()]
    return {'schema':'dreamwalker-rp-v9-audit-v1','status':'PASS_STATIC_RESOURCE_AND_EXACT_DUPLICATE_AUDIT',
            'sourceArchive':str(SOURCE),'sourceArchiveSha256':sha(SOURCE.read_bytes()),
            'suppliedFullJar':str(USER_JAR),'suppliedFullJarSha256':sha(USER_JAR.read_bytes()),
            'assetCount':len(rows),'fullSignatureIncludes':['model bytes','texture bytes','animation bytes','width','height','scale','clips'],
            'exactDuplicateGroups':groups,'canonicalAliasMap':alias_rows,'allAssets':rows,
            'parsedGeometryBehaviourDuplicateGroups':[names for names in parsed_signatures.values() if len(names)>1],
            'additionalParsedCandidates': [names for names in parsed_signatures.values() if len(names)>1 and frozenset(names) not in expected],
            'parsedComparisonScope':'JSON key order/whitespace and unused geometry identifier ignored; bone names, every authored transform/UV, texture bytes, animation/loop flags, dimensions/scale/clips retained. Candidates never auto-merged.',
            'runtimeAliasJavaSha256':sha(alias_java.read_bytes()),
            'sharedTextureDifferentBehaviourGroups':partial,'rpResourceCount':len(resource_proof),
            'allRpArtBytesUnchanged':True,'resourceProof':resource_proof,
            'scope':'Seven exact complete signatures only. Similar textures/names do not prove duplicate art; remaining distinct signatures stay separate.',
            'manualVisualAcceptance':'PENDING_USER_REVIEW','runtimeBehaviour':'see separate native/ordinary-client evidence'}

def gate_comparison(native_xml=None, native_log=None) -> dict:
    path=ROOT/'reports/SOURCE_REFERENCE_WOODGATE_ACTUAL2_V9.json'
    physics=ROOT/'reports/SOURCE_PHYSICS_WOODGATE_V9.json'
    r=json.loads(path.read_text(encoding='utf8'));p=json.loads(physics.read_text(encoding='utf8'))
    actual=p['woodGateActualSource'];moves=actual['actualMovements']
    if r['shutdown']!='STANDARD_MINECRAFT_STOP_EXIT0' or r['exit_code']!=0 or actual['canBeCollidedWith']:
        raise ValueError('Original measurement did not complete as reviewed')
    if len(moves)!=8 or not all(m['sameDisplacementWithAndWithoutGate'] for m in moves):
        raise ValueError('Original gate/control movement mismatch')
    native = None
    if native_xml is not None:
        names = {'rpv9behaviourgametests.actualwindowandgateblockbothsideswithoutdecorativeboundingboxwall',
                 'rpv9behaviourgametests.gateoneshotrejectsheldcadenceoffhandandreloadbackground',
                 'rpv9behaviourgametests.authoredgateandladderkeyframesremainnumericexactandidleneutral'}
        cases = [c for c in ET.parse(native_xml).getroot().iter('testcase') if c.get('name') in names]
        if len(cases) != 3 or any(e.tag in ('failure','error','skipped') for c in cases for e in c):
            raise ValueError('Required three native gate physics/one-shot/authored checks have not passed')
        native = {'status':'PASS_NATIVE_LOCAL_PHYSICS_AND_ONE_SHOT',
                  'xml':str(native_xml.resolve()), 'xmlSha256':sha(native_xml.read_bytes()),
                  'rawLog':str(native_log.resolve()), 'rawLogSha256':sha(native_log.read_bytes()),
                  'passedCases':sorted(c.get('name') for c in cases),
                  'scope':'Native Fabric GameTests; ordinary remapped-JAR client and manual gameplay are separate'}
    return {'schema':'dreamwalker-source-woodgate-v9-comparison-v1',
            'status':'MEASURED_SOURCE_PASSABLE_LOCAL_FIX_NATIVE_PASS' if native else 'MEASURED_SOURCE_PASSABLE_LOCAL_FIX_IMPLEMENTED_NATIVE_PENDING',
            'nativeLocalFixEvidence':native,
            'referenceWrapper':str(path),'referenceWrapperSha256':sha(path.read_bytes()),
            'physicsReport':str(physics),'physicsReportSha256':sha(physics.read_bytes()),
            'actualOriginalGate':actual,'actualMovementCount':8,
            'gateAndNativeControlEqual':True,'fullPlaneCrossings':sum(m['crossedGatePlane'] for m in moves),
            'nativeTerrainLimitedOriginalRotatedControls':2,
            'proposedPhysicalFix':{'sourceModelPixels':{'from':[-119,16,-8],'to':[119,171,8]},
                                  'worldTransform':'source renderer yaw-90, scale, position; quarter-block strips only for diagonal yaw',
                                  'deliberateApproximation':'one closed leaf plane bridges visible center gap; static throughout idle pulse; no decorative full AABB'},
            'collisionDeferred':False,'sourceModelTexturesAnimationBytesChanged':False,
            'oneShot':'original idle1.6seconds sampled once from server-tracked32ticks; eight source bone channels; load/background idle',
            'firstNativeStartupFailurePreserved':'reports/SOURCE_REFERENCE_WOODGATE_ACTUAL_V9.json',
            'manualGameplay':'NOT_RUN','compatibilityAcceptance':'PENDING_USER_REVIEW'}

def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,default=ROOT/'reports/RP_V9_OBJECT_AUDIT.json')
    parser.add_argument('--gate-output',type=Path,default=ROOT/'reports/SOURCE_WOODGATE_V9_COMPARISON.json')
    parser.add_argument('--native-xml',type=Path)
    parser.add_argument('--native-log',type=Path)
    args=parser.parse_args()
    if bool(args.native_xml) != bool(args.native_log):
        parser.error('--native-xml and --native-log must be provided together')
    for path,report in ((args.output,audit()),(args.gate_output,gate_comparison(args.native_xml,args.native_log))):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
        print(json.dumps({'report':str(path),'status':report['status']}))
    return 0

if __name__=='__main__':raise SystemExit(main())
