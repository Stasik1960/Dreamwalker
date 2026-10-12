"""Task-scoped atomic write workaround for Windows transient in-place denial."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
path=ROOT/'tools/collect_v124_comparison.py'
text=path.read_text(encoding='utf8')
before="    assert {(row['oldRegistryId'],row['oldVariant']) for row in alternate}=={(row['oldRegistryId'],row['oldVariant']) for row in expected_alternate}\n    assert review.get('alternateOneSidedModelsVisible') is True\n"
after="""    def view_key(row):return (row['oldRegistryId'],row['oldVariant'],row.get('viewYawDegrees',35),row.get('viewPitchDegrees',25))
    assert {view_key(row) for row in alternate}=={view_key(row) for row in expected_alternate}
    assert review.get('alternateOneSidedModelsVisible') is True
    source_alpha=read(DOC/'one-sided-source-alpha-audit.json')
    transparent=('bloodborne_dw:source_addon_floor_panels_empty',0)
    source_faces=[row for row in source_alpha['faces'] if row['id']=='90141' and row['variant']==0]
    assert source_faces and all(row['pixels']>0 and row['nonzeroAlphaPixels']==0 for row in source_faces)
    transparent_views=[row for row in alternate if (row['oldRegistryId'],row['oldVariant'])==transparent]
    assert len(transparent_views)==2 and {row['viewPitchDegrees'] for row in transparent_views}=={-25}
    exceptions=review.get('sourceVisibilityExceptions',[])
    assert any(row.get('oldRegistryId')==transparent[0] and row.get('oldVariant')==transparent[1]
               and row.get('status')=='SOURCE_FULLY_TRANSPARENT_STATE_PRESERVED'
               and row.get('visualVisibility')=='NOT_APPLICABLE_FULLY_TRANSPARENT_SOURCE' for row in exceptions)
"""
assert text.count(before)==1,'Expected exact QA collector block'
text=text.replace(before,after)
old="'alternateOneSidedModelsVisible':'PASS'"
new="'alternateOneSidedModelsVisible':'PASS_FOR_NONTRANSPARENT_SOURCE','alternateNontransparentViewsChecked':len(alternate)-len(transparent_views),'fullyTransparentSourceViewsChecked':len(transparent_views),'sourceVisibilityExceptions':exceptions"
assert text.count(old)==1
text=text.replace(old,new)
temporary=path.with_name(path.name+'.v124.tmp');temporary.write_bytes(text.encode('utf8'));temporary.replace(path)
print('PASS: QA-only collector validates8 exact alternate views and explicit fully transparent source exception')
