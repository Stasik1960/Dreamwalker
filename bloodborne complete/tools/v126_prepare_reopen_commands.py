"""Force every authored gallery/city chunk during production-only reopen."""
import argparse,json,math
from pathlib import Path
def main():
    p=argparse.ArgumentParser();p.add_argument('--manifest',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    m=json.loads(a.manifest.read_text(encoding='utf-8-sig'))
    assert m['schema']=='dreamwalker-flat-gallery-v126'
    rows=m['exhibits']+m['reviewPlatforms']+[m['city']]
    x0=min(r['platform'][0] for r in rows);z0=min(r['platform'][1] for r in rows)
    x1=max(r['platform'][0]+r['platform'][2]-1 for r in rows)
    z1=max(r['platform'][1]+r['platform'][3]-1 for r in rows)
    cx0,cx1=math.floor(x0/16),math.floor(x1/16);cz0,cz1=math.floor(z0/16),math.floor(z1/16)
    commands=[]
    # Each command stays below Minecraft's256-chunk limit. These are test
    # world's load tickets, not geometry/owner/state changes.
    for cx in range(cx0,cx1+1,16):
        for cz in range(cz0,cz1+1,8):
            commands.append(f'forceload add {cx*16} {cz*16} {(min(cx+15,cx1)+1)*16-1} {(min(cz+7,cz1)+1)*16-1}')
    commands+=['time set 6000','save-all flush']
    a.output.parent.mkdir(parents=True,exist_ok=True)
    a.output.write_text(json.dumps(commands,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':'PASS','chunks':(cx1-cx0+1)*(cz1-cz0+1),'commands':len(commands)}))
if __name__=='__main__':main()
