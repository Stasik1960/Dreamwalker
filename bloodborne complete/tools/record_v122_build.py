"""Preserve real build/test evidence without overwriting earlier audit history."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--run-name',default='v122-main2')
args=parser.parse_args()
assert args.run_name.replace('-','').replace('_','').isalnum()
report=ROOT/'build/remaining-transfer'/args.run_name/'result.json'
jar=ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.2-simple-collisions.jar'
xml=ROOT/'build/test-results/gametest'/('TEST-'+args.run_name+'.xml')
data=json.loads(report.read_text(encoding='utf-8'));tests=ET.parse(xml).getroot()
assert data['status']=='PASS' and data['exitCode']==0
assert not tests.findall('.//failure') and not tests.findall('.//error')
assert len(tests.findall('.//testcase'))==63
dest=ROOT/'docs/v12.2-simplified-collisions';dest.mkdir(parents=True,exist_ok=True)
previous=dest/'build-and-tests.json'
if previous.is_file():
    old=json.loads(previous.read_text(encoding='utf-8'))
    if old.get('productionJarSha256') != hashlib.sha256(jar.read_bytes()).hexdigest():
        history=dest/'history';history.mkdir(exist_ok=True)
        shutil.copyfile(previous,history/('build-'+old['productionJarSha256'][:12]+'.json'))
shutil.copyfile(xml,dest/xml.name)
data.update(productionJarSha256=hashlib.sha256(jar.read_bytes()).hexdigest(),
    gameTests='PASS',gameTestCount=63,gameTestFailures=0,
    gameTestXmlSha256=hashlib.sha256(xml.read_bytes()).hexdigest(),
    previousAttempt={'run':'v122-main1','status':'FAIL','passed':62,'failed':1,
        'reason':'NEW test compared raw physical-list emptiness with runtime physics after required six-face trimming. The assertion now accounts for oriented/cropped forms; independent all-state oracle and separate strict boundary checks provide additional evidence.',
        'protectedV12Tests':'PASS', 'finalRetest':'PASS_63_OF_63'})
(dest/'build-and-tests.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
