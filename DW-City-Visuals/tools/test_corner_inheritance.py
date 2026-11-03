"""Verify that corner lighting retains the released city editor and its resources."""
import argparse, hashlib, json, zipfile
from pathlib import Path


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--base', type=Path, required=True)
    ap.add_argument('--jar', type=Path, required=True)
    ap.add_argument('--report', type=Path, required=True)
    args = ap.parse_args()
    with zipfile.ZipFile(args.base) as base, zipfile.ZipFile(args.jar) as new:
        before, after = set(base.namelist()), set(new.namelist())
        missing = sorted(before-after)
        changes = sorted(n for n in before & after if base.read(n) != new.read(n))
        allowed = {'fabric.mod.json', 'client-dw-altvisual-overlay-refmap.json',
                   'assets/bloodborne_dw/visuals/profile-fingerprints.json'}
        unexpected = [n for n in changes if n not in allowed]
        oldmeta, meta = (json.loads(z.read('fabric.mod.json')) for z in (base, new))
        for key in ('id', 'depends', 'entrypoints', 'accessWidener', 'jars'):
            assert oldmeta.get(key) == meta.get(key), key+' changed'
        assert all(m in meta['mixins'] for m in oldmeta['mixins'])
        assert {'config':'dw-corner-surface-light.mixins.json','environment':'client'} in meta['mixins']
        assert not missing, missing
        assert not unexpected, unexpected
        assert not any(n.startswith('dev/dreamwalker/bloodbornedw/') and
                       ('CornerLightingQa' in n or 'CornerSurfaceLightTest' in n or 'CornerQuadGeometryTest' in n or 'CornerQuadSplitterTest' in n or 'CornerQuadParentScopeTest' in n or 'CornerQuadCacheTest' in n)
                       for n in after)
        assert not any(n.startswith(('dev/dreamwalker/cornerqa/', 'dev/dreamwalker/qacommon/')) for n in after)
        assert 'dw-cornerqa.mixins.json' not in after
        assert not new.testzip()
        report = {'status':'PASS', 'base':str(args.base), 'jar':str(args.jar),
                  'baseSha256':hashlib.sha256(args.base.read_bytes()).hexdigest(),
                  'jarSha256':hashlib.sha256(args.jar.read_bytes()).hexdigest(),
                  'unchangedEditorClasses':sum(n.endswith('.class') for n in before),
                  'unchangedAssets':sum(n.startswith('assets/') and not n.endswith('/') and n not in allowed for n in before),
                  'changedEntries':changes, 'addedEntries':sorted(after-before),
                  'allPreviousEntriesRetained':True, 'qaExcluded':True}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps({k:report[k] for k in ('status','unchangedEditorClasses','unchangedAssets','changedEntries')}))


if __name__ == '__main__':
    main()
