"""Check old-save visual contracts and migration offsets without running Minecraft."""
from catalog_geometry import *
from find_aliases import fingerprint
import subprocess,io,tarfile

# Published 1.1.0 source; pin the baseline so the check survives future commits.
BASELINE='ad80f423dca92937726af3bdcb2b7731aa0edb6d'
prefix='Bloodborne-Blocks/src/main/resources/'
archive=subprocess.check_output(['git','archive',BASELINE,prefix+'bloodborne_blocks/definitions.json',prefix+'assets/bloodborne_blocks/blockstates'],cwd=ROOT.parent)
tar=tarfile.open(fileobj=io.BytesIO(archive))
def oldjson(path):return json.load(tar.extractfile(prefix+path))
def canonical_apps(bs,state):
    result=copy.deepcopy(applications(bs,state))
    for choices in result:
        for a in choices:
            if a.get('x')==0:a.pop('x')
            if a.get('y')==0:a.pop('y')
    return result

defs={b['id']:b for b in DATA['blocks']};legacy=oldjson('bloodborne_blocks/definitions.json');checked=0
for old in legacy['blocks']:
    b=defs[old['id']];oldbs=oldjson('assets/bloodborne_blocks/blockstates/'+b['id']+'.json')
    newbs=json.loads((ASSETS/'blockstates'/(b['id']+'.json')).read_text())
    for sk in old['states']:
        state=dict(x.split('=') for x in sk.split(',') if x)
        upgraded={**b['default'],**state}
        assert canonical_apps(oldbs,state)==canonical_apps(newbs,upgraded),(b['id'],sk)
        checked+=1

aliases=json.loads((RES/'bloodborne_blocks/aliases.json').read_text());alias_states=0
for ident,alias in aliases['aliases'].items():
    assert set(alias['states'])==set(defs[ident]['states']),ident
    source=json.loads((ASSETS/'blockstates'/(ident+'.json')).read_text())
    target=json.loads((ASSETS/'blockstates'/(alias['target']+'.json')).read_text())
    for sk,replacement in alias['states'].items():
        assert replacement['state'] in defs[alias['target']]['states']
        values=[]
        for bs,key in [(source,sk),(target,replacement['state'])]:
            state=dict(x.split('=') for x in key.split(',') if x)
            values.append(fingerprint(json.dumps([v[0] for v in applications(bs,state)],sort_keys=True)))
        (a,origin),(b,destination)=values
        assert a==b and tuple(destination[i]+replacement['offset'][i] for i in range(3))==origin,(ident,sk)
        alias_states+=1
result={'baseline':BASELINE,'legacy_visual_states':checked,'alias_blocks':len(aliases['aliases']),'alias_states':alias_states,'minecraft_started':False}
(ROOT/'docs/migration-checks.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))
