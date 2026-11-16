"""Bake the static cabinet models and the fixed-size stock layer atlas."""

import json
import math
from pathlib import Path
from random import Random

from PIL import Image, ImageDraw

from chip_cabinet_layout import BOXES, DENOMINATIONS
from make_casino_items import COLORS, unlock_recipe

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
ASSETS = ROOT / "assets/yharnam_casino"
DATA = ROOT / "data/yharnam_casino"
FACES = ("north","south","east","west","up","down")
WOOD = {"oak":(75,48,39),"edge":(51,35,33),"back":(54,37,35),
        "trim":(101,76,53),"felt":(33,48,40),"rail":(103,73,52)}


def write_json(path, data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2)+"\n",encoding="utf8")


def bake_surfaces():
    folder = ASSETS / "textures/block"
    folder.mkdir(parents=True,exist_ok=True)
    for name,base in WOOD.items():
        rng = Random("yharnam-cabinet:"+name)
        image = Image.new("RGB",(32,32))
        for x in range(32):
            for y in range(32):
                swirl = math.sin(1.4*x+.46*math.sin(y*.16))+math.sin(x*.44+.13*math.sin(y*.3))
                if name == "trim":
                    swirl = math.sin(.39*x+y*.27) * .38
                elif name == "felt":
                    swirl = math.sin((x+y)*2) * .22
                shade = 5.5*swirl + rng.uniform(-3,3)
                image.putpixel((x,y),tuple(round(c+shade) for c in base))
        image.save(folder/f"chip_cabinet_{name}.png",optimize=True)


def bake_stock():
    image = Image.new("RGBA",(128,512),(0,0,0,0))
    draw = ImageDraw.Draw(image)
    for col, denomination in enumerate(DENOMINATIONS):
        x = col*16
        paper, rim, gold = (tuple(bytes.fromhex(hex_color)) for hex_color in COLORS[denomination])
        draw.rectangle((x,0,x+15,15),fill=paper)
        draw.ellipse((x+1,1,x+14,14),outline=rim,width=2)
        draw.ellipse((x+4,4,x+11,11),outline=gold)
        draw.point((x+7,7),fill=gold)
        for layer in range(64):
            y = 16 + layer*4
            draw.rectangle((x,y,x+15,y+3),fill=paper)
            draw.line((x,y,x+15,y),fill=rim)
            if layer%4 != 1:
                for notch in (3,11):
                    draw.point((x+notch,y+1),fill=gold)
            if layer%4 == 0:
                draw.line((x,y+2,x+15,y+3),fill=tuple(round(channel*.45) for channel in paper))
            else:
                draw.line((x,y+3,x+15,y+3),fill=(27,19,22))
    folder = ASSETS / "textures/entity"
    folder.mkdir(parents=True,exist_ok=True)
    image.save(folder/"chip_cabinet_stock.png",optimize=True)


def elements(bottom,top):
    values = []
    for _,lower,upper,surface in BOXES:
        floor,ceiling = max(bottom,lower[1]),min(top,upper[1])
        if floor >= ceiling:
            continue
        directions = [face for face in FACES if (face!="down" or floor==lower[1])
                      and (face!="up" or ceiling==upper[1])]
        values.append({
            "from":[lower[0],floor-bottom,lower[2]],
            "to":[upper[0],ceiling-bottom,upper[2]],
            "faces":{face:{"uv":[0,0,16,16],"texture":"#"+surface} for face in directions},
        })
    return values


def bake_models():
    textures = {role:"yharnam_casino:block/chip_cabinet_"+role for role in WOOD}
    textures["particle"] = "#oak"
    variants = {}
    for half,lower,upper in (("lower",0,16),("upper",16,32)):
        name = "chip_cabinet_"+half
        write_json(ASSETS/"models/block"/f"{name}.json",{
            "ambientocclusion":False,"textures":textures,"elements":elements(lower,upper),
        })
        for facing,y in (("south",0),("west",90),("north",180),("east",270)):
            variants[f"facing={facing},half={half}"]={"model":"yharnam_casino:block/"+name,"y":y}
    write_json(ASSETS/"blockstates/chip_cabinet.json",{"variants":variants})
    write_json(ASSETS/"models/item/chip_cabinet.json",{
        "ambientocclusion":False,"textures":textures,"elements":elements(0,32),
        "display":{
            "gui":{"rotation":[23,-28,0],"translation":[0,-4,0],"scale":[.62,.62,.62]},
            "ground":{"translation":[0,2,0],"scale":[.34,.34,.34]},
            "fixed":{"translation":[0,-3,0],"scale":[.55,.55,.55]},
            "firstperson_righthand":{"rotation":[0,-34,0],"translation":[0,-2,0],"scale":[.30,.30,.30]},
            "thirdperson_righthand":{"rotation":[0,-24,0],"translation":[0,0,0],"scale":[.29,.29,.29]},
        },
    })


if __name__ == "__main__":
    bake_surfaces()
    bake_stock()
    bake_models()
    write_json(DATA/"loot_tables/blocks/chip_cabinet.json",{
        "type":"minecraft:block",
        "pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"yharnam_casino:chip_cabinet"}],
                  "conditions":[{"condition":"minecraft:survives_explosion"},
                                {"condition":"minecraft:block_state_property",
                                 "block":"yharnam_casino:chip_cabinet","properties":{"half":"lower"}}]}],
    })
    write_json(DATA/"recipes/chip_cabinet.json",{
        "type":"minecraft:crafting_shaped","pattern":["DDD","IDI","DDD"],
        "key":{"D":{"item":"minecraft:dark_oak_planks"},"I":{"item":"minecraft:iron_nugget"}},
        "result":{"item":"yharnam_casino:chip_cabinet"},
    })
    unlock_recipe("chip_cabinet","minecraft:dark_oak_planks")
