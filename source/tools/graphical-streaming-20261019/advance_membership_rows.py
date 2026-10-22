import shutil
from common import *
wait_foreign();source=ROOT/'deliverables/streaming-7-20261020';target=OUT/'membership-fix/shape-only-release'
assert source.resolve().is_relative_to((ROOT/'deliverables').resolve()) and target.absolute().is_relative_to(OUT.resolve()) and not target.exists()
for p in files(source):pass
source.rename(target)
for name in ['nbt-probe.json','nbt-fields.csv','nbt-input.json']:
 shutil.copyfile(OUT/'membership-fix'/name,OUT/'membership-fix'/('shape-only-'+name))
