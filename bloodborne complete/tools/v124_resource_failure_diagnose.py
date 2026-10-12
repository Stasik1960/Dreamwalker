"""Read-only diagnostic for the first final V12.4 archive audit failures."""
from pathlib import Path
import json,zipfile,sys
ROOT=Path(__file__).resolve().parents[1]
sys.stdout=(ROOT/'docs/v12.4-dedup/resource-failure-diagnostic.txt').open('w',encoding='utf-8')
report=json.loads((ROOT/'docs/v12.4-dedup/resource-audit.json').read_text(encoding='utf-8'))
with zipfile.ZipFile(report['productionJar']) as jar,zipfile.ZipFile(report['baselineJar']) as baseline:
    source=json.loads((ROOT/'src/architecture/resources/bloodborne_dw.composite.mixins.json').read_text(encoding='utf-8'))
    packed=json.loads(jar.read('bloodborne_dw.composite.mixins.json'))
    print('MIXIN_SOURCE',json.dumps(source))
    print('MIXIN_JAR',json.dumps(packed))
    print('MIXIN_OLD_JAR',json.dumps(json.loads(baseline.read('bloodborne_dw.composite.mixins.json'))))
    for p in jar.namelist():
        if 'refmap' in p:
            value=json.loads(jar.read(p))
            print('REFMAP',p,sorted(value),len(value.get('mappings',{})),sorted(value.get('mappings',{})))
    for failure in report['failures']:
        if failure['code']!='MODEL_DEPENDENCY_CLOSURE':continue
        model=failure['detail']['model']
        namespace,path=model.split(':',1)
        p=f'assets/{namespace}/models/{path}.json'
        raw=json.loads(jar.read(p));face_refs=sorted({f['texture'] for e in raw.get('elements',[]) for f in e.get('faces',{}).values()})
        print('MODEL',model,'parent',raw.get('parent'),'textures',raw.get('textures'),'faceRefs',face_refs)
    descriptors=json.loads((ROOT/'docs/v12.4-dedup/baseline-content.json').read_text(encoding='utf-8'))['descriptors']
    for failure in report['failures']:
        if failure['code']!='NONPRIMARY_DESCRIPTOR_CHANGED':continue
        path=failure['detail'];actual=json.loads(jar.read('bloodborne_dw/composite/'+path+'.json'));original=descriptors[path]
        keys=[k for k in set(actual)|set(original) if actual.get(k)!=original.get(k)]
        print('STALE_DESCRIPTOR',path,keys,'variants',len(original['variants']),'->',len(actual['variants']))
