"""Freeze historical sets, never derive native test expectations from live rows."""
import hashlib,json,subprocess
from v127_visual_export import ROOT,RES,write_json
BASE='0c985dc28e4a38900cce3055ddb5658a71ed95e5'
def old(path):
    blob=subprocess.run(['git','show',BASE+':bloodborne complete/'+path],cwd=ROOT.parent,check=True,capture_output=True).stdout
    return json.loads(blob),hashlib.sha256(blob).hexdigest()
def main():
    remaining,a=old('src/architecture/resources/bloodborne_dw/remaining-catalogue.json')
    debug,b=old('src/architecture/resources/bloodborne_dw/debug_catalogue.json')
    registered=sorted('bloodborne_dw:'+r['path'] for r in remaining['objects'])
    reservations={r['temporaryId']:r['registryId'] for r in debug['entries']}
    marker=json.loads((ROOT/'build/v126-gallery-input.json').read_text(encoding='utf-8'))
    working=sorted(r['registry'] for r in marker['catalogue']['exhibits'] if r['origin']!='V12' and r['kind']=='architecture')
    assert len(registered)==1045 and len(reservations)==1322 and len(working)==544 and set(working)<=set(registered)
    assert all(any(r['registryId']==registry and not r.get('canonicalRegistryId') for r in debug['entries']) for registry in working)
    removed=['bloodborne_dw:'+p for p in ['source_bricks_bricks_0','source_polished_granite_slab','source_snow_height8','source_slabs_prismarine_brick_slab_pbs_04','source_nether_brick_slab','source_polished_andesite_slab','source_red_tulip_2']]
    assert set(removed)<=set(working)
    added=['bloodborne_dw:source_original_tree','bloodborne_dw:source_door_fragment']
    extra={'93230':'bloodborne_dw:dw_barrier','93231':'bloodborne_dw:dw_light','93232':added[0],'93233':added[1]}
    result={'schemaVersion':1,'baselineCommit':BASE,'baselineRemainingSha256':a,'baselineDebugSha256':b,
        'frozenV126SourceRegistrations':registered,'frozenV126WorkingSourceTypes':working,'frozenV126ReservedNumbers':reservations,
        'approvedV127HiddenCloneTypes':sorted(removed),'approvedV127AdditionalSourceTypes':sorted(added),'approvedV127AdditionalReservedNumbers':extra}
    write_json(ROOT/'src/gametest/resources/bloodborne_dw/v127-catalogue-expectations.json',result)
    print(json.dumps({'status':'PASS_FROZEN_SETS','registered':len(registered),'reserved':len(reservations),'working':len(working),'approvedHiddenClones':len(removed),'approvedNewSources':len(added),'approvedNewNumbers':len(extra)}))
if __name__=='__main__':main()
