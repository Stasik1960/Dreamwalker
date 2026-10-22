"""Freeze disjoint per-pose routes from confirmed unique-type evidence."""
from pathlib import Path
from collections import Counter
import argparse, hashlib, json
from export_cells import canonical, digest, state_key


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def build(request_path, protected_path, statues_path, planes_path):
    request, protected_doc, statues_doc, planes_doc = [read(p) for p in (request_path, protected_path, statues_path, planes_path)]
    protected = {r['registryId'] for r in protected_doc['entries']}
    descriptors = {r['registryId']: r for r in planes_doc['types']}
    statue_rows = {r['registryId']: r for r in statues_doc['entries']}
    poses = []
    templates = []
    by_registry = {}
    counts = Counter()
    for source in request['poses']:
        rid = source['registryId']
        props = source.get('properties', {})
        variant = int(props.get('variant', '0'))+16*int(props.get('art_bank', '0'))
        desc = descriptors.get(rid, {})
        statue = statue_rows.get(rid)
        variant_desc = next((r for r in desc.get('variants', []) if r['variant'] == variant), {})
        if rid in protected:
            route, reason = 'preserve', 'historic_v12_original_or_alias'
        elif rid.startswith('bloodborne_rp:'):
            route, reason = 'preserve', 'real_rp_function_or_entity'
        elif rid == 'bloodborne_dw:source_hunter_lamp_light':
            route, reason = 'preserve', 'two_original_hunter_lamp_technical_light_cells_luminance9'
        elif desc.get('openable'):
            route, reason = 'preserve', 'standalone_whole_openable_object'
        elif variant_desc.get('spriteTree'):
            route, reason = 'preserve', 'standalone_whole_sprite_tree_confirmed_actual_planes'
        elif statue and statue.get('classification') == 'whole_statue':
            allowed = {r['variant'] for r in statue.get('variants', [])}
            if not allowed or variant in allowed or statue.get('canonicalRegistryId'):
                route, reason = 'whole', 'confirmed_sculpture_whole_object_template'
            else:
                route, reason = 'preserve', 'specific_statue_variant_not_in_confirmed_models'
        elif statue and statue.get('classification', '').startswith('preserve'):
            route, reason = 'preserve', statue['classification']
        elif str(props.get('compact', 'false')).lower() == 'true':
            route, reason = 'preserve', 'already_native_compact_state'
        elif not desc:
            route, reason = 'preserve', 'specific_type_missing_registered_composite_descriptor'
        else:
            route, reason = 'static', 'static_decoration_pose_baked_into_independent_cells'
        row = {'key': source['key'], 'registryId': rid, 'variant': variant, 'route': route, 'reason': reason, 'instanceCount': source.get('objectCount', source.get('count', source.get('instances', 0)))}
        if route == 'whole':
            row['template'] = 'wo_'+digest({k: v for k, v in source.items() if k not in ('key', 'count', 'instances', 'objectCount')})[:32]
            templates.append({'key': row['template'], 'pose': source['key'], 'displayName': statue.get('name', desc.get('displayName', 'Статуя')), 'registryId': rid, 'catalogueNumber': statue.get('catalogueNumber')})
        poses.append(row)
        counts[route] += 1
        reg = by_registry.setdefault(rid, {'registryId': rid, 'poses': [], 'routes': {}, 'reasons': []})
        reg['poses'].append(row['key'])
        reg['routes'][str(variant)] = route
        if reason not in reg['reasons']:
            reg['reasons'].append(reason)
    result = {'schemaVersion': 1, 'status': 'CONFIRMED', 'precedence': ['historic_v12', 'real_rp', 'openable', 'actual_sprite_tree_planes', 'confirmed_whole_statue', 'already_native_compact', 'static_decoration'], 'sourcePoseCount': len(poses), 'poseRouteCounts': dict(counts), 'protectedRegistryIds': sorted(protected), 'openableRegistryIds': planes_doc['openableIds'], 'spriteTreeRegistryIds': planes_doc['spriteTreeIds'], 'poses': poses, 'types': sorted(by_registry.values(), key=lambda r: r['registryId']), 'preserveFootprintRule': 'Only each protected object own root/helpers/contributions; never owner-graph connected closure', 'inputs': {str(Path(p).name): hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in (request_path, protected_path, statues_path, planes_path)}}
    return result, templates


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--poses', type=Path, required=True)
    parser.add_argument('--protected', type=Path, required=True)
    parser.add_argument('--statues', type=Path, required=True)
    parser.add_argument('--planes', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--whole-objects', type=Path, required=True)
    parser.add_argument('--export-request', type=Path)
    parser.add_argument('--additional-native', type=Path)
    args = parser.parse_args()
    result, templates = build(args.poses, args.protected, args.statues, args.planes)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    args.whole_objects.parent.mkdir(parents=True, exist_ok=True)
    args.whole_objects.write_text(''.join(canonical(r)+'\n' for r in templates), encoding='utf-8')
    if args.export_request:
        source = read(args.poses)
        required = {row['key'] for row in result['poses'] if row['route'] in ('static', 'whole')}
        filtered = {**source, 'poses': [row for row in source['poses'] if row['key'] in required], 'preservedPoseCountNotExported': len(source['poses'])-len(required), 'classificationSha256': hashlib.sha256(args.out.read_bytes()).hexdigest()}
        if args.additional_native:
            extra = read(args.additional_native)
            states = extra if isinstance(extra, list) else extra.get('states', extra.get('nativeStates', []))
            by_state = {state_key(row): row for row in source.get('nativeStates', [])}
            for row in states:
                by_state.setdefault(state_key(row), row)
            filtered['nativeStates'] = [by_state[key] for key in sorted(by_state)]
            filtered['additionalNativeSha256'] = hashlib.sha256(args.additional_native.read_bytes()).hexdigest()
        args.export_request.parent.mkdir(parents=True, exist_ok=True)
        args.export_request.write_text(json.dumps(filtered, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': result['status'], 'poseRouteCounts': result['poseRouteCounts'], 'wholeTemplates': len(templates)}))


if __name__ == '__main__':
    main()
