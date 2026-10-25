"""One bounded palette remap of the READY map; no geometry export/conversion.

Keeps old whole roles, every other mod block, BE, owner file, lighting array,
inventory and level.dat. Old ordinary items migrate lazily when ItemStack reads.
"""
import collections, copy, hashlib, json, os, re, shutil, stat, sys, time
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024'
sys.path.insert(0,str(P/'tools'))
from world_io import *
SOURCE=Path('C:/Users/vakir/Desktop/bloodborne-native-grid-city')
DEST=T/'world'
def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for part in iter(lambda:f.read(4<<20),b''):h.update(part)
    return h.hexdigest()
def ordinary(path):
    for parent in (path,*path.parents):
        info=parent.lstat();assert not stat.S_ISLNK(info.st_mode) and not getattr(info,'st_file_attributes',0)&1024
def save(path,n):
    temporary=path.with_name(path.name+'.writing');temporary.write_text(json.dumps(n,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');os.replace(temporary,path)
def stripped(nbt):
    root=compound(nbt.root)
    data={k:v for k,v in root.items() if k not in ('sections','DreamwalkerVisuals')}
    sections=[]
    for raw in root.get('sections',Tag(9,[])).value:
        section=compound(raw);sections.append(Tag(10,{k:v for k,v in section.items() if k!='block_states'}))
    data['sections']=Tag(9,sections,10)
    return hashlib.sha256(encode_nbt(NbtFile(nbt.name,Tag(10,data)))).hexdigest()
def get_index(entry):
    n=compound(entry);name=n['Name'].value
    if not name.startswith('bloodborne_dw:native_grid_'):return None
    bank=int(name.rsplit('_',1)[1]);part=int(compound(n['Properties'])['part'].value);return bank*128+part
def replacement(index):
    physics=mapping[index]
    return Tag(10,{'Name':Tag(8,'bloodborne_dw:native_physics_'+str(physics//128)),
                   'Properties':Tag(10,{'part':Tag(8,str(physics%128))})})

started=time.monotonic()
layout_path=P/'src/nativevisual/resources/assets/bloodborne_dw/native_grid/visual_physics.json'
layout=json.loads(layout_path.read_text(encoding='utf-8'));mapping=layout['cellToPhysics']
doc=json.loads((P/'src/architecture/resources/assets/bloodborne_dw/native_grid/catalog.json').read_text(encoding='utf-8'))
fields=('collision','outline','culling','climbing','optics','fluid','localFunction','alt')
for visual,physics in enumerate(mapping):
    if physics>=0:
        row=doc['cells'][visual];base=doc['cells'][layout['representatives'][physics]]
        assert all(row.get(k)==base.get(k) for k in fields),'Physics signature changed'
ordinary(SOURCE)
assert SOURCE.resolve()!=DEST.resolve() and DEST.resolve().is_relative_to(T.resolve())
assert (T/'checkpoint-before-map.json').is_file(),'Save the implementation checkpoint before remapping the map'
receipt=T/'map-remap.json';progress=T/'map-remap-progress.json'
assert not receipt.exists()
if progress.exists():
    state=json.loads(progress.read_text(encoding='utf-8'));assert state['layoutSha256']==sha(layout_path)
else:
    assert not DEST.exists();DEST.mkdir()
    files={}
    for current,dirs,names in os.walk(SOURCE,followlinks=False):
        for name in dirs+names:ordinary(Path(current)/name)
        relative=Path(current).relative_to(SOURCE);(DEST/relative).mkdir(exist_ok=True)
        for name in names:
            if relative==Path('.') and name=='session.lock':continue
            src=Path(current)/name;dst=DEST/relative/name;digest=sha(src);shutil.copy2(src,dst);assert sha(dst)==digest and sha(src)==digest
            files[(relative/name).as_posix()]={'sha256':digest,'bytes':src.stat().st_size}
    state={'status':'COPIED_READY_MAP_REMAP_IN_PROGRESS','source':str(SOURCE),'destination':str(DEST),'layoutSha256':sha(layout_path),'sourceFiles':files,'regions':{},'convertedCells':0,'convertedChunks':0,'wholeRoleCellsKept':0}
    save(progress,state)

for relative,row in state['sourceFiles'].items():assert sha(SOURCE/relative)==row['sha256'],'Ready source changed'
witnesses=state.get('witnesses',{})
for current,dirs,names in os.walk(DEST,followlinks=False):
    if Path(current).name!='region':continue
    for name in sorted(names):
        if not name.endswith('.mca'):continue
        path=Path(current)/name;relative=path.relative_to(DEST).as_posix()
        if relative in state['regions']:
            assert sha(path)==state['regions'][relative]['sha256'];continue
        original=RegionFile.open(SOURCE/relative);target=RegionFile.open(path)
        local_cells=local_chunks=local_wholes=0;proofs={}
        for old_chunk in original.chunks():
            nbt=old_chunk.nbt();root=compound(nbt.root);unchanged=stripped(nbt);visual_sections=[];changed=False
            for section_tag in root.get('sections',Tag(9,[])).value:
                section=compound(section_tag)
                if 'block_states' not in section:continue
                block_states=compound(section['block_states']);palette=block_states['palette'].value
                native={i:get_index(entry) for i,entry in enumerate(palette)}
                if not any(index is not None for index in native.values()):continue
                indices=unpack_palette_indices(block_states.get('data',Tag(12,[])).value,len(palette))
                for old_i,count in collections.Counter(indices).items():
                    visual=native[old_i]
                    if visual is not None and mapping[visual]<0:local_wholes+=count
                if not any(index is not None and mapping[index]>=0 for index in native.values()):continue
                changed=True;new_palette=[];new_lookup={};old_to_new={}
                for i,entry in enumerate(palette):
                    visual=native[i];new=replacement(visual) if visual is not None and mapping[visual]>=0 else entry
                    key=block_state_key(new)
                    if key not in new_lookup:new_lookup[key]=len(new_palette);new_palette.append(new)
                    old_to_new[i]=new_lookup[key]
                entries=[];new_indices=[];y=section['Y'].value
                for at,i in enumerate(indices):
                    visual=native[i];new_indices.append(old_to_new[i])
                    if visual is not None and mapping[visual]>=0:
                        entries.append((visual<<12)|at);local_cells+=1
                        row=doc['cells'][visual];boxes=doc['shapes'][row['collision']];optics=doc['optics'][row['optics']]
                        categories=['any','collision' if boxes else 'noCollision',layout['physicsLayers'][mapping[visual]]]
                        if len(boxes)>=5:categories.append('multiCollision')
                        if optics.get('luminance',0)>0:categories.append('emitting')
                        if row.get('fluid'):categories.append('fluid')
                        if row.get('localFunction'):categories.append('localFunction')
                        if any(0<abs(b[k+3]-b[k])<.1 for b in boxes for k in range(3)):categories.append('thin')
                        for category in categories:
                            if category not in witnesses:witnesses[category]={'pos':[root['xPos'].value*16+(at&15),y*16+(at>>8),root['zPos'].value*16+((at>>4)&15)],'visual':visual,'physics':mapping[visual]}
                    else:assert new_palette[new_indices[-1]]==palette[i],'Protected/foreign state changed'
                block_states['palette']=Tag(9,new_palette,10)
                if len(new_palette)>1:block_states['data']=Tag(12,pack_palette_indices(new_indices,len(new_palette)))
                else:block_states.pop('data',None)
                if entries:visual_sections.append(Tag(10,{'Y':Tag(3,y),'Entries':Tag(11,entries)}))
            if not changed:continue
            assert 'DreamwalkerVisuals' not in root,'Map already partially remapped'
            if visual_sections:root['DreamwalkerVisuals']=Tag(10,{'Schema':Tag(3,1),'Catalog':Tag(8,layout['catalogSha256']),'Sections':Tag(9,visual_sections,10)})
            # No scheduled ticks may silently retain an unregistered ordinary block ID.
            for tick in root.get('block_ticks',Tag(9,[])).value:
                t=compound(tick)
                assert not t['i'].value.startswith('bloodborne_dw:native_grid_'),'Explicit tick remap needed before writing'
            assert stripped(nbt)==unchanged,'BE/light/heightmap/foreign metadata changed'
            target.set_chunk(old_chunk.x,old_chunk.z,nbt,timestamp=old_chunk.timestamp)
            proofs[(old_chunk.x,old_chunk.z)]=unchanged;local_chunks+=1
        if local_chunks:
            ordinary(path);assert path.resolve().is_relative_to(DEST.resolve());target.save(path)
            reopened=RegionFile.open(path)
            for coordinate,protected_hash in proofs.items():
                decoded=reopened.get_chunk(*coordinate).nbt();assert stripped(decoded)==protected_hash
                root=compound(decoded.root)
                table=compound(root.get('DreamwalkerVisuals',Tag(10,{'Sections':Tag(9,[],10)})));actual={}
                for raw in table['Sections'].value:
                    row=compound(raw);actual[row['Y'].value]={v&4095:v>>12 for v in row['Entries'].value}
                for section_tag in root['sections'].value:
                    s=compound(section_tag)
                    if 'block_states' not in s:continue
                    b=compound(s['block_states']);palette=b['palette'].value;indices=unpack_palette_indices(b.get('data',Tag(12,[])).value,len(palette));meta=actual.get(s['Y'].value,{})
                    for at,i in enumerate(indices):
                        entry=compound(palette[i]);physical=entry['Name'].value.startswith('bloodborne_dw:native_physics_')
                        assert physical==(at in meta),'Visual table coverage mismatch'
                        if physical:assert palette[i]==replacement(meta[at]),'Reopened physics mapping changed'
        state['regions'][relative]={'sha256':sha(path),'convertedCells':local_cells,'convertedChunks':local_chunks,'wholeRoleCellsKept':local_wholes}
        state['convertedCells']+=local_cells;state['convertedChunks']+=local_chunks;state['wholeRoleCellsKept']+=local_wholes;state['witnesses']=witnesses
        save(progress,state)
        print(json.dumps({'region':relative,'done':len(state['regions']),'cells':state['convertedCells'],'seconds':time.monotonic()-started}),flush=True)

unchanged_files=0
for relative,row in state['sourceFiles'].items():
    assert sha(SOURCE/relative)==row['sha256'],'Original READY source changed during remap'
    if relative not in state['regions']:assert sha(DEST/relative)==row['sha256'];unchanged_files+=1
result={**state,'status':'OFFLINE_REMAP_AND_REOPEN_VERIFIED_NOT_GAMEPLAY','unchangedOtherFiles':unchanged_files,'sourceUnchanged':True,'geometryExported':False,'exactPhysicsAndOptics':True,'protectedAndWholeStatesUnchanged':True,'lightingAndBEDataUnchanged':True,'seconds':time.monotonic()-started}
save(receipt,result)
print(json.dumps({k:v for k,v in result.items() if k not in ('sourceFiles','regions')},ensure_ascii=False),flush=True)
