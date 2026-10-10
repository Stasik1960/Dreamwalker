"""Capture saved V12 native fixtures/persistent state and optionally compare a prior snapshot.

Read-only world_io parsing, no third-party NBT dependency, game process or world writes. Use after
save-all flush or normal shutdown; files changing during the capture are rejected.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import stat

from world_io import RegionFile, decode_nbt
from build_v11_gallery import plain, identity

ROOT = Path(__file__).resolve().parents[1]
ORDERLESS = {'Entries', 'Groups', 'Nodes', 'Rules', 'Flags', 'Queue', 'Sources', 'Targets', 'Active', 'Imported', 'Players'}
ENTITY_FIELDS = ('id', 'UUID', 'Pos', 'Rotation', 'Open', 'Locked', 'DogsVisible', 'Scale',
                 'VerticalOffset', 'CustomName', 'CustomNameVisible', 'Links', 'NoGravity')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def regular(path):
    for node in (path, *path.parents):
        info = node.lstat()
        require(not stat.S_ISLNK(info.st_mode) and not getattr(info, 'st_file_attributes', 0) & 0x400,
                'Reparse/symlink input unsupported: ' + str(node))
    require(path.is_file(), 'Regular input required: ' + str(path))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def canonical(value, key=''):
    if isinstance(value, dict):
        return {k: canonical(v, k) for k, v in sorted(value.items())}
    if isinstance(value, list):
        rows = [canonical(v) for v in value]
        return sorted(rows, key=lambda v: json.dumps(v, sort_keys=True)) if key in ORDERLESS else rows
    return value


def signed(value, bits):
    return value - (1 << bits) if value >= 1 << (bits - 1) else value


def position(value):
    bits = value & 0xffffffffffffffff
    return [signed(bits >> 38, 26), signed(bits & 4095, 12), signed((bits >> 12) & 0x3ffffff, 26)]


def ref(value):
    return {**value, 'decodedUuid': identity(value.get('UUID', value.get('Instance'))),
            'decodedRoot': position(value['Root']) if 'Root' in value else None}


def summaries(data):
    tool = data.get('bloodborne_dw_tool_commands', {})
    commands = [{**row, 'Target': ref(row['Target']),
                 'Players': [{**p, 'decodedUuid': identity(p.get('UUID'))} for p in row.get('Players', [])]}
                for row in tool.get('Entries', [])]
    lever = data.get('bloodborne_dw_lever_groups', {})
    groups = []
    for group in lever.get('Groups', []):
        active = {identity(v) for v in group.get('Active', [])}
        sources = [{**ref(v), 'active': identity(v.get('Instance')) in active} for v in group.get('Sources', [])]
        groups.append({'uuid': identity(group.get('Id')), 'target': ref(group['Target']), 'sources': sources,
                       'sourceCount': len(sources), 'activeUuids': sorted(active),
                       'allFlagsFalse': not active, 'allFlagsTrue': bool(sources) and len(active) == len(sources),
                       'pending': bool(group['Pending']) if 'Pending' in group else None,
                       'initiator': group.get('Initiator')})
    lamps = data.get('bloodborne_rp_lamps', {})
    nodes = {identity(n['Id']): n for n in lamps.get('Nodes', [])}
    lamp_rows = []
    for node_id, node in nodes.items():
        partner_id = identity(node.get('Partner'))
        other = nodes.get(partner_id)
        lamp_rows.append({'nodeUuid': node_id, 'lampUuid': identity(node['Lamp']), 'partnerNodeUuid': partner_id,
                          'partnerLampUuid': identity(other['Lamp']) if other else None,
                          'mutual': other is not None and identity(other.get('Partner')) == node_id,
                          'dimension': node.get('Dimension'), 'blockPos': position(node['Pos']),
                          'position': [node.get(k) for k in ('X', 'Y', 'Z')]})
    policies = [{**row, 'Ref': ref(row['Ref'])}
                for row in data.get('bloodborne_dw_object_policies', {}).get('Entries', [])]
    return {'toolCommands': commands, 'leverGroups': groups, 'lampSchema': lamps.get('Schema'),
            'lampNodes': lamp_rows, 'objectPolicies': policies,
            'leverSemantics': 'Active stores true source UUIDs only. Active=[] means every saved source flag false; Open is separately saved on the target entity.'}


def log_review(path):
    regular(path)
    raw = path.read_bytes()
    text = raw.decode('utf16' if raw[:2] in (b'\xff\xfe', b'\xfe\xff') else 'utf-8-sig', errors='replace')
    headers = list(re.finditer(r'^\[[^\r\n]+\] \[[^\r\n]+/(?:WARN|ERROR)\]:[^\r\n]*', text, re.M))
    # A block ends at the next log header, including INFO, so auth stack traces
    # remain attached to their one ERROR without swallowing later warnings.
    known, unknown_errors, other_warnings = [], [], []
    for header in headers:
        next_header = re.search(r'^\[[^\r\n]+\] \[', text[header.end():], re.M)
        end = header.end() + next_header.start() if next_header else len(text)
        block = text[header.start():end].rstrip()
        title = header.group(0)
        category = None
        if 'Failed to verify authentication' in title and 'Status: 401' in block and 'com.mojang.authlib' in block:
            category = 'offline_launcher_auth401'
        elif 'Error loading class: com/jamieswhiteshirt/reachentityattributes/ReachEntityAttributes' in title:
            category = 'absent_optional_reachentityattributes'
        elif 'Shader rendertype_entity_translucent_emissive' in title and 'Sampler2' in title:
            category = 'vanilla_shader_sampler2'
        elif 'Missing sound for event: minecraft:' in title:
            category = 'vanilla_missing_sound'
        elif ' moved too quickly!' in title:
            category = 'ordinary_console_teleport_warning'
        elif any(v in title for v in ('SERVER IS RUNNING IN OFFLINE/INSECURE MODE', 'make no attempt to authenticate usernames',
                                     'ability for hackers to connect with any username', 'change "online-mode" to "true"', 'server.properties file')):
            category = 'explicit_offline_test_server'
        if category:
            known.append({'category': category, 'header': title})
        elif '/ERROR]' in title or re.search(r'(?:Exception|dev\.dreamwalker\.|Malformed saved|Ignoring invalid saved)', block):
            unknown_errors.append(block)
        else:
            other_warnings.append(title)
    return {'path': str(path), 'bytesRead': len(raw), 'sha256': sha(raw),
            'knownWarningsOrAuthErrors': known, 'unclassifiedErrorsOrModWarnings': unknown_errors,
            'otherWarnings': other_warnings, 'modErrorDetected': bool(unknown_errors),
            'scope': 'Log snapshot at capture time, including exact unclassified blocks; no frame-rate/gameplay inference'}


def differences(before, after, path='semanticState'):
    if isinstance(before, dict) and isinstance(after, dict):
        result = []
        for key in sorted(set(before) | set(after)):
            if key not in before or key not in after:
                result.append({'path': path + '.' + key, 'before': before.get(key), 'after': after.get(key)})
            else:
                result.extend(differences(before[key], after[key], path + '.' + key))
        return result
    return [] if before == after else [{'path': path, 'before': before, 'after': after}]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--plan', type=Path, default=ROOT / 'build/v12-delivery/v12-native-acceptance-plan.json')
    parser.add_argument('--compare', type=Path, help='Prior snapshot; compares saved semantics, excluding world time/file metadata')
    parser.add_argument('--log', type=Path, action='append', default=[])
    args = parser.parse_args()
    world, output = args.world.absolute(), args.output.absolute()
    require(world.is_relative_to(ROOT / 'build') and world.is_dir(), 'Read only isolated project-build world')
    require(output.resolve().is_relative_to((ROOT / 'build').resolve()), 'Write report only under ignored project build')
    regular(args.plan)
    plan_raw = args.plan.read_bytes()
    plan = json.loads(plan_raw.decode('utf-8-sig'))
    actors = {row['uuid']: row for row in plan['fixtureActors']}
    files, sources = {}, []

    def read_saved(path):
        regular(path)
        raw = path.read_bytes()
        files[path] = sha(raw)
        sources.append({'path': str(path.relative_to(world)), 'sha256': files[path], 'bytes': len(raw),
                        'modifiedUtc': datetime.fromtimestamp(path.stat().st_mtime, timezone.utc).isoformat()})
        return raw

    data, raw_data = {}, {}
    for path in sorted((world / 'data').glob('*.dat')):
        if not (path.name.startswith('bloodborne_') or path.name == 'scoreboard.dat'):
            continue
        document = plain(decode_nbt(read_saved(path), compressed='gzip').root)
        raw_data[path.stem] = document
        data[path.stem] = document.get('data', document)
    required = {'bloodborne_dw_tool_commands', 'bloodborne_dw_lever_groups', 'bloodborne_rp_lamps', 'bloodborne_dw_object_policies'}
    require(required <= data.keys(), 'Missing saved persistent data: ' + str(required - data.keys()))
    fixtures = {}
    for directory in (world / 'entities', world / 'DIM-1/entities', world / 'DIM1/entities'):
        for path in sorted(directory.glob('*.mca')):
            raw = read_saved(path)
            if not raw:
                continue
            for chunk in RegionFile(raw).chunks():
                for entity in plain(chunk.nbt().root).get('Entities', []):
                    key = identity(entity.get('UUID'))
                    if key in actors:
                        require(key not in fixtures, 'Repeated saved fixture UUID: ' + key)
                        expected = actors[key]
                        require(entity.get('id') == expected['registry'], 'Fixture registry differs: ' + key)
                        fixtures[key] = {'role': expected['role'], 'registry': entity['id'], 'uuid': key,
                                         'preparedPosition': expected['position'], 'savedEntity': entity,
                                         'state': {k: entity[k] for k in ENTITY_FIELDS if k in entity}}
    for path, expected in files.items():
        require(sha(path.read_bytes()) == expected, 'Input changed during capture; retry after completed save: ' + str(path))
    missing = sorted(set(actors) - set(fixtures))
    semantic = canonical({'persistentData': data, 'fixtures': {key: row['state'] for key, row in fixtures.items()}})
    result = {'schema': 'dreamwalker-v12-saved-native-state-v1', 'status': 'CAPTURED_SAVED_STATE',
              'capturedUtc': datetime.now(timezone.utc).isoformat(), 'productionJarSha256': plan['productionJarSha256'],
              'world': str(world), 'plan': {'path': str(args.plan.absolute()), 'sha256': sha(plan_raw)},
              'sourcesStableThroughCapture': True, 'sourceFiles': sources, 'persistentNbt': raw_data,
              'summaries': summaries(data), 'fixtures': fixtures, 'missingFixtureUuids': missing,
              'semanticState': semantic, 'logs': [log_review(path) for path in args.log],
              'scope': 'Last saved NBT bytes only; not unsaved live memory. Files were individually read and rehashed; no atomic server-memory snapshot claim.'}
    if args.compare:
        regular(args.compare)
        prior_raw = args.compare.read_bytes()
        prior = json.loads(prior_raw.decode('utf-8-sig'))
        require(prior['productionJarSha256'] == result['productionJarSha256'] and prior['plan']['sha256'] == result['plan']['sha256'],
                'Comparison uses another production JAR/fixture plan')
        delta = differences(prior['semanticState'], semantic)
        result['comparison'] = {'priorPath': str(args.compare.absolute()), 'priorSha256': sha(prior_raw),
                                'status': 'PASS_SAVED_SEMANTICS_IDENTICAL' if not delta else 'DIFFERENT_SAVED_SEMANTICS',
                                'differences': delta}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes((json.dumps(result, ensure_ascii=False, indent=2) + '\n').encode('utf8'))
    print(json.dumps({'status': result['status'], 'output': str(output), 'fixtures': len(fixtures),
                      'missingFixtures': missing, 'comparison': result.get('comparison', {}).get('status'),
                      'unclassifiedLogBlocks': sum(len(log['unclassifiedErrorsOrModWarnings']) for log in result['logs'])}), flush=True)


if __name__ == '__main__':
    main()
