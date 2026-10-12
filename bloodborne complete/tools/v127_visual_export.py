"""Exact post-V12 art inventory, normalized visual audit and Blockbench handoff.

Read-only with respect to gameplay resources. The exported generic mesh projects
use native rendered quad coordinates, per-face UV and embedded original PNGs;
ordinary MC source JSON/parent/texture closure is included independently.
"""
from __future__ import annotations
import argparse, base64, copy, csv, hashlib, io, json, math, time, uuid, zipfile
from collections import defaultdict
from functools import lru_cache
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from v124_visual_audit import Visuals, render, signature, rot

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources'
OUT=ROOT/'docs/v12.7/visual-export'
VANILLA=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'
BG=np.array([39,48,55],dtype=np.uint8)

def write_json(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    staged=path.with_name(path.name+'.v127-write.tmp')
    staged.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    for attempt in range(5):
        try:staged.replace(path);return
        except OSError:
            if attempt==4:raise
            time.sleep(.1)

def digest(data):return hashlib.sha256(data).hexdigest()
def uid(key):return str(uuid.uuid5(uuid.NAMESPACE_URL,'dreamwalker:v127:export:'+key))

def inventory():
    marker=json.loads((ROOT/'build/v126-gallery-input.json').read_text(encoding='utf-8'))
    rows=[r for r in marker['catalogue']['exhibits'] if r['origin']!='V12' and r['kind']=='architecture']
    assert len(rows)==544 and len({r['id'] for r in rows})==544
    overlay=RES/'bloodborne_dw/imported-type-aliases-v127.json'
    if overlay.exists():
        retired={member for group in json.loads(overlay.read_text(encoding='utf-8'))['groups'] for member in group['members'] if member!=group['primaryRegistryId']}
        rows=[r for r in rows if r['registry'] not in retired]
    current=json.loads((RES/'bloodborne_dw/remaining-catalogue.json').read_text(encoding='utf-8'))
    # Every original1045 path has a historic debug ID <=93227. Newly declared
    # extra sources have fresh reserved IDs and their own descriptor.
    debug=json.loads((RES/'bloodborne_dw/debug_catalogue.json').read_text(encoding='utf-8'))
    numbers={r['registryId']:r['temporaryId'] for r in debug['entries']}
    existing={r['registry'] for r in rows}
    for item in current['objects']:
        registry='bloodborne_dw:'+item['path']
        if registry not in existing and int(numbers[registry])>93227:
            rows.append({'registry':registry,'id':numbers[registry],'origin':'NEW','kind':'architecture'})
    return sorted(rows,key=lambda r:int(r['id']))

def spec_of(row):return json.loads((RES/'bloodborne_dw/composite'/f"{row['registry'].split(':')[1]}.json").read_text(encoding='utf-8'))

def active_indices(spec):
    metadata=json.loads((RES/'bloodborne_dw/imported-assembly-v126.json').read_text(encoding='utf-8'))
    ids=[r['installationPose'] for r in metadata['recipes'] if r['primaryRegistryId']==spec['id']]
    return ids or list(range(len(spec['variants'])))

class Art(Visuals):
    def optional(self,relative):
        for root in self.roots:
            source=root/relative
            if source.is_file():return source.read_bytes()
        if self.vanilla and relative in self.vanilla.namelist():return self.vanilla.read(relative)
        return None
    @lru_cache(maxsize=None)
    def blob(self,identifier,kind):
        namespace,path=identifier.split(':',1) if ':' in identifier else ('minecraft',identifier)
        relative='assets/'+namespace+'/'+kind+'/'+path+('.json' if kind=='models' else '.png')
        for root in self.roots:
            source=root/relative
            if source.is_file():return relative,source.read_bytes()
        if self.vanilla and relative in self.vanilla.namelist():return relative,self.vanilla.read(relative)
        raise FileNotFoundError(identifier)

def quads_for(art,spec,index,pose='closed',profile='base'):
    selected=spec['variants'][index]['poses'].get(pose,spec['variants'][index]['poses']['closed'])
    quads=[]
    for part in selected['parts']:
        p=copy.deepcopy(part)
        if profile=='alt':p['model']=p.get('altModel',p['model'])
        quads.extend(art.quads(p))
    return quads

def dependency_closure(art,models):
    documents,textures={},{}
    def model(identifier):
        relative,blob=art.blob(identifier,'models')
        if relative in documents:return
        documents[relative]=blob;document=json.loads(blob)
        parent=document.get('parent')
        if parent and not parent.startswith(('builtin/','minecraft:builtin/')):model(parent)
        for tex in art.textures(identifier).values():
            if not tex.startswith('#'):
                relative,blob=art.blob(tex,'textures');textures[relative]=blob
                metadata=art.optional(relative+'.mcmeta')
                if metadata is not None:textures[relative+'.mcmeta']=metadata
    for identifier in models:model(identifier)
    return documents,textures

def embedded_texture(art,identifier,tint=None):
    original=art.blob(identifier,'textures')[1]
    if tint is None:return original
    rgba=art.image(identifier).copy();rgba[...,:3]=(rgba[...,:3].astype(float)*np.asarray(tint)).astype(np.uint8)
    stream=io.BytesIO();Image.fromarray(rgba).save(stream,format='PNG');return stream.getvalue()

def runtime_tint(spec,index,pose,profile):
    if profile!='alt':return None
    parts=spec['variants'][index]['poses'].get(pose,spec['variants'][index]['poses']['closed'])['parts']
    return None if any(p.get('altModel',p['model'])!=p['model'] for p in parts) else [.60,.67,.72]

def make_bbmodel(art,quads,key,name,tint=None):
    identifiers=list(dict.fromkeys(q[2] for q in quads));textures=[]
    for i,identifier in enumerate(identifiers):
        relative,original=art.blob(identifier,'textures');blob=embedded_texture(art,identifier,tint);image=art.image(identifier);height,width=image.shape[:2]
        metadata=art.optional(relative+'.mcmeta');animation=json.loads(metadata).get('animation') if metadata else None
        frame_width=animation.get('width',width) if animation else width;frame_height=animation.get('height',frame_width) if animation else height
        textures.append({'name':identifier,'uuid':uid(key+'/texture/'+identifier),'id':str(i),'width':width,'height':height,
            'uv_width':16*width/frame_width,'uv_height':16*height/frame_height,'source':'data:image/png;base64,'+base64.b64encode(blob).decode(),
            'internal':True,'mode':'bitmap','visible':True,'render_mode':'default','render_sides':'auto',
            'dreamwalker_originalPNG_SHA256':digest(original),'dreamwalker_runtimeALTPreviewTint':tint,
            'dreamwalker_animationMetadata':animation,'dreamwalker_genericPreviewAnimationFrame':0 if animation else None})
    elements=[]
    # Preserve each face's native cyclic vertices, including coincident UV seams.
    # Quad-local vertex identities prevent Blockbench welding authored seams.
    for texture_index,identifier in enumerate(identifiers):
        vertices,faces={},{}
        for j,(points,uv,texture,source_tint_index) in enumerate(quads):
            if texture!=identifier:continue
            keys=[f'v{j}_{i}' for i in range(4)]
            for k,p in zip(keys,points):vertices[k]=[float(v) for v in p]
            faces['f'+str(j)]={'vertices':keys,'uv':{k:[float(v) for v in p] for k,p in zip(keys,uv)},'texture':texture_index}
        elements.append({'name':identifier.rsplit('/',1)[-1],'type':'mesh','uuid':uid(key+'/mesh/'+identifier),
            'origin':[0,0,0],'rotation':[0,0,0],'visibility':True,'export':True,'vertices':vertices,'faces':faces})
    return {'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':name,
        'model_identifier':key,'resolution':{'width':16,'height':16},'elements':elements,
        'outliner':[e['uuid'] for e in elements],'textures':textures,
        'dreamwalker_handoff':{'unitsPerBlock':16,'exactRenderedGeometry':True,'sourceFaceUVUnits':16,
            'runtimeALTVertexMultiplierPreview':tint,'authoredSourcePNGsUnchangedInSourceJava':True,
            'blockbenchFormatSource':'https://blockbench.net/wiki/docs/bbmodel/',
            'minecraftGenericMeshCaveat':'Keep the included original Java JSON for game reimport; arbitrary composite rotations cannot be exported directly as one legal Java block cube model.'}}

def verify_bbmodel(art,quads,project):
    restored=[];textures=project['textures']
    for mesh in project['elements']:
        for face in mesh['faces'].values():
            keys=face['vertices'];restored.append((np.array([mesh['vertices'][k] for k in keys]),np.array([face['uv'][k] for k in keys]),textures[face['texture']]['name']))
    expected=sorted(digest(p.astype('<f8').tobytes()+u.astype('<f8').tobytes()+t.encode()) for p,u,t,_ in quads)
    actual=sorted(digest(p.astype('<f8').tobytes()+u.astype('<f8').tobytes()+t.encode()) for p,u,t in restored)
    assert actual==expected,'BBMODEL quad vertex/UV roundtrip mismatch'
    for texture in textures:
        blob=base64.b64decode(texture['source'].split(',',1)[1]);tint=texture.get('dreamwalker_runtimeALTPreviewTint')
        assert blob==embedded_texture(art,texture['name'],tint)
        assert texture['dreamwalker_originalPNG_SHA256']==digest(art.blob(texture['name'],'textures')[1])
    return len(restored)

def composite(frame):
    alpha=frame[...,3:4].astype(float)/255
    return (frame[...,:3]*alpha+BG*(1-alpha)).astype(np.uint8)

def visuals_pass(args,rows,art):
    out=OUT/'projections';out.mkdir(parents=True,exist_ok=True)
    records,images=[],[]
    names={};side=80;columns=8;rows_per=10
    for n,row in enumerate(rows):
        spec=spec_of(row);indices=active_indices(spec);index=indices[0];quads=quads_for(art,spec,index)
        points=np.asarray([q[0] for q in quads]).reshape(-1,3)
        canonical=min((signature(art,quads,yaw*45)[0],yaw) for yaw in range(8))[1]
        frames=np.asarray([composite(render(art,quads,canonical*45+yaw*90,side)) for yaw in range(4)])
        images.append(frames)
        dimensions=min((np.ptp(rot(points,1,yaw*45,[8,8,8]),axis=0) for yaw in range(8)),key=lambda x:x[0]*x[2])
        dimensions=np.array([min(dimensions[0],dimensions[2]),dimensions[1],max(dimensions[0],dimensions[2])])
        record={'id':row['id'],'registryId':row['registry'],'name':spec['displayName'],'activePoses':indices,
            'defaultPose':index,'canonicalYawSteps':canonical,'dimensionsBlocks':[float(v/16) for v in dimensions],
            'quadCount':len(quads),'sourceTextures':sorted({q[2] for q in quads}),
            'renderSha256':[digest(f.tobytes()) for f in frames],
            'closedAppearanceSignatures':[min(signature(art,quads_for(art,spec,i),yaw*45)[0] for yaw in range(8)) for i in indices]}
        records.append(record)
        if n%40==0:print('current working visual',n,'/',len(rows),flush=True)
    dims=np.asarray([r['dimensionsBlocks'] for r in records]);features=[]
    for im in images:
        reduced=np.asarray([np.asarray(Image.fromarray(frame).resize((12,12),Image.Resampling.BOX)) for frame in im])
        features.append(reduced.reshape(-1)/255)
    features=np.asarray(features);seen=set();candidates=[]
    for i,one in enumerate(records):
        ratio=np.max(np.abs(dims-dims[i])/np.maximum(np.maximum(dims,dims[i]),.001),axis=1)
        distance=np.mean(np.abs(features-features[i]),axis=1)
        for j in [int(v) for v in np.argsort(distance) if v!=i and ratio[v]<=.16][:10]:
            pair=tuple(sorted((i,j)))
            if pair in seen:continue
            seen.add(pair);best=None
            for shift in range(4):
                a,b=images[i],np.roll(images[j],shift,axis=0)
                ma,mb=np.any(a!=BG,axis=3),np.any(b!=BG,axis=3);mask=ma|mb
                silhouette=1-float((ma&mb).sum())/max(1,int(mask.sum()))
                pixel=float(np.abs(a.astype(float)-b.astype(float))[mask].mean()/255) if mask.any() else 0
                result=(silhouette+.6*pixel,silhouette,pixel,shift)
                if best is None or result<best:best=result
            if best[0]<.13 and best[2]<.14:
                candidates.append({'a':one['id'],'b':records[j]['id'],'difference':best[0],
                    'silhouetteDifference':best[1],'pixelDifference':best[2],'shiftQuarterTurns':best[3],
                    'sameAllActiveArt':set(one['closedAppearanceSignatures'])==set(records[j]['closedAppearanceSignatures'])})
    candidates.sort(key=lambda p:p['difference'])
    for page in range(math.ceil(len(records)/(columns*rows_per))):
        subset=records[page*columns*rows_per:(page+1)*columns*rows_per]
        canvas=Image.new('RGB',(columns*side*2,rows_per*(side*2+20)),tuple(BG));draw=ImageDraw.Draw(canvas)
        for k,r in enumerate(subset):
            px=(k%columns)*side*2;py=(k//columns)*(side*2+20);idx=page*columns*rows_per+k
            for v in range(4):canvas.paste(Image.fromarray(images[idx][v]),(px+(v%2)*side,py+(v//2)*side))
            draw.text((px+4,py+side*2+2),r['id'],fill='white');r['reviewSheet']=f'projections/current-{page:02d}.png'
        canvas.save(out/f'current-{page:02d}.png')
    for n,p in enumerate(candidates):
        i=next(i for i,r in enumerate(records) if r['id']==p['a']);j=next(i for i,r in enumerate(records) if r['id']==p['b'])
        canvas=Image.new('RGB',(side*8,side+25),tuple(BG));draw=ImageDraw.Draw(canvas)
        b=np.roll(images[j],p['shiftQuarterTurns'],axis=0)
        for v in range(4):canvas.paste(Image.fromarray(images[i][v]),(v*side*2,0));canvas.paste(Image.fromarray(b[v]),(v*side*2+side,0))
        draw.text((4,side+4),f"{p['a']} / {p['b']} diff {p['difference']:.4f}",fill='white')
        path=f'projections/pair-{n:03d}-{p["a"]}-{p["b"]}.png';canvas.save(OUT/path);p['image']=path
    report={'status':'CURRENT_RENDER_COVERAGE_PENDING_REVIEW','method':'544 current offered NEW types; actual current descriptor transforms/source RGBA/UV. Four normalized yaw views and every active closed pose canonical surface signatures. Candidate comparison ignores resource paths and JSON layout. Original V12 retained untouched; native screenshot evidence remains separate.',
        'summary':{'workingTypes':len(records),'defaultRenderedFrames':len(records)*4,'allActivePoses':sum(len(r['activePoses']) for r in records),'nearCandidatePairs':len(candidates)},'objects':records,'candidates':candidates}
    write_json(OUT/'visual-inventory.json',report)
    with (OUT/'names-before.tsv').open('w',encoding='utf-8-sig',newline='') as stream:
        writer=csv.writer(stream,delimiter='\t');writer.writerow(['id','name','sizeXYZ','textures'])
        for r in records:writer.writerow([r['id'],r['name'],r['dimensionsBlocks'],','.join(r['sourceTextures'])])
    print(json.dumps(report['summary']),flush=True)

def export_pass(args,rows,art):
    dest=ROOT/'build/v127-blockbench-export';dest.mkdir(parents=True,exist_ok=True)
    archive=ROOT/'build/Dreamwalker-BB-V12.7-New-Blockbench-Assets.zip'
    index=[];closure_models,closure_textures={},{};checked=0;allnames=set()
    with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for n,row in enumerate(rows):
            spec=spec_of(row);indices=active_indices(spec);models=set();entries=[]
            # Hidden legacy art is retained in raw JSON so source compatibility is
            # reviewable; editable whole objects expose only current active poses.
            for v in spec['variants']:
                for p in v['poses'].values():
                    for part in p['parts']:models.add(part['model']);models.add(part.get('altModel',part['model']))
            docs,tex=dependency_closure(art,models);closure_models.update(docs);closure_textures.update(tex)
            for i in indices:
                variant=spec['variants'][i]
                for pose in variant['poses']:
                    for profile in ('base','alt'):
                        key=f"{row['id']}/pose-{i:03d}/{pose}-{profile}";quads=quads_for(art,spec,i,pose,profile)
                        tint=runtime_tint(spec,i,pose,profile)
                        project=make_bbmodel(art,quads,key,spec['displayName'],tint);checked+=verify_bbmodel(art,quads,project)
                        relative='editable/'+key+'.bbmodel';blob=json.dumps(project,ensure_ascii=False,separators=(',',':')).encode('utf-8')
                        z.writestr(relative,blob);entries.append({'pose':i,'state':pose,'profile':profile,'project':relative,'sha256':digest(blob),'quads':len(quads),
                            'runtimeALTVertexMultiplierPreview':tint,'originalTextureSHA256s':{t:digest(art.blob(t,'textures')[1]) for t in sorted({q[2] for q in quads})}})
            relative='runtime-descriptors/'+row['id']+'.json';blob=json.dumps(spec,ensure_ascii=False,indent=2).encode('utf-8');z.writestr(relative,blob)
            default=quads_for(art,spec,indices[0]);points=np.asarray([q[0] for q in default]).reshape(-1,3)
            flat=any(abs(v)<1e-7 for v in np.ptp(points,axis=0));plain=(len(default)==6 and len(spec['variants'][indices[0]]['poses']['closed']['parts'])==1)
            index.append({'id':row['id'],'registryId':row['registry'],'name':spec['displayName'],'kind':'sprite/plane' if flat else 'ordinary-block' if plain else 'complex-model',
                'descriptor':relative,'editableProjects':entries,'sourceModels':sorted(models),'textureDependencies':sorted(tex),'originalV12Excluded':True})
            if n%80==0:print('Blockbench export',n,'/',len(rows),flush=True)
        utility=[]
        for number,path,label in [('93230','dw_barrier','dw-барьер'),('93231','dw_light','Невидимый свет')]:
            models=[f'bloodborne_dw:block/{path}',f'bloodborne_dw:block/{path}_alt',f'bloodborne_dw:item/{path}']
            docs,tex=dependency_closure(art,models);closure_models.update(docs);closure_textures.update(tex)
            for profile in ('base','alt'):
                identifier=f'bloodborne_dw:block/{path}'+('_alt' if profile=='alt' else '')
                quads=art.quads({'model':identifier});key=f'{number}/pose-000/closed-{profile}';project=make_bbmodel(art,quads,key,label);checked+=verify_bbmodel(art,quads,project)
                relative='editable/'+key+'.bbmodel';blob=json.dumps(project,ensure_ascii=False,separators=(',',':')).encode('utf-8');z.writestr(relative,blob)
                utility.append({'id':number,'registryId':'bloodborne_dw:'+path,'name':label,'profile':profile,'project':relative,'sourceModels':models,'textures':sorted(tex),'worldRenderPolicy':'Hidden in world; held-item marker appearance retained, project shows editable textured cube.'})
        for path,blob in {**closure_models,**closure_textures}.items():z.writestr('source-java/'+path,blob)
        document={'schemaVersion':1,'status':'PASS_EXACT_MODEL_UV_TEXTURE_ROUNDTRIP','objects':index,
            'summary':{'workingNewTypes':len(index)+2,'compositeWorkingNewTypes':len(index),'newUtilityTypes':2,'projects':sum(len(r['editableProjects']) for r in index)+len(utility),'nativeQuadVertexUVRoundtrips':checked,'sourceJavaModelDependencies':len(closure_models),'pngTextureDependencies':sum(p.endswith('.png') for p in closure_textures),'animationMetadataDependencies':sum(p.endswith('.mcmeta') for p in closure_textures)},
            'utilityObjects':utility,
            'formatSources':['https://blockbench.net/wiki/docs/bbmodel/','https://github.com/JannisX11/blockbench/blob/master/js/formats/bbmodel.js']}
        z.writestr('INDEX.json',json.dumps(document,ensure_ascii=False,indent=2).encode('utf-8'))
        table=io.StringIO();writer=csv.writer(table,delimiter='\t');writer.writerow(['id','registryId','russianName','kind','projectCount','firstProject'])
        for r in index:writer.writerow([r['id'],r['registryId'],r['name'],r['kind'],len(r['editableProjects']),r['editableProjects'][0]['project']])
        for number in ('93230','93231'):
            variants=[r for r in utility if r['id']==number];first=variants[0]
            writer.writerow([number,first['registryId'],first['name'],'invisible-utility',len(variants),first['project']])
        z.writestr('INDEX-RU.tsv',table.getvalue().encode('utf-8-sig'))
        z.writestr('README-RU.txt',('''Новые объекты Dreamwalker BB V12.7 для Blockbench

Оригинальные типы V12 не включены. Каждый пятизначный ID имеет папку editable/ID; pose — индекс рабочего состояния, closed/open — состояние двери, base/alt — внешний вид. Откройте любой .bbmodel через File > Open Model. PNG встроены: исходный ресурспак и Minecraft не нужны. Грани сохраняют реальные координаты модели (16 единиц на блок) и UV. В source-java лежат исходные PNG побайтно, включая прозрачность, и метаданные анимации .png.mcmeta.

Сложные составные объекты сохранены как Generic Model mesh: произвольные повороты частей/наклоны нельзя без потерь записать одним Minecraft Java cube JSON. Меш можно редактировать в Blockbench; для возврата изменений в мод используйте source-java/assets/... JSON и runtime-descriptors/ID.json с точным ID/pose. Геометрия экспортирована, авторская коллизия не реконструируется.

Для простых кубов и плоских спрайтов PNG перечислены в INDEX.json -> textureDependencies и находятся в source-java/assets/.../textures. INDEX-RU.tsv связывает ID, название, вид и проект. source-java содержит полное замыкание parent/textures. Исторические служебные части входят только как зависимости; снятые с каталога дубликаты не предлагаются отдельными рабочими типами.

ALT с авторской альтернативной моделью сохраняется с её исходными PNG. Если у всей позы нет авторского ALT, игра умножает RGB вершин на (0.60, 0.67, 0.72). Только в editable/*-alt.bbmodel этот множитель запечён в копию PNG для удобного просмотра; alpha, UV и геометрия сохранены. Исходный PNG в source-java не меняется. INDEX и проект явно указывают runtimeALTVertexMultiplierPreview и SHA256 исходного PNG.

Анимированные PNG сохранены целыми полосами кадров вместе с .mcmeta. Generic-просмотр показывает первый кадр, не проигрывает Minecraft-анимацию. UV-канва текстуры масштабирована по размеру кадра, чтобы исходные UV не показывали всю полосу на одной грани. Метаданные анимации и номер кадра указаны в проекте.

93230 dw-барьер и 93231 Невидимый свет имеют отдельные проекты и исходные текстуры. Их куб здесь видим для редактирования. В игре сохраняется невидимость и показ маркера при удержании предмета.

Формат: Blockbench 4.10-compatible .bbmodel, Generic Model; описание формата https://blockbench.net/wiki/docs/bbmodel/ . Проверка: каждая грань после обратного чтения проекта совпадает с исходными rendered vertex/UV; встроенные PNG совпадают с исходными либо с явно указанной ALT preview-копией; исходные SHA256 проверены; архив проверен CRC. GUI открытие редактором отдельно отмечается в отчёте и не подменяется этой проверкой.
''').encode('utf-8'))
    with zipfile.ZipFile(archive) as z:assert z.testzip() is None
    document['archive']=str(archive);document['archiveSha256']=digest(archive.read_bytes());document['archiveBytes']=archive.stat().st_size
    write_json(OUT/'blockbench-export.json',document)
    print(json.dumps({'status':document['status'],**document['summary'],'archiveSha256':document['archiveSha256']}),flush=True)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['visuals','export','review-sheets']);args=parser.parse_args()
    if args.action=='review-sheets':
        report=json.loads((OUT/'visual-inventory.json').read_text(encoding='utf-8'));candidates=report['candidates']
        for page in range(math.ceil(len(candidates)/40)):
            subset=candidates[page*40:(page+1)*40];canvas=Image.new('RGB',(1280,2100),tuple(BG))
            for n,p in enumerate(subset):canvas.paste(Image.open(OUT/p['image']),(640*(n%2),105*(n//2)))
            canvas.save(OUT/f'projections/candidates-{page:02d}.png')
        return
    rows=inventory();art=Art(vanilla_jar=VANILLA)
    if args.action=='visuals':visuals_pass(args,rows,art)
    else:export_pass(args,rows,art)

if __name__=='__main__':main()
