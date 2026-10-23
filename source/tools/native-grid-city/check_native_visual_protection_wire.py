"""Check finite correction source wire only; no plan/cache/world database."""
import argparse
from pathlib import Path
import migrate as m
import native_certificates as c


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--certificate',type=Path,required=True);ap.add_argument('--certificate-sha256',required=True);ap.add_argument('--out',type=Path,required=True);a=ap.parse_args()
    reference={'path':str(a.certificate.resolve()),'sha256':a.certificate_sha256};document=c.bound(m,reference);c.visual_correction_proof(m,document)
    report={'schemaVersion':1,'status':'PASS_SOURCE_CORRECTION_WIRE_ONLY','certificate':reference,'sourceFiles':c.source_files(m),
            'protectedRowsToDelete':1,'nativePhysicalMembers':len(document['retainedWhole']['runtimeMemberCells']),
            'selectedQueueNativeRowsChanged':0,'sourceOnly':True,'itemPickDropGapUnresolved':True,'gameplayPass':False,
            'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0}
    m.atomic_json(a.out,report);print(report['status'])


if __name__=='__main__':main()
