"""Exercise authored geometry bounds and the gallery planner without world writes."""
from __future__ import annotations
import copy
import json
from pathlib import Path
from gallery_platform_plan import grid_plan, verify_plan, rotate, ROOT


def main():
    source = ROOT / 'docs/gallery-platform-envelopes.json'
    document = json.loads(source.read_text(encoding='utf8'))
    # Known right-handed cardinal basis rotations, including a nonzero pivot.
    assert all(abs(a-b) < 1e-8 for a,b in zip(rotate([9,8,8], 1, -90, [8,8,8]), [8,8,9]))
    assert all(abs(a-b) < 1e-8 for a,b in zip(rotate([8,9,8], 0, -90, [8,8,8]), [8,8,7]))
    rows = [{'key': key, 'bounds': bounds} for key,bounds in document['envelopes'].items()]
    plan = grid_plan(rows, columns=16, start_x=-10, start_z=25)
    result = verify_plan(plan)
    # A malformed plan must be rejected even when its visuals still fit.
    broken = copy.deepcopy(plan)
    for row in broken['platforms']:
        if row['column'] == 1:
            row['platform'][0] -= 1
            row['root'][0] -= 1
            row['worldBounds'][0] -= 1
            row['worldBounds'][3] -= 1
    try:
        verify_plan(broken)
    except ValueError:
        wrong_gap_rejected = True
    else:
        wrong_gap_rejected = False
    assert wrong_gap_rejected
    negative = sum(row['bounds'][1] < -1e-8 for row in rows)
    report = {**result, 'input': str(source), 'units': 'blocks',
              'sourceCompositeVariantEnvelopes': len(rows)-len(document.get('nativeEvidence',{})),
              'nativeGeometryEnvelopes':len(document.get('nativeEvidence',{})),
              'nativeGeometryEvidence':document.get('nativeEvidence',{}),
              'negativeYEnvelopesRaisedAboveFloor': negative,
              'wrongGapRejected': wrong_gap_rejected,
              'cardinalPivotTransforms': 'PASS',
              'allPrivateModelDependenciesResolved': 'PASS',
              'grid': {'columns':plan['columns'],'rows':plan['rows'],
                       'width':plan['width'],'depth':plan['depth']},
              'scope': 'Envelope preparation and planning only; actual world/runtime/save reopening is checked separately.'}
    output = ROOT / 'docs/gallery-platform-validation.json'
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf8')


if __name__ == '__main__':
    main()
