"""Joined exact source polygons of full-cube-base decoration candidates."""
import json
from PIL import Image,ImageDraw,ImageFont
from v125_map_index import ROOT,dump,sha
from v125_map_door_projections import SourceVisuals
from v124_visual_audit import render
DEST=ROOT/'docs/v12.5-city-review/assemblies'

def main():
    data=json.loads((DEST/'fullcube-ornament-supplement.json').read_text());keys={'CUBEDECOR-001','CUBEDECOR-004','CUBEDECOR-008','CUBEDECOR-002','CUBEDECOR-003','CUBEDECOR-035','CUBEDECOR-055','CUBEDECOR-062','CUBEDECOR-016','CUBEDECOR-019','CUBEDECOR-027','CUBEDECOR-034'}
    rows=[r for r in data['reportOnlyCandidates'] if r['key'] in keys];source=SourceVisuals();canvas=Image.new('RGBA',(1800,660),'#25302f');draw=ImageDraw.Draw(canvas);font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',12)
    for n,row in enumerate(rows):
        quads=[]
        for c in row['components']:
            for a in c['sourceApplications']:
                off=[c['relativePosition'][i]*16+a.get('offset',[0,0,0])[i] for i in range(3)];quads.extend(source.quads({'model':a['model'],'yaw':a.get('y',0),'pitch':a.get('x',0),'offset':off}))
        x=(n%6)*300;y=(n//6)*220;canvas.alpha_composite(Image.fromarray(render(source,quads,0,size=190)),(x+50,y+18));draw.text((x+5,y),row['key']+' '+ '+'.join(c['mainId'] for c in row['components']),font=font,fill='white');draw.text((x+5,y+207),'source count '+str(row['occurrences']),font=font,fill='white')
    file=DEST/'cube-base-decor-joined-source.png';canvas.convert('RGB').save(file);dump(DEST/'cube-base-decor-projection-evidence.json',{'status':'SOURCE_JOINED_POLYGON_PROJECTION_REVIEW_REQUIRED_NOT_NATIVE','fileName':str(file.relative_to(ROOT)),'sha256':sha(file),'rows':[{'key':r['key'],'mainIds':[c['mainId'] for c in r['components']],'sourceOccurrences':r['occurrences']} for r in rows]})

if __name__=='__main__':main()
