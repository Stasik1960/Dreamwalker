"""A bounded native tour of the newly authored gallery and city sample."""
import argparse, hashlib, json, math
from pathlib import Path
from prepare_flat_gallery_capture import camera
from prepare_gallery_v124_capture import unobstructed_camera
p=argparse.ArgumentParser();p.add_argument('--world',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--worldedit-qa',action='store_true');p.add_argument('--warm-seconds',type=int,default=20);a=p.parse_args()
m=json.loads((a.world/'gallery-manifest.json').read_text(encoding='utf8'))
assert m['schema']=='dreamwalker-flat-gallery-v127' and 5<=a.warm_seconds<=30
rows=m['exhibits'];groups=m['reviewPlatforms'];city=m['city'];frames=[]
obstacles=rows+groups+[city]
def checks(row):
    return {'root':row['root'],'registry':row['registry'],'uuid':row['uuid'],**row['placedState']}
def add(row,roots=None,entities=None,view=None):
    if view is None:
        try:view,_=unobstructed_camera({**row,'root':row.get('root',[0,64,0]),'kind':row.get('kind','architecture')},obstacles)
        except AssertionError:view=camera(row['bounds'])
    frames.append({'key':row['key'],'fileName':f'v127-new-gallery-{len(frames)+1:02d}.png',
                   'camera':view,'rootChecks':roots or [],'entityChecks':entities or [],'section':row['section']})
for number in ('90002','90083','90586','93232','93233','93230','93231'):
    row=next(r for r in rows if r['id']==number)
    add(row,[checks(row)] if row['kind']=='architecture' else [])
rp=next(r for r in rows if r['registry']=='bloodborne_rp:wood_gate')
add(rp,entities=[{'uuid':rp['uuid'],'registry':rp['registry']}])
door=next(r for r in groups if r['section']=='doors' and not r['open'])
add(door,[checks(c) for c in door['components']])
b=city['bounds'];center=[(b[i]+b[i+3])/2 for i in range(3)];eye=[center[0],b[4]+8,center[2]-52];delta=[center[i]-eye[i] for i in range(3)]
view=[eye[0],eye[1]-1.62,eye[2],math.degrees(math.atan2(-delta[0],delta[2])),-math.degrees(math.atan2(delta[1],math.hypot(delta[0],delta[2])))]
add({**city,'key':'CITY-OVERVIEW'},[checks(r) for r in city['placedOwners'][:10]],view=view)
detail=next(r for r in city['placedOwners'] if 'APPEND' in r.get('resolution',''))
add({**detail,'section':'city','key':'CITY-DETAIL'},[checks(detail)],view=camera(detail['bounds']))
marker={'productionJarSha256':m['productionJarSha256'],'v127GalleryBenchmark':True,'newGalleryOnly':True,
        'gallerySourceZipSha256':'NOT_PACKAGED_YET_NEW_GALLERY','galleryManifestSha256':hashlib.sha256((a.world/'gallery-manifest.json').read_bytes()).hexdigest(),
        'galleryCreated':True,'worldEditQa':a.worldedit_qa,'warmSecondsPerStation':a.warm_seconds,'frames':frames}
a.output.parent.mkdir(parents=True,exist_ok=True);assert not a.output.exists();a.output.write_text(json.dumps(marker,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps({'status':'PASS_PREPARED_NEW_GALLERY_NATIVE_TOUR','frames':len(frames),'warmSeconds':a.warm_seconds,'actualWorldEditCommands':a.worldedit_qa}))
