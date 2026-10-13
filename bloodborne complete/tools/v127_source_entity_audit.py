"""Read every original entity and block entity as inert typed source evidence."""
import collections,gzip,json,zipfile
from pathlib import PurePosixPath
from v125_map_index import ROOT,MAP,PACK,sha,dump
from scan_original_map_states import dimension_for
from world_io import RegionFile,compound,TAG_LIST,TAG_COMPOUND,NbtError
from v125_map_city import typed
O=ROOT/'docs/v12.7/conversion'
def main():
    O.mkdir(parents=True,exist_ok=True);counts=collections.Counter();items=collections.Counter();bes=collections.Counter();rows=[];invalid=[];regions=chunks=0
    with zipfile.ZipFile(MAP) as archive:
        for name in sorted(n for n in archive.namelist() if n.endswith('.mca') and PurePosixPath(n).parent.name in {'region','entities'}):
            regions+=1;dim=dimension_for(name,'');raw=archive.read(name)
            try:region=RegionFile(raw)
            except NbtError as error:
                invalid.append({'member':name,'bytes':len(raw),'reason':str(error),'decision':'REMOVE_INVALID_EMPTY_ENTITY_REGION' if len(raw)==0 else 'REVIEW_CORRUPT_REGION'});continue
            for stored in region.chunks():
                chunks+=1;root=compound(stored.nbt().root);root=compound(root['Level']) if 'Level' in root else root
                for category,keys in [('block_entity',('block_entities','TileEntities')),('entity',('Entities','entities'))]:
                    data=next((root[k].value for k in keys if k in root),[])
                    for entry in data:
                        d=compound(entry);id=d.get('id');id=id.value if id else 'missing:id'
                        (bes if category=='block_entity' else counts)[id]+=1
                        pos=[d.get(k).value for k in ['x','y','z']] if category=='block_entity' and all(k in d for k in ['x','y','z']) else [t.value for t in d.get('Pos',type('V',(),{'value':[]})()).value]
                        equipment=[]
                        for key in ['ArmorItems','HandItems','Item']:
                            value=d.get(key)
                            if value is None:continue
                            for item in (value.value if value.type==TAG_LIST else [value]):
                                it=compound(item);iid=it.get('id');itag=compound(it['tag']) if 'tag' in it else {};cmd=itag.get('CustomModelData')
                                if iid and iid.value!='minecraft:air':equipment.append({'slot':key,'item':iid.value,'customModelData':cmd.value if cmd else None});items[str((iid.value,cmd.value if cmd else None))]+=1
                        commands=[k for k in d if any(s.lower() in k.lower() for s in ['command','program','function'])]
                        action='REVIEW_VISIBLE_AUTHORED_ITEM' if equipment else 'KEEP_VANILLA_INERT' if category=='block_entity' and id in {'minecraft:sign','minecraft:hanging_sign'} else 'REMOVE_UNKNOWN_OR_SYSTEM'
                        rows.append({'category':category,'id':id,'dimension':dim,'sourceArchiveMember':name,'sourcePosition':pos,'equipment':equipment,'commandFieldNames':commands,'decision':action,'typedNbt':typed(entry),'commandsExecuted':False})
            print(json.dumps({'regions':regions,'last':name,'entities':sum(counts.values()),'blockEntities':sum(bes.values())}),flush=True)
    path=O/'original-entities-block-entities.jsonl.gz'
    with path.open('wb') as file:
        with gzip.GzipFile(fileobj=file,mode='wb',mtime=0) as out:
            for row in rows:out.write((json.dumps(row,ensure_ascii=False,separators=(',',':'))+'\n').encode())
    dump(O/'original-entities-summary.json',{'status':'PASS_READONLY_ALL_ENTITIES_TYPED_NBT_AUDIT','regions':regions,'chunks':chunks,'entityCounts':dict(counts),'blockEntityCounts':dict(bes),'equipmentCounts':dict(items),'invalidRegions':invalid,'typedEvidenceFile':path.name,'typedEvidenceSha256':sha(path),'sourceHashes':{'map':sha(MAP),'pack':sha(PACK)},'originalWorldWritten':False,'executableSourceFieldsExecuted':False})
if __name__=='__main__':main()
