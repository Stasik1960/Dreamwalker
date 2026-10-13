"""Extra native views of thin assemblies, without editing any model or world."""
import argparse,copy,hashlib,itertools,json,math,re
from pathlib import Path
from prepare_gallery_v124_capture import segment_intersects_box,STATE_KEYS

def front(row,obstacles,signs=(-1,1)):
    b=row['bounds'];c=[(b[i]+b[i+3])/2 for i in range(3)]
    axis=0 if b[3]-b[0]<b[5]-b[2] else 2;other=2-axis
    targets=[[b[i]+(b[i+3]-b[i])*v[i] for i in range(3)] for v in itertools.product((.02,.98),repeat=3)]+[c]
    span=max(b[i+3]-b[i] for i in range(3));distance=max(4,span*1.15)
    for lift in (.15,.4,.8,1.3,2,3):
        for sign in signs:
            for slant in (-.18,.18):
                eye=list(c);eye[axis]+=sign*distance;eye[other]+=slant*distance;eye[1]=b[4]+max(.5,span*lift)
                blockers=[]
                for o in obstacles:
                    if o['key']==row['key']:continue
                    box=o['bounds']
                    if all(box[i]<=eye[i]<=box[i+3] for i in range(3)) or any(segment_intersects_box(eye,t,box) for t in targets):blockers.append(o['key'])
                if blockers:continue
                delta=[c[i]-eye[i] for i in range(3)]
                view=[eye[0],eye[1]-1.62,eye[2],math.degrees(math.atan2(-delta[0],delta[2])),-math.degrees(math.atan2(delta[1],math.hypot(delta[0],delta[2])))]
                return view,{'policy':'Thin face normal preferred; nine visual-envelope rays clear','normalAxis':axis,'normalSign':sign,'liftFactor':lift,'blockers':[]}
    raise AssertionError('No visible thin-face view: '+row['key'])

def main():
    p=argparse.ArgumentParser();p.add_argument('--manifest',type=Path,required=True);p.add_argument('--base-input',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--opposite-of',type=Path);a=p.parse_args()
    m=json.loads(a.manifest.read_text(encoding='utf8'));base=json.loads(a.base_input.read_text(encoding='utf8'));old={r['key']:r for r in base['frames']}
    obstacles=m['exhibits']+m['reviewPlatforms']+[m['city']];frames=[]
    opposite={r['key']:r for r in json.loads(a.opposite_of.read_text(encoding='utf8'))['frames']} if a.opposite_of else {}
    for row in m['reviewPlatforms']:
        if a.opposite_of and row['key'] not in ('2672:2673:0,1,0','929:1092:0,2,0','DOOR-929:1092:0,2,0'):continue
        b=row['bounds'];width,depth=b[3]-b[0],b[5]-b[2]
        if not width<depth*.6:continue
        signs=(-opposite[row['key']]['cameraPlanningEvidence']['normalSign'],) if a.opposite_of else (-1,1)
        view,evidence=front(row,obstacles,signs);frame=copy.deepcopy(old[row['key']]);frame.update(fileName=('v125-opposite-' if a.opposite_of else 'v125-front-')+re.sub('[^a-zA-Z0-9_-]','_',row['key'])[:64]+'.png',camera=view,command='tp @s '+' '.join(map(str,view)),cameraPlanningEvidence=evidence)
        frames.append(frame)
    # This source decoration is extremely flat; inspect both sides rather than
    # interpreting a side-on/culled initial native view as a texture defect.
    row=next(r for r in m['exhibits'] if r['id']=='93151');b=row['bounds'];c=[(b[i]+b[i+3])/2 for i in range(3)]
    for name,view in ([] if a.opposite_of else [('west',[124.75,64.18,191.5,-90,19.5731]),('west-oblique',[124.75,64.18,190.75,-71.5651,18.6404])]):
        frame=copy.deepcopy(old[row['key']]);frame.update(fileName=f'v125-flat-93151-{name}.png',camera=view,command='tp @s '+' '.join(map(str,view)),cameraPlanningEvidence={'policy':'Native close WEST views of the authored WEST-only face; exact saved plane x127,y64..66,z191.4375..191.5625. Initial EAST view was correctly culled, no production change.'})
        frames.append(frame)
    result={k:v for k,v in base.items() if k!='frames'};result.update(frames=frames,scope='Additional native opposite-side/face-normal views; no block edits')
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
    print(json.dumps({'status':'PASS','frames':len(frames)}))
if __name__=='__main__':main()
