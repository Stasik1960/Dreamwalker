"""Reuse the accepted V12.7 source clip/preparation; update only the final followups."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
source=(ROOT/'tools/prepare_gallery_v1271_input.py').read_text(encoding='utf8')
def replace(old,new):
    global source
    assert source.count(old)==1,old
    source=source.replace(old,new)
replace("'v12.7.1' in meta['version']","'v12.7.3-final-checks' in meta['version']")
replace("rows=marker['catalogue']['exhibits'];new=[r for r in rows if r['origin']!='V12' and r['kind']=='architecture']", """rows=marker['catalogue']['exhibits']
            removed=[r for r in rows if r['registry'] in {'bloodborne_dw:source__wall_dec_top','bloodborne_dw:source_hold_wall_lantern_4'}]
            assert {r['id'] for r in removed}=={'90025','90599'}
            rows=[r for r in rows if r not in removed];marker['catalogue']['exhibits']=rows
            marker['finalFollowups']={'removedDuplicateCatalogueIds':['90025','90599'],'savedRegistryIdsRetained':True,'newSingleDoorId':'90960','originalPreparedCityReused':True}
            new=[r for r in rows if r['origin']!='V12' and r['kind']=='architecture']""")
replace("assert len(new)==539", "assert len(new)==537")
replace("assert len(rows)==621 and len(marker['reviewPlatforms'])==22", "assert len(rows)==619 and len(marker['reviewPlatforms'])==22")
replace("            for group in marker['reviewPlatforms']:", """            template=copy.deepcopy(next(g for g in marker['reviewPlatforms'] if g['primaryId']=='90923'))
            template.update(key='V1273-doors-90960-open',primaryId='90960',id='90960',registry='bloodborne_dw:source_stairs_andesite_stairs_top',russianLabel='Одинарная дверь · открыта',open=True,reviewPoseIndex=0)
            c=template['components'][0];c.update(registryId=template['registry'],mainId='90960',properties={'variant':'0','rotation':'0','profile':'base','open':'true'},descriptor='src/architecture/resources/bloodborne_dw/composite/source_stairs_andesite_stairs_top.json',sourceShift=[0,0,0],mountY=0)
            template['components']=[c];marker['reviewPlatforms'].append(template)
            for group in marker['reviewPlatforms']:""")
replace("displayRevision='V12.7.1'", "displayRevision='V12.7.3'")
replace("Original V12 plus539 canonical NEW and2 utility items; hidden compatibility states remain source-city only", "Original V12 plus537 canonical NEW and2 utility items; two requested duplicates remain hidden saved map states")
replace("newWorkingTypes=541,newArchitectureTypes=539", "newWorkingTypes=539,newArchitectureTypes=537")
namespace={'__name__':'__main__','__file__':__file__}
exec(compile(source,str(ROOT/'tools/prepare_gallery_v1271_input.py'),'exec'),namespace)
