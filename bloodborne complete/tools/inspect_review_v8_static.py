"""Source-level review counters; explicitly separate them from runtime/performance."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CHECKPOINT = ROOT / 'build/delivery/v7/dreamwalker-bb-fabric-1.20.1-v7-full-source.zip'
PREFIX = 'dreamwalker-bb-fabric-1.20.1-source-v7/'
def sha(data): return hashlib.sha256(data).hexdigest()
def counts(spec):
    return [{'variant': index, 'poses': {name: {'render_parts': len(pose['parts']),
        'physical_descriptor_volumes': len(pose['collision']),
        'selection_descriptor_volumes': len(pose.get('selection', pose['collision']))}
        for name, pose in variant['poses'].items()}} for index, variant in enumerate(spec['variants'])]
def main():
    delivered = json.loads((CHECKPOINT.parent / 'dreamwalker-bb-fabric-1.20.1-v7-delivery-manifest.json').read_text(encoding='utf8'))
    assert sha(CHECKPOINT.read_bytes()) == delivered['source_archive']['archive_sha256']
    rows = []
    with zipfile.ZipFile(CHECKPOINT) as source:
        for path in sorted((ROOT / 'src/architecture/resources/bloodborne_dw/composite').glob('prototype_*.json')):
            relative = path.relative_to(ROOT).as_posix()
            before = json.loads(source.read(PREFIX + relative))
            current = json.loads(path.read_text(encoding='utf8'))
            thin = current['id'] == 'bloodborne_dw:prototype_thin_window'
            rows.append({'registry': current['id'], 'current_descriptor_sha256': sha(path.read_bytes()),
                'v7': counts(before), 'current': counts(current),
                'root_essential_cells_before': len(before.get('essentialMask', [[0, 0, 0]])),
                'root_essential_cells_current': len(current.get('essentialMask', [[0, 0, 0]])),
                'registered_states_static_v7': 16 * 8 * 2 * 2,
                'registered_states_static_current': 16 * 8 * 2 * 2 * (3 if thin else 1),
                'registered_state_basis': 'variant0..15 × yaw0..7 × open2 × profile2' + (' × mount3' if thin else ''),
                'service_cells_actual': 'PENDING_NATIVE_RUNTIME; depends on orientation, mounting, payload and occupied foreign carriers'})
    report = {'schema': 'dreamwalker-review-v8-static-inventory-v1',
        'status': 'PASS_SOURCE_DESCRIPTOR_COUNTERS_RUNTIME_NOT_MEASURED',
        'checkpoint_sha256': sha(CHECKPOINT.read_bytes()), 'composites': rows,
        'wall_state_counts': {'v7': 32768, 'current': 82944,
            'v7_basis': '16 boolean-side masks×2post×2water×8material×8yaw×2course×2profile×2connections',
            'current_basis': '81 native side shapes×2up×2water×8material×8yaw×2profile×2connections',
            'classification': 'Static property Cartesian product, not selector allocations or measured runtime memory'},
        'ladder_state_counts': {'v7': 192, 'current': 192,
            'basis': '4facing×2diagonal×3variant×2profile×2source_clone×2water',
            'current_normal_sections': 'One coarse physical volume and zero owned helper cells; the real native support keeps its own physics.',
            'current_source_clone_sections': 'Original3/16 physical rung plane plus separately owned fixed original full-cube backing; unchanged source migration contract'},
        'performance_measured': False, 'native_registered_state_counts_verified': False,
        'limits': ['Descriptor cuboid counts are distinct from VoxelShape decomposition and clipped per-cell fragments.',
            'Registered state products are static source declarations; fresh native runtime reports are separate.',
            'Fewer lines/boxes alone do not prove CPU, memory or frame-time improvement.']}
    path = ROOT / 'reports/REVIEW_V8_STATIC_INVENTORY.json'
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({'status': report['status'], 'composites': len(rows), 'wall_static_states': 82944}))
if __name__ == '__main__': main()
