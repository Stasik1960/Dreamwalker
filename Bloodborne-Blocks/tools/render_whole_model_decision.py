"""Render an actual unresolved book placement for a user decision (offline)."""
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import render_modular_preview as raster
from export_blockbench_mesh_bundle import iter_meshes

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources/bloodborne_blocks'


def main():
    specs = [('A: current city', 'city', 'city_ornament_9a34ea84_0047', 'variant=5'),
             ('B: whole logical books', 'logical', 'o_books', 'facing=north,variant=books_0,visual=base')]
    data = []
    for title, layer, ident, state in specs:
        definition = next(d for d in json.loads((RES/layer/'definitions.json').read_bytes())['blocks'] if d['id'] == ident)
        mesh_id = definition['models'][state]
        mesh = next(mesh for key, mesh in iter_meshes(RES/layer/'meshes.json.gz') if key == mesh_id)
        geometry = json.loads((RES/layer/'geometry.json').read_bytes())
        shape = geometry['blocks'][ident]['states'][state]
        if 'ref' in shape: shape = geometry['profiles'][shape['ref']]
        data.append({'title': title, 'family': ident, 'state': state, 'mesh_id': mesh_id, 'mesh': mesh, 'shape': shape})
    size = 550
    sheet = Image.new('RGB', (size*2, size*2+140), (27,30,36))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 17)
    for row, camera in enumerate([raster.CAM.copy(), np.array([[1,0,0],[0,1,0],[0,0,1]])]):
        raster.CAM = camera
        points = np.concatenate([np.array(p['vertices'])[:,:3]@camera.T for d in data for p in d['mesh']['polygons']])
        low, high = points.min(axis=0), points.max(axis=0)
        frame = (low,high)
        for col,d in enumerate(data):
            panel = raster.draw_polys(d['mesh']['polygons'], size, frame).convert('RGB')
            overlay = ImageDraw.Draw(panel)
            scale = (size-20)/max(high[0]-low[0], high[1]-low[1], .1)
            center=(low+high)/2
            for cell_key, cell in d['shape'].get('cells',{}).items():
                offset=np.array([int(v) for v in cell_key.split(',')])
                for kind,color in [('outline','#4bbaff'),('collision','#ff714b')]:
                    for box in cell.get(kind,[]):
                        verts=np.array([[box[(3 if mask>>axis&1 else 0)+axis] for axis in range(3)] for mask in range(8)])+offset
                        projected=(verts@camera.T-center)*scale
                        projected[:,0]+=size/2; projected[:,1]=size/2-projected[:,1]
                        for a in range(8):
                            for axis in range(3):
                                b=a^(1<<axis)
                                if a<b: overlay.line([tuple(projected[a,:2]),tuple(projected[b,:2])],fill=color,width=2)
            y=row*(size+70)
            sheet.paste(panel,(col*size,y))
            draw.text((col*size+10,y+size+5),d['title']+f" ({len(d['mesh']['polygons'])} polygons)",font=font,fill='white')
            draw.text((col*size+10,y+size+28),'Blue: selection; orange: collision. Same scale.',font=font,fill='#b8c4cc')
            draw.text((col*size+10,y+size+49),'Offline asset render; city (-556, 50, -199)',font=font,fill='#b8c4cc')
    output=ROOT/'docs/whole-models-handoff/decisions'
    output.mkdir(parents=True,exist_ok=True)
    sheet.save(output/'books-current-vs-logical.png')
    (output/'books-current-vs-logical.json').write_text(json.dumps([{k:v for k,v in d.items() if k!='mesh'} for d in data],indent=2)+'\n',encoding='utf-8')
    print(output/'books-current-vs-logical.png')


if __name__ == '__main__': main()
