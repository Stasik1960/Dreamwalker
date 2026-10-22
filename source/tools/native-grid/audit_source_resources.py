"""Verify every original assets/data entry against the immutable blocks.2 JAR.

This checks resource preservation, not gameplay or map readiness.
"""
import argparse, hashlib, json, zipfile
from pathlib import Path
from run_profiles import ROOT, sha, write

BASELINE=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW-city-mesh-20261018\deliverables\block-fixes-2-20261020\game\dreamwalker-bb-materials.1-streaming.7-blocks.2.jar')
EXPECTED='40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51'

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--production',type=Path,required=True)
    parser.add_argument('--out',type=Path,default=ROOT/'research/source-resource-audit.json')
    args=parser.parse_args()
    assert sha(BASELINE)==EXPECTED,'Immutable source JAR binding changed'
    production=args.production.resolve();out=args.out.resolve()
    assert production.is_relative_to(ROOT) and out.is_relative_to(ROOT)
    failures=[];entries=[]
    with zipfile.ZipFile(BASELINE) as source,zipfile.ZipFile(production) as target:
        names=set(target.namelist())
        for name in sorted(source.namelist()):
            if name.endswith('/') or not name.startswith(('assets/','data/')):continue
            before=source.read(name);digest=hashlib.sha256(before).hexdigest()
            if name not in names:failures.append({'entry':name,'reason':'missing'})
            elif hashlib.sha256(target.read(name)).hexdigest()!=digest:failures.append({'entry':name,'reason':'changed'})
            entries.append({'entry':name,'bytes':len(before),'sha256':digest})
    manifest=json.dumps(entries,sort_keys=True,separators=(',',':')).encode()
    result={'status':'PASS' if not failures else 'FAIL','scope':'All original assets and data resources; no gameplay or readiness claim','sourceJarSha256':EXPECTED,'productionJarSha256':sha(production),'entriesChecked':len(entries),'bytesChecked':sum(e['bytes'] for e in entries),'resourceManifestSha256':hashlib.sha256(manifest).hexdigest(),'failures':failures,'mapReads':0,'mapWrites':0}
    write(out,result);print(json.dumps(result));assert not failures,failures

if __name__=='__main__':main()
