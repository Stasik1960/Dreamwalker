"""Independently compare final imported source assets, dependency closure and UV-lock wrappers.

Read-only toward source ZIPs and implementation. Writes one requested QA report.
Run through CodexControl/Guard.ps1 -Mode Operation after the importer has finished.
"""
from __future__ import annotations
import argparse
import copy
import hashlib
import itertools
import json
import struct
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / 'docs/remaining-transfer'
RES = ROOT / 'src/architecture/resources'
PACK = ROOT.parent / 'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16/bbmc_v15_resource.zip'
VANILLA = Path.home() / '.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'
FRAMES = {
    'west': ((-1,0,0),(0,16,0),(0,0,1),(0,-1,0)),
    'east': ((1,0,0),(16,16,16),(0,0,-1),(0,-1,0)),
    'down': ((0,-1,0),(0,0,16),(1,0,0),(0,0,-1)),
    'up': ((0,1,0),(0,16,0),(1,0,0),(0,0,1)),
    'north': ((0,0,-1),(16,16,0),(-1,0,0),(0,-1,0)),
    'south': ((0,0,1),(0,16,16),(1,0,0),(0,-1,0)),
}

def digest(data): return hashlib.sha256(data).hexdigest()
def canonical(value): return value if ':' in value or value.startswith(('#','builtin/')) else 'minecraft:'+value
def unmap(value):
    for prefix in ('bloodborne_dw:block/remaining/source/','bloodborne_dw:remaining/source/'):
        if value.startswith(prefix):
            namespace,local=value[len(prefix):].split('/',1)
            return namespace+':'+local
    return canonical(value)
def normalize(model):
    value=copy.deepcopy(model)
    if 'parent' in value:value['parent']=unmap(value['parent'])
    if 'textures' in value:value['textures']={key:unmap(ref) for key,ref in value['textures'].items()}
    for override in value.get('overrides',[]):
        if 'model' in override:override['model']=unmap(override['model'])
    for element in value.get('elements',[]):
        for face in element.get('faces',{}).values():
            if 'texture' in face:face['texture']=unmap(face['texture'])
    return value
def dot(a,b):return sum(x*y for x,y in zip(a,b))
def rotate(point,x,y,vector=False):
    value=tuple(point)
    for _ in range((int(x)//90)%4):
        a,b,c=value;value=(a,c,-b if vector else 16-b)
    for _ in range((int(y)//90)%4):
        a,b,c=value;value=(-c if vector else 16-c,b,a)
    return value
def uv_transform(side,x,y,uv):
    normal,origin,u,v=FRAMES[side]
    destination=next(frame for frame in FRAMES.values() if frame[0]==rotate(normal,x,y,True))
    point=tuple(origin[i]+uv[0]*u[i]+uv[1]*v[i] for i in range(3))
    moved=rotate(point,x,y);relative=tuple(moved[i]-destination[1][i] for i in range(3))
    return dot(relative,destination[2]),dot(relative,destination[3])
def expected_uv(face,side,element,x,y):
    # Default rectangle follows projection onto the six vanilla face frames.
    if 'uv' in face:uv=face['uv']
    else:
        origin,u,v=FRAMES[side][1:]
        corners=list(itertools.product(*zip(element['from'],element['to'])))
        values=[(dot(tuple(p[i]-origin[i] for i in range(3)),u),dot(tuple(p[i]-origin[i] for i in range(3)),v)) for p in corners]
        uv=[min(a for a,b in values),min(b for a,b in values),max(a for a,b in values),max(b for a,b in values)]
    a=list(uv_transform(side,x,y,uv[:2]));b=list(uv_transform(side,x,y,uv[2:]))
    sign=lambda n:(n>1e-8)-(n<-1e-8)
    for axis in (0,1):
        if sign(uv[axis+2]-uv[axis])!=sign(b[axis]-a[axis]):a[axis],b[axis]=b[axis],a[axis]
    base=uv_transform(side,x,y,(0,0));U=uv_transform(side,x,y,(1,0));V=uv_transform(side,x,y,(0,1))
    du=tuple(U[i]-base[i] for i in range(2));dv=tuple(V[i]-base[i] for i in range(2))
    cosine,sine=((1,0),(0,1),(-1,0),(0,-1))[(int(face.get('rotation',0))//90)%4]
    direction=tuple(round(du[i]*cosine+dv[i]*sine) for i in range(2))
    angle={(1,0):0,(0,1):90,(-1,0):180,(0,-1):270}[direction]
    return [round(n,8) for n in a+b],(-angle)%360

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path)
    parser.add_argument('--report',type=Path,default=DOC/'artifact-validation.json')
    args=parser.parse_args()
    manifest=json.loads((DOC/'imported-resources.json').read_text(encoding='utf-8'))
    progress=json.loads((DOC/'import-progress.json').read_text(encoding='utf-8'))
    if progress.get('status')!='COMPLETE':raise ValueError('Importer is still running; final artifacts are not frozen')
    failures=[];counts={};source_cache={};resolved_cache={};target_resolved={};rootless=[];heaviest=[];particle_defects={};emissive_uses={}
    for obj in manifest['objects']:
        descriptor=json.loads((RES/'bloodborne_dw/composite'/(obj['path']+'.json')).read_text(encoding='utf-8'))
        for variant in descriptor['variants']:
            for pose_name,pose in variant['poses'].items():
                applications=variant.get('sourceOpenApplications' if pose_name=='open' else 'sourceClosedApplications',[])
                application_index=-1;previous=None
                for part in pose['parts']:
                    if part['model'].startswith('bloodborne_dw:remaining/source_emissive/'):
                        if previous is None or application_index>=len(applications):
                            failures.append(obj['registryId']+': emissive overlay has no preceding source application')
                            continue
                        if {k:v for k,v in previous.items() if k not in ('model','altModel','emissive')}!={k:v for k,v in part.items() if k not in ('model','altModel','emissive')}:
                            failures.append(obj['registryId']+': emissive overlay transform differs from its base part')
                        emissive_uses.setdefault(part['model'],[]).append(applications[application_index])
                    else:
                        application_index+=1;previous=part
                if application_index+1!=len(applications):failures.append(obj['registryId']+': base part count differs from recorded source applications')
    with zipfile.ZipFile(PACK) as pack,zipfile.ZipFile(VANILLA) as vanilla:
        pack_prefix=next(name[:-len('pack.mcmeta')] for name in pack.namelist() if name.endswith('pack.mcmeta'))
        names=set(pack.namelist());vanilla_names=set(vanilla.namelist())
        emissive_properties=pack.read(pack_prefix+'assets/minecraft/optifine/emissive.properties').decode('utf-8-sig')
        emissive_suffix=next((line.split('=',1)[1].strip() for line in emissive_properties.splitlines() if line.strip().startswith('suffix.emissive=')),None)
        if emissive_suffix!='_e':failures.append('Source emissive suffix differs from overlay contract')
        def original(path):return pack.read(pack_prefix+path) if pack_prefix+path in names else vanilla.read(path)
        def source_model(path):
            if path not in source_cache:source_cache[path]=json.loads(original(path))
            return source_cache[path]
        def resolved_source(path,chain=()):
            if path in chain:raise ValueError('Source parent cycle: '+path)
            if path in resolved_cache:return resolved_cache[path]
            raw=source_model(path);value={};parent=raw.get('parent','')
            if parent and not parent.startswith('builtin/'):
                namespace,local=canonical(parent).split(':',1);value=copy.deepcopy(resolved_source('assets/'+namespace+'/models/'+local+'.json',chain+(path,)))
            textures={**value.get('textures',{}),**raw.get('textures',{})};value.update(copy.deepcopy(raw));value['textures']=textures;resolved_cache[path]=value;return value
        def target_model(id,chain=()):
            id=canonical(id)
            if id in chain:raise ValueError('Target parent cycle: '+id)
            if id in target_resolved:return target_resolved[id]
            namespace,local=id.split(':',1);path='assets/'+namespace+'/models/'+local+'.json'
            file=RES/path;raw=json.loads(file.read_text(encoding='utf-8')) if file.is_file() else json.loads(vanilla.read(path))
            value={};parent=raw.get('parent','')
            if parent and not parent.startswith('builtin/'):value=copy.deepcopy(target_model(parent,chain+(id,)))
            textures={**value.get('textures',{}),**raw.get('textures',{})};value.update(copy.deepcopy(raw));value['textures']=textures;target_resolved[id]=value;return value
        for row in manifest['resources']:
            kind=row['kind'];counts[kind]=counts.get(kind,0)+1
            path=RES/row['file']
            try:
                data=path.read_bytes()
                if digest(data)!=row['sha256']:raise ValueError('Manifest target hash differs')
                if kind in ('texture','texture_metadata'):
                    if data!=original(row['source']):raise ValueError('PNG/animation metadata bytes differ from source')
                    if kind=='texture' and row.get('role')=='source_colormap':
                        if row['source']!='assets/minecraft/textures/colormap/grass.png':raise ValueError('Unconfirmed authored colormap source')
                        if data[:8]!=b'\x89PNG\r\n\x1a\n' or struct.unpack('>II',data[16:24])!=(256,256):raise ValueError('Authored grass palette is not an exact 256x256 PNG')
                        if row.get('source_sha256',digest(data))!=digest(original(row['source'])):raise ValueError('Authored colormap source SHA mismatch')
                elif kind=='model':
                    raw=original(row['source'])
                    if digest(raw)!=row['source_sha256']:raise ValueError('Source model provenance hash differs')
                    if normalize(json.loads(data))!=normalize(json.loads(raw)):raise ValueError('Source geometry/UV/faces/rotations/display differ beyond dependency namespace')
                elif kind=='uvlock_model':
                    wrapper=json.loads(data);source=resolved_source(row['source']);application=wrapper['sourceUvlockApplication'];x,y=application['x'],application['y']
                    if x%90 or y%90 or not application['uvlock']:raise ValueError('Unsupported UV-lock cardinal transform')
                    actual=normalize(wrapper)['elements'];expected=normalize(source)['elements']
                    if len(actual)!=len(expected):raise ValueError('UV-lock wrapper element count differs')
                    for old,new in zip(expected,actual):
                        old_geometry={k:v for k,v in old.items() if k!='faces'};new_geometry={k:v for k,v in new.items() if k!='faces'}
                        if old_geometry!=new_geometry or old.get('faces',{}).keys()!=new.get('faces',{}).keys():raise ValueError('UV-lock wrapper changes geometry/faces')
                        for side,face in old.get('faces',{}).items():
                            copied=new['faces'][side];uv,angle=expected_uv(face,side,old,x,y)
                            if copied.get('uv')!=uv or copied.get('rotation',0)!=angle:raise ValueError('UV-lock face differs from independent cardinal face-frame transform: '+side)
                            if {k:v for k,v in face.items() if k not in ('uv','rotation')}!={k:v for k,v in copied.items() if k not in ('uv','rotation')}:raise ValueError('UV-lock wrapper alters face texture/culling/tint')
                elif kind=='emissive_model':
                    overlay=normalize(json.loads(data));source=normalize(resolved_source(row['source']))
                    overlay_textures={**source.get('textures',{}),**overlay.get('textures',{})}
                    used_overlay_aliases=set()
                    for element in overlay['elements']:
                        for face in element.get('faces',{}).values():
                            ref=face['texture'];seen=set()
                            while ref.startswith('#'):
                                alias=ref[1:]
                                if alias in seen or alias not in overlay_textures:raise ValueError('Emissive texture alias is unresolved')
                                seen.add(alias);used_overlay_aliases.add(alias);ref=overlay_textures[alias]
                            face['texture']=canonical(ref)
                    if set(overlay.get('textures',{}))!=used_overlay_aliases:raise ValueError('Emissive overlay texture aliases differ from exact used mask dependencies')
                    source_id=row['source'][len('assets/'):].replace('/models/',':')[:-len('.json')]
                    overlay_id='bloodborne_dw:'+row['file'].split('/models/',1)[1][:-len('.json')]
                    uses=emissive_uses.get(overlay_id,[])
                    if not uses:raise ValueError('Emissive overlay is not used by any source application')
                    if overlay['parent']!=source_id:raise ValueError('Emissive overlay parent differs from exact source model')
                    textures=source.get('textures',{})
                    def resolve_texture(ref):
                        seen=set()
                        while ref.startswith('#'):
                            alias=ref[1:]
                            if alias in seen or alias not in textures:raise ValueError('Source emissive face texture alias unresolved')
                            seen.add(alias);ref=textures[alias]
                        return canonical(ref)
                    for application in uses:
                        if canonical(application['model'])!=source_id:raise ValueError('Emissive overlay application source differs')
                        expected=[];masks=set()
                        for original_element in source.get('elements',[]):
                            element=copy.deepcopy(original_element);faces={}
                            for side,original_face in original_element.get('faces',{}).items():
                                texture=resolve_texture(original_face['texture']);namespace,local=texture.split(':',1)
                                mask_path='assets/'+namespace+'/textures/'+local+emissive_suffix+'.png'
                                if pack_prefix+mask_path not in names:continue
                                face=copy.deepcopy(original_face)
                                if application.get('uvlock'):
                                    uv,angle=expected_uv(face,side,original_element,application.get('x',0),application.get('y',0))
                                    face['uv']=uv
                                    if angle:face['rotation']=angle
                                    else:face.pop('rotation',None)
                                face['texture']=namespace+':'+local+emissive_suffix;faces[side]=face;masks.add(mask_path)
                            if faces:
                                element['faces']=faces;element['shade']=False;expected.append(element)
                        if overlay['elements']!=expected:raise ValueError('Emissive overlay changes source geometry/UV/face selection beyond declared mask and shade')
                        if sorted(masks)!=overlay.get('sourceEmissiveMasks') or sorted(masks)!=row.get('masks'):raise ValueError('Emissive mask provenance differs from exact face dependencies')
            except Exception as error:failures.append(row['file']+': '+str(error))
        for object in manifest['objects']:
            path=RES/'bloodborne_dw/composite'/ (object['path']+'.json');spec=json.loads(path.read_text(encoding='utf-8'))
            for index,variant in enumerate(spec['variants']):
                selection=variant['poses']['closed']['selection'];heaviest.append({'id':spec['id'],'variant':index,'selectionBoxes':len(selection),'collisionBoxes':len(variant['poses']['closed']['collision'])})
                if not any(all(box['from'][i]<16 and box['to'][i]>0 for i in range(3)) for box in selection):rootless.append({'id':spec['id'],'variant':index,'visibleBounds':variant['visibleBounds']})
                for pose in variant['poses'].values():
                    for part in pose['parts']:
                        for id in set((part['model'],part.get('altModel',part['model']))):
                            try:
                                model=target_model(id);textures=model.get('textures',{})
                                particle=textures.get('particle')
                                if particle and not particle.startswith('#'):
                                    particle_namespace,particle_local=canonical(particle).split(':',1)
                                    particle_path='assets/'+particle_namespace+'/textures/'+particle_local+'.png'
                                    if not (RES/particle_path).is_file() and particle_path not in vanilla_names:
                                        particle_defects[id]={'model':id,'declaredParticle':particle,'status':'SOURCE_PARTICLE_DEFECT_PRESERVED','policy':'Visible faces checked separately; no particle substitute invented; raw source comparison must still pass.'}
                                for element in model.get('elements',[]):
                                    for face in element.get('faces',{}).values():
                                        ref=face['texture'];seen=set()
                                        while ref.startswith('#'):
                                            alias=ref[1:]
                                            if alias in seen or alias not in textures:raise ValueError('Unresolved texture alias '+ref)
                                            seen.add(alias);ref=textures[alias]
                                        namespace,local=canonical(ref).split(':',1);texture='assets/'+namespace+'/textures/'+local+'.png'
                                        if not (RES/texture).is_file() and texture not in vanilla_names:raise ValueError('Missing texture '+ref)
                            except Exception as error:failures.append(spec['id']+' variant='+str(index)+' part='+id+': '+str(error))
        pack_sha=digest(PACK.read_bytes())
        if pack_sha!=manifest['packSha256']:failures.append('Source ZIP SHA differs from import provenance')
    jar_sha=None
    if args.jar:
        with zipfile.ZipFile(args.jar) as jar:
            for row in manifest['resources']:
                try:
                    if digest(jar.read(row['file']))!=row['sha256']:failures.append('JAR target differs: '+row['file'])
                except KeyError:failures.append('JAR target absent: '+row['file'])
        jar_sha=digest(args.jar.read_bytes())
    result={'schema':'dreamwalker-independent-source-artifact-validation-v1','status':'FAIL' if failures else 'PASS','packSha256':pack_sha,'productionJarSha256':jar_sha,'resourcesByKind':counts,
            'objects':len(manifest['objects']),'variants':sum(obj['variantCount'] for obj in manifest['objects']),'possibleNewBlockStates':len(manifest['objects'])*512,
            'knownUnusedParticleDefects':list(particle_defects.values()),
            'noSelectionInReservedRootCell':rootless,'heaviestSelection':sorted(heaviest,key=lambda row:row['selectionBoxes'],reverse=True)[:10],
            'failures':failures,'scope':'Exact raw source JSON/PNG/metadata preservation after namespace normalization, independent cardinal UV-lock face frames, all imported part texture/parent closure. Rootless selection and eager state count are reported risks, not native gameplay PASS.'}
    args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':result['status'],'resources':sum(counts.values()),'objects':result['objects'],'variants':result['variants'],'rootlessReservedCells':len(rootless),'failures':len(failures)},ensure_ascii=False))
    if failures:raise SystemExit(1)

if __name__=='__main__':main()
