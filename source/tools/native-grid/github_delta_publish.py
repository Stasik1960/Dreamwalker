"""Publish a reviewed delta in a FRESH GitHub branch/release; never replace assets.

Root supplies a hash-bound publication config after final capture and local Git
review. Credentials exist only in memory. This script does not pack, restore,
change source worlds, clean files, or modify the old checkpoint release.
"""
import argparse, hashlib, json, os, re, subprocess, sys, urllib.error, urllib.parse, urllib.request
from pathlib import Path

sys.dont_write_bytecode = True
from github_delta_restore import validate_manifest

REPOSITORY = 'Stasik1960/Dreamwalker'
ENV = dict(os.environ, GIT_TERMINAL_PROMPT='0', GCM_INTERACTIVE='never')


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def sha(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1 << 20), b''):
            result.update(block)
    return result.hexdigest()


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def bound(reference):
    require(sha(reference['path']) == reference['sha256'], 'Reviewed local evidence changed')
    return Path(reference['path'])


def save(path, data):
    temporary = path.with_suffix('.tmp')
    with temporary.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(data, stream, ensure_ascii=False, indent=2)
        stream.write('\n'); stream.flush(); os.fsync(stream.fileno())
    os.replace(temporary, path)


def git(directory, *arguments):
    result = subprocess.run(['git', *arguments], cwd=directory, env=ENV, text=True,
        capture_output=True, timeout=1800, creationflags=subprocess.CREATE_NO_WINDOW)
    require(result.returncode == 0, 'Git operation failed: ' + arguments[0])
    return result.stdout.strip()


def local(config):
    require(config.get('schemaVersion') == 1 and config.get('kind') == 'ROOT_REVIEWED_GITHUB_DELTA_PUBLICATION'
        and config.get('repository') == REPOSITORY, 'An explicit reviewed publication config is required')
    branch, tag = config['branch'], config['tag']
    require(re.fullmatch(r'codex/[a-z0-9][a-z0-9._/-]{1,120}', branch) and '..' not in branch
        and re.fullmatch(r'native-grid-city-[a-z0-9][a-z0-9._-]{1,100}', tag), 'Fresh explicit branch/tag required')
    require(branch != 'codex/native-grid-city-resume-20261022' and tag != 'native-grid-city-resume-20261022', 'Old checkpoint is immutable')
    directory = Path(config['gitDirectory']).resolve(); assets = Path(config['assetDirectory']).resolve()
    require(directory != assets and not assets.is_relative_to(directory), 'Keep binary release volumes outside the Git checkout')
    require(Path(git(directory, 'rev-parse', '--show-toplevel')).resolve() == directory, 'Use an independent reviewed Git repository')
    expected_remote = 'https://github.com/' + REPOSITORY + '.git'
    require(git(directory, 'remote', 'get-url', '--all', 'origin').splitlines() == [expected_remote]
        and git(directory, 'remote', 'get-url', '--push', '--all', 'origin').splitlines() == [expected_remote], 'Unexpected fetch/push URL or URL rewrite')
    require(git(directory, 'symbolic-ref', '--short', 'HEAD') == branch and not git(directory, 'status', '--porcelain'), 'Commit and review the exact clean publication branch first')
    commit = git(directory, 'rev-parse', 'HEAD')
    manifest_path = bound(config['manifest']); manifest = read(manifest_path); validate_manifest(manifest)
    require(manifest_path.resolve() == assets / 'bundle-manifest.json' and manifest['schemaVersion'] == 2
        and manifest['kind'] == 'DREAMWALKER_CONTENT_ADDRESSED_DELTA_CONTINUATION', 'Use the exact new delta manifest')
    freeze = manifest['sourceFreeze']['document']
    require(freeze['status'] == 'PASS' and freeze['quiescent'] is True and freeze['projectWritersStopped'] is True, 'Capture must be quiescent')
    ready = manifest['readyForDelivery']
    require(ready is (manifest['deliveryStatus'] == 'READY_FOR_DELIVERY') and config['deliveryStatus'] == manifest['deliveryStatus'], 'Publication may not relabel a checkpoint')
    if ready:
        accepted = read(bound(manifest['deliveryAcceptance']))
        require(accepted['status'] == 'PASS' and accepted['kind'] == 'FULL_NATIVE_GRID_CITY_DELIVERY_ACCEPTANCE'
            and accepted['readyForDelivery'] is True, 'Full delivery needs the actual final aggregate')
    new = [row for row in manifest['volumes'] if row['origin'] == 'delta']
    require(new and len(new) == manifest['delta']['newVolumes'] and manifest['delta']['oldVolumesReuploaded'] == 0, 'Only new delta volumes are uploaded')
    prefix = 'https://github.com/' + REPOSITORY + '/releases/download/' + tag + '/'
    expected = []
    for row in new:
        require(Path(row['name']).name == row['name'] and row['url'] == prefix + urllib.parse.quote(row['name']), 'New volume points to another release')
        path = assets / row['name']
        require(path.stat().st_size == row['bytes'] and sha(path) == row['sha256'], 'Local delta volume changed')
        expected.append({'name': row['name'], 'bytes': row['bytes'], 'sha256': row['sha256']})
    for name in ('bundle-manifest.json', 'SHA256SUMS.txt'):
        path = assets / name; expected.append({'name': name, 'bytes': path.stat().st_size, 'sha256': sha(path)})
    checksum = ''.join(row['sha256'] + '  ' + row['name'] + '\n' for row in expected if row['name'] != 'SHA256SUMS.txt')
    require((assets / 'SHA256SUMS.txt').read_text(encoding='utf-8') == checksum, 'Checksum file does not match the exact new assets')
    notes_path = bound(config['notes']); notes = notes_path.read_text(encoding='utf-8')
    marker = '\n\nManifest SHA256: `' + config['manifest']['sha256'] + '`\nDelivery status: `' + manifest['deliveryStatus'] + '`\n'
    body = notes.rstrip() + marker
    require(config['title'].strip() and len(config['title']) < 200, 'Explicit release title required')
    return directory, assets, commit, manifest, expected, body


class RejectAuthenticatedRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, stream, code, message, headers, new_url):
        raise RuntimeError('Authenticated GitHub redirect refused; credentials were not forwarded')


class Github:
    def __init__(self):
        completed = subprocess.run(['git', 'credential', 'fill'], input='protocol=https\nhost=github.com\n\n',
            text=True, capture_output=True, env=ENV, timeout=30, creationflags=subprocess.CREATE_NO_WINDOW)
        values = dict(line.split('=', 1) for line in completed.stdout.splitlines() if '=' in line)
        self.token = values.get('password')
        require(completed.returncode == 0 and bool(self.token), 'No local GitHub credential; no credential is printed or saved')
        self.opener = urllib.request.build_opener(RejectAuthenticatedRedirects())

    def headers(self):
        return {'Authorization': 'Bearer ' + self.token, 'Accept': 'application/vnd.github+json',
            'User-Agent': 'Dreamwalker-Delta-Publication', 'X-GitHub-Api-Version': '2022-11-28'}

    def api(self, method, path, data=None, allow_missing=False):
        payload = json.dumps(data).encode() if data is not None else None
        headers = self.headers()
        if payload is not None:
            headers['Content-Type'] = 'application/json'
        try:
            request = urllib.request.Request('https://api.github.com' + path, data=payload, headers=headers, method=method)
            with self.opener.open(request, timeout=90) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if allow_missing and error.code == 404:
                return None
            raise RuntimeError('GitHub API HTTP ' + str(error.code) + ' during ' + method) from None

    def upload(self, release, path, expected):
        url = release['upload_url'].split('{', 1)[0] + '?name=' + urllib.parse.quote(expected['name'])
        parsed = urllib.parse.urlparse(url)
        require(parsed.scheme == 'https' and parsed.hostname == 'uploads.github.com' and parsed.port in (None, 443)
            and parsed.username is None and parsed.password is None and not parsed.fragment, 'Unexpected binary upload HTTPS authority')
        headers = self.headers(); headers.update({'Content-Type': 'application/octet-stream', 'Content-Length': str(expected['bytes'])})
        with path.open('rb') as stream:
            request = urllib.request.Request(url, data=stream, headers=headers, method='POST')
            try:
                with self.opener.open(request, timeout=900) as response:
                    return json.load(response)
            except urllib.error.HTTPError as error:
                raise RuntimeError('GitHub asset upload HTTP ' + str(error.code)) from None


def verified_asset(asset, expected):
    require(asset['name'] == expected['name'] and asset['size'] == expected['bytes']
        and asset.get('digest') == 'sha256:' + expected['sha256'] and asset.get('state') == 'uploaded', 'Existing/returned remote asset differs; never overwrite or delete it')
    return {'name': expected['name'], 'bytes': expected['bytes'], 'sha256': expected['sha256'],
        'id': asset['id'], 'url': asset['browser_download_url'], 'remoteDigest': asset['digest']}


def tag_commit(api, base, tag, missing_ok=False):
    reference = api.api('GET', base + '/git/ref/tags/' + urllib.parse.quote(tag), allow_missing=missing_ok)
    if reference is None:
        return None
    require(reference['ref'] == 'refs/tags/' + tag, 'Different Git tag reference returned')
    value = reference['object']; seen = set()
    for _ in range(8):
        require(re.fullmatch(r'[0-9a-f]{40}(?:[0-9a-f]{24})?', value['sha']) and value['sha'] not in seen, 'Invalid/cyclic Git tag object')
        seen.add(value['sha'])
        if value['type'] == 'commit':
            return value['sha']
        require(value['type'] == 'tag', 'Git tag does not resolve to a commit')
        value = api.api('GET', base + '/git/tags/' + value['sha'])['object']
    raise RuntimeError('Git tag nesting exceeds the finite bound')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('check', 'create', 'upload', 'publish'))
    parser.add_argument('--config', type=Path, required=True); parser.add_argument('--config-sha256', required=True)
    args = parser.parse_args(); require(sha(args.config) == args.config_sha256, 'Publication config changed')
    config = read(args.config); directory, assets, commit, manifest, expected, body = local(config)
    if args.mode == 'check':
        print(json.dumps({'status': 'LOCAL_PUBLICATION_INPUTS_VERIFIED_NO_NETWORK', 'commit': commit,
            'newAssets': len(expected), 'newBytes': sum(row['bytes'] for row in expected), 'deliveryStatus': manifest['deliveryStatus']})); return
    api = Github(); base = '/repos/' + REPOSITORY
    state_path = assets / 'publication-state.json'
    release = api.api('GET', base + '/releases/tags/' + urllib.parse.quote(config['tag']), allow_missing=True)
    if release is None and state_path.exists():
        prior = read(state_path)
        require(prior.get('config') == {'path': str(args.config.resolve()), 'sha256': args.config_sha256}
                and prior.get('tag') == config['tag'] and prior.get('branch') == config['branch']
                and type(prior.get('releaseId')) is int, 'Draft recovery requires this exact publication state')
        release = api.api('GET', base + '/releases/' + str(prior['releaseId']))
        require(release['id'] == prior['releaseId'], 'Recovered draft release ID differs')
    remote_head = git(directory, 'ls-remote', '--heads', 'origin', 'refs/heads/' + config['branch'])
    if remote_head:
        require(remote_head.split()[0] == commit, 'Remote branch already names another commit; never force push')
    actual_tag_commit = tag_commit(api, base, config['tag'], missing_ok=True)
    require(actual_tag_commit is None or actual_tag_commit == commit, 'Existing Git tag points to another commit; never move it')
    if args.mode == 'create':
        require(actual_tag_commit is None or bool(remote_head), 'A tag without this exact publication branch already owns the namespace')
        if not remote_head:
            git(directory, 'push', 'origin', 'HEAD:refs/heads/' + config['branch'])
        if actual_tag_commit is None:
            api.api('POST', base + '/git/refs', {'ref': 'refs/tags/' + config['tag'], 'sha': commit})
        require(tag_commit(api, base, config['tag']) == commit, 'New Git tag differs from the reviewed commit')
        if release is None:
            release = api.api('POST', base + '/releases', {'tag_name': config['tag'], 'target_commitish': commit,
                'name': config['title'], 'body': body, 'draft': True, 'prerelease': not manifest['readyForDelivery'], 'make_latest': 'false'})
    require(release is not None and release['tag_name'] == config['tag'] and release['target_commitish'] == commit
        and release['body'] == body and release['name'] == config['title'], 'Release identity/content differs from the reviewed request')
    require(tag_commit(api, base, config['tag']) == commit, 'Actual Git tag differs from release metadata/reviewed commit')
    require(remote_head or args.mode == 'create', 'Publication branch is absent')
    state = {'status': 'DRAFT_CREATED', 'config': {'path': str(args.config.resolve()), 'sha256': args.config_sha256},
        'branch': config['branch'], 'commit': commit, 'tag': config['tag'], 'releaseId': release['id'],
        'manifestSha256': config['manifest']['sha256'], 'deliveryStatus': manifest['deliveryStatus'], 'tagVerifiedCommit': commit, 'assets': [], 'credentialsIncluded': False}
    if state_path.exists():
        prior = read(state_path)
        require(all(prior[key] == state[key] for key in ('config', 'branch', 'commit', 'tag', 'releaseId', 'manifestSha256', 'deliveryStatus')), 'Another publication owns the state file')
    if args.mode == 'create':
        require(release['draft'] is True, 'Existing published release must use verification mode')
        save(state_path, state); print(json.dumps(state)); return
    existing = {row['name']: row for row in release['assets']}
    require(len(existing) == len(release['assets']) and set(existing) <= {row['name'] for row in expected}, 'Unexpected assets in this fresh release')
    for row in expected:
        asset = existing.get(row['name'])
        if asset is None:
            require(args.mode == 'upload' and release['draft'] is True, 'Upload all exact new assets before publication')
            print('Uploading ' + row['name'] + ' (' + str(row['bytes']) + ' bytes)', flush=True)
            asset = api.upload(release, assets / row['name'], row)
        state['assets'].append(verified_asset(asset, row)); state['status'] = 'UPLOADING_VERIFIED_NEW_ASSETS'
        save(state_path, state)
    release = api.api('GET', base + '/releases/' + str(release['id']))
    require(len(release['assets']) == len(expected), 'Remote asset count differs')
    existing = {row['name']: row for row in release['assets']}
    state['assets'] = [verified_asset(existing[row['name']], row) for row in expected]
    state['status'] = 'ALL_NEW_REMOTE_ASSET_DIGESTS_VERIFIED'
    if args.mode == 'publish':
        require(tag_commit(api, base, config['tag']) == commit, 'Actual Git tag changed before publication')
        release = api.api('PATCH', base + '/releases/' + str(release['id']), {'draft': False,
            'prerelease': not manifest['readyForDelivery'], 'make_latest': 'false'})
        require(release['draft'] is False and release['prerelease'] is (not manifest['readyForDelivery']), 'Release publication status differs')
        require(tag_commit(api, base, config['tag']) == commit, 'Actual Git tag changed during publication')
        public_prefix = 'https://github.com/' + REPOSITORY + '/releases/download/' + config['tag'] + '/'
        canonical = {row['name']: row for row in release['assets']}
        state['assets'] = [verified_asset(canonical[row['name']], row) for row in expected]
        require(all(row['url'] == public_prefix + urllib.parse.quote(row['name']) for row in state['assets']), 'Public download URL changed')
        state.update(status='PUBLISHED_VERIFIED_FULL_DELIVERY' if manifest['readyForDelivery'] else 'PUBLISHED_VERIFIED_CHECKPOINT',
            releaseUrl='https://github.com/' + REPOSITORY + '/releases/tag/' + config['tag'],
            readmeUrl='https://github.com/' + REPOSITORY + '/blob/' + config['branch'] + '/README.md', publishedAt=release['published_at'])
    save(state_path, state); print(json.dumps(state))


if __name__ == '__main__':
    main()
