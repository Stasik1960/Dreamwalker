"""Read-only installed material/style inventory of all V12.4 working NEW types."""
from pathlib import Path
from collections import defaultdict
import hashlib,io,json,sys,traceback,zipfile
import numpy as np
from PIL import Image
from v124_visual_audit import Visuals

ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'docs/v12.5-city-review';OUT.mkdir(exist_ok=True)
BASELINE='653ae307ba4c44392ee08969f3890321e95659bb'
PACK=ROOT.parent/'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16/bbmc_v15_resource.zip'
VANILLA=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def digest(image):return hashlib.sha256(str(image.shape).encode()+image.tobytes()).hexdigest()
def source_model(identifier):
    for prefix in ('bloodborne_dw:remaining/source/','bloodborne_dw:block/remaining/source/'):
        if identifier.startswith(prefix):
            namespace,path=identifier[len(prefix):].split('/',1);return namespace+':'+path
    return identifier
def entry(identifier,kind):
    namespace,path=identifier.split(':',1);return 'assets/'+namespace+'/'+kind+'/'+path+('.json' if kind=='models' else '.png')
def main():
    if '--refresh-provenance' in sys.argv:
        report=read(OUT/'style-inventory.json')
        with zipfile.ZipFile(PACK) as pack:
            files=set(pack.namelist())
            for row in report['rows']:
                for state in row['states']:
                    for material in state['textures']:
                        origin=source_model(material['texture']);material.update(sourceTexture=origin,sourcePackContainsTexture=entry(origin,'textures') in files)
        (OUT/'style-inventory.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        return
    catalogue=read(ROOT/'src/architecture/resources/bloodborne_dw/debug_catalogue.json')
    aliases=read(ROOT/'src/architecture/resources/bloodborne_dw/imported-type-aliases.json')
    retired={r for g in aliases['groups'] for r in g['members'] if r!=g['primaryRegistryId']}
    registered={'bloodborne_dw:'+r['path'] for r in read(ROOT/'src/architecture/resources/bloodborne_dw/remaining-catalogue.json')['objects']}
    rows=[r for r in catalogue['entries'] if r['registryId'] in registered and r['registryId'] not in retired]
    assert len(rows)==572
    visuals=Visuals(vanilla_jar=VANILLA);texture_index=defaultdict(list);vanilla=zipfile.ZipFile(VANILLA);pack=zipfile.ZipFile(PACK)
    for path in vanilla.namelist():
        if path.startswith('assets/minecraft/textures/block/') and path.endswith('.png'):
            texture_index[digest(np.asarray(Image.open(io.BytesIO(vanilla.read(path))).convert('RGBA')))].append('minecraft:'+path.split('/textures/',1)[1][:-4])
    materials={};output=[]
    legacy=read(ROOT/'src/architecture/resources/bloodborne_dw/imported-stair-legacy-art.json')['states']
    legacy_by=defaultdict(list)
    for row in legacy:legacy_by[row['registryId']].append(row)
    def material(texture):
        if texture not in materials:
            image=visuals.image(texture);matches=texture_index.get(digest(image),[])
            origin=source_model(texture);source=entry(origin,'textures')
            materials[texture]={'texture':texture,'rgbaSha256':digest(image),'dimensions':list(image.shape[:2]),
              'vanillaPixelMatches':matches,'sourcePackContainsTexture':source in pack.namelist(),
              'sourceTexture':origin,'fullyTransparent':bool(not image[...,3].any())}
        return materials[texture]
    for row in sorted(rows,key=lambda r:r['temporaryId']):
        registry=row['registryId'];path=registry.split(':')[1];spec=read(ROOT/f'src/architecture/resources/bloodborne_dw/composite/{path}.json')
        poses=[]
        for variant,raw in enumerate(spec['variants']):
            for pose,data in raw['poses'].items():
                for profile in ('base','alt'):
                    parts=[dict(p,model=p.get('altModel',p['model'])) if profile=='alt' else p for p in data['parts']]
                    poses.append((str(variant)+'/'+pose+'/'+profile,parts))
        poses.extend(('legacy/'+r['key'],r['parts']) for r in legacy_by[registry])
        states=[];modelrows={}
        for selector,parts in poses:
            area=defaultdict(float);quads=[]
            for part in parts:
                mid=part['model'];sid=source_model(mid);path=entry(sid,'models')
                modelrows[mid]={'model':mid,'sourceModel':sid,'sourcePackContainsModel':path in pack.namelist(),
                  'sourceModelSha256':hashlib.sha256(pack.read(path)).hexdigest() if path in pack.namelist() else None}
                try:q=visuals.quads(part)
                except Exception as exc:raise RuntimeError((registry,selector,part)) from exc
                quads.extend(q)
                for vertices,uv,texture,tint in q:
                    measure=float(np.linalg.norm(np.cross(vertices[1]-vertices[0],vertices[2]-vertices[0]))+np.linalg.norm(np.cross(vertices[2]-vertices[0],vertices[3]-vertices[0])))/2
                    # Coverage removes invisible source planes from dominance.
                    image=visuals.image(texture);h,w=image.shape[:2];visible=[]
                    for a in np.linspace(.01,.99,8):
                        for b in np.linspace(.01,.99,8):
                            pixel=(1-a)*(1-b)*uv[0]+(1-a)*b*uv[1]+a*b*uv[2]+a*(1-b)*uv[3]
                            visible.append(image[int(pixel[1]*h/16)%h,int(pixel[0]*w/16)%w,3]/255)
                    area[texture]+=measure*float(np.mean(visible));material(texture)
            total=sum(area.values());vanilla_area=sum(v for t,v in area.items() if material(t)['vanillaPixelMatches'])
            vertices=np.asarray([q[0] for q in quads]).reshape(-1,3) if quads else np.zeros((1,3))
            states.append({'selector':selector,'visibleFaceArea':round(total,3),'vanillaSurfaceFraction':round(vanilla_area/total,5) if total else 0,
              'visualBoundsBlocks':[round(float(v)/16,5) for v in (*vertices.min(axis=0),*vertices.max(axis=0))],
              'textures':[dict(material(t),visibleArea=round(v,3),fraction=round(v/total,5) if total else 0) for t,v in sorted(area.items(),key=lambda p:-p[1])]})
        output.append({'id':row['temporaryId'],'registryId':registry,'name':row['name'],'variants':len(spec['variants']),
          'maxVanillaSurfaceFraction':max(s['vanillaSurfaceFraction'] for s in states),
          'defaultVanillaSurfaceFraction':states[0]['vanillaSurfaceFraction'],'sourceModels':list(modelrows.values()),'states':states})
    report={'schema':'dreamwalker-v125-installed-style-material-inventory-v1','baselineCommit':BASELINE,
      'scope':'ALL572POST_V12WORKINGTYPES','status':'MATERIAL_EVIDENCE_COMPLETE_VISUAL_DECISIONS_REQUIRED',
      'sourceResourcePackSha256':hashlib.sha256(PACK.read_bytes()).hexdigest(),'vanillaJarSha256':hashlib.sha256(VANILLA.read_bytes()).hexdigest(),
      'method':'Compare actual visible transformed faces and sampled alpha-weighted area. Texture equality uses decoded RGBA pixels and dimensions against vanilla1.20.1, irrespective of namespace/path/compression. Source ZIP model/texture presence is provenance, not a style decision. Includes all closed/open/base/alt and313preserved legacy stair appearance states.',
      'workingTypeCount':len(output),'typesWithAnyVanillaSurface':sum(r['maxVanillaSurfaceFraction']>0 for r in output),
      'typesWithDominantVanillaSurface':sum(r['maxVanillaSurfaceFraction']>=.5 for r in output),'rows':output}
    (OUT/'style-inventory.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    lines=[r['id']+' '+r['name']+' '+str(r['defaultVanillaSurfaceFraction'])+' max='+str(r['maxVanillaSurfaceFraction']) for r in output if r['maxVanillaSurfaceFraction']>0]
    (OUT/'style-vanilla-candidates.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8')
if __name__=='__main__':
    try:main()
    except Exception:
        (OUT/'style-inventory-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
