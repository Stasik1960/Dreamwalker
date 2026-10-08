"""Read saved review UUID/ledger counts; keep unique cells separate from shared contributions."""
import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path
from verify_review_v8_scene import ROOT, read, resolve, require, sha
from world_io import compound, read_nbt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verification-report', type=Path, required=True)
    parser.add_argument('--archive-report', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    vp, ap = resolve(args.verification_report), resolve(args.archive_report)
    v, a = read(vp), read(ap)
    require(v['status'] == 'PASS_SAVED_OWNERS_NATIVE_FIXTURES_AND_PRODUCTION_REOPEN', 'Saved scene proof failed')
    require(a['status'] == 'PACKAGED_VERIFIED_SCENE_PENDING_USER_REVIEW', 'Scene archive proof failed')
    require(v['production_jar_sha256'] == a['production_jar_sha256'], 'Scene count proofs use different JARs')
    require(a['verification_report_sha256'] == sha(vp), 'Independent proof changed')
    require(a['sha256'] == sha(a['output']), 'Archived world bytes changed')
    ledger_path = Path(v['world'])/'data/bloodborne_dw_composite_owners.dat'
    cells = compound(compound(read_nbt(ledger_path).root)['data'])['cells'].value
    positions = [tuple(compound(cell)['pos'].value) for cell in cells]
    require(len(positions) == len(set(positions)) == v['ledger_cell_count'], 'Saved ledger cell count differs')
    histogram = Counter(len(compound(cell)['owners'].value) for cell in cells)
    sums, by_kind = Counter(), defaultdict(Counter)
    for row in v['composite_owners']:
        sums.update(row['saved_sparse_counts'])
        by_kind[row['registryId']].update(row['saved_sparse_counts'])
    require(sum(count*owners for owners, count in histogram.items()) == sums['ownedCells'], 'Per-owner contribution count differs from saved ledger')
    result = {
        'schema': 'dreamwalker-review-v8-saved-counts-v1',
        'status': 'PASS_COUNTS_FROM_INDEPENDENT_SAVED_LEDGER_AND_NBT',
        'production_jar_sha256': v['production_jar_sha256'],
        'verification_report': str(vp), 'verification_report_sha256': sha(vp),
        'archive_report': str(ap), 'archive_report_sha256': sha(ap),
        'root_count': v['actual_root_count'], 'root_counts_by_kind': v['root_counts_by_kind'],
        'composite_owner_UUIDs': len(v['composite_owners']),
        'native_ladder_roots': v['root_counts_by_kind']['prototype_ladder'],
        'native_wall_roots': v['root_counts_by_kind']['prototype_wall'],
        'actual_unique_composite_BE_cells': v['cached_root_and_helper_bes'],
        'actual_unique_helper_carrier_cells': v['cached_root_and_helper_bes']-len(v['composite_owners']),
        'actual_unique_ledger_bound_cells': v['ledger_cell_count'],
        'shared_cell_owner_histogram': dict(histogram),
        'unique_multi_owner_cells': sum(count for owners, count in histogram.items() if owners > 1),
        'per_owner_contribution_sums': dict(sums), 'per_kind_contribution_sums': dict(by_kind),
        'native_fixture_cells': len(v['native_fixtures']),
        'foreign_native_BEs_preserved': sum(row['be_sha256'] is not None for row in v['native_fixtures']),
        'accepted_RP_control_entities': len(v['rp_controls']),
        'archive_files_omitting_only_session_lock': a['file_count'],
        'archive_uncompressed_bytes': a['uncompressed_bytes'], 'archive_sha256': a['sha256'],
        'counts_limit': 'Per-owner contributions count shared cells repeatedly; unique helper carrier cells are separate. No full city performance or manual visual approval claim.',
    }
    path = resolve(args.report)
    require(not path.exists(), 'Do not overwrite historical scene counts; select a fresh report path')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    print(json.dumps({key: result[key] for key in ('root_count', 'composite_owner_UUIDs',
        'actual_unique_helper_carrier_cells', 'actual_unique_ledger_bound_cells', 'unique_multi_owner_cells', 'archive_sha256')}))


if __name__ == '__main__':
    main()
