"""Native framebuffer capture: new shapes, every assembled/door preview, city."""
import argparse,hashlib,json,math,re
from pathlib import Path
from prepare_gallery_v124_capture import unobstructed_camera,STATE_KEYS
from prepare_flat_gallery_capture import camera
def main():
    p=argparse.ArgumentParser();p.add_argument('--manifest',type=Path,required=True);p.add_argument('--production-jar',type=Path,required=True);p.add_argument('--disk-report',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    m=json.loads(a.manifest.read_text(encoding='utf8'));d=json.loads(a.disk_report.read_text(encoding='utf8'));sha=hashlib.sha256(a.production_jar.read_bytes()).hexdigest()
    assert m['schema']=='dreamwalker-flat-gallery-v125' and d['status']=='PASS' and m['productionJarSha256']==d['productionJarSha256']==sha
    rows=m['exhibits'];groups=m['reviewPlatforms'];city=m['city'];chosen=[];used=set();frames=[]
    def add(row):
        if row['key'] not in used:chosen.append(row);used.add(row['key'])
    for section in ('v12','new','absent','items'):
        r=[x for x in rows if x['section']==section];add(r[0]);add(r[-1])
    mandatory=('90002','90001','93116','93117','93066','93069','90049','90083','90086','90151','93266','90595')
    for row in rows:
        if row.get('id') in mandatory or any(token in row['registry'] for token in ('halfarch','axed_exposed_cut_copper_stairs_outer_3','axed_oxidized_cut_copper_stairs_outer_right','addon_floor_panels_45','hold_ladder_02')):add(row)
    for row in sorted([r for r in rows if r['kind']=='architecture' and r['origin']!='V12'],key=lambda r:max(r['bounds'][i+3]-r['bounds'][i] for i in range(3)),reverse=True):
        if len(chosen)>=26:break
        add(row)
    obstacles=rows+[{'key':g['key'],'bounds':g['bounds']} for g in groups]+[{'key':'CITY-SOURCE-CLIP','bounds':city['bounds']}]
    def frame(row,checks,entities,view=None):
        index=len(frames)+1;key=row['key'];name=row.get('name',row.get('russianLabel','Фрагмент города'));evidence={}
        if view is None:
            try:view,evidence=unobstructed_camera({**row,'root':row.get('root',[0,64,0]),'kind':row.get('kind','architecture')},obstacles)
            except AssertionError:
                # Close displays may be surrounded by tall neighbours. A high
                # oblique camera shows the whole target without moving objects.
                b=row['bounds'];c=[(b[i]+b[i+3])/2 for i in range(3)];span=max(b[i+3]-b[i] for i in range(3));distance=max(5,span*1.25)
                eye=[c[0]+distance*.35,b[4]+distance*.85,c[2]-distance*.45];delta=[c[i]-eye[i] for i in range(3)];view=[eye[0],eye[1]-1.62,eye[2],math.degrees(math.atan2(-delta[0],delta[2])),-math.degrees(math.atan2(delta[1],math.hypot(delta[0],delta[2])))];evidence={'policy':'High oblique view; native raw pixels require visual review'}
        frames.append({'fileName':f'v125-{index:03d}-'+re.sub('[^a-zA-Z0-9_-]','_',key)[:64]+'.png','key':key,'section':row['section'],'name':name,'camera':view,'command':'tp @s '+' '.join(map(str,view)),'waitTicks':90,'rootChecks':checks,'entityChecks':entities,'cameraPlanningEvidence':evidence})
    def root(r):return {'root':r['root'],'registry':r['registry'],'uuid':r['uuid'],**{k:r['placedState'][k] for k in STATE_KEYS if k in r['placedState']}}
    for row in chosen:
        frame(row,[root(row)] if row['kind']=='architecture' else [],[] if row['kind']=='architecture' else [{'registry':'minecraft:item_frame' if row['kind']=='item_frame' else row['registry'],'uuid':row['uuid']}])
    for group in groups:frame(group,[root(c) for c in group['components']],[])
    b=city['bounds'];c=[(b[i]+b[i+3])/2 for i in range(3)]
    for n,azimuth in enumerate((0,90,180,270)):
        angle=math.radians(azimuth);eye=[c[0]+math.sin(angle)*52,b[4]+8,c[2]-math.cos(angle)*52];delta=[c[i]-eye[i] for i in range(3)];view=[eye[0],eye[1]-1.62,eye[2],math.degrees(math.atan2(-delta[0],delta[2])),-math.degrees(math.atan2(delta[1],math.hypot(delta[0],delta[2])))];sample=city['placedOwners'][n*10:n*10+10];frame({'key':f'CITY-OVERVIEW-{n+1}','section':'city','name':'Город · обзор '+str(n+1)},[root(r) for r in sample],[],view)
    # Close city detail checks cover appended conditional poses and native V12
    # walls, retaining source coordinates and the actual current UUID/state.
    additions=[r for r in city['placedOwners'] if 'APPEND' in r.get('resolution','')]
    for n,r in enumerate((additions[:2]+[r for r in city['placedOwners'] if r['registry']=='bloodborne_dw:prototype_wall'][:1])):
        frame({**r,'key':'CITY-DETAIL-'+str(n+1),'section':'city','name':'Город · деталь '+r.get('mainId','')},[root(r)],[],camera(r['bounds']))
    result={'schema':'dreamwalker-flat-gallery-v125-capture-input','gallerySchema':m['schema'],'productionJarSha256':sha,'newWorkingTypes':d['functionalCatalogue']['newWorkingTypes'],'quitAfterCapture':True,'frames':frames,'scope':'Native ScreenshotRecorder images; no block edits. All45 source construction/door review platforms, representative unchangedV12/new simplified shapes, city views and live typed root identities.'}
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n');print(json.dumps({'status':'PASS','frames':len(frames),'main':len(chosen),'review':len(groups)}))
if __name__=='__main__':main()
