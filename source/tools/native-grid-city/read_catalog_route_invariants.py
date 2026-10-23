"""Read the finite visual-route representation errors without compiling or writing DBs."""
import argparse
import json
import sys
from pathlib import Path
import migrate as m
import native_certificates as c
sys.path.insert(0, str(m.PROJECT / 'tools/native-grid'))
from export_cells import FragmentStore
sys.path.insert(0, str(m.PROJECT / 'tools/native-grid/qa'))
from check_affected_native_visual_cells import final_quads
from export_cells import TextureOpacity
from catalog import opaque_cube


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--compositions', type=Path, required=True)
    p.add_argument('--sha256', required=True)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--fragments', type=Path)
    p.add_argument('--fragments-sha256')
    p.add_argument('--textures', type=Path)
    a = p.parse_args()
    m.require(m.sha(a.compositions) == a.sha256, 'Exact actual normalized file required')
    problems = []
    with a.compositions.open(encoding='utf-8-sig') as stream:
        for line in stream:
            row = json.loads(line)
            mode = row.get('baseVisualMode', 'native')
            if mode in ('substrateFragments', 'nativePlusSubstrateFragments') and (
                    row.get('baseState') is None or not any(name in row for name in ('substrateFragments', 'substrateNativeFragments'))):
                problems.append(row)
    fluid_proof = None
    if a.fragments:
        m.require(a.fragments_sha256 and m.sha(a.fragments) == a.fragments_sha256, 'Exact saved geometry bank required')
        store = FragmentStore(a.fragments)
        references = 0
        nonempty = []
        full_cubes = []
        try:
            for row in problems:
                for item in row.get('fragments', []):
                    data = store.fragment(item['pose'], tuple(item['cell']))[1]
                    references += 1
                    if data.get('fluid') is not None:
                        nonempty.append({'key': row['key'], 'fragment': item, 'fluid': data['fluid']})
                if a.textures and opaque_cube(final_quads(row, store, TextureOpacity(a.textures))):
                    full_cubes.append(row['key'])
        finally:
            store.close()
        fluid_proof = {'fragments': {'path': str(a.fragments.resolve()), 'sha256': a.fragments_sha256},
                       'ordinaryFragmentReferences': references, 'nonemptyFluids': nonempty,
                       'allOrdinaryFluidsEmpty': not nonempty,
                       'fullCubeKeys': full_cubes, 'fullCubeTested': bool(a.textures)}
    report = {'status': 'READ_ONLY_ROUTE_REPRESENTATION_FACTS',
              'compositions': {'path': str(a.compositions.resolve()), 'sha256': a.sha256},
              'problemCount': len(problems), 'problems': problems, 'fluidProof': fluid_proof,
              'databaseWrites': 0, 'worldReads': 0}
    c.immutable_json(m.module_api(), a.out.resolve(), report)
    print(m.canonical({'status': report['status'], 'problemCount': len(problems)}))


if __name__ == '__main__':
    main()
