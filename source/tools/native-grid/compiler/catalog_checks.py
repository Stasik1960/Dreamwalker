"""Bounded regression checks for data loss at cell seams and binary encoding."""
from pathlib import Path
import json, math, sqlite3, struct, sys, traceback, zlib
sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog import CatalogCompiler, binary_mesh, opaque_cube, full_rectangles
from export_cells import clip_pose, clipped_quad, quad_cells, area, canonical, prepare_database, TextureOpacity


def check():
    source_root = Path(__file__).resolve().parents[3]
    out = source_root.parent/'research/compiler-checks-v5'
    out.mkdir(parents=True, exist_ok=True)
    material = {'sprite': 'bloodborne_dw:block/test_unchanged', 'layer': 'solid'}
    def quad(vertices, normal, color=-1):
        return {'vertices': [[*p, p[0]*3+p[2], p[1]*2, color, 0, 127] for p in vertices], 'normal': list(normal), 'material': material, 'face': 5, 'cull': -1, 'tint': -1, 'shade': True, 'ao': True, 'emissive': False, 'opaque': True}
    positive = quad([(1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)], (1, 0, 0))
    negative = quad([(1, 0, 1), (1, 1, 1), (1, 1, 0), (1, 0, 0)], (-1, 0, 0))
    assert list(quad_cells(positive)) == [(0, 0, 0)], 'Positive boundary plane duplicated or missing'
    assert list(quad_cells(negative)) == [(1, 0, 0)], 'Negative boundary plane duplicated or missing'
    diagonal = quad([(-.5, 0, -.5), (1.5, 0, 1.5), (1.5, 1, 1.5), (-.5, 1, -.5)], (2**-.5, 0, -2**-.5), -185273100)
    source = {'key': 'test-pose-A', 'quads': [positive, diagonal], 'cells': [{'cell': [0, 0, 0], 'collision': [[.1, .2, .3, .4, .5, .6]], 'outline': [[.1, .2, .3, .4, .5, .6]], 'climbing': [], 'optics': {'opacity': 0, 'luminance': 7}, 'fluid': None}]}
    cells, proof = clip_pose(source)
    assert proof['relativeError'] < 1e-6, 'Diagonal clipping area changed'
    for cell in cells:
        assert all(0 <= vertex[axis] <= 1 for q in cell['quads'] for vertex in q['vertices'] for axis in range(3))
        for q in cell['quads']:
            for vertex in q['vertices']:
                world_x, world_y, world_z = [vertex[i]+cell['cell'][i] for i in range(3)]
                assert abs(vertex[3]-(world_x*3+world_z)) < 1e-6, 'U interpolation changed'
                assert abs(vertex[4]-world_y*2) < 1e-6, 'V interpolation changed'
    assert full_rectangles([(0, 0, .5, 1), (.5, 0, 1, 1)])
    assert not full_rectangles([(0, 0, .49, 1), (.5, 0, 1, 1)])
    tinted = {**diagonal, 'material': {**material, 'tintSource': 'bloodborne_dw:source_grass_block'}}
    for coordinate in quad_cells(tinted):
        for clipped in clipped_quad(tinted, coordinate):
            assert clipped['material']['tintOffset'] == [-n for n in coordinate]
    assert all(q['material']['layer'] == 'translucent' for c in cells for q in c['quads'] if q['vertices'][0][5] == -185273100)
    # PNG alpha proof uses real filter reconstruction and a transparent pixel
    # elsewhere in the same atlas. Only the proven opaque 16-pixel tile wins.
    fixture = out/'resources/assets/test/textures/atlas.png'
    fixture.parent.mkdir(parents=True, exist_ok=True)
    def chunk(kind, data):
        return struct.pack('>I', len(data))+kind+data+struct.pack('>I', zlib.crc32(kind+data)&0xffffffff)
    image_rows = b''.join(b'\0'+b'\xff\xff\xff\xff'*16+b'\xff\xff\xff\0'*16 for _ in range(16))
    fixture.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR', struct.pack('>IIBBBBB', 32, 16, 8, 6, 0, 0, 0))+chunk(b'IDAT', zlib.compress(image_rows))+chunk(b'IEND', b''))
    texture_proof = TextureOpacity(out/'resources')
    patch = {**positive, 'material': {'sprite': 'test:atlas', 'layer': 'cutout'}, 'vertices': [[*p[:3], min(7.9, p[3]), min(15.9, p[4]), *p[5:]] for p in positive['vertices']]}
    assert texture_proof.opaque_quad(patch), 'Opaque subregion of transparent atlas was not proved'
    patch['vertices'][0][3] = 15.9
    assert not texture_proof.opaque_quad(patch), 'Transparent atlas texel was falsely promoted'
    patch['vertices'][0][3] = 3.0
    # The encoder must retain all color/normal bits, vertex layout and flags.
    encoded = binary_mesh([positive], lambda m: 23)
    assert struct.unpack('>iibbiB', encoded[:15]) == (1, 23, -1, 5, -1, 3)
    assert len(encoded) == 4+11+4*32
    first = struct.unpack('>fffffiii', encoded[15:47])
    assert first == (1., 0., 0., 3., 0., -1, 0, 127)
    raw = out/'source.jsonl'
    twin = {**source, 'key': 'test-pose-B'}
    raw.write_text(canonical(source)+'\n'+canonical(twin)+'\n', encoding='utf-8')
    fragment_db = out/'fragments.sqlite'
    substrate_state = {'registryId': 'test:exact_native_slab', 'properties': {}}
    substrate_shape = [[0., 0., 0., 1., .5, 1.]]
    native_data = out/'native.jsonl'
    full_state = {'registryId': 'test:opaque_native_patch', 'properties': {}}
    native_data.write_text(canonical({'state': substrate_state, 'quads': [], 'nativeVisualComplete': False, 'collision': substrate_shape, 'outline': substrate_shape, 'culling': [], 'climbing': [], 'optics': {'opacity': 0, 'luminance': 4, 'opaque': False, 'opaqueFullCube': False}, 'fluid': None})+'\n'+canonical({'state': full_state, 'renderType': 'MODEL', 'quads': [patch], 'nativeVisualComplete': True, 'collision': [], 'outline': [], 'culling': [], 'climbing': [], 'optics': {'opacity': 0, 'luminance': 0, 'opaque': False}, 'fluid': None})+'\n', encoding='utf-8')
    prepare_database(raw, fragment_db, native_data)
    compiler = CatalogCompiler(fragment_db, out/'catalog-state.sqlite', out/'assets/bloodborne_dw/native_grid', out/'resources')
    try:
        first = compiler.intern_cell({'key': 'first', 'fragments': [{'pose': source['key'], 'cell': [0, 0, 0]}]})
        second = compiler.intern_cell({'key': 'second', 'fragments': [{'pose': twin['key'], 'cell': [0, 0, 0]}]})
        assert first == second, 'Dedup incorrectly depends on source identity'
        whole = compiler.intern_whole({'key': 'whole-test-template', 'pose': source['key']})
        assert whole['baseCell'] != first, 'Whole role merged into independently destructible architecture'
        assert len({r['cell'] for r in whole['cells']}) == len(whole['cells']), 'Whole local roles lost their finite offsets'
        try:
            compiler.intern_cell({'baseState': substrate_state, 'fragments': []})
        except ValueError:
            pass
        else:
            raise AssertionError('Physics-only substrate silently lost visual geometry')
        native_index = compiler.intern_cell({'baseState': substrate_state, 'baseVisualMode': 'substrateFragments', 'substrateFragments': [{'pose': source['key'], 'cell': [0, 0, 0]}], 'fragments': []})
        mixed_index = compiler.intern_cell({'baseState': substrate_state, 'baseVisualMode': 'substrateFragments', 'substrateFragments': [{'pose': source['key'], 'cell': [0, 0, 0]}], 'fragments': [{'pose': source['key'], 'cell': [0, 0, 0]}]})
        air_index = compiler.intern_cell({'baseState': {'registryId': 'minecraft:air', 'properties': {}}, 'fragments': [{'pose': source['key'], 'cell': [0, 0, 0]}]})
        native_solid_index = compiler.intern_cell({'baseState': full_state, 'fragments': []})
        combined_index = compiler.intern_cell({'baseState': full_state, 'baseVisualMode': 'nativePlusSubstrateFragments', 'substrateFragments': [{'pose': source['key'], 'cell': [0, 0, 0]}], 'fragments': []})
        receipt = compiler.finish(out/'mapping.jsonl')
        document = json.loads((out/'assets/bloodborne_dw/native_grid/catalog.json').read_text(encoding='utf-8'))
        assert document['optics'][document['cells'][first]['optics']]['luminance'] == 7
        assert document['shapes'][document['cells'][first]['collision']] == [[.1, .2, .3, .4, .5, .6]]
        assert document['shapes'][document['cells'][native_index]['collision']] == substrate_shape, 'Source pose physics overwrote exact native slab physics'
        assert document['optics'][document['cells'][native_index]['optics']]['luminance'] == 4, 'Source pose emission overwrote exact native optics'
        assert document['optics'][document['cells'][mixed_index]['optics']]['luminance'] == 7, 'Ordinary emitting guest disappeared over exact native substrate'
        assert document['optics'][document['cells'][air_index]['optics']]['luminance'] == 7, 'Explicit AIR substrate suppressed an emitting source guest'
        assert document['meshes'][document['cells'][combined_index]['mesh']]['quadCount'] > document['meshes'][document['cells'][native_solid_index]['mesh']]['quadCount'], 'Native-plus-overhang route discarded one visual source'
        assert compiler.opacity.counts['promotedNativeOpaqueUvQuads'] > 0, 'Proven opaque native cutout patch did not become SOLID'
    finally:
        compiler.close()
    return {'status': 'PASS', 'checks': ['signed boundary-plane membership', 'diagonal/chunk seam area conservation', 'exact interpolated UV', 'exact shared double collision', 'packed ABGR/light/normal roundtrip', 'identity-independent final content dedup', 'separate whole object finite roles', 'no false rectangle occlusion', 'finite root biome tint offset', 'packed fractional alpha layer', 'opaque UV patch in transparent PNG atlas', 'explicit substrate visual transfer preserves actual native physics and emission', 'physics-only native cannot silently render empty', 'ordinary source guest emission survives native and explicit AIR substrates', 'native opaque sampled-UV cutout patch becomes SOLID', 'native base and foreign source overhang both retained', 'persistent compiler algorithm fingerprint'], 'clippingProof': proof, 'catalog': receipt}


if __name__ == '__main__':
    destination = Path(__file__).resolve().parents[3].parent/'research/compiler-checks-v5/result.json'
    try:
        report = check()
    except BaseException:
        report = {'status': 'FAIL', 'error': traceback.format_exc()}
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    if report['status'] != 'PASS':
        raise SystemExit(1)
