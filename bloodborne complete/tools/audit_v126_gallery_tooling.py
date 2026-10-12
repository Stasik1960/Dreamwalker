"""Source-only gallery preflight; this is not native world or pixel acceptance."""
import argparse, ast, hashlib, json
from pathlib import Path
from prepare_gallery_v126_input import ROOT, assembly_contract, canonical_catalogue, pose_index
from finalize_gallery_v126_world import readme_text

TOOLS = ('prepare_gallery_v126_input.py', 'verify_gallery_v126.py',
         'prepare_gallery_v126_capture.py', 'finalize_gallery_v126_world.py')


class SourceResources:
    def read(self, name):
        path = ROOT / 'src/architecture/resources' / name
        assert path.is_relative_to(ROOT / 'src/architecture/resources')
        return path.read_bytes()


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    for name in TOOLS:
        ast.parse((ROOT / 'tools' / name).read_text(encoding='utf8'), filename=name)
    resources = SourceResources()
    metadata, defaults, active = assembly_contract(resources)
    catalogue, _, _ = canonical_catalogue(read(ROOT / 'docs/gallery-v12.1/catalogue.json'), resources)
    rows = catalogue['exhibits']
    assert len(rows) == 624  # The native author adds 23 non-placeable framed items.
    selected = {row['registry']:row for row in rows if row['origin'] != 'V12' and row['kind'] == 'architecture'}
    for registry, pose in defaults.items():
        row = selected[registry]
        assert pose_index({**row, 'blockStateTag':row['state']}) == pose
        assert row['workingPoseIndices'] == active[registry]
    assert not set(selected).intersection(metadata['hiddenPartRedirects'])
    plan = read(ROOT / 'build/v126-assembly-plan/assembly-manifest.json')
    doors = read(ROOT / 'build/v126-door-plan/door-manifest.json')['doors']
    doors += read(ROOT / 'build/v126-city-mounteddoor/door-manifest.json')['doors']
    reviews = [dict(section='assemblies', primaryId=g['primaryId']) for g in plan['assemblies'] if not g['doorRequiresLeafPartitionAndOpenPose']]
    reviews += [dict(section='doors', primaryId=d['mainId'], reviewPoseIndex=d['variant'], open=o) for d in doors for o in (False, True)]
    assert len(reviews) == 22 and len({d['mainId'] for d in doors}) == 7
    assert {(g['reviewPoseIndex'], g['open']) for g in reviews if g['primaryId']=='90923'} == {(1, False), (1, True), (2, False), (2, True)}
    city = read(ROOT / 'docs/v12.6/city/city-final-aggregated.json')
    assert len(city['owners']) == 8426
    city['placedOwners'] = city['owners']
    catalogue['summary'] = {'newWorkingTypes':len(selected)}
    text = readme_text(dict(catalogue=catalogue, city=city, reviewPlatforms=reviews,
                          exhibits=rows, styleRetirements=catalogue['styleRetirements']), 'Проверка навигации')
    for stale in ('кандидатов в двери', 'Сборки ещё не объединены', 'Предметы пока остаются отдельными', 'Новые свойства дверям не добавлены'):
        assert stale not in text
    assert '7 новых функциональных типов дверей' in text and '90923' in text
    report = {'schema':'dreamwalker-v126-gallery-tooling-source-audit', 'status':'PASS_SOURCE_ONLY',
              'sourceScriptsParsed':list(TOOLS), 'plannedBuildingPlatforms':len(rows),
              'expectedNativeFramedItems':23, 'expectedFinalMainPlatforms':647,
              'newWorkingTypes':len(selected), 'completePrimaryTypes':len(defaults),
              'completeAssemblyRecipes':len(metadata['recipes']), 'hiddenPartRegistrationsExcluded':len(metadata['hiddenPartRedirects']),
              'staticReviews':6, 'doorStateReviews':16, 'totalReviews':len(reviews),
              'mountedDoor90923ActivePoses':active['bloodborne_dw:source_smooth_quartz_top'],
              'cityOwners':len(city['owners']), 'originalSourceWorldWritten':False,
              'productionJarRead':False, 'nativeWorldAndPixelVerification':'NOT_RUN_BY_SOURCE_PREFLIGHT',
              'scriptSha256':{name:hashlib.sha256((ROOT/'tools'/name).read_bytes()).hexdigest() for name in TOOLS}}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf8', newline='\n')
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    main()
