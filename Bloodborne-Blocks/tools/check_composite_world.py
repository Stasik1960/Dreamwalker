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
from logical_contract_v2 import load_contracts

def technical_membership(row,before,rules):
    """Require a complete exact MODDED assembly, not a raw carrier heuristic."""
    origin=tuple(row['origin']);dim=row['dimension'];matched=[]
    for rule in rules:
        rule_origin=rule.allowed_origins[0][1] if rule.atomic_owner_group else origin
        if not rule.accepts_origin(dim,rule_origin):continue
        if any(before.state(dim,add(rule_origin,p.offset))[1]!=key(p.state) for p in rule.required_context):continue
        if not guards_match(rule.variant_guards,rule_origin):continue
        cells=[(add(rule_origin,p.offset),key(p.state)) for p in (rule.source,)+rule.members]
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

def shared_helper_matches(actual,dim,p,owner,expected_bindings,permitted_roots):
    bindings=actual.helper_owners(dim,p)
    if not bindings or owner not in bindings:return False
    # Plural/root carriers require the entire exact expected binding set,
    # not merely finding this object's name somewhere in untrusted NBT.
    carrier=actual.state(dim,p)[1]
    if len(bindings)>1 or len(expected_bindings.get((dim,p),()))>1 or carrier!=PART:
        if bindings!=expected_bindings.get((dim,p),set()):return False
        if carrier!=PART and (dim,p) not in permitted_roots:return False
        if carrier!=PART and carrier!=permitted_roots[(dim,p)]:return False
        for root,name in bindings:
            expected=permitted_roots.get((dim,root))
            if not expected or expected.split('[')[0]!=name or actual.state(dim,root)[1]!=expected:return False
    return True

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
        owners=self.helper_owners(dim,pos)
        return next(iter(owners)) if owners and len(owners)==1 else None

    def helper_owners(self,dim,pos):
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
                    from city_palette import helper_bindings
                    try:owners=frozenset(helper_bindings(d))
                    except ValueError:owners=None
                    result[tuple(d[k].value for k in ('x','y','z'))]=owners
        return result

def check(output,oracle_path,source):
    if not source.is_file() or hashlib.sha256(source.read_bytes()).hexdigest()!='c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9':
        raise ValueError('IMMUTABLE_MODDED_INPUT_HASH_MISMATCH')
    oracle=json.loads(oracle_path.read_bytes())
    contracts,_=load_contracts(DEFAULT_RESOURCES)
    physical={f['id']:{k:s['physical_footprint'] for k,s in f['states'].items()} for f in contracts['families']}
    compiled,_,_=compile_modded_rules(DEFAULT_RESOURCES)
    from atomic_owner_groups import compile_groups
    compiled,group_diagnostics=compile_groups(compiled,DEFAULT_RESOURCES)
    group_rules={r.transaction_id:r for r in compiled if r.atomic_owner_group}
    by_occurrence={}
    for group in group_diagnostics['groups']:
        incomplete={tuple(e['origin']) for e in group['errors'] if 'origin' in e}
        for row in group['occurrences']:
            if tuple(row['origin']) not in incomplete:
                by_occurrence[(row['rule'],tuple(row['origin']))]=group_rules[group['transaction']]
    by_raw={}
    for rule in compiled:
        match=re.search(r'raw rule (\d+)$',rule.source_reference or '')
        if match:by_raw.setdefault(int(match.group(1)),[]).append(rule)
    actual=WorldReader(output);before=WorldReader(source)
    families={f:{'expected':v['expected_source_occurrences'],'logical':0,'foreign_conflicts':0,'fragmented':0}
              for f,v in oracle['families'].items()}
    failures=[];foreign=[];unresolved_membership=[]
    try:
        # Persisted state defaults preserve old oracle identities. Relocation
        # is recognized only for an exact, coordinate-guarded source assembly.
        for row in oracle['occurrences']:
            for o in row['outputs']:
                if o['family'] not in {'o_bench','o_high_balustrade'}:continue
                name,_,suffix=o['expected_logical_state'].partition('[')
                props=dict(p.split('=',1) for p in suffix.rstrip(']').split(',') if p)
                props['root_anchor']='canonical'
                o['expected_logical_state']=name+'['+','.join(k+'='+v for k,v in sorted(props.items()))+']'
                for rule in by_raw.get(row['rule'],()):
                    if not rule.allowed_origins or rule.target[0]!=name or not rule.accepts_origin(row['dimension'],tuple(row['origin'])):continue
                    if all(before.state(row['dimension'],add(row['origin'],p.offset))[1]==key(p.state) for p in (rule.source,)+rule.members+rule.required_context):
                        o['canonical_root']=list(add(row['origin'],rule.root_offset))
                        o['expected_logical_state']=key(rule.target)
                        break
        permitted_roots={(r['dimension'],tuple(o['canonical_root'])):o['expected_logical_state']
                         for r in oracle['occurrences'] for o in r['outputs']}
        for rule in group_rules.values():
            dim,origin=rule.allowed_origins[0]
            for output in rule.outputs:
                permitted_roots[(dim,add(origin,output.root_offset))]=key(output.target)
        # Root exceptions are read from the same complete group identity,
        # never inferred from an arbitrary nearby output block.
        for row in oracle['occurrences']:
            rule=by_occurrence.get((row['rule'],tuple(row['origin'])))
            if rule is None:continue
            for output in row['outputs']:
                for out in rule.outputs:
                    p=add(rule.allowed_origins[0][1],out.root_offset)
                    if (out.target[0]=='bloodborne_blocks:'+output['family'] and
                        dict(out.target[1]).get('root_anchor')=='upper' and
                        p==add(tuple(output['canonical_root']),(0,1,0))):
                        output['canonical_root']=list(p);output['expected_logical_state']=key(out.target)
        expected_bindings={}
        for rule in group_rules.values():
            dim,origin=rule.allowed_origins[0]
            for out in rule.outputs:
                root=add(origin,out.root_offset)
                for offset in out.shape:
                    if offset!=(0,0,0):expected_bindings.setdefault((dim,add(root,offset)),set()).add((root,out.target[0]))
        for row in oracle['occurrences']:
            for out in row['outputs']:
                root=tuple(out['canonical_root']);name,_,suffix=out['expected_logical_state'].partition('[')
                for offset in physical[out['family']][suffix.rstrip(']')]['cells']:
                    if tuple(offset)!=(0,0,0):expected_bindings.setdefault((row['dimension'],add(root,offset)),set()).add((root,name))
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
            group_rule=by_occurrence.get((row['rule'],tuple(row['origin'])))
            membership=technical_membership(row,before,[group_rule] if group_rule else by_raw.get(row['rule'],()))
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
                        if not shared_helper_matches(actual,dim,p,(root,expected.split('[')[0]),expected_bindings,permitted_roots):
                            errors.append({'reason':'physical_helper_missing_or_wrong_owner','position':p})
                errors.extend(fragment_errors)
                if membership is None:errors.append({'reason':'technical_membership_not_proven'})
                if errors:
                    families[o['family']]['fragmented']+=1
                    failures.append({'family':o['family'],'root':root,'errors':errors})
                else:families[o['family']]['logical']+=1
        n=sum(v['fragmented'] for v in families.values())
        group_failures=[];group_passed=0
        for group in group_diagnostics['groups']:
            rule=group_rules[group['transaction']];dim,origin=rule.allowed_origins[0]
            errors=list(group['errors'])
            errors.extend(independent_fragment_errors(actual,dim,
                {add(origin,p.offset) for p in (rule.source,)+rule.members},permitted_roots))
            for out in rule.outputs:
                root=add(origin,out.root_offset)
                if actual.state(dim,root)[1]!=key(out.target):
                    errors.append({'reason':'whole_owner_root_missing','root':root,'expected':key(out.target)})
                    continue
                for offset in out.shape:
                    p=add(root,offset)
                    if p!=root and not shared_helper_matches(actual,dim,p,(root,out.target[0]),expected_bindings,permitted_roots):
                        errors.append({'reason':'whole_owner_helper_missing','position':p})
            if errors:group_failures.append({'transaction':rule.transaction_id,'errors':errors})
            else:group_passed+=1
        return {'result':'FAIL' if failures else 'INCOMPLETE',
                'NO_COMPOSITE_FRAGMENTATION':'FAIL' if failures else 'NOT_PROVEN',
                'PROTECTED_WORLD_OBJECT_PRESERVED':'FAIL' if failures else 'PASS',
                'scope':'Protected exact source-pattern occurrences only; compatibility assembly gate still required.',
                'fragmented':n,'families':families,'failures':failures,'foreign_conflicts':foreign,
                'unresolved_technical_membership':unresolved_membership,
                'atomic_owner_groups':{'total':len(group_rules),'passed':group_passed,'failures':group_failures},
                'oracle_sha256':hashlib.sha256(oracle_path.read_bytes()).hexdigest()}
    finally:actual.close();before.close()

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('world',type=Path);p.add_argument('report',type=Path)
    a=p.parse_args();result=check(a.world,ROOT/'docs/composite-grid-repair/protected-world-oracle.json',ROOT/'reference-inputs/latest-modded-world.zip')
    a.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(result['result'],'fragmented',result['fragmented'])
    raise SystemExit(result['result']!='PASS')
