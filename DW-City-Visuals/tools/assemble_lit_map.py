"""Import lighting/height caches into the clean delivery; preserve all world content."""
from pathlib import Path
import argparse
import collections
import copy
import hashlib
import json
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent / 'map_io'))
import world_io as w

ROOT_CACHE = ('Heightmaps', 'isLightOn', 'starlight.light_version')
SECTION_CACHE = ('BlockLight', 'SkyLight', 'starlight.blocklight_state', 'starlight.skylight_state')


def without_cache(nbt):
    value = copy.deepcopy(nbt)
    root = value.root.value
    for key in ROOT_CACHE:
        root.pop(key, None)
    if 'sections' in root:
        kept = []
        for section in root['sections'].value:
            for key in SECTION_CACHE:
                section.value.pop(key, None)
            if set(section.value) != {'Y'}:
                kept.append(section)
        root['sections'].value = kept
    return value


def import_cache(base, lit):
    before = without_cache(base)
    result = copy.deepcopy(base)
    dst, src = result.root.value, lit.root.value
    if not src.get('isLightOn') or not src['isLightOn'].value or 'Heightmaps' not in src:
        raise ValueError('Prelight chunk has no complete light/height cache')
    for key in ROOT_CACHE:
        dst.pop(key, None)
        if key in src:
            dst[key] = copy.deepcopy(src[key])
    source_sections = {s.value['Y'].value: s for s in src['sections'].value}
    existing = set()
    for section in dst['sections'].value:
        y = section.value['Y'].value
        existing.add(y)
        source = source_sections.get(y)
        for key in SECTION_CACHE:
            section.value.pop(key, None)
            if source is not None and key in source.value:
                section.value[key] = copy.deepcopy(source.value[key])
    for y, source in source_sections.items():
        if y not in existing and any(k in source.value for k in SECTION_CACHE):
            data = {'Y': copy.deepcopy(source.value['Y'])}
            data.update({k: copy.deepcopy(source.value[k]) for k in SECTION_CACHE if k in source.value})
            dst['sections'].value.append(w.Tag(w.TAG_COMPOUND, data))
    if before != without_cache(result):
        raise ValueError('Non-cache world data changed')
    if w.lighting_cache_errors(dst):
        raise ValueError('Invalid vanilla/Starlight light cache')
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('base_map', 'lit_world', 'census', 'output', 'report'):
        p.add_argument('--' + name.replace('_', '-'), type=Path, required=True)
    a = p.parse_args()
    if a.output.exists():
        raise FileExistsError(a.output)
    rows = json.loads(a.census.read_text())['prelightChunks']
    if any(r['dimension'] != 'minecraft:overworld' for r in rows):
        raise ValueError('Unexpected dimension in this delivery census')
    groups = collections.defaultdict(set)
    for r in rows:
        groups[(r['x'] // 32, r['z'] // 32)].add((r['x'] & 31, r['z'] & 31))
    replacements, chunks, skipped = {}, 0, 0
    with zipfile.ZipFile(a.base_map) as source:
        levels = [n for n in source.namelist() if n.endswith('/level.dat')]
        if len(levels) != 1:
            raise ValueError('Ambiguous world root')
        prefix = levels[0][:-len('level.dat')]
        for (rx, rz), cells in sorted(groups.items()):
            relative = f'region/r.{rx}.{rz}.mca'
            name = prefix + relative
            if name not in source.namelist():
                skipped += len(cells)
                continue
            base_region = w.RegionFile(source.read(name))
            lit_region = w.RegionFile.open(a.lit_world / relative)
            modified = {}
            for x, z in sorted(cells):
                base = base_region.get_chunk(x, z)
                if base is None:
                    skipped += 1
                    continue
                lit = lit_region.get_chunk(x, z)
                if lit is None:
                    raise ValueError(f'Missing prelit chunk {rx},{rz}:{x},{z}')
                modified[(x, z)] = import_cache(base.nbt(), lit.nbt())
                chunks += 1
            if modified:
                # Repack just touched regions, removing unreachable old sector records.
                packed = w.RegionFile()
                for chunk in base_region.chunks():
                    key = (chunk.x, chunk.z)
                    packed.set_chunk(*key, modified.get(key, chunk.raw_nbt()), timestamp=chunk.timestamp)
                replacements[name] = packed.to_bytes()
        with zipfile.ZipFile(a.output, 'x', compression=zipfile.ZIP_DEFLATED) as target:
            for info in source.infolist():
                target.writestr(info, replacements.get(info.filename, source.read(info.filename)))
    with zipfile.ZipFile(a.output) as check:
        if check.testzip() is not None:
            raise ValueError('Output ZIP CRC failure')
    report = dict(status='PASS', cacheOnlyInvariant='PASS', importedChunks=chunks,
                  skippedNonexistentBaseChunks=skipped, changedRegions=sorted(replacements),
                  baseSha256=hashlib.sha256(a.base_map.read_bytes()).hexdigest(),
                  outputSha256=hashlib.sha256(a.output.read_bytes()).hexdigest(),
                  fixturesPlayersEntitiesAndBlockStatesImported=False)
    a.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({k: v for k, v in report.items() if k != 'changedRegions'}))


if __name__ == '__main__':
    main()
