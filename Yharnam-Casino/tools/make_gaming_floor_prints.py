"""Generate the small pixel-art textures used on the three gaming-floor props."""
from math import cos, pi, sin
from pathlib import Path
from random import Random
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[1] / "build/gaming-floor-textures"
OUT.mkdir(parents=True, exist_ok=True)

# Five-pixel-high sign painter's alphabet. Rows are five-bit masks.
LETTERS = {
    "Л":(7,9,9,9,17),"Т":(31,4,4,4,4),"П":(31,17,17,17,17),"О":(14,17,17,17,14),"К":(17,18,28,18,17),
    "Е":(31,16,30,16,31),"Р":(30,17,30,16,16),"Ч":(17,17,15,1,1),
    "Ё":(10,0,31,16,30,16,31),"Н":(17,17,31,17,17),"Ы":(17,17,29,21,29),
    "Й":(10,4,17,19,21,25,17),"И":(17,19,21,25,17),"В":(30,17,30,17,30),
    "А":(14,17,31,17,17)," ":(),
    "0":(14,19,21,25,14),"1":(4,12,4,4,14),"2":(14,17,2,8,31),
    "3":(30,1,14,1,30),"4":(18,18,31,2,2),"5":(31,16,30,1,30),
    "6":(14,16,30,17,14),"7":(31,2,4,8,8),"8":(14,17,14,17,14),
    "9":(14,17,15,1,14),"A":(14,17,31,17,17),"K":(17,18,28,18,17),
}

def lettering(draw,x,y,text,ink):
    x=round(x-(6*len(text)-1)/2)
    for letter in text:
        rows=LETTERS[letter]
        for j,row in enumerate(rows):
            for i in range(5):
                if row & (16>>i):
                    draw.point((x+i,round(y)+j+5-len(rows)),fill=ink)
        x+=6

def weave(w,h,palette,seed):
    rng=Random(seed)
    img=Image.new("RGB",(w,h),palette[0])
    draw=ImageDraw.Draw(img)
    for _ in range(w*h//7):
        x,y=rng.randrange(w),rng.randrange(h)
        draw.line((x,y,x+rng.randrange(1,3),y),fill=rng.choice(palette))
    return img

def cloth(jack):
    palette=("#245656","#285958","#205353") if jack else ("#315E46","#346149","#2C5942")
    img=weave(256,144,palette,19)
    draw=ImageDraw.Draw(img)
    ivory="#DECBA0"
    gold="#A69968"
    def uv(x,y): return round(128+x/2.96*256),round(72-y/1.8*144)
    if jack:
        lettering(draw,*uv(0,.19),"ЧЁРНЫЙ ВАЛЕТ",ivory)
        x,y=uv(0,-.012)
        draw.line((x-25,y,x+25,y),fill=gold)
        draw.polygon([(x-2,y),(x,y-2),(x+2,y),(x,y+2)],fill=ivory)
        for i in range(7):
            a=(201+i*23)*pi/180
            x,y=uv(.90*cos(a),.23+.68*sin(a))
            draw.line([(x-6,y-6),(x+6,y-6),(x+8,y-4),(x+8,y+4),(x+6,y+6),
                       (x-6,y+6),(x-8,y+4),(x-8,y-4),(x-6,y-6)],fill=gold)
            lettering(draw,x,y-2,str(i+1),ivory)
    else:
        draw.line([(42,56),(50,48),(206,48),(214,56),(214,90),(206,98),(50,98),(42,90),(42,56)],fill="#788664")
        for i in range(5):
            draw.rectangle((*uv((i-2)*.155-.063,.082),*uv((i-2)*.155+.063,-.105)),outline=gold)
        lettering(draw,*uv(0,.222),"ПОКЕР",ivory)
        for s in (-1,1):
            x,y=uv(s*.13,-.23)
            draw.line((x,y,x+s*12,y),fill=gold)
        x,y=uv(0,-.23)
        draw.polygon([(x-3,y),(x,y-2),(x+3,y),(x,y+2)],fill=ivory)
    img.save(OUT/("jack-felt.png" if jack else "poker-felt.png"))

def wheel_face():
    img=Image.new("RGB",(192,192),"#3B3234")
    d=ImageDraw.Draw(img)
    def pt(r,a): return (round(95.5+r*sin(a)),round(95.5-r*cos(a)))
    count=16
    for i in range(count):
        a=2*pi*i/count
        l,r=a-pi/count,a+pi/count
        d.polygon([(96,96),pt(93,l),pt(94,a),pt(93,r)],fill=("#354A53","#823E45","#56705F","#493D48")[i%4])
        d.line([pt(24,l),pt(92,l)],fill="#B18E61")
        d.polygon([pt(30,a),pt(46,a-.045),pt(59,a),pt(46,a+.045)],fill="#AB8B61")
        text=Image.new("RGBA",(23,13))
        lettering(ImageDraw.Draw(text),11,4,str(i+1),"#EFDEBD")
        text=text.resize((46,26),Image.Resampling.NEAREST).rotate(-i*360/count,resample=Image.Resampling.NEAREST,expand=True)
        x,y=pt(73,a)
        img.paste(text,(x-text.width//2,y-text.height//2),text)
    for r,col in ((90,"#BDA178"),(26,"#BDA178"),(22,"#E0BA7C")):
        pts=[pt(r,(i+.5)*2*pi/count) for i in range(count)]
        d.line(pts+pts[:1],fill=col,width=1)
    d.polygon([pt(20 if i%2==0 else 7,i*pi/4) for i in range(8)],fill="#D1AE72")
    img.save(OUT/"wheel-face.png")

def cards():
    for name,rank,ink in (("ace","A","#303633"),("king","K","#983E43"),("nine","9","#983E43")):
        img=Image.new("RGB",(16,24),"#E6DAB9")
        d=ImageDraw.Draw(img)
        lettering(d,4,2,rank,ink)
        if name=="nine":
            for cx,cy in [(x,y) for x in (4,11) for y in (9,13,17,21)]+[(8,15)]:
                d.polygon([(cx,cy-1),(cx+1,cy),(cx,cy+1),(cx-1,cy)],fill=ink)
        elif name=="king":
            d.polygon([(3,11),(6,14),(8,10),(10,14),(13,11),(11,18),(5,18)],fill="#AF8855")
            d.polygon([(12,3),(14,6),(12,9),(10,6)],fill=ink)
        else:
            d.polygon([(8,9),(12,14),(11,16),(9,16),(8,15),(7,16),(5,16),(4,14)],fill=ink)
            d.rectangle((7,16,9,19),fill=ink)
        img.save(OUT/(name+".png"))

if __name__ == "__main__":
    wood=weave(32,16,("#604838","#644A39","#503A2F","#72523E"),33)
    draw=ImageDraw.Draw(wood)
    draw.line((2,3,12,3),fill="#76553F")
    draw.line((17,9,26,9),fill="#4C382F")
    wood.save(OUT/"walnut.png")
    cloth(False)
    cloth(True)
    wheel_face()
    cards()
