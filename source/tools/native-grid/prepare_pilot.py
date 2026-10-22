"""Two small matched pilot copies from blocks.2, no migration or gameplay."""
import argparse,importlib.util,json,sys,hashlib,uuid
from pathlib import Path
P=Path(__file__).resolve().parents[2];O=P.parent/'research/pilot';O.mkdir(parents=True,exist_ok=True)
spec=importlib.util.spec_from_file_location('native_grid_migration',P/'tools/native-grid-city/migrate.py');migration=importlib.util.module_from_spec(spec);spec.loader.exec_module(migration)
for variant in ['baseline','candidate']:
    target=O/variant
    if not (O/(variant+'-copy.json')).is_file():
        migration.clone(argparse.Namespace(plan=migration.DEFAULT_PLAN,source_plan=migration.DEFAULT_OLD,target=target,bounds=[912,1007,64,159],context_radius=7,extra_chunks=P.parent/'research/function-qa/satellites.json'))
    marker=O/(variant+'-spawn.json')
    if not marker.exists():
        path=target/'level.dat';before=migration.sha(path);nbt=migration.read_nbt(path);data=migration.compound(migration.compound(nbt.root)['Data'])
        for axis,value in zip('XYZ',[980,100,95]):data['Spawn'+axis]=migration.Tag(3,value)
        migration.write_nbt(path,nbt,compressed='gzip')
        ident=uuid.UUID(bytes=hashlib.md5(b'OfflinePlayer:DreamwalkerQA').digest(),version=3)
        player=target/'playerdata'/(str(ident)+'.dat')
        if player.is_file():
            record=migration.read_nbt(player);fields=migration.compound(record.root)
            fields['Pos']=migration.Tag(9,[migration.Tag(6,n) for n in [980.0,100.0,95.0]],6)
            fields['Rotation']=migration.Tag(9,[migration.Tag(5,n) for n in [44.36,35.0]],5)
            migration.write_nbt(player,record,compressed='gzip')
        marker.write_text(json.dumps({'status':'PILOT_SPAWN_ONLY','levelBefore':before,'levelAfter':migration.sha(path),'spawn':[980,100,95],'originalMapUnchanged':True}),encoding='utf-8')
views=json.loads((P.parent/'research/final-route/route.json').read_text(encoding='utf-8'))
route={'schemaVersion':1,'cameras':views['cameras'][:3],'names':['brick-wide','brick-cornice','brick-border'],'probes':views['probes'][:3]}
(O/'route.json').write_text(json.dumps(route,indent=2),encoding='utf-8')
(O/'status.json').write_text(json.dumps({'status':'SOURCE_COPIES_ONLY','sourceMapUnchanged':True,'candidateUnmigrated':True,'playableNativeCandidate':False,'required':'compose->catalog->apply+cleanup->native relight->functional QA/performance pair'},indent=2),encoding='utf-8')
