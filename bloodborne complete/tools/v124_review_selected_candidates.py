"""Render selected non-masonry candidates from the preserved source descriptors."""
import json,sys,traceback
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
from v124_visual_audit import OUT,ROOT,Visuals,pose_data,render
from v124_full_visual_coverage import composite

def main():
    selected=list(range(15))+[21,24,25,27,30,33,35,37,43,53,63,71,72,83,92,98,99,130,132,151,534,673,683,815,816,824,827,835,849,872,888,889,892,901,905]
    pairs=json.loads((OUT/'additional-global-candidates.json').read_text(encoding='utf-8'))
    frozen=json.loads((OUT/'baseline-content.json').read_text(encoding='utf-8'))['descriptors']
    visuals=Visuals(vanilla_jar=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
    for page,start in enumerate(range(0,len(selected),18)):
        canvas=Image.new('RGB',(1024,18*110),(39,48,55));draw=ImageDraw.Draw(canvas)
        for row,index in enumerate(selected[start:start+18]):
            p=pairs[index]
            if p.get('diagnosticImage'):canvas.paste(Image.open(ROOT/p['diagnosticImage']).convert('RGB'),(0,row*110))
            else:
                for col,key in enumerate(['a','b']):
                    s=p[key];d=frozen[s['registryId'].split(':')[1]];quads=pose_data(visuals,d,d['variants'][s['variant']])
                    for yaw in range(8):canvas.paste(Image.fromarray(composite(render(visuals,quads,s['canonicalYaw']+yaw*45))),(yaw*128+col*64,row*110+8))
            draw.text((4,row*110+80),f"candidate {index:03}: {p['a']['id']} / {p['b']['id']}, diff {p['difference']:.5f}",fill='yellow')
        canvas.save(OUT/f'selected-full-review-{page:02}.png')

if __name__=='__main__':
    with (OUT/'selected-full-review-console.txt').open('w',encoding='utf-8') as s:
        sys.stdout=sys.stderr=s
        try:main()
        except BaseException:traceback.print_exc();raise
