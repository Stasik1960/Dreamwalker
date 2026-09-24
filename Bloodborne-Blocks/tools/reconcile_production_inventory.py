"""Join the captured pre-pruning inventory to actual full-source evidence."""
from collections import Counter,defaultdict
import gzip,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def read(path):return json.loads(gzip.decompress(path.read_bytes()) if path.suffix=='.gz' else path.read_bytes())
def build():
    inventory=read(ROOT/'docs/production-palette-inventory.json')
    usage=read(ROOT/'docs/production-source-usage.json.gz')
    manifest=read(ROOT/'docs/production-logical-palette.json')
    frozen=read(ROOT/'docs/production-authoring-inputs.json.gz')
    source_defs={d['id']:d for d in frozen['definitions']['blocks']}
    targets=defaultdict(list)
    for pattern in usage['exact_reviewed_patterns']:
        for ident in {t['id'] for t in pattern['targets']}:targets[ident].append(pattern)
    counts=Counter()
    for state in usage['states']:counts[state['id']]+=state['count']
    production={o['id']:o for o in manifest['objects']}
    excluded={o['id']:o for o in manifest['excluded']}
    status={'REMOVED_DUPLICATE':'DUPLICATE','REMOVED_FRAGMENT':'SUSPICIOUS_FRAGMENT','REMOVED_SUPERSEDED':'SUPERSEDED','REMOVED_COMPATIBILITY':'COMPATIBILITY_ONLY','UNRESOLVED':'NEEDS_SEMANTIC_REVIEW'}
    for row in inventory['inventory']:
        ident=row['id'];patterns=targets[ident]
        row['source_usage']={'scope':'all 33 Yharnam terrain regions',
            'exact_pattern_occurrences':sum(p['count'] for p in patterns) if patterns else None,
            'exact_pattern_signatures':[p['signature'] for p in patterns],
            'source_carrier_cells':counts[source_defs[ident].get('source')],
            'warning':'carrier cells are NOT semantic object occurrences; missing reviewed pattern yields null, not zero'}
        row['provisional_status_before_reconciliation']=row.get('provisional_status_before_reconciliation',row['provisional_status'])
        row['provisional_status']='KEEP' if ident in production else status[excluded[ident]['status']]
        row['final_runtime_registry_item']=ident in production
        row['authoritative_modded_world_requires_compatibility']=False
        row['referenced_by_production_migration']=any(s['migration_source_pattern'] for f in read(ROOT/'src/main/resources/bloodborne_blocks/logical/contracts-v2.json')['families'] if f['id']==ident for s in f['states'].values())
    inventory['status_counts']=dict(Counter(r['provisional_status'] for r in inventory['inventory']))
    inventory['phase']='pre-pruning structural inventory reconciled with full usage and final decisions; original baseline separate'
    (ROOT/'docs/production-palette-inventory.json').write_text(json.dumps(inventory,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(inventory['status_counts'])
if __name__=='__main__':build()
