from common import *
import copy

def main():
 log_to('prepare-pavement');manifest_path=RES/'bloodborne_dw/restored-material-slabs.json';backup(manifest_path)
 with zipfile.ZipFile(BASE) as z:manifest=json.loads(z.read('bloodborne_dw/restored-material-slabs.json'))
 existing={(m['source'],m['variant']) for m in manifest['materials'] if 'base' not in m};sources=read(PREVIOUS_OUT/'pavement-family-remaining.json');rows=[]
 base='restored_slab_c66444efa072e824'
 for r in sources:
  assert (r['source'],r['variant']) not in existing
  parts=r['parts'];assert len(parts)==1 and not any(parts[0].get(k,0) for k in ['yaw','pitch','extraYaw']),parts
  source=model(parts[0]['model']);top=all('type=top' in s for s in r['sourceRefs']) if r['sourceRefs'] else r['source'].endswith('_top')
  canonical=16 if top else 8
  name='restored_slab_decor_'+hashlib.sha256((r['source']+'/'+str(r['variant'])).encode()).hexdigest()[:16]
  new=dict(id='bloodborne_dw:'+name,source=r['source'],variant=r['variant'],cell=[0,0],name=r['name']+' · плита с основанием',primary=True,decorated=True,canonicalSurface=canonical/16,sourceModel=parts[0]['model'],sourceOffset=parts[0].get('offset',[0,0,0]))
  rows.append(new)
  for typ,surface in [('bottom',8),('top',16),('double',16)]:
   result=read(RES/'assets/bloodborne_dw/models/block/restored_slabs'/(base+'_'+typ+'.json'));result=copy.deepcopy(result)
   shift=surface-canonical
   for index,e in enumerate(source['elements']):
    e=copy.deepcopy(e)
    for key in ['from','to']:e[key][1]+=shift
    if 'rotation' in e:e['rotation']['origin'][1]+=shift
    for face in e['faces'].values():
     texture=face['texture'];seen=set()
     while texture.startswith('#'):
      assert texture not in seen;seen.add(texture);texture=source['textures'][texture[1:]]
     alias='decor_'+str(len(result['textures']));result['textures'][alias]=texture;face['texture']='#'+alias;face.pop('cullface',None)
    assert all(-16-1e-6<=v<=32+1e-6 for key in ['from','to'] for v in e[key]),(r['source'],typ,e)
    result['elements'].append(e)
   write(RES/'assets/bloodborne_dw/models/block/restored_slabs'/(name+'_'+typ+'.json'),result)
  variants={}
  for typ in ['bottom','top','double']:
   for facing,yaw in [('north',0),('east',90),('south',180),('west',270)]:variants['facing='+facing+',type='+typ]={'model':'bloodborne_dw:block/restored_slabs/'+name+'_'+typ,**({'y':yaw} if yaw else {})}
  write(RES/'assets/bloodborne_dw/blockstates'/(name+'.json'),{'variants':variants})
  write(RES/'assets/bloodborne_dw/models/item'/(name+'.json'),{'parent':'bloodborne_dw:block/restored_slabs/'+name+'_bottom'})
 manifest['materials']+=rows;write(manifest_path,manifest)
 for locale in ['ru_ru','en_us']:
  p=RES/'assets/bloodborne_dw/lang'/(locale+'.json');backup(p);d=read(p)
  for r in rows:d['block.bloodborne_dw.'+r['id'].split(':')[1]]=r['name']
  write(p,d)
 write(OUT/'new-pavement-materials.json',rows)
 for r in rows:print(r['source'],r['variant'],'->',r['id'],'surface',r['canonicalSurface'])
 print('PASS',len(rows),'decorated native slab materials')
if __name__=='__main__':main()
