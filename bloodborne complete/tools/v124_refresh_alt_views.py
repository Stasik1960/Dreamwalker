"""QA-only alternate views derive selectors from the latest reviewed plan."""
import json
from pathlib import Path
D=Path(__file__).resolve().parents[1]/'docs/v12.4-dedup'
path=D/'additional-migration-review-fixtures.json'
doc=json.loads(path.read_text(encoding='utf-8'))
plan=json.loads((D/'general-functional-groups.json').read_text(encoding='utf-8'))
maps={(r['oldId'],r['oldVariant']):r for g in plan['groups'] for r in g['mappings']}
groups={g['mainId']:g for g in plan['groups']}
for row in doc['fixtures']:
    if row['oldId']!='93223':continue
    m=maps[(row['oldId'],row['oldVariant'])];v=m['variant']
    row.update(groupId=m['mainId'],mainId=m['mainId'],mainRegistryId=m['mainRegistryId'],variant=v%16,
        yawDeltaSteps=m['yawDeltaSteps'],sourceShiftDelta=m['sourceShiftDelta'],
        properties={'art_bank':str(v//16)} if groups[m['mainId']]['newVariantCount']>16 else {})
    row.pop('savedNativeFixture',None)
    row['savedNativeFixtureBinding']='Fresh final native savedRows lookup by oldRegistryId|oldVariant; no embedded preview payload.'
registry='bloodborne_dw:source_addon_wall_deco_1'
if not any(r['oldId']=='90245' for r in doc['fixtures']):
    doc['fixtures'].append({'kind':'SOURCE_CATALOGUE_ALT_VIEW','groupId':'90245','oldId':'90245','oldRegistryId':registry,
        'oldVariant':0,'mainId':'90245','mainRegistryId':registry,'variant':0,'yawDeltaSteps':0,'sourceShiftDelta':[0,0,0],
        'properties':{},'viewYawDegrees':215,'reviewReason':'Native source catalogue page92 showed culled/blank one-sided surface; inspect opposite side before declaring source visual defect.'})
doc['status']='FINAL_PLAN_ALTERNATE_VIEWS_READY'
path.write_text(json.dumps(doc,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
