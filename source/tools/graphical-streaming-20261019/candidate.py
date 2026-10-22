import difflib,zipfile
from common import *
out=ROOT/'deliverables/streaming-7-20261020';(out/'game').mkdir(parents=True,exist_ok=True);(out/'sources').mkdir(exist_ok=True)
version=next(line.split('=',1)[1].strip() for line in (PROJECT/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
raw=PROJECT/'build/libs'/('dreamwalker-bb-fabric-1.20.1-'+version+'.jar');assert raw.is_file()
receipt=json.loads((OUT/'build-production.json').read_text());assert receipt['status']=='PASS' and receipt['sourceHashes']=={name:sha(PROJECT/name) for name in PRODUCTION_INPUTS},'Rebuild edited production sources before freezing'
target=out/'game/dreamwalker-bb-materials.1-streaming.7.jar';assert not target.exists()
prefix='dev/dreamwalker/bloodbornedw/composite/'
def allowed(name):return name in ['fabric.mod.json','bloodborne_dw.composite.mixins.json','dreamwalker-bb-fabric-1.20.1-refmap.json',prefix+'CompositeBlockEntity.class',prefix+'CompositeNetworking.class',prefix+'ShardedCompositeLedger.class',prefix+'mixin/ShardedLedgerTrackingMixin.class',prefix+'mixin/LithiumPaletteAllocationMixin.class'] or (name.startswith(prefix+'CompositeClient') or name.startswith(prefix+'CompositeInitialSync') or name.startswith(prefix+'MembershipNbtReader')) and name.endswith('.class')
def unaffected_mappings(obj):
 if isinstance(obj,dict):return {k:unaffected_mappings(v) for k,v in obj.items() if 'ShardedLedgerTrackingMixin' not in k and 'LithiumPaletteAllocationMixin' not in k}
 return obj
with zipfile.ZipFile(BASE) as old,zipfile.ZipFile(raw) as new:
 assert unaffected_mappings(json.loads(old.read('dreamwalker-bb-fabric-1.20.1-refmap.json')))==unaffected_mappings(json.loads(new.read('dreamwalker-bb-fabric-1.20.1-refmap.json'))),'Unrelated mixin mapping changed'
with zipfile.ZipFile(BASE) as old,zipfile.ZipFile(raw) as new,zipfile.ZipFile(target,'x') as result:
 for info in old.infolist():result.writestr(new.getinfo(info.filename) if allowed(info.filename) else info,new.read(info.filename) if allowed(info.filename) else old.read(info.filename))
 for info in new.infolist():
  if info.filename not in old.namelist() and allowed(info.filename):result.writestr(info,new.read(info.filename))
with zipfile.ZipFile(BASE) as old,zipfile.ZipFile(target) as new:
 names=set(old.namelist())|set(new.namelist());changed=[n for n in sorted(names) if n not in old.namelist() or n not in new.namelist() or old.read(n)!=new.read(n)]
 assert all(allowed(n) for n in changed),changed
 assert prefix+'mixin/LithiumPaletteAllocationMixin.class' in changed
 write(out/'binary-preservation.json',{'baseJar':str(BASE),'baseSha256':sha(BASE),'rawBuildJar':str(raw),'rawBuildSha256':sha(raw),'productionJar':str(target),'sha256':sha(target),'changedEntries':changed,'unchangedEntries':len(names)-len(changed),'policy':'All other classes/resources copied byte-for-byte from the accepted streaming.6 JAR; no QA classes.'})
paths=PRODUCTION_INPUTS
patch=[]
with zipfile.ZipFile(out/'sources/changed-sources.zip','x',zipfile.ZIP_DEFLATED) as archive:
 for path in paths:
  p=PROJECT/path;before=OUT/'source-before'/p.name;old=before.read_text(encoding='utf-8-sig').splitlines(keepends=True) if before.exists() else []
  current=p.read_text(encoding='utf-8-sig').splitlines(keepends=True);patch.extend(difflib.unified_diff(old,current,fromfile='a/bloodborne complete/'+path if old else '/dev/null',tofile='b/bloodborne complete/'+path))
  archive.write(p,'bloodborne complete/'+path)
(out/'sources/changes-from-streaming.6.patch').write_text(''.join(line if line.endswith('\n') else line+'\n\\ No newline at end of file\n' for line in patch),encoding='utf-8',newline='\n')
write(OUT/'candidate.json',{'jar':str(target),'sha256':sha(target),'binaryPreservation':str(out/'binary-preservation.json')})
