"""Bind the actual current native XML to owned mechanism/ladder/source-pair/roof cases.

This is development GameTest evidence, never a delivered-JAR ordinary-run claim.
"""
import argparse,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CLASSES={
 'MechanismLinksGameTests':'src/gametest/java/dev/dreamwalker/bloodbornedw/link/MechanismLinksGameTests.java',
 'PrototypeLadderGameTests':'src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/PrototypeLadderGameTests.java',
 'SourceLadderGameTests':'src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/SourceLadderGameTests.java',
 'PrototypeRoofGameTests':'src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/PrototypeRoofGameTests.java',
 'DwDiagnosticsGameTests':'src/gametest/java/dev/dreamwalker/bloodbornedw/diagnostics/DwDiagnosticsGameTests.java'}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def ref(p):return {'path':str(p.resolve()),'sha256':sha(p),'bytes':p.stat().st_size}
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--xml',required=True,type=Path);ap.add_argument('--jar',required=True,type=Path);ap.add_argument('--build-log',required=True,type=Path);ap.add_argument('--output',required=True,type=Path);a=ap.parse_args()
 if a.output.exists():raise ValueError('Preserve historical evidence: output exists')
 cases=list(ET.parse(a.xml).getroot().iter('testcase'));names={r.get('name'):r for r in cases};groups={}
 for name,path in CLASSES.items():
  source=ROOT/path;text=source.read_text(encoding='utf8');methods=re.findall(r'@GameTest\s*\([^)]*\)\s*public\s+void\s+(\w+)\s*\(',text)
  if not methods:raise ValueError('No actual annotated cases '+name)
  selected=[]
  for method in methods:
   key=name.lower()+'.'+method.lower();row=names.get(key)
   if row is None or row.find('failure') is not None or row.find('error') is not None:raise ValueError('Missing/failed actual owned case '+key)
   selected.append({'name':key,'timeSeconds':float(row.get('time','0')),'status':'PASS'})
  groups[name]={'source':ref(source),'cases':selected,'passed':len(selected)}
 failures=[r.get('name') for r in cases if r.find('failure') is not None or r.find('error') is not None]
 if failures:raise ValueError('Current full native XML contains failures '+str(failures))
 result={'schema':'dreamwalker-v9-owned-native-subsets-v1','status':'PASS_CURRENT_NATIVE_MECHANISM_LADDER_SOURCE_PAIR_ROOF_DIAGNOSTICS_CASES','scope':'Development GameTests; ordinary current-JAR runtime evidence NOT_RUN by this tool','candidateJar':ref(a.jar),'xml':ref(a.xml),'buildLog':ref(a.build_log),'allNativePassed':len(cases),'groups':groups,'ordinaryRuntime':'NOT_RUN_IN_THIS_REPORT','manualVisualAcceptance':'PENDING_USER_REVIEW','unsupportedFractionalNativeFoundation':'BOTTOM slab/fence explicitly refused without consumption; fractional selfstanding mounting still PENDING'}
 a.output.parent.mkdir(exist_ok=True);a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print(json.dumps({'status':result['status'],'allNative':len(cases),'groups':{k:v['passed'] for k,v in groups.items()}},ensure_ascii=False))
if __name__=='__main__':main()
