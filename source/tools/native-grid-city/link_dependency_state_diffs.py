"""Link the three saved addressed state diffs to exact pruned ledger cells."""
import pilot_dependencies as p
rows=p.m.read(p.REPAIR/'shard-semantic-difference.json')['shards'];refs={}
for row in rows:
    for cell in row['lostCells']:
        fields=p.m.compound(p.untyped(cell['position']));at=tuple(fields['pos'].value);refs.setdefault(at,[]).append(cell['uuid'])
result=[]
for cx,cz in ((18,-57),(18,-56),(18,-55)):
    path=p.PLAN/'context-extension'/('addressed-state-difference-%d-%d.json'%(cx,cz));report=p.m.read(path)
    for diff in report['targetAgainstStage1']['nonDefaultDifferences']:result.append({'chunk':[cx,cz],**diff,'prunedLedgerOwners':refs.get(tuple(diff['position']),[])})
p.m.atomic_json(p.REPAIR/'state-difference-ledger-links.json',{'states':result,'notLinkedToLostContributions':[r for r in result if not r['prunedLedgerOwners']],'mapWrites':0,'databaseWrites':0})
