import difflib
from common import *
paths=['gradle.properties','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeBlockEntity.java','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeClient.java','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeNetworking.java','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeInitialSync.java','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/mixin/ShardedLedgerTrackingMixin.java','src/architecture/resources/bloodborne_dw.composite.mixins.json','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/mixin/LithiumPaletteAllocationMixin.java']
lines=[]
for path in paths:
 p=PROJECT/path;before=OUT/'source-before'/p.name;old=before.read_text(encoding='utf-8-sig').splitlines(keepends=True) if before.exists() else []
 for line in difflib.unified_diff(old,p.read_text(encoding='utf-8-sig').splitlines(keepends=True),fromfile='a/bloodborne complete/'+path if old else '/dev/null',tofile='b/bloodborne complete/'+path):lines.append(line if line.endswith('\n') else line+'\n\\ No newline at end of file\n')
(ROOT/'deliverables/streaming-7-20261020/sources/changes-from-streaming.6.patch').write_text(''.join(lines),encoding='utf-8',newline='\n')
