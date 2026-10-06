"""Finalize only world metadata/datapack namespaces; verify all region bytes stay identical."""
import argparse,hashlib,json,pathlib,re,zipfile
import complete_world_io as w
def digest(b):return hashlib.sha256(b).hexdigest()
def main():
 p=argparse.ArgumentParser();p.add_argument('world',type=pathlib.Path);p.add_argument('--output',type=pathlib.Path,required=True);p.add_argument('--catalog',type=pathlib.Path,required=True);a=p.parse_args()
 if a.output.exists():raise ValueError('Refusing to overwrite '+str(a.output))
 a.output.parent.mkdir(parents=True,exist_ok=True);report={'input_sha256':digest(a.world.read_bytes()),'changed_paths':[],'regions_verified_identical':0};manifest=None
 with zipfile.ZipFile(a.world) as source,zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as out:
  for name in source.namelist():
   before=source.read(name);data=before
   if name=='level.dat':
    nbt=w.decode_nbt(data,compressed='gzip');d=w.compound(w.compound(nbt.root)['Data']);d.pop('Player',None);packs=w.compound(d['DataPacks']);enabled=[t for t in packs['Enabled'].value if not t.value.startswith('mod:') and t.value!='bloodborne_dw']
    if not any(t.value=='fabric' for t in enabled):enabled.append(w.Tag(w.TAG_STRING,'fabric'))
    packs['Enabled']=w.Tag(w.TAG_LIST,enabled,w.TAG_STRING);data=w.encode_nbt(nbt,compressed='gzip')
   elif name.startswith('datapacks/Bloodborne/') and name.endswith('.mcfunction'):
    text=data.decode('utf-8').replace('bloodborne:','bloodborne_rp:')
    if name.endswith('/load.mcfunction'):text='# Imported RP encounters. No automatic spawn or world reset.\n'
    data=text.encode('utf-8')
   elif name=='datapacks/Bloodborne/pack.mcmeta':
    d=json.loads(data);d['pack']['pack_format']=15;d['pack']['description']='BBMC manual encounters, migrated to Bloodborne DW';data=(json.dumps(d,indent=2)+'\n').encode()
   elif name in ('datapacks/Bloodborne/data/minecraft/tags/functions/load.json','datapacks/Bloodborne/data/minecraft/tags/functions/tick.json'):
    # The supplied tags point at nonexistent bloodborne:* functions; enabling
    # their spawn/kill scripts every tick would also violate the RP scope.
    data=b'{"replace":false,"values":[]}\n'
   elif name=='gallery-manifest.json':
    manifest=json.loads(data);manifest['catalog_sha256']=digest(a.catalog.read_bytes());manifest['metadata_finalization']='obsolete enabled Forge pack references removed; invalid automatic encounter tags disabled; manual RP functions migrated; embedded player removed';data=(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n').encode()
   if before!=data:report['changed_paths'].append(name)
   if name.endswith('.mca'):
    assert before==data;report['regions_verified_identical']+=1
   info=zipfile.ZipInfo(name,(2000,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;out.writestr(info,data)
 with zipfile.ZipFile(a.output) as z:assert not z.testzip()
 report['output_sha256']=digest(a.output.read_bytes());a.output.with_suffix('.finalization.json').write_text(json.dumps(report,indent=2)+'\n')
 if manifest:a.output.with_suffix('.gallery-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 print(json.dumps(report))
if __name__=='__main__':main()
