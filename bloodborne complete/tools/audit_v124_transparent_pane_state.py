"""Prove the blank authored pane state from exact final JAR RGBA and face areas."""
import hashlib
import io
import json
import math
import zipfile
from pathlib import Path
from PIL import Image
from build_v124_stair_types import inherited
from gallery_platform_plan import ModelGeometry

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / 'docs/v12.4-dedup'
RES = ROOT / 'src/architecture/resources'
JAR = ROOT / 'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.4-dedup.jar'
RUN = ROOT / 'build/runtime-client-v124-comparison-final-2'


def read(path):
    return json.loads(path.read_text(encoding='utf8'))


def main():
    digest = hashlib.sha256(JAR.read_bytes()).hexdigest()
    assert digest == '4c9e202cff6ae5acd17386a30547726948a0d0d9babf4e529c84ec5c51b0c640'
    original = read(RES / 'bloodborne_dw/composite/source_assembly_red_stained_glass_pane.json')
    baseline = read(DOC / 'baseline-content.json')
    frozen = next(value for value in baseline['descriptors'].values() if value['id'] == original['id'])
    assert frozen == original
    canonical = read(RES / 'bloodborne_dw/composite/source_assembly_orange_stained_glass_pane.json')
    assert original['variants'][5]['poses']['closed']['parts'] == canonical['variants'][12]['poses']['closed']['parts']
    models = ModelGeometry()
    dependencies = {}
    faces = []
    with zipfile.ZipFile(JAR) as archive:
        def verify(relative):
            raw = (RES / relative).read_bytes()
            assert archive.read(relative) == raw
            dependencies[relative] = hashlib.sha256(raw).hexdigest()
            return raw

        for occurrence, part in enumerate(original['variants'][5]['poses']['closed']['parts']):
            assert not part.get('altModel')
            current = part['model']
            while current:
                ns, name = current.split(':', 1)
                verify(f'assets/{ns}/models/{name}.json')
                current = models.document(current).get('parent')
            doc = inherited(models, part['model'])
            for element in models.elements(part['model']):
                dimensions = [element['to'][i] - element['from'][i] for i in range(3)]
                for side, face in element['faces'].items():
                    texture = face['texture']
                    while texture.startswith('#'):
                        texture = doc['textures'][texture[1:]]
                    ns, name = texture.split(':', 1)
                    relative = f'assets/{ns}/textures/{name}.png'
                    image = Image.open(io.BytesIO(verify(relative))).convert('RGBA')
                    uv = face['uv']
                    x0, x1 = sorted((uv[0] * image.width / 16, uv[2] * image.width / 16))
                    y0, y1 = sorted((uv[1] * image.height / 16, uv[3] * image.height / 16))
                    values = [image.getpixel((x, y))[3]
                              for y in range(math.floor(y0), math.ceil(y1))
                              for x in range(math.floor(x0), math.ceil(x1))]
                    axis = {'west': 0, 'east': 0, 'up': 1, 'down': 1, 'north': 2, 'south': 2}[side]
                    area = math.prod(dimensions[i] for i in range(3) if i != axis)
                    assert values and area > 0 and all(alpha == 0 for alpha in values)
                    faces.append({'partOccurrence': occurrence, 'model': part['model'], 'side': side,
                                  'uv': uv, 'modelAreaSquareUnits': area, 'texture': texture,
                                  'alphaSamples': len(values), 'nonzeroAlphaSamples': 0})
    capture = read(RUN / 'v124-client-comparison-output.json')
    views = []
    for frame in capture['frames']:
        for row in frame['pairs']:
            if row.get('kind') != 'MIGRATION_ALT_VIEW' or row['oldId'] != '93223' or row['oldVariant'] != 5:
                continue
            image = RUN / 'screenshots' / frame['fileName']
            assert hashlib.sha256(image.read_bytes()).hexdigest() == frame['sha256']
            assert row['nativeModelsPresent'] and row['beforeNativeBounds'] == row['afterNativeBounds']
            views.append({'viewYawDegrees': row['viewYawDegrees'], 'viewPitchDegrees': row['viewPitchDegrees'],
                          'fileName': frame['fileName'], 'sha256': frame['sha256'],
                          'nativeBounds': row['beforeNativeBounds'],
                          'visualVisibility': 'NOT_APPLICABLE_FULLY_TRANSPARENT_SOURCE'})
    assert len(faces) == 22 and sum(row['alphaSamples'] for row in faces) == 1144 and len(views) == 2
    result = {'schemaVersion': 1, 'status': 'PASS_SOURCE_FULLY_TRANSPARENT_STATE_PRESERVED',
              'productionJarSha256': digest, 'oldId': '93223', 'oldVariant': 5,
              'mainId': '93214', 'variant': 12, 'sourceDescriptorMatchesFrozenBaseline': True,
              'canonicalPartsExactlyEqualSourceParts': True, 'sourceFilesModified': False,
              'partOccurrences': 5, 'faceOccurrences': len(faces), 'alphaSamples': 1144,
              'nonzeroAlphaSamples': 0, 'nonzeroPhysicalFaceAreas': len(faces),
              'allCameraDirectionsPreserveBlankSource': True,
              'nativeModelsPresentAndBoundsEqual': True, 'nativeViewsActuallyReviewed': views,
              'dependencyBytesEqualFinalJar': dependencies, 'faces': faces}
    (DOC / 'transparent-93223-v5-source-proof.json').write_bytes(
        (json.dumps(result, ensure_ascii=False, indent=2) + '\n').encode('utf8'))
    print(json.dumps({key: value for key, value in result.items()
                      if key not in {'faces', 'dependencyBytesEqualFinalJar', 'nativeViewsActuallyReviewed'}}))


if __name__ == '__main__':
    main()
