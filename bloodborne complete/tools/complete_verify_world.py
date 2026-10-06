"""Read-only comparison of every original/converted chunk and palette index."""
import argparse,collections,copy,hashlib,json,pathlib,zipfile
import complete_world_io as w
import complete_convert_world as converter
def scan(tag,result):
 if tag.type==w.TAG_STRING and tag.value.startswith('bloodborne:'):result[tag.value]+=1
 elif tag.type==w.TAG_COMPOUND:
  for t in tag.value.values():scan(t,result)
 elif tag.type==w.TAG_LIST:
  for t in tag.value:scan(t,result)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('original',type=pathlib.Path);p.add_argument('converted',type=pathlib.Path);p.add_argument('--catalog',type=pathlib.Path,required=True);p.add_argument('--report',type=pathlib.Path,required=True);a=p.parse_args()
 catalog=json.loads(a.catalog.read_text(encoding='utf-8'));mapping={b['source']:'bloodborne_dw:'+b['id'] for b in catalog['blocks']};counts=collections.Counter();unknown=collections.Counter();chunks=0;palettes=0;errors=[]
 with zipfile.ZipFile(a.original) as old,zipfile.ZipFile(a.converted) as new:
  assert not old.testzip() and not new.testzip(),'CRC failure'
  assert set(old.namelist())==set(new.namelist()),'ZIP topology differs'
  for path in old.namelist():
   raw=old.read(path);after=new.read(path)
   if not raw:assert raw==after;continue
   if path.startswith('region/') and path.endswith('.mca'):
    before=w.RegionFile(raw);now=w.RegionFile(after);oldchunks=list(before.chunks());newchunks=list(now.chunks());assert len(oldchunks)==len(newchunks)
    for c in oldchunks:
     nc=now.get_chunk(c.x,c.z);assert nc and c.timestamp==nc.timestamp,'chunk placement/time differs'
     expected=c.nbt();actual=nc.nbt();r=w.compound(expected.root);s=w.compound(actual.root);assert r['xPos']==s['xPos'] and r['zPos']==s['zPos']
     changed=False
     for section in r.get('sections',w.Tag(w.TAG_LIST,[],w.TAG_COMPOUND)).value:
      sf=w.compound(section)
      if 'block_states' not in sf:continue
      bs=w.compound(sf['block_states']);palette=bs['palette'].value;palettes+=1
      indices=converter.indices(bs,len(palette));amounts=collections.Counter(indices.tolist())
      for i,entry in enumerate(palette):
       e=w.compound(entry);source=e['Name'].value
       if source in mapping:
        e['Name'].value=mapping[source];e.setdefault('Properties',w.Tag(w.TAG_COMPOUND,{})).value['visual']=w.Tag(w.TAG_STRING,'base');counts[source]+=amounts[i];changed=True
       elif source=='bloodborne:hunter_lamp_light_source':
        e['Name'].value='minecraft:light';e['Properties']=w.Tag(w.TAG_COMPOUND,{'level':w.Tag(w.TAG_STRING,'15'),'waterlogged':w.Tag(w.TAG_STRING,'false')});changed=True
     if changed:w.invalidate_chunk_lighting(r)
     # Whole typed NBT comparison verifies palette arrays, coordinates, block
     # entities/inventories, heightmaps, biomes and every unrelated field.
     if expected!=actual:errors.append(path+':'+str((c.x,c.z)))
     scan(actual.root,unknown);chunks+=1
   elif not path.startswith('entities/') and pathlib.PurePosixPath(path).name!='level.dat':
    if raw!=after:errors.append('unrelated file changed: '+path)
   if chunks and chunks%5000==0:print(json.dumps({'verified_chunks':chunks}),flush=True)
 for b in catalog['blocks']:
  if counts[b['source']]!=b['frequency']:errors.append('frequency differs '+b['id'])
 report={'terrain_chunks':chunks,'paletted_sections':palettes,'converted_cells':sum(counts.values()),'errors':errors,'remaining_original_strings_in_terrain':dict(unknown),'original_sha256':hashlib.sha256(a.original.read_bytes()).hexdigest(),'converted_sha256':hashlib.sha256(a.converted.read_bytes()).hexdigest(),'valid':not errors}
 a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report));raise SystemExit(0 if report['valid'] else 1)
if __name__=='__main__':main()
