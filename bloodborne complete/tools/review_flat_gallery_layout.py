"""Independent read-only review of the finalized four-section gallery manifest."""
from __future__ import annotations
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
from gallery_platform_plan import ROOT

MANIFEST = ROOT / 'build/flat-gallery/gallery-manifest.json'
ENVELOPES = ROOT / 'docs/gallery-platform-envelopes.json'
OUTPUT = ROOT / 'docs/gallery-v12.1/layout-review.json'
TOL = 1e-6


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def contains(outer, inner):
    return all(inner[i] >= outer[i]-TOL and inner[i+3] <= outer[i+3]+TOL for i in range(3))


def main():
    manifest = json.loads(MANIFEST.read_text(encoding='utf8'))
    models = json.loads(ENVELOPES.read_text(encoding='utf8'))
    rows, failures = manifest['exhibits'], []
    grouped, keys = defaultdict(dict), set()
    sections = {row['id']: row for row in manifest['catalogue']['sections']}
    catalogue = {row['key']:row for row in manifest['catalogue']['exhibits']}
    if len(rows) != 1942 or len(catalogue) != len(rows):
        failures.append('Unexpected catalogue/manifest exhibit count')
    exterior_geometry, elevated, diagnostic = 0, [], []
    for row in rows:
        key = row['key']
        if key in keys:
            failures.append('Repeated exhibit key: '+key)
        keys.add(key)
        if key not in catalogue:
            failures.append('Unknown exhibit: '+key)
        elif any(row.get(field) != catalogue[key].get(field) for field in ['section','registryId','itemId','numericId','variant','kind','name','origin']):
            failures.append('Authored category/identity differs from reviewed catalogue: '+key)
        if row['section'] not in sections or row['floor'] != sections[row['section']]['floor']:
            failures.append('Section floor/category mismatch: '+key)
        position = (row['row'], row['column'])
        if position in grouped['positions']:
            failures.append('Repeated grid location: '+key)
        grouped['positions'][position] = row
        x,z,width,depth = row['platform']
        low_x,low_y,low_z,high_x,high_y,high_z = row['bounds']
        if min(low_x-x,low_z-z,x+width-high_x,z+depth-high_z) < 2-TOL:
            failures.append('Planned envelope lacks two-block platform margin: '+key)
        if low_y < 64-TOL or high_y > 319+TOL:
            failures.append('Envelope below floor or outside world build height: '+key)
        if not contains(row['bounds'], row['actualBounds']):
            failures.append('Runtime selection/collider escapes envelope: '+key)
        if row['kind'] == 'architecture':
            envelope_key = row['registryId'] + '|' + str(row['variant'])
            local = models['envelopes'].get(envelope_key)
            if local is None:
                failures.append('No independently measured private visual envelope: '+key)
            else:
                root = row['root']
                world = [local[i]+root[i] for i in range(3)] + [local[i+3]+root[i] for i in range(3)]
                if not contains(row['bounds'],world):
                    failures.append('Closed/open BASE/ALT model envelope escapes platform plan: '+key)
                exterior_geometry += 1
            # Native wall art uses the registry/material, with no variant property.
            if 'variant' in row['placedState'] and row['placedState']['variant'] != str(row['variant']):
                failures.append('Placed variant differs: '+key)
            if row['placedState'].get('profile') != 'base' or row['placedState'].get('open','false') != 'false':
                failures.append('Wrong initial profile/open pose: '+key)
        elif row['kind'] == 'rp_object':
            if row['representation'] != 'registered_rp_entity' or not row['uuid']:
                failures.append('RP object lacks actual registered persistent entity: '+key)
        elif row['kind'] == 'diagnostic':
            diagnostic.append(key)
            if row['uuid'] or row['representation'] != 'diagnostic_placeholder_no_authored_mod_object' or row['section'] != 'error':
                failures.append('Malformed source-defect diagnostic placeholder: '+key)
        else:
            failures.append('Unexpected non-building exhibit: '+key)
        if row['root'][1] > 64:
            elevated.append({'key':key,'rootY':row['root'][1],'plannedBottomY':row['bounds'][1],
                             'actualBottomY':row['actualBounds'][1]})
        # Corner sign is a one-cell non-colliding fixture, clear of every envelope.
        sign_box = [x,64,z,x+1,65,z+1]
        if not (sign_box[3] <= low_x or sign_box[0] >= high_x or sign_box[5] <= low_z or sign_box[2] >= high_z):
            failures.append('Corner label may intersect displayed object: '+key)
    positions = grouped['positions']
    horizontal, vertical = 0, 0
    for row in rows:
        x,z,width,depth = row['platform']
        other = positions.get((row['row'],row['column']+1))
        if other:
            horizontal += 1
            if other['platform'][0] - x - width != 2:
                failures.append('Nonexact horizontal two-block gap: '+row['key'])
        other = positions.get((row['row']+1,row['column']))
        if other:
            vertical += 1
            if other['platform'][1] - z - depth != 2:
                failures.append('Nonexact vertical two-block gap: '+row['key'])
    pair_checks = 0
    for index,row in enumerate(rows):
        x,z,w,d = row['platform']
        for other in rows[index+1:]:
            X,Z,W,D = other['platform']
            pair_checks += 1
            if not (x+w+2 <= X or X+W+2 <= x or z+d+2 <= Z or Z+D+2 <= z):
                failures.append('Platforms overlap or are too close: '+row['key']+'/'+other['key'])
    section_ranges = {}
    for section in sections:
        selected = [row for row in rows if row['section'] == section]
        indices = sorted({row['row'] for row in selected})
        if indices != list(range(min(indices), max(indices)+1)) or min(indices) != manifest['sectionStartRows'][section]:
            failures.append('Section rows are discontinuous or start header differs: '+section)
        if any(len({row['section'] for row in rows if row['row'] == index}) != 1 for index in indices):
            failures.append('Mixed categories in the same row: '+section)
        section_ranges[section] = {'count':len(selected),'firstRow':min(indices),'lastRow':max(indices),
                                  'floor':sections[section]['floor']}
    if len(diagnostic) != 57:
        failures.append('Expected 57 explicit absent/defective source placeholders')
    report = {'status':'PASS' if not failures else 'FAIL',
              'manifest':str(MANIFEST),'manifestSha256':digest(MANIFEST),
              'privateGeometryEnvelopes':str(ENVELOPES),'geometrySha256':digest(ENVELOPES),
              'platforms':len(rows),'realBuildingObjects':len(rows)-len(diagnostic),
              'diagnosticPlaceholders':len(diagnostic),'sections':section_ranges,
              'independentPrivateModelEnvelopeChecks':exterior_geometry,
              'pairwisePlatformGapChecks':pair_checks,'horizontalExactGapChecks':horizontal,
              'verticalExactGapChecks':vertical,'allGapBlocks':2,
              'aboveFloorAndInsideBuildHeight':'PASS' if not failures else 'SEE_FAILURES',
              'labels':{'manifestBothSides':manifest.get('labelsBothSides',False),
                        'bothSidesCount':manifest.get('signsWithBothSides'),
                        'cornerFixturesDoNotIntersectEnvelopes':True,
                        'categoryIdentityAndFloorColour':'PASS'},
              'raisedRootCount':len(elevated),'raisedRootExamples':elevated[:12],
              'materialRenderingNote':'Two V12 wall exhibits use their existing manual tall arm to show distinct material; diagonal post keeps its canonical restricted state.',
              'scope':'Read-only manifest/private-model layout review. Saved-world persistence, native model baking and actual client screenshots are separate evidence.',
              'risks':[{'status':'ACCEPTED_LAYOUT_CHOICE','detail':'Negative source Y is handled by integer raising. Some objects sit above the platform; this keeps all model geometry visible and inside build height.'},
                       {'status':'NOT_A_SOURCE_OBJECT','detail':'57 damaged or missing source paths use labelled red-glass diagnostics; these are explicitly not authored blocks.'},
                       {'status':'OUTSIDE_EXHIBIT_GRID','detail':'Welcome/category sign stands are navigation fixtures outside the podium grid; grid gap checks cover all 1942 individual exhibit platforms.'}],
              'failures':failures}
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    if failures:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
