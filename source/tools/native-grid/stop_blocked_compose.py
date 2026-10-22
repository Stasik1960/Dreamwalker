"""Invoke the exact owned-child stop with captured diagnostics under Guard."""
import hashlib,json,subprocess,sys
from pathlib import Path
script=Path(__file__).with_suffix('.ps1')
if sys.argv[1:]!=['--archive-only']:
    result=subprocess.run([r'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(script)],capture_output=True,text=True)
    print(result.stdout);print(result.stderr)
    if result.returncode:raise SystemExit(result.returncode)
directory=script.parents[3]/'research/native-grid-plan/context-extension'
receipt_path=directory/'blocked-compose-stop.json'
receipt=json.loads(receipt_path.read_text(encoding='utf-8-sig'))
assert receipt['stopped'] and receipt['status']=='FAIL_PROVEN_COMPLETENESS_GUARD'
archive=directory/'blocked-compose';archive.mkdir(exist_ok=True)
proof=Path(receipt['proof']);payload=proof.read_bytes()
assert hashlib.sha256(payload).hexdigest()==receipt['proofSha256']
for source in (proof,directory.parent/'functional-guest-preflight.json'):
    content=source.read_bytes();target=archive/source.name
    if target.exists():assert target.read_bytes()==content
    else:target.write_bytes(content)
receipt['proof']=str(archive/proof.name)
receipt['functionalPreflightArchive']=str(archive/'functional-guest-preflight.json')
receipt['functionalPreflightSha256']=hashlib.sha256((archive/'functional-guest-preflight.json').read_bytes()).hexdigest()
receipt_path.write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'status':'FAILED_AUTHORING_EVIDENCE_ARCHIVED','receipt':str(receipt_path),'mapWrites':0}))
