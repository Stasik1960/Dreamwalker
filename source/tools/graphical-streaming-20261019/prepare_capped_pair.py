import shutil
from common import *
wait_foreign()
def move_case(name,target):
 source=OUT/name;dest=OUT/target
 assert source.resolve().is_relative_to(OUT.resolve()) and dest.absolute().is_relative_to(OUT.resolve()) and not dest.exists()
 for p in files(source):pass
 source.rename(dest)
move_case('baseline','baseline-unlimited-partial')
move_case('after','after-sparse-unlimited-partial')
dest=OUT/'after-sparse-unlimited-partial/screenshots';dest.mkdir(exist_ok=True)
for p in ordinary(CLIENT/'screenshots').glob('route-*.png'):
 assert p.resolve().is_relative_to((CLIENT/'screenshots').resolve());shutil.move(str(ordinary(p)),dest/p.name)
if (OUT/'comparison.json').exists():shutil.copyfile(OUT/'comparison.json',OUT/'membership-fix/transport-comparison-original.json')
write(OUT/'fps-conditions.json',{'status':'CLEAN_MATCHED_PAIR_AT_120_FPS','reason':'Vanilla maxFps=260 means unlimited. The old diagnostic presentation buffer overflowed; both versions will use 120 FPS. Original partial experiments remain archived and are excluded from final numbers.','clientFps':120,'serverHeap':'9G','clientHeap':'5G','viewDistance':8,'simulationDistance':6,'qaSha256':sha(OUT/'dreamwalker-native-route-qa.jar'),'productionJarSha256':json.loads((OUT/'candidate.json').read_text())['sha256']})
