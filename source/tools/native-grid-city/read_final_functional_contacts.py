"""Root-run finite diagnostic of final retain_local base-state placements."""
import argparse
import collections
import json
import sqlite3
from pathlib import Path
import migrate as m
import native_resume as n
import native_certificates as c


COVERAGE_SHA='a7d0173a8d8fad5604e76c0585d9ae13308d58c0f17d17ac3dfa2117c079e7e4'
RAW_SHA='3af36baa112a022b79b4c8834b0523e38be747a5072d7acdd8e17ea450184ec5'


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--plan',type=Path,default=m.DEFAULT_PLAN);parser.add_argument('--out',type=Path);args=parser.parse_args();plan=m.checked(args.plan);out=m.checked(args.out or plan/'full/final-functional-contacts.json')
    m.control_check();m.require(not out.exists(),'Choose a fresh diagnostic output');coverage_ref={'path':str(plan/'full-native-input-coverage.json'),'sha256':COVERAGE_SHA};coverage=n.read_bound(m,coverage_ref)
    m.require(coverage['kind']=='ACTUAL_POST_ACTIVATION_NATIVE_INPUT_GATE' and coverage['scope']=='FULL_CITY' and coverage['compositionsSha256']==RAW_SHA and all(coverage[key]==0 for key in ('worldFilesRead','persistentDatabaseWrites','mapWrites')),'Use the actual completed FULL coverage only')
    raw=plan/'compositions.jsonl';m.require(m.sha(raw)==RAW_SHA,'Final raw changed after actual coverage')
    states={m.canonical(row['state']):row for row in coverage['nonPassiveBaseStates'] if row['decision']=='retain_local'};m.require(0<len(states)<=64,'Finite retain_local states are required');db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix()+'?mode=ro',uri=True);context=None;blocked=False
    try:
        db.execute('PRAGMA query_only=ON');db.execute('BEGIN');generation=m.meta(db,'compositionGeneration');m.require(generation==coverage['compositionGeneration'] and generation['compositionsSha256']==RAW_SHA and m.meta(db,'binding')==coverage['sourceBinding'],'Final plan generation/source differs from coverage')
        placeholders=','.join('?' for _ in states);compositions={}
        for key,encoded in db.execute("SELECT key,data FROM compositions WHERE json_extract(data,'$.baseState') IN ("+placeholders+') LIMIT 4097',tuple(states)):
            row=json.loads(encoded);m.require(m.canonical(row['baseState']) in states,'Composition base-state match changed');compositions[key]=row
        blocked=len(compositions)>4096;contacts=[];sections=[];owners={};state_counts=collections.Counter();table_counts=collections.Counter();source_mismatches=0;total=None if blocked else 0
        assembly=m.meta(db,'preservedAssemblyOverrides');manifest_ref=assembly['manifest'] if assembly else None;manifest=n.read_bound(m,manifest_ref) if manifest_ref else None;approved={}
        if manifest:
            m.require(manifest['sourceBinding']==coverage['sourceBinding'] and manifest['status']=='CONFIRMED_FINITE_ASSEMBLY_MANIFEST','Preserved manifest belongs to another source')
            for row in manifest['poseMatches']:approved.setdefault(row['pose'],set()).add(row['assemblyKey'])
        def owner(dim,uid):
            key=(dim,uid)
            if key not in owners:
                rows=n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',key);m.require(len(rows)<=1,'Ambiguous direct source owner');obj=rows[0] if rows else None
                owners[key]={'dimension':dim,'uuid':uid,'object':obj,'approvedAssemblyKeys':sorted(approved.get(obj['pose'],())) if obj else [],'selected':n.row_dict(db,'SELECT * FROM selected_objects WHERE dim=? AND uuid=?',key),'cleanupScheduled':n.row_dict(db,'SELECT * FROM cleanup_owners WHERE dim=? AND uuid=?',key)}
                m.require(len(owners)<=4096,'Direct functional contact owners exceed finite bound; stop without traversal')
            return owners[key]
        if compositions and not blocked:
            keys=tuple(compositions);where='composition IN ('+','.join('?' for _ in keys)+')'
            total=db.execute('SELECT count(*) FROM placements WHERE '+where,keys).fetchone()[0]
            sections=[{'dimension':dim,'minimum':[x*16,y*16,z*16],'cells':count} for dim,x,y,z,count in db.execute('SELECT dim,x>>4,y>>4,z>>4,count(*) FROM placements WHERE '+where+' GROUP BY dim,x>>4,y>>4,z>>4 ORDER BY dim,x>>4,y>>4,z>>4',keys)]
            blocked=total>4096
            if not blocked:
                c.attach_source_journal_original(m,db);context=c.SourceVisualReservations(m,db)
                for placement in n.row_dict(db,'SELECT * FROM placements WHERE '+where+' ORDER BY dim,x,y,z',keys):
                    dim=placement['dim'];at=[placement[key] for key in ('x','y','z')];point_args=(dim,*at);base=compositions[placement['composition']]['baseState'];state_counts[m.canonical(base)]+=1;work={};direct=set()
                    for table in ('fragments','whole_cells','substrate_fragments','native_visual_roots','native_visual_fragments','bridge_cells','protected_cells'):
                        rows=n.row_dict(db,'SELECT * FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=? LIMIT 4097',point_args);m.require(len(rows)<=4096,'One functional cell has too many direct rows; stop without model/owner traversal');work[table]=rows;table_counts[table]+=len(rows)
                        for row in rows:
                            if 'uuid' in row:direct.add(row['uuid']);owner(dim,row['uuid'])
                    root_positions={tuple(at)}|{(row['rx'],row['ry'],row['rz']) for row in work['native_visual_fragments']};roots=[]
                    for root in sorted(root_positions):roots.extend(n.row_dict(db,'SELECT * FROM objects INDEXED BY object_root WHERE dim=? AND x=? AND z=? AND y=? LIMIT 4097',(dim,root[0],root[2],root[1])))
                    m.require(len(roots)<=4096,'Too many direct source roots at one functional cell')
                    for obj in roots:direct.add(obj['uuid']);owner(dim,obj['uuid'])
                    state,be,proof=context.source_point(dim,at);source_state=json.loads(placement['source_state']);exact=source_state==base and state==base;source_mismatches+=int(not exact)
                    contacts.append({'dimension':dim,'position':at,'placement':placement,'baseState':base,'placementSourceStateEqualsBase':source_state==base,'cachedSourceStateEqualsBase':state==base,'work':work,'sourceRootObjects':roots,'directSourceOwnerUuids':sorted(direct),'approvedWholeContactOwnerUuids':sorted(uid for uid in direct if owners[(dim,uid)]['approvedAssemblyKeys'] or (owners[(dim,uid)]['object'] or {}).get('kind')=='whole'),
                                     'actualSource':{'state':state,'blockEntityPresent':be is not None,'typedBlockEntitySha256':context.source_be_digest(dim,at,be,proof),'chunkBinding':proof}})
        report={'schemaVersion':1,'status':'BLOCKED_FINITE_SEED_BOUND' if blocked else 'READ_ONLY_FINITE_FUNCTIONAL_CONTACTS_NOT_REPAIR','coverage':coverage_ref,'raw':{'path':str(raw),'sha256':RAW_SHA},'sourceBinding':coverage['sourceBinding'],'compositionGeneration':generation,'retainedStateDecisions':list(states.values()),'matchedCompositionKeys':list(compositions),'compositions':compositions,'affectedPlacementCells':total,'uniqueAlignedSections':len(sections),'sections':sections,'placementCellsByState':[{'state':json.loads(key),'cells':count} for key,count in sorted(state_counts.items())],'directWorkRowCounts':dict(table_counts),'directOwners':list(owners.values()),'contacts':contacts,'sourceStateMismatches':source_mismatches,'approvedAssemblyManifest':manifest_ref,
                'approvedWholeDirectContactCells':sum(bool(row['approvedWholeContactOwnerUuids']) for row in contacts),'wholeRoleContactCells':sum(bool(row['work']['whole_cells']) for row in contacts),
                'limits':'Only final derived placements with an exact retain_local base state from the bound coverage. Cached source/BE reads are capped at 4096 affected cells. Direct indexed owners only; no owner/model traversal, old discovery scan, source world read, repair or acceptance claim. On bound exceeded, root must choose a smaller diagnostic scope before source reads.','nativeComposeCursor':m.meta(db,'nativeComposeCursor'),'sourceLocalRetentionJournal':m.meta(db,'sourceLocalRetentionJournal'),'reportSourceSha256':m.sha(__file__),'databaseWrites':0,'mapWrites':0,'worldFilesRead':0,'sourceCachedPositionsRead':len(contacts),'ownerGraphTraversal':False,'acceptanceGate':False}
        m.require(db.total_changes==0 and db.execute('PRAGMA query_only').fetchone()==(1,) and m.meta(db,'compositionGeneration')==generation,'Read-only diagnostic changed final plan');db.rollback()
    finally:
        if context:context.close()
        db.close()
    m.atomic_json(out,report);print(report['status'],{'states':len(states),'compositionKeys':len(compositions),'cells':total,'sections':len(sections),'sourceStateMismatches':source_mismatches,'directWorkRows':dict(table_counts)})
    return 2 if blocked else 0


if __name__=='__main__':raise SystemExit(main())
