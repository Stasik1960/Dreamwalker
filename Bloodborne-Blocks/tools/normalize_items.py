"""Generate bounded item geometry from authored block models; never resize world models."""
import copy, itertools, json, math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/bloodborne_blocks'
MODELS = ASSETS / 'models'

def resolve(name, seen=()):
    if not name.startswith('bloodborne_blocks:'):
        return {}
    if name in seen:
        raise ValueError('Cyclic model parent: ' + name)
    d = json.loads((MODELS / (name.split(':', 1)[1] + '.json')).read_text(encoding='utf-8-sig'))
    base = resolve(d.get('parent', ''), (*seen, name))
    textures = {**base.get('textures', {}), **d.get('textures', {})}
    base.update(d)
    base['textures'] = textures
    return base

def vertices(elements):
    points = []
    for e in elements:
        for p in itertools.product(*zip(e['from'], e['to'])):
            p = list(p)
            r = e.get('rotation')
            if r:
                axis = 'xyz'.index(r['axis']); a = (axis + 1) % 3; b = (axis + 2) % 3
                angle = math.radians(r['angle']); c = math.cos(angle); s = math.sin(angle)
                u, v = p[a]-r['origin'][a], p[b]-r['origin'][b]
                factor = 1/abs(c) if r.get('rescale') else 1
                p[a] = r['origin'][a] + (u*c-v*s)*factor
                p[b] = r['origin'][b] + (u*s+v*c)*factor
            points.append(p)
    return points

def generate():
    count = 0
    for path in sorted((MODELS / 'item').glob('*.json')):
        d = resolve('bloodborne_blocks:item/' + path.stem)
        if not d.get('elements'):
            continue  # Vanilla generated sprites and vanilla parents retain their own transforms.
        elements = copy.deepcopy(d['elements'])
        points = vertices(elements)
        center = [(min(p[a] for p in points)+max(p[a] for p in points))/2 for a in range(3)]
        radius = max(math.dist(p, center) for p in points)
        scale = 7 / max(radius, 0.01)
        for e in elements:
            # Preserve implicit UVs before changing model-space coordinates.
            x,y,z = e['from']; X,Y,Z = e['to']
            uv = {'down':[x,16-Z,X,16-z], 'up':[x,z,X,Z], 'north':[16-X,16-Y,16-x,16-y],
                  'south':[x,16-Y,X,16-y], 'west':[z,16-Y,Z,16-y], 'east':[16-Z,16-Y,16-z,16-y]}
            for side, face in e.get('faces', {}).items():
                face.setdefault('uv', uv[side]); face.pop('cullface', None)
            for key in ('from','to'):
                e[key] = [round(8+(v-center[a])*scale, 6) for a,v in enumerate(e[key])]
            if 'rotation' in e:
                e['rotation']['origin'] = [round(8+(v-center[a])*scale, 6) for a,v in enumerate(e['rotation']['origin'])]
        display = {
            'gui': {'rotation':[30,135,0], 'scale':[0.85]*3},
            'ground': {'translation':[0,3,0], 'scale':[0.4]*3},
            'fixed': {'rotation':[0,180,0], 'scale':[0.65]*3},
            'head': {'scale':[0.6]*3}}
        for hand in ('righthand','lefthand'):
            display['firstperson_'+hand] = {'rotation':[0,45,0], 'scale':[0.35]*3}
            display['thirdperson_'+hand] = {'rotation':[75,45,0], 'translation':[0,2.5,0], 'scale':[0.4]*3}
        out = {'credit':d.get('credit','Bloodborne architecture'), 'textures':d['textures'],
               'elements':elements, 'display':display, 'gui_light':'side', 'ambientocclusion':False}
        path.write_text(json.dumps(out, ensure_ascii=False, separators=(',',':'))+'\n', encoding='utf-8')
        assert max(math.dist(p,[8,8,8]) for p in vertices(elements)) < 7.00001, path
        count += 1
    print(f'Validated {count} item models: radius <= 7, GUI diameter <= 11.9 pixels; all hand/frame transforms explicit.')

if __name__ == '__main__':
    generate()
