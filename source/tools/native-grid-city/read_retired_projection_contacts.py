"""Read only legacy projected members whose derived queue has been retired."""
import json
import sqlite3
import migrate as m
import native_resume as n
import native_certificates as c

database=m.DEFAULT_PLAN/'plan.sqlite'
db=sqlite3.connect(database.as_uri()+'?mode=ro',uri=True,timeout=3)
db.execute('PRAGMA query_only=ON');db.execute('BEGIN')
c.attach_source_journal_original(m,db)
try:
    cursor=m.meta(db,'nativeLocalRetention');seen=set();issues=[]
    events=[(sequence,json.loads(data)) for sequence,data in db.execute('SELECT sequence,data FROM source_local_retentions ORDER BY sequence')]
    while cursor:
        receipt=n.read_bound(m,cursor);preview=n.read_bound(m,receipt['preview'])
        for case in preview['cases']:
            for owner in case['retainedOwners']:
                for policy in owner.get('memberPolicies',[]):
                    if policy['policy']=='original':continue
                    dim=owner['dimension'];at=tuple(policy['position']);args=(dim,*at);key=(dim,owner['uuid'],at)
                    if key in seen:continue
                    seen.add(key)
                    queue=db.execute('SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone()
                    if queue is not None:continue
                    demands={table:bool(db.execute('SELECT 1 FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=? LIMIT 1',args).fetchone()) for table in ('fragments','substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots')}
                    matches=[]
                    for sequence,event in events:
                        zone=event.get('sourceZone',{})
                        inside=zone.get('dimension')==dim and all(zone['minimum'][i]<=at[i]<=zone['maximum'][i] for i in range(3))
                        domains=[d for d in event.get('originalDomains',[]) if d['object']['dim']==dim and d['object']['uuid']==owner['uuid']]
                        members=[d for d in domains if list(at) in d.get('cells',[]) or any(p.get('position')==list(at) for p in d.get('points',[]))]
                        if inside or domains:matches.append({'sequence':sequence,'insideSourceZone':inside,'originalOwnerDomains':len(domains),'domainMemberMatches':len(members),'domainKeys':[list(d) for d in domains]})
                    original_points=[point for _,event in events for domain in event.get('originalDomains',[]) if domain['object']['dim']==dim and domain['object']['uuid']==owner['uuid'] for point in domain['domain'] if point['position']==list(at)]
                    issues.append({'dimension':dim,'position':list(at),'uuid':owner['uuid'],'policy':policy['policy'],'policySourceState':policy['sourceState'],'policyStoredContributions':policy['storedContributions'],'actualOldStoredContributions':n.row_dict(db,'SELECT * FROM old.stored_contributions WHERE dim=? AND x=? AND y=? AND z=?',args),'savedOriginalPointStates':[{key:value for key,value in point.items() if key in ('position','state','sourceState')} for point in original_points],'demands':demands,'matchingEvents':matches,'protected':n.row_dict(db,'SELECT uuid,reason FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=?',args)})
        cursor=receipt.get('previousRetention')
    print(json.dumps({'status':'READ_ONLY_DIAGNOSTIC','projectedMembersInspected':len(seen),'missingQueue':issues,'databaseWrites':0,'mapWrites':0},ensure_ascii=False,indent=2))
finally:db.close()
