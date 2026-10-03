"""Independent consistency checks for the published factual bundle."""
import ast
import csv
import json
import re
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlparse

ROOT=Path(__file__).resolve().parents[2]
def read(name):return json.loads((ROOT/name).read_text(encoding='utf-8'))
manifest=read('input-manifest.json')
assert len(manifest)==16 and all('path' not in m for m in manifest)
maps=read('map-analysis.json')
assert maps['valid'] and len(maps['archives'])==6
assert sum(a['containers']['region']['decoded_chunks'] for a in maps['archives'])==165372
assert sum(sum(c['decoded_chunks'] for c in a['containers'].values()) for a in maps['archives'])==166062
for a in maps['archives']:
    assert not a['errors'] and a['zip_crc_failure'] is None
    assert not a['unsafe_paths'] and not a['duplicate_paths']
    assert all(c['chunks']==c['decoded_chunks'] for c in a['containers'].values())
    assert sum(a['block_state_counts'].values())==a['explicit_section_cells']
    assert len(a['block_counts'])==a['actual_unique_blocks']
growth=read('map-growth.json')
assert growth['valid'] and growth['selective_parser_comparison_chunks']==12
assert growth['input_sha256_verified'] and growth['archive_measurements_match_reference']
assert len(growth['archives'])==5
for g in growth['archives']:
    m=next(m for m in maps['archives'] if m['file']==g['file'])
    assert g['overworld_terrain_chunks']==m['containers']['region']['decoded_chunks']
    assert sum(g['heightmap_sources'].values())+g['heightmap_missing_chunks']==g['overworld_terrain_chunks']
assert growth['archives'][0]['columns_with_surface_above_y0']==229485
assert growth['archives'][-1]['columns_with_surface_above_y0']==748683
assert growth['archives'][-1]['chunks_with_surface_above_y0']==3446
inventory=read('mod-inventory-full.json')
assert len(inventory['entities'])==len({e['id'] for e in inventory['entities']})==98
assert sum(e['category']=='MONSTER' for e in inventory['entities'])==21
assert sum(e['category']=='MISC' for e in inventory['entities'])==77
assert len(inventory['items'])==len({e['id'] for e in inventory['items']})==121
assert sum(e['spawn_egg'] for e in inventory['items'])==18
assert len(inventory['sounds'])==51
assert len(inventory['animations'])==32
assert len(inventory['geo_paths'])==101
assert len(inventory['texture_paths'])==95 and len(inventory['sound_paths'])==400
registry={e['id'] for e in inventory['entities']}
for filename,count,types in [('bbmc_v15_map.zip',275,31),('bbmc_v16_map (1).zip',262,60)]:
    a=next(a for a in maps['archives'] if a['file']==filename)
    custom={k:v for k,v in a['entity_ids'].items() if k.startswith('bloodborne:')}
    assert sum(custom.values())==count and len(custom)==types
for a in maps['archives']:
    assert all(k in registry for k in a['entity_ids'] if k.startswith('bloodborne:'))
    for e in inventory['entities']:
        assert e['map_counts'][a['file']]==a['entity_ids'].get(e['id'],0)
resources=read('resource-analysis.json')['audits'][:4]
comparisons=read('resource-deltas-full.json')
for a,b,c in zip(resources,resources[1:],comparisons):
    expected={p for p in a['asset_hashes'].keys()|b['asset_hashes'].keys()
              if a['asset_hashes'].get(p)!=b['asset_hashes'].get(p)}
    assert {d['path'] for d in c['changes']}==expected
    assert len(c['changes'])==len(expected)
    for d in c['changes']:
        assert d['before_sha256']==a['asset_hashes'].get(d['path'])
        assert d['after_sha256']==b['asset_hashes'].get(d['path'])
assert len(comparisons[-1]['changes'])==2
with (ROOT/'resource-deltas-full.csv').open(encoding='utf-8',newline='') as f:
    assert len(list(csv.DictReader(f)))==sum(len(c['changes']) for c in comparisons)==992
for name in ('control-v14','control-vladra120'):
    for fn in ('result.json','restart-result.json'):
        r=read('verification/'+name+'/'+fn)
        assert r['ready'] and not r['timeout']
        log=(ROOT/'verification'/name/(fn.removesuffix('.json')+'-lifecycle.log')).read_text(encoding='utf-8')
        assert 'Done (' in log and 'Stopping server' in log
        assert any(s in log for s in ('Saved the game','Saved the world','All dimensions are saved'))
for name,missing in [('no-gecko','GeckoLib'),('gecko-v16','PoseStack')]:
    r=read('verification/'+name+'/result.json')
    assert not r['ready'] and missing in '\n'.join(r['diagnostics'])

class Links(HTMLParser):
    def __init__(self):super().__init__();self.links=[]
    def handle_starttag(self,tag,attrs):
        self.links.extend(v for k,v in attrs if k in ('href','src'))
checked=0
for p in ROOT.rglob('*'):
    if not p.is_file():continue
    assert p.suffix not in ('.jar','.zip','.ogg','.class'),p
    if p.suffix=='.json':json.loads(p.read_text(encoding='utf-8'))
    if p.suffix=='.py':ast.parse(p.read_text(encoding='utf-8'),filename=str(p))
    if p.suffix not in ('.md','.html','.json','.py','.java','.log','.csv'):continue
    text=p.read_text(encoding='utf-8')
    private_pattern=r'(?i)[A-Z]:[\\/]Users[\\/]|'+'file'+r'://'
    assert not re.search(private_pattern,text),p
    values=[]
    if p.suffix=='.html':
        links=Links();links.feed(text);values=links.links
    elif p.suffix=='.md':
        values=re.findall(r'(?<!!)\[[^\]\n]+\]\(([^)\n]+)\)',text)
    for value in values:
        url=urlparse(value)
        if url.scheme in ('https','http') or not url.path:continue
        target=(p.parent/unquote(url.path)).resolve()
        assert target.exists(),(p,value)
        checked+=1
print(json.dumps({'valid':True,'inputs':16,'entities':98,'items':121,'sound_events':51,
                  'terrain_chunks':165372,'asset_delta_records':992,'checked_local_links':checked}))
