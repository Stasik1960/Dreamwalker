import shutil
from common import *

wait_foreign()
source=OUT/'normal-save';target=OUT/'normal-save-spawn-wait-fixture'
assert source.resolve().parent==target.resolve().parent==OUT.resolve() and not target.exists()
assert json.loads((source/'checks.json').read_text())['error']=='java.lang.IllegalStateException: Saved probe did not unload: [15, -58]'
for addon in (SERVER/'mods').glob('dreamwalker-streaming7-checks-*.jar'):
 shutil.copyfile(addon,source/addon.name)
ordinary(source).rename(target)
