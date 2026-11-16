"""Generate the chip and ticket textures, item models and survival recipes."""

import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
NAMESPACE = "yharnam_casino"
ASSETS = ROOT / "assets" / NAMESPACE
DATA = ROOT / "data" / NAMESPACE
COLORS = {
    1: ("826B58", "D1B18E", "FFF0BB"),
    5: ("824F38", "BE7756", "FFE4BF"),
    10: ("365853", "589183", "DBF2CF"),
    50: ("3E4567", "677CA1", "ECF6FC"),
    100: ("693345", "A25164", "FFE1C5"),
    500: ("514064", "9C80A8", "F8E4FF"),
    1000: ("443B2A", "CAA25B", "FFEAB0"),
}
GLYPHS = {
    "0": ("111","101","101","101","111"),
    "1": ("010","110","010","010","111"),
    "5": ("111","100","111","001","111"),
}


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+"\n",encoding="utf8")


def item_model(name):
    dump(ASSETS/"models/item"/f"{name}.json",{
        "parent":"minecraft:item/generated",
        "textures":{"layer0":f"{NAMESPACE}:item/{name}"},
    })


def chip_texture(path, value, colors):
    dark, rim, ink = ["#"+color for color in colors]
    image = Image.new("RGBA",(32,32))
    draw = ImageDraw.Draw(image)
    outline = [(9,2),(22,2),(28,7),(30,12),(30,21),(25,27),
               (22,29),(9,29),(3,24),(1,19),(1,11),(5,5)]
    draw.polygon(outline,fill="#19171D")
    draw.polygon([(10,3),(21,3),(27,7),(29,12),(29,20),(24,26),
                  (21,27),(10,27),(4,23),(2,18),(2,12),(6,6)],fill=dark)
    draw.polygon([(11,4),(21,4),(26,8),(27,12),(27,19),(23,24),
                  (20,26),(10,26),(5,22),(4,17),(4,12),(7,7)],fill=rim)
    draw.polygon([(10,7),(21,7),(25,11),(25,19),(21,23),(10,23),
                  (6,19),(6,11)],fill=dark)
    for point in ((9,6),(22,6),(5,10),(26,10),(5,20),(26,20),(9,24),(22,24)):
        draw.point(point,fill=ink)
    draw.line((12,5,19,5),fill=ink)
    draw.line((12,25,19,25),fill="#2A2023")
    digits = str(value)
    scale = 2 if len(digits) < 3 else 1
    width = (len(digits)*4-1)*scale
    x = (32-width)//2
    y = (30-5*scale)//2
    for index, digit in enumerate(digits):
        for row, pixels in enumerate(GLYPHS[digit]):
            for column, filled in enumerate(pixels):
                if filled == "1":
                    left = x + (index*4+column)*scale
                    top = y+row*scale
                    draw.rectangle((left,top,left+scale-1,top+scale-1),fill=ink)
    if value >= 100:
        draw.line((9,20,22,20),fill=rim)
    image.save(path)


def ticket_texture(path):
    image = Image.new("RGBA",(32,32))
    draw = ImageDraw.Draw(image)
    draw.polygon([(3,6),(10,6),(12,4),(27,4),(29,6),(29,12),(26,15),(26,18),
                  (29,21),(29,26),(13,26),(11,28),(3,28),(1,26),(1,21),
                  (4,18),(4,14),(1,12),(1,8)],fill="#261B21")
    draw.polygon([(3,8),(11,8),(13,6),(27,6),(27,12),(24,15),(24,19),(27,22),
                  (27,24),(12,24),(10,26),(3,26),(3,22),(6,19),(6,13),(3,11)],fill="#D1AA78")
    draw.rectangle((8,10,22,23),outline="#854A43")
    draw.polygon([(15,10),(21,16),(15,22),(9,16)],fill="#68293A")
    draw.line((15,12,15,20),fill="#F5D29A",width=1)
    draw.line((12,16,18,16),fill="#F5D29A",width=1)
    draw.line((13,8,21,8),fill="#FFE5AC")
    image.save(path)


def iou_texture(path):
    image = Image.new("RGBA",(32,32))
    draw = ImageDraw.Draw(image)
    # A cold blue-gray folded note, deliberately unlike the warm gold RP ticket.
    draw.polygon([(6,2),(24,2),(29,7),(29,28),(25,31),(6,31),(2,27),(2,7)],fill="#171C28")
    draw.polygon([(7,4),(22,4),(27,9),(27,27),(24,29),(7,29),(4,26),(4,8)],fill="#71899B")
    draw.polygon([(22,4),(27,9),(22,9)],fill="#B8CAD2")
    draw.line((7,7,19,7),fill="#D8E4E5",width=1)
    draw.rectangle((7,11,24,24),outline="#344B62")
    draw.line((9,14,22,14),fill="#D2DEE0",width=1)
    draw.line((9,17,19,17),fill="#AFC2C9",width=1)
    draw.line((9,20,21,20),fill="#D2DEE0",width=1)
    draw.ellipse((17,19,24,26),fill="#26394E",outline="#B7D1D5")
    draw.line((19,22,22,22),fill="#D8E4E5",width=1)
    draw.point((20,21),fill="#D8E4E5")
    draw.point((21,23),fill="#D8E4E5")
    image.save(path)


def key_texture(path):
    image = Image.new("RGBA",(32,32))
    draw = ImageDraw.Draw(image)
    draw.polygon([(19,2),(25,2),(30,7),(30,13),(25,18),(20,18),(17,16),
                  (9,24),(9,28),(6,31),(2,31),(1,27),(5,23),(5,19),
                  (8,16),(11,16),(14,13),(14,7)],fill="#211721")
    draw.polygon([(19,4),(24,4),(28,8),(28,12),(24,16),(20,16),(17,13),
                  (7,23),(7,27),(5,29),(3,29),(3,27),(7,23),(7,20),
                  (9,18),(12,18),(16,14),(16,8)],fill="#A57542")
    draw.polygon([(19,5),(23,5),(27,8),(27,11),(24,14),(20,14),(17,11),(17,8)],
                 fill="#DFB579")
    draw.polygon([(21,7),(24,8),(25,10),(22,12),(19,11),(19,9)],fill=(0,0,0,0))
    draw.line((6,26,16,16),fill="#ECD29D")
    image.save(path)


def gm_key_texture(path):
    image = Image.new("RGBA",(32,32))
    draw = ImageDraw.Draw(image)
    # A cold silver-blue ceremonial key, distinct from the steward key's warm gold.
    draw.polygon([(18,1),(25,1),(31,7),(31,13),(26,18),(21,18),(18,16),
                  (11,23),(11,27),(8,30),(4,30),(1,27),(1,24),(5,20),(8,20),
                  (14,14),(13,10),(14,5)],fill="#101723")
    draw.polygon([(18,3),(24,3),(29,8),(29,12),(25,16),(21,16),(17,13),
                  (9,21),(9,25),(6,28),(3,27),(3,25),(7,21),(7,18),(10,18),
                  (16,12),(15,8),(16,5)],fill="#7898AD")
    draw.polygon([(19,4),(23,4),(27,7),(28,10),(25,14),(21,14),(18,11),(17,8)],
                 fill="#C4D6DE")
    draw.polygon([(20,6),(24,6),(26,8),(26,10),(23,12),(20,11),(19,9)],
                 fill=(0,0,0,0))
    draw.line((5,25,17,13),fill="#E8F5F7",width=1)
    draw.line((8,24,11,27),fill="#405E77",width=1)
    draw.polygon([(14,17),(16,15),(18,17),(16,19)],fill="#5EA9C1")
    draw.point((16,16),fill="#D9FAFF")
    image.save(path)


def shapeless(name, ingredient, count, output, quantity):
    dump(DATA/"recipes"/f"{name}.json",{
        "type":"minecraft:crafting_shapeless",
        "group":"yharnam_casino_chip_exchange",
        "ingredients":[{"item":ingredient} for _ in range(count)],
        "result":{"item":output,"count":quantity},
    })
    unlock_recipe(name,ingredient)

def unlock_recipe(name, ingredient):
    recipe = f"{NAMESPACE}:{name}"
    dump(DATA/"advancements/recipes"/f"{name}.json",{
        "parent":"minecraft:recipes/root",
        "criteria":{
            "has_ingredient":{
                "trigger":"minecraft:inventory_changed",
                "conditions":{"items":[{"items":[ingredient]}]},
            },
            "has_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{"recipe":recipe}},
        },
        "requirements":[["has_ingredient","has_recipe"]],
        "rewards":{"recipes":[recipe]},
    })

def cabinet_recipe(style, frame, accent, glass):
    dump(DATA/"recipes"/f"{style}.json",{
        "type":"minecraft:crafting_shaped",
        "pattern":["AGA","FRF","FLF"],
        "key":{
            "A":{"item":f"minecraft:{accent}"},
            "G":{"item":f"minecraft:{glass}"},
            "F":{"item":f"minecraft:{frame}"},
            "R":{"item":"minecraft:redstone"},
            "L":{"item":"minecraft:lever"},
        },
        "result":{"item":f"{NAMESPACE}:{style}"},
    })
    unlock_recipe(style,f"minecraft:{accent}")
    dump(ASSETS/"blockstates"/f"{style}.json",{"variants":{"":{"model":f"{NAMESPACE}:block/{style}"}}})
    particle = "quartz_block_side" if frame == "quartz_block" else frame
    dump(ASSETS/"models/block"/f"{style}.json",{
        "textures":{"particle":f"minecraft:block/{particle}"},
    })
    dump(DATA/"loot_tables/blocks"/f"{style}.json",{
        "type":"minecraft:block",
        "pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":f"{NAMESPACE}:{style}"}],
                  "conditions":[{"condition":"minecraft:survives_explosion"},
                                {"condition":"minecraft:block_state_property",
                                 "block":f"{NAMESPACE}:{style}","properties":{"half":"lower"}}]}],
    })


def main():
    texture_dir = ASSETS/"textures/item"
    texture_dir.mkdir(parents=True,exist_ok=True)
    for value, colors in COLORS.items():
        name = f"chip_{value}"
        chip_texture(texture_dir/f"{name}.png",value,colors)
        item_model(name)
    ticket_texture(texture_dir/"prize_ticket.png")
    item_model("prize_ticket")
    iou_texture(texture_dir/"chip_iou.png")
    item_model("chip_iou")
    key_texture(texture_dir/"cabinet_key.png")
    item_model("cabinet_key")
    gm_key_texture(texture_dir/"gm_key.png")
    item_model("gm_key")
    dump(DATA/"recipes/cabinet_key.json",{
        "type":"minecraft:crafting_shaped",
        "pattern":[" G","I ","I "],
        "key":{"G":{"item":"minecraft:gold_ingot"},"I":{"item":"minecraft:iron_ingot"}},
        "result":{"item":f"{NAMESPACE}:cabinet_key"},
    })
    unlock_recipe("cabinet_key","minecraft:gold_ingot")
    shapeless("buy_chip", "minecraft:gold_nugget",1,f"{NAMESPACE}:chip_1",1)
    shapeless("redeem_chip", f"{NAMESPACE}:chip_1",1,"minecraft:gold_nugget",1)
    values = list(COLORS)
    for small, large in zip(values,values[1:]):
        smaller, larger = f"{NAMESPACE}:chip_{small}",f"{NAMESPACE}:chip_{large}"
        shapeless(f"combine_{large}",smaller,large//small,larger,1)
        shapeless(f"change_{large}",larger,1,smaller,large//small)
    cabinet_recipe("hunters_wager","polished_deepslate","copper_ingot","orange_stained_glass")
    cabinet_recipe("astral_choir","quartz_block","oxidized_copper","cyan_stained_glass")
    cabinet_recipe("crimson_oath","dark_oak_planks","iron_ingot","red_stained_glass")


if __name__ == "__main__":
    main()
