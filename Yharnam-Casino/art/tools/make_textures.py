from __future__ import annotations
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]; OUT=ROOT/'textures'; OUT.mkdir(parents=True,exist_ok=True)
THEMES={
 'hunters_wager':dict(title="Hunter's Wager",bg=(203,181,139),edge=(32,27,27),line=(126,39,31),accent=(174,37,31),symbols=['lantern','cleaver','vial','moon','rune','bell','eye','coin']),
 'astral_choir':dict(title='Astral Fairy',bg=(125,139,148),edge=(24,32,43),line=(32,111,126),accent=(40,105,183),symbols=['crescent','eye','cluster','observatory','star','orb','rune','bell']),
 'crimson_oath':dict(title='Crimson Guard',bg=(62,19,27),edge=(18,12,17),line=(154,41,51),accent=(218,160,121),symbols=['crown','chalice','rapier','rose','moon','gem','mask','coin']),
}
def font(size,family='georgiab.ttf'):
    for p in ('C:/Windows/Fonts/'+family,'C:/Windows/Fonts/timesbd.ttf','C:/Windows/Fonts/DejaVuSans-Bold.ttf'):
        try: return ImageFont.truetype(p,size)
        except OSError: pass
    return ImageFont.load_default(size=size)
def center(d,box,s,f,fill,stroke=0,outline=None):
    b=d.textbbox((0,0),s,font=f,stroke_width=stroke); x=(box[0]+box[2]-b[2]+b[0])/2; y=(box[1]+box[3]-b[3]+b[1])/2; d.text((x,y),s,font=f,fill=fill,stroke_width=stroke,stroke_fill=outline)
def star(d,x,y,r,c,o):
    pts=[]
    for i in range(10):
        a=-math.pi/2+i*math.pi/5; rr=r if i%2==0 else r*.42; pts.append((x+math.cos(a)*rr,y+math.sin(a)*rr))
    d.polygon(pts,fill=c,outline=o)
def icon(d,k,x,y,t):
    c,o,h=t['accent'],t['edge'],(220,204,169)
    if k=='lantern': d.rectangle((x-17,y-20,x+17,y+23),fill=c,outline=o,width=3); d.rectangle((x-25,y-28,x+25,y-20),fill=c,outline=o,width=2); d.rectangle((x-25,y+23,x+25,y+29),fill=c,outline=o,width=2); d.ellipse((x-8,y-8,x+8,y+10),fill=h,outline=o,width=2)
    elif k=='cleaver': d.polygon([(x-25,y-28),(x+25,y-14),(x+17,y+17),(x-8,y+28),(x-25,y+11)],fill=c,outline=o); d.line((x-4,y+7,x+24,y+31),fill=o,width=7)
    elif k=='vial': d.rounded_rectangle((x-15,y-17,x+15,y+27),8,fill=c,outline=o,width=3); d.rectangle((x-9,y-30,x+9,y-17),fill=c,outline=o,width=3); d.line((x-10,y-4,x+10,y-4),fill=h,width=3)
    elif k in ('moon','crescent'): d.ellipse((x-28,y-28,x+28,y+28),fill=c,outline=o,width=2); d.ellipse((x-10,y-32,x+31,y+12),fill=t['bg'],outline=t['bg'])
    elif k=='rune':
        d.line([(x,y-28),(x,y+26)],fill=c,width=6)
        for side in (-1,1):
            d.line([(x+side*19,y-19),(x+side*7,y-6),(x,y+3),
                    (x+side*13,y+21)],fill=c,width=5,joint='curve')
    elif k=='gem':
        d.polygon([(x,y-28),(x+27,y-9),(x+19,y+16),(x,y+28),(x-19,y+16),(x-27,y-9)],
                  fill=c,outline=o,width=2)
        d.polygon([(x,y-23),(x+21,y-8),(x,y+17),(x-20,y-8)],fill=h)
        d.line((x,y-24,x,y+25),fill=o,width=2)
    elif k=='eye':
        d.polygon([(x-34,y),(x-19,y-13),(x,y-18),(x+19,y-13),(x+34,y),
                   (x+19,y+13),(x,y+18),(x-19,y+13)],fill=c,outline=o,width=3)
        d.ellipse((x-11,y-14,x+11,y+14),fill=h,outline=o,width=2)
        d.ellipse((x-4,y-9,x+4,y+9),fill=o)
    elif k=='cluster':
        for dx,dy,r in [(-18,10,9),(0,-12,12),(20,8,8),(4,20,6)]: d.ellipse((x+dx-r,y+dy-r,x+dx+r,y+dy+r),fill=c,outline=o,width=2)
    elif k=='observatory': d.arc((x-30,y-10,x+30,y+42),180,360,fill=c,width=8); d.line((x-23,y+15,x+23,y+15),fill=c,width=6); d.line((x,y-22,x,y+15),fill=c,width=6); d.ellipse((x-8,y-30,x+8,y-14),fill=c,outline=o)
    elif k=='star': star(d,x,y,28,c,o)
    elif k=='orb': d.ellipse((x-27,y-27,x+27,y+27),fill=c,outline=o,width=3); d.arc((x-38,y-13,x+38,y+18),180,360,fill=h,width=3)
    elif k=='bell': d.pieslice((x-28,y-27,x+28,y+24),180,360,fill=c,outline=o); d.rectangle((x-33,y+12,x+33,y+22),fill=c,outline=o); d.ellipse((x-6,y+21,x+6,y+31),fill=c,outline=o)
    elif k=='crown': d.polygon([(x-31,y+22),(x-25,y-23),(x-7,y-4),(x,y-27),(x+8,y-4),(x+28,y-23),(x+32,y+22)],fill=c,outline=o); d.line((x-30,y+9,x+30,y+9),fill=o,width=3)
    elif k=='chalice': d.polygon([(x-25,y-24),(x+25,y-24),(x+12,y+4),(x+5,y+12),(x+5,y+26),(x+23,y+30),(x-23,y+30),(x-5,y+26),(x-5,y+12),(x-12,y+4)],fill=c,outline=o)
    elif k=='rapier': d.line((x-25,y+27,x+24,y-27),fill=c,width=6); d.line((x-31,y+13,x-12,y+31),fill=o,width=5); d.line((x-22,y+18,x-3,y+18),fill=c,width=4)
    elif k=='rose': d.ellipse((x-24,y-20,x+3,y+8),fill=c,outline=o); d.ellipse((x-3,y-20,x+24,y+8),fill=c,outline=o); d.ellipse((x-15,y-6,x+15,y+20),fill=c,outline=o); d.line((x,y+15,x-4,y+32),fill=(45,95,48),width=5)
    elif k=='mask': d.ellipse((x-25,y-30,x+25,y+30),fill=c,outline=o,width=3); d.ellipse((x-17,y-5,x-4,y+5),fill=t['bg']); d.ellipse((x+4,y-5,x+17,y+5),fill=t['bg']); d.line((x-13,y+18,x+13,y+18),fill=o,width=3)
    elif k=='coin': d.ellipse((x-28,y-28,x+28,y+28),fill=c,outline=o,width=4); center(d,(x-20,y-18,x+20,y+18),'✦',font(29),o)
def reel(name,t):
    background = {
        'hunters_wager': (194,168,123),
        'astral_choir': (31,43,61),
        'crimson_oath': (69,24,37),
    }[name]
    inks = {
        'hunters_wager': [(131,71,34),(63,59,56),(158,39,38),(118,81,53)],
        'astral_choir': [(183,214,225),(120,186,185),(158,178,228),(148,177,188)],
        'crimson_oath': [(225,191,149),(215,204,189),(189,201,208),(204,117,134)],
    }[name]
    border = inks[0]
    im = Image.new('RGB',(128,512),background)
    d = ImageDraw.Draw(im)
    for x in range(128):
        edge = (abs(x-63.5)/63.5)**2
        d.line((x,0,x,511),fill=tuple(round(c*(1-.32*edge)) for c in background))
    for x in (7,120):
        d.line((x,0,x,511),fill=border,width=1)
    for index, symbol in enumerate(t['symbols']):
        top = index*64
        d.line((7,top,120,top),fill=t['line'],width=1)
        for x,ptr in ((14,1),(113,-1)):
            d.line((x,top+30,x+ptr*5,top+32,x,top+34),fill=border)
        stamp_bg = background
        stamp = Image.new('RGB',(104,96),stamp_bg)
        ink = inks[index % len(inks)]
        icon(ImageDraw.Draw(stamp),symbol,52,48,{**t,'accent':ink,'bg':stamp_bg})
        mask = Image.new('L',stamp.size)
        mask.putdata([0 if rgb==stamp_bg else 255 for rgb in stamp.getdata()])
        bounds = mask.getbbox()
        assert bounds, symbol
        stamp = stamp.crop(bounds)
        mask = mask.crop(bounds)
        mark_height = 43
        mark_width = min(87,round(stamp.width/stamp.height*mark_height*1.48))
        size = mark_width,mark_height
        im.paste(stamp.resize(size,Image.Resampling.LANCZOS),
                 ((128-mark_width)//2,top+(64-mark_height)//2),
                 mask.resize(size,Image.Resampling.LANCZOS))
    im.save(OUT/f'{name}_reel.png')


def marquee(name,t):
    base, edge, ink = {
        'hunters_wager': ((41,26,25),(156,111,68),(231,198,145)),
        'astral_choir': ((21,31,48),(121,162,164),(213,227,227)),
        'crimson_oath': ((50,19,34),(146,138,156),(233,213,210)),
    }[name]
    im = Image.new('RGB',(256,96),base)
    d = ImageDraw.Draw(im)
    for y in range(96):
        tint = 1+.14*math.sin(y*math.pi/95)-.15*y/95
        d.line((0,y,255,y),fill=tuple(round(v*tint) for v in base))
    d.line([(4,14),(14,4),(241,4),(251,14),(251,81),(241,91),(14,91),(4,81),(4,14)],
           fill=edge,width=2)
    d.line((37,21,219,21),fill=edge)
    d.line((46,69,210,69),fill=edge)
    for x in (19,237):
        if name=='astral_choir':
            star(d,x,45,9,ink,edge)
        else:
            d.polygon([(x,35),(x+6,45),(x,55),(x-6,45)],fill=ink,outline=edge)
    for x in (12,243):
        for y in (12,82):
            d.ellipse((x-1,y-1,x+2,y+2),fill=ink)
    title = t['title'].upper()
    for size in range(29,11,-1):
        face = font(size,'timesbd.ttf')
        if d.textlength(title,font=face)<194:
            break
    center(d,(27,26,229,68),title,face,ink)
    im.save(OUT/f'{name}_marquee.png')


def main():
    for name, theme in THEMES.items():
        reel(name,theme)
        marquee(name,theme)
    symbols = {name: theme['symbols'] for name,theme in THEMES.items()}
    (OUT/'reel_symbols.json').write_text(json.dumps(symbols,indent=2)+'\n',encoding='utf8')


if __name__=='__main__':
    main()
