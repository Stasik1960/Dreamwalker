"""Bind existing functional evidence to a strictly client-render-only patch."""
import hashlib,json,traceback,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024'
OLD='5ad7cda65c62c6682d34746fb40534b5647f678306739660103fbe7fc0a779f1'
def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for b in iter(lambda:stream.read(1<<20),b''):h.update(b)
    return h.hexdigest()
def content(jar,name):
    h=hashlib.sha256()
    with jar.open(name) as stream:
        for b in iter(lambda:stream.read(1<<20),b''):h.update(b)
    return h.hexdigest()
def ref(path):return {'path':str(path),'sha256':digest(path)}
def main():
    old=T/'runtime-history'/(OLD+'.jar');new=T/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.1.jar'
    assert digest(old)==OLD
    changed=[];unchanged=0
    with zipfile.ZipFile(old) as before,zipfile.ZipFile(new) as after:
        names=set(before.namelist());assert names==set(after.namelist())
        for name in sorted(names):
            if name.endswith('/'):continue
            b,a=content(before,name),content(after,name)
            if b!=a:changed.append({'entry':name,'before':b,'after':a})
            else:unchanged+=1
    prefix='dev/dreamwalker/bloodbornedw/composite/StaticArchitectureClient'
    assert changed and all(r['entry']==prefix+'.class' or r['entry'].startswith(prefix+'$') and r['entry'].endswith('.class') for r in changed),changed
    reports=[T/'server-checks.json',T/'integration-checks.json',T/'map-remap.json',T/'region-repack.json']
    result={'status':'EXACT_SHARED_BYTECODE_AND_DATA_UNCHANGED_CLIENT_LAYER_ONLY','before':ref(old),'after':ref(new),
        'changedEntries':changed,'unchangedEntriesSha256Verified':unchanged,'reusedEvidence':[ref(p) for p in reports],
        'scope':'All physics, optical/light predicates, blocks/registries, chunk/network persistence, items, whole templates, catalog, meshes and v12 assets byte-identical. Client source-quad layer dispatch only. New client rendering still requires its own route.'}
    (T/'client-layer-change-proof.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':
    try:main()
    except BaseException:
        (T/'client-layer-change-proof-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
