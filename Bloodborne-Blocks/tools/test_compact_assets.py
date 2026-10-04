"""Prevent retired/prototype registry assets from surviving catalog regeneration."""
import argparse,json,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
def check(jar=None):
    ids={d['id']for scope in ('logical','city')for d in json.loads((RES/f'bloodborne_blocks/{scope}/definitions.json').read_bytes())['blocks']}
    if not(RES/'bloodborne_blocks/city/compact-catalog.json').exists():return
    expected={
        'assets/bloodborne_blocks/blockstates/':ids|{'architecture_part'},
        'assets/bloodborne_blocks/models/item/':ids,
        'data/bloodborne_blocks/loot_tables/blocks/':ids}
    for prefix,wanted in expected.items():
        actual={p.stem for p in(RES/prefix).glob('*.json')}
        if actual!=wanted:raise AssertionError(f'source asset boundary {prefix}: extra={sorted(actual-wanted)[:8]}, missing={sorted(wanted-actual)[:8]}')
    if jar:
        with zipfile.ZipFile(jar)as archive:
            for prefix,wanted in expected.items():
                actual={Path(n).stem for n in archive.namelist()if n.startswith(prefix)and n.endswith('.json')}
                if actual!=wanted:raise AssertionError(f'jar asset boundary {prefix}: extra={sorted(actual-wanted)[:8]}, missing={sorted(wanted-actual)[:8]}')
            for name in archive.namelist():
                if name.startswith(('assets/bloodborne_blocks/','data/bloodborne_blocks/'))and not name.endswith('/'):
                    if not(RES/name).is_file():raise AssertionError('unowned packaged runtime asset '+name)
    print('compact registry asset boundary PASS:',len(ids),'public IDs, no retired/prototype blockstate/item/loot assets')
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--jar',type=Path);a=p.parse_args();check(a.jar)
