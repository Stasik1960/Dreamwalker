"""Request exact compact baked models missing from addressed native physics rows."""
from pathlib import Path
import argparse, hashlib, json, sqlite3
from export_cells import FragmentStore, state_key


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--needed', type=Path, required=True)
    parser.add_argument('--additional-needed', type=Path, help='Additional finite observed native-state list')
    parser.add_argument('--fragments', type=Path, required=True)
    parser.add_argument('--additional-poses', type=Path, help='Addressed additional actual source-pose request')
    parser.add_argument('--door-footprints', type=Path, help='Exact preserved openable source-pose request')
    parser.add_argument('--plan', type=Path, help='Read-only current migration plan for activated root coverage')
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    document = json.loads(args.needed.read_text(encoding='utf-8-sig'))
    rows = document if isinstance(document, list) else document.get('nativeStates', document.get('states', []))
    if args.additional_needed:
        extra = json.loads(args.additional_needed.read_text(encoding='utf-8-sig'))
        rows = [*rows, *(extra if isinstance(extra, list) else extra.get('nativeStates', extra.get('states', [])))]
    store = FragmentStore(args.fragments)
    materials = json.loads((Path(__file__).resolve().parents[2]/'src/architecture/resources/bloodborne_dw/restored-material-slabs.json').read_text(encoding='utf-8-sig'))
    native_slabs = {row['id'] for row in materials['materials']}
    requested, complete, unsupported, missing = {}, set(), {}, {}
    for value in rows:
        state = value.get('state', value)
        state = {'registryId': state.get('registryId', state.get('id')), 'properties': state.get('properties', {})}
        key = state_key(state)
        try:
            native = store.native(key)
        except KeyError:
            missing[key] = state
            continue
        if native.get('nativeVisualComplete', True):
            complete.add(key)
            continue
        # This route is deliberately state-specific. Compact=true at a reviewed
        # even yaw uses the actual vanilla MODEL multipart, without a BE.
        # StaticArchitecture still requires explicit source origin transfer.
        # Reviewed physical-only slab overlays retain a separate native model;
        # the Java exporter enforces resident<0 before accepting that route.
        properties = state['properties']
        compact = str(properties.get('compact', '')).lower() == 'true'
        even_yaw = int(properties.get('rotation', '-1')) in (0, 2, 4, 6)
        class_name = native.get('className', '')
        if (compact and even_yaw and ('ImportedCompactBlock' in class_name or not class_name)) or state['registryId'] in native_slabs:
            requested[key] = {**state, 'physicsOnly': False}
        else:
            unsupported[key] = {'state': state, 'className': class_name, 'reason': 'Needs explicit source-origin substrate visual transfer'}
    poses = {}
    if args.additional_poses:
        source = json.loads(args.additional_poses.read_text(encoding='utf-8-sig'))
        for row in source if isinstance(source, list) else source.get('poses', []):
            if row['key'] in poses and poses[row['key']] != row:
                raise ValueError('One supplemental source key has two meanings: '+row['key'])
            poses[row['key']] = row
    output = {'schemaVersion': 1, 'poses': [poses[key] for key in sorted(poses)], 'nativeStates': [requested[key] for key in sorted(requested)], 'neededSourceSha256': hashlib.sha256(args.needed.read_bytes()).hexdigest(), 'completeNeededStates': len(complete), 'explicitTransferStates': [unsupported[key] for key in sorted(unsupported)]}
    if args.additional_poses:
        output['additionalPosesSha256'] = hashlib.sha256(args.additional_poses.read_bytes()).hexdigest()
    if args.additional_needed:
        output['additionalNeededSha256'] = hashlib.sha256(args.additional_needed.read_bytes()).hexdigest()
    if args.door_footprints:
        doors = json.loads(args.door_footprints.read_text(encoding='utf-8-sig'))
        output['doorFootprints'] = {'requestPath': str(args.door_footprints.absolute()), 'sha256': hashlib.sha256(args.door_footprints.read_bytes()).hexdigest(), 'poses': doors['poses']}
    output['missingNativePhysics'] = [missing[key] for key in sorted(missing)]
    if args.plan:
        plan = sqlite3.connect('file:'+args.plan.absolute().as_posix()+'?mode=ro', uri=True)
        activated = [pose for (pose,) in plan.execute('SELECT DISTINCT o.pose FROM activated_native a JOIN objects o ON o.dim=a.dim AND o.uuid=a.uuid ORDER BY o.pose')]
        absent = [pose for pose in activated if pose not in poses and not next(store.pose_cells(pose), None)]
        output['activatedSourceCoverage'] = {'actualActivatedPoses': len(activated), 'missingPoses': absent}
        plan.close()
    store.close()
    output['coverageStatus'] = 'INCOMPLETE' if missing or output.get('activatedSourceCoverage', {}).get('missingPoses') else 'PASS'
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(output, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': output['coverageStatus'], 'missingNativePhysics': len(missing), 'activatedSourceCoverage': output.get('activatedSourceCoverage'), 'requestedNativeVisuals': len(requested), 'additionalSourcePoses': len(poses), 'alreadyComplete': len(complete), 'explicitTransferStates': len(unsupported), 'requestSha256': hashlib.sha256(args.out.read_bytes()).hexdigest()}))


if __name__ == '__main__':
    main()
