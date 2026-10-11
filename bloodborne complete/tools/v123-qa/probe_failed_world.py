"""Read-only native QA failure cell proof from an already stopped derived world."""
import argparse,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from world_io import RegionFile,compound,section_blocks,block_state_key
from verify_flat_gallery import plain
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--world',type=Path,required=True);p.add_argument('--report',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[2];world=a.world.resolve();assert world.is_relative_to((root/'build').resolve())
rows=[]
for x,y,z in [(366,128,31),(368,128,32),(366,127,31)]:
    file=world/'region'/f'r.{(x>>4)//32}.{(z>>4)//32}.mca';row={'cell':[x,y,z]}
    for chunk in RegionFile.open(file).chunks():
        data=compound(chunk.nbt().root)
        if data['xPos'].value!=x>>4 or data['zPos'].value!=z>>4:continue
        for section in data['sections'].value:
            if compound(section)['Y'].value!=y>>4:continue
            palette,indices=section_blocks(section);row['state']=block_state_key(palette[indices[(y&15)*256+(z&15)*16+(x&15)]])
        for entity in data.get('block_entities').value:
            e=compound(entity)
            if [e['x'].value,e['y'].value,e['z'].value]==[x,y,z]:row['blockEntity']=plain(entity)
    rows.append(row)
a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps({'scope':'Read only stopped QA world','world':str(world),'cells':rows},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
