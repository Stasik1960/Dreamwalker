"""Bound native-retraction wire/source facts only, no active DB or world."""
import argparse
from pathlib import Path
import migrate as m
import native_certificates as c


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--certificate',type=Path,required=True);parser.add_argument('--certificate-sha256',required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    reference={'path':str(args.certificate),'sha256':args.certificate_sha256};document=c.bound(m,reference);c.retraction_proof_files(m,document)
    classification=m.read(m.PROJECT/'tools/native-grid/classification.json')
    policies=[policy for owner in document['owners'] for policy in c.member_policies(m,owner,classification)]
    guard=c.bound(m,document['retirementNativeGuard'])
    m.atomic_json(args.out,{'schemaVersion':1,'status':'PASS_WIRE_AND_BOUND_PROOFS_ONLY','certificate':reference,'sourceFiles':c.source_files(m),
                          'pendingNatives':1,'retractedNatives':len(document['retractedNatives']),'owners':len(document['owners']),
                          'activatedRowsRetracted':len(document['retractedNatives']),'substrateRowsRetracted':sum(len(row['substrateFragments']) for row in document['retractedNatives']),
                          'memberPolicies':policies,'completedNativeGuardPoints':len(guard['points']),
                          'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'applyAuthorization':False})
    print('PASS_WIRE_AND_BOUND_PROOFS_ONLY',len(document['retractedNatives']),len(document['owners']),len(guard['points']))


if __name__=='__main__':main()
