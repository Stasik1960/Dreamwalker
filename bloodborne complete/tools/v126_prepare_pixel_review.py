"""Index raw native PNGs and create labelled contact sheets for visual review.

Contact sheets are QA aids. Original ScreenshotRecorder files stay byte exact;
this script never records a visual PASS on behalf of an observer.
"""
import argparse, hashlib, json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path): return json.loads(path.read_text(encoding='utf-8-sig'))
def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf8')

def main():
    p=argparse.ArgumentParser(); p.add_argument('--profile',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True); a=p.parse_args()
    profile=read(a.profile); run=Path(profile['run_directory'])
    capture=read(run/'client-capture-output.json'); assert capture['status']=='CAPTURE_COMPLETE'
    assert capture['productionJarSha256']==profile['artifact_sha256']==sha(Path(profile['artifact']))
    assert not a.output.exists(); a.output.mkdir(parents=True)
    frames=[]
    for n,row in enumerate(capture['frames']):
        path=run/'screenshots'/row['fileName']; assert sha(path)==row['sha256']
        assert row['status']=='PASS' and row['renderedCameraVerified']
        frames.append({'index':n,'path':str(path.resolve()),'fileName':row['fileName'],
                       'key':row['key'],'name':row['name'],'section':row['section'],
                       'sha256':row['sha256'],'status':'PENDING_OBSERVED_PIXELS'})
    font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',15)
    sheets=[]
    for first in range(0,len(frames),12):
        subset=frames[first:first+12]; canvas=Image.new('RGB',(1440,1200),(25,25,25)); draw=ImageDraw.Draw(canvas)
        for i,row in enumerate(subset):
            x=(i%3)*480; y=(i//3)*300
            with Image.open(row['path']) as im: canvas.paste(im.resize((480,270)),(x,y))
            draw.text((x+3,y+272),str(row['index'])+' · '+row['key'][:48],font=font,fill='white')
            draw.text((x+3,y+287),row['name'][:48],font=font,fill='white')
        path=a.output/f'contact-{first:03d}-{first+len(subset)-1:03d}.png'; canvas.save(path)
        sheets.append({'path':str(path.resolve()),'indices':[r['index'] for r in subset]})
    result={'status':'PASS_INDEX_NATIVE_IMAGES_VISUAL_REVIEW_PENDING',
            'productionJarSha256':capture['productionJarSha256'],'captureSha256':sha(run/'client-capture-output.json'),
            'clientProfile':str(a.profile.resolve()),'frames':frames,'sheets':sheets}
    write(a.output/'index.json',result); print(json.dumps({'status':result['status'],'frames':len(frames),'sheets':len(sheets)}))

if __name__=='__main__': main()
