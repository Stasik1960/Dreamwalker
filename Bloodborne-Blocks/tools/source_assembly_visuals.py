"""Source-pack + vanilla-only offline geometry; never imported mod artwork."""
import copy
import functools
import io
import itertools
import json
import os
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

os.environ.setdefault('BLOODBORNE_VANILLA_JAR', str(Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'))
from catalog_geometry import rotation
from modular_mesh import FACE_INDEX, face_uv, texname, tile_uv
from manual_review_world import DEFAULT_SOURCEPACK, EXPECTED_PACK_SHA256, _sha256


@functools.lru_cache(None)
def archives():
    if _sha256(DEFAULT_SOURCEPACK) != EXPECTED_PACK_SHA256: raise ValueError('Wrong source resource pack')
    return zipfile.ZipFile(DEFAULT_SOURCEPACK), zipfile.ZipFile(os.environ['BLOODBORNE_VANILLA_JAR'])


def resource(name, kind, suffix):
    namespace, local = name.split(':',1) if ':' in name else ('minecraft',name)
    path = f'assets/{namespace}/{kind}/{local}.{suffix}'
    for archive in archives():
        try: return archive.read(path)
        except KeyError: pass
    raise ValueError('Missing original resource: ' + path)


@functools.lru_cache(None)
def source_model(name):
    if name.split(':')[-1].startswith('builtin/'): return {}
    raw = json.loads(resource(name,'models','json'))
    base = copy.deepcopy(source_model(raw['parent'])) if 'parent' in raw else {}
    textures = {**base.get('textures',{}), **raw.get('textures',{})}
    base.update(raw); base['textures'] = textures
    return base


@functools.lru_cache(None)
def source_texture(name):
    return np.asarray(Image.open(io.BytesIO(resource(name,'textures','png'))).convert('RGBA'))


def source_polys(apps):
    polygons = []
    for app in apps:
        md = source_model(app['model'])
        for element in md.get('elements',[]):
            points = np.array(list(itertools.product(*zip(element['from'],element['to']))),float)
            if 'rotation' in element:
                r = element['rotation']; axis = 'xyz'.index(r['axis']); matrix = rotation(axis,r['angle'])
                if r.get('rescale'):
                    scale = np.ones(3); scale[[i for i in range(3) if i != axis]] = 1/np.cos(np.radians(r['angle']))
                    matrix = matrix @ np.diag(scale)
                origin = np.asarray(r['origin'],float); points = (points-origin) @ matrix.T + origin
            matrix = rotation(1,-app.get('y',0)) @ rotation(0,-app.get('x',0))
            points = ((points-8) @ matrix.T+8)/16 + np.asarray(app.get('offset',[0,0,0]))
            for side,face in element.get('faces',{}).items():
                vertices = np.column_stack((points[FACE_INDEX[side]],face_uv(face,side,element,app)))
                if np.linalg.norm(np.cross(vertices[1,:3]-vertices[0,:3],vertices[2,:3]-vertices[0,:3])) < 1e-10: continue
                tex = texname(md,face['texture']); source_texture(tex)  # fail closed on missing PNG
                polygons.extend(tile_uv({'texture':tex,'vertices':vertices.tolist()}))
    return polygons, []
