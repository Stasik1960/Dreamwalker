"""Make a publication copy without player profiles/chat; preserve map payload bytes."""
from __future__ import annotations

import argparse
import copy
import hashlib
import io
import json
import zipfile
from pathlib import Path, PurePosixPath, PureWindowsPath

from world_io import compound, decode_nbt, encode_nbt

PRIVATE_FOLDERS = {'playerdata', 'advancements', 'stats', 'ftbessentials',
                   'rpchat', 'dw_languages', 'serverconfig'}
PRIVATE_FILES = {('data', 'cpm.json')}


def safe_name(name):
    path = PurePosixPath(name)
    if path.is_absolute() or PureWindowsPath(name).drive or '..' in path.parts or '\\' in name or path.as_posix() != name:
        raise ValueError('unsafe archive name')
    return path


def sha(data):
    return hashlib.sha256(data).hexdigest()


def public_world(payload):
    output = io.BytesIO()
    removed = players = preserved = 0
    with zipfile.ZipFile(io.BytesIO(payload)) as source, zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED, compresslevel=2) as target:
        seen = set()
        for info in source.infolist():
            path = safe_name(info.filename)
            if len(path.parts) < 2 or info.filename.casefold() in seen:
                raise ValueError('invalid world archive structure')
            seen.add(info.filename.casefold())
            relative = tuple(p.casefold() for p in path.parts[1:])
            if relative[0] in PRIVATE_FOLDERS or relative[:2] == ('customnpcs', 'playerdata') or relative in PRIVATE_FILES:
                removed += 1
                continue
            data = source.read(info)
            if relative == ('level.dat',):
                nbt = decode_nbt(data, compressed='gzip' if data[:2] == b'\x1f\x8b' else None)
                expected = copy.deepcopy(nbt)
                expected_data = compound(compound(expected.root)['Data'])
                player = expected_data.pop('Player', None)
                if player is not None:
                    compound(compound(nbt.root)['Data']).pop('Player')
                    data = encode_nbt(nbt, compressed='gzip')
                    if decode_nbt(data, compressed='gzip') != expected:
                        raise ValueError('world metadata changed beyond Player')
                    players += 1
            target.writestr(info, data)
        # Check every retained map/data file against its source, not only regions.
    with zipfile.ZipFile(io.BytesIO(payload)) as source, zipfile.ZipFile(io.BytesIO(output.getvalue())) as target:
        if target.testzip():
            raise ValueError('public world CRC failure')
        for info in target.infolist():
            if safe_name(info.filename).parts[1:] != ('level.dat',):
                if target.read(info.filename) != source.read(info.filename):
                    raise ValueError('retained world payload changed')
                preserved += 1
    return output.getvalue(), {'removedEntries': removed, 'removedPlayerTags': players,
                              'unchangedRetainedFiles': preserved}


def publish(source, output):
    source, output = Path(source).resolve(), Path(output).resolve()
    if source == output or output.exists():
        raise ValueError('publication output must be new')
    original_sha = sha(source.read_bytes())
    stats = {}
    with zipfile.ZipFile(source) as archive:
        files, seen = {}, set()
        for info in archive.infolist():
            name = safe_name(info.filename)
            if name.parts[0] != 'Bloodborne-Launch-Base' or info.filename.casefold() in seen:
                raise ValueError('unexpected bundle entry')
            seen.add(info.filename.casefold())
            files[info.filename] = archive.read(info)
    prefix = 'Bloodborne-Launch-Base/'
    for name in ('Main-City.zip', 'Approval-Gallery.zip'):
        files[prefix + name], stats[name] = public_world(files[prefix + name])
    files[prefix + 'README.md'] += '''
## Копия для публикации

Из архивов миров удалены профили и история игроков, журналы RP Chat, сохранённые
знания игроков, личные данные FTB/CustomNPCs/CPM, serverconfig и Data.Player в level.dat.
Все оставшиеся файлы карты, включая region, entities, poi и данные NPC,
сохранены без изменения байтов. Исходный локальный комплект не изменён.
'''.encode('utf8')
    records = [{'path': name.removeprefix(prefix), 'bytes': len(data), 'sha256': sha(data)}
               for name, data in sorted(files.items()) if name != prefix + 'Files-SHA256.json']
    files[prefix + 'Files-SHA256.json'] = (json.dumps({'files': records}, ensure_ascii=False, indent=2) + '\n').encode('utf8')
    output.parent.mkdir(parents=True, exist_ok=True)
    staging = output.with_suffix('.zip.building')
    if staging.exists():
        raise ValueError('publication staging output already exists')
    with zipfile.ZipFile(staging, 'w', zipfile.ZIP_DEFLATED, compresslevel=2) as archive:
        for name, data in sorted(files.items()):
            archive.writestr(name, data)
    with zipfile.ZipFile(staging) as archive:
        if archive.testzip():
            raise ValueError('publication CRC failure')
        for row in records:
            if sha(archive.read(prefix + row['path'])) != row['sha256']:
                raise ValueError('publication digest mismatch')
    if sha(source.read_bytes()) != original_sha:
        raise ValueError('local original bundle changed')
    staging.replace(output)
    return {'worlds': stats, 'sourceUnchanged': True, 'sha256': sha(output.read_bytes())}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    print(json.dumps(publish(args.source, args.output)), flush=True)
