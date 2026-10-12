"""Read-only source texture/model inspection; writes diagnostic files only."""
from pathlib import Path
import hashlib
import itertools
import json
import math
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/v12.6/doors/semantics'
SOURCE = ROOT / 'src/architecture/resources/assets/bloodborne_dw'
ALLIUM_ARCH_START = {40:52,41:46,42:41,43:38,44:35,45:32,46:31,47:27,48:26,49:25,50:24,51:23,
                    **{v:22 for v in range(52,61)},**{v:20 for v in range(61,123)}}


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def native_bounds(element):
    points = list(itertools.product(*zip(element['from'], element['to'])))
    rotation = element.get('rotation')
    if rotation:
        assert rotation['angle'] == 0
        if rotation.get('rescale'):
            scale = math.sqrt(2)
            axis = 'xyz'.index(rotation['axis'])
            origin = rotation['origin']
            points = [[origin[i] + (p[i] - origin[i]) * (1 if i == axis else scale)
                       for i in range(3)] for p in points]
    return [min(p[i] for p in points) for i in range(3)] + [max(p[i] for p in points) for i in range(3)]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    result = {}
    for name in ['allium', 'dark_door_1', 'dark_door_2']:
        path = SOURCE / f'models/remaining/source/minecraft/block/{name}.json'
        model = read(path)
        result[name] = {'path': str(path.relative_to(ROOT)), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                        'textures': model['textures'], 'elements': [{'element': i, 'nativeModelUnitsBounds': native_bounds(e),
                        'sourceFrom': e['from'], 'sourceTo': e['to'], 'rotation': e.get('rotation'),
                        'faces': e['faces']} for i,e in enumerate(model['elements'])]}
    for name in ['spirelamp_0089', 'roof_3', 'roof_5']:
        path = SOURCE / f'textures/block/remaining/source/minecraft/block/addon/{name}.png'
        texture = Image.open(path).convert('RGBA')
        checker = Image.new('RGBA', texture.size, '#797f7f')
        draw = ImageDraw.Draw(checker)
        for y in range(0, texture.height, 8):
            for x in range(0, texture.width, 8):
                if (x//8+y//8)%2:
                    draw.rectangle((x,y,x+7,y+7), fill='#9aa1a1')
        checker.alpha_composite(texture)
        magnified = checker.convert('RGB').resize((texture.width*8, texture.height*8), Image.Resampling.NEAREST)
        draw = ImageDraw.Draw(magnified)
        for value in range(0,128,8):
            draw.text((value*8+2,2), str(value), fill='#ff4040', stroke_width=1, stroke_fill='#ffffff')
            draw.text((2,value*8+2), str(value), fill='#ff4040', stroke_width=1, stroke_fill='#ffffff')
        magnified.save(OUT/f'{name}-inspection.png')
        overlay=checker.convert('RGB').resize((texture.width*8,texture.height*8),Image.Resampling.NEAREST)
        overlay_draw=ImageDraw.Draw(overlay)
        if name == 'spirelamp_0089':
            previous=None
            for v,start in ALLIUM_ARCH_START.items():
                point=(start*8,v*8)
                if previous:
                    overlay_draw.line([previous,point],fill='#ff3333',width=2)
                previous=point
            overlay_draw.line([(52*8,40*8),(55*8,40*8),(55*8,123*8),(20*8,123*8)],fill='#ff3333',width=2)
        elif name == 'roof_3':
            overlay_draw.rectangle((8*8,16*8,24*8,64*8),outline='#ff3333',width=2)
            overlay_draw.rectangle((24*8,16*8,40*8,64*8),outline='#ff3333',width=2)
        else:
            pixels=texture.load()
            for v in range(25):
                wood=[u for u in range(62,96) if pixels[u,v][3]>=64 and pixels[u,v][0]-pixels[u,v][2]>=8 and pixels[u,v][1]-pixels[u,v][2]>=4]
                if wood:
                    overlay_draw.line([(min(wood)*8,v*8),((max(wood)+1)*8,v*8)],fill='#ff3333',width=1)
        overlay.save(OUT/f'{name}-semantic-boundary.png')
        if name in ['spirelamp_0089', 'roof_5']:
            crop_bounds = (14,34,56,78) if name == 'spirelamp_0089' else (60,0,98,28)
            crop = checker.crop(crop_bounds).convert('RGB').resize(((crop_bounds[2]-crop_bounds[0])*18, (crop_bounds[3]-crop_bounds[1])*18), Image.Resampling.NEAREST)
            cd = ImageDraw.Draw(crop)
            for y in range(crop_bounds[1],crop_bounds[3]):
                for x in range(crop_bounds[0],crop_bounds[2]):
                    X,Y=(x-crop_bounds[0])*18,(y-crop_bounds[1])*18
                    cd.rectangle((X,Y,X+17,Y+17), outline='#999999')
                    if y == crop_bounds[1] or x == crop_bounds[0]:
                        cd.text((X+2,Y+2),str(x if y == crop_bounds[1] else y),fill='#ffffff',stroke_width=1,stroke_fill='#000000')
            crop.save(OUT/f'{name}-boundary-inspection.png')
        result[name] = {'path': str(path.relative_to(ROOT)), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'pixels':texture.size}
        if name == 'roof_5':
            pixels=texture.load()
            intervals=[]
            for y in range(25):
                wood=[x for x in range(62,96) if pixels[x,y][3] >= 64 and pixels[x,y][0]-pixels[x,y][2]>=8 and pixels[x,y][1]-pixels[x,y][2]>=4]
                intervals.append({'v':y,'woodUHalfOpen':[min(wood),max(wood)+1] if wood else None,
                                  'samples':{str(x):pixels[x,y] for x in range(62,96) if x%4==0}})
            result[name]['woodRows']=intervals
        if name == 'spirelamp_0089':
            pixels=texture.load()
            result[name]['alphaSamples']={str(y):[pixels[x,y] for x in range(16,56)] for y in range(26,123)}
            ascii_rows=[]
            for y in range(40,74):
                chars=''.join('.' if pixels[x,y][3]==0 else '#' if pixels[x,y][0]>=140 else '+' if pixels[x,y][0]>=100 else 'o' if pixels[x,y][0]>=70 else ':' for x in range(18,55))
                ascii_rows.append(f'{y:02d} {chars}')
            (OUT/'spirelamp-middle-ascii.txt').write_text('\n'.join(ascii_rows),encoding='utf-8')
    (OUT/'source-geometry.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    semantic = {
        'schemaVersion':1,
        'status':'SOURCE_TEXTURE_AND_NATIVE_CAPTURE_SEMANTIC_REVIEW_OFFLINE',
        'authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED',
        'scope':'Read-only classification of moving painted surfaces; no production or source artwork changes.',
        'uvUnits':'Texture texels, 128x128 texture; divide all supplied u/v coordinates by 8 for model JSON UV units.',
        '90266': {
            'sourceModel':'minecraft:block/allium',
            'nativeRescale':'All rescale:true angle0 elements use sqrt(2) on the two axes perpendicular to rotation.axis.',
            'sourceElementModelUnitsBounds':result['allium']['elements'],
            'closedPoseOffset':[0,16,0],
            'middleElements':[0,1],
            'middleMovingTexelRowIntervals':[{'v':[v,v+1],'u':[start,55]} for v,start in ALLIUM_ARCH_START.items() if v<74],
            'middleAllOtherTexels':'STATIC',
            'lowerElements':[4,5],
            'lowerMovingTexelRectangle':[20,74,55,122],
            'upperOrnamentElementsStatic':[2,3],
            'frontBackMirroring':'Apply classification in UV coordinates to the original face UV transform; source elements alternate mirrored u direction.',
            'movingArchPeakPoseYModelUnits':99.86889219483163+16-(40-26)*math.sqrt(2),
            'movingArchSpringPoseYModelUnits':99.86889219483163+16-(61-26)*math.sqrt(2),
            'evidenceNativeCapture':'build/runtime-client-v125-flatgallery-native-1/screenshots/v125-065-DOOR-REVIEW-90266.png',
            'caveat':'The precise operational split is inferred from the visible arch band and the user red outline, not original author hinge metadata. Closed-face geometry/UV/RGBA must remain invariant after partitioning; the new open pose still needs native acceptance.'
        },
        '93153': {
            'sourceLowerModel':'minecraft:block/dark_door_1',
            'sourceUpperModel':'minecraft:block/dark_door_2',
            'lowerModelBounds':[-16,-16,5,32,32,9],
            'lowerPoseOffset':[0,16,0],
            'upperPoseOffset':[0,48,0],
            'twoMovingModelUnitRectangles':[[-8,-16,5,8,32,9],[8,-16,5,24,32,9]],
            'lowerMovingFrontBackTexelRectangles':[[8,16,24,64],[24,16,40,64]],
            'lowerStaticOuterTexelRectangles':[[0,16,8,64],[40,16,48,64]],
            'sourceUpperEntirelyStatic':True,
            'hingeXModelUnits':[-8,24],
            'closedMeetingXModelUnits':8,
            'evidenceNativeCapture':'build/runtime-client-v125-flatgallery-native-1/screenshots/v125-068-DOOR-REVIEW-93153.png',
            'caveat':'UV on east/west and rotated up/down faces differs; do not crop those side faces using north/south UV coordinates. Partition geometry first and interpolate each original face UV transform.'
        },
        '90031-upper-90623': {
            'atlas':'minecraft:block/addon/roof_5',
            'centerFrontBackTexelBounds':[62,0,96,25],
            'rows':result['roof_5']['woodRows'],
            'woodColorCriterion':'Within the specified center cap only: alpha>=64, R-B>=8, G-B>=4; use the continuous interval from first to last wood texel in each row so dark internal leaf seams move with the wood.',
            'allOtherCapTexels':'STATIC_STONE',
            'method':'Distinct brown wood against exact neutral gray stone, source nearest texels. Only classify UV surfaces, preserve original material and alpha values.',
            'caveat':'Use original face UV transform to convert rows into model coordinates. Split the central wood cap at the same x=8 meeting seam as the lower 90031 leaves. Stone side panels and gray upper arch remain static.'
        }
    }
    (OUT/'moving-static-boundaries.json').write_text(json.dumps(semantic,ensure_ascii=False,indent=2),encoding='utf-8')
    allium_mask = {
        'schemaVersion':1,
        'rows':[{'v':v,'movingUStart':ALLIUM_ARCH_START.get(v,55)} for v in range(26,75)],
        'sourceTextureSha256':result['spirelamp_0089']['sha256'],
        'sourceModelSha256':result['allium']['sha256'],
        'sourceTexture':result['spirelamp_0089']['path'],
        'sourceModel':result['allium']['path'],
        'textureSize':[128,128],
        'movingUEndExclusive':55,
        'derivation':'Visible gray outer arch band in spirelamp_0089, traced against the closed V12.5 native screenshot and user screenshot3. Main arch peak row40, descending to hinge band row61. All other texels remain in static frame/ornament. This is inferred functional semantics, not author metadata.',
        'nativeValidation':'NOT_RUN',
        'reviewedClosedNativeCapture':'build/runtime-client-v125-flatgallery-native-1/screenshots/v125-065-DOOR-REVIEW-90266.png',
        'closedNativeCaptureSha256':hashlib.sha256((ROOT/'build/runtime-client-v125-flatgallery-native-1/screenshots/v125-065-DOOR-REVIEW-90266.png').read_bytes()).hexdigest(),
        'frameChecks':{
            'upperOrnamentSourceElementsEntirelyStatic':[2,3],
            'middleSourceElementsUVMask':[0,1],
            'lowerSourceElements':[4,5],
            'lowerMovingUTexelsHalfOpen':[20,55],
            'lowerMovingVTexelsHalfOpen':[74,122],
            'sourcePoseOffsetModelUnits':[0,16,0],
            'nativeAngleZeroRescaleFactor':math.sqrt(2),
            'nativeModelUnitsBoundsByElement':[{'element':e['element'],'bounds':e['nativeModelUnitsBounds']} for e in result['allium']['elements']],
            'uvReflection':'Apply each face original UV mapping, not a common X cut; original front/back mirroring and tintindex must survive.',
            'closedPartitionProofRequired':'Union of moving and static source-face partitions must preserve every original point, UV, RGBA and tintindex exactly. No invented source artwork.',
        },
    }
    (OUT/'allium-moving-mask.json').write_text(json.dumps(allium_mask,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'SEMANTICS-RU.txt').write_text('''Семантическое разделение створок: 90266, 93153 и верх90031/90623.

Статус: OFFLINE, исходные поверхности, UV и игровые снимки V12.5 просмотрены.
Новые открытые положения требуют отдельной проверки в клиенте. Это не экспорт
авторских коллизий и не изменение исходных моделей или текстур.

93153: нижний dark_door_1: x−8..8 и8..24, y−16..32, z5..9 — две створки.
Боковины x−16..−8 и24..32 неподвижны. Верх dark_door_2 неподвижен полностью.
В установленном объекте нижняя деталь имеет offsetY16, верхняя offsetY48.
На фронтальных гранях это atlasroof_3 texelsu8..24 и24..40,v16..64.

90266: не поворачивать исходные большие элементы целиком. Каждый включает
неподвижную наружную панель, нарисованную на той же поверхности, что створка.
Верхние sourceelements2/3 целиком неподвижны. У sourceelements0/1 двигается
центральная поверхность под видимой серой дугой; точные интервалы строк UV
зафиксированы в moving-static-boundaries.json. У нижних4/5 двигаетсяu20..55.
Прочие области статичны. Отражённые UV противоположных половин сохранить.
Все sourceelements имеют rescale:true с angle0: реально Minecraft масштабирует
перпендикулярные оси наsqrt(2), что существенно отличается от sourcefrom/to.

Верх90031 (прежняя90623): atlasroof_5u62..96,v0..25 содержит не только камень,
но и коричневый арочный верх створок. Указанные в JSON интервалыwoodRows —
движущаяся древесина; остальная поверхность — статичный серый камень. Тёмные
внутренние швы между первым/последним коричневыми texels входят в створку.

PNGinspection увеличивает исходные texels для диагностики. PNGsemantic-boundary
показывает только аналитическую линию деления; это не новое игровое искусство.
sha256 исходных файлов перечислены в source-geometry.json.
''',encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False,indent=2))


if __name__ == '__main__':
    main()
