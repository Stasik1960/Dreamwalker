"""Read-only bounded original lamp proof: original class bytes and two source cells.

Does not launch Minecraft, rewrite an archive, scan the full world or claim a
target runtime PASS. Source properties are bytecode-derived until the separately
recorded integrated-client probe confirms them.
"""
from __future__ import annotations
import base64,hashlib,json,subprocess,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
import world_io as wi

ROOT=Path(__file__).resolve().parents[1]
SOURCE=Path('C:/Users/vakir/Downloads/bbmc_v16_map (1).zip')
MOD=ROOT/'build/source-reference-client-original-physics-v2/mods/Bloodborne_X_Minecraft_mod_6.0.jar'
ENGINE=ROOT/'build/source-client-forge-1.18.2/libraries/net/minecraft/client/1.18.2-20220404.173914/client-1.18.2-20220404.173914-srg.jar'
JAVAP=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/javap.exe')
MAPPINGS=ROOT/'inputs/extracted/vanilla-1.18.2/client-mappings.txt'
MERGED=ROOT/'build/source-client-forge-1.18.2/libraries/de/oceanlabs/mcp/mcp_config/1.18.2-20220404.173914/mcp_config-1.18.2-20220404.173914-mappings-merged.txt'
POSITIONS=[(228,69,-932),(229,69,-931)]
ORIGINAL_ID='bloodborne:hunter_lamp_light_source'
TARGET_ID='bloodborne_dw:source_hunter_lamp_light'
def digest(data):return hashlib.sha256(data).hexdigest()
def file_digest(path):
    value=hashlib.sha256()
    with path.open('rb') as source:
        for part in iter(lambda:source.read(1024*1024),b''):value.update(part)
    return value.hexdigest()
def typed(value):
    if isinstance(value,wi.Tag):return {'type':value.type,**({'list_type':value.list_type} if value.list_type is not None else {}),'value':typed(value.value)}
    if isinstance(value,dict):return {key:typed(child) for key,child in value.items()}
    if isinstance(value,list):return [typed(child) for child in value]
    if isinstance(value,bytes):return list(value)
    return value
def main():
    reports=ROOT/'reports';reports.mkdir(exist_ok=True)
    classes=['com.potomy.bloodborne.core.init.BlockInit','net.minecraft.world.level.block.AirBlock',
             'net.minecraft.world.level.block.state.BlockBehaviour$Properties','net.minecraft.world.level.material.Material',
             'net.minecraft.world.level.material.Material$Builder','net.minecraft.world.level.block.state.BlockBehaviour']
    outputs={};class_proofs=[]
    for name in classes:
        jar=MOD if name.startswith('com.potomy.') else ENGINE
        result=subprocess.run([str(JAVAP),'-classpath',str(jar),'-p','-c',name],check=True,capture_output=True,text=True)
        outputs[name]=result.stdout
        with zipfile.ZipFile(jar) as archive:
            entry=name.replace('.','/')+'.class';class_proofs.append({'class':name,'jar':str(jar),'entry':entry,'class_sha256':digest(archive.read(entry))})
    init=outputs[classes[0]]
    assert 'bipush        9' in init and 'class net/minecraft/world/level/block/AirBlock' in init and 'Material.f_76296_' in init
    assert 'hunter_lamp_light_source' in init
    # Official + merged SRG mapping establish f_76296_ -> Material.AIR.
    official=MAPPINGS.read_text(encoding='utf-8');merged=MERGED.read_text(encoding='utf-8')
    material=official.split('net.minecraft.world.level.material.Material ->',1)[1].split('\nnet.minecraft.',1)[0]
    assert 'net.minecraft.world.level.material.Material AIR -> a' in material and 'a f_76296_' in merged
    payload=''.join('CLASS '+name+'\nSOURCE '+str(MOD if name.startswith('com.potomy.') else ENGINE)+'\n'+output+'\n' for name,output in outputs.items())
    proof=reports/'SOURCE_LAMP_ENGINE_BYTECODE.txt';proof.write_text(payload,encoding='utf-8')
    cells=[]
    with zipfile.ZipFile(SOURCE) as archive:
        for pos in POSITIONS:
            x,y,z=pos;cx,cz=x//16,z//16;name=f'region/r.{cx//32}.{cz//32}.mca'
            chunk=wi.RegionFile(archive.read(name)).get_chunk(cx%32,cz%32);assert chunk is not None
            root=wi.compound(chunk.nbt().root)
            section=next(s for s in root['sections'].value if wi.compound(s)['Y'].value==y//16)
            palette,indices=wi.section_blocks(section);index=(y%16)*256+(z%16)*16+x%16;state=palette[indices[index]]
            assert wi.block_state_key(state)==ORIGINAL_ID
            bes=[be for be in root.get('block_entities',wi.Tag(9,[])).value if tuple(wi.compound(be)[p].value for p in ['x','y','z'])==pos]
            assert not bes
            cells.append({'dimension':'minecraft:overworld','pos':list(pos),'source_state':wi.block_state_key(state),
                'raw_source_state_typed':typed(state),'raw_source_state_nbt_sha256':digest(wi.encode_nbt(wi.NbtFile('',state))),
                'source_block_entity':None,'source_region':name,'source_chunk':[cx,cz],
                'source_compressed_chunk_sha256':digest(chunk.compressed_payload),'target_state':TARGET_ID,
                'mapping':'Explicit approved bounded compatibility mapping; positions and all unrelated cells unchanged; original typed state retained in preparation provenance.'})
    with zipfile.ZipFile(MOD) as archive:
        assets=[name for name in archive.namelist() if 'hunter_lamp_light_source' in name]
    existing_world=reports/'WORLD_AUDIT.json'
    prior=json.loads(existing_world.read_text(encoding='utf-8'))
    global_count=sum(row['count'] for row in prior['states'] if row['state']==ORIGINAL_ID)
    assert global_count==len(cells)==2,'Existing full audit and bounded exact cell recognition disagree'
    report={'schema':'dreamwalker-source-lamp-proof-v1','status':'ORIGINAL_BYTECODE_AND_EXACT_SOURCE_CELLS_PROVEN_TARGET_RUNTIME_PENDING',
        'source_archive':str(SOURCE),'source_archive_sha256_previously_verified':'39e83f2941b768fd2ae9f84d8f57dc76aec78bd926dc5e38eab3059251d2bb08',
        'full_archive_rehash_in_this_bounded_audit':False,'original_mod':{'path':str(MOD),'sha256':file_digest(MOD)},
        'source_mappings':{'official':str(MAPPINGS),'official_sha256':file_digest(MAPPINGS),'merged_srg':str(MERGED),'merged_srg_sha256':file_digest(MERGED)},
        'bytecode':{'path':str(proof),'sha256':file_digest(proof),'classes':class_proofs},'source_assets_named_for_light':assets,
        'source_implementation':'AirBlock(Properties.of(Material.AIR).lightLevel(state -> 9)); no Properties.air/no additional properties',
        'semantics_bytecode_derived':{'light':9,'render':'INVISIBLE','outline':'EMPTY','collision':'EMPTY via inherited hasCollision=true + AirBlock empty outline',
            'occlusion':'EMPTY','opacity':0,'fluid':'EMPTY','replaceable':True,'isAir':False,'hasCollision_setting':True,'canOcclude_setting':True,
            'material_blocks_motion':False,'material_solid':False,'material_solid_blocking':False,'material_liquid':False,'material_flammable':False,
            'piston_reaction':'NORMAL','map_color':'NONE','hardness':0,'resistance':0,'random_ticks':False,'block_entity':False,'state_properties':[],
            'item_registered':False,'sound':'STONE','friction':.6,'speed_multiplier':1,'jump_multiplier':1},
        'source_cells':cells,'previous_full_world_count_evidence':{'path':str(existing_world),'sha256':file_digest(existing_world),'count':global_count,'full_scan_repeated':False},
        'scope':'Exactly two known bounded-source cells, not a full-world search or broad RP rewrite.',
        'original_integrated_client_probe':'SEPARATE_SOURCE_REFERENCE_REPORT_REQUIRED','target_native_game_test':'SourceTechnicalLightGameTests.originalAirPhysicsLightNineAndNativePalettePersistWithoutAnItem; pending coordinated execution',
        'unsupported_ids_policy':'Preparation must enumerate and reject every unmapped non-target-registered state before target load; do not silently allow vanilla AIR fallback.',
        'original_inputs_written':False,'catalog_numeric_id':None,'collectible_item':False}
    path=reports/'SOURCE_LAMP_BLOCK_AUDIT.json'
    actual=reports/'SOURCE_PHYSICS_CLIENT_SWEPT_ACTUAL.json'
    if actual.exists():
        physics=json.loads(actual.read_text(encoding='utf-8'));samples=[row for row in physics['actual_shapes'] if tuple(row['pos']) in POSITIONS]
        assert len(samples)==2 and all(row['lightEmission']==9 and row['isAir'] is False and row['replaceable'] is True and row['actualBlockClass']=='net.minecraft.world.level.block.AirBlock' and row['collisionShape']==row['outlineShape']==[] for row in samples)
        report['original_integrated_client_probe']={'status':'PASS_TWO_EXACT_LIGHT_CELLS','path':str(actual),'sha256':file_digest(actual),'runtime_mode':physics['runtime_mode'],'actual_shapes':samples}
    executions=sorted(reports.glob('FIRST_SET_GAMETEST_ATTEMPT_*.xml'),key=lambda path:int(path.stem.rsplit('_',1)[1]))
    executed=executions[-1] if executions else reports/'FIRST_SET_GAMETEST_ATTEMPT_11.xml'
    if executed.exists():
        cases=[case for case in ET.fromstring(executed.read_bytes()).iter('testcase') if case.attrib['name']=='sourcetechnicallightgametests.originalairphysicslightnineandnativepalettepersistwithoutanitem']
        assert len(cases)==1 and not list(cases[0]),'Native technical light execution did not pass'
        report['target_native_game_test']={'status':'PASS','path':str(executed),'sha256':file_digest(executed),'case':cases[0].attrib['name'],'scope':'Specified executed native physics/ray/movement/palette test only; other compatibility cases, independent saved-world preservation and artifact freeze have separate evidence.'}
        report['status']='ORIGINAL_BYTECODE_SOURCE_CELLS_AND_TARGET_NATIVE_TEST_PROVEN_SAVED_WORLD_CHECK_SEPARATE'
    saved_reports=sorted((path for path in reports.glob('SOURCE_REVIEW_POSTSAVE_INDEPENDENT_V*.json') if path.stem.rsplit('V',1)[1].isdigit()),key=lambda path:int(path.stem.rsplit('V',1)[1]))
    saved=saved_reports[-1] if saved_reports else reports/'SOURCE_REVIEW_POSTSAVE_INDEPENDENT_V4.json'
    if saved.exists():
        verifier=json.loads(saved.read_text(encoding='utf-8'));samples=verifier['source_technical_light_checks'];original={tuple(row['pos']):row for row in cells}
        assert len(samples)==2 and all(row['status']=='PASS' and row['state']==TARGET_ID and row['live_parity_evidence']['luminance']==9 and row['live_parity_evidence']['runtimeParity'].startswith('PASS_') and digest(base64.b64decode(row['live_parity_evidence']['originalTypedState']))==original[tuple(row['pos'])]['raw_source_state_nbt_sha256'] for row in samples)
        report['target_bounded_saved_light_check']={'status':'PASS_TWO_EXACT_CELLS_AND_TYPED_PROVENANCE','path':str(saved),'sha256':file_digest(saved),'source_technical_light_checks':samples,'limit':'Only these two technical cells and their recorded live parity/provenance; complete world/entity/metadata parity and user review have separate verdicts.'}
        report['status']='ORIGINAL_SOURCE_AND_TARGET_NATIVE_AND_BOUNDED_SAVED_LIGHT_SCOPE_PASS'
    path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':report['status'],'cells':len(cells),'light':9,'target_id':TARGET_ID,'source_assets':assets}))
if __name__=='__main__':main()
