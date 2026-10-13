"""Derive gallery-only readers/navigation from reviewed previous tooling."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def replace(s,old,new):
    assert s.count(old)==1,old[:100]
    return s.replace(old,new)
source=(ROOT/'tools/verify_gallery_v126.py').read_text(encoding='utf8').replace('v126','v127')
source=source.replace('from prepare_gallery_v127_input import ArchiveGeometry,component_geometry,assembly_contract,pose_index','from prepare_gallery_v126_input import ArchiveGeometry,component_geometry,assembly_contract,pose_index')
source=replace(source,'len(mainrows)==647','len(mainrows)==644')
source=replace(source,'len(newrows)==544 and len({r[\'id\'] for r in newrows})==544','len(newrows)==539 and len({r[\'id\'] for r in newrows})==539')
source=replace(source,"positions={tuple(r['root']) for r in arch}","positions={tuple(r['root']) for r in arch+mainrows if 'root' in r}")
source=replace(source,"if row['kind']!='architecture':","if row['kind'] not in ('architecture','utility'):")
source=replace(source,"retired.update(assemblies['hiddenPartRedirects'])","retired.update(assemblies['hiddenPartRedirects'])\n        newer=json.loads(jar.read('bloodborne_dw/imported-type-aliases-v127.json'))\n        retired.update(r for g in newer['groups'] for r in g['members'] if r!=g['primaryRegistryId'])")
source=replace(source,"stable.sort(key=lambda r:r['key']);compared=False","""utility=[r for r in mainrows if r['kind']=='utility'];assert len(utility)==2
    for row in utility:
        state=states[tuple(row['root'])];assert state['registry']==row['registry'] and state['properties']==row['placedState']
        stable.append({'key':row['key'],'root':row['root'],'state':state})
    stable.sort(key=lambda r:r['key']);compared=False""")
(ROOT/'tools/verify_gallery_v127.py').write_text(source,encoding='utf8')
final=(ROOT/'tools/finalize_gallery_v126_world.py').read_text(encoding='utf8').replace('v126','v127').replace('V12.6','V12.7')
final=final.replace('combined JAR V12.7 вместо предыдущего BB JAR','combined JAR V12.7 (SHA b98c752b...) и WorldEdit 7.2.15')
(ROOT/'tools/finalize_gallery_v127_world.py').write_text(final,encoding='utf8')
print('PASS_PREPARED_V127_DISK_VERIFIER_AND_NAVIGATION')
