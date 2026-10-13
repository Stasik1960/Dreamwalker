"""Explicit QA-only world-copy server, retaining all ordinary runner safeguards."""
import sys
from pathlib import Path
import run_v11_server as base
assert '--world-copy' in sys.argv and '--gallery-input' not in sys.argv
assert '--run-name' in sys.argv and sys.argv[sys.argv.index('--run-name')+1].startswith('v127-new-gallery-we-preflight')
original_inputs=base.extra_mod_inputs;original_copy=base.copy_extra_mods
def inputs(paths,allow_gallery=False):
    records=original_inputs(paths,allow_gallery=True)
    assert {r['id'] for r in records}=={'dreamwalker_gallery_author','worldedit'}
    return records
def copy(records,mods):
    result=original_copy(records,mods)
    (mods.parent/'gallery-worldedit-preflight.request').write_text('Explicit command preflight in an isolated copy only\n',encoding='utf8')
    return result
base.extra_mod_inputs=inputs;base.copy_extra_mods=copy;base.main()
