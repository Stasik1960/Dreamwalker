"""Import artwork locally from a verified user-owned reference JAR; never copy its Java code."""
import argparse
import hashlib
import json
import pathlib
import re
import zipfile

REFERENCE_SHA256 = '399541635dfc559580193ca38da074f7158e064647d612fdb6d92236565266d2'
SOUND_ALIASES = {
 'entity/huntsman/huntsman_b/where_are_you_hiding1':'entity/huntsman/huntsman_b/where_are_you_hidin1',
 'entity/huntsman/huntsman_a/where_you_hiding2':'entity/huntsman/huntsman_a/where_you_hidin2',
 'entity/huntsman/huntsman_a/where_you_hiding3':'entity/huntsman/huntsman_a/where_you_hidin3',
}

def rewrite(value):
    if isinstance(value, str):
        value=value.replace('bloodborne:', 'bloodborne_rp:').replace('subtitles.bloodborne.', 'subtitles.bloodborne_rp.')
        if value.startswith('bloodborne_rp:'):
            path=value.split(':',1)[1]
            value='bloodborne_rp:'+SOUND_ALIASES.get(path,path)
        # Repair incomplete numeric/Molang expressions present in the supplied animations.
        if value in ('-', '+'): return '0'
        if re.fullmatch(r'--[0-9.]+',value): return value[2:]
        return re.sub(r'[+]\s*(?=\)|$)', '', value)
    if isinstance(value, list):
        return [rewrite(v) for v in value]
    if isinstance(value, dict):
        return {rewrite(k): rewrite(v) for k, v in value.items()}
    return value

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=pathlib.Path)
    args = parser.parse_args()
    digest = hashlib.sha256(args.jar.read_bytes()).hexdigest()
    if digest != REFERENCE_SHA256:
        raise SystemExit('Unexpected reference JAR SHA-256; no files imported')
    root = pathlib.Path(__file__).resolve().parents[1]
    target = root / 'private-assets' / 'assets' / 'bloodborne_rp'
    used_textures={v['texture'] for v in json.loads((root/'src/main/resources/assets/bloodborne_rp/catalog.json').read_text(encoding='utf-8')).values()}
    # Discard previous imported files individually, within the fixed gitignored directory.
    if target.exists():
        for old in target.rglob('*'):
            if old.is_file(): old.unlink()
    copied = []
    with zipfile.ZipFile(args.jar) as archive:
        for entry in archive.infolist():
            if entry.is_dir() or not entry.filename.startswith('assets/bloodborne/'):
                continue
            relative = pathlib.PurePosixPath(entry.filename.removeprefix('assets/bloodborne/'))
            if relative.is_absolute() or '..' in relative.parts or any(':' in p for p in relative.parts):
                raise SystemExit('Unsafe archive path')
            if not (relative.parts[0] in ('geo', 'animations', 'textures', 'sounds') or str(relative) == 'sounds.json'):
                continue
            if relative.parts[0]=='textures' and relative.as_posix() not in used_textures:
                continue
            if relative.parts[0]=='sounds':
                relative=pathlib.PurePosixPath(relative.as_posix().lower().replace(' ','_'))
            data = archive.read(entry)  # ZIP CRC verified by zipfile
            if relative.suffix == '.json':
                data = (json.dumps(rewrite(json.loads(data)), ensure_ascii=False, indent=2) + '\n').encode('utf-8')
            dest = target.joinpath(*relative.parts)
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(data)
            copied.append({'path': relative.as_posix(), 'sha256': hashlib.sha256(data).hexdigest()})
    manifest = {'reference_sha256': digest, 'files': copied, 'java_code_copied': False}
    (root/'private-assets'/'import-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'imported':len(copied),'directory':str(target),'java_code_copied':False}))

if __name__ == '__main__':
    main()
