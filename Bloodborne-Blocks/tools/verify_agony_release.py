"""Read-only cumulative release checks; writes evidence only, never scans the source world."""
from __future__ import annotations

import argparse, gzip, hashlib, json, re, zipfile
from pathlib import Path
from xml.etree import ElementTree

from production_fingerprints import ROOT, read, verify, write
from production_coverage import validate
from build_production_gallery import verify as verify_gallery


def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--gallery',type=Path,required=True)
    parser.add_argument('--zip',type=Path,required=True)
    parser.add_argument('--log',type=Path,required=True)
    args=parser.parse_args();out=ROOT/'docs/agony-patch'
    fingerprints=verify(allowlist_path=out/'allowlist.json')
    write(out/'untouched-regression.json',fingerprints)
    coverage=validate(gallery=args.gallery);write(out/'coverage.json',coverage)
    gallery=verify_gallery(args.gallery)
    resources=ROOT/'src/main/resources'
    with zipfile.ZipFile(args.jar) as jar:
        checked=0
        for path in resources.rglob('*'):
            if path.is_file():
                relative=path.relative_to(resources).as_posix()
                actual_bytes=jar.read(relative)
                if relative=='bloodborne_blocks.mixins.json':
                    actual_config=json.loads(actual_bytes);refmap=actual_config.pop('refmap')
                    assert refmap=='bloodborne-blocks-refmap.json' and jar.read(refmap)
                    assert actual_config==json.loads(path.read_bytes()),'unexpected mixin transformation'
                else:assert actual_bytes==path.read_bytes(),f'JAR resource differs: {relative}'
                checked+=1
        manifest=read(ROOT/'docs/production-logical-palette.json')
        expected={r['id'] for r in manifest['objects']}
        actual={Path(n).stem for n in jar.namelist() if n.startswith('assets/bloodborne_blocks/blockstates/') and n.endswith('.json')}
        assert actual==expected|{'architecture_part'},'JAR contains missing/stale blockstate IDs'
    with zipfile.ZipFile(args.zip) as archive:
        for path in args.gallery.rglob('*'):
            if path.is_file():
                relative=args.gallery.name+'/'+path.relative_to(args.gallery).as_posix()
                assert archive.read(relative)==path.read_bytes(),f'ZIP differs: {relative}'
    log=args.log.read_text(encoding='utf8',errors='replace')
    assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log,'successful final Gradle run required'
    xml=ElementTree.parse(ROOT/'build/test-results/gametest/TEST-logical-gametest.xml').getroot()
    cases=xml.findall('.//testcase')
    assert cases and not xml.findall('.//failure') and not xml.findall('.//error'),'GameTests failed'
    baseline=read(out/'baseline-fingerprints.json.gz')['fingerprints']['families']
    definitions={r['id']:r for r in read(resources/'bloodborne_blocks/logical/definitions.json')['blocks']}
    counts={}
    for locale in ('ru_ru','en_us'):
        names=read(resources/f'assets/bloodborne_blocks/lang/{locale}.json')
        counts[locale]=sum(baseline[i]['display_names'][locale]!=names['block.bloodborne_blocks.'+i] for i in definitions)
    # Cached census only: count the exact retired states, not all states of a carrier.
    required=read(ROOT/'docs/required-production-families.json')
    census=read(ROOT/required['source_census']);context=[]
    for decision in read(out/'decisions.json')['decisions']:
        if decision['status']!='CONTEXT_ONLY':continue
        components=[p['pattern']['components'][0] for p in decision['source_patterns']]
        observed=[r for r in census['states'] if any(r['id']==c['id'] and all(r['properties'].get(k)==v for k,v in c['properties'].items()) for c in components)]
        context.append({'retired_id':decision['id'],'status':'CONTEXT_ONLY','migration':'leave original cells unconsumed',
            'exact_cached_states':len(observed),'cached_cells':sum(d['count'] for r in observed for d in r['dimensions'].values()),
            'reason':decision['reason']})
    archives={}
    for name,expected_sha in [('source-world.zip','4353737d536677469d3b895e3515496ab64fab7b224e43428eb96e8c09724a51'),
                              ('source-resource-pack.zip','0f2c3d64a1d60734ae0786d128b522ea6bbd175f26f5d164bed5522c46898308')]:
        path=ROOT/'reference-inputs'/name
        assert path.is_file(),path
        actual_sha=sha(path);assert actual_sha==expected_sha,f'authoritative archive changed: {name}'
        archives[name]=actual_sha
    report={'result':'PASS','production_before':56,'production_after':len(definitions),
        'untouched_families':len(baseline)-len(fingerprints['allowed_families']),
        'display_names_changed':counts,'jar_resource_files_verified':checked,
        'permitted_loom_transform':'mixins.json gains the generated bloodborne-blocks-refmap.json reference only','gallery':gallery,
        'python_tests':sum(map(int,re.findall(r'Ran (\d+) tests?',log))),
        'gametests':len(cases),'gametest_failures':0,'gradle':'BUILD SUCCESSFUL',
        'source_archives_unchanged':archives,'context_retirements':context,
        'artifacts':{kind:{'file':str(path.resolve()),'bytes':path.stat().st_size,'sha256':sha(path)} for kind,path in [('jar',args.jar),('gallery_zip',args.zip)]},
        'limits':['No full city conversion or source scan.','Offline render/alpha inspection and native LevelStorage + dedicated GameTests; graphical client acceptance is still required.',
                  'C046/C561 review artwork is one placeable survivor; indistinguishable raw CASES carriers retain the original o_cases_0 mapping.']}
    write(out/'release-proof.json',report)
    print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__=='__main__':main()
