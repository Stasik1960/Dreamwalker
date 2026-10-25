"""Freeze bounded coordinate-backed source samples; diagnostic ladder-only transform.

This is an explicitly limited test fixture, NOT the deliverable full world or a
playable first-set migration. Ladder-only output deliberately remains diagnostic:
it does not yet own the adjacent invisible source climbing/support assembly.
Untouched selected chunk compressed payloads are copied byte-for-byte. Other
source terrain/entities/POI and Forge NBT remain preserved, not discarded.
"""
from __future__ import annotations
import copy, hashlib, json, math, struct, zipfile
from pathlib import Path
from world_io import RegionFile, Tag, compound, section_blocks, block_state_key, pack_palette_indices, encode_nbt
from positional_rng import weighted_index
from record_inputs import sha256
ROOT=Path(__file__).resolve().parents[1]
SOURCE=Path('C:/Users/vakir/Downloads/bbmc_v16_map (1).zip')
SAMPLES=[('vertical_ladder',(177,33,-1108)),('double_door_candidate',(-93,59,-212)),
    ('shutters_candidate',(-32,37,-1120)),('thin_window_candidate',(-121,53,-241)),
    ('rp_lamp',(229,69,-931)),('architecture_tree_1',(-16,119,-504)),
    ('architecture_tree_2',(-14,119,-500)),('independent_wall_candidate',(154,58,-976)),
    ('independent_roof_candidate',(-173,59,-239))]
def subset_region(data,keep,rx,rz):
    out=bytearray(8192); selected=[]
    for lx,lz in sorted(keep,key=lambda c:c[1]*32+c[0]):
        i=lx+lz*32; loc=int.from_bytes(data[i*4:i*4+4],'big'); sector,n=loc>>8,loc&255
        if not sector:continue
        payload=data[sector*4096:(sector+n)*4096]
        if len(payload)!=n*4096:payload+=bytes(n*4096-len(payload))
        target=len(out)//4096;out.extend(payload)
        out[i*4:i*4+4]=((target<<8)|n).to_bytes(4,'big')
        out[4096+i*4:4096+i*4+4]=data[4096+i*4:4096+i*4+4]
        src=RegionFile(data).get_chunk(lx,lz)
        copied=RegionFile(bytes(out)).get_chunk(lx,lz)
        assert copied.compressed_payload==src.compressed_payload and copied.compression==src.compression and copied.timestamp==src.timestamp
        selected.append({'chunk':[rx*32+lx,rz*32+lz],'compression':src.compression,'payload_sha256':hashlib.sha256(src.compressed_payload).hexdigest()})
    return bytes(out),selected
def migrate_ladder(data,path,log):
    region=RegionFile(data)
    for stored in list(region.chunks()):
        nbt=stored.nbt();root=compound(nbt.root);cx=root['xPos'].value;cz=root['zPos'].value;changed=False
        for section in root.get('sections',Tag(9,[],10)).value:
            values=section_blocks(section)
            if not values:continue
            palette,indices=values; fields=compound(section);sy=fields['Y'].value;container=compound(fields['block_states'])
            applicable={i:compound(s) for i,s in enumerate(palette) if compound(s)['Name'].value=='minecraft:beehive' and compound(compound(s).get('Properties',Tag(10,{}))).get('honey_level',Tag(8,'' )).value=='1'}
            if not applicable:continue
            key_to_index={block_state_key(t):i for i,t in enumerate(palette)}
            for index,pi in enumerate(indices):
                if pi not in applicable:continue
                source=applicable[pi];props=compound(source['Properties']);pos=[cx*16+index%16,sy*16+index//256,cz*16+(index//16)%16]
                variant=weighted_index([1,1,1],pos)
                target=Tag(10,{'Name':Tag(8,'bloodborne_dw:prototype_ladder'),'Properties':Tag(10,{
                    'facing':Tag(8,props['facing'].value),'variant':Tag(8,str(variant)),'profile':Tag(8,'base'),'waterlogged':Tag(8,'false')})})
                key=block_state_key(target)
                if key not in key_to_index:key_to_index[key]=len(palette);palette.append(target)
                indices[index]=key_to_index[key];changed=True
                log.append({'dimension':'minecraft:overworld','pos':pos,'source_state':block_state_key(Tag(10,source)),'target_state':key,
                    'source_model':f'minecraft:block/hold/wood_ladder_{variant+2:02}','chosen_variant':variant,
                    'source_position_seed_frozen':True,'transform_translation':[0,0,0],'source_offset':[0,0,0],'members':[pos]})
            container['data']=Tag(12,pack_palette_indices(indices,len(palette)))
        if changed:
            # Retain original lighting/heightmaps here: prototype's lighting effect
            # is intentionally unaccepted and full-city lighting policy not ready.
            region.set_chunk(stored.x,stored.z,nbt,compression=stored.compression,timestamp=stored.timestamp)
    return region.to_bytes()
def main():
    destination=ROOT/'build/prototype';destination.mkdir(parents=True,exist_ok=True)
    original=destination/'Source-coordinate-fixture.zip';migrated=destination/'Ladder-only-coordinate-fixture.zip'
    backups=[]
    for old,historical in [(original,destination/'Source-coordinate-fixture-v1-historical.zip'),
                           (migrated,destination/'Ladder-only-coordinate-fixture-v1-historical.zip'),
                           (ROOT/'reports/FIRST_FIXTURE.json',ROOT/'reports/FIRST_FIXTURE_V1_HISTORICAL.json'),
                           (ROOT/'reports/FIRST_FIXTURE_VERIFICATION.json',ROOT/'reports/FIRST_FIXTURE_VERIFICATION_V1_HISTORICAL.json')]:
        if old.exists() and not historical.exists():historical.write_bytes(old.read_bytes())
        if historical.exists():backups.append({'original_path':str(old),'historical_path':str(historical),'historical_sha256':sha256(historical)})
    dump_path=ROOT/'reports/FIRST_FIXTURE_HISTORICAL_INDEX.json'
    dump_path.write_text(json.dumps({'status':'UNCHANGED_HISTORICAL_BYTES_RETAINED','artifacts':backups},indent=2)+'\n',encoding='utf-8')
    source_sha_before=sha256(SOURCE)
    chunks={(x//16+dx,z//16+dz) for _,(x,y,z) in SAMPLES for dx in [-1,0,1] for dz in [-1,0,1]}
    by_region={}
    for cx,cz in chunks:by_region.setdefault((cx//32,cz//32),set()).add((cx%32,cz%32))
    selected_files={};manifest=[];log=[]
    with zipfile.ZipFile(SOURCE) as source:
        for name in source.namelist():
            if name=='level.dat' or name.startswith('data/') and not name.endswith('/'):
                selected_files[name]=source.read(name);continue
            parts=name.split('/')
            if len(parts)!=2 or parts[0] not in ['region','entities','poi'] or not name.endswith('.mca'):continue
            _,rx,rz,_=parts[-1].split('.');pair=(int(rx),int(rz))
            if pair not in by_region:continue
            data=source.read(name)
            if not data:continue
            subset,records=subset_region(data,by_region[pair],*pair)
            if records:
                selected_files[name]=subset;manifest.append({'path':name,'chunks':records})
    def write_zip(path,files):
        with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as z:
            for n,data in sorted(files.items()):
                info=zipfile.ZipInfo(n,date_time=(2026,10,7,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,data)
    write_zip(original,selected_files)
    converted={n:migrate_ladder(data,n,log) if n.startswith('region/') else data for n,data in selected_files.items()}
    write_zip(migrated,converted)
    second_log=[];second={n:migrate_ladder(data,n,second_log) if n.startswith('region/') else data for n,data in converted.items()}
    assert second==converted and not second_log,'Prototype converter is not idempotent'
    assert sha256(SOURCE)==source_sha_before,'Original source changed'
    result={'schema':'dreamwalker-first-fixture-v2','status':'SOURCE_SUBSET_AND_DIAGNOSTIC_LADDER_ONLY_NOT_PLAYABLE_MIGRATION','source_world_sha256':source_sha_before,
        'samples':[{'purpose':name,'dimension':'minecraft:overworld','pos':list(pos)} for name,pos in SAMPLES],
        'subset_rule':'3x3 original terrain/entity/POI chunks around each sample; selected original chunk compressed bytes preserved',
        'not_full_world':True,'rp_entities_not_migrated':True,'source_game':'1.18.2 Forge + original Bloodborne needed for full source-entity reference',
        'numeric_ids_frozen':False,'region_chunks':manifest,'ladder_cell_changes':len(log),'instances':log,
        'historical_artifacts':backups,'checks':{'selected_original_compressed_chunk_payload_and_timestamps':'PASS','original_source_bytes_unchanged':'PASS',
            'offline_second_conversion_idempotent':'PASS','lighting_heightmaps_runtime_validation':'NOT_RUN',
            'source_1_18_rng_bytecode':json.loads((ROOT/'reports/SOURCE_RNG_VERIFICATION.json').read_text(encoding='utf-8'))['status']
                if (ROOT/'reports/SOURCE_RNG_VERIFICATION.json').exists() else 'NOT_RUN',
            'source_same_anchor_native_support':'REJECTED_ALL_34' if (ROOT/'reports/LADDER_SOURCE_CONTEXT.json').exists()
                and json.loads((ROOT/'reports/LADDER_SOURCE_CONTEXT.json').read_text(encoding='utf-8')).get('backing_outcomes')=={'False':34} else 'NOT_RUN',
            'source_assembly_migration':'NOT_IMPLEMENTED','gameplay':'NOT_RUN'},
        'outputs':{'source_fixture':{'path':str(original),'sha256':sha256(original)},'ladder_fixture':{'path':str(migrated),'sha256':sha256(migrated)}}}
    (ROOT/'reports/FIRST_FIXTURE.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print('Frozen source fixture; ladder-only transformed cells='+str(len(log))+', second conversion unchanged.')
if __name__=='__main__':main()
