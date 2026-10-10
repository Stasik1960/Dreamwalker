"""Prepare a SHA-bound V12 native acceptance plan. Never launch or send game inputs."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import uuid
import zipfile
from run_v11_server import ROOT, ordinary, require


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--gallery-manifest',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--port',type=int,default=55796)
    args=parser.parse_args()
    for p in (args.jar,args.gallery_manifest):ordinary(p)
    jar=args.jar.resolve();manifest_path=args.gallery_manifest.resolve();output=args.output.resolve()
    require(output.is_relative_to((ROOT/'build').resolve()) and not output.exists(),'Fresh project-build output required')
    require(1<=args.port<=65535,'Invalid localhost port')
    bound=hashlib.sha256(jar.read_bytes()).hexdigest()
    with zipfile.ZipFile(jar) as archive:metadata=json.loads(archive.read('fabric.mod.json'))
    require(metadata['version'].lower().endswith('-v12'),'Prepare only against a built V12 JAR')
    manifest=json.loads(manifest_path.read_text(encoding='utf-8-sig'))
    require(manifest.get('revision')=='V12' and manifest['productionJarSha256']==bound,'Wrong revision or production SHA')
    rows=manifest['exhibits'];tool=[r for r in rows if r['item']=='bloodborne_dw:bb_tool']
    require(len(tool)==1 and tool[0]['representation']=='item_frame','Missing unique framed V12 tool')
    require(manifest.get('productionReopen',{}).get('verifiedExhibits')==len(rows),'Native gallery reopen evidence required')
    doc=(ROOT/'docs/V12_NATIVE_ACCEPTANCE.md').read_text(encoding='utf-8-sig')
    cases=[]
    for line in doc.splitlines():
        if not re.match(r'^\| [A-Z]\d+ ',line):continue
        name,action,assertion=[part.strip() for part in line.split('|')[1:-1]]
        cases.append({'case':name.split(' ',1)[0],'name':name,'nativeAction':action,'assert':assertion,'status':'PENDING'})
    require(len(cases)==22,'Acceptance checklist case count changed; review plan before preparing')
    # Fresh derived QA zone only: each air batch is <=47*41*16=30832 blocks.
    commands=['fill -48 '+str(y)+' -6 -2 '+str(min(y+15,128))+' 34 minecraft:air'
              for y in range(64,129,16)]
    commands += ['fill -48 63 -6 -2 63 34 minecraft:smooth_stone',
                 'scoreboard objectives add dw_v12_personal dummy','scoreboard objectives add dw_v12_global dummy']
    actors=[]
    specification=[('door','bloodborne_rp:door_1',[-8,64,8])]
    specification += [('lever'+str(n),'bloodborne_rp:lever_1',[-44+4*n,64,8]) for n in range(1,8)]
    specification += [('lamp_a','bloodborne_rp:hunterlamp',[-36,64,24]),('lamp_b','bloodborne_rp:hunterlamp',[-8,64,24]),
                      ('dog','bloodborne_rp:cage_obj_1',[-46,64,28])]
    for role,registry,pos in specification:
        instance=uuid.uuid5(uuid.NAMESPACE_URL,'dreamwalker-v12-native:'+bound+':'+role)
        words=[(instance.int>>(96-32*n))&0xffffffff for n in range(4)]
        words=[n if n<0x80000000 else n-0x100000000 for n in words]
        nbt='{UUID:[I;'+','.join(map(str,words))+'],NoGravity:1b}'
        commands.append('summon '+registry+' '+' '.join(map(str,pos))+' '+nbt)
        actors.append({'role':role,'registry':registry,'uuid':str(instance),'position':pos,
                       'readOnlyQueries':['data get entity '+str(instance)+' Pos','data get entity '+str(instance)+' Open']})
    give=['give @s bloodborne_dw:bb_tool','give @s bloodborne_dw:prototype_roof 3','give @s bloodborne_dw:prototype_ladder 2']
    value={'schema':'dreamwalker-v12-native-acceptance-plan-v1','status':'PREPARED_NOT_RUN',
           'productionJarSha256':bound,'modVersion':metadata['version'],
           'galleryManifestSha256':hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
           'galleryExhibits':len(rows),'cases':cases,'localhostPort':args.port,
           'freshDerivedWorldOnlySetupCommands':commands,'fixtureActors':actors,
           'zonePreparation':{'clearBounds':[-48,64,-6,-2,128,34],'airBatches':5,'maxBlocksPerAirBatch':30832,
                              'floorY':63,'standingY':64,'scope':'Fresh derived QA world only; original gallery and published snapshots unchanged'},
           'playerExecutedInventoryCommands':give,
           'ordinaryPlayerPlacements':[{'registry':'bloodborne_dw:prototype_roof','roots':[[-44,64,-3],[-40,64,-3],[-28,64,-3]],
                                      'assert':'/bb select radius6 affects first2 sameID, leaves third outside radius and unrelated IDs unchanged'},
                                     {'registry':'bloodborne_dw:prototype_ladder','roots':[[-18,64,-3],[-12,64,-3]],
                                      'assert':'Ordinary cardinal and45-degree item placement; one outline AABB, target/break works, diagonal physical remains empty'}],
           'neutralGuiCommandLines':{'personal':['scoreboard players add @s dw_v12_personal 1'],
                                    'global':['scoreboard players add #global dw_v12_global 1']},
           'nativeFrameBudget':'10..20 purposeful images: mode/GUI/chat, dog, ALL incomplete/open/reset, pair, limits, rejoin; no bulk old PNG replay',
           'serverReadOnlyAssertions':['data get entity <UUID> Pos/Open/Locked/DogsVisible',
                                      'scoreboard players get <player> dw_v12_personal','scoreboard players get #global dw_v12_global'],
           'existingJavaAssertions':['RpObjectEntity.isOpen/animationBusy/dogsVisible/verticalOffset',
                                    'MechanismRules.list/rule/flag','MechanismLinks.targets/sources/inspect',
                                    'ObjectPolicies.leversOnly','LampState saved NBT',
                                    'BbToolItem.mode; BbToolService.move/special/alternate/clear/save; ToolCommands.view/writeNbt'],
           'commandDeletionScope':'Remove whole link component; only selected/deleted object loses its commands and durable counters. Other members keep their own command bindings.',
           'newApiContract':'Server GameTests measure real callbacks/packets and scoreboard effects; native GUI/tool inputs and whole-server rejoin remain separate acceptance.',
           'rejoin':'Native save-all flush; normal close server/clients; fresh byte-verified saved-world copy and new client run; compare UUID/positions/group/pair/commands/limits/counters.',
           'clientPreparation':'Existing run_v11_client --server127.0.0.1 --port --username distinctA/B --qa-keybindings; prepare only, root controls --launch.',
           'inputOwnership':'Root performs real clicks/keys and live commands. No automated clicks, mocked player proof or background Sky activation.',
           'setupCommandsSent':False,'GUI_launched':False}
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_bytes((json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf8'))
    print(json.dumps({'status':value['status'],'cases':len(cases),'output':str(output)}),flush=True)


if __name__=='__main__':main()
