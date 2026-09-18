"""Remove every placed vanilla barrier from a NEW copy, preserving other NBT."""
import argparse
import copy
import hashlib
import shutil
from world_io import *


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def remove(source, target, report):
    source, target = source.resolve(), target.resolve()
    if not (source/'level.dat').is_file() or target.exists() or source in target.parents:
        raise ValueError('Source must be a world, target a separate new folder')
    before = {p.relative_to(source): sha(p) for p in source.rglob('*') if p.is_file()}
    shutil.copytree(source, target)
    counts = Counter()
    regions = {p for p in before if p.suffix == '.mca' and p.parent.name == 'region'}
    for rel in sorted(regions):
        region = RegionFile.open(source/rel)
        expected = {}
        for stored in region.chunks():
            nbt = stored.nbt()
            root = compound(nbt.root)
            changed = False
            counts['chunks_checked'] += 1
            for section in root.get('sections', Tag(TAG_LIST, [], TAG_COMPOUND)).value:
                bs_tag = compound(section).get('block_states')
                if bs_tag is None:
                    continue
                bs = compound(bs_tag)
                palette = bs['palette'].value
                removed = {i for i,t in enumerate(palette) if compound(t)['Name'].value == 'minecraft:barrier'}
                if not removed:
                    continue
                indices = unpack_palette_indices(bs.get('data', Tag(TAG_LONG_ARRAY, [])).value, len(palette))
                counts['barriers_removed'] += sum(i in removed for i in indices)
                # Rebuild a deduplicated palette; preserve each non-barrier state verbatim.
                new, lookup, mapping = [], {}, {}
                for i,t in enumerate(palette):
                    if i in removed:
                        t = Tag(TAG_COMPOUND, {'Name':Tag(TAG_STRING, 'minecraft:air')})
                    key = block_state_key(t)
                    if key not in lookup:
                        lookup[key] = len(new)
                        new.append(t)
                    mapping[i] = lookup[key]
                bs['palette'] = Tag(TAG_LIST, new, TAG_COMPOUND)
                packed = pack_palette_indices([mapping[i] for i in indices], len(new))
                if packed:
                    bs['data'] = Tag(TAG_LONG_ARRAY, packed)
                else:
                    bs.pop('data', None)
                changed = True
            if changed:
                invalidate_chunk_lighting(root)
                root.pop('Heightmaps', None)
                expected[(stored.x, stored.z)] = nbt
                region.set_chunk(stored.x, stored.z, nbt, timestamp=stored.timestamp)
                counts['chunks_changed'] += 1
        if expected:
            region.save(target/rel)
            counts['regions_changed'] += 1
        saved = RegionFile.open(target/rel)
        for stored in RegionFile.open(source/rel).chunks():
            actual = saved.get_chunk(stored.x, stored.z)
            assert actual.timestamp == stored.timestamp
            nbt = actual.nbt()
            assert nbt == expected.get((stored.x, stored.z), stored.nbt()), str(rel)
            assert not lighting_cache_errors(compound(nbt.root))
            for section in compound(nbt.root).get('sections', Tag(TAG_LIST, [], TAG_COMPOUND)).value:
                bs = compound(section).get('block_states')
                if bs:
                    assert all(compound(t)['Name'].value != 'minecraft:barrier' for t in compound(bs)['palette'].value)
        print(rel.as_posix(), 'verified;', counts['barriers_removed'], 'barriers removed', flush=True)
    assert set(before) == {p.relative_to(target) for p in target.rglob('*') if p.is_file()}
    for rel, digest in before.items():
        assert sha(source/rel) == digest, ('source changed', rel)
        if rel not in regions:
            assert sha(target/rel) == digest, ('non-region changed', rel)
            counts['non_region_files_unchanged'] += 1
    result = {'counts':dict(counts), 'barriers_remaining':0, 'source_unchanged':True, 'minecraft_started':False}
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(result), flush=True)


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('source', type=Path)
    p.add_argument('target', type=Path)
    p.add_argument('report', type=Path)
    a = p.parse_args()
    remove(a.source, a.target, a.report)
