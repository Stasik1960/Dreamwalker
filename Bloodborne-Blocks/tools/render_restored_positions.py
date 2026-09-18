"""Offline before/after scene samples, using actual state/model geometry."""
import argparse
import functools
from render_modular_preview import *
from relocate_modular_conflicts import WorldEditor, props_of
from world_io import compound


def render(before, after, output):
    worlds=[WorldEditor(before),WorldEditor(after)]
    meshes=read_meshes()
    legacy={b['id']:b for b in DATA['blocks']}
    modular={b['id']:b for b in json.loads((OUT/'definitions.json').read_text())['blocks']}
    @functools.lru_cache(None)
    def shape(name, properties):
        values=dict(properties);ident=name.removeprefix('bloodborne_blocks:')
        if ident in meshes:
            return rotate_polys(meshes[ident]['polygons'],-FACING.index(values.get('facing','north')))
        if ident not in legacy:
            return []
        bs=json.loads((ASSETS/'blockstates'/f'{ident}.json').read_text())
        result=[]
        for choices in applications(bs,values):
            app=choices[0];md=model(app['model'])
            for e in md.get('elements',[]):result.extend(element_polys(e,app,md))
        return result
    samples=[('Вставки фасада',(-516,88,0),(-505,104,11)),
             ('Проёмы и детали фасада',(-592,120,37),(-579,138,47)),
             ('Ящики и мешки',(-376,56,-294),(-362,79,-281))]
    size=650
    sheet=Image.new('RGB',(size*2,len(samples)*(size+45)),(27,30,36))
    draw=ImageDraw.Draw(sheet);font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',19)
    for row,(label,low,high) in enumerate(samples):
        scenes=[]
        for world in worlds:
            polygons=[]
            for x in range(low[0],high[0]+1):
                for y in range(low[1],high[1]+1):
                    for z in range(low[2],high[2]+1):
                        pos=(x,y,z);state=world.state(pos)
                        if state is None:continue
                        name=compound(state)['Name'].value
                        ident=name.removeprefix('bloodborne_blocks:')
                        if modular.get(ident,{}).get('full_cube'):
                            enclosed=True
                            for dx,dy,dz in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)):
                                near=world.state((x+dx,y+dy,z+dz))
                                nid='' if near is None else compound(near)['Name'].value.removeprefix('bloodborne_blocks:')
                                if not modular.get(nid,{}).get('full_cube'):enclosed=False;break
                            if enclosed:continue
                        for p in shape(name,tuple(sorted(props_of(state).items()))):
                            vertices=np.array(p['vertices']);vertices[:,:3]+=np.array(pos)-low
                            polygons.append({**p,'vertices':vertices.tolist()})
            scenes.append(polygons)
        pts=np.concatenate([np.array(p['vertices'])[:,:3]@CAM.T for scene in scenes for p in scene])
        frame=(pts.min(axis=0),pts.max(axis=0))
        for col,scene in enumerate(scenes):
            sheet.paste(draw_polys(scene,size,frame),(col*size,row*(size+45)))
            draw.text((col*size+12,row*(size+45)+size+8),label+(' — до' if col==0 else ' — восстановлено'),fill='white',font=font)
        print('Rendered',label,flush=True)
    output.parent.mkdir(parents=True,exist_ok=True);sheet.save(output)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for k in ('before','after','output'):p.add_argument(k,type=Path)
    a=p.parse_args();render(a.before,a.after,a.output)
