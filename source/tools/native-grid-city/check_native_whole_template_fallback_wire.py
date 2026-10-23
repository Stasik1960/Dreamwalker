"""File-only W-only certificate/source wire check; no plan database."""
import argparse
from pathlib import Path
import migrate as m
import native_certificates as c


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--certificate',type=Path,required=True);ap.add_argument('--certificate-sha256',required=True);ap.add_argument('--out',type=Path,required=True);a=ap.parse_args()
    reference={'path':str(a.certificate.resolve()),'sha256':a.certificate_sha256};document=c.bound(m,reference);c.whole_template_fallback_proof(m,document)
    report={'schemaVersion':1,'status':'PASS_NATIVE_WHOLE_TEMPLATE_FALLBACK_WIRE_ONLY','certificate':reference,'sourceFiles':c.source_files(m),
            'ordinaryNativeRetained':False,'wholeSelectionsToRemove':1,'wholeRolesToRemove':len(document['whole']['wholeCells']),
            'originalWholeMembers':len(document['whole']['runtimeMemberCells']),'nativeSeedAndRootKept':document['keepNativeQueue'],
            'sourceOnly':True,'itemPickDropGapUnresolved':True,'gameplayPass':False,'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0}
    m.atomic_json(a.out,report);print(report['status'])


if __name__=='__main__':main()
