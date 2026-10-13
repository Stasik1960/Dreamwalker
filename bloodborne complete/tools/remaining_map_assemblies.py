"""Read-only source v16 map evidence for split-object offsets; no extraction/write."""
from pathlib import Path
import hashlib, json, traceback, zipfile
from collections import Counter, defaultdict
from world_io import RegionFile, compound, section_blocks, block_state_key

ROOT = Path(__file__).resolve().parents[1]
MAP = ROOT.parent / 'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16/bbmc_v16_map.zip'
OUT = ROOT / 'docs/remaining-transfer'
WANTED = {'acacia_stairs','birch_stairs','dark_oak_stairs','spruce_stairs','warped_stairs','oak_door','iron_door','dark_oak_door','warped_door','crimson_door','stripped_jungle_log','stripped_acacia_log'}

def main():
    original_sha = hashlib.sha256(MAP.read_bytes()).hexdigest()
    cells = defaultdict(dict); counts = Counter(); scanned = 0
    with zipfile.ZipFile(MAP) as archive:
        for name in archive.namelist():
            if not (name.startswith('region/') or '/region/' in name) or not name.endswith('.mca'):
                continue
            dimension = name.split('/region/')[0] if '/region/' in name else 'minecraft:overworld'
            for chunk in RegionFile(archive.read(name)).chunks():
                scanned += 1
                value = compound(chunk.nbt().root)
                value = compound(value['Level']) if 'Level' in value else value
                cx, cz = value.get('xPos').value, value.get('zPos').value
                for section in value.get('sections', value.get('Sections')).value:
                    props = compound(section); states = props.get('block_states')
                    if states is None:
                        continue
                    palette = compound(states).get('palette')
                    if palette is None:
                        continue
                    eligible = {i for i, item in enumerate(palette.value) if compound(item)['Name'].value.removeprefix('minecraft:') in WANTED}
                    if not eligible:
                        continue
                    palette, indices = section_blocks(section)
                    sy = props['Y'].value
                    for index, choice in enumerate(indices):
                        if choice not in eligible:
                            continue
                        state = block_state_key(palette[choice]); counts[state] += 1
                        pos = (cx * 16 + index % 16, sy * 16 + index // 256, cz * 16 + (index // 16) % 16)
                        cells[dimension][pos] = state
    pairs = Counter(); examples = {}
    for dimension, positions in cells.items():
        for pos, state in positions.items():
            if 'half=top' in state or 'half=upper' in state:
                continue
            for dy in range(1, 7):
                other = positions.get((pos[0], pos[1]+dy, pos[2]))
                if not other:
                    continue
                key = (state, other, dy)
                pairs[key] += 1; examples.setdefault(key, {'dimension':dimension,'lower':pos,'upper':(pos[0],pos[1]+dy,pos[2])})
    final_sha = hashlib.sha256(MAP.read_bytes()).hexdigest()
    assert original_sha == final_sha
    result = {'map_sha256_before':original_sha,'map_sha256_after':final_sha,'chunks_scanned':scanned,'states':dict(counts),
              'vertical_pairs':[{'lowerState':first,'upperState':second,'offset':[0,dy*16,0],'occurrences':count,'example':examples[(first,second,dy)]} for (first,second,dy),count in pairs.most_common()]}
    (OUT / 'source-assembly-evidence.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')

if __name__ == '__main__':
    try:
        main(); (OUT/'map-audit-status.txt').write_text('PASS',encoding='utf-8')
    except Exception:
        (OUT/'map-audit-status.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
