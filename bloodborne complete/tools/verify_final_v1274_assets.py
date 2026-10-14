"""Read-only proof of composed cap/flat/door scope and exact source pixels."""
from pathlib import Path
import collections,copy,hashlib,json,subprocess,zipfile
import final_v1274_surface_caps as cap
import final_v1274_flat_layers as layer

OUT=cap.ROOT/'build/gallery-followups-v1274'
def read(path):return json.loads(path.read_text(encoding='utf8'))
def signature(element,side,face):
    return cap.canonical({'elementMetadata':{k:v for k,v in element.items() if k not in ('faces',)},'side':side,'face':face})
def main():
    door_report=read(OUT/'door-90986-caps-r5/report.json')
    assert door_report['planSha256']=='bfcd14e6aac67ab35899991cd4294aa05c2854dab61ac35bc2f098d8df32f1a7'
    assert door_report['movingOriginalIndices']==[0] and door_report['originalUpperInfillElement3CompletelyFixed']
    reports=[read(OUT/'surface-caps/apply-report.json'),read(OUT/'flat-layers/apply-report.json'),door_report]
    boundary=read(OUT/'flat-boundary/apply-report.json')
    assert boundary['planSha256']=='8116ed141f5fe63741890154353b18e93101bba58efbe09c9b82de73ed07c550'
    assert boundary['previousEpsPlanSha256']==reports[1]['planSha256']
    reports.append(boundary)
    assert all(str(r['status']).startswith('APPLIED') for r in reports)
    expected={}
    for report in reports:
        for row in report['plans']:
            if report is boundary:assert expected[row['path']]==row['inputSha256']
            expected[row['path']]=row['outputSha256']
    mismatches=[]
    for name,sha in expected.items():
        actual=cap.digest((cap.REPO/name).read_bytes())
        if actual!=sha:mismatches.append({'path':name,'expectedSha256':sha,'actualSha256':actual})
    assert not mismatches,mismatches
    flat_actions={r['path']:{(a['element'],a['side']):a for a in r['actions']} for r in reports[1]['plans']}
    for row in boundary['plans']:
        for action in row['actions']:
            key=(action['element'],action['side']);old=flat_actions[row['path']][key]
            assert old['translationModelUnits']==action['previousTranslationModelUnits']
            assert action['translationModelUnits']==-action['previousTranslationModelUnits']
            assert action['onlyTinyNormalDirectionReversed'] and action['nativeBoundaryDirection']=='inward'
            flat_actions[row['path']][key]=action
    source_names=sorted(n for n in expected if '/models/remaining/source/' in n or '/models/remaining/source_uvlock/' in n)
    git=cap.GitObjects();model_proofs=[];old_faces=0;translated=0;volume_elements=0;default_uv=0
    try:
        for name in source_names:
            old=read_doc=json.loads(git.get(cap.BASELINE,name));original=cap.resolve_elements(old,cap.BASELINE,git)
            current=read(cap.REPO/name)
            assert {k:v for k,v in old.items() if k!='elements'}=={k:v for k,v in current.items() if k!='elements'},('Model metadata changed',name)
            # Face-level correspondence also covers old two-sided planes split
            # into separate offset copies. Extra repaired caps cannot substitute
            # for a source face unless full geometry/UV/metadata is identical.
            actual=collections.Counter()
            for element in current['elements']:
                for side,face in element.get('faces',{}).items():
                    single=copy.deepcopy(element);single.pop('faces',None)
                    actual[signature(single,side,face)]+=1
            face_count=0;offset_count=0
            for index,element in enumerate(original):
                actions=flat_actions.get(name,{})
                if all(element['to'][a]-element['from'][a]>cap.EPS for a in range(3)) and not any((index,s) in actions for s in element.get('faces',{})):volume_elements+=1
                for side,face in element.get('faces',{}).items():
                    wanted=copy.deepcopy(element);wanted.pop('faces',None)
                    action=actions.get((index,side))
                    if action:
                        axis,sign=cap.SIDES[side];delta=action['translationModelUnits']
                        if action.get('nativeBoundaryDirection')=='inward':
                            assert action.get('onlyTinyNormalDirectionReversed') and delta*sign<0
                            assert delta==-action['previousTranslationModelUnits']
                            assert cap.face_plane(element,side)==(-16 if sign<0 else 32)
                        else:assert delta*sign>0
                        assert abs(delta)>=1/64-cap.EPS
                        wanted['from'][axis]=cap.clean(wanted['from'][axis]+delta);wanted['to'][axis]=cap.clean(wanted['to'][axis]+delta)
                        if 'uv' not in face:
                            assert cap.default_uv(element,side)==cap.default_uv(wanted,side)
                            default_uv+=1
                        translated+=1;offset_count+=1
                    key=signature(wanted,side,face)
                    assert actual[key]>0,('Original face geometry/UV/metadata missing',name,index,side,action)
                    actual[key]-=1;old_faces+=1;face_count+=1
            model_proofs.append({'model':name,'originalFacesMatchedExactly':face_count,'onlyApprovedNormalTranslations':offset_count})
        # Every production PNG, including marker and RP sprites, is unchanged
        # since the shipped v12.7.3 revision. Original imported RP PNGs also
        # require byte equality with the immutable v15 source archive.
        png_names=subprocess.run(['git','ls-tree','-r','--name-only',cap.ADDED_REVISION,'--','bloodborne complete/src/architecture/resources/assets','bloodborne complete/src/rp/resources/assets'],cwd=cap.REPO,capture_output=True,check=True).stdout.decode().splitlines()
        png_names=[n for n in png_names if n.endswith('.png')];png_fail=[];png_count=0
        for name in png_names:
            old_bytes=git.get(cap.ADDED_REVISION,name);current_bytes=(cap.REPO/name).read_bytes()
            if old_bytes!=current_bytes:png_fail.append(name)
            png_count+=1;git.cache.pop((cap.ADDED_REVISION,name),None)
        assert not png_fail,png_fail
        archive=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\Bloodborne-Blocks\reference-inputs\bbmc-v15-v16\bbmc_v15_resource.zip')
        rp_count=0;unmapped=[];rp_fail=[]
        with zipfile.ZipFile(archive) as z:
            keys={n[n.index('assets/'):]:n for n in z.namelist() if 'assets/' in n}
            for file in sorted((cap.RES/'assets/bloodborne_dw/textures/block/remaining/source').rglob('*.png')):
                relative=file.relative_to(cap.RES/'assets/bloodborne_dw/textures/block/remaining/source').as_posix()
                ns,tail=relative.split('/',1);key='assets/'+ns+'/textures/'+tail
                if key not in keys:unmapped.append(relative);continue
                if z.read(keys[key])!=file.read_bytes():rp_fail.append(relative)
                rp_count+=1
        assert not rp_fail,rp_fail
        assert rp_count>0
        barrel='bloodborne complete/src/architecture/resources/assets/bloodborne_dw/models/remaining/source/minecraft/block/blue_orchid.json'
        assert read(cap.REPO/barrel)==json.loads(git.get(cap.BASELINE,barrel))
    finally:git.close()
    native_model_count=0
    for tree in (cap.RES/'assets',cap.ROOT/'src/rp/resources/assets'):
        for file in tree.rglob('*.json'):
            doc=read(file)
            if not isinstance(doc,dict):continue
            native_model_count+=1
            for index,element in enumerate(doc.get('elements',[])):
                assert layer.native_bounds_legal(element),('Native JSON model bounds',str(file),index,element.get('from'),element.get('to'))
    result={'status':'PASS_COMPOSED_ASSET_SCOPE_AND_EXACT_SOURCE_PIXELS','allFinalProductionModelHashesMatchAppliedPlans':True,
            'finalChangedModels':len(expected),'sourceModelsWithIndependentOldFaceCorrespondence':len(model_proofs),
            'originalFacesUvAndMetadataMatched':old_faces,'approvedTinySinglePlaneNormalTranslations':translated,
            'originalVolumeElementsUnchanged':volume_elements,'implicitUvMappingsVerified':default_uv,
            'allAssetJsonFilesCheckedAgainstNativeElementLimits':native_model_count,'nativeOutOfRangeFromToCoordinates':0,
            'boundaryCorrectionPlanSha256':boundary['planSha256'],'approvedBoundaryInwardDirections':boundary['normalDirectionsReversed'],
            'doorR5DerivativePlanSha256':door_report['planSha256'],'doorR5OnlyLowerElement0MovesAndOriginalUpper3Fixed':True,
            'allProductionPngsByteIdenticalToV1273':png_count,'importedSourcePngsByteIdenticalToOriginalV15':rp_count,
            'sourcePngsWithoutV15Entry':unmapped,'barrel90308AllTwelveInventedCapsRemovedAndModelEqualsPreV1273':True,
            'door90986RebuiltOpenModelHashesMatchReviewedDerivatives':True,'models':model_proofs,
            'scope':'Geometry/UV/flags/pixels only. Native world lighting, camera visibility and placement are separate runtime checks.'}
    (OUT/'asset-scope-final.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    (OUT/'asset-scope-final-summary.json').write_text(json.dumps({k:v for k,v in result.items() if k!='models'},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
if __name__=='__main__':main()
