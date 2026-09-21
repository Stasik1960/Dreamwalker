"""Ignored source-model contact sheets for authored v2 families."""
import json, sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[1]; RES=ROOT/'src/main/resources/bloodborne_blocks'; OUT=ROOT/'build/source-contact-sheets'; OUT.mkdir(parents=True,exist_ok=True)
sys.path.insert(0,str(ROOT/'tools'))
import build_logical_objects as g
from render_modular_preview import draw_polys

def main():
    defs={x['id']:x for x in g.read(g.RES/'bloodborne_blocks/definitions.json')['blocks']}; _,overrides=g.semantic_catalog()
    selected=set(sys.argv[1:]); rows=[]; seen=set()
    for source,block in defs.items():
        if selected and source not in selected: continue
        bs=g.read(g.RES/f'assets/minecraft/blockstates/{source}.json') if (g.RES/f'assets/minecraft/blockstates/{source}.json').is_file() else g.read(g.RES/f'assets/bloodborne_blocks/blockstates/{source}.json')
        for state in g.source_states(block):
            if state.get('waterlogged')=='true' or state.get('assembled')=='true': continue
            for app in g.apps_for_state(bs,state):
                model=app['model']; key=model
                if key in seen or not g.proven_authored_artwork(model,overrides): continue
                seen.add(key); polys,_=g.object_polys([{'model':model}]); rows.append({'source':source,'state':g.key(state),'model':model,'polys':polys,'bounds':len(polys)})
    rows.sort(key=lambda x:(x['source'],x['state']))
    inv=[{k:row[k] for k in ('source','state','model','bounds')} for row in rows]
    (OUT/'inventory.json').write_text(json.dumps({'count':len(rows),'models':inv},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    inv=[]; font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',11); page=20; tile=(240,280)
    for start in range(0,len(rows),page):
        chunk=rows[start:start+page]; sheet=Image.new('RGB',(tile[0]*5,tile[1]*4),(27,30,36)); d=ImageDraw.Draw(sheet)
        for n,row in enumerate(chunk):
            x=(n%5)*tile[0]; y=(n//5)*tile[1]; sheet.paste(draw_polys(row['polys'],240),(x,y)); label=f"{row['source']}\n{row['model'].split('/')[-1]}"; d.multiline_text((x+4,y+242),label,fill='white',font=font)
            inv.append({k:row[k] for k in ('source','state','model','bounds')})
        path=OUT/f'page-{start//page+1:03d}.png'; sheet.save(path)
    print(json.dumps({'pages':(len(rows)+page-1)//page,'models':len(rows),'out':str(OUT)},ensure_ascii=False))
if __name__=='__main__': main()
