"""Compile the client mixins and apply them to the pinned v22 Editing Fix JAR."""

import argparse
import hashlib
import json
from pathlib import Path
import os
import subprocess
from tempfile import TemporaryDirectory
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo


HERE = Path(__file__).resolve().parent
BASE_SHA256 = '14a71110c3bd897d04fffd96e50075ed16fa5d572523d9fbaa65d8637ef55889'
CONFIG = 'dreamwalker-city-performance.mixins.json'


def sha256(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('base', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--javac', default='javac')
    parser.add_argument('--dependency', type=Path, action='append', required=True,
                        help='Pass the MC 1.20.1 intermediary JAR, Fabric Loader, Sponge Mixin, MixinExtras and JOML')
    args = parser.parse_args()
    if sha256(args.base) != BASE_SHA256:
        parser.error('input does not match the v22 Bush Restoration Editing Fix release')
    if args.output.exists():
        parser.error('output already exists')

    with TemporaryDirectory(prefix='dreamwalker-city-performance-') as directory:
        classes = Path(directory)
        subprocess.run([args.javac, '-proc:none', '--release', '17', '-encoding', 'UTF-8',
                        '-classpath', os.pathsep.join(map(str, [args.base, *args.dependency])),
                        '-d', directory, *map(str, sorted((HERE / 'src').rglob('*.java')))], check=True)
        additions = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
        additions[CONFIG] = (HERE / CONFIG).read_bytes()
    with ZipFile(args.base) as source:
        manifest = json.loads(source.read('fabric.mod.json'))
        manifest['version'] += '-city-performance.1'
        manifest['mixins'].append(CONFIG)
        if set(source.namelist()) & additions.keys():
            raise ValueError('performance overlay unexpectedly replaces a release entry')
        with ZipFile(args.output, 'x', ZIP_DEFLATED, compresslevel=6) as output:
            for entry in source.infolist():
                data = (json.dumps(manifest, indent=2).encode() + b'\n' if entry.filename == 'fabric.mod.json'
                        else source.read(entry))
                output.writestr(entry, data)
            for name, data in sorted(additions.items()):
                info = ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.compress_type = ZIP_DEFLATED
                output.writestr(info, data)
    with ZipFile(args.base) as base, ZipFile(args.output) as output:
        original = set(base.namelist())
        if set(output.namelist()) != original | additions.keys():
            raise ValueError('output entry list differs from the base and the overlay')
        for name in original - {'fabric.mod.json'}:
            if base.read(name) != output.read(name):
                raise ValueError(f'unexpected change to release entry: {name}')
    print(f'{sha256(args.output)}  {args.output}')


if __name__ == '__main__':
    main()
