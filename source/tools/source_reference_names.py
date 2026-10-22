"""Resolve official source names to installed Forge 1.18.2 SRG members."""
from pathlib import Path
import re,json
ROOT=Path(__file__).resolve().parents[1]
REF=ROOT/'build/source-reference-forge-1.18.2'

def names():
    moj=REF/'libraries/net/minecraft/server/1.18.2-20220404.173914/server-1.18.2-20220404.173914-mappings.txt'
    tsrg=REF/'libraries/de/oceanlabs/mcp/mcp_config/1.18.2-20220404.173914/mcp_config-1.18.2-20220404.173914-mappings-merged.txt'
    official={};current=None
    for line in moj.read_text(encoding='utf-8').splitlines():
        if not line or line.startswith('#'):continue
        if not line.startswith(' '):
            current,obfuscated=line.rstrip(':').split(' -> ');official[current]={'obf':obfuscated,'methods':{},'fields':{}}
        elif current:
            if ' -> ' not in line:continue
            left,right=line.strip().split(' -> ')
            if '(' in left:
                clean=re.sub(r'^(\d+:\d+:)+','',left);head,parameters=clean.rstrip(')').split('(')
                method=head.split(' ')[-1];official[current]['methods'].setdefault(method,[]).append((parameters,right))
            else:official[current]['fields'][left.split(' ')[-1]]=right
    srg={};current=None
    for line in tsrg.read_text(encoding='utf-8').splitlines()[1:]:
        if not line.startswith('\t'):
            left,right=line.split(' ');current=left.replace('/','.');srg[current]={'methods':{},'fields':{}}
        elif line.startswith('\t') and not line.startswith('\t\t'):
            fields=line.strip().split()
            if len(fields)==3:srg[current]['methods'][(fields[0],fields[1])]=fields[2]
            elif len(fields)==2:srg[current]['fields'][fields[0]]=fields[1]
    def method(cls,name,parameters):
        source=official[cls];items=[obf for args,obf in source['methods'][name] if args==parameters]
        assert len(items)==1,(cls,name,parameters,items)
        primitives={'boolean':'Z','byte':'B','char':'C','short':'S','int':'I','long':'J','float':'F','double':'D'}
        def descriptor(param):
            if param.endswith('[]'):return '['+descriptor(param[:-2])
            return primitives[param] if param in primitives else 'L'+official.get(param,{'obf':param})['obf'].replace('.','/')+';'
        signature='('+''.join(descriptor(param) for param in parameters.split(',') if param)+')'
        targets={target for (obf,desc),target in srg[source['obf']]['methods'].items() if obf==items[0] and desc.startswith(signature)}
        assert len(targets)==1,(cls,name,targets)
        return next(iter(targets))
    def field(cls,name):return srg[official[cls]['obf']]['fields'][official[cls]['fields'][name]]
    result={
        'overworld':method('net.minecraft.server.MinecraftServer','overworld',''),
        'getBlockState':method('net.minecraft.world.level.Level','getBlockState','net.minecraft.core.BlockPos'),
        'collisionShape':method('net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase','getCollisionShape','net.minecraft.world.level.BlockGetter,net.minecraft.core.BlockPos,net.minecraft.world.phys.shapes.CollisionContext'),
        'outlineShape':method('net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase','getShape','net.minecraft.world.level.BlockGetter,net.minecraft.core.BlockPos,net.minecraft.world.phys.shapes.CollisionContext'),
        'lightEmission':method('net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase','getLightEmission',''),
        'isAir':method('net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase','isAir',''),
        'getBlock':method('net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase','getBlock',''),
        'getMaterial':method('net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase','getMaterial',''),
        'materialReplaceable':method('net.minecraft.world.level.material.Material','isReplaceable',''),
        'toAabbs':method('net.minecraft.world.phys.shapes.VoxelShape','toAabbs',''),
        'contextOf':method('net.minecraft.world.phys.shapes.CollisionContext','of','net.minecraft.world.entity.Entity'),
        'setPos':method('net.minecraft.world.entity.Entity','setPos','double,double,double'),
        'move':method('net.minecraft.world.entity.Entity','move','net.minecraft.world.entity.MoverType,net.minecraft.world.phys.Vec3'),
        'getX':method('net.minecraft.world.entity.Entity','getX',''),
        'getY':method('net.minecraft.world.entity.Entity','getY',''),
        'getZ':method('net.minecraft.world.entity.Entity','getZ',''),
        'getEntityByUuid':method('net.minecraft.server.level.ServerLevel','getEntity','java.util.UUID'),
        'getEntityBox':method('net.minecraft.world.entity.Entity','getBoundingBox',''),
        'entityCollidable':method('net.minecraft.world.entity.Entity','canBeCollidedWith',''),
        'getYaw':method('net.minecraft.world.entity.Entity','getYRot',''),
        'setYaw':method('net.minecraft.world.entity.Entity','setYRot','float'),
        'moverSelf':field('net.minecraft.world.entity.MoverType','SELF'),
        'boxFields':[field('net.minecraft.world.phys.AABB',n) for n in ['minX','minY','minZ','maxX','maxY','maxZ']],
    }
    return result

def count_args(desc):
    signature=desc.split(')')[0][1:]
    return len(re.findall(r'\[*L[^;]+;|\[*[ZBCSIJFD]',signature))

def client_names(client_root):
    moj=ROOT/'inputs/extracted/vanilla-1.18.2/client-mappings.txt'
    tsrg=client_root/'libraries/de/oceanlabs/mcp/mcp_config/1.18.2-20220404.173914/mcp_config-1.18.2-20220404.173914-mappings-merged.txt'
    current=None;obfuscated=None;selected={}
    for line in moj.read_text(encoding='utf-8').splitlines():
        if not line or line.startswith('#'):continue
        if not line.startswith(' '):
            current,obf=line.rstrip(':').split(' -> ')
            if current=='net.minecraft.client.Minecraft':obfuscated=obf
        elif current=='net.minecraft.client.Minecraft':
            if 'getInstance() -> ' in line:selected['getInstance']=(line.split(' -> ')[1],'()')
            elif 'loadLevel(java.lang.String) -> ' in line:selected['loadLevel']=(line.split(' -> ')[1],'(Ljava/lang/String;)')
            elif ' stop() -> ' in line:selected['stop']=(line.split(' -> ')[1],'()')
            elif ' screen -> ' in line:selected['screen']=(line.split(' -> ')[1],None)
    current=None;result={}
    for line in tsrg.read_text(encoding='utf-8').splitlines()[1:]:
        if not line.startswith('\t'):current=line.split(' ')[0]
        elif current==obfuscated and not line.startswith('\t\t'):
            fields=line.strip().split()
            for key,(obf,desc) in selected.items():
                if fields[0]==obf and (len(fields)==2 if desc is None else len(fields)==3 and fields[1].startswith(desc)):result[key]=fields[-1]
    assert set(result)=={'getInstance','loadLevel','stop','screen'},result
    return result

if __name__=='__main__':print(json.dumps(names(),indent=2))
