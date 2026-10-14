"""Audit offered source models; repair only exposed opaque caps and empty icons."""
from pathlib import Path
import argparse,collections,copy,hashlib,io,json,zipfile
from PIL import Image
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/architecture/resources';OUT=ROOT/'build/final-followups-20261014'
SIDES={'west':(0,-1),'east':(0,1),'down':(1,-1),'up':(1,1),'north':(2,-1),'south':(2,1)}
OPPOSITE={'west':'east','east':'west','up':'down','down':'up','north':'south','south':'north'}
def read(p):return json.loads(p.read_text(encoding='utf8'))
def write(p,d):
    newline='\r\n' if p.is_file() and b'\r\n' in p.read_bytes() else '\n'
    p.parent.mkdir(parents=True,exist_ok=True)
    with p.open('w',encoding='utf8',newline=newline) as stream:stream.write(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def file(kind,name):ns,path=name.split(':',1);return RES/'assets'/ns/kind/(path+('.json' if kind=='models' else '.png'))
@__import__('functools').lru_cache(None)
def model(name):
    p=file('models',name)
    if not p.is_file():return {}
    d=read(p);base=model(d['parent']) if d.get('parent','').count(':') and not d['parent'].startswith('builtin:') else {}
    return {**base,**d,'textures':{**base.get('textures',{}),**d.get('textures',{})}}
def texture(d,name):
    for _ in range(32):
        if not name.startswith('#'):return name
        name=d.get('textures',{}).get(name[1:],'missing:missing')
    raise ValueError('Texture cycle')
@__import__('functools').lru_cache(None)
def image(name):
    p=file('textures',name)
    if not p.is_file():return None
    with Image.open(p) as im:return im.convert('RGBA')
def crop(d,face):
    im=image(texture(d,face.get('texture','missing:missing')))
    if im is None:return None
    uv=face.get('uv',[0,0,16,16]);a,b,c,e=uv
    box=[int(min(a,c)*im.width/16),int(min(b,e)*im.height/16),int(max(a,c)*im.width/16),int(max(b,e)*im.height/16)]
    if box[0]==box[2] or box[1]==box[3]:return None
    part=im.crop(box)
    if a>c:part=part.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    if b>e:part=part.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
    if face.get('rotation'):part=part.rotate(-face['rotation'],expand=True)
    return part
def opacity(d,f):
    im=crop(d,f)
    if im is None:return 0.,False
    values=[p[3] for p in im.getdata()];return sum(v==255 for v in values)/len(values),any(v>0 for v in values)
def rotation(e):return e.get('rotation',{}).get('angle',0)
def exposed(e,side,elements):
    axis,sign=SIDES[side];axes=[i for i in range(3) if i!=axis];plane=e['to'][axis] if sign>0 else e['from'][axis]
    pieces=[(e['from'][axes[0]],e['from'][axes[1]],e['to'][axes[0]],e['to'][axes[1]])]
    for other in elements:
        if other is e or rotation(other):continue
        if not (other['from'][axis]<=plane+1e-6 and other['to'][axis]>=plane-1e-6):continue
        if sign>0 and other['to'][axis]<=plane+1e-6:continue
        if sign<0 and other['from'][axis]>=plane-1e-6:continue
        ox0,oy0,ox1,oy1=other['from'][axes[0]],other['from'][axes[1]],other['to'][axes[0]],other['to'][axes[1]];remaining=[]
        for x0,y0,x1,y1 in pieces:
            a,b,c,d=max(x0,ox0),max(y0,oy0),min(x1,ox1),min(y1,oy1)
            if c<=a or d<=b:remaining.append((x0,y0,x1,y1));continue
            for p in [(x0,y0,a,y1),(c,y0,x1,y1),(a,y0,c,b),(a,d,c,y1)]:
                if p[2]>p[0]+1e-6 and p[3]>p[1]+1e-6:remaining.append(p)
        pieces=remaining
        if not pieces:return False
    return bool(pieces)

def main():
    args=argparse.ArgumentParser();args.add_argument('--apply',action='store_true');opt=args.parse_args()
    gallery=read(ROOT/'build/v1271-gallery-delivery-final5b/World/gallery-manifest.json')
    paths={r.get('registry',r.get('registryId','')).split(':')[-1] for r in gallery['exhibits'] if r.get('gridId','').startswith(('new/','v12/'))}
    paths-= {'source__wall_dec_top','source_hold_wall_lantern_4'}
    surfaces=collections.defaultdict(list);empty=[];icons=[];duplicates=collections.defaultdict(list);decal=[]
    checked_models=set();volume_skips=collections.Counter();faces_added=0
    for path in sorted(paths):
        specpath=RES/'bloodborne_dw/composite'/(path+'.json')
        if not specpath.is_file():continue
        spec=read(specpath);is_source=path.startswith('source_');all_sig=[];front=False;maxface=None;visible=False
        for vi,var in enumerate(spec['variants']):
            geometry=[];face_normals=[]
            for part in var['poses']['closed']['parts']:
                name=part['model'];d=model(name);elements=d.get('elements',[])
                if not elements and d.get('parent'):volume_skips['external_parent_unresolved']+=1
                for e in elements:
                    faces=e.get('faces',{});visible_faces=[]
                    for side,f in faces.items():
                        opaque,has_pixels=opacity(d,f)
                        if not has_pixels:continue
                        visible=True;visible_faces.append(side)
                        axis,sign=SIDES[side];other=[k for k in range(3) if k!=axis];area=(e['to'][other[0]]-e['from'][other[0]])*(e['to'][other[1]]-e['from'][other[1]])
                        if vi==0 and area>0 and not rotation(e) and not any(part.get(k,0) for k in ('yaw','pitch','extraYaw')):
                            if side=='south':front=True
                            if maxface is None or area>maxface[0]:maxface=(area,side)
                        im=crop(d,f);all_sig.append([vi,part.get('offset',[0,0,0]),part.get('yaw',0),part.get('pitch',0),e['from'],e['to'],e.get('rotation'),side,hashlib.sha256(im.tobytes()).hexdigest(),list(im.size),e.get('shade',True),d.get('ambientocclusion',True)])
                    if visible_faces:geometry.append((e,part));face_normals.extend(visible_faces)
                if is_source and name not in checked_models:
                    checked_models.add(name);native=file('models',name);raw=read(native);updated=copy.deepcopy(elements);changed=[]
                    for index,e in enumerate(elements):
                        if rotation(e):volume_skips['rotated_element']+=1;continue
                        if any(e['to'][k]-e['from'][k]<=1e-5 for k in range(3)):volume_skips['intentional_plane']+=1;continue
                        for side in set(SIDES)-set(e.get('faces',{})):
                            if not exposed(e,side,elements):volume_skips['internal_face']+=1;continue
                            source_side=OPPOSITE[side]
                            if source_side not in e.get('faces',{}):source_side=next((s for s,f in e.get('faces',{}).items() if opacity(d,f)[0]>.99),None)
                            if source_side is None or opacity(d,e['faces'][source_side])[0]<.99:volume_skips['transparent_or_no_suitable_texture']+=1;continue
                            face=copy.deepcopy(e['faces'][source_side]);face.pop('cullface',None);updated[index].setdefault('faces',{})[side]=face
                            changed.append({'element':index,'side':side,'copiedExistingFace':source_side});faces_added+=1
                    if changed:
                        surfaces[name].extend(changed)
                        if opt.apply:raw['elements']=updated;write(native,raw)
            if is_source and geometry and all(not rotation(e) and not any(p.get(k,0) for k in ('yaw','pitch','extraYaw')) for e,p in geometry):
                points=[([e['from'][i]+p.get('offset',[0,0,0])[i] for i in range(3)],[e['to'][i]+p.get('offset',[0,0,0])[i] for i in range(3)]) for e,p in geometry]
                spans=[max(b[i] for a,b in points)-min(a[i] for a,b in points) for i in range(3)]
                flat=[i for i,x in enumerate(spans) if abs(x)<1e-6]
                if len(flat)==1:
                    axis=flat[0];normals=[SIDES[s] for s in face_normals if SIDES[s][0]==axis]
                    if normals:decal.append({'path':path,'variant':vi,'axis':axis,'value':points[0][0][axis]/16,'normal':normals[0][1]})
        if not visible:empty.append(path)
        if not front and maxface:
            side=maxface[1];rot={'west':[0,90,0],'east':[0,-90,0],'south':[0,0,0],'up':[90,0,0],'down':[-90,0,0],'north':[0,180,0]}[side]
            icons.append({'path':path,'normalFace':side,'guiRotation':rot})
            if opt.apply:
                item=RES/'assets/bloodborne_dw/models/item'/(path+'.json');doc=read(item);display=doc.setdefault('display',{})
                for mode in ('gui','firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand'):display[mode]={'rotation':rot,'translation':[0,0,0],'scale':[1,1,1]}
                write(item,doc)
        if all_sig:duplicates[hashlib.sha256(json.dumps(sorted(all_sig,key=str),sort_keys=True).encode()).hexdigest()].append(path)
    if opt.apply:
        bypath=collections.defaultdict(list)
        for row in decal:bypath[row['path']].append({k:v for k,v in row.items() if k!='path'})
        for path,rows in bypath.items():p=RES/'bloodborne_dw/composite'/(path+'.json');doc=read(p);doc['surfaceDecals']=rows;write(p,doc)
    report={'status':'APPLIED_REQUIRES_NATIVE_PIXEL_AND_BEHAVIOUR_VALIDATION' if opt.apply else 'AUDIT_ONLY','offeredPathsChecked':len(paths),'modelsChecked':len(checked_models),'exposedOpaqueFaceCandidates':dict(surfaces),'newFaceCount':faces_added,'preservedCases':dict(volume_skips),'predictedEmptyIconCandidates':icons,'surfaceDecals':decal,'emptyOfferedModels':empty,'exactDrawableSignatureGroups':[v for v in duplicates.values() if len(v)>1],'pngPixelsEdited':False,'oldFaceUvCoordinatesAndElementsEdited':False,'scope':'Only offered post-V12 models; zero-thickness planes, rotated elements, covered internal faces and transparency are excluded from cap additions. Native image checks still required.'}
    write(OUT/('surface-applied.json' if opt.apply else 'surface-audit.json'),report)
if __name__=='__main__':main()
