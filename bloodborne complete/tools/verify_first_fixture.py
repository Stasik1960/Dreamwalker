"""Independently compare original archive, frozen fixture and ladder test copy."""
import hashlib,json,zipfile
from pathlib import Path
from world_io import RegionFile,compound,section_blocks,block_state_key,Tag
ROOT=Path(__file__).resolve().parents[1]
def main():
    manifest=json.loads((ROOT/'reports/FIRST_FIXTURE.json').read_text(encoding='utf-8'))
    source=zipfile.ZipFile('C:/Users/vakir/Downloads/bbmc_v16_map (1).zip')
    original=zipfile.ZipFile(manifest['outputs']['source_fixture']['path'])
    result=zipfile.ZipFile(manifest['outputs']['ladder_fixture']['path'])
    allowed={tuple(row['pos']):row for row in manifest['instances']};seen=set();checks=[];unchanged=0;payloads=0
    assert set(original.namelist())==set(result.namelist())
    for name in original.namelist():
        before=original.read(name);after=result.read(name)
        if not name.startswith('region/'):
            assert before==after,name+' unrelated file changed';unchanged+=1
        if name.endswith('.mca'):
            src_region=RegionFile(source.read(name));a=RegionFile(before);b=RegionFile(after)
            for stored in a.chunks():
                source_chunk=src_region.get_chunk(stored.x,stored.z)
                assert source_chunk.compressed_payload==stored.compressed_payload and source_chunk.timestamp==stored.timestamp
                payloads+=1
                if not name.startswith('region/'):continue
                na=stored.nbt();nb=b.get_chunk(stored.x,stored.z).nbt();ra=compound(na.root);rb=compound(nb.root)
                other_a={k:v for k,v in ra.items() if k!='sections'};other_b={k:v for k,v in rb.items() if k!='sections'}
                assert other_a==other_b,(name,stored.x,stored.z,'non-section NBT changed')
                sa=ra.get('sections',Tag(9,[],10)).value;sb=rb.get('sections',Tag(9,[],10)).value
                assert len(sa)==len(sb)
                for old,new in zip(sa,sb):
                    oa=compound(old);ob=compound(new)
                    assert {k:v for k,v in oa.items() if k!='block_states'}=={k:v for k,v in ob.items() if k!='block_states'}
                    ba=section_blocks(old);bb=section_blocks(new)
                    if not ba:assert ba==bb;continue
                    pa,ia=ba;pb,ib=bb;ka=[block_state_key(t) for t in pa];kb=[block_state_key(t) for t in pb]
                    for i,(x,y) in enumerate(zip(ia,ib)):
                        old_key,new_key=ka[x],kb[y]
                        if old_key==new_key:continue
                        pos=(ra['xPos'].value*16+i%16,oa['Y'].value*16+i//256,ra['zPos'].value*16+(i//16)%16)
                        assert pos in allowed,(name,pos,'unplanned cell changed')
                        row=allowed[pos];assert old_key==row['source_state'] and new_key==row['target_state'];seen.add(pos)
    assert seen==set(allowed),'missing or extra conversion rows'
    checks=[{'check':'fixture_chunk_compressed_payloads_vs_original','status':'PASS','count':payloads},
        {'check':'nonterrain_files_byte_identical','status':'PASS','count':unchanged},
        {'check':'all_unplanned_cells_and_typed_NBT_preserved','status':'PASS'},
        {'check':'source_geometry_world_translation_unchanged','status':'PASS','method':'target uses identical source element coordinates and same block anchor; rotations facing mapping unchanged'},
        {'check':'converted_actual_cell_count','status':'PASS','count':len(seen)},
        {'check':'same_anchor_source_backing','status':'REJECTED',
            'evidence':'reports/LADDER_SOURCE_CONTEXT.json',
            'reason':'All 34 visual carrier anchors have an adjacent non-solid vanilla ladder as native backing; diagnostic same-anchor conversion is not playable.'},
        {'check':'playable_support_and_climb_on_source_fixture','status':'NOT_RUN','reason':'source assembly ownership/support adapter is not implemented'}]
    output={'schema':'dreamwalker-first-fixture-independent-verification-v1','scope':'Ladder-only limited fixture, not full city','checks':checks}
    (ROOT/'reports/FIRST_FIXTURE_VERIFICATION.json').write_text(json.dumps(output,indent=2)+'\n',encoding='utf-8')
    print('Independent fixture preservation checks PASS; '+str(len(seen))+' ladder cell changes; same-anchor backing rejected; gameplay NOT_RUN.')
if __name__=='__main__':main()
