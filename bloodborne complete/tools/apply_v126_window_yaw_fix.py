"""Correct only the newly composed 90797 middle's relative source orientation."""
import copy,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def read(p):return json.loads(p.read_text(encoding='utf8'))
def main():
    manifest=read(ROOT/'build/v126-assembly-plan/assembly-manifest.json');group=next(g for g in manifest['assemblies'] if g['primaryId']=='90797')
    stage=read(ROOT/group['stagedDescriptor']);p=ROOT/'src/architecture/resources/bloodborne_dw/composite/source_pressure_plate_window_bottom_4.json';current=read(p);before=copy.deepcopy(current['variants'][:group['installationPose']])
    index=group['installationPose'];replacement=stage['variants'][index];current['variants'][index]['poses']['closed']['parts']=copy.deepcopy(replacement['poses']['closed']['parts']);current['variants'][index]['visibleBounds']=copy.deepcopy(replacement['visibleBounds'])
    assert current['variants'][:index]==before
    if 'open' in current['variants'][index]['poses']:current['variants'][index]['poses']['open']['parts']=copy.deepcopy(replacement['poses']['closed']['parts'])
    temp=p.with_suffix('.v126-yaw.tmp');temp.write_text(json.dumps(current,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n');temp.replace(p)
    print('PASS: only NEW90797 whole-pose parts/bounds changed; old fragment poses and physics recipes retained')
if __name__=='__main__':main()
