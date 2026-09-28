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
    def _region(self,relative):
        result=super()._region(relative)
        while len(self.regions)>4:
            oldest=next(iter(self.regions))
            if oldest==relative:
                self.regions[oldest]=self.regions.pop(oldest)
            else:self.regions.pop(oldest)
        return result

    @lru_cache(maxsize=64)
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
                result[int(compound(section)['Y'].value)] = ([block_state_key(p) for p in palette], np.asarray(indices,dtype=np.uint16))
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

def reconcile(occurrences, rules):
    """Only explicit existing supersession may remove an exact raw match."""
    by_cell=defaultdict(set)
    sources=[]
    for i,row in enumerate(occurrences):
        cells=frozenset(tuple(c['position']) for c in row['source_cells'])
        sources.append(cells)
        for p in cells:by_cell[(row['dimension'],p)].add(i)
    removed={}
    for i,row in enumerate(occurrences):
        rule=rules[row['rule']]
        possible=set().union(*(by_cell[(row['dimension'],p)] for p in sources[i]))
        for j in possible-{i}:
            other=occurrences[j]
            if sources[j]<sources[i] and all('bloodborne_blocks:'+o['family'] in rule.supersedes_targets for o in other['outputs']):
                removed.setdefault(j,[]).append(i)
    # A supersession cycle is impossible with strict containment. Do not
    # remove a match unless at least one surviving ancestor really replaces it.
    retained=[row for i,row in enumerate(occurrences) if i not in removed]
    superseded=[{'rule':occurrences[i]['rule'],'origin':occurrences[i]['origin'],
                 'families':[o['family'] for o in occurrences[i]['outputs']],
                 'by':[{'rule':occurrences[j]['rule'],'origin':occurrences[j]['origin']} for j in parents]}
                for i,parents in sorted(removed.items())]
    owners=defaultdict(list)
    for i,row in enumerate(retained):
        for output in row['outputs']:
            for p in output['physical_cells']:
                owners[(row['dimension'],tuple(p))].append({'occurrence':i,'family':output['family'],
                    'root':output['canonical_root'],'state':output['expected_logical_state']})
    conflicts=[{'dimension':dim,'position':list(p),'owners':values} for (dim,p),values in sorted(owners.items()) if len(values)>1]
    return retained,superseded,conflicts

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
        exact_count=len(occurrences)
        occurrences,superseded,conflicts=reconcile(occurrences,rules)
        families=json.loads((DEFAULT_RESOURCES/'contracts-v2.json').read_bytes())['families']
        totals=Counter(o['family'] for row in occurrences for o in row['outputs'])
        result={'schema':1,'baseline':'419b85eeab56180f0e26272ffc2a2136f6a05a18',
            'source_sha256':SOURCE_SHA,'contract_sha256':hashlib.sha256((DEFAULT_RESOURCES/'contracts-v2.json').read_bytes()).hexdigest(),
            'policy':'Exact current source patterns, weighted-model guards and declared strict-source supersession; no semantic discovery.',
            'exact_transaction_matches':exact_count,'superseded_matches':superseded,'physical_conflicts':conflicts,
            'families':{f['id']:{'expected_source_occurrences':totals[f['id']],
                'migration_disabled':f.get('migration_disabled',False)} for f in families},'occurrences':occurrences}
        path=ROOT/'docs/composite-grid-repair/protected-world-oracle.json'
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(result,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
        print('ORACLE',len(occurrences),dict(totals),flush=True)
    finally: reader.close()

if __name__=='__main__': build()
