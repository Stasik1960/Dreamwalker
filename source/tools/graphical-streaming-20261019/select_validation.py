import shutil,zipfile
from common import *
wait_foreign()
old=OUT/'validation-tail';archive=OUT/'validation-tail-old-addon-selection'
assert old.resolve().is_relative_to(OUT.resolve()) and archive.absolute().is_relative_to(OUT.resolve()) and not archive.exists()
for p in files(old):pass
old.rename(archive)
screens=archive/'screenshots';screens.mkdir(exist_ok=True)
for p in ordinary(CLIENT/'screenshots').glob('route-*.png'):
 assert p.resolve().is_relative_to((CLIENT/'screenshots').resolve())
 shutil.move(str(ordinary(p)),screens/p.name)
wrong=OUT/'dreamwalker-native-tail-validation.jar';saved=OUT/'validation-tail-old-addon-selection.jar'
assert not saved.exists();ordinary(wrong).rename(saved)
version=next(line.split('=',1)[1].strip() for line in (PROJECT/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
correct=PROJECT/'build/libs'/('dreamwalker-native-route-qa-'+version+'.jar')
with zipfile.ZipFile(correct) as z:
 bytecode=z.read('dev/dreamwalker/bloodbornedw/routeqa/RouteClient.class')
 assert b'dw.route.validationTail' in bytecode and b'Presentation sample limit exceeded' not in bytecode
shutil.copyfile(correct,wrong)
write(OUT/'validation-ready.json',{'status':'CORRECT_VALIDATION_ARTIFACT_SELECTED','source':str(correct),'qaSha256':sha(wrong),'frozenMeasurementQaUnchanged':sha(OUT/'dreamwalker-native-route-qa.jar'),'productionJarUnchanged':sha(Path(json.loads((OUT/'candidate.json').read_text())['jar']))})
