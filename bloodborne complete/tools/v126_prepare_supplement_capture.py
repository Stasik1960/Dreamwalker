"""Wider tall-object, reverse open-door and farther city views; no edits."""
import argparse,copy,json,math,re
from pathlib import Path
def read(p):return json.loads(p.read_text(encoding='utf8'))
def main():
    p=argparse.ArgumentParser();p.add_argument('--manifest',type=Path,required=True)
    p.add_argument('--previous',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    m=read(a.manifest);old=read(a.previous);frames=[];previous={r['key']:r for r in old['frames']}
    def emit(original,eye,centre,name,tag=''):
        row=copy.deepcopy(original);key='SUPPLEMENT-'+(tag+'-' if tag else '')+original['key'];delta=[centre[i]-eye[i] for i in range(3)]
        camera=[*eye,math.degrees(math.atan2(-delta[0],delta[2])),-math.degrees(math.atan2(delta[1],math.hypot(delta[0],delta[2])))]
        camera[1]-=1.62;row.update(key=key,name=name,camera=camera,command='tp @s '+' '.join(map(str,camera)),waitTicks=120)
        row['fileName']=f'v126s-{len(frames)+1:03d}-'+re.sub('[^a-zA-Z0-9_-]','_',original['key'])[:64]+'.png'
        row['cameraPlanningEvidence']={'policy':'Supplemental native framing; reverse side for open leaves / wider tall object / city context. Original frame preserved.'}
        frames.append(row)
    for row in m['exhibits']:
        if row['key']!=old['frames'][5]['key']:continue
        assert row['id']=='93151'
        # The authored plane has only its WEST-facing source surface. The
        # first camera saw its culled EAST side; the neighbouring arc was
        # unrelated. Inspect the actual thin detail from its source front.
        emit(previous[row['key']],[102.4375,65.62,188.5],[106.4375,65,191.5],'93151 · лицевая сторона тонкой детали')
    for g in m['reviewPlatforms']:
        if g['section']!='doors' or not g['open']:continue
        b=g['bounds'];c=[(b[i]+b[i+3])/2 for i in range(3)];d=max(7,max(b[i+3]-b[i] for i in range(3))*1.5+3)
        emit(previous[g['key']],[c[0]-d*.55,c[1]+d*.25,c[2]+d*.85],c,g['primaryId']+' · открытые створки с обратной стороны')
        if g['primaryId'] in {'90031','90266','93152','93153'}:
            emit(previous[g['key']],[c[0]+d*.75,c[1]+d*.3,c[2]-d*.65],c,g['primaryId']+' · открытые створки с правого бока','RIGHT-SIDE')
    city=m['city'];b=city['bounds'];c=[(b[i]+b[i+3])/2 for i in range(3)]
    for n,azimuth in enumerate((0,90,180,270)):
        angle=math.radians(azimuth);eye=[c[0]+math.sin(angle)*52,b[4]+8,c[2]-math.cos(angle)*52]
        emit(previous[f'CITY-OVERVIEW-{n+1}'],eye,c,'Город · полный дальний обзор '+str(n+1))
    # Exterior street-level context avoids the earlier brick-wall close-up
    # camera that entered neighbouring source geometry. Target identities
    # remain the same; this view intentionally assesses the city context.
    for n in (1,2):
        original=previous[f'CITY-DETAIL-{n}'];at=original['rootChecks'][0]['root']
        centre=[at[0]+.5,at[1]+.8,at[2]+.5]
        eye=[b[0]-10,at[1]+7,at[2]-(8 if n==1 else -8)]
        emit(original,eye,centre,'Город · наружный фасад у проверяемой кладки '+str(n))
    result={k:v for k,v in old.items() if k!='frames'};result.update(frames=frames,scope='Supplemental framing only; reverse OPEN doors, full93154, city render distance10. No world blocks changed.')
    assert len(frames)==19 and result['quitAfterCapture'];a.output.parent.mkdir(parents=True,exist_ok=True)
    a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print(json.dumps({'status':'PASS','frames':len(frames)}))
if __name__=='__main__':main()
