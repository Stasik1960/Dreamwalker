"""Copy exact V12.3 native proof and independently verify saved state/NBT/UUID.

No Minecraft/build launch, no world writes, no existing release/report edits.
"""
from __future__ import annotations
import hashlib,json,shutil,sys,uuid
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from world_io import RegionFile,compound,section_blocks,block_state_key
from verify_flat_gallery import plain
ROOT=Path(__file__).resolve().parents[2]
DEST=ROOT/'docs/v12.3-outline-icons'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf8'))
def records(world,targets):
    found={};wanted=set(tuple(r['root']) for r in targets)
    regions={world/'region'/f'r.{(x>>4)//32}.{(z>>4)//32}.mca' for x,y,z in wanted}
    for region in regions:
        for chunk in RegionFile.open(region).chunks():
            data=compound(chunk.nbt().root);cx,cz=data['xPos'].value,data['zPos'].value;cells=[p for p in wanted if p[0]>>4==cx and p[2]>>4==cz]
            if not cells:continue
            sections={compound(t)['Y'].value:t for t in data['sections'].value}
            entities={tuple(compound(t)[key].value for key in ['x','y','z']):plain(t) for t in data['block_entities'].value}
            for x,y,z in cells:
                palette,indices=section_blocks(sections[y>>4]);found[(x,y,z)]={'state':block_state_key(palette[indices[(y&15)*256+(z&15)*16+(x&15)]]),'entity':entities[(x,y,z)]}
    assert set(found)==wanted
    return found
def uuid_str(words):return str(uuid.UUID(int=sum((word&0xffffffff)<<(96-i*32) for i,word in enumerate(words))))
def main():
    first=ROOT/'build/runtime-server-v123-outline-3';reopen=ROOT/'build/runtime-server-v123-outline-reopen-1'
    create=read(first/'v123-native-output.json');opened=read(reopen/'v123-native-output.json')
    assert create['status']=='PASS' and opened['status']=='PASS_REOPEN' and len(create['fixtures'])==len(opened['fixtures'])==21
    assert create['productionJarSha256']==opened['productionJarSha256']
    assert read(ROOT/'build/v123-server-3.json')['status']==read(ROOT/'build/v123-server-reopen-1.json')['status']=='PASS'
    source_world=first/'isolated-smoke-world';reopen_world=reopen/'isolated-smoke-world';a=records(source_world,create['fixtures']);b=records(reopen_world,create['fixtures']);proof=[]
    for row in create['fixtures']:
        pos=tuple(row['root']);old,new=a[pos],b[pos];expected=f"{row['registry']}[open=false,profile=base,rotation={row['rotation']},variant={row['variant']}]"
        assert old['state']==new['state']==expected,(row['role'],old['state'],new['state'],expected)
        for record in [old,new]:assert uuid_str(record['entity']['resident']['uuid'])==row['uuid'] and record['entity']['resident']['id']==row['registry'] and record['entity']['resident']['root']==row['root']
        for key in ['payload','resident','owners']:assert old['entity'][key]==new['entity'][key],(row['role'],key)
        payload=new['entity']['payload']
        if row['role']=='height_and_source_shift':assert payload['VerticalOffset']==1.25 and payload['SourceShift']==[.25,.5,-.125]
        proof.append({'role':row['role'],'root':row['root'],'state':new['state'],'uuid':row['uuid'],'payload':payload,'nativeRootOwnerContributionsPreserved':True,'status':'PASS'})
    natives=DEST/'native';history=DEST/'history';natives.mkdir(parents=True,exist_ok=True);history.mkdir(parents=True,exist_ok=True)
    mapped=[(ROOT/'build/v123-server-3.json',natives/'server-create-launcher.json'),(first/'v123-native-output.json',natives/'server-create-qa.json'),(first/'launch-console.log',natives/'server-create-console.log'),(source_world/'v123-fixture.json',natives/'server-fixture.json'),(ROOT/'build/v123-server-reopen-1.json',natives/'server-reopen-launcher.json'),(reopen/'v123-native-output.json',natives/'server-reopen-qa.json'),(reopen/'launch-console.log',natives/'server-reopen-console.log')]
    for i in [1,2,3,4]:
        build=ROOT/f'build/v123-qa-build-{i}';mapped.extend([(build/'build-report.json',history/f'qa-build-{i}.json'),(build/'build.log',history/f'qa-build-{i}.log'),(build/'resources/v123-qa-input.json',history/f'qa-build-{i}-binding.json')])
    for i in [1,2]:
        run=ROOT/f'build/runtime-server-v123-outline-{i}';mapped.extend([(ROOT/f'build/v123-server-{i}.json',history/f'server-failed-{i}-launcher.json'),(run/'v123-native-output.json',history/f'server-failed-{i}-qa.json'),(run/'launch-console.log',history/f'server-failed-{i}-console.log')])
    mapped.append((ROOT/'build/v123-qa-build-2/failed-world-cell-proof.json',history/'failed-world-cell-proof.json'))
    client=ROOT/'build/runtime-client-v123-outline-icons-1'
    mapped.extend([(client/'client-profile.json',history/'client-failed-1-launcher.json'),(client/'v123-client-output.json',history/'client-failed-1-qa.json'),(client/'launch-console.log',history/'client-failed-1-console.log')])
    for png in sorted((client/'screenshots').glob('*.png')):mapped.append((png,history/('client-failed-1-'+png.name)))
    copied=[]
    for src,dst in mapped:
        assert src.is_file() and not src.is_symlink() and not dst.exists(),str(dst);shutil.copy2(src,dst);assert sha(src)==sha(dst);copied.append({'source':str(src),'file':dst.relative_to(DEST).as_posix(),'bytes':dst.stat().st_size,'sha256':sha(dst)})
    comparison={'schema':'dreamwalker-v123-saved-state-nbt-anvil-proof-v1','status':'PASS','scope':'Read only original saved fixture and actually reopened/resaved fixture; exact expected blockstate properties, resident UUID, typed payload and owner geometry','productionJarSha256':create['productionJarSha256'],'roots':proof}
    (natives/'saved-state-nbt-comparison.json').write_text(json.dumps(comparison,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    text='''Two initial isolated QA runs failed an aiming witness. All exhaustive geometry checks had completed. The QA reused one fake player for many poses within a single server tick and compared a hardcoded +Z world ray with the player targeting ray. The actual player look vector retained a movement/head yaw offset (e.g. -0.10230976, 0, 0.99474275); therefore the two rays differed. The native hit cell was the correct owned composite cell and had the correct simplified shape.

QA correction only: a fresh player for each fixture, a separate player for pass-through movement, fixed head/body/previous angles and an explicit +Z direction assertion. World ray and owner targeting now use the same actual player direction. No production change, failed case removal, or weakened geometry/target assertion was needed.

The corrected third run passed 1045 new roots, 1802 variants, 1803 distinct poses, 28848 state/profile/rotation combinations plus 1046 offset combinations, 334234 per-cell checks and 21 ordinary placement/raycast/pick/break/copy fixtures. A separate server reopened the saved world successfully; an independent Anvil read verifies exact saved states, typed NBT, UUIDs and root contributions for all 21 fixtures. Raw FAIL and PASS artifacts are preserved in this folder.

The first native client run captured both GUI icon sheets (all 69 source bone snapshots unchanged), and two world outline frames. It then failed a QA owner assertion for chain 90086: the crosshair hit an owned helper cell, but the helper asked VerticalMount.owner(hitCell), an API that returns only the root resident. Helpers intentionally have no resident. QA correction only: use the actual production CompositeRuntime.target(world, hitCell, player) ray/owner lookup and independently check the saved root resident UUID. This does not alter production, placement, selection or geometry. The first client was shut down normally, its partial native PNGs and original FAIL evidence remain here, and the complete corrected client evidence is packaged separately by the parent task.

The QA addons are isolated test helpers and are absent from the production JAR. No manual keyboard/mouse operation or real author collision export was performed. Final native screenshots/client evidence are packaged separately by the parent task.
'''
    (history/'README.txt').write_text(text,encoding='utf8')
    (natives/'evidence-index.json').write_text(json.dumps({'status':'PASS','rawByteCopies':copied,'exactCopiedFileCount':len(copied),'savedStateNbtProof':'native/saved-state-nbt-comparison.json'},indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':'PASS','rawFiles':len(copied),'savedRoots':len(proof),'destination':str(DEST)}))
if __name__=='__main__':main()
