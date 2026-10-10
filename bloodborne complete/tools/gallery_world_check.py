"""Typed disk verification/finalization of the explicitly authored isolated gallery."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import uuid
from world_io import Tag, RegionFile, read_nbt, write_nbt

ROOT = Path(__file__).resolve().parents[1]

def plain(tag):
    if tag.type == 10:
        return {key: plain(value) for key, value in tag.value.items()}
    if tag.type == 9:
        return [plain(value) for value in tag.value]
    return list(tag.value) if isinstance(tag.value, bytes) else tag.value

def identity(value):
    if isinstance(value, str):
        try:
            return str(uuid.UUID(value))
        except ValueError:
            return None  # A registry ID is not an instance UUID.
    if isinstance(value, list) and len(value) == 4:
        bits = 0
        for word in value:
            bits = (bits << 32) | (word & 0xffffffff)
        return str(uuid.UUID(int=bits))
    return None

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world', required=True, type=Path)
    parser.add_argument('--manifest', required=True, type=Path)
    parser.add_argument('--report', required=True, type=Path)
    parser.add_argument('--prepare-entry', action='store_true')
    args = parser.parse_args()
    world = args.world.resolve()
    if not world.is_relative_to(ROOT / 'build') or world.name not in ('isolated-smoke-world','prototype-fixture'):
        raise ValueError('Only this project isolated authored gallery is allowed')
    manifest = json.loads(args.manifest.read_text(encoding='utf8'))
    if manifest['status'] != 'PASS_AUTHORED_REQUIRES_PRODUCTION_REOPEN':
        raise ValueError('Authoring did not pass')
    level = read_nbt(world / 'level.dat')
    data = level.root.value['Data'].value
    if args.prepare_entry:
        # Only explicit singleplayer entry metadata changes. Preserve every other typed field.
        data['allowCommands'] = Tag(1, 1)
        data['GameType'] = Tag(3, 1)
        packs = data['DataPacks'].value
        enabled = packs['Enabled'].value
        name = 'file/dw_gallery_entry'
        if name not in [tag.value for tag in enabled]:
            enabled.append(Tag(8, name))
        packs['Disabled'].value = [tag for tag in packs['Disabled'].value if tag.value != name]
        write_nbt(world / 'level.dat', level)
    found = {}
    blocks = {}
    for path in sorted((world / 'region').glob('*.mca')):
        for chunk in RegionFile.open(path).chunks():
            doc = plain(chunk.nbt().root)
            for entity in doc.get('block_entities', []):
                resident = entity.get('resident', {})
                ident = identity(resident.get('uuid')) or identity(resident.get('id'))
                if ident:
                    found[ident] = {'representation': 'real_architecture_root', 'data': entity}
            for section in doc.get('sections', []):
                for entry in section.get('block_states', {}).get('palette', []):
                    blocks[entry['Name']] = blocks.get(entry['Name'], 0) + 1
    for path in sorted((world / 'entities').glob('*.mca')):
        if path.stat().st_size == 0:
            continue  # Minecraft writes zero-byte files for regions without any saved entities.
        for chunk in RegionFile.open(path).chunks():
            for entity in plain(chunk.nbt().root).get('Entities', []):
                ident = identity(entity.get('UUID'))
                if ident:
                    if ident in found:
                        raise ValueError('Duplicate UUID: ' + ident)
                    found[ident] = {'representation': entity['id'], 'data': entity}
    missing = [row['uuid'] for row in manifest['exhibits'] if row['uuid'] not in found]
    if missing:
        raise ValueError('Saved exhibit UUIDs missing: ' + ', '.join(missing))
    retired = [key for key in blocks if key.startswith('bloodborne_dw:') and
               (key.split(':')[1] in {'prototype_ladder_art_1', 'prototype_ladder_art_2'} or
                key.split(':')[1] in {'prototype_wall_skin_' + str(n) for n in (2,3,4,5,7)})]
    if retired:
        raise ValueError('Retired architecture was offered in gallery: ' + ', '.join(retired))
    rows = []
    for row in manifest['exhibits']:
        actual = found[row['uuid']]['data']
        if row['kind'] in ('rp_object', 'rp_mob') and actual['id'] != row['registry']:
            raise ValueError('Entity type mismatch: ' + row['id'])
        if row['kind'] == 'rp_mob' and (not actual.get('NoAI') or not actual.get('PersistenceRequired')):
            raise ValueError('NPC may escape/despawn: ' + row['id'])
        if row['note'] == 'stand_cage_obj_2' and actual.get('DogsVisible', 1) != 0:
            raise ValueError('Hidden dog state lost')
        rows.append({'id': row['id'], 'uuid': row['uuid'], 'registry': row['registry'],
                     'savedType': actual['id'], 'savedState': actual})
    mod_data = {}
    for path in sorted((world / 'data').glob('bloodborne*.dat')):
        mod_data[path.name] = plain(read_nbt(path).root)
    report = {'status': 'PASS_SAVED_GALLERY_DISK', 'world': str(world),
              'manifestSha256': hashlib.sha256(args.manifest.read_bytes()).hexdigest(),
              'catalogueTypes': manifest['catalogueTypes'], 'verifiedExhibits': len(rows),
              'allowCommands': data.get('allowCommands').value if data.get('allowCommands') else None,
              'gameType': data['GameType'].value, 'enabledDatapacks': plain(data['DataPacks'])['Enabled'],
              'savedModData': mod_data, 'exhibits': rows,
              'scope': 'Actual saved NBT, not visual/client input acceptance'}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({key: report[key] for key in ('status','catalogueTypes','verifiedExhibits','allowCommands','gameType')}))

if __name__ == '__main__':
    main()
