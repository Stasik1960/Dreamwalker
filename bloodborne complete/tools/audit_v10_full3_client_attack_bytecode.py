"""Read only the actual FULL4ATT3 mod classes; never start Minecraft or compile code."""
from pathlib import Path
import argparse
import hashlib
import io
import json
import subprocess
import zipfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--run', type=Path, required=True)
    ap.add_argument('--javap', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    args = ap.parse_args()
    if args.output.exists():
        raise SystemExit('Refusing to overwrite existing evidence directory')
    args.output.mkdir(parents=True)
    specs = [
        ('immersive-portals-5.2.0-mc1.20.1-fabric.jar', 'META-INF/jars/imm_ptl_core-5.2.0.jar', [
            'qouteall/imm_ptl/core/mixin/client/interaction/MixinMinecraft_B.class',
            'qouteall/imm_ptl/core/mixin/client/interaction/MixinMultiPlayerGameMode.class',
            'qouteall/imm_ptl/core/block_manipulation/BlockManipulationClient.class',
            'qouteall/imm_ptl/core/mixin/client/MixinClientConnection.class']),
        ('bettercombat-fabric-1.8.6+1.20.1.jar', None, [
            'net/bettercombat/mixin/client/ClientPlayerInteractionManagerMixin.class',
            'net/bettercombat/mixin/client/MinecraftClientInject.class']),
        ('Pehkui-3.8.3+1.14.4-1.21.jar', None, [
            'virtuoel/pehkui/mixin/reach/client/compat1204minus/ClientPlayerInteractionManagerMixin.class']),
    ]
    processed = args.run / '.fabric/processedMods'
    reach = list(processed.glob('reach-entity-attributes-2.4.0-*.jar'))
    if len(reach) != 1:
        raise RuntimeError('Expected exactly one actual processed reach-entity-attributes jar')
    specs.append((reach[0], None, [
        'com/jamieswhiteshirt/reachentityattributes/mixin/client/ClientPlayerInteractionManagerMixin.class']))
    rows = []
    for name, nested, entries in specs:
        jar = name if isinstance(name, Path) else args.run / 'mods' / name
        outer = jar.read_bytes()
        with zipfile.ZipFile(io.BytesIO(outer)) as z:
            payload = z.read(nested) if nested else outer
        with zipfile.ZipFile(io.BytesIO(payload)) as z:
            for entry in entries:
                if entry not in z.namelist():
                    # Keep the inventory witness if a guessed client-class spelling is absent.
                    rows.append({'jar': str(jar), 'entry': entry, 'status': 'ABSENT',
                                 'relevantClassInventory': [x for x in z.namelist() if
                                 x.endswith('.class') and ('client' in x.lower() or 'interaction' in x.lower())]})
                    continue
                data = z.read(entry)
                target = args.output / entry
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
                proc = subprocess.run([str(args.javap), '-J-Dfile.encoding=UTF-8', '-J-Duser.language=en', '-p', '-c', '-v', str(target)],
                                      capture_output=True, check=True)
                text = proc.stdout.decode('utf-8', errors='strict')
                out = target.with_suffix('.javap.txt')
                out.write_text(text, encoding='utf-8')
                rows.append({'jar': str(jar), 'jarSha256': sha(outer), 'nestedEntry': nested,
                             'nestedSha256': sha(payload) if nested else None, 'entry': entry,
                             'classSha256': sha(data), 'javapPath': str(out),
                             'javapSha256': sha(out.read_bytes()), 'status': 'DISASSEMBLED'})
    # Use the exact actual remapped runtime classes, rather than a recalled vanilla source.
    mc = args.run / '.fabric/remappedJars/minecraft-1.20.1-0.19.5/client-intermediary.jar'
    with zipfile.ZipFile(mc) as z:
        for entry in ['net/minecraft/class_310.class', 'net/minecraft/class_636.class']:
            data = z.read(entry)
            target = args.output / entry
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            proc = subprocess.run([str(args.javap), '-J-Dfile.encoding=UTF-8', '-J-Duser.language=en', '-p', '-c', '-v', str(target)],
                                  capture_output=True, check=True)
            out = target.with_suffix('.javap.txt')
            out.write_text(proc.stdout.decode('utf-8'), encoding='utf-8')
            rows.append({'jar': str(mc), 'scope': 'actual runtime vanilla base class before mixins',
                         'entry': entry, 'classSha256': sha(data), 'javapPath': str(out),
                         'javapSha256': sha(out.read_bytes()), 'status': 'DISASSEMBLED'})
    report = {'schema': 'dw-v10-full3-client-attack-primary-bytecode-v1',
              'scope': 'Static routing/conditions. Packet delivery and live injected handler execution are not observed here.',
              'runDirectory': str(args.run), 'javapExecutable': str(args.javap), 'classes': rows,
              'productionWrites': False, 'minecraftStarted': False, 'compiled': False}
    (args.output / 'manifest.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': 'PRIMARY_CLASS_BYTES_AND_JAVAP_SAVED', 'classes': len(rows), 'output': str(args.output)}))


if __name__ == '__main__':
    main()
