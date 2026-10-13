"""Scoped atomic QA patch for the proven second fully transparent source state."""
import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / 'tools/collect_v124_comparison.py'
text = path.read_text(encoding='utf8')
start = text.index("    transparent=('bloodborne_dw:source_addon_floor_panels_empty',0)")
end = text.index('    source_alternate=', start)
assert 'len(nontransparent)==6' in text[start:end] and 'len(expected_models)==3' in text[start:end]
replacement = """    transparent=('bloodborne_dw:source_addon_floor_panels_empty',0)
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
"""
text = text[:start] + replacement + text[end:]
ast.parse(text)
temporary = path.with_name(path.name + '.v124.tmp')
temporary.write_bytes(text.encode('utf8'))
temporary.replace(path)
print('PASS: four nontransparent views/two visible models, four independently proven transparent views')
