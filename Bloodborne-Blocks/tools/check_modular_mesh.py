"""Small geometry regressions, including values from vanilla 1.20.1 UV locking."""
from build_modular_palette import *

e={'from':[0,0,0],'to':[16,16,16]}
face={'uv':[2,3,6,9],'texture':'minecraft:block/stone'}
np.testing.assert_allclose(face_uv(face,'up',e,{'uvlock':True,'y':90}),[[7,2],[7,6],[13,6],[13,2]],atol=1e-6)
np.testing.assert_allclose(face_uv({**face,'rotation':90},'up',e,{'uvlock':True,'y':90}),[[13,2],[7,2],[7,6],[13,6]],atol=1e-6)
np.testing.assert_allclose(face_uv({**face,'rotation':90},'north',e,{}),[[6,9],[6,3],[2,3],[2,9]],atol=1e-6)
points=np.array(list(itertools.product((0.,1.),repeat=3)))
polys=[polygon(points[idx],np.array([[0,16],[16,16],[16,0],[0,0]]),'minecraft:block/stone') for idx in FACE_INDEX.values()]
assert is_cube(polys)
rotated=[]
for p in polys:
    a=np.array(p['vertices']);a[:,:3]=(a[:,:3]-.5)@rotation(1,45).T+.5;rotated.append({**p,'vertices':a.tolist()})
cut=clip_solid(rotated,0,.5,True)
assert len(cut)==5 and sum('cap_axis' in p for p in cut)==1, 'Cap through vertices missing'
for p in cut:
    a=np.array(p['vertices']);assert np.all(np.isfinite(a));assert np.linalg.norm(np.cross(a[1,:3]-a[0,:3],a[2,:3]-a[0,:3]))>1e-8
tiles=tile_uv(polygon(points[FACE_INDEX['up']],np.array([[-16,16],[32,16],[32,0],[-16,0]]),'minecraft:block/stone'))
assert len(tiles)==3
assert all(np.all((np.array(p['vertices'])[:,3:]>=0)&(np.array(p['vertices'])[:,3:]<=16)) for p in tiles)
assert is_cube(polys[:-1]+[r for p in [polys[-1]] for r in [clip(p,2,.5,True)[0],clip(p,2,.5,False)[0]]]), 'Split cube coverage not recognized'
print('Modular mesh regressions PASS: vanilla UV, cap through vertices, tiled UV, split cube coverage')

