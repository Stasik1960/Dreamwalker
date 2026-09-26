"""Read-only exact Contract V2 occurrences; source world is never conversion input."""
import gzip
import hashlib
import json
from collections import Counter, defaultdict
from functools import lru_cache
from pathlib import Path

import numpy as np
from compare_modded_reference import LazyReference, _region_relative
from convert_logical_world import DEFAULT_RESOURCES, Output, add, as_tag_state
from logical_contract_v2 import direct_rules
from source_variant_rng import guards_match
from world_io import compound, section_blocks, block_state_key

ROOT = Path(__file__).resolve().parents[1]
SOURCE_SHA = '4353737d536677469d3b895e3515496ab64fab7b224e43428eb96e8c09724a51'

class EvidenceReader(LazyReference):
    """Bounded decoded-section cache; uses the existing archive path validation."""
    @lru_cache(maxsize=256)
    def sections(self, dimension, cx, cz):
        region = self._region(_region_relative(dimension, cx//32, cz//32))
        stored = region.get_chunk(cx%32, cz%32) if region else None
        if stored is None: return None
        result = {}
        root = compound(stored.nbt().root)
        for section in root.get('sections', ()).value if 'sections' in root else ():
            values = section_blocks(section)
            if values:
                palette, indices = values
                result[int(compound(section)['Y'].value)] = ([block_state_key(p) for p in palette], indices)
        return result

    def state(self, dimension, point):
        sections = self.sections(dimension, point[0]//16, point[2]//16)
        if sections is None: return 'MISSING', None
        section = sections.get(point[1]//16)
        if section is None: return 'FOUND', 'minecraft:air'
        palette, indices = section
        return 'FOUND', palette[indices[(point[1]&15)*256+(point[2]&15)*16+(point[0]&15)]]

    def close(self):
        self.sections.cache_clear()
        super().close()

def key(state): return block_state_key(as_tag_state(state))

def build():
    source = ROOT/'reference-inputs/source-world.zip'
    if hashlib.sha256(source.read_bytes()).hexdigest()!=SOURCE_SHA:
        raise ValueError('SOURCE_REFERENCE_HASH_MISMATCH')
    rules, _ = direct_rules(DEFAULT_RESOURCES)
    index = json.loads(gzip.decompress((ROOT/'docs/source-assembly-carrier-index.json.gz').read_bytes()))
    counts = {s.get('key',s.get('state_id')):s.get('count',0) for s in index['states']}
    seeds = defaultdict(list)
    for rule in rules:
        piece = min((rule.source,)+rule.members,key=lambda p:counts.get(key(p.state),10**12))
        seeds[key(piece.state)].append((rule,piece))
    reader = EvidenceReader(source)
    origins = defaultdict(set)
    dim = 'eh_s2:yharnam'
    try:
        names = sorted(n for n in reader.members if n.startswith('dimensions/eh_s2/yharnam/region/'))
        for number,name in enumerate(names,1):
            region = reader._region(name)
            for stored in region.chunks():
                root=compound(stored.nbt().root)
                cx,cz=int(root['xPos'].value),int(root['zPos'].value)
                for section in root.get('sections',()).value if 'sections' in root else ():
                    fields=compound(section);states=fields.get('block_states')
                    if states is None:continue
                    palette_tag=compound(states).get('palette')
                    if palette_tag is None:continue
                    selected={i:block_state_key(p) for i,p in enumerate(palette_tag.value) if block_state_key(p) in seeds}
                    if not selected:continue
                    _,indices=section_blocks(section);indices=np.asarray(indices)
                    sy=int(fields['Y'].value)
                    for i,k in selected.items():
                        for offset in np.flatnonzero(indices==i):
                            pos=(cx*16+int(offset&15),sy*16+int(offset>>8),cz*16+int((offset>>4)&15))
                            for rule,piece in seeds[k]:
                                origins[rule.number].add(tuple(pos[a]-piece.offset[a] for a in range(3)))
            print(f'oracle seed scan {number}/{len(names)}',flush=True)
        occurrences=[]
        for rule in rules:
            pieces=(rule.source,)+rule.members
            for origin in sorted(origins[rule.number]):
                if not guards_match(rule.variant_guards,origin):continue
                cells=[{'position':list(add(origin,p.offset)),'state':key(p.state)} for p in pieces]
                if not all(reader.state(dim,tuple(p['position']))[1]==p['state'] for p in cells):continue
                outputs=rule.outputs or (Output(rule.target,rule.root_offset,rule.shape),)
                occurrences.append({'rule':rule.number,'source_dimension':dim,'dimension':'minecraft:overworld',
                    'origin':list(origin),'source_cells':cells,'outputs':[{
                        'family':o.target[0].split(':')[1],'expected_logical_state':key(o.target),
                        'canonical_root':list(add(origin,o.root_offset)),
                        'orientation':dict(o.target[1]).get('facing'),
                        'physical_cells':[list(add(add(origin,o.root_offset),p)) for p in sorted(o.shape)]
                    } for o in outputs]})
            print(f'oracle rule {rule.number}: checked {len(origins[rule.number])}',flush=True)
        families=json.loads((DEFAULT_RESOURCES/'contracts-v2.json').read_bytes())['families']
        totals=Counter(o['family'] for row in occurrences for o in row['outputs'])
        result={'schema':1,'baseline':'419b85eeab56180f0e26272ffc2a2136f6a05a18',
            'source_sha256':SOURCE_SHA,'contract_sha256':hashlib.sha256((DEFAULT_RESOURCES/'contracts-v2.json').read_bytes()).hexdigest(),
            'policy':'Exact current source patterns plus original weighted-model guards; no semantic discovery.',
            'families':{f['id']:{'expected_source_occurrences':totals[f['id']],
                'migration_disabled':f.get('migration_disabled',False)} for f in families},'occurrences':occurrences}
        path=ROOT/'docs/composite-grid-repair/protected-world-oracle.json'
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(result,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
        print('ORACLE',len(occurrences),dict(totals),flush=True)
    finally: reader.close()

if __name__=='__main__': build()
