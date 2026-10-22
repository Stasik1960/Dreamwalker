"""Compact actual-triangle evidence; screenshots themselves stay lossless."""
import argparse
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).absolute().parents[3]
OUT = ROOT / 'research/city-materials-20261019'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--case', required=True)
    args = ap.parse_args()
    folder = OUT / args.case
    cap = json.loads((folder / 'client-capture-output.json').read_text())
    result = {'status': cap['status'], 'jarSha256': cap['productionJarSha256'], 'frames': []}
    for frame in cap['frames']:
        png = folder / 'screenshots' / frame['fileName']
        image = Image.open(png).convert('RGB')
        row = {k: frame.get(k) for k in ['key', 'actualCamera', 'dayTime', 'ambientOcclusion', 'gamma', 'rainGradient', 'thunderGradient', 'terrainRenderComplete', 'lookedAt', 'lookedAtState']}
        row['png'] = str(png)
        row['probes'] = []
        probes = frame.get('pixelRayProbes', []) + ([frame['pixelRayProbe']] if 'pixelRayProbe' in frame else [])
        for probe in probes:
            hits = probe['visibleTriangleProbe']['nearestIntersections']
            visible = next((h for h in hits if not h.get('transparentAtBaseLevel')), None)
            p = {'pixel': probe['pixel'], 'screenRGB': list(image.getpixel(tuple(probe['pixel']))),
                 'collider': probe.get('block'), 'colliderState': probe.get('state'),
                 'rendererFailures': probe['visibleTriangleProbe']['rendererFailures'],
                 'transparentIntersections': sum(bool(h.get('transparentAtBaseLevel')) for h in hits)}
            if visible:
                p['visible'] = {k: visible.get(k) for k in ['root', 'state', 'models', 'staticSources', 'sprite', 'spriteLocalUv',
                    'nearestTextureTexels', 'quad', 'physicalOutwardCenterLight', 'physicalOutwardVertexLight', 'geometricNormal',
                    'actualRootOpacity', 'carrier', 'carrierOpaqueFullCube']}
                if p['visible'].get('quad'):
                    p['visible']['quad'].pop('bakedVertexData', None)
            row['probes'].append(p)
        result['frames'].append(row)
    (folder / 'summary.json').write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
