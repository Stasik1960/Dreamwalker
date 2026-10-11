"""Read-only final-JAR collision integrity checks; never rewrites mod resources.

The geometry oracle below walks the model graph inside the final JAR and
implements renderer transforms independently of the collision generator.
"""
from __future__ import annotations
import argparse
import hashlib
import itertools
import json
import math
from pathlib import Path
import traceback
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / 'docs/collision-simplification/final-policy-integrity.json'
EXPECTED_JAR = 'ab2c7806fe0bfce94cdd081924fe9a643b087caa9b8b1ca7b17fcb35f3874c32'
JAR = ROOT / 'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.2-simple-collisions.jar'
EPSILON = 1e-6  # model units: output JSON vertices are rounded to eight decimals.


def sha(data):
    return hashlib.sha256(data).hexdigest()


def turn(point, axis, degrees, pivot):
    """Independent right-hand axis rotation, matching MatrixStack ordering."""
    r = math.radians(degrees)
    c, s = math.cos(r), math.sin(r)
    if degrees % 90 == 0:
        c, s = round(c), round(s)
    x, y, z = [point[i] - pivot[i] for i in range(3)]
    if axis == 'x':
        result = [x, c*y - s*z, s*y + c*z]
    elif axis == 'y':
        result = [c*x + s*z, y, -s*x + c*z]
    else:
        result = [c*x - s*y, s*x + c*y, z]
    return [result[i] + pivot[i] for i in range(3)]


class JarModels:
    def __init__(self, jar):
        self.jar = jar
        self.documents = {}
        self.element_cache = {}
        self.point_cache = {}

    def elements(self, identifier, seen=()):
        if identifier in self.element_cache:
            return self.element_cache[identifier]
        if identifier in seen:
            raise ValueError('Model cycle: ' + identifier)
        namespace, path = identifier.split(':', 1) if ':' in identifier else ('minecraft', identifier)
        entry = f'assets/{namespace}/models/{path}.json'
        data = self.jar.read(entry)
        self.documents[entry] = sha(data)
        document = json.loads(data)
        if 'elements' in document:
            result = document['elements']
        else:
            parent = document.get('parent', '')
            result = [] if not parent or parent.startswith(('builtin/', 'minecraft:builtin/')) else self.elements(parent, seen+(identifier,))
        self.element_cache[identifier] = result
        return result

    def points(self, identifier):
        if identifier in self.point_cache:
            return self.point_cache[identifier]
        points = []
        for element in self.elements(identifier):
            if not element.get('faces'):
                continue
            for corner in itertools.product(*zip(element['from'], element['to'])):
                point = list(corner)
                rotation = element.get('rotation')
                if rotation:
                    axis, pivot = rotation['axis'], rotation['origin']
                    if rotation.get('rescale'):
                        scale = 1 / math.cos(math.radians(rotation['angle']))
                        index = 'xyz'.index(axis)
                        point = [pivot[i] + (point[i]-pivot[i])*(1 if i == index else scale) for i in range(3)]
                    point = turn(point, axis, rotation['angle'], pivot)
                points.append(point)
        self.point_cache[identifier] = points
        return points


def rendered_points(pose, profile, models):
    points = []
    for part in pose['parts']:
        identifier = part.get('altModel', part['model']) if profile == 'ALT' else part['model']
        pivot = part.get('pivot', [8, 8, 8])
        for value in models.points(identifier):
            point = turn(value, 'y', part.get('extraYaw', 0), part.get('extraPivot', [8, 8, 8]))
            point = turn(point, 'x', -part.get('pitch', 0), pivot)
            point = turn(point, 'y', -part.get('yaw', 0), pivot)
            points.append([point[i]+part.get('offset', [0, 0, 0])[i] for i in range(3)])
    return points


def main():
    global EXPECTED_JAR
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--expected-jar-sha256', default=EXPECTED_JAR)
    args = parser.parse_args()
    assert len(args.expected_jar_sha256) == 64 and all(c in '0123456789abcdef' for c in args.expected_jar_sha256)
    EXPECTED_JAR = args.expected_jar_sha256
    failures = []
    def check(condition, message, **case):
        if not condition:
            failures.append(dict(message=message, **case))
    result = {
        'schema': 'dreamwalker-v122-final-policy-integrity-v1',
        'status': 'FAIL',
        'scope': 'Read-only final JAR data audit; does not substitute for Minecraft runtime checks.',
        'jar': str(JAR.relative_to(ROOT)),
        'jarSha256': sha(JAR.read_bytes()),
        'expectedJarSha256': EXPECTED_JAR,
        'geometryOracle': 'Independent JAR model graph, actual element corner/rescale and renderer transforms; BASE and ALT profiles, all eight yaw values.',
        'geometryRoundingToleranceModelUnits': EPSILON,
    }
    check(result['jarSha256'] == EXPECTED_JAR, 'Final JAR SHA mismatch')
    catalogue = json.loads((ROOT/'src/architecture/resources/bloodborne_dw/remaining-catalogue.json').read_text(encoding='utf-8'))
    baseline = json.loads((ROOT/'docs/remaining-transfer/baseline.json').read_text(encoding='utf-8'))
    source_paths = {row['path'] for row in catalogue['objects']}
    check(len(source_paths) == 1045, 'Expected 1045 surviving imported objects', actual=len(source_paths))
    objects = variants = poses = orientations = boxes = profile_envelopes = compared = legacy = 0
    max_boxes = 0
    descriptor_shas = {}
    with zipfile.ZipFile(JAR) as jar:
        models = JarModels(jar)
        working = ROOT/'src/architecture/resources/bloodborne_dw/composite'
        jar_entries = {name for name in jar.namelist() if name.startswith('bloodborne_dw/composite/') and name.endswith('.json')}
        local_entries = {'bloodborne_dw/composite/'+path.name for path in working.glob('*.json')}
        check(jar_entries == local_entries, 'JAR/working descriptor inventory mismatch', jarOnly=sorted(jar_entries-local_entries), workingOnly=sorted(local_entries-jar_entries))
        for path in sorted(working.glob('*.json')):
            data = path.read_bytes()
            entry = 'bloodborne_dw/composite/'+path.name
            check(jar.read(entry) == data, 'Descriptor bytes differ between final JAR and working tree', descriptor=entry)
            descriptor_shas[entry] = sha(data)
            compared += 1
            document = json.loads(data)
            relative = path.relative_to(ROOT).as_posix()
            is_legacy = relative in baseline['implementation']
            if is_legacy:
                legacy += 1
                check(not document.get('simplePhysicalCollision', False), 'V12 object opted into new policy', id=document['id'])
                check(sha(data) == baseline['implementation'][relative], 'V12 descriptor bytes changed from baseline', id=document['id'])
            if path.stem not in source_paths:
                check(not document.get('simplePhysicalCollision', False), 'Object outside imported catalogue opted in', id=document['id'])
                continue
            objects += 1
            check(not is_legacy, 'Imported catalogue overlaps V12 descriptor', id=document['id'])
            check(document.get('simplePhysicalCollision') is True, 'Imported object missing policy marker', id=document['id'])
            for variant_index, variant in enumerate(document['variants']):
                variants += 1
                for pose_name, pose in variant['poses'].items():
                    poses += 1
                    case = dict(id=document['id'], variant=variant_index, pose=pose_name)
                    rotations = pose.get('simpleCollisionByRotation', [])
                    check(len(rotations) == 8, 'Expected eight oriented collision arrays', **case)
                    check(len(pose.get('collision', [])) <= 3, 'Canonical collision box budget exceeded', **case)
                    profiles = {profile: rendered_points(pose, profile, models) for profile in ('BASE', 'ALT')}
                    for yaw, physical in enumerate(rotations):
                        orientations += 1
                        boxes += len(physical)
                        max_boxes = max(max_boxes, len(physical))
                        check(len(physical) <= 3, 'Oriented collision box budget exceeded', yaw=yaw, **case)
                        for b in physical:
                            check(all(math.isfinite(v) for v in b['from']+b['to']) and all(b['from'][i] < b['to'][i] for i in range(3)), 'Invalid physical box', yaw=yaw, box=b, **case)
                        for profile, raw in profiles.items():
                            profile_envelopes += 1
                            transformed = [turn(p, 'y', -yaw*45, [8,8,8]) for p in raw]
                            check(bool(transformed) or not physical, 'Physical box without rendered model', yaw=yaw, profile=profile, **case)
                            if not transformed:
                                continue
                            low = [min(p[i] for p in transformed) for i in range(3)]
                            high = [max(p[i] for p in transformed) for i in range(3)]
                            for b in physical:
                                check(all(b['from'][i] >= low[i]-EPSILON and b['to'][i] <= high[i]+EPSILON for i in range(3)), 'Physical box outside actual rendered vertex envelope', yaw=yaw, profile=profile, box=b, visualFrom=low, visualTo=high, **case)
        result['jarModelDocumentsRead'] = len(models.documents)
        result['modelGraphDigestSha256'] = sha(json.dumps(models.documents, sort_keys=True, separators=(',', ':')).encode())
    check(objects == 1045 and variants == 1802 and poses == 1803 and orientations == 14424, 'Unexpected imported coverage totals', objects=objects, variants=variants, poses=poses, orientations=orientations)
    boundary_file = ROOT/'docs/v12.2-simplified-collisions/boundary-math.json'
    boundary = json.loads(boundary_file.read_text(encoding='utf-8'))
    check(boundary['status'] == 'PASS' and '303 checks' in boundary['runOutput'], 'Latest pure Java boundary verification did not pass')
    for relative, digest in boundary['sources'].items():
        check(sha((ROOT/relative).read_bytes()) == digest, 'Boundary report source hash no longer matches', source=relative)
    math_source = (ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/ImportedCollisionMath.java').read_text(encoding='utf-8')
    check('low[axis] < 0 && low[axis] > -.5' in math_source and 'high[axis] > 1 && high[axis] < 1.5' in math_source, 'Production strict six-face comparisons changed')
    check('x0 + shiftX, box.y0 + mountY + shiftY, z0 + shiftZ' in math_source, 'Production crop no longer follows full instance translation')
    result.update(status='PASS' if not failures else 'FAIL',
        descriptorsComparedByteForByte=compared, v12DescriptorsUnchangedAndNotOptedIn=legacy,
        importedObjects=objects, variants=variants, poses=poses, orientations=orientations,
        physicalBoxesAcrossEightAlternatives=boxes, maximumPhysicalBoxesPerOrientation=max_boxes,
        renderedProfileEnvelopesChecked=profile_envelopes,
        descriptorInventoryDigestSha256=sha(json.dumps(descriptor_shas, sort_keys=True, separators=(',', ':')).encode()),
        strictBoundaryEvidence={'status':boundary['status'], 'report':str(boundary_file.relative_to(ROOT)), 'reportSha256':sha(boundary_file.read_bytes()), 'sources':boundary['sources'], 'checks':303, 'threshold': 'STRICT overflow <0.5 independently for six faces; equality and just-above remain; mount and SourceShift before crop'},
        failuresCount=len(failures), failures=failures[:30])
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    if failures:
        raise SystemExit(1)


if __name__ == '__main__':
    try:
        main()
    except Exception:
        REPORT.parent.mkdir(parents=True, exist_ok=True)
        REPORT.write_text(json.dumps({'status':'FAIL', 'expectedJarSha256':EXPECTED_JAR, 'traceback':traceback.format_exc()}, indent=2)+'\n', encoding='utf-8')
        raise
