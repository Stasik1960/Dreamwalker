"""Preserve the measured pair before repairing only the validation harness."""
import shutil,zipfile
from common import *

def main():
 wait_foreign()
 target=OUT/'frozen-measurement-sources.zip';assert not target.exists()
 with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as archive:
  for p in files(PROJECT/'src/routeqa'):archive.write(p,p.relative_to(PROJECT).as_posix())
 dest=OUT/'after/screenshots';dest.mkdir(exist_ok=False)
 for p in ordinary(CLIENT/'screenshots').glob('route-*.png'):
  assert p.resolve().is_relative_to((CLIENT/'screenshots').resolve())
  shutil.move(str(ordinary(p)),dest/p.name)
 assert len(list(dest.glob('*.png')))==2
 crash=SERVER/'crash-reports/crash-2026-10-20_02.11.51-server.txt'
 shutil.copyfile(ordinary(crash),OUT/'after/server/watchdog.txt')
 write(OUT/'validation-input.json',{'productionJarSha256':json.loads((OUT/'candidate.json').read_text())['sha256'],'measuredComparison':'Unchanged baseline/after frozen QA; only the failed tower-to-brick tail will be validated again.','frozenMeasurementQaSha256':sha(OUT/'dreamwalker-native-route-qa.jar'),'frozenSourcesSha256':sha(target)})

if __name__=='__main__':main()
