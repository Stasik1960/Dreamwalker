"""Addressed two global-index field diff; no map/SQLite writes."""
import pilot_dependencies as p
db=p.c.attached_readonly(p.m,p.PLAN)
try:
    binding=p.m.meta(db,'binding');source=p.Path(binding['source']);expected=p.m.read_nbt(source/p.GLOBAL);p.m.crop_pilot_global_index(expected,p.original_shards());rows={}
    for variant in ('baseline','candidate'):
        actual=p.m.read_nbt(p.m.ROOT/'research/pilot'/variant/p.GLOBAL)
        e=p.m.compound(expected.root);a=p.m.compound(actual.root);e=p.m.compound(e['data']) if 'data'in e else e;a=p.m.compound(a['data']) if 'data'in a else a
        rows[variant]={'differentFields':[{'key':key,'expected':p.c._typed(p.m,e[key]) if key in e else None,'actual':p.c._typed(p.m,a[key]) if key in a else None} for key in sorted(set(e)|set(a)) if key not in e or key not in a or p.c._typed(p.m,e[key])!=p.c._typed(p.m,a[key])],'indexSetEqual':set(e['OwnerShardChunks'].value)==set(a['OwnerShardChunks'].value),'indexExpected':len(e['OwnerShardChunks'].value),'indexActual':len(a['OwnerShardChunks'].value)}
        extras=[];aux={(r['x'],r['z']) for r in p.m.read(p.INVENTORY)['chunks']}
        for key in sorted(set(a['OwnerShardChunks'].value)-set(e['OwnerShardChunks'].value)):
            x=key&0xffffffff;z=(key>>32)&0xffffffff;x=x if x<1<<31 else x-(1<<32);z=z if z<1<<31 else z-(1<<32);path=p.m.ROOT/'research/pilot'/variant/('data/bloodborne_dw_owner_shards/owners-%d.%d.nbt'%(x,z));tag=p.m.read_nbt(path) if path.is_file() else None;data=p.m.compound(tag.root) if tag else {};data=p.m.compound(data['data']) if 'data'in data else data
            extras.append({'chunk':[x,z],'insideAuxiliary88':(x,z) in aux,'path':str(path),'exists':path.is_file(),'sha256':p.m.sha(path) if path.is_file() else None,'fields':{k:{'type':v.type,'size':len(v.value) if isinstance(v.value,(dict,list,bytes,bytearray)) else v.value} for k,v in data.items()},'typedNbt':p.c._typed(p.m,tag.root) if tag else None})
        rows[variant]['extraIndexRecords']=extras
    p.m.atomic_json(p.REPAIR/'global-field-difference.json',rows)
finally:db.close()
