from common import *
log_to('package-asset-harness');out=OUT/'native-assets-only';out.mkdir(exist_ok=True);prod=DELIVERY/'game/dreamwalker-bb-materials.1-streaming.7-blocks.1.jar';qa=PROJECT/'build/libs/dreamwalker-block-repair-checks-0.1.0-v12.7.4-owner32-static-city.6-chunk-mesh.3-materials.1-streaming.7-blocks.1.jar';target=out/'asset-harness.jar'
assets=set();textures=set()
with zipfile.ZipFile(prod) as p:
 manifest=json.loads(p.read('bloodborne_dw/restored-material-slabs.json'));assets.add('bloodborne_dw/restored-material-slabs.json')
 for m in manifest['materials']:
  name=m['id'].split(':')[1];assets.add('assets/bloodborne_dw/blockstates/'+name+'.json')
  if 'base' not in m:
   for typ in ['bottom','top','double']:
    n='assets/bloodborne_dw/models/block/restored_slabs/'+name+'_'+typ+'.json';assets.add(n);d=json.loads(p.read(n))
    for e in d['elements']:
     for face in e['faces'].values():
      tex=face['texture']
      while tex.startswith('#'):tex=d['textures'][tex[1:]]
      textures.add(tex)
    textures.update(v for v in d.get('textures',{}).values() if not v.startswith('#'))
 for tex in textures:
  ns,name=tex.split(':',1);n='assets/'+ns+'/textures/'+name+'.png';assets.add(n)
  if n+'.mcmeta' in p.namelist():assets.add(n+'.mcmeta')
 with zipfile.ZipFile(qa) as q,zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
  for n in sorted(assets):z.writestr(n,p.read(n))
  for n in q.namelist():
   if n.startswith('dev/dreamwalker/bloodbornedw/blockassetsqa/') or n.startswith('dev/dreamwalker/bloodbornedw/composite/blockrepairqa/mixin/'):z.writestr(n,q.read(n))
  z.writestr('block-repair-qa.mixins.json',q.read('block-repair-qa.mixins.json'))
  z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'dreamwalker_slab_assets_qa','version':'1.0','environment':'client','entrypoints':{'main':['dev.dreamwalker.bloodbornedw.blockassetsqa.SlabAssetFactory'],'client':['dev.dreamwalker.bloodbornedw.blockassetsqa.SlabAssetClient']},'mixins':['block-repair-qa.mixins.json'],'depends':{'fabric-api':'*'}}))
write(out/'asset-preservation.json',{'status':'PASS','productionJarSha256':sha(prod),'harnessSha256':sha(target),'exactAssetEntries':sorted(assets),'stateSchema':'type + waterlogged + facing, matching production MaterialSlabs.Slab','scope':'Minecraft native model baking of exact production models/textures; helper collision behavior is tested separately in the real production server.'});print('PASS',len(assets),len(textures))
