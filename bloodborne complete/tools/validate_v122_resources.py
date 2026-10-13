"""Re-run the independent source/UV/dependency audit for surviving V12.2 art.

The historical transfer manifest is immutable. Its two deliberately retired
objects and exact deleted resource paths are filtered only in memory.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
legacy = ROOT / 'tools/validate_remaining_artifacts.py'
source = legacy.read_text(encoding='utf-8')
needle = "    failures=[];counts={};source_cache={}"
assert source.count(needle) == 1
filter_code = """    retirement=json.loads((ROOT/'docs/v12.2-simplified-collisions/retirement.json').read_text(encoding='utf-8'))
    assert retirement['status']=='PASS'
    retired=json.loads((RES/'bloodborne_dw/retired-source-objects.json').read_text(encoding='utf-8'))
    ids={row['registryId'] for row in retired['objects']}
    assert ids=={'bloodborne_dw:source_jungle_trapdoor_bottom','bloodborne_dw:source_jungle_trapdoor_open'}
    removed={row['path'].split('/resources/',1)[1] for row in retirement['removedFiles']}
    manifest['objects']=[row for row in manifest['objects'] if row['registryId'] not in ids]
    manifest['resources']=[row for row in manifest['resources'] if row['file'] not in removed]
    assert len(manifest['objects'])==1045
    policy=json.loads((ROOT/'docs/collision-simplification/policy-report.json').read_text(encoding='utf-8'))
    updates={row['file'].split('/resources/',1)[1]:row for row in policy['resources']}
    assert len(updates)==1045
    for row in manifest['resources']:
        if row['file'] in updates:
            update=updates[row['file']]
            assert row['sha256']==update['beforeSha256'], 'Policy has lost the original descriptor provenance'
            row['sha256']=update['afterSha256']
"""
source = source.replace(needle, filter_code + needle)
exec(compile(source, str(legacy), 'exec'), {'__name__': '__main__', '__file__': str(legacy)})
