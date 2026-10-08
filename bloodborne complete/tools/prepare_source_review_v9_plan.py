"""Write a fresh V9 source contract; never overwrite an earlier plan or input."""
from pathlib import Path
import argparse
import copy
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DESCRIPTORS = ROOT / 'src/architecture/resources/bloodborne_dw/composite'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def registered_composite_paths():
    """Bounded first-set initializer, including its automatic window02 addition."""
    source = (ROOT / 'src/architecture/java/dev/dreamwalker/bloodbornedw/DreamwalkerBb.java').read_text(encoding='utf8')
    calls = re.findall(r'CompositeArchitecture\.initialize\(([^;]*)\);', source)
    if len(calls) != 1:
        raise ValueError('Expected exactly one explicit composite initializer; review the registration guard')
    strings = re.findall(r'"(prototype_[a-z_0-9]+)"', calls[0])
    if re.sub(r'"prototype_[a-z_0-9]+"|[\s,]', '', calls[0]) or len(set(strings)) != len(strings):
        raise ValueError('Composite initializer is no longer a unique literal first-set list')
    glazing = (ROOT / 'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/GlazingTypes.java').read_text(encoding='utf8')
    names = dict(re.findall(r'public static final String (WINDOW0[12])\s*=\s*"([a-z_0-9]+)";', glazing))
    if set(names) != {'WINDOW01', 'WINDOW02'}:
        raise ValueError('Review the explicit glazing registration aliases')
    architecture = (ROOT / 'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeArchitecture.java').read_text(encoding='utf8')
    compact = re.sub(r'\s+', '', architecture)
    expected = 'if(kinds.contains(GlazingTypes.WINDOW01)&&!kinds.contains(GlazingTypes.WINDOW02))kinds.add(GlazingTypes.WINDOW02);'
    if expected not in compact:
        raise ValueError('Automatic glazing registration changed; do not infer descriptor coverage')
    result = set(strings)
    if names['WINDOW01'] in result:
        result.add(names['WINDOW02'])
    return result


def frozen_descriptor_contract(artifact):
    actual = registered_composite_paths()
    source = {path.stem: path for path in DESCRIPTORS.glob('*.json')}
    if set(source) != actual:
        raise ValueError('Source descriptor files differ from actual registered first-set types')
    prefix = 'bloodborne_dw/composite/'
    with zipfile.ZipFile(artifact) as archive:
        members = {name[len(prefix):-5]: name for name in archive.namelist()
                   if name.startswith(prefix) and name.endswith('.json')}
        if set(members) != actual:
            raise ValueError('Frozen JAR descriptor set differs from current registered types')
        result = {}
        for kind in sorted(actual):
            payload = archive.read(members[kind])
            if payload != source[kind].read_bytes():
                raise ValueError('Source/frozen JAR descriptor bytes differ: ' + kind)
            if json.loads(payload)['id'] != 'bloodborne_dw:' + kind:
                raise ValueError('Descriptor filename/registered ID differ: ' + kind)
            result[kind] = sha(payload)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--from-plan', type=Path, default=ROOT / 'reports/FIRST_SET_MIGRATION_PLAN_V8_FINAL2.json')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    args = parser.parse_args()
    old, output, artifact = args.from_plan.resolve(), args.output.resolve(), args.jar.resolve()
    if output.parent != (ROOT / 'reports').resolve() or output.exists() or output == old:
        raise ValueError('A fresh plan directly under reports is required; earlier plans remain byte exact')
    original_bytes = old.read_bytes()
    plan = copy.deepcopy(json.loads(original_bytes))
    if plan.get('review_revision') not in {'V8', 'V9'}:
        raise ValueError('Only the reviewed immutable V8/V9 bounded source cohort can be extended')
    plan['previousPlan'] = {'path': str(old), 'sha256': sha(original_bytes), 'status': 'HISTORICAL_READ_ONLY'}
    plan['review_revision'] = 'V9'
    plan['runtime_contract'].update({
        'tree': 'Canonical accepted V8 variant1 lower2 and upper16 becomes sole variant0; original root/SourceShift/owner preserved.',
        'roof': 'Visual and selection unchanged; physical unit cube remains axis aligned, translated by MountY and SourceShift.',
        'door': 'User accepted V8 central leaf plus fixed sides/header; unchanged original closed source artwork.',
        'thin_window': 'Legacy source pose may retain original angle; only new ordinary window01/window02 cardinal items issued.',
        'source_ladder_state': 'Exact legacy prototype_ladder source_clone=true, freestanding=false, original art/facing/water/profile retained.',
        'overlap': 'Only exact initial owner UUID under SourceConversionScope may preserve actual original intersections; no saved/item privilege.',
        'runtime_migration': 'NOT_RUN_CURRENT_V9_PLAN',
        'user_acceptance': 'V8_OTHER_ARCH_ACCEPTED_V9_NAMED_FIXES_PENDING'})
    for entry in plan['coordinate_instances']:
        if entry['target_kind'] == 'bloodborne_dw:prototype_tree':
            entry['target_art_variant'] = 0
    plan['descriptor_sha256'] = frozen_descriptor_contract(artifact)
    plan['production_descriptor_artifact'] = {'path': str(artifact), 'sha256': sha(artifact.read_bytes()),
                                            'registered_composite_type_count': len(plan['descriptor_sha256']),
                                            'verification': 'EXACT_REGISTERED_SET_SOURCE_AND_FROZEN_JAR_BYTES'}
    # New ordinary types are covered by the descriptor guard; this cohort's legacy registry IDs remain unchanged.
    plan['ordinaryTypeMapping'] = 'reports/V9_TYPE_ID_MAPPING.json'
    output.write_text(json.dumps(plan, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    assert old.read_bytes() == original_bytes
    print(json.dumps({'status': 'V9_SOURCE_PLAN_PREPARED_RUNTIME_NOT_RUN', 'path': str(output),
                      'sha256': sha(output.read_bytes()), 'descriptor_count': len(plan['descriptor_sha256'])}))


if __name__ == '__main__':
    main()
