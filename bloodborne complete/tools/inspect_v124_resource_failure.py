from pathlib import Path
import json,zipfile
ROOT=Path(__file__).resolve().parents[1]; DOC=ROOT/'docs/v12.4-dedup'
base=json.loads((DOC/'baseline-content.json').read_text(encoding='utf8'))
report=json.loads((DOC/'resource-audit.json').read_text(encoding='utf8'))
def diff(a,b,path=''):
    if type(a)!=type(b):return [(path,a,b)]
    if isinstance(a,dict):
        return [(path+'/'+k,a.get(k),b.get(k)) for k in set(a)^set(b)]+sum((diff(a[k],b[k],path+'/'+k) for k in a.keys()&b.keys()),[])
    if isinstance(a,list):
        if len(a)!=len(b):return [(path+'/length',len(a),len(b))]
        return sum((diff(x,y,path+'/'+str(i)) for i,(x,y) in enumerate(zip(a,b))),[])
    return [] if a==b else [(path,a,b)]
with zipfile.ZipFile(ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.4-dedup.jar') as jar:
    for issue in report['failures']:
        if issue['code']=='NONPRIMARY_DESCRIPTOR_CHANGED':
            path=issue['detail'];current=json.loads(jar.read('bloodborne_dw/composite/'+path+'.json'))
            rows=diff(base['descriptors'][path],current);print(path,len(rows),json.dumps(rows[:6],ensure_ascii=False))
    name='bloodborne_dw.composite.mixins.json'
    print('MIXINS source', (ROOT/'src/architecture/resources'/name).read_text(encoding='utf8'))
    print('MIXINS jar',jar.read(name).decode())
