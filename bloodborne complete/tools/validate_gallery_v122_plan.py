"""Read-only layout proof using every architecture model in a prepared marker."""
from __future__ import annotations
import argparse
from collections import Counter,defaultdict
import json
import math
import hashlib
from pathlib import Path
import sys
import zipfile
from verify_gallery_v122 import grid

def main():
    p=argparse.ArgumentParser();p.add_argument('--input',type=Path,required=True);p.add_argument('--report',type=Path,required=True);a=p.parse_args()
    marker=json.loads(a.input.read_text(encoding='utf8'));assert marker['gallerySchema']=='dreamwalker-flat-gallery-v122'
    jar=Path(marker['catalogue']['productionJar'])
    assert hashlib.sha256(jar.read_bytes()).hexdigest()==marker['productionJarSha256']
    with zipfile.ZipFile(jar) as archive:
        composites={name for name in archive.namelist() if name.startswith('bloodborne_dw/composite/') and name.endswith('.json')}
    groups=defaultdict(list)
    for row in marker['catalogue']['exhibits']:
        if row['kind']!='architecture':continue
        bounds=row['localVisualBounds'];w=math.ceil(bounds[3]-bounds[0]-1e-8)+2;d=math.ceil(bounds[5]-bounds[2]-1e-8)+2
        groups[(row['section'],w,d)].append(row)
    cursor=0;rows=[]
    for (section,w,d),values in groups.items():
        cols=min(16,len(values));count=(len(values)+cols-1)//cols
        for i,row in enumerate(values):
            bounds=row['localVisualBounds'];r,c=divmod(i,cols);x,z=c*(w+2),cursor+r*(d+2)
            origin=[x+1+((w-2)-(bounds[3]-bounds[0]))/2-bounds[0],64+row.get('surfaceClearance',0)-bounds[1],z+1+((d-2)-(bounds[5]-bounds[2]))/2-bounds[2]]
            root=[min(x+w-1,max(x,math.floor(origin[0]))),64,min(z+d-1,max(z,math.floor(origin[2])))]
            if 'bloodborne_dw/composite/'+row['registry'].split(':')[1]+'.json' not in composites:
                root=[math.floor(origin[0]+.5),64,math.floor(origin[2]+.5)]
                origin[0],origin[2]=root[0],root[2]
            world=[bounds[j]+origin[j] for j in range(3)]+[bounds[j+3]+origin[j] for j in range(3)]
            corners=[[x,64,z],[x+w-1,64,z],[x,64,z+d-1],[x+w-1,64,z+d-1]]
            choices=[corner for corner in corners if corner!=root]
            assert choices and (choices[0][0]+1<=world[0]+1e-6 or choices[0][0]>=world[3]-1e-6 or choices[0][2]+1<=world[2]+1e-6 or choices[0][2]>=world[5]-1e-6), 'Planned first label corner overlaps rendered art: '+row['key']
            rows.append({**row,'gridId':f'{section}/{w}x{d}','row':r,'column':c,'platform':[x,z,w,d],'bounds':world,'root':root,'labelPos':choices[0],'renderedBottomOnPlatform':True})
        cursor+=count*d+(count-1)*2+2
    proof=grid(rows)
    result={'status':'PASS','scope':'Every retained architecture offered variant, exact model points; RP and item-frame bounds validated at runtime and again from saved production model/NBT',
            'productionJarSha256':marker['productionJarSha256'],'architectureVariants':len(rows),'removedRedStations':len(marker['removedRedExhibits']),
            'sectionCounts':dict(Counter(row['section'] for row in rows)),
            'planarAntiZFightClearance':1/1024,'planarExhibits':[row['key'] for row in rows if row.get('surfaceClearance',0)>0],
            'layout':proof}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

if __name__=='__main__':
    try:main()
    except Exception as failure:
        if '--report' in sys.argv:
            report=Path(sys.argv[sys.argv.index('--report')+1]);report.parent.mkdir(parents=True,exist_ok=True)
            report.write_text(json.dumps({'status':'FAIL','failure':repr(failure)},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
        raise
