"""Read-only, exact source-pack -> built-JAR material and block/model audit."""
import collections,hashlib,io,json,re,traceback,zipfile
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFont
HERE=Path(__file__).absolute().parent
P=HERE.parents[1]/'bloodborne complete'
JAR=P/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-owner32-static-city.6-chunk-mesh.2.jar'
PACK=P/'build/runtime-client-city-mesh-baseline/resourcepacks/bbmc_v15_resource.zip'
def sha(data):return hashlib.sha256(data).hexdigest()
def norm(value):
    if isinstance(value,dict):return {k:norm(v) for k,v in value.items()}
    if isinstance(value,list):return [norm(v) for v in value]
    if isinstance(value,str):
        for prefix in ['bloodborne_dw:remaining/source/','bloodborne_dw:block/remaining/source/']:
            if value.startswith(prefix):
                ns,rest=value[len(prefix):].split('/',1);return ns+':'+rest
    return value
def modelnorm(value):
    d=norm(value)
    if 'parent' in d:d['parent']=rid(d['parent'])
    if 'textures' in d:d['textures']={k:rid(v) if not v.startswith('#') else v for k,v in d['textures'].items()}
    for element in d.get('elements',[]):
        for face in element.get('faces',{}).values():
            if 'texture' in face and not face['texture'].startswith('#'):face['texture']=rid(face['texture'])
    return d
def rid(value):return value if ':' in value else 'minecraft:'+value
def asset(kind,value,suffix):
    ns,path=rid(value).split(':',1);return 'assets/'+ns+'/'+kind+'/'+path+suffix
def sourceid(path,prefix,suffix):
    s=path[len(prefix):-len(suffix)];ns,rest=s.split('/',1);return ns+':'+rest
def base_model(value):
    value=rid(norm(value))
    prefix='bloodborne_dw:remaining/source_uvlock/'
    if value.startswith(prefix):
        ns,path=value[len(prefix):].split('/',1);value=ns+':'+re.sub(r'_x-?\d+_y-?\d+$','',path)
    return value
def sampling_fields(model):
    out=collections.Counter()
    for element in model.get('elements',[]):
        for face in element.get('faces',{}).values():
            fields={k:face[k] for k in ['texture','uv','rotation','tintindex'] if k in face}
            fields['shade']=element.get('shade',True)
            if 'uv' not in face:fields['defaultUvGeometry']=[element.get('from'),element.get('to')]
            out[json.dumps(fields,sort_keys=True)]+=1
    return out
def stats(data):
    ar=np.asarray(Image.open(io.BytesIO(data)).convert('RGBA'));opaque=ar[:,:,3]>0
    rgb=ar[:,:,:3][opaque];luma=rgb@np.array([.2126,.7152,.0722]) if len(rgb) else np.array([])
    return {'size':[int(ar.shape[1]),int(ar.shape[0])],'visiblePixels':int(opaque.sum()),'transparentFraction':float((ar[:,:,3]==0).mean()),'partialAlphaFraction':float(((ar[:,:,3]>0)&(ar[:,:,3]<255)).mean()),'visibleMeanRGB':[round(float(v),4) for v in rgb.mean(axis=0)] if len(rgb) else None,'visibleMeanLuma':round(float(luma.mean()),4) if len(rgb) else None,'visibleLumaP95':round(float(np.percentile(luma,95)),4) if len(rgb) else None,'visibleNearBlackFraction':float((luma<12).mean()) if len(rgb) else None}
def run():
    with zipfile.ZipFile(JAR) as mod,zipfile.ZipFile(PACK) as src,zipfile.ZipFile(P/'build/runtime-client-city-mesh-baseline/minecraft-client.jar') as vanilla:
        mn=set(mod.namelist());sn=set(src.namelist());vn=set(vanilla.namelist());texture_rows=[];model_rows=[]
        for name in sorted(mn):
            prefix=next((p for p in ['assets/bloodborne_dw/textures/block/remaining/source/','assets/bloodborne_dw/textures/remaining/source/'] if name.startswith(p)),None)
            if prefix is None or not name.endswith('.png'):continue
            sid=sourceid(name,prefix,'.png');original=asset('textures',sid,'.png');raw=mod.read(name)
            row={'modAsset':name,'sourceTexture':sid,'originalAsset':original,'modSha256':sha(raw),'mod':stats(raw),'originalPresent':original in sn}
            if original in sn:
                old=src.read(original);a=np.asarray(Image.open(io.BytesIO(raw)).convert('RGBA'));b=np.asarray(Image.open(io.BytesIO(old)).convert('RGBA'))
                row.update(originalSha256=sha(old),byteIdentical=raw==old,rgbaIdentical=a.shape==b.shape and np.array_equal(a,b),original=stats(old),maxChannelDelta=int(np.abs(a.astype(np.int16)-b.astype(np.int16)).max()) if a.shape==b.shape else None)
            texture_rows.append(row)
        texture_by_id={r['sourceTexture']:r for r in texture_rows}
        raw_models={}
        for name in sorted(mn):
            prefix='assets/bloodborne_dw/models/remaining/source/'
            if not name.startswith(prefix) or not name.endswith('.json'):continue
            sid=sourceid(name,prefix,'.json');original=asset('models',sid,'.json');model=json.loads(mod.read(name));raw_models[sid]=model
            row={'modModel':'bloodborne_dw:remaining/source/'+sid.replace(':','/',1),'sourceModel':sid,'originalAsset':original,'originalPresent':original in sn,'vanillaFallbackPresent':original not in sn and original in vn}
            if original in sn or original in vn:
                old=modelnorm(json.loads((src if original in sn else vanilla).read(original)));new=modelnorm(model)
                row['allFieldsEqualAfterNamespaceRewrite']=new==old
                row['differingTopLevelFields']=[k for k in sorted(set(old)|set(new)) if old.get(k)!=new.get(k)]
                row['originalFacesPreserved']=all(element in new.get('elements',[]) for element in old.get('elements',[]))
                row['texturesEqualAfterNamespaceRewrite']=new.get('textures')==old.get('textures')
                original_samples=sampling_fields(old);current_samples=sampling_fields(new)
                row['originalTextureSamplingFieldsPreserved']=all(current_samples[k]>=v for k,v in original_samples.items())
            model_rows.append(row)
        resolved_cache={}
        def resolve_model(model_id,trail=()):
            model_id=rid(norm(model_id))
            if model_id in resolved_cache:return resolved_cache[model_id]
            if model_id in trail:return {'textures':{},'elements':[],'unresolvedParents':[model_id]}
            copied=raw_models.get(model_id);original=asset('models',model_id,'.json')
            modname=asset('models',model_id,'.json')
            d=modelnorm(copied) if copied is not None else modelnorm(json.loads(mod.read(modname))) if modname in mn else modelnorm(json.loads(src.read(original))) if original in sn else modelnorm(json.loads(vanilla.read(original))) if original in vn else {}
            parent=resolve_model(d['parent'],trail+(model_id,)) if 'parent' in d else {'textures':{},'elements':[],'unresolvedParents':[]}
            out={'textures':dict(parent['textures']),'elements':d.get('elements',parent['elements']),'unresolvedParents':list(parent['unresolvedParents'])}
            out['textures'].update(d.get('textures',{}))
            if not d:out['unresolvedParents'].append(model_id)
            resolved_cache[model_id]=out;return out
        model_materials={}
        def material_info(model_id):
            d=resolve_model(model_id);refs=[];faces=0
            for element in d['elements']:
                for face in element.get('faces',{}).values():
                    value=face.get('texture','');seen=set();faces+=1
                    while value.startswith('#') and value not in seen:
                        seen.add(value);value=d['textures'].get(value[1:],'UNRESOLVED:'+value)
                    refs.append(rid(value) if not value.startswith('UNRESOLVED:') else value)
            used=sorted(set(refs));model_materials[model_id]=used
            return {'faceTextures':used,'resolvedFaces':faces,'unresolvedParents':d['unresolvedParents'],'allUsedCopiedTexturesMatchSource':all(texture_by_id.get(t,{}).get('rgbaIdentical',False) for t in used) if used else None}
        for row in model_rows:row.update(material_info(row['sourceModel']))
        catalogue=json.loads(mod.read('bloodborne_dw/remaining-catalogue.json'))
        spec_names={n for n in mn if n.startswith('bloodborne_dw/composite/') and n.endswith('.json')}
        blocks=[];texture_users=collections.defaultdict(set)
        for obj in catalogue['objects']:
            registry='bloodborne_dw:'+obj['path'];specname='bloodborne_dw/composite/'+obj['path']+'.json'
            spec=json.loads(mod.read(specname)) if specname in spec_names else None
            for v in obj['variants']:
                index=v['variant'];parts=[]
                if spec and index<len(spec.get('variants',[])):
                    for pose in spec['variants'][index].get('poses',{}).values():parts.extend(pose.get('parts',[]))
                actual=sorted({rid(norm(p['model'])) for p in parts});expected=[rid(norm(m)) for m in v.get('sourceModels',[])]
                for model in actual:
                    if model not in model_materials:material_info(model)
                textures=sorted({t for m in actual for t in model_materials.get(m,[])})
                row={'registryId':registry,'variant':index,'displayName':v.get('name',obj.get('displayName')),'catalogueSourceModels':expected,'actualPartModels':actual,'textures':textures,'specPresent':spec is not None,'exactSourceModelSet':set(actual)==set(expected),'sourceModelSetAfterUvlockResolution':{base_model(v) for v in actual}=={base_model(v) for v in expected},'allActualMaterialsHaveExactCopiedRgba':bool(textures) and all(texture_by_id.get(t,{}).get('rgbaIdentical',False) for t in textures)}
                blocks.append(row)
                for t in textures:texture_users[t].add(registry+'[variant='+str(index)+']')
        fallback=json.loads(mod.read('bloodborne_dw/source-fallback-carriers-v1271.json'))
        fallback_rows=[]
        for i,state in enumerate(fallback['sourceStates']):
            models=[rid(a['model']) for a in fallback['sourceApplicationSets'][i]]
            textures=sorted({t for m in models for t in model_materials.get(m,[])})
            fallback_rows.append({'index':i,'sourceState':state,'carrier':fallback['targetRegistryIds'][i],'sourceModels':models,'textures':textures})
            for t in textures:texture_users[t].add('fallback['+str(i)+']='+state)
        for row in texture_rows:row['matchedBlocksAndStates']=sorted(texture_users[row['sourceTexture']])
        colormaps=[]
        for palette in ['grass','foliage']:
            current='assets/bloodborne_dw/textures/remaining/colormap/'+palette+'.png';original='assets/minecraft/textures/colormap/'+palette+'.png'
            if current in mn and original in sn:colormaps.append({'name':palette,'modAsset':current,'originalAsset':original,'byteIdentical':mod.read(current)==src.read(original),'modSha256':sha(mod.read(current)),'originalSha256':sha(src.read(original))})
        summary={'jar':str(JAR),'jarSha256':sha(JAR.read_bytes()),'pack':str(PACK),'packSha256':sha(PACK.read_bytes()),'copiedTextures':len(texture_rows),'uniqueSourceTextures':len(texture_by_id),'missingOriginalTextures':sum(not r['originalPresent'] for r in texture_rows),'byteIdenticalTextures':sum(r.get('byteIdentical',False) for r in texture_rows),'rgbaIdenticalTextures':sum(r.get('rgbaIdentical',False) for r in texture_rows),'copiedModels':len(model_rows),'modelsPresentInOriginalPack':sum(r['originalPresent'] for r in model_rows),'equalModelsAfterNamespaceRewrite':sum(r.get('allFieldsEqualAfterNamespaceRewrite',False) for r in model_rows),'textureAssignmentsEqualModels':sum(r.get('texturesEqualAfterNamespaceRewrite',False) for r in model_rows),'catalogueBlockVariants':len(blocks),'catalogueVariantsWithSpecs':sum(r['specPresent'] for r in blocks),'exactSourceModelSetVariants':sum(r['exactSourceModelSet'] for r in blocks),'fallbackStates':len(fallback_rows),'darkVisibleTexturesBelowLuma32':[r['sourceTexture'] for r in texture_rows if r['mod']['visibleMeanLuma'] is not None and r['mod']['visibleMeanLuma']<32],'rgbaMismatches':[r['sourceTexture'] for r in texture_rows if r.get('rgbaIdentical') is False]}
        summary.update(modelsResolvedThroughVanillaFallback=sum(r['vanillaFallbackPresent'] for r in model_rows),sourceModelSetVariantsAfterUvlockResolution=sum(r['sourceModelSetAfterUvlockResolution'] for r in blocks),variantsWithAllActualMaterialsExactCopiedRgba=sum(r['allActualMaterialsHaveExactCopiedRgba'] for r in blocks))
        summary.update(modelsWithOriginalTextureSamplingFieldsPreserved=sum(r.get('originalTextureSamplingFieldsPreserved',False) for r in model_rows),modelsWithChangedOriginalTextureSamplingFields=[r['sourceModel'] for r in model_rows if r.get('originalTextureSamplingFieldsPreserved') is False])
        summary['copiedColormaps']=colormaps
        (HERE/'materials-audit.json').write_text(json.dumps({'summary':summary,'textures':texture_rows,'models':model_rows,'blockVariants':blocks,'fallbackStates':fallback_rows},indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
        static=json.loads(mod.read('bloodborne_dw/static-architecture-v1274.json'));static_poses=[];spec_cache={}
        for pose in static['poses']:
            reg=pose['registryId'];props=pose['properties'];sp='bloodborne_dw/composite/'+reg.split(':',1)[1]+'.json'
            if sp not in spec_cache:spec_cache[sp]=json.loads(mod.read(sp))
            v=int(props.get('variant','0'))+16*int(props.get('art_bank','0'))
            if props.get('trim')=='true':v+=10
            variants=spec_cache[sp]['variants'];v=v if v<len(variants) else 0
            p=variants[v]['poses']['open' if props.get('open')=='true' else 'closed'];actual=sorted({rid(norm(x['model'])) for x in p.get('parts',[])})
            for m in actual:
                if m not in model_materials:material_info(m)
            static_poses.append({'poseKey':pose['key'],'registryId':reg,'properties':props,'sourcePartModels':actual,'textures':sorted({t for m in actual for t in model_materials.get(m,[])})})
        static_states=[{'index':i,'blockId':'bloodborne_dw:static_architecture_'+str(i//128),'part':i%128,'visibleRootPoses':[f['pose'] for f in r['fragments'] if f['origin']==[0,0,0]],'allFragments':[{'pose':f['pose'],'origin':f['origin']} for f in r['fragments']]} for i,r in enumerate(static['recipes'])]
        (HERE/'static-state-material-mapping.json').write_text(json.dumps({'productionJarSha256':summary['jarSha256'],'method':'Actual shared recipe -> source pose/state -> selected JSON part models -> resolved texture IDs. Root geometry is emitted only for origin-zero fragments.','poses':static_poses,'states':static_states},indent=2)+'\n')
        (HERE/'materials-summary.json').write_text(json.dumps(summary,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
        # Lossless original/mod samples; no brightness or gamma change.
        dark=sorted([r for r in texture_by_id.values() if r['originalPresent'] and r['mod']['visibleMeanLuma'] is not None],key=lambda r:r['mod']['visibleMeanLuma'])[:12]
        canvas=Image.new('RGB',(1060,80+len(dark)*150),(225,225,225));draw=ImageDraw.Draw(canvas)
        draw.text((15,12),'Original v15                      Built mod .2                       Exact RGBA + block/model mapping',(0,0,0))
        for i,r in enumerate(dark):
            y=55+i*150
            for x,data in [(15,src.read(r['originalAsset'])),(175,mod.read(r['modAsset']))]:
                im=Image.open(io.BytesIO(data)).convert('RGBA');im.thumbnail((128,128),Image.Resampling.NEAREST)
                if im.width<128 and im.height<128:im=im.resize((128,128),Image.Resampling.NEAREST)
                canvas.paste(im,(x,y),im)
            draw.text((330,y+8),r['sourceTexture'],(0,0,0))
            draw.text((330,y+32),'RGBA equal: '+str(r['rgbaIdentical'])+'; mean luma: '+str(r['mod']['visibleMeanLuma']),(0,0,0))
            for n,user in enumerate(r['matchedBlocksAndStates'][:3]):draw.text((330,y+55+18*n),user[:105],(0,0,0))
        canvas.save(HERE/'original-vs-mod-dark-materials.png')
try:run()
except Exception:(HERE/'materials-error.txt').write_text(traceback.format_exc());raise
