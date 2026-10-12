"""Bind complete native comparison frames, pixel review, normal save and exact JAR."""
from pathlib import Path
import argparse,hashlib,json,math,re,shutil
ROOT=Path(__file__).resolve().parents[1];DOC=ROOT/'docs/v12.4-dedup'
def read(p):return json.loads(p.read_text(encoding='utf8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    p=argparse.ArgumentParser();p.add_argument('--profile',type=Path,required=True);p.add_argument('--review',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    profile=read(a.profile);run=Path(profile['run_directory']).resolve();assert run.is_relative_to(ROOT/'build')
    output=run/'v124-client-comparison-output.json';capture=read(output);review=read(a.review)
    digest=sha(Path(profile['artifact']));assert digest==profile['artifact_sha256']==capture['productionJarSha256']==review['productionJarSha256']
    assert capture['status']=='CAPTURE_COMPLETE' and capture['captureComplete'] is True
    assert capture['capturedFrames']==capture['pages']==len(capture['frames'])==math.ceil(capture['pairCount']/12)
    assert review['status']=='PASS' and review['reviewedFrames']==len(capture['frames'])
    pairs=[];images=[];verified=[]
    for frame in capture['frames']:
        image=run/'screenshots'/frame['fileName'];assert image.read_bytes()[:8]==b'\x89PNG\r\n\x1a\n'
        assert frame['sha256']==sha(image) and frame['bytes']==image.stat().st_size
        assert frame['width']>=640 and frame['height']>=360
        assert all(row['nativeModelsPresent'] and row['sameCentreScaleView'] for row in frame['pairs'])
        pairs.extend(frame['pairs']);verified.append(image)
        images.append({'fileName':image.name,'sha256':sha(image),'bytes':image.stat().st_size})
    assert len(pairs)==capture['pairCount']
    base=read(DOC/'baseline-content.json');expected={(row['id'],i) for row in base['descriptors'].values() for i in range(len(row['variants']))}
    actual={(row['oldRegistryId'],row['oldVariant']) for row in pairs if not row.get('kind') or row.get('kind')=='SOURCE_CATALOGUE_REVIEW'}
    assert expected==actual and len(actual)==1802
    manifest=read(DOC/'type-state-mapping.json');mapped=[row for row in pairs if not row.get('kind')]
    assert len(mapped)==len(manifest['mappings'])
    assert {(row['oldRegistryId'],row['oldVariant']) for row in mapped}=={(row['oldRegistryId'],row['oldVariant']) for row in manifest['mappings']}
    functional=[row for row in pairs if row.get('kind')=='FUNCTIONAL_NEW_STATE'];assert len(functional)==200
    assert {(row['mainId'],row['variant'],row.get('properties',{}).get('trim','false')) for row in functional}=={(row['mainId'],row['variant'],row.get('properties',{}).get('trim','false')) for row in read(DOC/'additional-functional-stair-review-fixtures.json')['fixtures']}
    alternate=[row for row in pairs if row.get('kind')=='MIGRATION_ALT_VIEW']
    expected_views=read(DOC/'additional-migration-review-fixtures.json')['fixtures']
    expected_alternate=[row for row in expected_views if row['kind']=='MIGRATION_ALT_VIEW']
    assert len(alternate)==len(expected_alternate) and expected_alternate
    def view_key(row):return (row['oldRegistryId'],row['oldVariant'],row.get('viewYawDegrees',35),row.get('viewPitchDegrees',25))
    assert {view_key(row) for row in alternate}=={view_key(row) for row in expected_alternate}
    assert review.get('alternateOneSidedModelsVisible') is True
    source_alpha=read(DOC/'one-sided-source-alpha-audit.json')
    transparent=('bloodborne_dw:source_addon_floor_panels_empty',0)
    transparent_pane=('bloodborne_dw:source_assembly_red_stained_glass_pane',5)
    transparent_models={transparent,transparent_pane}
    source_faces=[row for row in source_alpha['faces'] if row['id']=='90141' and row['variant']==0]
    assert source_faces and all(row['pixels']>0 and row['nonzeroAlphaPixels']==0 for row in source_faces)
    pane_proof=read(DOC/'transparent-93223-v5-source-proof.json')
    assert pane_proof['status']=='PASS_SOURCE_FULLY_TRANSPARENT_STATE_PRESERVED'
    assert pane_proof['productionJarSha256']==digest and pane_proof['oldVariant']==5
    assert pane_proof['faceOccurrences']==22 and pane_proof['alphaSamples']==1144 and pane_proof['nonzeroAlphaSamples']==0
    assert pane_proof['sourceDescriptorMatchesFrozenBaseline'] and pane_proof['canonicalPartsExactlyEqualSourceParts']
    assert pane_proof['nativeModelsPresentAndBoundsEqual'] and not pane_proof['sourceFilesModified']
    assert all(row['modelAreaSquareUnits']>0 and row['nonzeroAlphaSamples']==0 for row in pane_proof['faces'])
    transparent_views=[row for row in alternate if (row['oldRegistryId'],row['oldVariant']) in transparent_models]
    assert len(transparent_views)==4
    assert {row['viewPitchDegrees'] for row in transparent_views if (row['oldRegistryId'],row['oldVariant'])==transparent}=={-25}
    exceptions=review.get('sourceVisibilityExceptions',[])
    assert {(row['oldRegistryId'],row['oldVariant']) for row in exceptions}==transparent_models
    assert all(row.get('status')=='SOURCE_FULLY_TRANSPARENT_STATE_PRESERVED'
               and row.get('visualVisibility')=='NOT_APPLICABLE_FULLY_TRANSPARENT_SOURCE' for row in exceptions)
    nontransparent=[row for row in alternate if (row['oldRegistryId'],row['oldVariant']) not in transparent_models]
    frame_by_file={frame['fileName']:frame for frame in capture['frames']}
    def checked_view(row):
        frame=frame_by_file[row['fileName']]
        assert row['sha256']==frame['sha256']==sha(run/'screenshots'/frame['fileName'])
        assert any(view_key(actual)==view_key(row) and actual.get('kind')=='MIGRATION_ALT_VIEW' for actual in frame['pairs'])
    transparent_review=review.get('transparentViewVisibility',[])
    assert len(transparent_review)==4
    assert {view_key(row) for row in transparent_review}=={view_key(row) for row in transparent_views}
    for row in transparent_review:
        assert row['status']=='SOURCE_FULLY_TRANSPARENT_STATE_PRESERVED'
        assert row['visualVisibility']=='NOT_APPLICABLE_FULLY_TRANSPARENT_SOURCE'
        checked_view(row)
    view_review=review.get('alternateViewVisibility',[])
    assert len(view_review)==len(nontransparent)==4
    assert {view_key(row) for row in view_review}=={view_key(row) for row in nontransparent}
    for row in view_review:
        assert row['status'] in {'VISIBLE_SOURCE_MATCH','CULLED_BACK_FACE'}
        checked_view(row)
    model_review=review.get('alternateModelVisibility',[])
    expected_models={(row['oldRegistryId'],row['oldVariant']) for row in nontransparent}
    assert len(model_review)==len(expected_models)==2
    assert {(row['oldRegistryId'],row['oldVariant']) for row in model_review}==expected_models
    for row in model_review:
        assert row['status']=='VISIBLE_SOURCE_MATCH'
        checked_view(row)
        assert any(view_key(actual)==view_key(row) and actual['status']=='VISIBLE_SOURCE_MATCH' for actual in view_review)
    source_alternate=[row for row in pairs if row.get('kind')=='SOURCE_CATALOGUE_ALT_VIEW']
    expected_source_alternate=[row for row in expected_views if row['kind']=='SOURCE_CATALOGUE_ALT_VIEW']
    assert len(source_alternate)==len(expected_source_alternate)==1
    assert {(row['oldRegistryId'],row['oldVariant']) for row in source_alternate}=={(row['oldRegistryId'],row['oldVariant']) for row in expected_source_alternate}
    assert review.get('alternateSourceModelsVisible') is True
    copied=profile['derived_world_copy'];source=Path(copied['source']);assert copied['copy_byte_verification']=='PASS'
    assert all(sha(source/row['path'])==row['sha256'] for row in copied['files'])
    log=(run/'launch-console.log').read_text(encoding='utf8',errors='replace')
    assert 'logged in with entity id' in log and 'Stopping!' in log and 'All dimensions are saved' in log
    errors=[line for line in log.splitlines() if '/ERROR]' in line]
    assert all(re.fullmatch(r'\[[^\]]+\] \[Render thread/ERROR\]: Failed to verify authentication',line) for line in errors) and len(errors)<=1
    assert not any(token in log for token in ('OutOfMemoryError','Minecraft has crashed!','Crash report saved','Exception in thread'))
    result={'schema':'dreamwalker-v124-native-model-acceptance-v1','status':'PASS','productionJarSha256':digest,'nativeClientOpenAndNormalSave':'PASS','allFrozenNewStatesRendered':len(actual),'mappedOldStatesRendered':len(mapped),'newFunctionalStairStatesRendered':len(functional),'alternateOneSidedModelsVisible':'PASS_FOR_NONTRANSPARENT_SOURCE','alternateNontransparentViewsChecked':len(nontransparent),'alternateModelsActuallyVisible':len(model_review),'fullyTransparentSourceViewsChecked':len(transparent_views),'sourceVisibilityExceptions':exceptions,'retainedPairReviews':sum(row.get('kind')=='RETAINED_REVIEW' for row in pairs),'nativeModelMissingCount':0,'reviewedFrames':len(images),'sourceWorldUnchanged':True,'physicalMouseKeyboard':'NOT_RUN','frameRateBenchmark':'NOT_RUN','review':review,'frames':images,'captureSha256':sha(output),'clientLogSha256':sha(run/'launch-console.log')}
    destination=DOC/'native-comparison-final';assert not destination.exists();destination.mkdir()
    for image in verified:shutil.copyfile(image,destination/image.name)
    (destination/'native-capture.json').write_bytes(output.read_bytes());a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({k:v for k,v in result.items() if k not in ('review','frames')},ensure_ascii=False))
if __name__=='__main__':main()
