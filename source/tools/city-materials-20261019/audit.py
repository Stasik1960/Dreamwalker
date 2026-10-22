"""One read-only resource pass against streaming.5 and the authoritative v15 pack.

Reuse the earlier exact namespace/model provenance audit, then examine the UV
rectangles of every effective model. A transparent pixel outside a face's UV
rectangle is recorded separately and is never called a material defect.
"""
import collections
import hashlib
import io
import json
import math
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

PROJECT = Path(__file__).absolute().parents[2]
ROOT = PROJECT.parent
OUT = ROOT / 'research/city-materials-20261019'
JAR = ROOT / 'deliverables/collision-streaming-20261018/game/dreamwalker-bb-streaming.5.jar'
PACK = Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\Bloodborne-Blocks\reference-inputs\bbmc-v15-v16\bbmc_v15_resource.zip')
VANILLA = PROJECT / 'build/runtime-client-city-mesh-baseline/minecraft-client.jar'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def ordinary(path):
    if path.is_symlink() or getattr(path.lstat(), 'st_file_attributes', 0) & 1024:
        raise ValueError('Reparse point refused: ' + str(path))
    return path


def files(path):
    for child in sorted(ordinary(path).iterdir()):
        ordinary(child)
        if child.is_dir():
            yield from files(child)
        elif child.is_file():
            yield child


def preserve():
    result = {'baseCommit': 'ece36054605f3fbd00b1ee90449b6749e0a977d7',
              'jar': str(JAR), 'jarSha256': digest(JAR.read_bytes()), 'worlds': []}
    assert result['jarSha256'] == 'a179fce5acf607bbcdf86202ddbb20e33928f0a5c6936b5e6ef7de7be14ecbaf'
    lists = []
    for relative in ['map/world', 'backup/world']:
        folder = ROOT / 'deliverables/collision-streaming-20261018' / relative
        rows = [{'path': p.relative_to(folder).as_posix(), 'bytes': p.stat().st_size,
                 'sha256': digest(p.read_bytes())} for p in files(folder)]
        lists.append(rows)
        result['worlds'].append({'path': str(folder), 'files': len(rows),
                                'bytes': sum(r['bytes'] for r in rows),
                                'manifestSha256': digest(json.dumps(rows, sort_keys=True).encode())})
    assert lists[0] == lists[1], 'Original delivered world and backup differ'
    (OUT / 'original-world-files.json').write_text(json.dumps(lists[0], indent=2) + '\n')
    result['status'] = 'PASS_BASE_JAR_AND_TWO_INDEPENDENT_UNCHANGED_WORLDS'
    (OUT / 'preservation-before.json').write_text(json.dumps(result, indent=2) + '\n')


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    preserve()
    # Execute the proven exact resource provenance audit with explicit new inputs.
    prior = Path(__file__).with_name('provenance_audit.py')
    code = prior.read_text(encoding='utf-8')
    code = code[:code.rindex('try:run()')]
    env = {'__file__': str(prior)}
    exec(compile(code, str(prior), 'exec'), env)
    env.update(HERE=OUT, P=PROJECT, JAR=JAR, PACK=PACK)
    env['run']()
    with zipfile.ZipFile(JAR) as mod, zipfile.ZipFile(PACK) as ref, zipfile.ZipFile(VANILLA) as vanilla:
        names = set(mod.namelist())
        ref_names = set(ref.namelist())
        vanilla_names = set(vanilla.namelist())
        ref_hashes = collections.defaultdict(list)
        for name in sorted(ref_names):
            if name.endswith('.png'):
                ref_hashes[digest(ref.read(name))].append(name)
        png_rows = []
        for name in sorted(names):
            if not name.endswith('.png') or not name.startswith('assets/'):
                continue
            raw = mod.read(name)
            im = np.asarray(Image.open(io.BytesIO(raw)).convert('RGBA'))
            png_rows.append({'path': name, 'sha256': digest(raw), 'size': [im.shape[1], im.shape[0]],
                             'exactV15Assets': ref_hashes.get(digest(raw), []),
                             'nonopaqueTexels': int((im[:, :, 3] != 255).sum())})
        def rid(x):
            return x if ':' in x else 'minecraft:' + x
        def asset(kind, x, suffix):
            ns, path = rid(x).split(':', 1)
            return f'assets/{ns}/{kind}/{path}{suffix}'
        cache = {}
        def resolve(mid, trail=()):
            mid = rid(mid)
            if mid in cache:
                return cache[mid]
            if mid in trail or mid.startswith('minecraft:builtin/'):
                return {}
            path = asset('models', mid, '.json')
            raw = mod.read(path) if path in names else vanilla.read(path) if path in vanilla_names else None
            if raw is None:
                return {'missing': mid}
            d = json.loads(raw)
            parent = resolve(d['parent'], trail + (mid,)) if 'parent' in d else {}
            result = dict(parent)
            result.update(d)
            result['textures'] = dict(parent.get('textures', {})) | d.get('textures', {})
            cache[mid] = result
            return result
        image_cache = {}
        uv_cache = {}
        def used_alpha(tid, uv):
            key = (tid, *uv)
            if key in uv_cache:
                return uv_cache[key]
            if tid not in image_cache:
                path = asset('textures', tid, '.png')
                raw = mod.read(path) if path in names else vanilla.read(path) if path in vanilla_names else None
                image_cache[tid] = np.asarray(Image.open(io.BytesIO(raw)).convert('RGBA')) if raw else None
            im = image_cache[tid]
            if im is None:
                result = {'missingTexture': tid}
            else:
                # JSON block model UVs use 0..16, regardless of PNG resolution.
                # For animated sheets inspect all physical frames, like production.
                h, w = im.shape[:2]
                frame_h = min(h, w)
                u0, u1 = sorted([uv[0], uv[2]])
                v0, v1 = sorted([uv[1], uv[3]])
                if u0 < 0 or v0 < 0 or u1 > 16 or v1 > 16:
                    result = {'outOfSpriteUv': True, 'uv': uv}
                else:
                    x0, x1 = math.floor(u0 * w / 16), math.ceil(u1 * w / 16)
                    y0, y1 = math.floor(v0 * frame_h / 16), math.ceil(v1 * frame_h / 16)
                    x0, y0 = min(x0, w - 1), min(y0, frame_h - 1)
                    x1, y1 = max(x0 + 1, min(x1, w)), max(y0 + 1, min(y1, frame_h))
                    sampled = np.concatenate([im[y + y0:y + y1, x0:x1, 3].ravel()
                                              for y in range(0, h, frame_h) if y + frame_h <= h])
                    result = {'usedAlphaMin': int(sampled.min()), 'usedAlphaMax': int(sampled.max()),
                              'usedNonopaqueTexels': int((sampled != 255).sum()),
                              'pngNonopaqueTexels': int((im[:, :, 3] != 255).sum()),
                              'usedPixelRectangle': [x0, y0, x1, y1]}
            uv_cache[key] = result
            return result
        def default_uv(e, side):
            x, y, z = e['from']; X, Y, Z = e['to']
            return {'down': [x, 16-Z, X, 16-z], 'up': [x, z, X, Z],
                    'north': [16-X, 16-Y, 16-x, 16-y], 'south': [x, 16-Y, X, 16-y],
                    'west': [z, 16-Y, Z, 16-y], 'east': [16-Z, 16-Y, 16-z, 16-y]}[side]
        model_rows = []
        for path in sorted(names):
            if not path.startswith('assets/bloodborne_dw/models/') or not path.endswith('.json'):
                continue
            mid = 'bloodborne_dw:' + path.split('/models/', 1)[1][:-5]
            d = resolve(mid)
            counters = collections.Counter()
            materials = {}
            for e in d.get('elements', []):
                for side, face in e.get('faces', {}).items():
                    counters['faces'] += 1
                    tid = face.get('texture', '')
                    seen = set()
                    while tid.startswith('#') and tid not in seen:
                        seen.add(tid)
                        tid = d.get('textures', {}).get(tid[1:], 'UNRESOLVED:' + tid)
                    tid = rid(tid)
                    uv = face.get('uv', default_uv(e, side))
                    info = used_alpha(tid, uv)
                    materials[json.dumps([tid, uv])] = {'texture': tid, 'uv': uv, **info}
                    counters['usedAlphaFaces'] += info.get('usedNonopaqueTexels', 0) > 0
                    counters['unusedPngAlphaOnlyFaces'] += (info.get('usedNonopaqueTexels') == 0 and info.get('pngNonopaqueTexels', 0) > 0)
                    counters['outOfSpriteFaces'] += info.get('outOfSpriteUv', False)
                    counters['missingTextureFaces'] += 'missingTexture' in info
                    counters['tintedFaces'] += face.get('tintindex', -1) >= 0
                    counters['unshadedFaces'] += not e.get('shade', True)
            model_rows.append({'model': mid, 'ambientOcclusion': d.get('ambientocclusion', True),
                               **counters, 'materials': list(materials.values())})
        totals = collections.Counter()
        for row in model_rows:
            totals.update({k: v for k, v in row.items() if isinstance(v, int) and not isinstance(v, bool)})
        summary = {'jarSha256': digest(JAR.read_bytes()), 'pack': str(PACK), 'packSha256': digest(PACK.read_bytes()),
                   'allModPngs': len(png_rows), 'exactV15Pngs': sum(bool(r['exactV15Assets']) for r in png_rows),
                   'effectiveModModels': len(model_rows), **totals,
                   'modelsWithUsedAlpha': sum(r.get('usedAlphaFaces', 0) > 0 for r in model_rows),
                   'modelsWithOnlyUnusedPngAlpha': sum(r.get('unusedPngAlphaOnlyFaces', 0) > 0 and not r.get('usedAlphaFaces', 0) for r in model_rows),
                   'layerPolicy': 'Production per-baked-quad UV/mipmap opacity proof; translucent recipes retain translucent. Validate native suspects; unused PNG alpha is not a defect.'}
        (OUT / 'all-resources-uv.json').write_text(json.dumps({'summary': summary, 'pngs': png_rows, 'models': model_rows}, indent=2) + '\n')
        (OUT / 'all-resources-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
        provenance = json.loads((OUT / 'materials-audit.json').read_text())
        selected = {m for row in provenance['blockVariants'] for m in row['actualPartModels']}
        active = [row for row in model_rows if rid(env['norm'](row['model'])) in selected]
        interpretation = {'selectedModels': len(active),
                          'selectedMissingTextureFaces': sum(r.get('missingTextureFaces', 0) for r in active),
                          'parentTemplatesAreNotRendered': True,
                          'inheritedOutOfSpriteModels': [{'model': r['model'], 'outOfSpriteFaces': r['outOfSpriteFaces']}
                                                         for r in active if r.get('outOfSpriteFaces', 0)],
                          'originalAmbientOcclusionDifferences': [r['sourceModel'] for r in provenance['models']
                                                                  if 'ambientocclusion' in r.get('differingTopLevelFields', [])],
                          'note': 'Out-of-sprite UVs are present in v15 too: suspect only; no speculative clamping or texture edits.'}
        (OUT / 'audit-interpretation.json').write_text(json.dumps(interpretation, indent=2) + '\n')
        print(json.dumps(summary))


if __name__ == '__main__':
    main()
