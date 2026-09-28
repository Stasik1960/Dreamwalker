"""Prove curated overlap mappings against the immutable historical meshes."""
import gzip
import hashlib
import json
import zipfile
from pathlib import Path
from build_city_compat import rotated_mesh
from modded_world_adapter import proven_compositions

ROOT=Path(__file__).resolve().parents[1]

def signature(polygons):
    result=set()
    for polygon in polygons:
        vertices=[tuple(round(float(n),6) for n in row) for row in polygon['vertices']]
        result.add((polygon['texture'],min(tuple(vertices[i:]+vertices[:i]) for i in range(len(vertices)))))
    return result

def verify():
    manifest=json.loads((ROOT/'docs/composite-grid-repair/proven-module-compositions.json').read_bytes())
    jar=ROOT.parent/'releases/Bloodborne-Blocks/bloodborne-blocks-2.0.1-mc1.20.1.jar'
    if hashlib.sha256(jar.read_bytes()).hexdigest()!=manifest['historicalJarSha256']:
        raise ValueError('HISTORICAL_JAR_HASH_MISMATCH')
    with zipfile.ZipFile(jar) as z:meshes=json.loads(gzip.decompress(z.read('bloodborne_blocks/v2/meshes.json.gz')))
    def polygons(state):
        turns=('north','east','south','west').index(dict(state[1]).get('facing','north'))
        return rotated_mesh(meshes[state[0].split(':')[1]],turns)['polygons']
    rows=[]
    for sources,target in proven_compositions().items():
        expected=signature([p for source in sources for p in polygons(source)])
        actual=signature(polygons(target))
        if expected!=actual:raise ValueError('COMPOSITE_POLYGON_UNION_MISMATCH')
        rows.append({'target':target,'polygons':len(actual),'missing':0,'extra':0})
    return {'result':'PASS','compositions':rows}

if __name__=='__main__':print(json.dumps(verify()))
