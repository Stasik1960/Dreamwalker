import collections, hashlib, json, sys
from pathlib import Path
BASE=Path(__file__).resolve().parents[2]; RUN=BASE/'build/retirement-rc1-20260927'; OUT=RUN/'atomic-diagnostic'
ENGINE=Path('C:/Users/vakir/Documents/ChatGPT/DW/Bloodborne-Blocks')
sys.path.insert(0,str(ENGINE/'tools'))
mode=sys.argv[1]; first=OUT/'Bloodborne-MODDED-atomic-diagnostic-rc1'; second=OUT/'second-world-verified'; cleaned=RUN/'Bloodborne-MODDED-retired-composites-rc1'
def save(name,data):
    (OUT/name).write_text(json.dumps(data,indent=2)+'\n',encoding='utf8',newline='\n')
if mode=='logical':
    from check_logical_world import check
    result=check(cleaned.with_suffix('.zip'),first,OUT/'first.json',ENGINE/'src/main/resources/bloodborne_blocks/logical')
    save('verified-accepted-transactions.json',result);print(json.dumps(result))
elif mode in ('preservation','second-preservation'):
    from verify_modded_preservation import verify
    result=verify(cleaned if mode=='preservation' else first,first if mode=='preservation' else second,json.loads((OUT/('first.json' if mode=='preservation' else 'second-verified.json')).read_bytes()))
    save('verified-'+mode+'.json',result);print(json.dumps({k:v for k,v in result.items() if k!='errors'}));sys.exit(result['result']!='PASS')
elif mode=='protected':
    from check_composite_world import check
    result=check(first,ENGINE/'docs/composite-grid-repair/protected-world-oracle.json',BASE/'reference-inputs/latest-modded-world.zip')
    result['comparisonScope']='Historical protected-object oracle, whose source hash is pinned to the original ZIP; retirement itself is verified separately against the cleaned copy.'
    save('verified-protected.json',result);print(json.dumps({k:v for k,v in result.items() if not isinstance(v,(dict,list))}));sys.exit(result['result']!='PASS')
elif mode=='helpers-registry':
    from convert_logical_world import World,load_defaults
    from city_palette import audit_helpers
    from world_io import compound,TAG_LIST,TAG_COMPOUND,Tag
    city=ENGINE/'src/main/resources/bloodborne_blocks/city'; logical=city.parent/'logical'
    world=World(first,load_defaults(logical))
    result=audit_helpers(world,city)
    registered={'bloodborne_blocks:architecture_part'}
    for folder in (city,logical):registered.update('bloodborne_blocks:'+b['id'] for b in json.loads((folder/'definitions.json').read_bytes())['blocks'])
    names=set()
    for chunk in world.chunks.values():
        for section in chunk.root().get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
            bs=compound(section).get('block_states')
            if bs:names.update(compound(x)['Name'].value for x in compound(bs)['palette'].value)
    unknown=sorted(n for n in names if n.startswith('bloodborne_blocks:') and n not in registered)
    retired=set(json.loads((RUN/'retirement-ledger.json').read_bytes())['retiredIds'])
    result.update(scope='Existing repair target runtime geometry and full palette scan; not rc.1 registry.',unknownToTargetRegistry=unknown,unknownToTargetRegistryCount=len(unknown),remainingRetiredIds=sorted(names&retired),orphanHelpers=len(result['orphans']))
    save('verified-helpers-registry.json',result);print(json.dumps({k:v for k,v in result.items() if not isinstance(v,(dict,list))}))
elif mode=='manifests':
    from world_io import RegionFile,compound,section_blocks,block_state_key
    def sha(p):
        with p.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
    def manifest(p):return {f.relative_to(p).as_posix():sha(f) for f in sorted(p.rglob('*')) if f.is_file()}
    left,right=manifest(first),manifest(second);assert len(left)==186 and len(right)==186
    save('verified-first-world-manifest.json',{'files':left});save('verified-second-world-manifest.json',{'files':right})
    air_checks=[]
    for w in (first,second):
        nonair=[]
        for row in json.loads((RUN/'retirement-ledger.json').read_bytes())['ledger']:
            x,y,z=row['position']; c=RegionFile.open(w/'region'/f'r.{x//512}.{z//512}.mca').get_chunk(x//16%32,z//16%32)
            s=next(s for s in compound(c.nbt().root)['sections'].value if compound(s)['Y'].value==y//16)
            pal,indices=section_blocks(s);state=block_state_key(pal[indices[(y&15)*256+(z&15)*16+(x&15)]])
            if state!='minecraft:air':nonair.append({'position':row['position'],'state':state})
        air_checks.append({'world':w.name,'cells':33,'nonAir':nonair})
    result={'files':186,'byteIdentical':left==right,'differentPaths':sorted(k for k in left.keys()|right.keys() if left.get(k)!=right.get(k)),'retirementAirChecks':air_checks}
    initial=json.loads((OUT/'first.command.json').read_bytes())['engineFiles']
    result['engineUnchanged']=all(sha(ENGINE/k)==v for k,v in initial.items())
    save('verified-manifests.json',result);print(json.dumps(result));sys.exit(not result['byteIdentical'] or any(c['nonAir'] for c in air_checks) or not result['engineUnchanged'])
else:raise ValueError(mode)
