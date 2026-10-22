"""Validate a compiled NGQ1 resource and report actual shared-bank statistics."""
from pathlib import Path
from collections import Counter
import argparse, hashlib, json, math, struct


def inspect(directory):
    directory = Path(directory)
    catalog = json.loads((directory/'catalog.json').read_text(encoding='utf-8'))
    layers = Counter()
    cull_faces = Counter()
    emissive = tinted = 0
    mesh_layers = []
    binary = directory/'meshes.bin'
    with binary.open('rb') as stream:
        assert stream.read(4) == b'NGQ1'
        count = struct.unpack('>i', stream.read(4))[0]
        assert count == len(catalog['meshes'])
        for mesh in catalog['meshes']:
            quads = struct.unpack('>i', stream.read(4))[0]
            assert quads == mesh['quadCount']
            present = set()
            for _ in range(quads):
                material, cull, face, tint, flags = struct.unpack('>ibbiB', stream.read(11))
                assert 0 <= material < len(catalog['materials']) and -1 <= cull <= 5 and 0 <= face <= 5 and 0 <= flags <= 7
                layer = catalog['materials'][material]['layer']
                layers[layer] += 1
                present.add(layer)
                cull_faces[str(cull)] += 1
                emissive += bool(flags & 4)
                tinted += tint >= 0
                for vertex in range(4):
                    values = struct.unpack('>fffffiii', stream.read(32))
                    assert all(math.isfinite(value) for value in values[:5])
                    assert all(-1e-6 <= value <= 1.000001 for value in values[:3]), 'Compiled visual escaped its local cell'
            mesh_layers.append(present)
        assert stream.read(1) == b'', 'Trailing data or a mismatched mesh layout'
    cells_by_layer = Counter()
    for cell in catalog['cells']:
        for shape in ('collision', 'outline', 'culling', 'climbing'):
            assert 0 <= cell[shape] < len(catalog['shapes'])
        assert 0 <= cell['mesh'] < len(catalog['meshes']) and 0 <= cell['optics'] < len(catalog['optics'])
        present = mesh_layers[cell['mesh']]
        cells_by_layer['+'.join(sorted(present)) if present else 'empty'] += 1
    for whole in catalog['wholeObjects']:
        assert any(row['offset'] == [0, 0, 0] and row['cell'] == whole['baseCell'] for row in whole['cells'])
        for member in whole['cells']:
            assert 0 <= member['cell'] < len(catalog['cells'])
    markers = [path for folder in ('blockstates', 'models/block', 'models/item') for path in (directory.parent/folder).glob('native_grid_*.json')]
    mesh_bytes, catalog_bytes = binary.stat().st_size, (directory/'catalog.json').stat().st_size
    marker_bytes = sum(path.stat().st_size for path in markers)
    return {'status': 'PASS', 'cells': len(catalog['cells']), 'blockBanks': math.ceil(len(catalog['cells'])/128), 'wholeObjects': len(catalog['wholeObjects']), 'wholeMemberCells': sum(len(row['cells']) for row in catalog['wholeObjects']), 'sharedMeshes': len(catalog['meshes']), 'sharedShapes': len(catalog['shapes']), 'sharedShapeBoxes': sum(len(shape) for shape in catalog['shapes']), 'sharedOptics': len(catalog['optics']), 'materials': len(catalog['materials']), 'materialsByLayer': dict(Counter(row['layer'] for row in catalog['materials'])), 'sharedQuadsByLayer': dict(layers), 'cellTypesByLayerSet': dict(cells_by_layer), 'cullingNonemptyCellTypes': sum(bool(catalog['shapes'][cell['culling']]) for cell in catalog['cells']), 'preparedQuadsByCullFace': dict(cull_faces), 'sharedQuadVertices': sum(layers.values())*4, 'maxMeshQuads': max((mesh['quadCount'] for mesh in catalog['meshes']), default=0), 'emissiveQuads': emissive, 'tintedQuads': tinted, 'meshBytes': mesh_bytes, 'catalogBytes': catalog_bytes, 'markerFiles': len(markers), 'markerBytes': marker_bytes, 'totalRuntimeResourceBytes': mesh_bytes+catalog_bytes+marker_bytes, 'meshesSha256': hashlib.sha256(binary.read_bytes()).hexdigest(), 'catalogSha256': hashlib.sha256((directory/'catalog.json').read_bytes()).hexdigest(), 'runtimePositionsInUnitCell': True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    report = inspect(args.resources)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    main()
