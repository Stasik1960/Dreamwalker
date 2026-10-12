"""Copy only a completed derived QA world; omit its named test player's saves."""
import argparse,hashlib,json,shutil,uuid
from pathlib import Path
from package_v124_release import ordinary_project_path,project_files,sha
from world_io import read_nbt,write_nbt,compound,Tag
ROOT=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--report',type=Path,required=True);a=p.parse_args()
    source=a.source.resolve();output=a.output.resolve();assert source.is_relative_to(ROOT/'build') and output.is_relative_to(ROOT/'build') and not output.exists()
    assert (source/'level.dat').is_file() and (source/'gallery-manifest.json').is_file()
    value=bytearray(hashlib.md5(b'OfflinePlayer:DreamwalkerQA').digest());value[6]=(value[6]&15)|48;value[8]=(value[8]&63)|128;identity=uuid.UUID(bytes=bytes(value))
    records=[];omitted=[];output.mkdir(parents=True)
    for file in sorted(project_files(source)):
        relative=file.relative_to(source)
        if file.name=='session.lock' or (relative.parts[0] in ('playerdata','stats','advancements') and file.name.startswith(str(identity))):omitted.append(str(relative));continue
        target=output/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(file,target);assert sha(target)==sha(file);records.append({'path':str(relative),'sha256':sha(file)})
    level=read_nbt(output/'level.dat');data=compound(compound(level.root)['Data']);player=data.get('Player')
    if player is not None:
        raw=compound(player);integer_uuid=raw['UUID'].value
        number=0
        for v in integer_uuid:number=(number<<32)|(int(v)&0xffffffff)
        assert uuid.UUID(int=number)==identity,'Only the known generated offline test player may be reset'
        del data['Player']
    data['GameType']=Tag(3,1);data['SpawnX']=Tag(3,0);data['SpawnY']=Tag(3,64);data['SpawnZ']=Tag(3,-6);data['SpawnAngle']=Tag(5,0)
    write_nbt(output/'level.dat',level)
    assert all(sha(source/r['path'])==r['sha256'] for r in records),'Completed native evidence world changed'
    result={'status':'PASS','schema':'dreamwalker-v125-delivery-world-copy','source':str(source),'output':str(output),'copiedFiles':len(records),'omittedKnownQaPlayerFiles':omitted,'knownQaIdentity':str(identity),'clearLevelPlayer':player is not None,'productionObjectDataEdited':False,'sourceWorldUnchanged':True}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print(json.dumps(result))
if __name__=='__main__':main()
