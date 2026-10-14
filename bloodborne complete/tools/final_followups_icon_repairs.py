"""Repair only icons proven empty by actual native GUI screenshots."""
import math,collections
import numpy as np
from final_followups_surface_audit import ROOT,RES,OUT,read,write,model,opacity,SIDES
proof=read(OUT/'icon-pixels-r4b.json');assert proof['status']=='FAIL_EMPTY_NATIVE_ICONS'
def rotation(axis,angle):
    r=math.radians(angle);c,s=math.cos(r),math.sin(r)
    return np.array({'x':[[1,0,0],[0,c,-s],[0,s,c]],'y':[[c,0,s],[0,1,0],[-s,0,c]],'z':[[c,-s,0],[s,c,0],[0,0,1]]}[axis])
rows=[]
for failed in proof['emptyItems']:
    path=failed['item'].split(':')[-1];spec=read(RES/'bloodborne_dw/composite'/(path+'.json'));state=failed['state'];variant=int(state.get('variant',0))+16*int(state.get('art_bank',0));pose=spec['variants'][variant]['poses']['open' if state.get('open')=='true' else 'closed'];best=None
    for part in pose['parts']:
        matrix=rotation('y',-part.get('yaw',0))@rotation('x',-part.get('pitch',0))@rotation('y',part.get('extraYaw',0));d=model(part['model'])
        for e in d.get('elements',[]):
            m=matrix
            if e.get('rotation'):m=m@rotation(e['rotation']['axis'],e['rotation']['angle'])
            for side,face in e.get('faces',{}).items():
                opaque,visible=opacity(d,face)
                if not visible:continue
                axis,sign=SIDES[side];other=[k for k in range(3) if k!=axis];area=(e['to'][other[0]]-e['from'][other[0]])*(e['to'][other[1]]-e['from'][other[1]])
                if area<=0:continue
                n=np.zeros(3);n[axis]=sign;n=m@n
                if best is None or area>best[0]:best=(area,n,part['model'],side)
    assert best is not None,failed
    _,n,name,side=best;yaw=math.degrees(math.atan2(-n[0],n[2]));pitch=math.degrees(math.atan2(n[1],math.hypot(n[0],n[2])));rot=[round(pitch,6),round(yaw,6),0]
    item=RES/'assets/bloodborne_dw/models/item'/(path+'.json');doc=read(item);before=doc.get('display',{})
    display=doc.setdefault('display',{})
    for mode in ('gui','firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand'):display[mode]={'rotation':rot,'translation':[0,0,0],'scale':[1,1,1]}
    write(item,doc);rows.append({'id':failed['id'],'item':failed['item'],'normal':n.tolist(),'sourceModel':name,'sourceFace':side,'rotation':rot,'previousDisplay':before})
write(OUT/'native-proven-icon-repairs.json',{'status':'APPLIED_REQUIRES_NATIVE_RECHECK','nativeFailingItems':len(rows),'rows':rows,'worldGeometryEdited':False,'pngPixelsEdited':False,'method':'Rotate the largest actually authored drawable face, after source element/part transformations, to the native GUI camera. Only items with fewer than9 real icon pixels are edited.'})
