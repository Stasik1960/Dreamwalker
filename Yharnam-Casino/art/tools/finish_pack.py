"""Check the model exports and package the collection."""

import json
import struct
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

from PIL import Image, ImageChops, ImageDraw, ImageFilter

from make_textures import font
from styles import THEMES

ROOT = Path(__file__).resolve().parents[1]
PREVIEWS = ROOT / "previews"
STYLES = (
    ("hunters_wager", "Мастерская охотника", "Чугун, дуб и латунь", (207,155,105)),
    ("astral_choir", "Часовня Хора", "Известняк, медь и витраж", (134,205,209)),
    ("crimson_oath", "Реликварий Кейнхёрста", "Серебро, бархат и гранат", (222,131,152)),
)
PIVOTS = {"lever","reel_01","reel_02","reel_03"}


def cabinet_portrait(key):
    with Image.open(PREVIEWS/f"{key}.png") as render:
        bounds = render.getchannel("A").getbbox()
        assert bounds and 10 < bounds[1] < bounds[3] < render.height-8
        assert 240 < bounds[2]-bounds[0] < 460 and 530 < bounds[3]-bounds[1] < 710
        portrait = render.crop(bounds).convert("RGBA")
    dimensions = tuple(round(.695*length) for length in portrait.size)
    return portrait.resize(dimensions,Image.Resampling.LANCZOS)


def draw_collection():
    page = Image.new("RGBA",(1500,885),(14,13,19,255))
    draw = ImageDraw.Draw(page)
    draw.text((44,35),"Автоматы Ярнама",font=font(56,"georgiab.ttf"),fill=(242,232,214))
    draw.line((46,118,1454,118),fill=(65,51,54))
    for index,(key,style,detail,color) in enumerate(STYLES):
        width,height = 456,702
        card = Image.new("RGBA",(width,height),(26,23,30))
        light = Image.new("L",card.size)
        ImageDraw.Draw(light).ellipse((80,147,397,600),fill=104)
        card.paste(tuple(round(.29*v) for v in color)+(255,), (0,0,width,height),
                   light.filter(ImageFilter.GaussianBlur(82)))
        shadow = Image.new("L",card.size)
        ImageDraw.Draw(shadow).ellipse((90,561,369,602),fill=235)
        card.paste((8,7,12,255),(0,0,width,height),shadow.filter(ImageFilter.GaussianBlur(13)))
        card_draw = ImageDraw.Draw(card)
        card_draw.rectangle((0,0,width-1,height-1),outline=(70,57,62))
        card_draw.text((23,28),style,font=font(15,"arial.ttf"),fill=color)
        card_draw.text((21,53),THEMES[key][0],font=font(30,"georgiab.ttf"),fill=(249,238,222))
        card_draw.text((23,95),detail,font=font(14,"arial.ttf"),fill=(186,175,175))
        portrait = cabinet_portrait(key)
        card.alpha_composite(portrait,((width-portrait.width)//2,591-portrait.height))
        card_draw.line((23,619,width-24,619),fill=(73,59,66))
        card_draw.text((23,633),"Полоса барабана",font=font(12,"arial.ttf"),fill=(195,184,179))
        reel_path = ROOT / "textures" / f"{key}_reel.png"
        with Image.open(reel_path) as reel:
            assert reel.size == (128,512)
            for band in range(8):
                symbol = reel.crop((0,band*64,128,(band+1)*64))
                thumb = symbol.resize((46,30),Image.Resampling.LANCZOS)
                card.paste(thumb,(23+band*51,652))
        page.alpha_composite(card,(46+index*476,144))
    page.convert("RGB").save(PREVIEWS/"bloodborne_slots_preview.png",optimize=True)


def make_spin():
    frames, times = [], []
    start, stop = None, None
    for filename in sorted((ROOT/".build").glob("_spin_*.png")):
        frame_number = int(filename.stem.rsplit("_",1)[1])
        with Image.open(filename) as source:
            frame = Image.new("RGBA",(504,270),(12,15,23,255))
            frame.alpha_composite(source,(12,48))
        draw = ImageDraw.Draw(frame)
        draw.text((13,11),"Astral Choir",font=font(22,"georgiab.ttf"),fill=(186,221,222))
        status = "Готов" if frame_number==1 else "Стоп" if frame_number>=97 else "Вращение"
        draw.text((490,17),status,font=font(14,"arial.ttf"),fill=(208,190,186),anchor="ra")
        frame = frame.convert("RGB")
        if frame_number == 1:
            start = frame
        if frame_number == 109:
            stop = frame
        frames.append(frame)
        times.append(650 if frame_number==1 else 960 if frame_number==109 else 70)
    assert len(frames) == 44 and start is not None and stop is not None
    difference = ImageChops.difference(start,stop).convert("L")
    for x in (90,212,333):
        samples = difference.crop((x,80,x+83,222)).histogram()
        assert sum(samples[15:])/sum(samples) > .06
    frames[0].save(PREVIEWS/"reels_in_motion.gif",save_all=True,append_images=frames[1:],
                   duration=times,loop=0,optimize=True,disposal=1)


def verify_models():
    symbols = json.loads((ROOT/"textures/reel_symbols.json").read_text(encoding="utf8"))
    for key, (_, _, surfaces) in THEMES.items():
        assert len(set(symbols[key])) == 8
        model = ROOT / "models" / key
        glb = model.with_suffix(".glb").read_bytes()
        magic, version, length, size, format_id = struct.unpack_from("<4sIII4s",glb)
        assert (magic,version,length,format_id) == (b"glTF",2,len(glb),b"JSON")
        gltf = json.loads(glb[20:20+size])
        clip, = gltf["animations"]
        assert clip["name"] == "pull_and_spin"
        rotations = [channel for channel in clip["channels"] if channel["target"]["path"]=="rotation"]
        assert {gltf["nodes"][channel["target"]["node"]]["name"] for channel in rotations} == PIVOTS
        for channel in rotations:
            sampler = clip["samplers"][channel["sampler"]]
            assert gltf["accessors"][sampler["input"]]["max"][0] > 3.75
        assert len(gltf["images"]) >= 2 and all("bufferView" in image for image in gltf["images"])
        project = json.loads(model.with_suffix(".bbmodel").read_text(encoding="utf8"))
        assert project["meta"]["model_format"] == "free"
        assert {node["name"] for node in project["outliner"][0]["children"]} == PIVOTS | {"cabinet"}
        assert project["textures"][0]["source"].startswith("data:image/png;base64,")
        with Image.open(ROOT/"textures"/f"{key}_atlas.png") as atlas:
            for part, bounds in (("reel",(8,8,136,520)),("marquee",(152,8,408,104))):
                with Image.open(ROOT/"textures"/f"{key}_{part}.png") as original:
                    assert not ImageChops.difference(atlas.crop(bounds),original).getbbox()
            for index, surface in enumerate(surfaces):
                x,y = 8+(index%3)*146,552+(index//3)*146
                image_path = ROOT/"textures/surfaces"/f"{key}_{surface}.png"
                with Image.open(image_path) as original:
                    assert not ImageChops.difference(atlas.crop((x,y,x+128,y+128)),original).getbbox()


def archive():
    groups = {
        "models": ("*.glb","*.bbmodel"),
        "textures": ("*.png","reel_symbols.json"),
        "textures/surfaces": ("*.png",),
        "previews": ("*.png","*.gif"),
        "tools": ("*.py",),
    }
    files = [ROOT/"README.md",ROOT/"bloodborne_slots.blend"]
    for folder,patterns in groups.items():
        for pattern in patterns:
            files.extend(sorted((ROOT/folder).glob(pattern)))
    destination = ROOT.with_suffix(".zip")
    with ZipFile(destination,"w",ZIP_DEFLATED) as package:
        for filename in files:
            package.write(filename,filename.relative_to(ROOT.parent).as_posix())
    with ZipFile(destination) as package:
        assert package.testzip() is None
    return destination


if __name__ == "__main__":
    verify_models()
    draw_collection()
    make_spin()
    print("Verified and packaged:",archive())
