"""Create an isolated exact Mojang asset cache; existing installations are read-only."""
from __future__ import annotations
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import json
import os
from pathlib import Path
import re
import time
from urllib.parse import urlsplit
from urllib.request import Request, urlopen
import uuid

ROOT = Path(__file__).resolve().parents[1]
HASH = re.compile(r'[0-9a-f]{40}')
OFFICIAL_INDEX_HOSTS = {'piston-meta.mojang.com', 'launchermeta.mojang.com'}

def sha1(data: bytes) -> str:
    return hashlib.sha1(data).hexdigest()

def matching(path: Path, expected: str, size: int | None = None) -> bytes | None:
    if not path.is_file() or (size is not None and path.stat().st_size != size):
        return None
    data = path.read_bytes()
    return data if sha1(data) == expected else None

def official_fetch(url: str, expected: str, size: int | None, allowed_hosts: set[str]) -> bytes:
    location = urlsplit(url)
    if location.scheme != 'https' or location.hostname not in allowed_hosts or location.username or location.password:
        raise ValueError('Asset download must use an official HTTPS endpoint')
    last = None
    for attempt in range(3):
        try:
            with urlopen(Request(url, headers={'User-Agent': 'Dreamwalker-isolated-asset-verifier/1'}), timeout=30) as response:
                final = urlsplit(response.geturl())
                if final.scheme != 'https' or final.hostname not in allowed_hosts:
                    raise ValueError('Asset redirect left official HTTPS endpoint')
                data = response.read()
            if sha1(data) != expected or (size is not None and len(data) != size):
                raise ValueError('Official asset checksum/size mismatch: ' + expected)
            return data
        except (OSError, ValueError) as error:
            last = error
            if attempt < 2:
                time.sleep(attempt + 1)
    raise RuntimeError('Verified asset fetch failed: ' + expected) from last

def owned_write(path: Path, data: bytes, owned_root: Path) -> None:
    path.resolve().relative_to(owned_root)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.' + uuid.uuid4().hex + '.download.tmp')
    temporary.write_bytes(data)
    os.replace(temporary, path)

def ensure_assets(minecraft_metadata, projectROOT, userHome):
    """Return (assets directory, exact index path, checksum/source provenance).

    Metadata accepts the decoded minecraft-info dictionary or a saved JSON path.
    Only <projectROOT>/build/client-assets-1.20.1-<expected SHA1> is written.
    Three explicit existing object caches may be read; no account/config files.
    """
    metadata = (minecraft_metadata if isinstance(minecraft_metadata, dict)
                else json.loads(Path(minecraft_metadata).read_text(encoding='utf8')))
    if metadata.get('id') != '1.20.1':
        raise ValueError('This isolated helper is scoped to Minecraft1.20.1')
    entry = metadata['assetIndex']
    expected = str(entry['sha1']).lower()
    if HASH.fullmatch(expected) is None or not re.fullmatch(r'[A-Za-z0-9_.-]+', str(entry['id'])):
        raise ValueError('Invalid asset index id/checksum')
    project = Path(projectROOT).resolve()
    user = Path(userHome).resolve()
    build = (project / 'build').resolve()
    build.relative_to(project)
    assets = (build / ('client-assets-1.20.1-' + expected)).resolve()
    assets.relative_to(build)
    assets.mkdir(parents=True, exist_ok=True)
    index_path = assets / 'indexes' / (str(entry['id']) + '.json')
    index = matching(index_path, expected, entry.get('size'))
    index_source = 'existing_verified_isolated_cache'
    if index is None:
        index = official_fetch(entry['url'], expected, entry.get('size'), OFFICIAL_INDEX_HOSTS)
        owned_write(index_path, index, assets)
        index_source = 'official_download'
    decoded = json.loads(index)
    objects = decoded['objects']
    unique = {}
    for name, obj in objects.items():
        object_hash = obj['hash']
        size = obj['size']
        if HASH.fullmatch(object_hash) is None or type(size) is not int or size < 0:
            raise ValueError('Invalid object checksum/size in exact asset index: ' + name)
        if object_hash in unique and unique[object_hash] != size:
            raise ValueError('Same asset hash has conflicting sizes')
        unique[object_hash] = size
    cache_roots = [user / '.gradle/caches/fabric-loom/assets',
                   user / 'AppData/Roaming/.minecraft/assets',
                   user / 'Limacina/project/dw/assets']
    comparisons = []
    for cache in cache_roots:
        indexes = [cache / 'indexes' / (str(entry['id']) + '.json'),
                   cache / 'indexes' / ('1.20.1-' + str(entry['id']) + '.json')]
        for candidate in indexes:
            if not candidate.is_file():
                continue
            cached_data = candidate.read_bytes()
            cached_objects = json.loads(cached_data).get('objects', {})
            comparisons.append({'index': str(candidate), 'sha1': sha1(cached_data),
                                'byte_sha1_matches': sha1(cached_data) == expected,
                                'decoded_objects_equal': cached_objects == objects,
                                'cached_logical_objects': len(cached_objects),
                                'expected_logical_objects': len(objects),
                                'expected_entries_equal': sum(cached_objects.get(k) == v for k, v in objects.items()),
                                'missing_or_changed_expected_entries': sum(cached_objects.get(k) != v for k, v in objects.items())})

    def complete_object(object_hash, size):
        target = assets / 'objects' / object_hash[:2] / object_hash
        if matching(target, object_hash, size) is not None:
            return {'hash': object_hash, 'bytes': size, 'source': 'existing_verified_isolated_cache'}
        invalid_candidates = []
        for cache in cache_roots:
            candidate = cache / 'objects' / object_hash[:2] / object_hash
            data = matching(candidate, object_hash, size)
            if data is not None:
                owned_write(target, data, assets)
                return {'hash': object_hash, 'bytes': size, 'source': 'copied_verified_cache',
                        'source_path': str(candidate), 'invalid_read_only_candidates': invalid_candidates}
            if candidate.is_file():
                invalid_candidates.append(str(candidate))
        url = 'https://resources.download.minecraft.net/' + object_hash[:2] + '/' + object_hash
        data = official_fetch(url, object_hash, size, {'resources.download.minecraft.net'})
        owned_write(target, data, assets)
        return {'hash': object_hash, 'bytes': size, 'source': 'official_download', 'url': url,
                'invalid_read_only_candidates': invalid_candidates}

    records = []
    with ThreadPoolExecutor(max_workers=4) as pool:
        pending = {pool.submit(complete_object, object_hash, size): object_hash
                   for object_hash, size in unique.items()}
        for future in as_completed(pending):
            records.append(future.result())
    counts = Counter(row['source'] for row in records)
    provenance = {'schema': 'dreamwalker-isolated-client-assets-v1', 'status': 'PASS_ALL_ASSETS_VERIFIED',
                  'minecraft': '1.20.1', 'assets_directory': str(assets), 'asset_index_path': str(index_path),
                  'asset_index_id': entry['id'], 'asset_index_url': entry['url'],
                  'asset_index_expected_sha1': expected, 'asset_index_actual_sha1': sha1(index),
                  'asset_index_bytes': len(index), 'asset_index_source': index_source,
                  'logical_object_count': len(objects), 'unique_object_count': len(unique),
                  'verified_object_count': len(records), 'total_unique_bytes': sum(unique.values()),
                  'source_counts': dict(counts), 'cache_index_comparisons': comparisons,
                  'allowed_read_only_cache_roots': [str(p) for p in cache_roots],
                  'existing_installations_modified': False,
                  'object_records': sorted(records, key=lambda row: row['hash'])}
    initial = assets / 'asset-provenance-initial.json'
    if not initial.is_file():
        initial_data = (json.dumps(provenance, ensure_ascii=False, indent=2) + '\n').encode('utf8')
        owned_write(initial, initial_data, assets)
    provenance['initial_completion_provenance'] = str(initial)
    provenance['initial_completion_provenance_sha256'] = hashlib.sha256(initial.read_bytes()).hexdigest()
    data = (json.dumps(provenance, ensure_ascii=False, indent=2) + '\n').encode('utf8')
    owned_write(assets / 'asset-provenance.json', data, assets)
    return assets, index_path, provenance

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--metadata', type=Path, required=True)
    parser.add_argument('--project-root', type=Path, default=ROOT)
    parser.add_argument('--user-home', type=Path, default=Path('C:/Users/vakir'))
    args = parser.parse_args()
    assets, index, provenance = ensure_assets(args.metadata, args.project_root, args.user_home)
    print(json.dumps({'status': provenance['status'], 'assetsDir': str(assets), 'indexPath': str(index),
                      'logical_objects': provenance['logical_object_count'],
                      'unique_objects': provenance['unique_object_count'],
                      'sources': provenance['source_counts'], 'index_sha1': provenance['asset_index_actual_sha1']}), flush=True)

if __name__ == '__main__':
    main()
