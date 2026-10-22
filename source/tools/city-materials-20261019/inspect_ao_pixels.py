"""Measure the extra visual suspicion; AO-on is a branch control, not a release pair."""
import json
from pathlib import Path
from PIL import Image

out=Path(__file__).absolute().parents[3]/'research/city-materials-20261019'
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
frames={case:read(out/case/'client-capture-output.json')['frames'][0] for case in ['compare-final','final-ao-on']}
for key in ['actualCamera','dayTime','gamma','rainGradient','thunderGradient']:
    assert frames['compare-final'][key]==frames['final-ao-on'][key],key
images={case:Image.open(out/case/'screenshots'/frame['fileName']).convert('RGB') for case,frame in frames.items()}
points=read(Path(__file__).with_name('remaining-trim-frames.json'))[0]['probePixels']
rows=[{'pixel':pixel,'flatRGB':list(images['compare-final'].getpixel(tuple(pixel))),
    'nativeAoRGB':list(images['final-ao-on'].getpixel(tuple(pixel)))} for pixel in points]
result={'status':'SUSPICION_REQUIRES_VISIBLE_TRIANGLE_AND_SAVED_LIGHT_PROOF','pixels':rows}
(out/'ao-suspicion.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))
