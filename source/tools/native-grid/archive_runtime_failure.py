"""Preserve the exact failed runtime before its addressed storage-only rebuild."""
import shutil
from run_profiles import P,ROOT,sha,write
jar=P/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-native-grid.1.jar'
expected='6c0de50530b5917c3e6ea848a2e97745eae2588509983e5852b5ecf68a8a939a'
assert sha(jar)==expected
out=ROOT/'research/runtime-history/pre-lithium-head'
out.mkdir(parents=True,exist_ok=True)
saved=out/'dreamwalker-native-grid-before-storage-fix.jar'
if saved.exists():assert sha(saved)==expected
else:shutil.copy2(jar,saved)
assert sha(saved)==expected
write(out/'binding.json',{'status':'PRESERVED_FAILED_RUNTIME','sha256':expected,'archive':str(saved),'sourcePath':str(jar),'failedCheck':'native-grid-plan/relight-launch-receipt.json','catalogSha256':'4306e64f6f7bbbf47b4c0fe498d3201b16045e2b590309a087c4c85771867afe'})
print(str(saved))
