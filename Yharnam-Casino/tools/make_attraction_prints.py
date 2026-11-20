"""Generate the single-zero roulette and clockwork rat derby pixel prints."""
from math import cos, pi, sin
from PIL import Image, ImageDraw
from make_gaming_floor_prints import LETTERS, OUT, weave

# Clockwise around the wheel, beginning at the centre of the green pocket.
ORDER = (0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,
         5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26)
RED = set(ORDER[1::2])
INK, WINE, ZERO = "#25292c", "#914849", "#346852"
RULE, IVORY = "#bca77b", "#eddfba"
GLYPHS = {**LETTERS, "У": (17,17,10,4,8), "Л": (7,9,9,9,17),
          ":":(0,4,0,4,0), "–":(0,0,14,0,0)}

def stamp(text, ink=IVORY):
    img = Image.new("RGBA", (len(text)*6-1,7))
    d = ImageDraw.Draw(img)
    for i, ch in enumerate(text):
        glyph = GLYPHS[ch]
        for y, row in enumerate(glyph):
            for x in range(5):
                if row & (16 >> x):
                    d.point((i*6+x,y+7-len(glyph)),fill=ink)
    return img

def print_at(image, xy, text, ink=IVORY, factor=1, *, from_end=False):
    sign = stamp(text,ink)
    if factor != 1:
        sign = sign.resize((sign.width*factor,sign.height*factor),Image.Resampling.NEAREST)
    if from_end:
        # The player at the 2:1 end looks along -X, towards zero.
        sign = sign.transpose(Image.Transpose.ROTATE_90)
    image.paste(sign,(round(xy[0]-sign.width/2),round(xy[1]-sign.height/2)),sign)

def layout():
    image=weave(512,256,("#315649","#35594b","#2e5145"),68)
    draw=ImageDraw.Draw(image)
    def px(x,y):
        return (round(256+128*x),round(128-128*y))
    def box(x0,y0,x1,y1,fill=None):
        draw.rectangle((px(x0,y1),px(x1,y0)),fill=fill,outline=RULE)
    x0, step, y0, dy = -.19, .141, -.142, .207
    end = x0+12*step
    # The apex of the zero touches the wheel end of the layout.
    shape = [px(x0,y0),px(x0,y0+3*dy),px(x0-.108,y0+3*dy),px(x0-.205,y0+1.5*dy),px(x0-.108,y0)]
    draw.polygon(shape,fill=ZERO,outline=RULE)
    print_at(image,px(x0-.085,y0+1.5*dy),"0",from_end=True)
    for column in range(12):
        for row in range(3):
            number=column*3+row+1
            x,y=x0+column*step,y0+row*dy
            box(x,y,x+step,y+dy,WINE if number in RED else INK)
            print_at(image,px(x+step/2,y+dy/2),str(number),from_end=True)
    for row in range(3):
        y=y0+row*dy
        box(end,y,end+.147,y+dy)
        print_at(image,px(end+.074,y+dy/2),"2:1",from_end=True)
    for i,txt in enumerate(("1–12","13–24","25–36")):
        x=x0+4*i*step
        box(x,-.315,x+4*step,y0)
        print_at(image,px(x+2*step,-.225),txt)
    for i,txt in enumerate(("1–18","ЧЁТ",None,None,"НЕЧЁТ","19–36")):
        x=x0+2*i*step
        box(x,-.505,x+2*step,-.315)
        mid=x+step
        if txt:
            print_at(image,px(mid,-.407),txt)
        else:
            draw.polygon([px(mid-.077,-.409),px(mid,-.358),px(mid+.077,-.409),px(mid,-.46)],
                         fill=WINE if i==2 else INK,outline=RULE)
    # A small sign-painter's cartouche, the same alphabet used on the card tables.
    print_at(image,px(.66,.597),"РУЛЕТКА")
    for sign in (-1,1):
        draw.line((px(.66+sign*.24,.586),px(.66+sign*.50,.586)), fill=RULE)
        mx=.66+sign*.52
        draw.polygon([px(mx-.026,.586),px(mx,.604),px(mx+.026,.586),px(mx,.568)],fill=RULE)
    image.save(OUT/"roulette-layout.png")

def number_ring():
    size=384
    img=Image.new("RGB",(size,size),INK);draw=ImageDraw.Draw(img)
    def point(r,angle):
        return round(size*.5+r*sin(angle)),round(size*.5-r*cos(angle))
    half=pi/len(ORDER)
    for i,n in enumerate(ORDER):
        a=i*2*half
        draw.polygon([point(r,t) for r,t in [(192,a-half),(192,a+half),(110,a+half),(110,a-half)]],
                     fill=ZERO if n==0 else WINE if n in RED else INK)
        draw.line((point(121,a-half),point(192,a-half)),fill="#a18b6e",width=1)
        label=stamp(str(n))
        label=label.resize((label.width*2,label.height*2),Image.Resampling.NEAREST)
        label=label.rotate(-i*360/len(ORDER),resample=Image.Resampling.NEAREST,expand=True)
        x,y=point(167,a)
        img.paste(label,(round(x-label.width/2),round(y-label.height/2)),label)
    img.save(OUT/"roulette-numbers.png")

GLYPHS.update({"Б":(31,16,30,17,30),"С":(15,16,16,16,15),"Г":(31,16,16,16,16),
               "Ш":(21,21,21,21,31),"Ф":(4,14,21,14,4),"М":(17,27,21,17,17)})
LANES=(-.350,-.046,.258,.562)
ENAMELS=("#86414a","#416676","#777147","#806040")
IVORY="#e9d3aa"
GOLD="#a18b63"

def raceway():
    img=weave(512,256,("#293a39","#2d3d3c","#283837"),91)
    draw=ImageDraw.Draw(img)
    def uv(x,y):return round(256+x*128),round(128-y*128)
    def rect(x0,y0,x1,y1,**kwargs):draw.rectangle([uv(x0,y1),uv(x1,y0)],**kwargs)
    def line(x0,y0,x1,y1,col):draw.line([uv(x0,y0),uv(x1,y1)],fill=col)
    for i,y in enumerate(LANES):
        top,bottom=y+.142,y-.142
        rect(-1.62,bottom,1.57,top,fill=("#43534c","#384c49")[i%2])
        for j in range(31):
            x=-1.19+j*.081
            line(x,y+.106,x+.025,y+.106,"#637365")
        line(-1.48,top,1.53,top,GOLD)
        rect(-1.27,y-.017,1.52,y+.017,fill="#253432")
        line(-1.27,y-.02,1.52,y-.02,"#7d8068")
        line(-1.27,y+.022,1.52,y+.022,"#545e53")
        # Individual brass-and-enamel starting plates.
        rect(-1.61,bottom+.026,-1.39,top-.026,outline=GOLD,fill=ENAMELS[i])
        print_at(img,uv(-1.50,y+.007),str(i+1),IVORY,2)
        line(-1.315,bottom+.006,-1.315,top-.006,"#c9bea1")
        for j in range(4):
            x=-.95+j*.57
            for dy in (-.019,.019):
                line(x,y+.080+dy,x+.028,y+.080,"#98a38c")
    lo,hi=LANES[0]-.142,LANES[-1]+.142
    for j in range(25):
        y=lo+j*.049
        for i in range(3):
            x=1.20+i*.048
            rect(x,y,x+.047,min(hi,y+.048),fill=IVORY if (i+j)%2==0 else "#262d2c")
    for x in (1.182,1.358):line(x,lo,x,hi,GOLD)
    # Four matching enamel betting spots sit on the accessible player-side ledge.
    for i,x in enumerate((-.99,-.39,.21,.81)):
        y=-.714
        col=ENAMELS[i]
        shape=[uv(x-.25,y-.071),uv(x+.20,y-.071),uv(x+.26,y),uv(x+.20,y+.071),uv(x-.25,y+.071)]
        draw.polygon(shape,outline=GOLD,fill="#303b37")
        rect(x-.23,y-.055,x-.095,y+.055,fill=col)
        print_at(img,uv(x-.163,y+.010),str(i+1))
        for k in range(3):
            mid=x+.015+k*.061
            draw.polygon([uv(mid-.018,y),uv(mid,y+.022),uv(mid+.018,y),uv(mid,y-.022)],fill=GOLD if k==1 else col)
    img.save(OUT/"derby-lanes.png")

def faceplate():
    img=weave(172,23,("#412c33","#452e35","#402b31"),6)
    d=ImageDraw.Draw(img)
    d.rectangle((1,1,170,21),outline="#997b55")
    d.rectangle((3,3,168,19),outline="#64513d")
    print_at(img,(86,11),"КРЫСИНЫЕ БЕГА",IVORY)
    for x in (22,150):
        d.line((x-11,11,x+11,11),fill=GOLD)
        d.polygon([(x,7),(x+4,11),(x,15),(x-4,11)],fill="#b99c70")
        d.point((x,11),fill="#553e33")
    img.save(OUT/"derby-plaque.png")

if __name__ == "__main__":
    assert len(set(ORDER))==37 and len(RED)==18
    layout()
    number_ring()
    raceway()
    faceplate()
