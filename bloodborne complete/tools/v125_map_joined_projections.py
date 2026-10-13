"""Textured joined-source polygons for construction/door candidate selection."""
import json,math
from PIL import Image,ImageDraw,ImageFont
import numpy as np
from v125_map_index import ROOT,BASELINE,dump,sha
from v125_map_door_projections import SourceVisuals
from v124_visual_audit import Visuals,render,rot
from v125_map_resolve import rotation
from pathlib import Path
BASE=ROOT/'docs/v12.5-city-review'

def main():
    source=SourceVisuals();native=Visuals(vanilla_jar=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'));font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',12);rows=[];sheets=[]
    for section,file in [('assemblies','gallery-preview-meaningful-assemblies.json'),('doors','gallery-door-preview-platforms.json')]:
        data=json.loads((BASE/section/file).read_text(encoding='utf-8-sig'));canvas=Image.new('RGBA',(1800,220*math.ceil(len(data['platforms'])/6)),'#25302f');draw=ImageDraw.Draw(canvas)
        for no,platform in enumerate(data['platforms']):
            quads=[]
            for c in platform['components']:
                rel=[v*16 for v in c['relativePosition']]
                if c.get('sourceApplications'):
                    for a in c['sourceApplications']:
                        off=[rel[i]+a.get('offset',[0,0,0])[i] for i in range(3)]
                        quads.extend(source.quads({'model':a['model'],'offset':off,'yaw':a.get('y',0),'pitch':a.get('x',0)}))
                else:
                    spec=json.loads((ROOT/'src/architecture/resources/bloodborne_dw/composite'/f'{c["registryId"].split(":",1)[1]}.json').read_text());parts=spec['variants'][int(c['properties'].get('variant','0'))]['poses']['closed']['parts'];yaw=int(c['properties'].get('rotation','0'));shift=rotation(c.get('sourceShift',[0,0,0]),yaw)
                    for p in parts:
                        for q in native.quads(p):quads.append((rot(q[0],1,-yaw*45,[8,8,8])+np.array(rel)+np.array(shift)*16,*q[1:]))
            x=(no%6)*300;y=(no//6)*220
            canvas.alpha_composite(Image.fromarray(render(source if all(c.get('sourceApplications') for c in platform['components']) else native,quads,0,size=190)),(x+50,y+18))
            ids='+'.join(c['mainId'] for c in platform['components']);draw.text((x+5,y),f'{no+1:02d} '+ids[:40],font=font,fill='white');draw.text((x+5,y+207),platform['key'][:44],font=font,fill='white')
            rows.append({'section':section,'number':no+1,'key':platform['key'],'mainIds':[c['mainId'] for c in platform['components']],'sourceQuads':len(quads),'sourceProjection':'JOINED_ACTUAL_COMPONENT_SOURCE_POLYGONS','nativeGameReview':'NOT_RUN','fileName':section+'-joined-source-sheet.png'})
        out=BASE/section/(section+'-joined-source-sheet.png');canvas.convert('RGB').save(out);sheets.append({'section':section,'fileName':str(out.relative_to(ROOT)),'sha256':sha(out),'platforms':len(data['platforms'])})
    dump(BASE/'assemblies/gallery-source-preview-projections.json',{'schemaVersion':1,'status':'JOINED_SOURCE_PROJECTIONS_FOR_MANUAL_REVIEW_NOT_NATIVE_GAME','baselineCommit':BASELINE,'sheets':sheets,'rows':rows,'geometryPolicy':'Exactoriginalsourcepolygonswithactualrelativecelloffsets, noindividualpartseating. Normalizedimagefit only. Original90001usesunchangedcurrentV12modelreference.'})

if __name__=='__main__':main()
