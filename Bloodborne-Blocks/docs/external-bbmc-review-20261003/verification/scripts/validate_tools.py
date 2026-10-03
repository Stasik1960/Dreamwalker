import argparse, collections, hashlib, json, runpy, sys
from pathlib import Path
from types import SimpleNamespace as Tag
import numpy as np

cli=argparse.ArgumentParser()
cli.add_argument('--audit-root',type=Path,required=True)
cli.add_argument('--inputs',type=Path,required=True)
cli.add_argument('--work-root',type=Path,required=True)
options=cli.parse_args()
root=options.audit_root.resolve()
bundle=Path(__file__).resolve().parents[2]
scratch=options.work_root.resolve()
assert scratch.is_relative_to(root)
scratch.mkdir(parents=True,exist_ok=True)
scripts=bundle/'verification/scripts'
sys.argv=[str(scripts/'audit_maps.py'),'--inputs',str(root),'--output',str(scratch/'maps')]
module=runpy.run_path(str(scripts/'audit_maps.py'),run_name='audit_module')
section=module['section']
palette=[Tag(type=10,value={'Name':Tag(type=8,value='minecraft:synthetic_'+str(i))}) for i in range(100)]
indices=np.arange(4096,dtype=np.uint64)%100
words=np.zeros((4096+8)//9,dtype=np.uint64)
for i,value in enumerate(indices):words[i//9]|=value<<np.uint64((i%9)*7)
data=words.view(np.int64).tolist()
counts,_,nonair=section(palette,data)
assert sum(counts.values())==4096 and len(counts)==100 and nonair==4096
assert sorted(counts.values())==sorted(collections.Counter(indices.tolist()).values())
bad=words.copy();bad[0]=(bad[0]&~np.uint64(127))|np.uint64(127)
try:section(palette,bad.view(np.int64).tolist())
except ValueError:pass
else:raise AssertionError('Invalid palette index accepted')
maps=json.loads((bundle/'map-analysis.json').read_text(encoding='utf-8'))
gate=module['fully_valid'];assert gate(maps['archives'])
assert not gate(maps['archives'][:-1])
changed=json.loads(json.dumps(maps['archives']));changed[0]['errors']=['fixture'];assert not gate(changed)

smoke=runpy.run_path(str(scripts/'smoke.py'),run_name='smoke_module')
run=smoke['run'];globals_=run.__globals__;globals_['ROOT']=scratch/'smoke';globals_['ROOT'].mkdir(exist_ok=True)
globals_['DOWNLOADS']=options.inputs
def forbidden(*args,**kwargs):raise AssertionError('Negative test reached Popen')
original=globals_['subprocess'].Popen;globals_['subprocess'].Popen=forbidden
rejected=0
def expect_reject(name,**kwargs):
    global rejected
    try:run(name,**kwargs)
    except (ValueError,FileExistsError):rejected+=1
    else:raise AssertionError(name)
try:
    expect_reject('../escape',no_mod=True)
    existing=globals_['ROOT']/'existing';existing.mkdir(exist_ok=True)
    expect_reject('existing',no_mod=True)
    target=globals_['ROOT']/'reuse';target.mkdir(exist_ok=True);(target/'mods').mkdir(exist_ok=True)
    config={'minecraft':'1.18.2','bloodborne':False,'gecko':False,'mod_sha256':{}}
    (target/'scenario.json').write_text(json.dumps(config))
    (target/'mods/extra.jar').write_bytes(b'fixture')
    expect_reject('reuse',no_mod=True,reuse=True)
    expect_reject('reuse',no_mod=True,reuse=True,vanilla120=True)
    target=globals_['ROOT']/'tampered';target.mkdir(exist_ok=True);(target/'mods').mkdir(exist_ok=True)
    digest=hashlib.sha256((globals_['DOWNLOADS']/'Bloodborne_X_Minecraft_mod_6.0 (1).jar').read_bytes()).hexdigest()
    config={'minecraft':'1.18.2','bloodborne':True,'gecko':False,'mod_sha256':{'bloodborne.jar':digest}}
    (target/'scenario.json').write_text(json.dumps(config));(target/'mods/bloodborne.jar').write_bytes(b'fixture')
    expect_reject('tampered',gecko=False,reuse=True)
finally:globals_['subprocess'].Popen=original

original=json.loads((bundle/'resource-analysis.json').read_text(encoding='utf-8'))
fresh=json.loads((root/'portable-validation/resources/resource-analysis.json').read_text(encoding='utf-8'))
assert len(original['audits'])==len(fresh['audits'])==10
for a,b in zip(original['audits'],fresh['audits']):
    assert a['sha256']==b['sha256'] and a['asset_hashes']==b['asset_hashes'] and a['categories']==b['categories']
manifest=json.loads((bundle/'input-manifest.json').read_text(encoding='utf-8'))
for m in manifest:
    p=globals_['DOWNLOADS']/m['file']
    assert p.stat().st_size==m['bytes']
    assert hashlib.sha256(p.read_bytes()).hexdigest().lower()==m['sha256'].lower()
result={'valid':True,'resource_audits_repeated':10,'input_sha256_rechecked':16,
        'synthetic_palette_entries':100,'palette_cells':4096,'invalid_palette_rejected':True,
        'incomplete_and_failed_map_gate_rejected':True,'smoke_negative_cases_rejected':rejected,
        'minecraft_processes_started_by_this_check':0}
(scratch/'publication-validation.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result))
