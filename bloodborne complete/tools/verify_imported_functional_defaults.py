"""Functional geometry checks: player-sized passages and useful stair supports."""
import json
import math
from pathlib import Path
from simplify_imported_collisions import rotate_clip_boxes

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/collision-simplification'

def overlaps(a,b):
    return all(min(a['to'][i],b['to'][i])-max(a['from'][i],b['from'][i])>1e-7 for i in range(3))

def physical(path,yaw):
    d=json.loads((ROOT/'src/architecture/resources/bloodborne_dw/composite'/f'{path}.json').read_text(encoding='utf-8'))
    return rotate_clip_boxes(d['variants'][0]['poses']['closed']['simpleCollisionByRotation'][yaw],0)

def main():
    checks=[]
    for axis in range(3):
        for side in ('from','to'):
            for overflow in (.5-1e-10,.5,.5+1e-10):
                source={'from':[0,0,0],'to':[16,16,16]}
                source[side][axis]=-overflow*16 if side=='from' else 16+overflow*16
                result=rotate_clip_boxes([source],0)
                expected=(0 if side=='from' else 16) if overflow<.5 else source[side][axis]
                assert len(result)==1 and math.isclose(result[0][side][axis],expected,rel_tol=0,abs_tol=1e-12),(axis,side,overflow,result)
                checks.append({'check':'strict_half_block_threshold','axis':axis,'side':side,'overflow':overflow,'status':'PASS'})
    for path,center,direction in [('source_deepslate_iron_ore',[8,8],[0,1]),('source_acacia_fence_gate',[8,12],[0,1]),('source_warped_gargoyle',[8,8],[1,0])]:
        for yaw in range(8):
            angle=math.radians(yaw*45);c,s=math.cos(angle),math.sin(angle)
            cx=8+c*(center[0]-8)-s*(center[1]-8)
            cz=8+s*(center[0]-8)+c*(center[1]-8)
            # A full standing player must walk through the passage, approaching
            # from either side. Native player boxes remain axis aligned.
            for step in range(-12,13):
                distance=step*2
                x,z=cx+(c*direction[0]-s*direction[1])*distance,cz+(s*direction[0]+c*direction[1])*distance
                body={'from':[x-4.8,0,z-4.8],'to':[x+4.8,28.8,z+4.8]}
                assert not any(overlaps(body,b) for b in physical(path,yaw)),(path,yaw,step)
            checks.append({'id':'bloodborne_dw:'+path,'yaw':yaw,'check':'standing_player_passage_0.6x1.8','status':'PASS'})
    for path,positions in [('source_hold_ladder_01',[-13,-7,0,16,29]),('source_hold_wood_ladder_01',[-13,-7,0,16,29])]:
        boxes=physical(path,0);heights=[]
        for z in positions:
            supports=[b['to'][1] for b in boxes if b['from'][0]<=8<=b['to'][0] and b['from'][2]<=z<=b['to'][2]]
            assert supports,(path,z,'missing support')
            heights.append(max(supports))
        assert all(0<=n-p<=9.6 for p,n in zip([0]+heights,heights)),(path,heights)
        checks.append({'id':'bloodborne_dw:'+path,'check':'ascent_with_native_0.6_step_and_supported_landing','supportHeights':heights,'status':'PASS'})
    result={'status':'PASS_STATIC_FUNCTIONAL_GEOMETRY','checks':len(checks),'cases':checks,
            'limitations':'Geometric player sweep/support checks; actual movement and world reopening require Minecraft game evidence.'}
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/'functional-defaults-check.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

if __name__=='__main__':
    try:main()
    except Exception:
        import traceback
        OUT.mkdir(parents=True,exist_ok=True)
        (OUT/'functional-defaults-check.json').write_text(json.dumps({'status':'FAIL','traceback':traceback.format_exc()},indent=2)+'\n',encoding='utf-8')
        raise
