"""File-only wire check for exactly one retained-P visual reservation."""
import argparse
from pathlib import Path
import migrate as m
import native_certificates as c


def main():
    p=argparse.ArgumentParser();p.add_argument('--certificate',type=Path,required=True);p.add_argument('--certificate-sha256',required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    reference={'path':str(a.certificate.resolve()),'sha256':a.certificate_sha256};document=c.bound(m,reference);c.retained_functional_visual_proof(m,document)
    report={'schemaVersion':1,'status':'PASS_RETAINED_FUNCTIONAL_VISUAL_WIRE_ONLY','certificate':reference,'sourceFiles':c.source_files(m),'rowsToDelete':1,
            'retainedPPhysicalMembers':len(document['retainedFunctional']['runtimeMemberCells']),'ordinaryCVisualCells':len(document['native']['visualCells']),
            'sourceOnly':True,'gameplayPass':False,'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0}
    m.atomic_json(a.out,report);print(report['status'])


if __name__=='__main__':main()
