"""Prepare SHA-bound native client camera/root-identity checks from the new gallery."""
from __future__ import annotations
import argparse
from collections import defaultdict
import hashlib
import json
import math
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]

def diagnostic(row):return row.get('kind')=='diagnostic' or row['representation'].startswith('diagnostic_placeholder')
def architecture(row):return row.get('kind')=='architecture' or row['representation'] in ('ordinary_block_item','ordinary_native_block_item','native_composite_placement_transaction')

def camera(bounds):
    center=[(bounds[i]+bounds[i+3])/2 for i in range(3)]
    spans=[bounds[i+3]-bounds[i] for i in range(3)]
    distance=max(4.5,max(spans)*1.35)
    eye=[center[0]+distance*.25,center[1]+max(1.4,max(spans)*.22),center[2]-distance]
    delta=[center[i]-eye[i] for i in range(3)]
    yaw=math.degrees(math.atan2(-delta[0],delta[2]))
    pitch=-math.degrees(math.atan2(delta[1],math.hypot(delta[0],delta[2])))
    return [round(eye[0],4),round(eye[1]-1.62,4),round(eye[2],4),round(yaw,4),round(pitch,4)]

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--manifest',type=Path,required=True)
    parser.add_argument('--production-jar',type=Path,required=True)
    parser.add_argument('--disk-report',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--samples',type=int,default=24)
    args=parser.parse_args()
    output=args.output.resolve()
    assert output.is_relative_to((ROOT/'build').resolve()) and not output.exists()
    assert 8<=args.samples<=100
    manifest=json.loads(args.manifest.read_text(encoding='utf8'))
    disk=json.loads(args.disk_report.read_text(encoding='utf8'))
    digest=hashlib.sha256(args.production_jar.read_bytes()).hexdigest()
    assert manifest['productionJarSha256']==disk['productionJarSha256']==digest and disk['status']=='PASS'
    sections=defaultdict(list)
    for row in manifest['exhibits']:sections[row['section']].append(row)
    chosen=[];used=set()
    def add(row):
        if row['key'] not in used:chosen.append(row);used.add(row['key'])
    for rows in sections.values():
        add(rows[0]);add(rows[-1])
        placeable=[row for row in rows if not diagnostic(row)]
        if placeable:
            add(max(placeable,key=lambda row:max(row['bounds'][i+3]-row['bounds'][i] for i in range(3))))
    actual_states={row['key']:row.get('state',{}).get('properties',{}) for row in disk['stableExhibits']}
    placeable=[row for row in manifest['exhibits'] if not diagnostic(row)]
    # Cover old native blocks, entity objects, and several absent-on-map source objects.
    for prefix in ('bloodborne_dw:prototype_wall','bloodborne_dw:prototype_ladder','bloodborne_rp:'):
        matches=[row for row in placeable if row['registry'].startswith(prefix)]
        if matches:add(matches[0])
    for numeric in ('93155','93229','90225','90226','90042','90043'):
        matches=[row for row in placeable if row['id']==numeric]
        if matches:add(matches[0])
    for row in sorted(placeable,key=lambda row:max(row['bounds'][i+3]-row['bounds'][i] for i in range(3)),reverse=True):
        if len(chosen)>=args.samples:break
        add(row)
    frames=[]
    for index,row in enumerate(chosen):
        bounds=row.get('bounds')
        if not bounds or diagnostic(row):
            x,y,z=row['root']
            bounds=[x-1,y,z-1,x+2,y+2,z+2]
        view=camera(bounds)
        slug=re.sub(r'[^A-Za-z0-9_-]','_',row['key'])[:72]
        frame={'fileName':f'gallery-{index+1:02d}-{slug}.png','key':row['key'],'section':row['section'],
               'name':row['name'],'camera':view,'command':'tp @s '+' '.join(map(str,view)),'waitTicks':80,
               'rootChecks':[],'entityChecks':[]}
        if architecture(row):
            check={'root':row['root'],'registry':row['registry'],'uuid':row['uuid']}
            for key in ('variant','profile','open'):
                if key in actual_states[row['key']]:check[key]=actual_states[row['key']][key]
            frame['rootChecks'].append(check)
        elif row.get('uuid'):
            frame['entityChecks'].append({'registry':'minecraft:item_frame' if row['representation']=='item_frame' else row['registry'],'uuid':row['uuid']})
        frames.append(frame)
    result={'schema':'dreamwalker-flat-gallery-capture-input-v1','productionJarSha256':digest,
            'manifestSha256':hashlib.sha256(args.manifest.read_bytes()).hexdigest(),
            'quitAfterCapture':True,'frames':frames,'scope':'Every section, old native and entity examples, map-absent examples and largest exhibits. Full saved inventory is checked independently.'}
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

if __name__=='__main__':main()
