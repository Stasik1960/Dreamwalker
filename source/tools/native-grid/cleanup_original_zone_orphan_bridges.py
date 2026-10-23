"""Root-only atomic removal of diagnostic-bound derived orphan bridge rows.

No world writer or planner/schema connection. Original discovery is attached
mode=ro. Only exact bridge rows and one commit-evidence meta reference change.
Receipt bytes are fsynced before COMMIT; the matching meta reference is commit
authority, so an interrupted uncommitted receipt must never be used alone.
"""
import argparse, hashlib, json, sqlite3, sys
from pathlib import Path
sys.dont_write_bytecode=True
SOURCE=Path(__file__).resolve().parents[2];ROOT=SOURCE.parent
sys.path.insert(0,str(SOURCE/'tools/native-grid-city'))
import migrate as m
import native_resume as n
import native_certificates as c

EXPECTED={'migrate':'38848f33d11c739a049bf0f2a28f5bea784dc5cb90a274e20a46b4cd70ac860a',
          'nativeResume':'476879e9e4ce62c4d968dca6ca2d25acf83e6a69a4d6c40c7277598737ec5a1c',
          'nativeCertificates':'60048781ac7b61b84bceac4204b30f04384e48ac658dfdd768978e14581538aa'}
DIAGNOSTIC=ROOT/'research/processes/20261023T121111-read_original_zone_bridges.log'
META='originalZoneOrphanBridgeCleanup'
WORK=('fragments','substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots','cell_queue','placements')


def ref(path):return {'path':str(Path(path).resolve()),'sha256':m.sha(path)}
def identity(row):return (row['dim'],row['x'],row['y'],row['z'])
def inside(zone,at):return at[0]==zone['dimension'] and all(zone['minimum'][i]<=at[i+1]<=zone['maximum'][i] for i in range(3))


def heads(db):
    return {key:m.meta(db,key) for key in ('binding','sourceLocalRetentionJournal','sourceVisualReservationJournal','nativeLocalRetention','nativeResumeSourceBinding')}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan',type=Path,default=ROOT/'research/native-grid-plan')
    parser.add_argument('--expected-diagnostic-sha256',required=True)
    parser.add_argument('--out',type=Path,required=True)
    a=parser.parse_args();plan=a.plan.resolve();out=a.out.resolve()
    m.control_check()
    m.require(plan==(ROOT/'research/native-grid-plan').resolve(),'Repair only the current stopped plan, never a source/copy database')
    m.require(out.is_relative_to(ROOT/'research') and not out.exists(),'Use one fresh research receipt; never overwrite evidence')
    m.require(c.source_files(m)==EXPECTED,'The reviewed V11 production source tuple changed')
    m.require(m.sha(DIAGNOSTIC)==a.expected_diagnostic_sha256,'Bound diagnostic bytes changed')
    diagnostic=m.read(DIAGNOSTIC)
    m.require(diagnostic['status']=='READ_ONLY_DIAGNOSTIC' and diagnostic['orphanOnly'] is True,'Not an orphan-only diagnostic')
    wanted={identity(row['bridge']):row for row in diagnostic['points']}
    m.require(len(wanted)==len(diagnostic['points'])==diagnostic['bridgePoints'] and wanted,'Diagnostic bridge set is duplicate/empty/incomplete')
    db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix()+'?mode=rw',uri=True,timeout=30)
    try:
        db.execute('PRAGMA synchronous=FULL');db.execute('PRAGMA cache_size=-2048');db.execute('PRAGMA temp_store=FILE')
        c.attach_source_journal_original(m,db)  # Existing helper attaches old mode=ro.
        db.execute('PRAGMA old.cache_size=-2048')
        db.execute('BEGIN IMMEDIATE')
        n.require_unfinalized(m,db)
        before_cursor=m.meta(db,'nativeComposeCursor');before_heads=heads(db);before_counts=n.counts(db)
        m.require(before_cursor['phase']=='NATIVE_TRANSFER' and before_cursor==diagnostic['cursor']
                  and before_heads['sourceLocalRetentionJournal']==diagnostic['localJournal'],'Stopped cursor/journal differs from the actual diagnostic')
        m.require(m.meta(db,META) is None,'A prior cleanup receipt is already committed; do not repeat removal')
        admitted=m.meta(db,'nativeResumeSourceBinding');m.require(admitted,'Missing admitted old source lineage')
        allowed=[admitted['sourceFiles']]
        for transition in c.source_lineage(m,db,admitted):allowed.extend((transition['fromSourceFiles'],transition['toSourceFiles']))
        def admitted_sources(files):m.require(files in allowed,'Journal source tuple is outside the independently admitted old lineage')
        context=c.SourceVisualReservations(m,db);previous=None;event_count=0;zone_count=0
        current={};witnesses={key:[] for key in wanted};snapshot_zones={};snapshot_owners={}
        for sequence,encoded,digest in db.execute('SELECT sequence,data,digest FROM source_local_retentions ORDER BY sequence'):
            event=m.json.loads(encoded);event_count+=1
            m.require(sequence==event_count and m.digest(event)==digest and event['previous']==previous
                      and event['sourceBinding']==before_heads['binding'],'Accepted source journal chain changed')
            admitted_sources(event['sourceFiles'])
            previous={'schemaVersion':1,'events':sequence,'head':digest,'sourceBinding':event['sourceBinding']}
            zone=event.get('sourceZone')
            if not zone:continue
            zone_count+=1;low,high=zone['minimum'],zone['maximum']
            args=(zone['dimension'],low[0],high[0],low[1],high[1],low[2],high[2])
            hits=n.row_dict(db,'SELECT * FROM bridge_cells WHERE dim=? AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? AND z BETWEEN ? AND ?',args)
            for bridge in hits:
                key=identity(bridge);m.require(key in wanted and bridge==wanted[key]['bridge'],'Fresh zone has an unbound/different bridge row')
                m.require(key not in current or current[key]==bridge,'Shared zone bridge identity changed');current[key]=bridge
            affected={key for key in wanted if inside(zone,key)}
            if not affected:continue
            zone_key=(zone['dimension'],*zone['minimum'])
            if zone_key not in snapshot_zones:
                m.require(context.zone_snapshot(zone['dimension'],zone['minimum'])==zone,'Affected original source section/raw typed BE snapshot changed')
                snapshot_zones[zone_key]=m.digest(zone)
            else:m.require(snapshot_zones[zone_key]==m.digest(zone),'Repeated source zone has different immutable snapshots')
            for point in event['pointPlan']:
                key=(zone['dimension'],*point['position'])
                if key not in affected:continue
                bridge=wanted[key]['bridge']
                m.require(point['before']['bridge_cells']==[bridge] and point['before']['queue']==[]
                          and point['retire'] is False and not any(point['remainingDemands'].values()),
                          'Accepted before-image does not prove an untouched undemanded orphan')
                diag=[row for row in wanted[key]['originalZoneEvents'] if row['sequence']==sequence]
                m.require(len(diag)==1 and diag[0]['beforePoint']==[point]
                          and diag[0]['originalDomains']==len(event['originalDomains']),'Diagnostic and accepted point provenance differ')
                witnesses[key].append({'sequence':sequence,'eventDigest':digest})
            for domain in event['originalDomains']:
                obj=domain['object'];key=(obj['dim'],obj['uuid'])
                if not any((obj['dim'],*point['position']) in affected for point in domain['domain']):continue
                if key in snapshot_owners:
                    m.require(snapshot_owners[key]==m.digest(domain),'Affected owner has inconsistent complete snapshots');continue
                if domain.get('runtimeFootprintEnvelope'):context.__dict__.setdefault('_saved_envelopes',{})[obj['pose']]=domain['runtimeFootprintEnvelope']
                actual=context.original_domain(obj['dim'],obj['uuid'],unreplaced=False)
                m.require(actual==domain,'Affected complete root/helper/BE/payload/ledger snapshot changed')
                snapshot_owners[key]=m.digest(domain)
        m.require(previous==before_heads['sourceLocalRetentionJournal'] and zone_count==diagnostic['zoneEventsInspected'],
                  'Accepted source journal/zone inventory is incomplete')
        m.require(current=={key:row['bridge'] for key,row in wanted.items()} and all(witnesses.values()),
                  'Fresh zone bridge intersection differs from the complete diagnostic set')
        for key,row in wanted.items():
            m.require(not any(row['currentDemands'].values()),'Diagnostic contains actual work')
            for table in WORK:m.require(not db.execute('SELECT 1 FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=? LIMIT 1',key).fetchone(),
                                        'Orphan acquired current replacement/derived work: '+table)
        changes=db.total_changes
        for key,bridge in sorted(current.items()):
            deleted=db.execute('DELETE FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=? AND host_state=? AND retained_resident=? AND type_index IS ?',
                               (*key,bridge['host_state'],bridge['retained_resident'],bridge['type_index'])).rowcount
            m.require(deleted==1,'Delete only one exact diagnostic-bound full bridge row')
        # Mandatory complete historical + all source-journal validation using
        # only independently admitted source tuples, not an unconditional skip.
        n.validate_resume(m,db,plan,source_files_validator=admitted_sources)
        after_cursor=m.meta(db,'nativeComposeCursor');after_heads=heads(db);after_counts=n.counts(db)
        m.require(after_cursor==before_cursor and after_heads==before_heads,'Cleanup changed cursor/binding/journal heads')
        m.require(db.total_changes-changes==len(current),'Only exact derived bridge rows may change before commit evidence')
        for table,count in before_counts.items():
            m.require(after_counts[table]==count-(len(current) if table=='bridge_cells' else 0),'Another planner row count changed: '+table)
        m.require(c.source_files(m)==EXPECTED and m.sha(DIAGNOSTIC)==a.expected_diagnostic_sha256,'Source/diagnostic changed during the atomic repair')
        receipt={'schemaVersion':1,'status':'PASS','kind':'AUDITED_ORIGINAL_ZONE_ORPHAN_BRIDGE_CLEANUP',
                 'sourceBinding':before_heads['binding'],'sourceFiles':EXPECTED,'diagnostic':ref(DIAGNOSTIC),'utility':ref(__file__),
                 'beforeCursor':before_cursor,'afterCursor':after_cursor,'beforeJournalHeads':before_heads,'afterJournalHeads':after_heads,
                 'beforeCounts':before_counts,'afterCounts':after_counts,'deletedBridgeRows':len(current),
                 'deletedRows':[current[key] for key in sorted(current)],'acceptedPointWitnesses':[{'position':list(key),'events':witnesses[key]} for key in sorted(current)],
                 'affectedSourceZonesValidatedBeforeDelete':len(snapshot_zones),'completeAffectedOriginalOwnersValidatedBeforeDelete':len(snapshot_owners),
                 'fullValidationBeforeCommit':True,'allDeletedOrphanOnly':True,'allOriginalSnapshotsValidated':True,
                 'onlyDerivedBridgeRowsDeleted':True,'processedRowsReset':0,'mapWrites':0,'noMapWrites':True,
                 'receiptCommitAuthority':{'metaKey':META,'rule':'This immutable receipt is committed only if the plan meta reference matches its path and SHA256'}}
        m.atomic_json(out,receipt)  # Existing fsync + replace; never overwrite a prior report.
        m.meta(db,META,ref(out));db.commit()
        m.require(m.meta(db,META)==ref(out),'Committed cleanup evidence reference differs')
        print(m.canonical({'status':'PASS','receipt':ref(out),'deletedBridgeRows':len(current),'metaKey':META}))
    except BaseException:
        db.rollback();raise
    finally:db.close()


if __name__=='__main__':main()
