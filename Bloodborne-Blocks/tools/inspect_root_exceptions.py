"""Read-only evidence for the two user-authorized root conflicts. Never migrates."""
import json
from composite_world_oracle import ROOT, EvidenceReader

CASES = (((-374,73,-291),'o_bench'), ((-560,98,-9),'o_high_balustrade'))

def inspect():
    oracle=json.loads((ROOT/'docs/composite-grid-repair/protected-world-oracle.json').read_bytes())
    readers=[EvidenceReader(ROOT/'reference-inputs'/name) for name in ('source-world.zip','latest-modded-world.zip')]
    result=[]
    try:
        for root,family in CASES:
            matches=[r for r in oracle['occurrences'] if any(o['family']==family and tuple(o['canonical_root'])==root for o in r['outputs'])]
            if len(matches)!=1: raise ValueError(('AMBIGUOUS_SOURCE_ASSEMBLY',root,family,len(matches)))
            assembly=matches[0]
            candidates=[]
            for delta in ((0,1,0),(0,-1,0),(1,0,0),(-1,0,0),(0,0,1),(0,0,-1)):
                point=tuple(a+b for a,b in zip(root,delta))
                owners=[{'family':o['family'],'root':o['canonical_root']} for row in oracle['occurrences'] for o in row['outputs'] if list(point) in o['physical_cells']]
                candidates.append({'position':point,'delta':delta,
                    # Oracle output dimensions are MODDED dimensions; source
                    # Yharnam was historically mapped into the overworld.
                    'source':readers[0].state('eh_s2:yharnam',point),
                    'modded':readers[1].state('minecraft:overworld',point),
                    'own_source_cell':any(tuple(c['position'])==point for c in assembly['source_cells']),
                    'physical_owners':owners})
            result.append({'root':root,'family':family,'source_cells':assembly['source_cells'],
                'candidates':candidates,'status':'EVIDENCE_ONLY_NOT_AUTHORIZED_FOR_AUTOMATIC_RELOCATION'})
    finally:
        for reader in readers:reader.close()
    return {'scope':'Only the two explicitly authorized exceptions; no generic root-shift fallback.',
        'required_proofs':['unchanged world-space mesh at every rotation','own pivot without translation drift',
            'neighbor is own assembly or proven free','no footprint expansion','other object unchanged',
            'shared manual/converter anchor policy','idempotent second conversion'], 'cases':result}

if __name__=='__main__':
    report=inspect()
    (ROOT/'docs/composite-grid-repair/root-exception-evidence.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
    for case in report['cases']:
        print(case['family'],case['root'])
        for c in case['candidates']:print(c['position'],'own source:',c['own_source_cell'],'modded:',c['modded'],'owners:',c['physical_owners'])
