"""Open a disposable release copy in a prepared Fabric dedicated server."""

import argparse
import queue
import shutil
import subprocess
import threading
import time
from pathlib import Path
from zipfile import ZipFile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('template', type=Path, help='Existing Fabric 1.20.1 server with dependencies')
    parser.add_argument('jar', type=Path)
    parser.add_argument('world', type=Path)
    parser.add_argument('run', type=Path)
    parser.add_argument('--java', default='java')
    parser.add_argument('--check', nargs=5, action='append', metavar=('DIM', 'X', 'Y', 'Z', 'STATE'), default=[])
    parser.add_argument('--cleanup', nargs=7, metavar=('DIM', 'X', 'Y', 'Z', 'HELPER_X', 'HELPER_Y', 'HELPER_Z'),
                        help='Remove a verified static root and check that its helper is retired in the test copy')
    args = parser.parse_args()

    run = args.run.resolve()
    run.mkdir(parents=True)
    for name in ('fabric-server-launch.jar', 'eula.txt'):
        shutil.copy2(args.template / name, run / name)
    for name in ('libraries', 'versions', 'config'):
        shutil.copytree(args.template / name, run / name)
    mods = run / 'mods'
    mods.mkdir()
    for dependency in sorted((args.template / 'mods').glob('*.jar')):
        if not dependency.name.lower().startswith(('dreamwalker-', 'bb-editing-smoke-helper-')):
            shutil.copy2(dependency, mods)
    shutil.copy2(args.jar, mods)
    (run / 'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=25641\nlevel-name=bloodborne\nview-distance=3\n'
        'simulation-distance=3\nspawn-protection=0\n', encoding='ascii')
    with ZipFile(args.world) as archive:
        for path in archive.namelist():
            if not (run / path).resolve().is_relative_to(run / 'bloodborne'):
                raise ValueError(f'Unexpected archive member: {path}')
        archive.extractall(run)

    process = subprocess.Popen([args.java, '-Xms1G', '-Xmx6G', '-XX:ActiveProcessorCount=6',
                                '-jar', 'fabric-server-launch.jar', 'nogui'],
                               cwd=run, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, encoding='utf-8', errors='replace',
                               creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    messages = queue.Queue()

    def read():
        with (run / 'server-qa.log').open('w', encoding='utf-8') as log:
            for line in process.stdout:
                log.write(line)
                log.flush()
                messages.put(line)
        messages.put(None)

    reader = threading.Thread(target=read, daemon=True)
    reader.start()

    def expect(fragment, timeout):
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            line = messages.get(timeout=max(0.1, end - time.monotonic()))
            if line is None or 'Encountered an unexpected exception' in line:
                break
            if fragment in line:
                print(line.rstrip(), flush=True)
                return
        raise RuntimeError(f'Expected {fragment!r}; inspect {run / "server-qa.log"}')

    def command(line):
        process.stdin.write(line + '\n')
        process.stdin.flush()

    def check_block(dimension, position, state, marker):
        for attempt in range(15):
            command(f'execute in {dimension} if block {position} {state} run say {marker}')
            try:
                expect(marker, 2)
                return
            except queue.Empty:
                if attempt == 14:
                    raise RuntimeError(f'Expected {state} at {position} in {dimension}') from None

    try:
        expect('Done (', 180)
        for i, (dimension, x, y, z, state) in enumerate(args.check):
            command(f'execute in {dimension} run forceload add {x} {z}')
            expect('to be force loaded', 30)
            check_block(dimension, f'{x} {y} {z}', state, f'DW_CITY_PERF_STATIC_BLOCK_{i}')
            command(f'execute in {dimension} run forceload remove {x} {z}')
        if args.cleanup:
            dimension, x, y, z, hx, hy, hz = args.cleanup
            if (int(x) >> 4, int(z) >> 4) != (int(hx) >> 4, int(hz) >> 4):
                parser.error('--cleanup currently requires the root and helper in the same chunk')
            command(f'execute in {dimension} run forceload add {x} {z}')
            expect('to be force loaded', 30)
            command(f'execute in {dimension} run setblock {x} {y} {z} minecraft:air')
            expect('Changed the block', 30)
            check_block(dimension, f'{hx} {hy} {hz}', 'minecraft:air', 'DW_CITY_PERF_STATIC_HELPER_REMOVED')
            command(f'execute in {dimension} run forceload remove {x} {z}')
        command('save-all flush')
        expect('Saved the game', 120)
        command('stop')
        expect('All dimensions are saved', 120)
        if process.wait(timeout=60) != 0:
            raise RuntimeError(f'Server exit code: {process.returncode}')
    finally:
        if process.poll() is None:
            command('stop')
            try:
                process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                process.terminate()
        reader.join(timeout=5)


if __name__ == '__main__':
    main()
