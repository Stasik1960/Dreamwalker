"""Exclude inherited audit helpers and freeze the actual matched game dependencies."""
import json, zipfile
from run_profiles import PROFILES, sha, write
lock=json.loads((PROFILES/'profile-lock.json').read_text(encoding='utf-8'))
removed=[];sets={}
for role in ['client','server']:
    records=[]
    for variant in ['baseline','candidate']:
        mods=PROFILES/variant/role/'mods'
        for path in sorted(mods.glob('*.jar')):
            with zipfile.ZipFile(path) as z:meta=json.loads(z.read('fabric.mod.json'))
            if meta['id']=='dw_streaming7_checks':
                removed.append({'variant':variant,'role':role,'file':path.name,'sha256':sha(path)})
                path.unlink()
                continue
            assert 'checks' not in meta['id'] and 'qa' not in meta['id'],'Unexpected audit helper in measured profile: '+meta['id']
            records.append({'variant':variant,'file':path.name,'id':meta['id'],'version':meta['version'],'sha256':sha(path)})
    assert {r['file']:r['sha256'] for r in records if r['variant']=='baseline'}=={r['file']:r['sha256'] for r in records if r['variant']=='candidate'}
    sets[role]=records
lock['mods']=sets
lock['inheritedAuditHelpersRemoved']=removed
lock['benchmarkHelperPolicy']='Only the same lightweight route recorder; no migration or functional QA helpers.'
write(PROFILES/'profile-lock.json',lock)
print(json.dumps({'status':'PROFILES_FROZEN','removedHelpers':len(removed),'dependencies':{k:len(v)//2 for k,v in sets.items()},'sha256':sha(PROFILES/'profile-lock.json')}))
