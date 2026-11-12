"""Cast and turned wheel designs, with the decorated face along positive X."""
from __future__ import annotations

import math

from gothic_parts import crescent, oval, polar, rotate2, window


def rim(craft, name, inner, outer, low, high, tone, *, count=24, flutes=0):
    height = high - low
    bevel = min(.0014, (outer-inner)*.29)
    profile = [(low,inner+bevel),(low,outer-bevel),(low+height*.33,outer),
               (low+height*.70,outer),(high,outer-bevel),(high,inner+bevel),
               (low+height*.70,inner),(low+height*.31,inner)]
    return craft.turn(name,"X",profile,tone,count=count,closed=True,flutes=flutes)


def hub(craft, name, radius, rear, front, tone):
    craft.turn(name,"X",[(rear,radius*.83),(rear+.0015,radius),(front-.0015,radius),(front,radius*.78)],
               tone,count=24)


def panel(craft, name, outline, low, high, tone, rim_tone):
    return craft.plate(name,outline,low,high,tone,axis="X",bevel=.00085,rim=rim_tone)


def gem(craft, name, radius, angle, offset, low, high, colors, count=8):
    center = polar(offset,angle)
    return craft.gem(name,oval(radius,radius,count,center),low,high,colors,axis="X")


def rosary_bead(craft):
    profile = [
        (-.023,.026),(-.020,.032),(-.014,.0368),(-.009,.0383),(-.005,.0387),(-.003,.038),
        (.003,.038),(.005,.0387),(.010,.0384),(.015,.0368),(.019,.0332),(.021,.0280),
    ]
    craft.turn("Turned ivory tyre with central groove","X",profile,
               ["bronze","bone","ivory","ivory","bone","tread","bone","ivory","ivory","bone","bronze"],count=32)
    rim(craft,"Bronze chain setting",.021,.031,.019,.025,
        ["bronze","brass","bronze","gold","brass","bronze"],count=30,flutes=10)
    hub(craft,"Oxblood seal in the rosary center",.020,.021,.025,"leather")
    rim(craft,"Cast gilt bezel",.009,.015,.023,.028,"brass",count=24)
    gem(craft,"Deep garnet axle seal",.011,0,0,.025,.030,("garnet","ruby","red"),12)
    for i in range(10):
        angle=math.tau*i/10
        gem(craft,"Raised ivory rosary bead",.0044,angle,.026,.024,.0296,("bone","silver_light","ivory"),8)
        gem(craft,"Recessed garnet bead",.0021,angle+math.pi/10,.026,.023,.027,("garnet","ruby","red"),6)


def rose_cog(craft):
    profile = [(-.021,.026),(-.017,.034),(-.011,.0389),(-.004,.039),(.006,.0387),(.013,.0375),
               (.018,.0341),(.020,.0308)]
    craft.turn("Rolled black tyre","X",profile,["steel_dark","tread","rubber","tread","rubber","lead","steel_dark"],
               count=32)
    rim(craft,"Scalloped bronze gear barrel",.024,.0344,.016,.026,"bronze",count=42,flutes=14)
    for i in range(14):
        angle=math.tau*i/14
        outline = [(u,v) for u,v in (rotate2(p,angle) for p in
                    ((-.0032,.030),(.0032,.030),(.0037,.0335),(.0026,.0372),(-.0024,.0374),(-.0037,.034)))]
        panel(craft,"Forged cog tooth",outline,.022,.028,"brass" if i%2 else "bronze","gold")
    hub(craft,"Smoked rose center",.024,.019,.022,"shadow")
    rim(craft,"Fine bronze rose lip",.021,.0255,.021,.027,"brass")
    for i in range(7):
        angle=math.tau*i/7
        points=[rotate2(p,angle) for p in ((0,.007),(.004,.012),(.007,.017),(.004,.022),
                                         (0,.023),(-.004,.022),(-.007,.017),(-.004,.012))]
        panel(craft,"Etched metal rose petal",points,.022,.0268,"steel","bronze")
        gem(craft,"Petal garnet",.0027,angle+math.pi/2,.017,.025,.0288,("garnet","ruby","red"),6)
    gem(craft,"Faceted rose cog hub",.008,0,0,.023,.0298,("steel","silver","garnet"),8)


def stained_glass(craft):
    profile = [(-.018,.023),(-.017,.030),(-.013,.035),(-.007,.0384),(-.002,.039),(.003,.0388),
               (.009,.038),(.015,.035),(.017,.030)]
    craft.turn("Tapered lead-cased window tread","X",profile,
               ["steel_dark","lead","rubber","tread","rubber","tread","lead","bronze"],count=32)
    rim(craft,"Iron frame with double moulding",.024,.0333,.014,.023,
        ["lead","steel_dark","steel","bronze","brass","lead"],count=32)
    window(craft,"X",.021,.0298,8,.0068)
    for i in range(8):
        gem(craft,"Small leaded rim rivet",.0018,math.tau*i/8,.033,.020,.0248,("bronze","brass","gold"),6)


def tomb_roller(craft):
    profile = [(-.027,.030),(-.024,.036),(-.020,.0395),(-.013,.0395),(-.011,.0373),(-.008,.0391),
               (.003,.0391),(.006,.0376),(.009,.0395),(.016,.0394),(.020,.0361),(.023,.030)]
    craft.turn("Wide stepped funeral tread","X",profile,
               ["slate","steel_dark","rubber","tread","steel_dark","rubber","tread","steel_dark","rubber","lead","slate"],
               count=30)
    rim(craft,"Octagonal funeral bezel",.025,.033,.019,.027,
        ["steel_dark","pewter","lead","bronze","steel","lead"],count=16,flutes=8)
    hub(craft,"Recessed engraved slate",.026,.021,.025,"slate")
    outline=[(-.003,-.023),(.003,-.023),(.0055,-.017),(.0036,-.015),(.004,-.004),(.013,-.004),
             (.015,-.006),(.021,-.003),(.021,.003),(.015,.006),(.013,.004),(.004,.004),(.004,.014),
             (.006,.017),(.003,.022),(-.003,.022),(-.006,.017),(-.004,.014),(-.004,.004),(-.013,.004),
             (-.015,.006),(-.021,.003),(-.021,-.003),(-.015,-.006),(-.013,-.004),(-.004,-.004),(-.0036,-.015),(-.0055,-.017)]
    panel(craft,"Stepped raised sepulchral cross",outline,.025,.029,"bone","pewter")
    gem(craft,"Cross garnet center",.0044,0,0,.026,.0299,("garnet","ruby","red"),8)
    for i in range(4):
        gem(craft,"Corner case rivet",.0033,math.tau*(i+.5)/4,.025,.024,.028,
            ("bronze","steel","silver"),6)


def moon_hub(craft):
    profile = [(-.019,.022),(-.017,.033),(-.013,.0378),(-.008,.039),(-.001,.039),(.002,.0378),
               (.006,.0389),(.012,.0375),(.018,.034),(.020,.029)]
    craft.turn("Asymmetric cupped moon tread","X",profile,
               ["steel_dark","indigo","rubber","tread","lead","rubber","indigo","tread","lead"],count=32)
    rim(craft,"Moon frame in blackened silver",.022,.0325,.016,.026,
        ["steel_dark","steel","pewter","indigo","silver","lead"],count=30)
    hub(craft,"Blue-black eclipse mirror",.025,.019,.024,"indigo")
    panel(craft,"Silver crescent with sculpted horns",crescent(.020,.023),.024,.0294,"silver","pewter")
    for i,(u,v) in enumerate(((.012,.014),(.015,-.006),(.003,-.017),(.016,.004))):
        r=.004 if i==0 else .0023
        star=[(u,v-r),(u+r*.22,v-r*.21),(u+r,v),(u+r*.22,v+r*.21),
              (u,v+r),(u-r*.22,v+r*.21),(u-r,v),(u-r*.22,v-r*.21)]
        panel(craft,"Cast stellar setting",star,.024,.0274,"gold" if i==0 else "silver","bronze")
    for i in range(6):
        gem(craft,"Night enamel frame pin",.0018,math.tau*i/6,.028,.024,.0275,("lead","blue","lapis"),6)


WHEEL_DESIGNS = {
    "rosary_bead": rosary_bead, "rose_cog": rose_cog, "stained_glass": stained_glass,
    "tomb_roller": tomb_roller, "moon_hub": moon_hub,
}
