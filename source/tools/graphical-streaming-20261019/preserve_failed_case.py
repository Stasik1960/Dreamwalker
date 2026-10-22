import shutil,sys
from common import *
wait_foreign();case=OUT/sys.argv[1];p=json.loads((case/'prepared.json').read_text());assert p['status']=='FAIL'
dest=case/'screenshots';dest.mkdir(exist_ok=True)
for image in ordinary(CLIENT/'screenshots').glob('route-*.png'):
 assert image.resolve().is_relative_to((CLIENT/'screenshots').resolve());shutil.move(str(ordinary(image)),dest/image.name)
for side in ['server','client']:
 for name in [side+'.jfr',side+'-partial.jfr']:
  path=case/side/name
  if path.exists() and path.stat().st_size==0:ordinary(path).rename(path.with_suffix('.failed-empty'))
crashes=sorted((SERVER/'crash-reports').glob('crash-*-server.txt'),key=lambda x:x.stat().st_mtime)
if crashes:shutil.copyfile(ordinary(crashes[-1]),case/'server/watchdog.txt')
