"""Read-only independent alpha proof for three withdrawn invisible source helpers."""
from __future__ import annotations
import copy
import hashlib
import io
import json
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[1]
DOC=ROOT/'docs/remaining-transfer'
PACK=ROOT.parent/'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16/bbmc_v15_resource.zip'
VANILLA=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'
HELPERS={'source_glass':['minecraft:block/glass'],'source_ladder':['minecraft:block/ladder'],
         'source_assembly_glass_pane':['minecraft:block/'+name for name in ('glass_pane_post','glass_pane_side','glass_pane_side_alt','glass_pane_noside','glass_pane_noside_alt')]}

def canonical(ref):return ref if ':' in ref else 'minecraft:'+ref
def sha(data):return hashlib.sha256(data).hexdigest()

def main():
    from PIL import Image
    failures=[];model_evidence=[];texture_evidence={};cache={}
    with zipfile.ZipFile(PACK) as pack,zipfile.ZipFile(VANILLA) as vanilla:
        prefix=next(name[:-len('pack.mcmeta')] for name in pack.namelist() if name.endswith('pack.mcmeta'));names=set(pack.namelist())
        def raw(path):
            if prefix+path in names:return pack.read(prefix+path),'SOURCE_PACK',prefix+path
            return vanilla.read(path),'VANILLA_PARENT',path
        def model(id,chain=()):
            id=canonical(id)
            if id in chain:raise ValueError('Parent cycle')
            if id in cache:return cache[id]
            namespace,local=id.split(':',1);data,origin,entry=raw('assets/'+namespace+'/models/'+local+'.json');document=json.loads(data)
            value={};parent=document.get('parent','')
            if parent and not parent.startswith('builtin/'):value=copy.deepcopy(model(parent,chain+(id,)))
            textures={**value.get('textures',{}),**document.get('textures',{})};value.update(document);value['textures']=textures
            cache[id]=value
            return value
        for path,models in HELPERS.items():
            for id in models:
                try:
                    resolved=model(id);faces=0;used=set()
                    for element in resolved.get('elements',[]):
                        for face in element.get('faces',{}).values():
                            faces+=1;ref=face['texture'];seen=set()
                            while ref.startswith('#'):
                                alias=ref[1:]
                                if alias in seen or alias not in resolved['textures']:raise ValueError('Unresolved visible-face texture alias')
                                seen.add(alias);ref=resolved['textures'][alias]
                            ref=canonical(ref);used.add(ref)
                            if ref not in texture_evidence:
                                namespace,local=ref.split(':',1);data,origin,entry=raw('assets/'+namespace+'/textures/'+local+'.png')
                                with Image.open(io.BytesIO(data)) as source:
                                    rgba=source.convert('RGBA');extrema=rgba.getchannel('A').getextrema()
                                    texture_evidence[ref]={'texture':ref,'entry':entry,'origin':origin,'sha256':sha(data),'size':list(rgba.size),'wholeImageAlphaExtrema':list(extrema)}
                            if texture_evidence[ref]['wholeImageAlphaExtrema']!=[0,0]:raise ValueError('Visible source face has nonzero alpha: '+ref)
                    if not faces:raise ValueError('No faces; cannot classify as all-alpha-zero visual')
                    model_evidence.append({'path':path,'model':id,'faces':faces,'visibleTextures':sorted(used),'status':'ALL_VISIBLE_FACE_PIXELS_ALPHA_ZERO'})
                except Exception as error:failures.append({'model':id,'error':str(error)})
    catalogue=json.loads((ROOT/'src/architecture/resources/bloodborne_dw/remaining-catalogue.json').read_text(encoding='utf-8'))
    offered={obj['path'] for obj in catalogue['objects']}
    for path in HELPERS:
        if path in offered:failures.append({'path':path,'error':'All-transparent helper remains in final active catalogue'})
    result={'schema':'dreamwalker-independent-transparent-helper-proof-v1','status':'FAIL' if failures else 'PASS','packSha256':sha(PACK.read_bytes()),'withdrawnObjects':list(HELPERS),'withdrawnStates':9,
            'models':model_evidence,'textures':list(texture_evidence.values()),'policy':'Only source-declared fully transparent PNG faces; no replacement texture or geometry invented. Five-digit number reservations are audited by the catalogue planner separately.','failures':failures}
    (DOC/'transparent-helper-validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':result['status'],'models':len(model_evidence),'textures':len(texture_evidence),'failures':len(failures)}))
    if failures:raise SystemExit(1)

if __name__=='__main__':
    try:main()
    except Exception:
        import traceback
        (DOC/'transparent-helper-error.log').write_text(traceback.format_exc(),encoding='utf-8')
        raise
