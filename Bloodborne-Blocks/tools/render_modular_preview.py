"""Software-render comparison of authored meshes and modular cells (not game QA)."""
from build_modular_palette import *
from render_catalog import Image, ImageDraw, ImageFont, CAM

def draw_polys(polys,size=260,frame=None):
    canvas=np.zeros((size,size,4),dtype=np.uint8);canvas[:]=[27,30,36,255];depth=np.full((size,size),-np.inf)
    if not polys:return Image.fromarray(canvas)
    pts=np.concatenate([np.array(p['vertices'])[:,:3]@CAM.T for p in polys]);lo=pts.min(axis=0);hi=pts.max(axis=0)
    if frame is not None:lo,hi=frame
    scale=(size-20)/max(hi[0]-lo[0],hi[1]-lo[1],.1);center=(lo+hi)/2
    for p in polys:
        data=np.array(p['vertices']);vertices=data[:,:3]@CAM.T;uv=data[:,3:5]/16;tex=texture(p['texture'])
        v=(vertices-center)*scale;v[:,0]+=size/2;v[:,1]=size/2-v[:,1]
        normal=np.cross(vertices[1]-vertices[0],vertices[2]-vertices[0]);length=np.linalg.norm(normal)
        if length<1e-8:continue
        shade=.65+.35*abs(normal[2]/length)
        for i in range(1,len(v)-1):
            ids=[0,i,i+1];tri=v[ids];tuv=uv[ids]
            x0=max(0,int(np.floor(tri[:,0].min())));x1=min(size,int(np.ceil(tri[:,0].max()))+1);y0=max(0,int(np.floor(tri[:,1].min())));y1=min(size,int(np.ceil(tri[:,1].max()))+1)
            if x1<=x0 or y1<=y0:continue
            xx,yy=np.meshgrid(np.arange(x0,x1)+.5,np.arange(y0,y1)+.5);a,b,c=tri;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
            if abs(den)<1e-8:continue
            w0=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den;w1=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;w2=1-w0-w1
            z=w0*a[2]+w1*b[2]+w2*c[2];tx=w0*tuv[0,0]+w1*tuv[1,0]+w2*tuv[2,0];ty=w0*tuv[0,1]+w1*tuv[1,1]+w2*tuv[2,1]
            colors=tex[np.clip((ty*tex.shape[0]).astype(int),0,tex.shape[0]-1),np.clip((tx*tex.shape[1]).astype(int),0,tex.shape[1]-1)].copy()
            mask=(w0>=-1e-6)&(w1>=-1e-6)&(w2>=-1e-6)&(z>depth[y0:y1,x0:x1])&(colors[:,:,3]>32)
            colors[:,:,:3]=(colors[:,:,:3]*shade).astype(np.uint8);canvas[y0:y1,x0:x1][mask]=colors[mask];depth[y0:y1,x0:x1][mask]=z[mask]
    return Image.fromarray(canvas)

def main():
    meshes=read_meshes();mapping=json.loads((OUT/'migration.json').read_text())
    semantics=json.loads((ROOT/'docs/semantic-catalog-v2.json').read_text(encoding='utf-8-sig'))
    samples=['stone_bricks','nether_brick_wall','dead_fire_coral_fan','waxed_exposed_cut_copper_stairs','asset_addon_books_test','acacia_stairs']
    sheet=Image.new('RGB',(1120,len(samples)*308),(27,30,36));draw=ImageDraw.Draw(sheet);font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',15)
    for row,ident in enumerate(samples):
        spec=mapping[ident];parts=spec['states'][statekey(spec['default'])]
        selected=statekey(spec['default'])
        if not parts:
            selected,parts=next(((k,v) for k,v in spec['states'].items() if v),(selected,parts))
        together=[]
        if isinstance(parts,dict):
            bs=json.loads((ASSETS/'blockstates'/f'{ident}.json').read_text())
            state=dict(x.split('=') for x in selected.split(',') if x)
            for group in applications(bs,state):
                app=group[0];md=model(app['model'])
                for e in md.get('elements',[]):together.extend(element_polys(e,app,md))
            parts=[]
        for part in parts:
            ps=rotate_polys(meshes[part['id']]['polygons'],-FACING.index(part['properties']['facing']))
            for p in ps:
                v=np.array(p['vertices']);v[:,:3]+=part['offset'];together.append({**p,'vertices':v.tolist()})
        sheet.paste(draw_polys(together),(0,row*308));draw.text((8,row*308+263),semantics[ident]['ru'][:26],font=font,fill='white')
        draw.text((8,row*308+284),'Сборка секций' if parts else 'Цельный предмет',font=font,fill='#aab4c0')
        for col,part in enumerate(parts[:3]):
            ps=rotate_polys(meshes[part['id']]['polygons'],-FACING.index(part['properties']['facing']))
            sheet.paste(draw_polys(ps),(280*(col+1),row*308));draw.text((280*(col+1)+8,row*308+263),'Одна клетка '+str(part['offset']),font=font,fill='white')
    path=ROOT/'build/modular-preview.png';sheet.save(path);print(path)

if __name__=='__main__':main()

