"""Supplement the one completed route rejected only by a case-sensitive log guard."""
import hashlib,json,re
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024';R=T/'runs/addon-iris-off-2'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
original=read(R/'receipt.json');assert original['status']=='FAIL' and original['error']==''
error=(R/'runner-error.txt').read_text();assert "if addon_files:assert 'Sodium' in console and 'Iris' in console and 'Indium' in console" in error
code=(R/'runner-source.py').read_text();guard=code.index("if addon_files:assert 'Sodium' in console")
assert code.index("assert client.returncode==0")<guard and code.index("assert server.returncode==0 and c['status']=='PASS_NATIVE_GRAPHICAL_ROUTE' and s['status']=='PASS_SAVED'")<guard
c=read(R/'client/client-route.json');s=read(R/'server/server-route.json');assert c['status']=='PASS_NATIVE_GRAPHICAL_ROUTE' and s['status']=='PASS_SAVED' and len(c['phases'])==14
console=(R/'client-console.log').read_text(encoding='utf-8',errors='replace');assert all(re.search(r'^\s*- '+kind+r'\s',console,re.MULTILINE) for kind in ('sodium','iris','indium'))
for phase in c['phases']:assert (T/'profiles/addon-iris-off-2/client/screenshots'/('route-'+phase['phase']+'.png')).is_file()
result={**original,'status':'PASS','clientExitCode':0,'serverExitCode':0,'acceptanceQualifier':'COMPLETED_GAME_ROUTE_AND_NORMAL_ZERO_EXITS_VERIFIED_AFTER_CORRECTED_LOG_ID_GUARD','originalReceiptSha256':sha(R/'receipt.json'),'originalRunnerSourceSha256':sha(R/'runner-source.py'),'originalErrorSha256':sha(R/'runner-error.txt'),'reason':'Original runner reached only the final uppercase-name guard after its explicit client/server exit0 and recorder PASS assertions. Fabric reports lowercase mod IDs; all three required IDs are present. Original FAIL retained.'}
(R/'receipt-verified.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
