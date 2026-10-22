"""Negative visual controls for the black patches found in the rejected preview."""
import json
from pathlib import Path
import numpy as np
from PIL import Image

OUT=Path(__file__).absolute().parents[3]/'research/city-materials-20261019'


def pixels(case,key):return np.asarray(Image.open(OUT/case/'screenshots'/f'{case}-{key}.png').convert('RGB')).astype(np.int16)


def main():
    rows=[]
    for key in ['facade','tower','brick']:
        base=pixels('compare-base',key);preview=pixels('compare-candidate',key);final=pixels('compare-final2',key)
        was_visible=base.mean(axis=2)>25
        preview_black=was_visible&(preview.max(axis=2)<12)
        final_black=was_visible&(final.max(axis=2)<12)
        rows.append({'view':key,'newBlackPixelsRejectedPreview':int(preview_black.sum()),
            'newBlackPixelsFinal':int(final_black.sum()),
            'previewRegressionPixelsStillBlack':int((preview_black&final_black).sum())})
    result={'method':'Same native camera/time/weather/settings. Negative mask: baseline mean RGB>25, replacement max RGB<12. Native PNGs are unchanged.',
        'views':rows,'status':'PASS_NO_NEW_BLACK_NEGATIVE_CONTROLS' if all(r['newBlackPixelsFinal']==0 for r in rows) else 'REQUIRES_PIXEL_REVIEW'}
    (OUT/'negative-visual-controls.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result))


if __name__=='__main__':main()
