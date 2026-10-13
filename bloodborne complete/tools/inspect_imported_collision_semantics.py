"""Read-only working evidence for the new-object physical-default review."""
import json
from pathlib import Path
from gallery_platform_plan import ModelGeometry, corners, rotate

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/collision-simplification'

def element_boxes(pose, models, *, include_points=False):
    result = []
    for part in pose['parts']:
        if part.get('emissive'):
            continue
        for element in models.elements(part['model']):
            if not element.get('faces'):
                continue
            points = list(corners(element))
            local = element.get('rotation')
            if local:
                import math
                axis = 'xyz'.index(local['axis'])
                if local.get('rescale'):
                    scale = 1 / math.cos(math.radians(local['angle']))
                    points = [[local['origin'][i] + (p[i] - local['origin'][i]) * (1 if i == axis else scale) for i in range(3)] for p in points]
                points = [rotate(p, axis, local['angle'], local['origin']) for p in points]
            moved = []
            for point in points:
                point = rotate(point, 1, part.get('extraYaw', 0), part.get('extraPivot', [8,8,8]))
                point = rotate(point, 0, -part.get('pitch', 0), part.get('pivot', [8,8,8]))
                point = rotate(point, 1, -part.get('yaw', 0), part.get('pivot', [8,8,8]))
                moved.append([point[i] + part.get('offset', [0,0,0])[i] for i in range(3)])
            result.append({'from':[round(min(p[i] for p in moved),8) for i in range(3)],
                           'to':[round(max(p[i] for p in moved),8) for i in range(3)],
                           'physical':all(element['to'][i]-element['from'][i]>1e-7 for i in range(3)),
                           'name':element.get('name',''), 'model':part['model']})
            if include_points:result[-1]['points']=moved
    return result

def main():
    models = ModelGeometry()
    output = []
    for path in sorted((ROOT/'src/architecture/resources/bloodborne_dw/composite').glob('source_*.json')):
        doc=json.loads(path.read_text(encoding='utf-8'))
        variants=[]
        for index,var in enumerate(doc['variants']):
            variants.append({'variant':index, 'sourceRefs':var.get('sourceStateRefs'),
                             'closed':element_boxes(var['poses']['closed'],models),
                             'open':element_boxes(var['poses']['open'],models) if 'open' in var['poses'] else None})
        output.append({'id':doc['id'],'name':doc['displayName'],'openable':doc.get('openable'),
                       'variants':variants})
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/'element-review.json').write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    interesting=[{'id':d['id'],'name':d['name'],'openable':d['openable'],
                  'elements':len(d['variants'][0]['closed']),
                  'sourceRefs':d['variants'][0]['sourceRefs'],
                  'boxes':d['variants'][0]['closed']}
                 for d in output if d['openable'] or any(s in d['name'].lower() for s in ('лестниц','арка','растен','куст','дерево','цветок','ветв','трава'))]
    (OUT/'special-objects-review.json').write_text(json.dumps(interesting,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'objects':len(output),'special':len(interesting)}))

if __name__=='__main__':main()
