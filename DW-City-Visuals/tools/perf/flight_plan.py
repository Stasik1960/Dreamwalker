"""Copy the audited immutable two-lap route used by the final benchmark pair."""
import argparse, hashlib
from pathlib import Path

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('output',type=Path)
a=p.parse_args()
plan_path=Path(__file__).resolve().parents[2]/'validation/city-editor/flight-plan-v2.json'
payload=plan_path.read_bytes()
if hashlib.sha256(payload).hexdigest()!='a479d1c7f08049e92503a3c02cecbeb3177578ebaed66667244540dc74fcd7c4':
    raise ValueError('Audited flight plan changed; preserve the tested route')
a.output.parent.mkdir(parents=True,exist_ok=True)
with a.output.open('xb') as f:f.write(payload)

