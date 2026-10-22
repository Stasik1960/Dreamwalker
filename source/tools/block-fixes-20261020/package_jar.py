from common import *
import shutil,difflib,subprocess
def main():
 log_to('package-jar')
 raw=PROJECT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-owner32-static-city.6-chunk-mesh.3-materials.1-streaming.7-blocks.1.jar'
 target=DELIVERY/'game/dreamwalker-bb-materials.1-streaming.7-blocks.1.jar';target.parent.mkdir(parents=True,exist_ok=True)
 classes=['dev/dreamwalker/bloodbornedw/DreamwalkerBb','dev/dreamwalker/bloodbornedw/composite/CompositeSpec','dev/dreamwalker/bloodbornedw/composite/CompositeSourceShift','dev/dreamwalker/bloodbornedw/composite/StaticArchitectureCatalog','dev/dreamwalker/bloodbornedw/composite/StaticArchitectureBlock','dev/dreamwalker/bloodbornedw/composite/StaticArchitectureRuntime','dev/dreamwalker/bloodbornedw/composite/StaticArchitectureWorldAccess','dev/dreamwalker/bloodbornedw/composite/CompositeItem','dev/dreamwalker/bloodbornedw/composite/CompositeRootBlock','dev/dreamwalker/bloodbornedw/composite/MaterialSlabs','dev/dreamwalker/bloodbornedw/composite/WoodFloorCollision']
 classes.append('dev/dreamwalker/bloodbornedw/composite/CompositeRuntime')
 deleted={'assets/bloodborne_dw/blockstates/source_invisible_carrier.json','assets/bloodborne_dw/blockstates/source_invisible_solid_carrier.json'}
 assets={'bloodborne_dw/restored-material-slabs.json','bloodborne_dw/wood-floor-collision.json','assets/bloodborne_dw/lang/ru_ru.json','assets/bloodborne_dw/lang/en_us.json'}
 resource_names=set(assets)
 for m in read(RES/'bloodborne_dw/restored-material-slabs.json')['materials']:
  name=m['id'].split(':')[1];resource_names.add('assets/bloodborne_dw/blockstates/'+name+'.json');resource_names.add('assets/bloodborne_dw/models/item/'+name+'.json')
  if 'base' not in m:
   for typ in ['bottom','top','double']:resource_names.add('assets/bloodborne_dw/models/block/restored_slabs/'+name+'_'+typ+'.json')
 def changed(n):return any(n==c+'.class' or n.startswith(c+'$') and n.endswith('.class') for c in classes) or n in assets or '/restored_slabs/' in n or n.startswith('assets/bloodborne_dw/blockstates/restored_slab_') or n.startswith('assets/bloodborne_dw/models/item/restored_slab_')
 with zipfile.ZipFile(BASE) as old,zipfile.ZipFile(raw) as new,zipfile.ZipFile(target.with_suffix('.pending'),'w') as result:
  names=set(old.namelist())|{n for n in new.namelist() if changed(n) and n.endswith('.class')}|resource_names
  for n in sorted(names-deleted):
   if n.endswith('/'):continue
   if n=='fabric.mod.json':
    d=json.loads(old.read(n));d['version']=json.loads(new.read(n))['version'];data=(json.dumps(d,ensure_ascii=False,indent=2)+'\n').encode('utf8');info=old.getinfo(n)
   elif n in resource_names:
    data=(RES/n).read_bytes();info=zipfile.ZipInfo(n,(2026,10,20,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
   elif changed(n):data=new.read(n);info=new.getinfo(n)
   else:data=old.read(n);info=old.getinfo(n)
   result.writestr(info,data)
 target.with_suffix('.pending').replace(target)
 with zipfile.ZipFile(BASE) as old,zipfile.ZipFile(target) as new:
  oldnames=set(old.namelist());newnames=set(new.namelist());allnames={n for n in oldnames|newnames if not n.endswith('/')};different=[n for n in sorted(allnames) if n not in oldnames or n not in newnames or old.read(n)!=new.read(n)]
  assert all(changed(n) or n in deleted or n=='fabric.mod.json' for n in different),[n for n in different if not(changed(n) or n in deleted or n=='fabric.mod.json')]
  assert not any('/blockqa/' in n or 'BlockRepairChecks' in n for n in new.namelist())
  write(OUT/'binary-preservation.json',{'base':str(BASE),'baseSha256':sha(BASE),'result':str(target),'sha256':sha(target),'changedEntries':different,'unchangedEntries':len(allnames)-len(different),'deleted':sorted(deleted),'policy':'All entries outside the listed block repairs copied byte for byte from streaming.7; no QA classes included.'})
 tested=OUT/'native-final/server/mods/dreamwalker-bb-blocks.jar'
 receipt=OUT/'native-ground-final/receipt.json'
 if receipt.exists() and read(receipt)['status']=='PASS':tested=OUT/'native-ground-final/server/mods/dreamwalker-bb-blocks.jar'
 if tested.exists():
  with zipfile.ZipFile(tested) as before,zipfile.ZipFile(target) as final:
   aa=set(before.namelist());bb=set(final.namelist());delta=[n for n in sorted(aa|bb) if n not in aa or n not in bb or before.read(n)!=final.read(n)]
   gameplay=[n for n in delta if not n.startswith('assets/bloodborne_dw/models/block/restored_slabs/')]
   if '--retest' in sys.argv:
    assert all(n.startswith('dev/dreamwalker/bloodbornedw/composite/CompositeRuntime') or n.startswith('dev/dreamwalker/bloodbornedw/composite/WoodFloorCollision') for n in gameplay),gameplay
   else:assert not gameplay,gameplay
   write(OUT/'final-mechanics-equivalence.json',{'status':'RETEST_REQUIRED' if gameplay else 'PASS','testedJarSha256':sha(tested),'finalJarSha256':sha(target),'allClassesAndGameplayDataIdentical':not gameplay,'changesAfterMechanicsTests':delta,'policy':'Gameplay class differences require a fresh native mechanics test. Asset byte equivalence with the native client harness is verified separately.'})
 print('PASS',target,sha(target))
if __name__=='__main__':main()
