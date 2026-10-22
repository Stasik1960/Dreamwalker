from common import *

def main():
 log_to('package-jar');assert read(OUT/'build.json')['status']=='PASS'
 raw=PROJECT/'build/libs'/('dreamwalker-bb-fabric-1.20.1-'+VERSION+'.jar');target=DELIVERY/'game'/GAME;target.parent.mkdir(parents=True,exist_ok=True)
 classes=['dev/dreamwalker/bloodbornedw/composite/MaterialSlabs','dev/dreamwalker/bloodbornedw/composite/MaterialSlabClient']
 resources={'bloodborne_dw/restored-material-slabs.json','assets/bloodborne_dw/lang/ru_ru.json','assets/bloodborne_dw/lang/en_us.json'}
 for m in read(RES/'bloodborne_dw/restored-material-slabs.json')['materials']:
  name=m['id'].split(':')[1];resources.add('assets/bloodborne_dw/blockstates/'+name+'.json');resources.add('assets/bloodborne_dw/models/item/'+name+'.json')
  if 'base' not in m:
   for typ in ['bottom','top','double']:resources.add('assets/bloodborne_dw/models/block/restored_slabs/'+name+'_'+typ+'.json')
 def allowed(n):return any(n==c+'.class' or n.startswith(c+'$') and n.endswith('.class') for c in classes)
 with zipfile.ZipFile(BASE) as old,zipfile.ZipFile(raw) as built,zipfile.ZipFile(target.with_suffix('.pending'),'w') as result:
  names=set(old.namelist())|{n for n in built.namelist() if allowed(n)}|resources
  for n in sorted(names):
   if n.endswith('/'):continue
   if n=='fabric.mod.json':
    d=json.loads(old.read(n));d['version']=VERSION;entry='dev.dreamwalker.bloodbornedw.composite.MaterialSlabClient';assert entry not in d['entrypoints']['client'];d['entrypoints']['client'].append(entry);data=(json.dumps(d,ensure_ascii=False,indent=2)+'\n').encode('utf8');info=old.getinfo(n)
   elif n in resources:data=(RES/n).read_bytes();info=zipfile.ZipInfo(n,(2026,10,20,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
   elif allowed(n):data=built.read(n);info=built.getinfo(n)
   else:data=old.read(n);info=old.getinfo(n)
   result.writestr(info,data)
 target.with_suffix('.pending').replace(target)
 with zipfile.ZipFile(BASE) as old,zipfile.ZipFile(target) as final:
  a=set(old.namelist());b=set(final.namelist());changed=[n for n in sorted(a|b) if n not in a or n not in b or old.read(n)!=final.read(n)]
  assert all(allowed(n) or n in resources or n=='fabric.mod.json' for n in changed),changed
  assert not any('/blockassetsqa/' in n or 'PavementMapChecks' in n or 'BlockRepairChecks' in n for n in b)
 write(OUT/'binary-preservation.json',{'status':'PASS','baseJar':str(BASE),'baseSha256':sha(BASE),'jar':str(target),'sha256':sha(target),'changedEntries':changed,'policy':'Every entry outside MaterialSlabs, the new cutout-layer initializer, material manifest and slab assets is copied byte-for-byte from blocks.1.'})
 print('PASS',sha(target),len(changed),'changed entries')
if __name__=='__main__':main()
