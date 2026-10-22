from common import *
from collections import Counter
sys.path.insert(0,str(PROJECT/'tools'))
from world_io import *
from world_io import _plain
log_to('summary')
d=read(OUT/'world-audit.json');s=read(OUT/'selected-materials.json');keys={(t['registryId'],t['variant']) for t in s['slabs']}
print('CHUNKS',d['chunks'],'FLOORS',d['floorComponents'])
print('NATIVE INVISIBLE',[(k,v) for k,v in d['blockCounts'].items() if any(w in k for w in ['invisible','barrier','light','fallback'])])
fallback=read(RES/'bloodborne_dw/source-fallback-carriers-v1271.json')
for state,num in d['invisibleAndFallbackStates'].items():
 if 'fallback' not in state:continue
 props=dict(x.split('=') for x in state.split('[',1)[1][:-1].split(','));i=int(props.get('source','0'))+128*int(props.get('source_bank','0'));source=fallback['sourceStates'][i]
 if 'slab' in source or 'waterlogged=true' in source:print('FALLBACK HALF/WET',state,num,source)
print('SOURCE BLOCKS',[(k,v) for k,v in d['targetSourceBlocks'].items() if k.split('[',1)[0] in {t[0] for t in keys}])
for p in SOURCE_WORLD.rglob('owners-*.nbt'):
 n=read_nbt(p);root=compound(n.root)
 tables=root.get('ownerTable',Tag(9,[],10)).value
 if any('slab' in compound(t)['id'].value for t in tables):
  print('SAMPLE SHARD',p.relative_to(SOURCE_WORLD));write(OUT/'sample-shard.json',_plain(n.root));break
print('PASS')
