"""Package native material palettes and the generated transparent foliage.

Texture pixels are copied unchanged; material colors are a renderer resource,
so three comparisons share the same architecture, UVs, world and physics.
"""
import argparse, json, shutil, zipfile
from pathlib import Path

VARIANTS = {
    '01-pale-gold': ('Светлая золотая осень', {'stone':'#FFF4E9','roof':'#DDE1EB','metal':'#E2DAD0','wood':'#F3DFBF','foliage':'#FFEFC9'}),
    '02-classic-amber': ('Золотая осень — янтарь', {'stone':'#FFE9CA','roof':'#C7D1DF','metal':'#CFCAC1','wood':'#ECC398','foliage':'#FFDC9A'}),
    '03-copper-evening': ('Медная осень', {'stone':'#FFDEB4','roof':'#BFC6D8','metal':'#C3B6AA','wood':'#D8AB86','foliage':'#FFD4A5'}),
}


def build(leaf, output):
    if output.exists():raise ValueError('output must be new')
    output.mkdir(parents=True)
    manifest={'schemaVersion':1,'variants':[],'pixels':'unchanged generated foliage','gameplay':'unchanged'}
    for name,(label,colors) in VARIANTS.items():
        folder=output/name;folder.mkdir()
        palette={'schemaVersion':1,'colors':colors,'foliage':True,'leaf_texture':'bloodborne_blocks:block/autumn/leaves'}
        files={'pack.mcmeta':{'pack':{'pack_format':15,'description':label}},
               'assets/bloodborne_blocks/autumn/palette.json':palette,
               'assets/minecraft/atlases/blocks.json':{'sources':[{'type':'single','resource':'bloodborne_blocks:block/autumn/leaves'}]}}
        for rel,value in files.items():
            path=folder/rel;path.parent.mkdir(parents=True,exist_ok=True)
            path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        texture=folder/'assets/bloodborne_blocks/textures/block/autumn/leaves.png';texture.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(leaf,texture)
        with zipfile.ZipFile(output/(name+'.zip'),'w',zipfile.ZIP_DEFLATED) as archive:
            for file in sorted(folder.rglob('*')):
                if file.is_file():archive.write(file,file.relative_to(folder).as_posix())
        manifest['variants'].append({'id':name,'label':label,'colors':colors,'resourcePack':name+'.zip'})
    (output/'palettes.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (output/'Artist-Palette.md').write_text('''# Палитры золотой осени

Три ресурс-пака применяются по одному. Геометрия города, координаты объектов и физика одинаковы.
Цвета в `palettes.json` — множители материала. Светлые участки сохраняют исходную текстуру, синий канал камня ослаблен.
Множитель не повышает яркость исходного пикселя: недостаточно светлый фасад требует правки его PNG художником.
Прозрачная листва добавляется визуальными плоскостями; коллизии дерева сохраняются.

Для художника: корректировать силуэт и текстуры на собранном OBJ/glTF, сохранять UV и источник каждого материала.
Размеры — блоки Minecraft, ось Y направлена вверх. OBJ использует нижнее начало V, glTF — верхнее.
Материал возвращается через ресурс-пак и модель `bloodborne_polygons`, не через изменение игровых коллизий.
До выбора варианта не сводить палитры в один пак.
''',encoding='utf-8')
    return manifest


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('leaf',type=Path);p.add_argument('output',type=Path)
    a=p.parse_args();print(json.dumps({'variants':len(build(a.leaf,a.output)['variants'])}))
