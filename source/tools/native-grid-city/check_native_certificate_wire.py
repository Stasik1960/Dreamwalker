"""Finite certificate wire/proof comparison only; opens no database or world."""
import argparse
from collections import Counter
from pathlib import Path
import migrate as m
import native_certificates as c


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--certificate',type=Path,required=True)
    parser.add_argument('--certificate-sha256',required=True)
    parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args();document=c.bound(m,{'path':str(args.certificate),'sha256':args.certificate_sha256})
    c.proof_files(m,document)
    classification_path=m.PROJECT/'tools/native-grid/classification.json';classification=m.read(classification_path)
    owners={};modes=Counter()
    for case in document['cases']:
        for owner in case['owners']:
            policies=c.member_policies(m,owner,classification)
            if owner['uuid'] in owners:m.require(owners[owner['uuid']]==owner,'Shared explicit owner has inconsistent wire/proof')
            else:
                owners[owner['uuid']]=owner;modes.update(row['policy'] for row in policies)
    m.atomic_json(args.out,{'schemaVersion':1,'status':'PASS_WIRE_AND_BOUND_PROOFS_ONLY',
                          'certificate':{'path':str(args.certificate),'sha256':args.certificate_sha256},
                          'sourceFiles':c.source_files(m),'classificationSha256':m.sha(classification_path),
                          'nativeCases':len(document['cases']),'uniqueOwners':len(owners),'memberPolicies':dict(modes),
                          'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'applyAuthorization':False})
    print('PASS_WIRE_AND_BOUND_PROOFS_ONLY',len(document['cases']),len(owners),dict(modes))


if __name__=='__main__':main()
