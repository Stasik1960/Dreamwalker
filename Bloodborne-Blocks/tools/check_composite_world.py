"""Independent world-level gate; registry validity alone is insufficient."""
import argparse
import hashlib
import json
import re
from collections import Counter
from functools import lru_cache
from pathlib import Path
from composite_world_oracle import EvidenceReader, ROOT
from compare_modded_reference import _region_relative
from world_io import RegionFile, compound
from convert_logical_world import PART, unpack_pos_long, DEFAULT_RESOURCES, add
from composite_world_oracle import key
from modded_world_adapter import compile_modded_rules
from source_variant_rng import guards_match

def technical_membership(row,before,rules):
    """Require a complete exact MODDED assembly, not a raw carrier heuristic."""
    origin=tuple(row['origin']);dim=row['dimension'];matched=[]
    for rule in rules:
        if not guards_match(rule.variant_guards,origin):continue
        cells=[(add(origin,p.offset),key(p.state)) for p in (rule.source,)+rule.members]
        if all(before.state(dim,p)[1]==expected for p,expected in cells):matched.append(cells)
    return {p for cells in matched for p,_ in cells} if matched else None

def independent_fragment_errors(actual,dim,positions,permitted_roots):
    errors=[]
    for p in sorted(positions):
        value=actual.state(dim,p)[1]
        if value is None:
            errors.append({'reason':'source_component_chunk_missing','position':p})
        elif value.startswith('bloodborne_blocks:') and value!=PART and permitted_roots.get((dim,p))!=value:
            errors.append({'reason':'source_component_remains_independent','position':p,'state':value})
    return errors

class WorldReader(EvidenceReader):
    def __init__(self,path):
        self.directory=path if path.is_dir() else None
        if self.directory:
            self.regions={};self.chunks={}
        else:super().__init__(path)

    def _region(self,relative):
        if not self.directory:return super()._region(relative)
        if relative not in self.regions:
            p=self.directory/relative
            self.regions[relative]=RegionFile.open(p) if p.exists() else None
        while len(self.regions)>4:
            oldest=next(iter(self.regions))
            if oldest==relative:self.regions[oldest]=self.regions.pop(oldest)
            else:self.regions.pop(oldest)
        return self.regions[relative]

    def close(self):
        self.sections.cache_clear()
        self.helper_index.cache_clear()
        if not self.directory:super().close()

    def helper_owner(self,dim,pos):
        return self.helper_index(dim,pos[0]//16,pos[2]//16).get(pos)

    @lru_cache(maxsize=256)
    def helper_index(self,dim,cx,cz):
        r=self._region(_region_relative(dim,cx//32,cz//32))
        c=r.get_chunk(cx%32,cz%32) if r else None
        if c is None:return {}
        result={}
        root=compound(c.nbt().root)
        for entry in root.get('block_entities',()).value if 'block_entities' in root else ():
            d=compound(entry)
            if all(k in d for k in ('x','y','z')):
                if d.get('id') and d['id'].value==PART and 'Root' in d and 'Owner' in d:
                    result[tuple(d[k].value for k in ('x','y','z'))]=(unpack_pos_long(d['Root'].value),d['Owner'].value)
        return result

def check(output,oracle_path,source):
    if not source.is_file() or hashlib.sha256(source.read_bytes()).hexdigest()!='c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9':
        raise ValueError('IMMUTABLE_MODDED_INPUT_HASH_MISMATCH')
    oracle=json.loads(oracle_path.read_bytes())
    physical=json.loads((DEFAULT_RESOURCES/'physical-footprints.json').read_bytes())['families']
    compiled,_,_=compile_modded_rules(DEFAULT_RESOURCES)
    by_raw={}
    for rule in compiled:
        match=re.search(r'raw rule (\d+)$',rule.source_reference or '')
        if match:by_raw.setdefault(int(match.group(1)),[]).append(rule)
    permitted_roots={(r['dimension'],tuple(o['canonical_root'])):o['expected_logical_state']
                     for r in oracle['occurrences'] for o in r['outputs']}
    actual=WorldReader(output);before=WorldReader(source)
    families={f:{'expected':v['expected_source_occurrences'],'logical':0,'foreign_conflicts':0,'fragmented':0}
              for f,v in oracle['families'].items()}
    failures=[];foreign=[];unresolved_membership=[]
    try:
        rows=sorted(oracle['occurrences'],key=lambda r:(r['dimension'],r['origin'][0]//16,r['origin'][2]//16,r['origin'][1]))
        for number,row in enumerate(rows):
            if number%2500==0:print('protected world gate',number,'/',len(rows),flush=True)
            dim=row['dimension']
            original=[(c,before.state(dim,tuple(c['position']))[1]) for c in row['source_cells']]
            # Only positively identified foreign edits qualify for this exemption.
            # Air, missing chunks and unchanged vanilla source carriers are NOT
            # silently declared foreign to make the gate pass.
            conflicts=[{'position':c['position'],'state':s} for c,s in original if s and
                       not s.startswith('bloodborne_blocks:') and s not in {'minecraft:air','minecraft:cave_air','minecraft:void_air',c['state']}]
            if conflicts:
                changed=[c for c in conflicts if actual.state(dim,tuple(c['position']))[1]!=c['state']]
                if changed:failures.append({'gate':'FOREIGN_EDIT_CHANGED','cells':changed})
                foreign.append({'gate':'PROTECTED_ORACLE_FOREIGN_CONFLICT','origin':row['origin'],'cells':conflicts})
                for o in row['outputs']:families[o['family']]['foreign_conflicts']+=1
                continue
            membership=technical_membership(row,before,by_raw.get(row['rule'],()))
            if membership is None:
                unresolved_membership.append({'origin':row['origin'],'rule':row['rule'],'reason':'no_complete_exact_modded_assembly'})
            cells=set(tuple(c['position']) for c in row['source_cells']) | (membership or set())
            fragment_errors=independent_fragment_errors(actual,dim,cells,permitted_roots)
            for o in row['outputs']:
                root=tuple(o['canonical_root']);expected=o['expected_logical_state'];state=actual.state(dim,root)[1]
                errors=[]
                if state!=expected:errors.append({'reason':'expected_logical_root_missing','actual':state})
                else:
                    state_key=expected.partition('[')[2].rstrip(']')
                    for offset in physical[o['family']][state_key]['cells']:
                        p=add(root,offset)
                        if p==root:continue
                        if actual.state(dim,p)[1]!=PART or actual.helper_owner(dim,p)!=(root,expected.split('[')[0]):
                            errors.append({'reason':'physical_helper_missing_or_wrong_owner','position':p})
                errors.extend(fragment_errors)
                if membership is None:errors.append({'reason':'technical_membership_not_proven'})
                if errors:
                    families[o['family']]['fragmented']+=1
                    failures.append({'family':o['family'],'root':root,'errors':errors})
                else:families[o['family']]['logical']+=1
        n=sum(v['fragmented'] for v in families.values())
        return {'result':'FAIL' if failures else 'INCOMPLETE',
                'NO_COMPOSITE_FRAGMENTATION':'FAIL' if failures else 'NOT_PROVEN',
                'PROTECTED_WORLD_OBJECT_PRESERVED':'FAIL' if failures else 'PASS',
                'scope':'Protected exact source-pattern occurrences only; compatibility assembly gate still required.',
                'fragmented':n,'families':families,'failures':failures,'foreign_conflicts':foreign,
                'unresolved_technical_membership':unresolved_membership,
                'oracle_sha256':hashlib.sha256(oracle_path.read_bytes()).hexdigest()}
    finally:actual.close();before.close()

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('world',type=Path);p.add_argument('report',type=Path)
    a=p.parse_args();result=check(a.world,ROOT/'docs/composite-grid-repair/protected-world-oracle.json',ROOT/'reference-inputs/latest-modded-world.zip')
    a.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(result['result'],'fragmented',result['fragmented'])
    raise SystemExit(result['result']!='PASS')
