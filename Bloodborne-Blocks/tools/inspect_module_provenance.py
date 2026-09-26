"""Read-only exact forward-map evidence for a specific historical module cell."""
import json
from composite_world_oracle import EvidenceReader, ROOT
from source_mapping_archive import archive

def inspect(point):
    source=EvidenceReader(ROOT/'reference-inputs/source-world.zip')
    modded=EvidenceReader(ROOT/'reference-inputs/latest-modded-world.zip')
    try:
        status,current=modded.state('minecraft:overworld',point)
        if status!='FOUND' or not current:raise ValueError('Missing MODDED fixture')
        name,_,suffix=current.partition('[')
        props=dict(v.split('=',1) for v in suffix.rstrip(']').split(',') if v)
        rows=[]
        for ident,entry in archive()['v2\\migration.json'].items():
            for state_key,parts in entry['states'].items():
                if not isinstance(parts,list):continue
                for part in parts:
                    if 'bloodborne_blocks:'+part['id']!=name or part.get('properties',{})!=props:continue
                    origin=tuple(point[i]-part['offset'][i] for i in range(3))
                    found,actual=source.state('eh_s2:yharnam',origin)
                    if found!='FOUND' or not actual:continue
                    actual_name,_,actual_suffix=actual.partition('[')
                    expected=dict(v.split('=',1) for v in state_key.split(',') if v)
                    actual_props=dict(v.split('=',1) for v in actual_suffix.rstrip(']').split(',') if v)
                    if actual_name!='minecraft:'+ident or any(expected.get(k)!=v for k,v in actual_props.items()):continue
                    mismatches=[]
                    for component in parts:
                        p=tuple(origin[i]+component['offset'][i] for i in range(3))
                        expected_component='bloodborne_blocks:'+component['id']
                        if component.get('properties'):
                            expected_component+='['+','.join(k+'='+v for k,v in sorted(component['properties'].items()))+']'
                        observed=modded.state('minecraft:overworld',p)[1]
                        if observed!=expected_component:mismatches.append({'position':p,'expected':expected_component,'actual':observed})
                    rows.append({'origin':origin,'source_state':actual,'archived_state':state_key,
                                 'parts':len(parts),'mismatches':mismatches})
        return {'position':point,'modded_state':current,'matching_forward_sources':rows,
                'scope':'Evidence only; identical module states may have multiple producers. Not an ownership assignment.'}
    finally:source.close();modded.close()

if __name__=='__main__':
    results=[inspect(p) for p in ((-540,42,-33),(-332,77,-137))]
    (ROOT/'docs/composite-grid-repair/module-provenance.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf8')
    for row in results:
        print(row['position'],[(s['origin'],s['source_state'],s['parts'],len(s['mismatches'])) for s in row['matching_forward_sources']])
