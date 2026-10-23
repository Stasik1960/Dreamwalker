"""Finite JSON/source-contract check only; no plan/cache/world connection."""
import argparse
from pathlib import Path
import migrate as m
import native_certificates as c


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--certificate',type=Path,required=True);ap.add_argument('--certificate-sha256',required=True);ap.add_argument('--out',type=Path,required=True);a=ap.parse_args()
    document=c.bound(m,{'path':str(a.certificate.resolve()),'sha256':a.certificate_sha256});c.preserved_whole_proof_files(m,document)
    natives=[document['native']]+document['additionalPreservedNatives'];owner=document['owners'][0]
    snapshots={(row['dimension'],*row['position']):row for row in document['pointDemands']}
    expected={(row['dimension'],*at) for row in natives for at in row['runtimeMemberCells']}|{(owner['dimension'],*at) for at in owner['runtimeMemberCells']}|{(row['dim'],row['x'],row['y'],row['z']) for row in owner['wholeCells']}
    m.require(len(snapshots)==len(document['pointDemands']) and set(snapshots)==expected,'Whole wire lacks its complete finite union')
    prunes=set()
    for key,row in snapshots.items():
        remaining={name:row[name] for name in ('fragments','substrate_fragments','native_visual_fragments','native_visual_roots')}
        remaining['whole_cells']=[entry for entry in row['whole_cells'] if entry['uuid']!=owner['uuid']]
        m.require(not any(remaining.values()) and row['queue'] in ([],[{'processed':0}]),'Finite wire has unexpected remaining/completed work')
        if row['queue']:prunes.add(key)
    guard=c.bound(m,document['queueNativeGuard']);points={(row['dimension'],*row['position']):row for row in guard['points']}
    m.require(len(points)==len(guard['points']) and set(points)==prunes and all(row['queue']==snapshots[key]['queue'] and row['queuePruningSafe'] is True and not row['foreignActivated'] for key,row in points.items()),'Whole native guard does not bind every exact q0 prune')
    report={'schemaVersion':1,'status':'PASS_FINITE_WIRE_ONLY_NOT_EXECUTION','certificate':{'path':str(a.certificate.resolve()),'sha256':m.sha(a.certificate)},
            'sourceFiles':c.source_files(m),'counts':{'preservedNatives':len(natives),'nativePhysicalMembers':sum(len(row['runtimeMemberCells']) for row in natives),'wholeOwners':1,
                'wholeRoles':len(owner['wholeCells']),'wholeRuntimeMembers':len(owner['runtimeMemberCells']),'unionPoints':len(snapshots),'pendingPrunes':len(prunes),'completedPrunes':0},
            'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0}
    m.atomic_json(a.out,report);print(report['status'],report['counts'])


if __name__=='__main__':main()
