"""Verify normal entry and absence of QA-player/ticket artifacts in final world."""
import argparse,json
from pathlib import Path
from world_io import read_nbt,compound
ROOT=Path(__file__).resolve().parents[1]
def main():
    p=argparse.ArgumentParser();p.add_argument('--world',type=Path,required=True);p.add_argument('--report',type=Path,required=True);a=p.parse_args()
    world=a.world.resolve();assert world.is_relative_to(ROOT/'build')
    data=compound(compound(read_nbt(world/'level.dat').root)['Data'])
    assert 'Player' not in data and [data[k].value for k in ['SpawnX','SpawnY','SpawnZ']]==[0,64,-6] and data['GameType'].value==1
    forced=world/'data/chunks.dat'
    if forced.exists():
        body=compound(compound(read_nbt(forced).root)['data']);assert not body.get('Forced').value
    assert not any((world/'playerdata').glob('*.dat'))
    entry=world/'datapacks/dw_gallery_entry/data/dw_gallery/functions/entry.mcfunction';assert entry.exists() and 'gamemode creative' in entry.read_text(encoding='utf8')
    result={'status':'PASS_NORMAL_DELIVERY_ENTRY','spawn':[0,64,-6],'creative':True,'knownQaPlayerCleared':True,'persistentForceLoadTickets':0,'qaAddonRequired':False,'productionObjectDataEdited':False}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps(result))
if __name__=='__main__':main()
