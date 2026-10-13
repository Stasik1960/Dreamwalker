"""Bounded read-only live JVM diagnostic for root-run isolated validation."""
from pathlib import Path
import argparse, subprocess

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('--pid', type=int, required=True)
p.add_argument('--output', type=Path, required=True)
p.add_argument('--histogram', action='store_true')
a = p.parse_args()
output = a.output.resolve()
assert output.is_relative_to(ROOT / 'build') and not output.exists()
java = Path.home() / 'AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/jcmd.exe'
command = [str(java), str(a.pid), 'GC.class_histogram'] if a.histogram else [str(java), str(a.pid), 'Thread.print', '-l']
result = subprocess.run(command, capture_output=True, text=True, timeout=50)
output.write_text(result.stdout + '\n' + result.stderr, encoding='utf-8')
raise SystemExit(result.returncode)
