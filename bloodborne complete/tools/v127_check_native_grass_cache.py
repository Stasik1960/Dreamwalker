"""Read pinned native color-cache fields/invalidation ABI; never launch Java."""
import ast, hashlib, json, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
JAR=Path.home()/'.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2/minecraft-merged-1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2.jar'
def write_json(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')

tree=ast.parse((ROOT/'tools/v127_check_native_opacity_cache.py').read_text(encoding='utf8'))
source=ast.unparse(next(node for node in tree.body if isinstance(node,ast.FunctionDef) and node.name=='read_class'))
source=source.replace('members()\n    methods = members()', 'fields = members()\n    methods = members()')
source=source.replace("if name not in ('getOpacity', 'initShapeCache', 'initCache'):", "if 'Code' not in attrs:")
source=source.replace("return results", "return {'fields': [{'access':a,'name':n,'descriptor':d} for a,n,d,_ in fields], 'methods':results}")
namespace={};exec(source,dict(hashlib=hashlib),namespace)
with zipfile.ZipFile(JAR) as archive:
    result={}
    for path in ('net/minecraft/client/world/ClientWorld.class','net/minecraft/client/world/BiomeColorCache.class'):
        blob=archive.read(path);info=namespace['read_class'](blob)
        info['classSha256']=hashlib.sha256(blob).hexdigest()
        info['methods']=[row for row in info['methods'] if row['method'] in ('<init>','getColor','calculateColor','reloadColor','resetChunkColor')
                         or any('BiomeColorCache.' in ref['reference'] for ref in row['references'])]
        result[path]=info
    print(json.dumps(result,indent=2))
    write_json(ROOT/'build/v127-native-grass-cache-abi.json',result)
