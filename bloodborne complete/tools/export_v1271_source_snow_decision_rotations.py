"""Read-only exact saved target rotation evidence for the24 optical poses."""
import json,sqlite3
from prepare_v1271_city_compatibility import ROOT,read,write,sha

if __name__=='__main__':
    plan=ROOT/'build/v1271-city-preparation/source-plan.sqlite';frequency=read(plan.parent/'decision-frequency-proof.json')['decisionCellCounts'];rows=[]
    con=sqlite3.connect(f'file:{plan}?mode=ro',uri=True)
    for i,raw in con.execute('SELECT id,data FROM decisions'):
        d=json.loads(raw)
        if d.get('reason')!='EXACT_SOURCE_NATIVE_OPTICAL_CARRIER_POSE':continue
        rows.append({'decisionId':i,'sourceState':d['sourceState'],'registryId':d['registryId'],'properties':d['properties'],'sourceApplications':d['sourceApplications'],'sourceCells':frequency[str(i)]})
    con.close();assert len(rows)==48 and sum(r['sourceCells'] for r in rows)==2905
    write(ROOT/'build/final-geometry/source-snow-current-target-rotations.json',{'status':'READ_ONLY_SAVED_PREPARED_ROTATION_EVIDENCE','sourcePlanSha256':sha(plan),'rows':rows,'positiveRows':[r for r in rows if r['sourceCells']],'sourceCells':2905,'sourceArraysRescanned':False})
