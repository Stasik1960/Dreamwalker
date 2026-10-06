"""Independent complete typed-NBT comparison of all entity chunks and original level.dat."""
import argparse,collections,hashlib,json,pathlib,zipfile
import complete_world_io as w
ROOT=pathlib.Path(__file__).resolve().parents[1]
MOBS=set('huntsman_a huntsman_b huntsman_c huntsman_d huntsman_e huntsman_f huntsman_g huntsman_h huntsman_i huntsman_j scourge_beast cleric_beast blood_starved_beast maneater_boar carrion_crow brick_troll church_servant church_giant'.split())

def expected(tag,allowed,migrations,frozen):
 if tag.type==w.TAG_LIST:
  for child in tag.value:expected(child,allowed,migrations,frozen)
 elif tag.type==w.TAG_COMPOUND:
  fields=tag.value;ident=fields.get('id')
  if ident and ident.type==w.TAG_STRING and ident.value.startswith('bloodborne:'):
   suffix=ident.value.split(':',1)[1]
   assert suffix in allowed,'Unknown migration source '+ident.value
   ident.value='bloodborne_rp:'+suffix;migrations[suffix]+=1
   if suffix in MOBS:
    for key in ('Attributes','Health','ForgeCaps','Brain'):fields.pop(key,None)
    fields['bloodborne_rp_frozen']=w.Tag(w.TAG_BYTE,1);frozen[suffix]+=1
   for old,new in [('IsOpen','Open'),('isOpen','Open'),('is_open','Open'),('IsLocked','Locked'),('isLocked','Locked')]:
    if old in fields and new not in fields:fields[new]=fields[old]
  for child in fields.values():expected(child,allowed,migrations,frozen)

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('original',type=pathlib.Path);p.add_argument('converted',type=pathlib.Path);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args()
 allowed=set(json.loads((ROOT/'src/main/resources/assets/bloodborne_rp/catalog.json').read_text(encoding='utf-8')));errors=[];chunks=entities=regions=0;migrations=collections.Counter();frozen=collections.Counter()
 with zipfile.ZipFile(a.original) as old,zipfile.ZipFile(a.converted) as new:
  assert old.testzip() is None and new.testzip() is None
  assert set(old.namelist())==set(new.namelist()),'Archive topology differs'
  for name in old.namelist():
   if not name.startswith('entities/') or not name.endswith('.mca'):continue
   regions+=1;before=old.read(name);after=new.read(name)
   if not before:
    assert before==after,'Empty placeholder changed';continue
   original=w.RegionFile(before);converted=w.RegionFile(after);original_chunks=list(original.chunks());new_chunks=list(converted.chunks())
   assert {(c.x,c.z) for c in original_chunks}=={(c.x,c.z) for c in new_chunks},'Entity chunk coordinates differ'
   for chunk in original_chunks:
    other=converted.get_chunk(chunk.x,chunk.z);assert chunk.timestamp==other.timestamp,'Timestamp differs'
    before_nbt=chunk.nbt();after_nbt=other.nbt();fields=w.compound(before_nbt.root)
    entities+=len(fields.get('Entities',w.Tag(w.TAG_LIST,[],w.TAG_COMPOUND)).value)
    expected(before_nbt.root,allowed,migrations,frozen)
    if before_nbt!=after_nbt:errors.append(name+':'+str((chunk.x,chunk.z)))
    chunks+=1
  level=w.decode_nbt(old.read('level.dat'),compressed='gzip');level.root.value['Data'].value['LevelName']=w.Tag(w.TAG_STRING,'bloodborne-dw BBMC v16')
  if level!=w.decode_nbt(new.read('level.dat'),compressed='gzip'):errors.append('level.dat differs outside allowed LevelName')
 report={'entity_region_files':regions,'entity_chunks':chunks,'top_level_entities':entities,'migrated_ids_including_nested_items':dict(migrations),'frozen_mobs':dict(frozen),'level_dat_full_typed_comparison':True,'errors':errors,'valid':not errors,'source_sha256':hashlib.sha256(a.original.read_bytes()).hexdigest(),'converted_sha256':hashlib.sha256(a.converted.read_bytes()).hexdigest()}
 a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report));raise SystemExit(1 if errors else 0)
if __name__=='__main__':main()
