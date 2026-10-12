"""Read-only registered-NEW collision audit; preserve exact protected baseline."""
import hashlib
import json
from pathlib import Path
import numpy as np
from gallery_platform_plan import ModelGeometry
from v125_collision_geometry import envelope, geometry_key, solid_parts, solid_volume

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/architecture/resources'
OUT = ROOT / 'docs/v12.5-collisions'


def read(path):
    return json.loads(path.read_text(encoding='utf8'))


def main():
    catalogue = read(RES / 'bloodborne_dw/remaining-catalogue.json')
    paths = [row['path'] for row in catalogue['objects']]
    assert len(paths) == len(set(paths)) == 1045 and 'source_ladder_backing' not in paths
    frozen = read(ROOT / 'docs/v12.4-dedup/baseline-content.json')
    assert {value['id'].split(':', 1)[1] for value in frozen['descriptors'].values()} == set(paths)
    models = ModelGeometry()
    result, geometry_cache = [], {}
    for path in paths:
        source = RES / 'bloodborne_dw/composite' / (path + '.json')
        spec = read(source)
        assert spec['simplePhysicalCollision'] and spec['id'] == 'bloodborne_dw:' + path
        poses = []
        for variant, row in enumerate(spec['variants']):
            for pose_name, pose in row['poses'].items():
                for profile in ('base', 'alt'):
                    solids = solid_parts(pose, models, profile)
                    if not pose['collision'] or not solids:
                        poses.append({'variant': variant, 'pose': pose_name, 'profile': profile,
                                      'passable': not pose['collision'], 'solidElements': len(solids)})
                        continue
                    low, high = envelope(solids)
                    volume = float(np.prod(high - low))
                    occupied_upper_bound = min(1., sum(solid_volume(points) for points in solids) / volume)
                    key = geometry_key(solids)
                    geometry_id = geometry_cache.setdefault(key, len(geometry_cache))
                    poses.append({'variant': variant, 'pose': pose_name, 'profile': profile, 'passable': False,
                                  'geometryId': geometry_id, 'solidElements': len(solids),
                                  'envelope': {'from': low.tolist(), 'to': high.tolist()},
                                  'summedSolidVolumeToEnvelopeUpperBound': round(occupied_upper_bound, 6),
                                  'existingDefaultBoxes': len(pose['collision'])})
        result.append({'registryId': spec['id'], 'name': spec['displayName'],
                       'descriptorSha256': hashlib.sha256(source.read_bytes()).hexdigest(), 'poses': poses})
    OUT.mkdir(parents=True, exist_ok=True)
    report = {'schemaVersion': 1, 'status': 'READ_ONLY_GEOMETRY_AUDIT',
              'baselineCommit': '653ae307ba4c44392ee08969f3890321e95659bb', 'registeredNewTypes': len(paths),
              'baselineWorkingNewTypes': 572, 'workingNewTypes': 551, 'uniqueSolidGeometries': len(geometry_cache),
              'selectorPoseProfiles': sum(len(row['poses']) for row in result),
              'originalV12Changed': False, 'authorCollisionExport': 'NOT_SUPPLIED_NOT_APPLIED',
              'objects': result}
    (OUT / 'geometry-audit.json').write_bytes((json.dumps(report, ensure_ascii=False, indent=2) + '\n').encode('utf8'))
    print(json.dumps({key: value for key, value in report.items() if key != 'objects'}))
    suspects = []
    for row in result:
        for pose in row['poses']:
            if pose.get('summedSolidVolumeToEnvelopeUpperBound', 1) < .45:
                bounds = pose['envelope']
                if max(bounds['to'][i] - bounds['from'][i] for i in range(3)) > 24:
                    suspects.append({'registryId': row['registryId'], 'name': row['name'], **pose})
    (OUT / 'concave-candidates.json').write_bytes((json.dumps(suspects, ensure_ascii=False, indent=2) + '\n').encode('utf8'))
    print(json.dumps({'concavePoseProfileCandidates': len(suspects)}))


if __name__ == '__main__':
    main()
