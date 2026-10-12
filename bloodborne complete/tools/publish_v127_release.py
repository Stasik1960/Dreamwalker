"""Root-run idempotent publication of a validated, accepted V12.7 package.

Dry-run is the default. Git credentials remain only in memory, are never printed
or stored, and uploads/public download verification use bounded streaming IO.
API references: https://docs.github.com/en/rest/releases/releases and /assets.
"""
import argparse
import hashlib
import http.client
import json
import mimetypes
import os
from pathlib import Path
import subprocess
import urllib.parse
import urllib.request
from package_v127_release import ROOT, REPO, DEST, sha, read, CHUNK

REPOSITORY = 'Stasik1960/Dreamwalker'
BRANCH = 'codex/bb-v12-tool'
TAG = 'bb-v12.7-final'


def git(*args):
    return subprocess.check_output(['git', *args], cwd=REPO, text=True).strip()


def credentials():
    environment = dict(os.environ, GIT_TERMINAL_PROMPT='0', GCM_INTERACTIVE='Never')
    result = subprocess.run(['git', 'credential', 'fill'], input='protocol=https\nhost=github.com\n\n',
                            text=True, capture_output=True, cwd=REPO, env=environment, timeout=45)
    values = dict(line.split('=', 1) for line in result.stdout.splitlines() if '=' in line)
    assert result.returncode == 0 and values.get('password'), 'GitHub publication credential is unavailable'
    return values['password']


class API:
    def __init__(self, token):
        self.token = token

    def request(self, method, path, body=None, allow404=False):
        assert path.startswith('/repos/' + REPOSITORY + '/')
        encoded = json.dumps(body).encode('utf8') if body is not None else None
        headers = {'Authorization': 'Bearer ' + self.token, 'Accept': 'application/vnd.github+json',
                   'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'Dreamwalker-accepted-release'}
        if encoded is not None:
            headers['Content-Type'] = 'application/json'
        connection = http.client.HTTPSConnection('api.github.com', timeout=60)
        try:
            connection.request(method, path, body=encoded, headers=headers)
            response = connection.getresponse()
            content = response.read(4 * 1024 * 1024)
            if response.status == 404 and allow404:
                return None
            assert 200 <= response.status < 300, f'GitHub {method} returned HTTP {response.status}; no credential data is logged'
            return json.loads(content) if content else None
        finally:
            connection.close()

    def upload(self, url, path):
        address = urllib.parse.urlparse(url)
        assert address.scheme == 'https' and address.hostname == 'uploads.github.com'
        query = urllib.parse.urlencode({'name': path.name})
        connection = http.client.HTTPSConnection(address.hostname, timeout=180)
        try:
            connection.putrequest('POST', address.path + '?' + query)
            connection.putheader('Authorization', 'Bearer ' + self.token)
            connection.putheader('Accept', 'application/vnd.github+json')
            connection.putheader('User-Agent', 'Dreamwalker-accepted-release')
            connection.putheader('X-GitHub-Api-Version', '2022-11-28')
            connection.putheader('Content-Type', mimetypes.guess_type(path.name)[0] or 'application/octet-stream')
            connection.putheader('Content-Length', str(path.stat().st_size))
            connection.endheaders()
            with path.open('rb') as stream:
                for data in iter(lambda: stream.read(CHUNK), b''):
                    connection.send(data)
            response = connection.getresponse()
            result = response.read(4 * 1024 * 1024)
            assert response.status == 201, f'Asset upload returned HTTP {response.status}; retry recovers any completed upload by name'
            return json.loads(result)
        finally:
            connection.close()


def public_sha(url):
    address = urllib.parse.urlparse(url)
    assert address.scheme == 'https' and address.hostname == 'github.com'
    # No Authorization header is sent to the public URL or redirect destinations.
    digest, size = hashlib.sha256(), 0
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'Dreamwalker-public-release-verification'}), timeout=180) as response:
        for data in iter(lambda: response.read(CHUNK), b''):
            digest.update(data)
            size += len(data)
    return digest.hexdigest(), size


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', type=Path, default=DEST)
    parser.add_argument('--tag', default=TAG)
    parser.add_argument('--publish', action='store_true')
    args = parser.parse_args()
    package = args.package.resolve()
    assert package.is_relative_to(REPO / 'releases/Dreamwalker-BB')
    manifest = read(package / 'release-manifest.json')
    assert manifest['schema'] == 'dreamwalker-v127-release' and manifest['status'] == 'PASS'
    assert manifest['galleryCreated'] is False and manifest['originalMapChanged'] is False
    for row in manifest['files']:
        path = package / row['name']
        assert path.parent == package and path.stat().st_size == row['bytes'] and sha(path) == row['sha256'], row['name']
    accepted = read(package / 'acceptance.json')
    assert accepted['status'] == 'PASS' and accepted['productionJarSha256'] == manifest['productionJarSha256']
    assert git('branch', '--show-current') == BRANCH
    assert git('remote', 'get-url', 'origin') in {
        'https://github.com/Stasik1960/Dreamwalker.git', 'git@github.com:Stasik1960/Dreamwalker.git'}
    source = manifest['sourceArchiveContentCommit']
    target = git('rev-parse', 'HEAD')
    assert subprocess.run(['git', 'merge-base', '--is-ancestor', source, target], cwd=REPO).returncode == 0
    protected = ['bloodborne complete/src', 'bloodborne complete/tools', 'bloodborne complete/build.gradle',
                 'bloodborne complete/gradle.properties', 'bloodborne complete/settings.gradle', 'bloodborne complete/gradle']
    assert subprocess.run(['git', 'diff', '--quiet', source, target, '--', *protected], cwd=REPO).returncode == 0, 'Code changed after accepted packaging'
    assert not git('status', '--porcelain', '--untracked-files=no'), 'Commit reviewed tracked changes before publication'
    assert args.tag.startswith('bb-v12.7') and not any(c in args.tag for c in ' \r\n~^:?*[\\')
    assets = [path for path in sorted(package.iterdir()) if path.is_file()]
    preview = {'status': 'PASS_PUBLICATION_PREFLIGHT', 'repository': REPOSITORY, 'branch': BRANCH,
               'targetCommit': target, 'tag': args.tag, 'productionJarSha256': manifest['productionJarSha256'],
               'assets': [{'name': path.name, 'bytes': path.stat().st_size, 'sha256': sha(path)} for path in assets],
               'publicationAttempted': False}
    if not args.publish:
        print(json.dumps(preview, ensure_ascii=False, indent=2))
        return
    # Publication was explicitly authorized by the owner; no force push/ref replacement.
    existing_tag = subprocess.run(['git', 'rev-parse', '--verify', 'refs/tags/' + args.tag + '^{}'], cwd=REPO, text=True, capture_output=True)
    if existing_tag.returncode == 0:
        assert existing_tag.stdout.strip() == target, 'An existing immutable tag points elsewhere'
    else:
        subprocess.run(['git', 'tag', '-a', args.tag, target, '-m', 'Dreamwalker BB V12.7 accepted final revision'], cwd=REPO, check=True)
    subprocess.run(['git', 'push', 'origin', 'HEAD:refs/heads/' + BRANCH, 'refs/tags/' + args.tag], cwd=REPO, check=True)
    remote = git('ls-remote', 'origin', 'refs/heads/' + BRANCH).split()[0]
    assert remote == target, 'GitHub branch verification differs from accepted commit'
    api = API(credentials())
    prefix = '/repos/' + REPOSITORY
    release = api.request('GET', prefix + '/releases/tags/' + urllib.parse.quote(args.tag, safe=''), allow404=True)
    owner_marker = 'Production SHA-256: `' + manifest['productionJarSha256'] + '`'
    if release is None:
        body = '# Dreamwalker BB V12.7\n\n' + owner_marker + '\n\n' + (package / 'REPORT-RU.txt').read_text(encoding='utf-8-sig')
        try:
            release = api.request('POST', prefix + '/releases', {'tag_name': args.tag, 'target_commitish': target,
                                  'name': 'Dreamwalker BB V12.7 — final', 'body': body, 'draft': True, 'prerelease': False})
        except Exception:
            release = api.request('GET', prefix + '/releases/tags/' + urllib.parse.quote(args.tag, safe=''), allow404=True)
            if release is None:
                raise
    assert owner_marker in release.get('body', ''), 'An unrelated release already occupies this tag'
    verified = []
    for path in assets:
        current = api.request('GET', prefix + f"/releases/{release['id']}/assets?per_page=100")
        matching = [asset for asset in current if asset['name'] == path.name]
        assert len(matching) <= 1, 'Repeated remote asset name'
        expected = sha(path)
        if matching:
            asset = matching[0]
        else:
            print(json.dumps({'status': 'UPLOADING', 'asset': path.name, 'bytes': path.stat().st_size}), flush=True)
            try:
                asset = api.upload(release['upload_url'].split('{', 1)[0], path)
            except Exception:
                current = api.request('GET', prefix + f"/releases/{release['id']}/assets?per_page=100")
                recovered = [a for a in current if a['name'] == path.name and a.get('state') == 'uploaded']
                if len(recovered) != 1:
                    raise
                asset = recovered[0]
        assert asset.get('state') == 'uploaded' and asset['size'] == path.stat().st_size, 'Incomplete/conflicting upload retained for review'
        assert asset.get('digest') == 'sha256:' + expected, 'Remote asset digest differs; existing data is never overwritten'
        verified.append({'name': path.name, 'bytes': path.stat().st_size, 'sha256': expected, 'url': asset['browser_download_url']})
    if release['draft']:
        release = api.request('PATCH', prefix + f"/releases/{release['id']}", {'draft': False})
    for row in verified:
        digest, size = public_sha(row['url'])
        assert digest == row['sha256'] and size == row['bytes'], 'Public asset download checksum mismatch'
        row['publicDownloadSHA256'] = 'PASS'
        print(json.dumps({'status': 'PASS_PUBLIC_DOWNLOAD', 'asset': row['name']}), flush=True)
    record = dict(preview, status='PASS_GITHUB_PUBLISHED_AND_PUBLIC_DOWNLOAD_VERIFIED', publicationAttempted=True,
                  releaseUrl=release['html_url'], assets=verified)
    out = ROOT / 'build/v127-release-publication.json'
    out.write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({'status': record['status'], 'releaseUrl': record['releaseUrl'], 'targetCommit': target}))


if __name__ == '__main__':
    main()
