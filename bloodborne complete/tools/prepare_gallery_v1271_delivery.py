"""Copy the newly tested gallery into a clean local delivery save.

Strip only QA player saves. Mod block/entity data is copied byte-for-byte.
The resulting delivery copy requires a production-only reopen and disk proof.
"""
import argparse, json, shutil
from pathlib import Path
from run_v11_server import files_without_links,ordinary,sha
from world_io import read_nbt,write_nbt,compound,Tag

ROOT=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--world',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    source=a.world.absolute();target=a.output.absolute();build=(ROOT/'build').absolute()
    assert source.is_relative_to(build) and target.is_relative_to(build) and not target.exists()
    ordinary(source)
    for parent in (target,*target.parents):
        if parent.exists():ordinary(parent)
    m=json.loads((source/'gallery-manifest.json').read_text(encoding='utf-8-sig'))
    assert m['schema']=='dreamwalker-flat-gallery-v1271' and m['status']=='PASS_AUTHORED_REQUIRES_PRODUCTION_REOPEN'
    target.mkdir(parents=True);copied=[]
    for path in files_without_links(source):
        relative=path.relative_to(source)
        if relative.parts[0] in {'playerdata','stats','advancements'}:continue
        destination=target/relative;destination.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,destination)
        digest=sha(path);assert digest==sha(destination);copied.append((relative,digest))
    level=read_nbt(target/'level.dat');data=compound(compound(level.root)['Data'])
    data.pop('Player',None);data['GameType']=Tag(3,1);data['allowCommands']=Tag(1,1)
    data['SpawnX']=Tag(3,0);data['SpawnY']=Tag(3,64);data['SpawnZ']=Tag(3,-6)
    data['LevelName']=Tag(8,'Dreamwalker BB v12.7.1 — галерея и город');write_nbt(target/'level.dat',level)
    assert all(sha(source/relative)==digest for relative,digest in copied), 'Tested source world changed during delivery copy'
    result={'status':'PASS_DELIVERY_COPY_PREPARED_REQUIRES_PRODUCTION_REOPEN','source':str(source),'world':str(target),'productionJarSha256':m['productionJarSha256'],'sourceUnchanged':True,'qaPlayersOmitted':True,'embeddedPlayerRemoved':True,'modObjectsModified':False,'copiedFiles':len(copied)}
    (target.parent/'delivery-preparation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

if __name__=='__main__':main()
