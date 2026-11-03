"""Verify a completed capture and arrange unchanged screenshots for visual review."""
import argparse, hashlib, json
from pathlib import Path
from PIL import Image, ImageDraw


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--run', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    args = ap.parse_args()
    complete = json.loads((args.run/'evidence/complete.json').read_text())
    rows = json.loads((args.run/'evidence/captures.json').read_text())
    assert complete['status'] == 'CAPTURED' and complete['count'] == len(rows) == 32
    keys = {(r['corrected'], r['night'], r['camera']) for r in rows}
    assert keys == {(fixed, night, c) for fixed in (False, True) for night in (False, True) for c in range(1,9)}
    targets = [(44,87), (36,51), (36,53), (143,80), (36,53), (97,14), (44,10), (127,121)]
    lock = json.loads((args.run/'lock.json').read_text())
    assert complete['alt'] == (lock['mode'] == 'alt')
    for r in rows:
        assert r['clientTimeOfDay'] % 24000 == (18000 if r['night'] else 6000)
        assert abs(r['clientSkyAngle'] - (0.5 if r['night'] else 0.0)) < 0.01
        bank,part = targets[r['camera']-1]
        assert r['targetState'] == f'Block{{bloodborne_dw:static_architecture_{bank}}}[part={part},waterlogged=false]'
        assert r['alt'] == complete['alt'] and r['pngWriteVerified'] and r['terrainComplete']
        assert r['chunks']['missingChunks'] == 0 and r['nativeIdleStableSeconds'] >= 2
    lookup = {(r['corrected'], r['night'], r['camera']):r for r in rows}
    args.output.mkdir(parents=True, exist_ok=True)
    mode = 'alt' if complete['alt'] else 'original'
    sheets = []
    for night in (False,True):
        for first in (1,5):
            sheet = Image.new('RGB', (1280, 4*386), '#202020')
            draw = ImageDraw.Draw(sheet)
            for line,camera in enumerate(range(first,first+4)):
                for column,fixed in enumerate((False,True)):
                    row=lookup[fixed,night,camera]
                    with Image.open(args.run/'screenshots'/row['screenshot']) as im:
                        assert im.size == (1280,720)
                        # Central 1000x562 region; originals are retained alongside.
                        crop=im.convert('RGB').crop((140,79,1140,641)).resize((640,360),Image.Resampling.LANCZOS)
                    sheet.paste(crop,(640*column,386*line+26))
                    draw.text((640*column+8,386*line+6),f"{mode} camera {camera} {'night' if night else 'day'} {'FIXED' if fixed else 'REFERENCE'}", fill='white')
            name=f"{mode}-{'night' if night else 'day'}-{first}-{first+3}.jpg"
            sheet.save(args.output/name,quality=93)
            sheets.append(name)
    report={'status':'CAPTURE_VALIDATED', 'mode':mode, 'count':len(rows), 'sheets':sheets,
            'requestedTargetsVerified':True, 'clientTimeAndSkyAngleVerified':True,
            'screenshots':{r['screenshot']:hashlib.sha256((args.run/'screenshots'/r['screenshot']).read_bytes()).hexdigest() for r in rows},
            'note':'Readiness, client time and file integrity verified. Visual correctness requires inspecting screenshots.'}
    (args.output/f'{mode}-capture-verification.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({'status':report['status'],'mode':mode,'count':len(rows),'sheets':sheets}))


if __name__=='__main__':
    main()
