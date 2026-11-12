"""Run the same city camera route on either release using a prepared Fabric client."""

import argparse
import json
import shutil
import subprocess
from pathlib import Path
from zipfile import ZipFile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('profile', type=Path, help='Existing 1.20.1 Fabric client-profile.json')
    parser.add_argument('template', type=Path, help='Prepared QA instance with dependencies, config and options')
    parser.add_argument('jar', type=Path)
    parser.add_argument('world', type=Path)
    parser.add_argument('run', type=Path)
    args = parser.parse_args()

    run = args.run.resolve()
    run.mkdir(parents=True)
    mods = run / 'mods'
    mods.mkdir()
    for dependency in sorted((args.template / 'mods').glob('*.jar')):
        if not dependency.name.lower().startswith('dreamwalker-'):
            shutil.copy2(dependency, mods)
    shutil.copy2(args.jar, mods)
    for folder in ('resourcepacks', 'config'):
        if (args.template / folder).is_dir():
            shutil.copytree(args.template / folder, run / folder)
    shutil.copy2(args.template / 'options.txt', run)

    with ZipFile(args.world) as archive:
        for path in archive.namelist():
            target = (run / 'saves' / path).resolve()
            if not target.is_relative_to(run / 'saves' / 'bloodborne'):
                raise ValueError(f'Unexpected archive member: {path}')
        archive.extractall(run / 'saves')

    command = json.loads(args.profile.read_text(encoding='utf-8'))['command']
    command = [arg for arg in command if not arg.startswith(('-Xmx', '-Xms', '-XX:ActiveProcessorCount'))]
    if '--quickPlaySingleplayer' in command:
        offset = command.index('--quickPlaySingleplayer')
        del command[offset:offset + 2]
    command[1:1] = ['-Xms512m', '-Xmx8G', '-XX:ActiveProcessorCount=6', '-XX:+ExitOnOutOfMemoryError',
                    '-Ddreamwalker.qa.case=base']
    for flag, value in {'--gameDir': str(run), '--accessToken': '0', '--username': 'DWEditingQA',
                        '--uuid': 'a6e55db3f39945b9950d69034c36b7a2'}.items():
        command[command.index(flag) + 1] = value

    with (run / 'console.log').open('wb') as log:
        process = subprocess.Popen(command, cwd=run, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    (run / 'pid').write_text(f'{process.pid}\n', encoding='ascii')
    print(f'Client PID {process.pid}, log {run / "console.log"}')


if __name__ == '__main__':
    main()
