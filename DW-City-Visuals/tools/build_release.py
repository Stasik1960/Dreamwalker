"""Package the built overlay with the pinned baseline and explicit resource replacements."""
from pathlib import Path
import argparse
import json
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--overlay', type=Path, default=ROOT / 'build/libs/dw-altvisual-overlay-20261104-cityeditor.2.1-cornerlight.2-rgb.2-altfix.1-cityfollowup.1-rgbrelief.1.jar')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    base = ROOT / 'baseline/mod.jar'
    with zipfile.ZipFile(base) as original, zipfile.ZipFile(args.overlay) as overlay:
        names = set(original.namelist())
        changed = [name for name in overlay.namelist() if name in names
                   and name not in ('fabric.mod.json', 'META-INF/MANIFEST.MF')
                   and overlay.read(name) != original.read(name)]
    receipt = args.output.with_suffix('.receipt.json')
    command = [sys.executable, str(ROOT / 'tools/package_overlay.py'), '--base', str(base),
               '--overlay', str(args.overlay), '--output', str(args.output), '--receipt', str(receipt)]
    for name in changed:
        command.extend(('--replace-entry', name))
    result = subprocess.run(command, text=True, capture_output=True)
    args.output.with_suffix('.build.log').write_text(result.stdout + result.stderr)
    if result.returncode:
        raise SystemExit(result.returncode)
    data = json.loads(receipt.read_text())
    print('Combined mod:', args.output)
    print('CRC:', data.get('crcCheck'), 'receipt:', receipt)


if __name__ == '__main__':
    main()
