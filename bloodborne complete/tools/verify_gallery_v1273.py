"""The accepted typed-NBT/owner/disk verifier with this release's exact counts."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
source=(ROOT/'tools/verify_gallery_v1271.py').read_text(encoding='utf8')
# Apply after the existing V12.7.1 adapter has patched the original verifier.
needle="namespace = {'__file__':__file__,'__name__':'v1271_verifier_adapter'}"
if needle not in source:
    needle="namespace={'__file__':__file__,'__name__':'v1271_verifier_adapter'}"
# The adapter's exact footer is checked below, not guessed at execution time.
assert "exec(compile(source" in source
source=source.replace("exec(compile(source", "assert source.count('len(mainrows)==644 and len(groups)==22')==1 and source.count('==539')==2\nsource=source.replace('len(mainrows)==644 and len(groups)==22','len(mainrows)==642 and len(groups)==23').replace('==539','==537')\nexec(compile(source",1)
exec(compile(source,str(ROOT/'tools/verify_gallery_v1271.py'),'exec'),{'__name__':'__main__','__file__':__file__})
