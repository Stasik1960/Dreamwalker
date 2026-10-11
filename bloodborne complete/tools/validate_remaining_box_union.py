"""Independently prove source-only box coalescing by exact coordinate slab coverage.

Input is gzip/plain JSONL: {id, variant, pose, field, before, after, descriptor?}.
No merge-generator code is imported; all positive-volume cuboids are compared as
closed unions by their complete interval coverage on every coordinate slab.
"""
from __future__ import annotations
import argparse
import gzip
import hashlib
import json
import math
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources/bloodborne_dw/composite'
DOC=ROOT/'docs/remaining-transfer'

def bounds(box):
    values=tuple(box['from'])+tuple(box['to'])
    if len(values)!=6 or not all(isinstance(n,(int,float)) and math.isfinite(n) for n in values):raise ValueError('Invalid finite cuboid coordinates')
    if not all(values[i]<values[i+3] for i in range(3)):raise ValueError('Degenerate cuboid changes require a separate surface contract')
    return values

def merged(intervals):
    result=[]
    for low,high in sorted(intervals):
        if result and low<=result[-1][1]:result[-1]=(result[-1][0],max(high,result[-1][1]))
        else:result.append((low,high))
    return tuple(result)

def equal_rectangles(left,right):
    events={}
    for which,rows in enumerate((left,right)):
        for index,row in enumerate(rows):
            y0,y1,z0,z1=row
            events.setdefault(y0,[]).append((which,index,(z0,z1),True))
            events.setdefault(y1,[]).append((which,index,(z0,z1),False))
    active=({},{});coordinates=sorted(events)
    for coordinate in coordinates[:-1]:
        for which,index,interval,starts in events[coordinate]:
            if starts:active[which][index]=interval
            else:active[which].pop(index)
        if merged(active[0].values())!=merged(active[1].values()):return False,coordinate
    return True,None

def equal_unions(before,after):
    events={}
    for which,rows in enumerate((before,after)):
        for index,row in enumerate(rows):
            x0,y0,z0,x1,y1,z1=bounds(row);rectangle=(y0,y1,z0,z1)
            events.setdefault(x0,[]).append((which,index,rectangle,True))
            events.setdefault(x1,[]).append((which,index,rectangle,False))
    active=({},{});coordinates=sorted(events)
    for coordinate in coordinates[:-1]:
        for which,index,rectangle,starts in events[coordinate]:
            if starts:active[which][index]=rectangle
            else:active[which].pop(index)
        matches,y=equal_rectangles(tuple(active[0].values()),tuple(active[1].values()))
        if not matches:return False,{'xSlabStarts':coordinate,'ySlabStarts':y}
    return True,None

def self_check():
    box=lambda a,b:{'from':a,'to':b}
    whole=[box([0,0,0],[2,2,2])]
    split=[box([0,0,0],[1,2,2]),box([1,0,0],[2,2,2])]
    assert equal_unions(whole,split)[0]
    assert equal_unions(whole,split+split)[0]
    assert not equal_unions(whole,[box([0,0,0],[1,2,2])])[0]
    assert not equal_unions(whole,[box([0,0,0],[2,2,1.999999999])])[0]
    assert equal_unions([],[])[0]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--snapshots',type=Path,required=True)
    parser.add_argument('--report',type=Path,default=DOC/'box-union-validation.json')
    args=parser.parse_args();self_check()
    failures=[];cases=0;before_count=0;after_count=0;descriptors={};seen=set()
    opener=gzip.open if args.snapshots.suffix=='.gz' else open
    with opener(args.snapshots,'rt',encoding='utf-8') as stream:
        for line in stream:
            if not line.strip():continue
            row=json.loads(line);case=(row['id'],row['variant'],row['pose'],row['field'])
            cases+=1;before_count+=len(row['before']);after_count+=len(row['after'])
            try:
                if case in seen:raise ValueError('Duplicate union case')
                seen.add(case)
                if not row['id'].startswith('bloodborne_dw:source_') or row['field'] not in ('selection','collision'):raise ValueError('Coalescing scope exceeds new source boxes')
                path=RES/(row['id'].split(':',1)[1]+'.json')
                if path not in descriptors:descriptors[path]=json.loads(path.read_text(encoding='utf-8'))
                current=descriptors[path]['variants'][row['variant']]['poses'][row['pose']][row['field']]
                if current!=row['after']:raise ValueError('Final descriptor boxes do not match the verified target snapshot')
                same,where=equal_unions(row['before'],row['after'])
                if not same:raise ValueError('Exact union coverage changed: '+json.dumps(where))
            except Exception as error:failures.append({'case':list(case),'error':str(error)})
    hashes={str(path.relative_to(ROOT)):hashlib.sha256(path.read_bytes()).hexdigest() for path in descriptors}
    result={'schema':'dreamwalker-independent-box-union-validation-v1','status':'FAIL' if failures else 'PASS','algorithm':'Exact X coordinate slabs; independent Y sweep and merged Z interval union; no tolerance, volume estimate or generator import.',
            'snapshotsSha256':hashlib.sha256(args.snapshots.read_bytes()).hexdigest(),'cases':cases,'beforeBoxes':before_count,'afterBoxes':after_count,'descriptorHashes':hashes,'failures':failures}
    if not cases:result['status']='FAIL';result['failures'].append({'error':'No recorded union cases'})
    args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({key:result[key] for key in ('status','cases','beforeBoxes','afterBoxes')}))
    if result['status']!='PASS':raise SystemExit(1)

if __name__=='__main__':main()
