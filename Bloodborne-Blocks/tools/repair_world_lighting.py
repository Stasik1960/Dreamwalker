"""Repair lighting cache in a NEW offline world copy; never changes the source."""
import argparse
import hashlib
import shutil
import zipfile
from world_io import *

ROOT_LIGHT = {'isLightOn', 'starlight.light_version'}
SECTION_LIGHT = {'SkyLight', 'BlockLight', 'starlight.skylight_state', 'starlight.blocklight_state'}


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def unchanged_content(before, after):
    """Independent comparison: only removal of the six exact cache keys allowed."""
    a, b = compound(before.root), compound(after.root)
    assert before.name == after.name
    assert {k:v for k,v in a.items() if k not in ROOT_LIGHT | {'sections'}} == {
        k:v for k,v in b.items() if k not in ROOT_LIGHT | {'sections'}}
    assert not ROOT_LIGHT.intersection(b)
    sa = a.get('sections', Tag(TAG_LIST, [], TAG_COMPOUND))
    sb = b.get('sections', Tag(TAG_LIST, [], TAG_COMPOUND))
    assert sa.type == sb.type and sa.list_type == sb.list_type and len(sa.value) == len(sb.value)
    for old, new in zip(sa.value, sb.value):
        old, new = compound(old), compound(new)
        assert {k:v for k,v in old.items() if k not in SECTION_LIGHT} == new


def repair(source, target, archive):
    source, target, archive = source.resolve(), target.resolve(), archive.resolve()
    if not (source/'level.dat').is_file():
        raise ValueError('Source must be a world folder')
    if target.exists() or archive.exists() or source == target or source in target.parents or target in source.parents:
        raise ValueError('Choose a separate NEW output folder and archive')
    if source in archive.parents or target in archive.parents:
        raise ValueError('Archive must be outside the world folders')
    original = {p.relative_to(source): sha(p) for p in source.rglob('*') if p.is_file()}
    shutil.copytree(source, target)
    counts = Counter()
    region_paths = {rel for rel in original if rel.suffix == '.mca' and rel.parent.name == 'region'}
    for rel in sorted(region_paths):
        region = RegionFile.open(source/rel)
        dirty = False
        for stored in list(region.chunks()):
            nbt = stored.nbt()
            root = compound(nbt.root)
            counts['chunks_checked'] += 1
            if 'starlight.light_version' in root:
                counts['chunks_with_starlight_cache'] += 1
            for section in root.get('sections', Tag(TAG_LIST, [], TAG_COMPOUND)).value:
                sd = compound(section)
                for array, state in (('SkyLight','starlight.skylight_state'), ('BlockLight','starlight.blocklight_state')):
                    if sd.get(state, Tag(TAG_INT, 0)).value in (2, 3) and array not in sd:
                        counts['initialized_arrays_missing'] += 1
            if invalidate_chunk_lighting(root):
                region.set_chunk(stored.x, stored.z, nbt, timestamp=stored.timestamp)
                counts['chunks_reset'] += 1
                dirty = True
        if dirty:
            region.save(target/rel)
            counts['regions_reset'] += 1
        # Read the saved bytes back, compare every non-light NBT field, not just IDs.
        old, new = RegionFile.open(source/rel), RegionFile.open(target/rel)
        assert {(c.x,c.z) for c in old.chunks()} == {(c.x,c.z) for c in new.chunks()}
        for stored in old.chunks():
            fixed = new.get_chunk(stored.x, stored.z)
            assert fixed.timestamp == stored.timestamp
            unchanged_content(stored.nbt(), fixed.nbt())
            counts['chunks_content_verified'] += 1
        print('Verified', rel.as_posix(), counts['chunks_content_verified'], flush=True)
    assert set(original) == {p.relative_to(target) for p in target.rglob('*') if p.is_file()}
    for rel, digest in original.items():
        assert sha(source/rel) == digest, 'Source modified: ' + str(rel)
        if rel not in region_paths:
            assert sha(target/rel) == digest, 'Non-region data changed: ' + str(rel)
            counts['other_files_unchanged'] += 1
    archive.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=4, allowZip64=True) as z:
        for path in sorted(target.rglob('*')):
            if path.is_file() and path.name != 'session.lock':
                z.write(path, 'Ether-Bloodborne-2.0.1/'+path.relative_to(target).as_posix())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None
        assert 'Ether-Bloodborne-2.0.1/level.dat' in z.namelist()
    report = {'counts':dict(counts), 'only_lighting_cache_changed':True, 'source_unchanged':True,
              'minecraft_started':False, 'archive':archive.name, 'bytes':archive.stat().st_size, 'sha256':sha(archive)}
    archive.with_suffix('.verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    archive.with_suffix(archive.suffix+'.sha256').write_text(report['sha256']+'  '+archive.name+'\n', encoding='ascii')
    print(json.dumps(report), flush=True)


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('source', type=Path);p.add_argument('target', type=Path);p.add_argument('archive', type=Path)
    a = p.parse_args();repair(a.source, a.target, a.archive)
