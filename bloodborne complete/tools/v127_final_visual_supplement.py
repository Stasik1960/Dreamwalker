"""Review new types and later exact source-part poses after the original pass."""
import json
import numpy as np
from PIL import Image,ImageDraw
from v127_visual_export import Art,ROOT,RES,OUT,VANILLA,inventory,spec_of,active_indices,quads_for,render,composite,BG,write_json

def main():
    art=Art(vanilla_jar=VANILLA);records=[]
    partial=json.loads((RES/'bloodborne_dw/imported-source-parts-v127.json').read_text(encoding='utf-8'))['parts']
    partial_poses={}
    for part in partial:partial_poses.setdefault(part['registryId'],set()).add(part['variant'])
    for row in inventory():
        extra=int(row['id'])>=93228
        if not extra and row['registry'] not in partial_poses and row['registry']!='bloodborne_dw:source_deepslate':continue
        spec=spec_of(row);indices=active_indices(spec) if extra or row['registry']=='bloodborne_dw:source_deepslate' else sorted({0}|partial_poses[row['registry']]);side=160
        sheet=Image.new('RGB',(side*4,180*len(indices)),tuple(BG));draw=ImageDraw.Draw(sheet)
        for n,index in enumerate(indices):
            quads=quads_for(art,spec,index)
            for yaw in range(4):sheet.paste(Image.fromarray(composite(render(art,quads,yaw*90,side))),(yaw*side,n*180+20))
            draw.text((5,n*180),f"{row['id']} pose {index} | {spec['displayName']}",fill=(238,242,245))
        path=OUT/'projections'/f"final-extra-{row['id']}.png";sheet.save(path)
        records.append({'id':row['id'],'registryId':row['registry'],'name':spec['displayName'],'poses':indices,'image':str(path.relative_to(OUT)),'scope':'NEW_RESERVED_TYPE' if extra else 'LATER_SOURCE_COMPATIBILITY_STATE','reviewStatus':'PENDING_VIEW'})
    write_json(OUT/'final-visual-supplement.json',{'method':'Current native source RGBA/UV transformed geometry; four yaw views of every active pose of newly reserved composite types and later source-part compatibility states. Source_deepslate includes its mirrored source suffixes. Software projection, native client QA tracked separately.','objects':records})
    print(json.dumps({'objects':len(records),'poses':sum(len(r['poses']) for r in records)}))

if __name__=='__main__':main()
