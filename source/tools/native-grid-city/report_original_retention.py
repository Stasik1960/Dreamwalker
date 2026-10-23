"""Reporting only: committed plan/journal counts, no world or old DB reads."""
import argparse
import collections
import json
import sqlite3
from pathlib import Path
import migrate as m


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--plan',type=Path,default=m.DEFAULT_PLAN);parser.add_argument('--out',type=Path);parser.add_argument('--provisional',action='store_true');parser.add_argument('--include-plan-remainder',action='store_true');args=parser.parse_args();plan=m.checked(args.plan)
    out=m.checked(args.out or plan/'full/original-retention-summary.json');m.require(not out.exists(),'Choose a new report path; keep prior snapshots immutable')
    db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix()+'?mode=ro',uri=True)
    try:
        db.execute('PRAGMA query_only=ON');db.execute('BEGIN');generation=m.meta(db,'compositionGeneration');m.require(generation or args.provisional,'Run after final composition, or explicitly label the snapshot --provisional')
        m.require(generation or not args.include_plan_remainder,'The optional plan-remainder grouping requires final composition')
        binding=m.meta(db,'binding');head=m.meta(db,'sourceLocalRetentionJournal');cursor=m.meta(db,'nativeComposeCursor');previous=None;events=0;domain_occurrences=0;domain_cell_occurrences=0;zone_events=0;owners={};zones={};members=set();native_cells=set();groups={};event_kinds=collections.Counter();retired=collections.Counter();pose_registries={}
        for sequence,encoded,digest in db.execute('SELECT sequence,data,digest FROM source_local_retentions ORDER BY sequence'):
            event=json.loads(encoded);events+=1;m.require(sequence==events and m.hashlib.sha256(encoded.encode()).hexdigest()==digest and event['sourceBinding']==binding and event['previous']==previous,'Committed journal encoding/head chain changed during reporting')
            event_kinds[event['kind']]+=1;zone=event.get('sourceZone');mode='aligned_source_section' if zone else 'direct_original_contact'
            if zone:
                zone_events+=1;key=(zone['dimension'],*zone['minimum']);record=zones.setdefault(key,{'dimension':zone['dimension'],'minimum':zone['minimum'],'maximum':zone['maximum'],'eventOccurrences':0});m.require(record['maximum']==zone['maximum'] and all(value%16==0 for value in zone['minimum']) and zone['maximum']==[value+15 for value in zone['minimum']],'Unknown/nonaligned original section');record['eventOccurrences']+=1
            for domain in event['originalDomains']:
                obj=domain['object'];identity=(obj['dim'],obj['uuid']);metadata=(obj['kind'],obj.get('reason'),obj['pose']);domain_occurrences+=1;domain_cell_occurrences+=len(domain['domain'])
                record=owners.setdefault(identity,{'metadata':metadata,'occurrences':0,'modes':set()});m.require(record['metadata']==metadata,'Original owner classification changed across committed events');record['occurrences']+=1;record['modes'].add(mode)
                if obj['pose'] not in pose_registries:
                    row=db.execute('SELECT data FROM poses WHERE key=?',(obj['pose'],)).fetchone();m.require(row,'Journal owner pose missing from current plan');pose_registries[obj['pose']]=json.loads(row[0])['registryId']
                key=(obj['kind'],obj.get('reason'),pose_registries[obj['pose']]);group=groups.setdefault(key,{'owners':set(),'occurrences':0,'cells':set()});group['owners'].add(identity);group['occurrences']+=1
                for point in domain['domain']:
                    cell=(obj['dim'],*point['position']);members.add(cell);group['cells'].add(cell)
            for cell in event.get('originalNativeCells',[]):native_cells.add((cell['dimension'],*cell['position']))
            for point in event.get('pointPlan',[]):
                if point['retire']:
                    for queue in point['before']['queue']:retired['processed_'+str(queue['processed'])]+=1
            previous={'schemaVersion':1,'events':sequence,'head':digest,'sourceBinding':binding}
        m.require(previous==head,'Journal report is not bound to the committed head')
        outside={cell for cell in members|native_cells if (cell[0],*(value//16*16 for value in cell[1:])) not in zones}
        protected_rows=[{'reason':reason,'planRows':count} for reason,count in db.execute('SELECT reason,count(*) FROM protected_cells GROUP BY reason ORDER BY reason')]
        owner_counts=dict(db.execute('SELECT reason,count(*) FROM (SELECT DISTINCT reason,dim,uuid FROM protected_cells) GROUP BY reason'))
        cell_counts=dict(db.execute('SELECT reason,count(*) FROM (SELECT DISTINCT reason,dim,x,y,z FROM protected_cells) GROUP BY reason'))
        for row in protected_rows:row.update(uniqueOwnerIdentities=owner_counts[row['reason']],uniqueCoordinateCells=cell_counts[row['reason']])
        protected_groups=[{'objectKind':kind,'originalObjectReason':reason,'uniqueOwnerIdentities':count} for kind,reason,count in db.execute('SELECT o.kind,o.reason,count(*) FROM (SELECT DISTINCT dim,uuid FROM protected_cells) p LEFT JOIN objects o ON o.dim=p.dim AND o.uuid=p.uuid GROUP BY o.kind,o.reason ORDER BY o.kind,o.reason')]
        retained=m.meta(db,'nativeRetainedComponents') or [];legacy_members={(row['dimension'],*at) for row in retained for at in row['mustRemainOriginalCells']}
        remainder=None
        if args.include_plan_remainder:
            remainder={'definition':'Plan objects without a cleanup_owners entry; includes ordinary/native substrate and may include converted base geometry. This is NOT a count of problematic decorative objects or all remaining old-format blocks.',
                       'groups':[{'kind':kind,'reason':reason,'sourceStatus':status,'objects':count} for kind,reason,status,count in db.execute('SELECT o.kind,o.reason,o.source_status,count(*) FROM objects o WHERE NOT EXISTS(SELECT 1 FROM cleanup_owners c WHERE c.dim=o.dim AND c.uuid=o.uuid) GROUP BY o.kind,o.reason,o.source_status ORDER BY o.kind,o.reason,o.source_status')]}
        report={'schemaVersion':1,'status':'PROVISIONAL_COMMITTED_PLAN_REPORT_NOT_ACCEPTANCE' if args.provisional else 'FINAL_COMPOSITION_PLAN_REPORT_NOT_ACCEPTANCE','plan':str(plan),'sourceBinding':binding,'classification':m.meta(db,'classification'),'compositionGeneration':generation,'nativeComposeCursor':cursor,'sourceLocalRetentionJournal':head,
                'localOriginalJournal':{'events':events,'eventsByKind':dict(event_kinds),'domainOccurrences':domain_occurrences,'uniqueObjectOwners':len(owners),'duplicateOwnerOccurrences':domain_occurrences-len(owners),'domainCellOccurrences':domain_cell_occurrences,'uniqueKnownObjectDomainCells':len(members),'unownedNativeSourceCells':len(native_cells),'zoneEvents':zone_events,'uniqueAlignedSections':len(zones),'repeatedSectionEvents':zone_events-len(zones),'exactSectionCells':4096*len(zones),'outsideSectionOriginalDomainCells':len(outside),'sectionAndKnownOriginalDomainCellUnion':4096*len(zones)+len(outside),'ownerCountsByRetentionMode':dict(collections.Counter(mode for record in owners.values() for mode in record['modes'])),'groups':[{'objectKind':kind,'originalObjectReason':reason,'registryId':registry,'uniqueOwners':len(group['owners']),'domainOccurrences':group['occurrences'],'uniqueKnownDomainCells':len(group['cells'])} for (kind,reason,registry),group in sorted(groups.items(),key=lambda pair:tuple(str(value) for value in pair[0]))],'sections':list(zones.values()),'retiredQueueRowOccurrencesByPreviousProcessed':dict(retired)},
                'protectedPlanProvenance':{'byProtectionReason':protected_rows,'ownerGroups':protected_groups,'uniqueOwnerIdentities':sum(row['uniqueOwnerIdentities'] for row in protected_groups),'meaning':'Provenance/protection rows may reserve visual cells. These counts are separate from exact source sections and known original domains; groups/reasons can overlap.'},
                'historicalRetentionMeta':{'nativeLocalRetention':m.meta(db,'nativeLocalRetention'),'retainedNativeComponents':len(retained),'uniqueStrictNativeMemberCells':len(legacy_members),'nativeWholeTemplateFallback':m.meta(db,'nativeWholeTemplateFallback'),'nativeVisualProtectionCorrection':m.meta(db,'nativeVisualProtectionCorrection'),'nativeRetainedFunctionalVisualCorrection':m.meta(db,'nativeRetainedFunctionalVisualCorrection')},'optionalPlanObjectsWithoutCleanup':remainder,
                'limits':'Journal owners count only committed automatic local retentions, not every original object left in the map. Known domain/envelope cells are not claimed to be an exact physical runtime footprint. No initial candidate census is repeated or recomputed. No gameplay/performance/acceptance claim.',
                'sourceFiles':{'migrate':m.sha(m.__file__),'nativeResume':m.sha(Path(m.__file__).with_name('native_resume.py')),'nativeCertificates':m.sha(Path(m.__file__).with_name('native_certificates.py'))},'reportSourceSha256':m.sha(__file__),'databaseWrites':0,'oldDatabaseAttached':False,'sourceWorldReads':0,'acceptanceGate':False}
        m.require(db.total_changes==0 and db.execute('PRAGMA query_only').fetchone()==(1,) and m.meta(db,'sourceLocalRetentionJournal')==head and m.meta(db,'nativeComposeCursor')==cursor,'Read-only report changed committed state');db.rollback()
    finally:db.close()
    m.atomic_json(out,report);print(report['status'],{'journalOwners':len(owners),'events':events,'uniqueSections':len(zones),'sectionAndKnownDomainCells':4096*len(zones)+len(outside),'protectedOwnerIdentities':report['protectedPlanProvenance']['uniqueOwnerIdentities']})


if __name__=='__main__':main()
