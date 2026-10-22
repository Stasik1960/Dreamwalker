from common import *
import copy
def main():
 log_to('generate-overlays');plan=read(OUT/'world-plan.json');assert 'residentConflicts' in plan,'Refresh the exact overlay plan first';assert plan['residentConflicts']==0,plan['residentConflicts']
 doc=read(RES/'bloodborne_dw/restored-material-slabs.json');bases={m['id']:m for m in doc['materials'] if 'base' not in m};recipes=read(RES/'bloodborne_dw/static-architecture-v1274.json')['recipes']
 needed=set()
 def closure(base,recipe):
  if recipe<0 or (base,recipe) in needed:return
  assert recipes[recipe]['resident']<0,'A native helper overlay cannot replace a visible root'
  needed.add((base,recipe))
  for n in recipes[recipe]['remove']:closure(base,n)
 cells=0
 for row in read(OUT/'slab-placements.json'):
  if row.get('remainingRecipe',-1)>=0:closure(row['id'],row['remainingRecipe']);cells+=1
 materials=list(bases.values());index={}
 for base,recipe in sorted(needed):
  name='restored_slab_overlay_'+hashlib.sha256((base+'/'+str(recipe)).encode()).hexdigest()[:16];id='bloodborne_dw:'+name;row=dict(bases[base],id=id,base=base,overlayRecipe=recipe,primary=False);materials.append(row);index[base+'/'+str(recipe)]=id
  source=base.split(':')[1]
  write(RES/'assets/bloodborne_dw/blockstates'/(name+'.json'),read(RES/'assets/bloodborne_dw/blockstates'/(source+'.json')))
  write(RES/'assets/bloodborne_dw/models/item'/(name+'.json'),{'parent':'bloodborne_dw:block/restored_slabs/'+source+'_bottom'})
 for locale in ['ru_ru','en_us']:
  p=RES/'assets/bloodborne_dw/lang'/(locale+'.json');d=read(p)
  for m in materials:d['block.bloodborne_dw.'+m['id'].split(':')[1]]=m['name']
  write(p,d)
 doc['materials']=materials;write(RES/'bloodborne_dw/restored-material-slabs.json',doc)
 write(OUT/'overlay-index.json',index);write(OUT/'overlay-report.json',{'status':'PASS','cells':cells,'variantsIncludingRemovalClosure':len(needed),'residentConflicts':0,'policy':'Only physical helper fragments share the slab cell. Original model roots stay at their exact positions. No block entities or per-position mod registry are added.'})
 print('PASS',cells,len(needed))
if __name__=='__main__':main()
