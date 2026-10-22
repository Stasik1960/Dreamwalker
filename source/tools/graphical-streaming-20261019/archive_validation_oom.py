from common import *
import shutil
wait_foreign();source=OUT/'validation-tail';target=OUT/'validation-tail-7g-oom'
assert source.resolve().is_relative_to(OUT.resolve()) and target.absolute().is_relative_to(OUT.resolve()) and not target.exists()
for p in files(source):pass
source.rename(target);dest=target/'screenshots';dest.mkdir(exist_ok=True)
for p in ordinary(CLIENT/'screenshots').glob('route-*.png'):
 assert p.resolve().is_relative_to((CLIENT/'screenshots').resolve());shutil.move(str(ordinary(p)),dest/p.name)
