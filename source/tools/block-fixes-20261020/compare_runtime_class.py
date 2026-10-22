from common import *
log_to('runtime-class-before');raw=PROJECT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-owner32-static-city.6-chunk-mesh.3-materials.1-streaming.7-blocks.1.jar';n='dev/dreamwalker/bloodbornedw/composite/CompositeRuntime.class'
with zipfile.ZipFile(BASE) as a,zipfile.ZipFile(raw) as b:
 x=a.read(n);y=b.read(n);write(OUT/'runtime-class-before.json',{'identical':x==y,'baseClassSha256':hashlib.sha256(x).hexdigest(),'compiledBeforeEditClassSha256':hashlib.sha256(y).hexdigest()});print('IDENTICAL',x==y)
