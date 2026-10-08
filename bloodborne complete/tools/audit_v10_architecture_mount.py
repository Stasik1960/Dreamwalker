"""Bounded V10 audit; no world reads, model rewrites or historical report edits."""
from pathlib import Path
import argparse, hashlib, json, zipfile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/architecture/resources'
def sha(data): return hashlib.sha256(data).hexdigest()
def main():
    p=argparse.ArgumentParser();p.add_argument('--baseline-jar',type=Path,default=ROOT/'build/frozen-artifacts/v9-attempt-12/dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.4.jar');p.add_argument('--native-xml',type=Path);p.add_argument('--jar',type=Path);p.add_argument('--report',type=Path,default=ROOT/'reports/V10_ARCHITECTURE_HEIGHT_AND_ART.json');a=p.parse_args()
    table=json.loads((RES/'bloodborne_dw/debug_catalogue.json').read_text(encoding='utf8'));architecture=[r for r in table['entries'] if r['kind']=='architecture']
    assert len(table['entries'])==118 and len(architecture)==18 and len({r['temporaryId'] for r in table['entries']})==118
    assert next(r for r in architecture if r['temporaryId']=='90020')['registryId']=='bloodborne_dw:prototype_glass_window_03'
    preserved=[]
    with zipfile.ZipFile(a.baseline_jar) as baseline:
        # Legacy saved states still refer to these exact authored JSON bytes.
        for name in ['bloodborne_dw/composite/prototype_thin_window.json']+[f'assets/bloodborne_dw/models/base/source/minecraft/block/hold/window_{n:02}.json' for n in range(1,4)]:
            data=(RES/name).read_bytes();assert data==baseline.read(name);preserved.append({'path':name,'sha256':sha(data)})
        textures=[]
        for path in (RES/'assets/bloodborne_dw/textures').rglob('*.png'):
            name=path.relative_to(RES).as_posix()
            if name.endswith('/builder_tool.png') or name.endswith('/composite_builder.png'):continue
            if name in baseline.namelist():
                data=path.read_bytes();assert data==baseline.read(name),name;textures.append(name)
    rows=[]
    for number in (2,3):
        path=RES/f'assets/bloodborne_dw/models/base/v10_windows/window_{number:02}.json';model=json.loads(path.read_text(encoding='utf8'));faces=model['elements'][0]['faces']
        assert set(faces)=={'north','south'} and faces['north']['uv']==[.125,10.25,5.625,15.75] and faces['south']['uv']==[5.625,10.25,.125,15.75]
        if number==3:assert model['elements'][0]['rotation']['angle']==0
        rows.append({'temporaryId':'90010' if number==2 else '90020','currentModel':path.relative_to(RES).as_posix(),'sha256':sha(path.read_bytes()),'frontRectangle':[.125,10.25,5.625,15.75],'backUsesSameRectangleWithReversedU':True,'intrinsicYawDegrees':0 if number==3 else model['elements'][0].get('rotation',{}).get('angle',0)})
    native={'status':'NOT_RUN_FOR_THIS_REPORT'}
    if a.native_xml:
        import xml.etree.ElementTree as ET
        cases=[c for c in ET.parse(a.native_xml).getroot().iter('testcase') if 'architectureverticalmountgametests.' in c.attrib.get('name','').lower()]
        native={'xml':str(a.native_xml),'methods':len(cases),'passed':sum(c.find('failure') is None and c.find('error') is None for c in cases),'failures':[{'name':c.attrib.get('name'),'message':c.find('failure').attrib.get('message')} for c in cases if c.find('failure') is not None]}
        native['status']='PASS_OWNED_NATIVE_HEIGHT_AND_CREATIVE' if len(cases)==4 and native['passed']==4 else 'PARTIAL_NATIVE_EVIDENCE_WITH_FAILURES'
    report={'schema':'v10-bounded-architecture-height-art-v1','staticStatus':'PASS_APPEND_ONLY_TYPES_LEGACY_ART_UV_AND_TEXTURE_BYTE_AUDIT','baselineJar':str(a.baseline_jar),'reservedTemporaryRows':118,'canonicalArchitectureTypes':18,'types':[{'temporaryId':r['temporaryId'],'registryId':r['registryId']} for r in architecture],'legacyExactFiles':preserved,'unchangedExistingArchitecturePngCount':len(textures),'glazing':rows,'heightContract':{'rootUuidAndProvenance':'FIXED','offsetField':'VerticalOffset: DOUBLE in owner payload','steps':'GUI caller:1/16,1/8 default,1/4,1; API exact double','range':'actual dimension build height and whole-object physical/selection bounds','foreignBlocksAndTypedNbt':'PRESERVED; height-specific intersection grant exists only during transaction','livingEntitiesRightsAndLoadedCells':'CHECKED','stackedNativeWall':'Moving detaches from old grid seam: RAW authored 1.5-high physical shape; junction dedup only offset0','sourceLadder':'Original fixed owned role/provenance remains; its collider/outline translate; no old-height physics/climb ghost','newPickedItem':'art type/profile only, noUUID/links/height/source-payload privilege','geometryCache':'perdescriptor bounded4096, no height blockstate crossproduct'},'native':native,'actualClient':'PENDING_CURRENT_V10_RUNTIME_REPORT','manualVisualAcceptance':'PENDING_USER_REVIEW','fullCityCatalog':'NOT_READY'}
    if a.jar:
        with zipfile.ZipFile(a.jar) as jar:
            meta=json.loads(jar.read('fabric.mod.json'));assert meta['id']=='bloodborne_dw' and meta['version']=='0.1.0-prototype.5'
            for row in rows:assert jar.read(row['currentModel'])==(RES/row['currentModel']).read_bytes()
            assert jar.read('bloodborne_dw/debug_catalogue.json')==(RES/'bloodborne_dw/debug_catalogue.json').read_bytes()
        report['currentProduction']={'path':str(a.jar),'sha256':sha(a.jar.read_bytes()),'bytes':a.jar.stat().st_size,'version':meta['version']}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print(report['staticStatus']);print(native['status'])
if __name__=='__main__':main()
