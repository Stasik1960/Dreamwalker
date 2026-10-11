"""Capture Python stage exit codes/logs when CodexControl Guard hides stdio."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
STAGES = {'client': 'collect_v123_client_evidence.py',
          'acceptance': 'finalize_v123_acceptance.py',
          'package': 'package_v123_release.py'}

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('stage', choices=STAGES)
args = parser.parse_args()
target = ROOT / ('build/v123-release-stage-' + args.stage)
target.parent.mkdir(parents=True, exist_ok=True)
environment = dict(os.environ, PYTHONUTF8='1')
with target.with_suffix('.log').open('wb') as output:
    process = subprocess.run([sys.executable, str(ROOT / 'tools' / STAGES[args.stage])],
                             cwd=ROOT, env=environment, stdout=output, stderr=subprocess.STDOUT)
target.with_suffix('.json').write_text(json.dumps({'stage': args.stage,
    'status': 'PASS' if process.returncode == 0 else 'FAIL', 'exitCode': process.returncode}) + '\n', encoding='utf8')
sys.exit(process.returncode)
