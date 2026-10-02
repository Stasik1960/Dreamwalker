"""Export complete rendered objects as textured OBJ/MTL and glTF 2.0."""
from __future__ import annotations

import argparse
import gzip
import json
import math
import os
import shutil
import struct
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_JAR = Path(r'C:\Users\vakir\.gradle\caches\fabric-loom\1.20.1\minecraft-client.jar')


def read_json(path):
    return json.loads(path.read_bytes())


def read_gz(path):
    return json.loads(gzip.decompress(path.read_bytes()))


def texture_bytes(resources, texture, jar=DEFAULT_JAR):
    ns, rel = texture.split(':', 1) if ':' in texture else ('minecraft', texture)
    key = f'assets/{ns}/textures/{rel.removesuffix(".png")}.png'
    if (resources / key).is_file():
        return (resources / key).read_bytes(), key
    if jar.is_file():
        with zipfile.ZipFile(jar) as archive:
            if key in archive.namelist():
                return archive.read(key), key
    return None, key


def normal(a, b, c):
    u, v = [b[i]-a[i] for i in range(3)], [c[i]-a[i] for i in range(3)]
    n = [u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0]]
    length = math.sqrt(sum(x*x for x in n)) or 1
    return tuple(x/length for x in n)


def mesh_geometry(mesh, offset=(0, 0, 0)):
    vertices, uv, normals, triangles, materials = [], [], [], [], []
    for polygon in mesh.get('polygons', []):
        points, texture = polygon['vertices'], polygon.get('texture')
        if len(points) < 3 or not texture:
            raise ValueError('invalid textured polygon')
        base = len(vertices)
        vertices.extend(tuple(float(p[i])+offset[i] for i in range(3)) for p in points)
        # Minecraft and glTF measure V from the top; OBJ conversion happens at write.
        uv.extend((float(p[3])/16, float(p[4])/16) for p in points)
        normals.extend([normal(*vertices[base:base+3])] * len(points))
        materials.extend([texture] * len(points))
        triangles.extend((base, base+i, base+i+1, texture) for i in range(1, len(points)-1))
    if not vertices:
        raise ValueError('empty object mesh')
    return vertices, uv, normals, triangles, materials


def gltf(mesh, textures, files, offset=(0, 0, 0)):
    vertices, uv, normals, triangles, _ = mesh_geometry(mesh, offset)
    blob, views, accessors = bytearray(), [], []

    def add(values, fmt, kind, count, target, bounds=None):
        blob.extend(b'\0' * (-len(blob) % 4))
        start = len(blob)
        data = struct.pack('<'+fmt*len(values), *values)
        blob.extend(data)
        views.append({'buffer': 0, 'byteOffset': start, 'byteLength': len(data), 'target': target})
        accessor = {'bufferView': len(views)-1, 'componentType': 5126 if fmt == 'f' else 5125,
                    'count': count, 'type': kind}
        if bounds:
            accessor.update(min=bounds[0], max=bounds[1])
        accessors.append(accessor)
        return len(accessors)-1

    position = add([x for p in vertices for x in p], 'f', 'VEC3', len(vertices), 34962,
                   ([min(p[i] for p in vertices) for i in range(3)], [max(p[i] for p in vertices) for i in range(3)]))
    norm = add([x for p in normals for x in p], 'f', 'VEC3', len(normals), 34962)
    texcoord = add([x for p in uv for x in p], 'f', 'VEC2', len(uv), 34962)
    primitives = []
    for index, texture in enumerate(textures):
        indices = [v for t in triangles if t[3] == texture for v in t[:3]]
        if indices:
            accessor = add(indices, 'I', 'SCALAR', len(indices), 34963, ([min(indices)], [max(indices)]))
            primitives.append({'attributes': {'POSITION': position, 'NORMAL': norm, 'TEXCOORD_0': texcoord},
                               'indices': accessor, 'material': index, 'mode': 4})
    return {'asset': {'version': '2.0', 'generator': 'Bloodborne assembled artist kit'}, 'scene': 0,
            'scenes': [{'nodes': [0]}], 'nodes': [{'mesh': 0}], 'meshes': [{'primitives': primitives}],
            'buffers': [{'byteLength': len(blob), 'uri': 'mesh.bin'}], 'bufferViews': views, 'accessors': accessors,
            'materials': [{'pbrMetallicRoughness': {'baseColorTexture': {'index': i}, 'metallicFactor': 0, 'roughnessFactor': 1},
                           'alphaMode': 'MASK', 'doubleSided': True} for i in range(len(textures))],
            'images': [{'uri': f} for f in files], 'textures': [{'sampler': 0, 'source': i} for i in range(len(files))],
            'samplers': [{'magFilter': 9728, 'minFilter': 9728, 'wrapS': 10497, 'wrapT': 10497}], 'buffer_data': bytes(blob)}


def select_states(definition, all_variants=False):
    defaults = {**definition.get('default', {}), **definition.get('placement_properties', {})}
    if 'facing' in defaults:
        defaults['facing'] = 'north'
    if 'root_anchor' in defaults:
        defaults['root_anchor'] = 'canonical'
    for state, mesh in sorted(definition['models'].items()):
        props = dict(p.split('=', 1) for p in state.split(',') if '=' in p)
        if all_variants or all(props.get(k) == defaults.get(k) for k in props if k in
                               {'facing', 'root_anchor', 'face', 'half', 'axis', 'waterlogged'}):
            yield state, mesh


def export(resources, output, ids=None, all_variants=False, jar=DEFAULT_JAR):
    resources, output = Path(resources).resolve(), Path(output).resolve()
    if output.exists() or resources == output or resources in output.parents:
        raise FileExistsError('artist output must be new and outside resources')
    output.parent.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix='artist-', dir=output.parent))
    try:
        logical, city = resources/'bloodborne_blocks/logical', resources/'bloodborne_blocks/city'
        definitions, meshes, geometries = [], {}, {}
        for folder, is_city in ((logical, False), (city, True)):
            if not (folder/'definitions.json').exists():
                continue
            definitions.extend((d, is_city) for d in read_json(folder/'definitions.json')['blocks']
                               if (not is_city or d.get('whole_owner')) and (not ids or d['id'] in ids))
            for name in ('meshes.json.gz', 'owner-meshes.json.gz'):
                if (folder/name).exists():
                    meshes.update(read_gz(folder/name))
            if (folder/'geometry.json').exists():
                geo = read_json(folder/'geometry.json')
                for ident, block in geo['blocks'].items():
                    geometries[ident] = {k: geo.get('profiles', {}).get(g.get('ref'), g) for k, g in block['states'].items()}
        contracts = {d['id']: d for d in read_json(logical/'contracts-v2.json')['families']} if (logical/'contracts-v2.json').exists() else {}
        debug = read_json(resources/'bloodborne_blocks/debug-ids.json')['ids']
        manifest = {'schema_version': 3, 'objects': [], 'texture_references': {}, 'units': 'Minecraft blocks; Y-up'}
        for definition, is_city in sorted(definitions, key=lambda row: row[0]['id']):
            ident = definition['id']
            if ident not in debug or len(debug[ident]) != 5 or not debug[ident].isdigit():
                raise ValueError('missing numeric debug ID: '+ident)
            for state, mesh_id in select_states(definition, all_variants):
                render = contracts.get(ident, {}).get('states', {}).get(state, {}).get('render_mesh')
                if ident in contracts and render is None:
                    raise ValueError('missing contract render state: '+ident+' '+state)
                if render:
                    mesh_id, offset = render['id'], render['offset']
                else:
                    offset = geometries.get(ident, {}).get(state, {}).get('render_offset', [0,0,0])
                if mesh_id not in meshes:
                    raise ValueError('missing mesh: '+str(mesh_id))
                mesh = meshes[mesh_id]
                vertices, uv, normals, triangles, _ = mesh_geometry(mesh, offset)
                stem = ident+'__'+state
                folder = stage/stem
                folder.mkdir()
                textures = sorted({p['texture'] for p in mesh['polygons']})
                files = []
                names = {t: t.replace(':', '__').replace('/', '__') for t in textures}
                for texture in textures:
                    data, source = texture_bytes(resources, texture, jar)
                    if data is None:
                        raise FileNotFoundError('missing texture: '+texture)
                    filename = names[texture]+'.png'
                    (folder/filename).write_bytes(data)
                    files.append(filename)
                    manifest['texture_references'][texture] = {'source': source, 'example': (Path(stem)/filename).as_posix()}
                material = '\n'.join(line for t in textures for line in [f'newmtl {names[t]}', 'Kd 1 1 1', f'map_Kd {names[t]}.png', ''])
                (folder/(stem+'.mtl')).write_text(material+'\n', encoding='utf8')
                obj = ['mtllib '+stem+'.mtl'] + ['v %.8f %.8f %.8f'%p for p in vertices]
                obj += ['vt %.8f %.8f'%(u,1-v) for u,v in uv] + ['vn %.8f %.8f %.8f'%p for p in normals]
                current = None
                for triangle in triangles:
                    if triangle[3] != current:
                        current = triangle[3]
                        obj.append('usemtl '+names[current])
                    obj.append('f '+' '.join(f'{i+1}/{i+1}/{i+1}' for i in triangle[:3]))
                (folder/(stem+'.obj')).write_text('\n'.join(obj)+'\n', encoding='utf8')
                document = gltf(mesh, textures, files, offset)
                (folder/'mesh.bin').write_bytes(document.pop('buffer_data'))
                (folder/(stem+'.gltf')).write_text(json.dumps(document, indent=2)+'\n', encoding='utf8')
                manifest['objects'].append({'id': ident, 'debug_id': debug[ident], 'state': state,
                                             'source': 'city' if is_city else 'logical', 'mesh': mesh_id,
                                             'render_offset': offset, 'attachment_root': stem})
        (stage/'artist-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
        (stage/'README-ru.md').write_text('''# Материалы для художника

В каждой папке — цельная конструкция, OBJ + MTL, glTF + mesh.bin и реальные PNG материалов.
Откройте glTF в Blender; PNG лежат рядом. Начните с деревьев, затем камня и крыш.
Единица длины — блок Minecraft; Y вверх. Геометрия уже повёрнута и смещена как в игре.
OBJ: начало V снизу; glTF: начало V сверху. Alpha MASK и ближайшая фильтрация сохраняют пиксельный стиль.
Варианты внешности, открытые состояния и освещение сохранены; повороты и служебные якоря убраны.
ID на табличке галереи совпадает с debug_id в artist-manifest.json.
Не изменяйте расположение игровых опор и коллизии при художественной правке.
Для возврата PNG замените соответствующий assets/<namespace>/textures/... из source в манифесте.
Для возврата изменённой геометрии сохраняйте UV и названия текстур; импорт полигонов выполняется отдельно.
''', encoding='utf8')
        os.replace(stage, output)
        return manifest
    finally:
        if stage.exists():
            shutil.rmtree(stage)


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('output', type=Path)
    p.add_argument('--resources', type=Path, default=ROOT/'src/main/resources')
    p.add_argument('--ids', nargs='*')
    p.add_argument('--all-variants', action='store_true')
    p.add_argument('--minecraft-jar', type=Path, default=DEFAULT_JAR)
    a = p.parse_args()
    m = export(a.resources, a.output, a.ids, a.all_variants, a.minecraft_jar)
    print(json.dumps({'states': len(m['objects']), 'definitions': len({r['id'] for r in m['objects']})}))
