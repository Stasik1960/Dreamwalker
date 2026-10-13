"""Read-only proof that every planned V12.2 station has a separate corner label.

The first fresh author snapshot provides the full deterministic RP/item layout.
Reauthoring with identical input preserves these station positions; the new QA
author also requires the selected corner to be an actual air cell at placement.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path


def main():
    p=argparse.ArgumentParser();p.add_argument('--manifest',type=Path,required=True);p.add_argument('--report',type=Path,required=True);a=p.parse_args()
    raw=a.manifest.read_bytes();plan=json.loads(raw)
    assert plan['schema']=='dreamwalker-flat-gallery-v122'
    rows=plan['exhibits'];proof=[];conflicts=[]
    for row in rows:
        x,z,w,d=row['platform'];root=row['root'];b=row['bounds']
        corners=[[x,64,z],[x+w-1,64,z],[x,64,z+d-1],[x+w-1,64,z+d-1]]
        choices=[c for c in corners if c!=root and (c[0]+1<=b[0]+1e-6 or c[0]>=b[3]-1e-6 or c[2]+1<=b[2]+1e-6 or c[2]>=b[5]-1e-6)]
        assert choices,'No visually separate non-root corner: '+row['key']
        if 'labelPos' in row:
            assert row['labelPos'] in choices,'Actual selected label corner overlaps the rendered object: '+row['key']
        if corners[0]==root:conflicts.append(row['key'])
        proof.append({'key':row['key'],'root':root,'plannedLabelPos':row.get('labelPos',choices[0]),'availableSeparateCorners':len(choices)})
    result={'schema':'dreamwalker-flat-gallery-v122-preauthor-label-proof','status':'PASS',
            'productionJarSha256':plan['productionJarSha256'],'deterministicPriorManifestSha256':hashlib.sha256(raw).hexdigest(),
            'stations':len(rows),'rootLabelDisjoint':len(proof),'cornerCellOutsideRenderedFootprint':len(proof),
            'formerFrontCornerConflicts':conflicts,'proofTiming':'COMPLETED_AUTHOR_MANIFEST_READ_ONLY',
            'scope':'Independent full-station geometry and selected labelPos proof on the completed manifest; author separately checked actual air, saved verifier checked every typed resident and actual labelPos',
            'plannedLabels':proof}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')


if __name__=='__main__':main()
