from pathlib import Path
from zipfile import ZipFile
import hashlib
import shutil

work = Path(__file__).resolve().parent
base = Path(r'D:\DreamWalker\.local\bloodborne-v22-r2\client\mods\DreamWalker-v22-Bloodborne-Seam-Repair.jar')
output = work / 'Seam-Repair-Startup-Step2.jar'
expected = '0747595154f08d9b1be1ecfaa95e5abc51e884cfc11c406508089bcd857be36c'
with base.open('rb') as stream:
    if hashlib.file_digest(stream, 'sha256').hexdigest() != expected:
        raise RuntimeError('Seam Repair baseline does not match recorded SHA-256')
prefix = 'dev/dreamwalker/bloodbornedw/composite/CompactStateTransitions'
with ZipFile(work / 'intermediary.jar') as patch, ZipFile(base) as source, ZipFile(output, 'w') as target:
    replacements = {name for name in patch.namelist() if name.startswith(prefix) and name.endswith('.class')}
    if prefix + '.class' not in replacements or prefix + '$StateIndex.class' not in replacements:
        raise RuntimeError('Missing expected optimized classes')
    existing = set(source.namelist())
    for entry in source.infolist():
        with (patch if entry.filename in replacements else source).open(entry.filename) as src, target.open(entry, 'w') as dst:
            shutil.copyfileobj(src, dst, 1024 * 1024)
    for name in sorted(replacements - existing):
        with patch.open(name) as src, target.open(name, 'w') as dst:
            shutil.copyfileobj(src, dst, 1024 * 1024)
with output.open('rb') as stream:
    print(hashlib.file_digest(stream, 'sha256').hexdigest(), output)
