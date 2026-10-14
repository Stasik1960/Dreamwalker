"""Rebind the saved gallery after a verified client-only section-redraw callback addition."""
from pathlib import Path
import hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'build/final-followups-20261014'
old=ROOT/'build/runtime-client-final-followups-specific-final-full9/mods/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.3-final-checks.jar'
new=ROOT/'build/libs'/old.name
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(old)=='2704af8afa851e15433347174e63a57c74a6e0d45bb37b3e9370bb1e0d28e777'
addition='dev/dreamwalker/bloodbornedw/composite/mixin/CompositeMeshRenderSectionMixin.class'
with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as b:
 assert set(b.namelist())==set(a.namelist())|{addition}
 changed=[name for name in a.namelist() if a.read(name)!=b.read(name)]
 assert set(changed)=={'bloodborne_dw.composite.mixins.json','dreamwalker-bb-fabric-1.20.1-refmap.json'},changed
 before=json.loads(a.read(changed[0]));after=json.loads(b.read(changed[0]));before['client'].insert(before['client'].index('CompositeMeshLightQueueMixin')+1,'CompositeMeshRenderSectionMixin')
 assert before==after
 before=json.loads(a.read('dreamwalker-bb-fabric-1.20.1-refmap.json'));after=json.loads(b.read('dreamwalker-bb-fabric-1.20.1-refmap.json'));removed=[]
 def remove_new_reference(value):
  if isinstance(value,dict):
   for key in list(value):
    if key==addition[:-6]:removed.append(value.pop(key))
    else:remove_new_reference(value[key])
  elif isinstance(value,list):
   for child in value:remove_new_reference(child)
 remove_new_reference(after);assert before==after and removed and all(set(row)=={'scheduleBlockRender(III)V'} for row in removed),('Unreviewed reference map delta',removed)
previous=OUT/'last-client-only-delta.json';normal=OUT/'normal-reuse-only-delta.json'
if not normal.exists():normal.write_bytes(previous.read_bytes())
record={'status':'PASS_EXACT_CLIENT_RENDER_SECTION_CALLBACK_ONLY_DELTA','beforeJarSha256':sha(old),'productionJarSha256':sha(new),'addedClientClass':addition,'clientMixinRegistrationAddedOnly':True,'allExistingNativeClassesByteIdentical':True,'allServerClassesAllModelsTexturesAndDescriptorsByteIdentical':True,'worldBlocksEntitiesNbtAndNavigationUnmodified':True,'galleryProductionBindingUpdatedOnly':True,'prior151ServerTestsAnd550InventoryPixelsRemainApplicable':True}
previous.write_text(json.dumps(record,indent=2)+'\n',encoding='utf8')
for path in [ROOT/'build/v1273-gallery-delivery/World/gallery-manifest.json',ROOT/'build/v1273-gallery-delivery/World/README-RU.txt',ROOT/'build/v1273-gallery/input-final-client.json']:
 if not path.exists():continue
 text=path.read_text(encoding='utf-8-sig')
 if sha(new) in text:continue
 prior=sha(old)
 if path.name=='input-final-client.json' and prior not in text:
  prior=json.loads(normal.read_text(encoding='utf8'))['beforeJarSha256']
 assert prior in text,str(path);path.write_text(text.replace(prior,sha(new)),encoding='utf8')
