"""Lossless native before/after contact sheets; no gamma or colour correction."""
import json
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont

OUT=Path(__file__).absolute().parents[3]/'research/city-materials-20261019'


def main():
    folder=OUT/'comparisons';folder.mkdir(exist_ok=True)
    font=ImageFont.truetype(r'C:\Windows\Fonts\arial.ttf',24)
    for key in ['facade','tower','brick']:
        before=Image.open(OUT/'compare-base/screenshots'/f'compare-base-{key}.png').convert('RGB')
        after=Image.open(OUT/'compare-final2/screenshots'/f'compare-final2-{key}.png').convert('RGB')
        assert before.size==after.size
        width,height=before.size
        sheet=Image.new('RGB',(width*2,height+44),(235,235,235));draw=ImageDraw.Draw(sheet)
        draw.text((12,8),'streaming.5 — до',fill=(0,0,0),font=font)
        draw.text((width+12,8),'materials.1 — после',fill=(0,0,0),font=font)
        sheet.paste(before,(0,44));sheet.paste(after,(width,44))
        sheet.save(folder/(key+'-before-after.png'))
        if key=='facade':
            rect=(560,0,930,720)
            a,b=before.crop(rect),after.crop(rect)
            detail=Image.new('RGB',(a.width*2,a.height+44),(235,235,235));draw=ImageDraw.Draw(detail)
            draw.text((8,8),'streaming.5',fill=(0,0,0),font=font)
            draw.text((a.width+8,8),'materials.1',fill=(0,0,0),font=font)
            detail.paste(a,(0,44));detail.paste(b,(a.width,44));detail.save(folder/'facade-detail-before-after.png')


if __name__=='__main__':main()
