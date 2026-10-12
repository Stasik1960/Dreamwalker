"""Observe the authored front of the three single OPEN leaves and narrow door."""
import argparse,copy,json,math
from pathlib import Path
def read(p):return json.loads(p.read_text(encoding='utf8'))
def main():
    p=argparse.ArgumentParser();p.add_argument('--manifest',type=Path,required=True);p.add_argument('--previous',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    m=read(a.manifest);old=read(a.previous);lookup={f['key']:f for f in old['frames']};frames=[]
    for row in m['reviewPlatforms']:
        if not (row['primaryId'] in {'90458','90923'} and row['open'] or row['primaryId']=='93160' and not row['open']):continue
        b=row['bounds'];c=[(b[i]+b[i+3])/2 for i in range(3)]
        if row['open']:
            # The tall90923 header is static. Frame the moving lower leaf,
            # rather than centring high on the complete arch's bounding box.
            c[1]=b[1]+1.5
        # Single leaves rotate their authored NORTH surface to EAST at90°.
        # The narrow closed93160 already faces EAST/WEST along the X axis.
        eye=[c[0]+4,c[1]+.35,c[2]-.25];delta=[c[i]-eye[i] for i in range(3)]
        camera=[eye[0],eye[1]-1.62,eye[2],math.degrees(math.atan2(-delta[0],delta[2])),-math.degrees(math.atan2(delta[1],math.hypot(delta[0],delta[2])))]
        f=copy.deepcopy(lookup[row['key']]);f.update(key='SOURCE-FACE-'+row['key'],camera=camera,command='tp @s '+' '.join(map(str,camera)),waitTicks=100,
            name=row['primaryId']+' · лицевая поверхность створки',fileName=f'v126f-{len(frames)+1:03d}-{row["primaryId"]}-pose{row["reviewPoseIndex"]}.png')
        f['cameraPlanningEvidence']={'policy':'Authored EAST source face; source-sidedness preserved, no back faces invented.'};frames.append(f)
    assert len(frames)==4;result={k:v for k,v in old.items() if k!='frames'};result.update(frames=frames,scope='Four native source-face views only; no world blocks edited.')
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print(json.dumps({'status':'PASS','frames':len(frames)}))
if __name__=='__main__':main()
