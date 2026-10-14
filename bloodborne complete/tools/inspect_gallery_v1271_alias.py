"""Read a single saved resident and its original manifest row; no world writes."""
import argparse, json
from pathlib import Path
from world_io import RegionFile, compound
from verify_flat_gallery import plain
from verify_v1271_final_readiness import ROOT, ordinary, read, write

def main():
    p=argparse.ArgumentParser();p.add_argument('--world',type=Path,required=True);p.add_argument('--manifest',type=Path,required=True);p.add_argument('--uuid',required=True);p.add_argument('--report',type=Path,required=True);a=p.parse_args()
    m=read(ordinary(a.manifest));row=next(v for v in m['city']['placedOwners'] if v['uuid']==a.uuid)
    x,y,z=row['root'];world=ordinary(a.world);assert world.is_relative_to(ROOT/'build')
    for chunk in RegionFile.open(world/'region'/f'r.{x>>9}.{z>>9}.mca').chunks():
        data=compound(chunk.nbt().root)
        if data['xPos'].value!=x>>4 or data['zPos'].value!=z>>4:continue
        for tag in data['block_entities'].value:
            raw=compound(tag)
            if [raw[v].value for v in ('x','y','z')]==row['root']:
                write(ordinary(a.report),{'row':row,'nbt':plain(tag)});return
    raise AssertionError('Saved resident missing')

if __name__=='__main__':main()
