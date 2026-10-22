import shutil
from common import *
wait_foreign();source=ROOT/'deliverables/streaming-7-20261020';target=OUT/'membership-fix/two-field-release'
assert source.resolve().is_relative_to((ROOT/'deliverables').resolve()) and target.absolute().is_relative_to(OUT.resolve()) and not target.exists()
for p in files(source):pass
source.rename(target)
for name in ['build-production.json','build-production.log','build-membership.json','build-membership.log']:
 shutil.copyfile(OUT/name,OUT/'membership-fix'/('two-field-'+name))
for name in ['nbt-probe.json','nbt-layout.json']:
 shutil.copyfile(OUT/'membership-fix'/name,OUT/'membership-fix'/('two-field-'+name))
