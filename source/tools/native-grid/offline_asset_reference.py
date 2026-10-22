"""Addressed offline source-data references; never starts a game or reads a map.

Only saved old.chunk_cache payloads, immutable exports and plan metadata are read.
The result cannot assert baseline gameplay, compatibility or benchmark success.
"""
from __future__ import annotations
import argparse, base64, hashlib, json, math, sqlite3, struct, subprocess, sys, uuid, zipfile
from pathlib import Path

PROJECT=Path(__file__).resolve().parents[2]
ROOT=PROJECT.parent
sys.path.insert(0,str(PROJECT/'tools'))
from world_io import Tag,NbtFile,compound,decode_nbt,encode_nbt,read_nbt,section_blocks
from export_cells import canonical,state_key,canonical_boxes

def read(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def sha(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as f:
        for block in iter(lambda:f.read(1<<20),b''):h.update(block)
    return h.hexdigest()
def ro(path):
    db=sqlite3.connect('file:'+Path(path).absolute().as_posix()+'?mode=ro',uri=True)
    db.row_factory=sqlite3.Row
    return db
def owner_id(tag):return str(uuid.UUID(bytes=struct.pack('>4i',*compound(tag)['uuid'].value)))
def owners(fields):
    if 'OwnerDataV1' in fields:
        fields=compound(decode_nbt(bytes(fields['OwnerDataV1'].value)).root)
    return fields.get('owners',Tag(9,[],10)).value
def boxes(tag):return [[float(v.value) for v in row.value] for row in tag.value]
def state(tag):
    p=compound(tag)
    return {'registryId':p['Name'].value,'properties':{k:v.value for k,v in p.get('Properties',Tag(10,{})).value.items()}}
def typed(tag):
    value=tag.value
    if tag.type==10:value={k:typed(v) for k,v in sorted(value.items())}
    elif tag.type==9:value=[typed(v) for v in value]
    elif tag.type==7:value=base64.b64encode(bytes(value)).decode()
    return {'type':tag.type,**({'listType':tag.list_type} if tag.type==9 else {}),'value':value}
def sorted_tag(tag):
    if tag.type==10:return Tag(10,{k:sorted_tag(v) for k,v in sorted(tag.value.items())})
    if tag.type==9:return Tag(9,[sorted_tag(v) for v in tag.value],tag.list_type)
    return tag
def nbt64(tag):return base64.b64encode(encode_nbt(NbtFile('',sorted_tag(tag)))).decode()
def runtime_contribution(tag):
    """CompositeData.data decodes/recanonicalizes rootData's payload bytes."""
    fields=dict(compound(tag));rd=dict(compound(fields['rootData']));raw=bytes(rd['payload'].value)
    rd['payload']=Tag(7,encode_nbt(NbtFile('',sorted_tag(decode_nbt(raw).root if raw else Tag(10,{})))))
    fields['rootData']=Tag(10,rd)
    return Tag(10,fields)
def state_string(value):
    props=value['properties']
    return 'Block{'+value['registryId']+'}'+('['+','.join(k+'='+props[k] for k in sorted(props))+']' if props else '')

class Inputs:
    def __init__(self,plan):
        self.plan=plan;self.db=ro(ROOT/'research/native-grid-plan/plan.sqlite')
        self.binding=json.loads(self.db.execute("SELECT value FROM meta WHERE key='binding'").fetchone()[0])
        self.old=ro(Path(self.binding['oldPlan'])/'plan.sqlite')
        self.fragments=ro(ROOT/'research/fragments.sqlite')
        self.chunks={};self.provenance={};self.descriptors={};self.shards={};self.shard_provenance={}
        self.manifest=read(self.binding['copyManifest'])
        if sha(self.binding['copyManifest'])!=self.binding['copyManifestSha256']:raise ValueError('Immutable manifest changed')
        self.jar=zipfile.ZipFile(plan['productionJar'])
    def chunk(self,cx,cz):
        key=(cx,cz)
        if key not in self.chunks:
            row=self.old.execute("SELECT region_path,region_hash,compression,payload FROM chunk_cache WHERE dim='.' AND x=? AND z=?",key).fetchone()
            if row is None:raise ValueError('Missing existing cached chunk '+str(key))
            doc=decode_nbt(row['payload'],compressed={1:'gzip',2:'zlib',3:None}[row['compression']]);fields=compound(doc.root)
            sections={compound(s)['Y'].value:section_blocks(s) for s in fields['sections'].value}
            entities={tuple(compound(be)[k].value for k in ('x','y','z')):be for be in fields.get('block_entities',Tag(9,[],10)).value}
            self.chunks[key]=(sections,entities)
            self.provenance[key]={'chunk':[cx,cz],'region':row['region_path'],'boundRegionSha256':row['region_hash'],'compression':row['compression'],'cachedCompressedPayloadSha256':hashlib.sha256(row['payload']).hexdigest()}
        return self.chunks[key]
    def cell(self,pos):
        x,y,z=pos;sections,bes=self.chunk(x//16,z//16);sec=sections.get(y//16)
        actual={'registryId':'minecraft:air','properties':{}} if sec is None else state(sec[0][sec[1][(x&15)+16*(z&15)+256*(y&15)]])
        return actual,bes.get(tuple(pos))
    def descriptor(self,registry):
        if registry not in self.descriptors:
            path='bloodborne_dw/composite/'+registry.split(':')[-1]+'.json'
            self.descriptors[registry]=json.loads(self.jar.read(path)) if path in self.jar.namelist() else None
        return self.descriptors[registry]
    def ledger(self,pos):
        key=(pos[0]//16,pos[2]//16)
        if key not in self.shards:
            rel='data/bloodborne_dw_owner_shards/owners-%s.%s.nbt'%key
            wanted=self.manifest['files'].get(rel)
            if wanted is None:raise ValueError('Exact addressed source shard is not manifest-bound: '+rel)
            path=Path(self.plan['targetWorldPath'])/rel;kind='unchanged_baseline_copy'
            if not path.is_file() or sha(path)!=wanted['sha256']:
                path=Path(self.binding['source'])/rel;kind='immutable_original_source'
            if path.stat().st_size!=wanted['bytes'] or sha(path)!=wanted['sha256']:raise ValueError('Addressed shard immutable SHA mismatch: '+str(path))
            root=compound(read_nbt(path).root)
            if 'data' in root:root=compound(root['data'])
            if not root.get('Dictionary',Tag(3,0)).value:raise ValueError('Not a saved dictionary shard: '+str(path))
            table={}
            for raw in root['cells'].value:
                cell=compound(raw);point=tuple(cell['pos'].value);values=[]
                for ref in cell['refs'].value:
                    oi,di,si=ref.value;s=compound(root['shapeTable'].value[si])
                    values.append(Tag(10,{'owner':root['ownerTable'].value[oi],'rootData':root['dataTable'].value[di],'collision':s['collision'],'selection':s['selection']}))
                table[point]=values
            self.shards[key]=table
            self.shard_provenance[key]={'chunk':list(key),'relativePath':rel,'readPath':str(path),'origin':kind,'bytes':wanted['bytes'],'sha256':wanted['sha256'],'manifestSha256':self.binding['copyManifestSha256']}
        return self.shards[key].get(tuple(pos),[])
    def actual_entries(self,pos):
        raw=self.ledger(pos)
        if raw:return raw,'saved_dictionary_ledger'
        _,be=self.cell(pos)
        return (owners(compound(be)) if be else []),'saved_block_entity_fallback'
    def inventory_cell(self,pos):
        actual,be=self.cell(pos);out={'pos':list(pos),'state':actual,'be':None,'contributions':[]}
        native=self.fragments.execute('SELECT data FROM native WHERE state=?',(state_key(actual),)).fetchone()
        out['nativePhysicsExported']=native is not None
        if native:out['nativeShapes']={k:json.loads(native[0]).get(k) for k in ('collision','outline')}
        if be is None:return out
        fields=compound(be);out['be']={'id':fields.get('id',Tag(8,'')).value,'keys':sorted(fields)}
        for raw in owners(fields):
            c=compound(raw);o=compound(c['owner']);rd=compound(c['rootData']);oid=owner_id(c['owner']);registry=o['id'].value
            value={'ownerUuid':oid,'registryId':registry,'root':o['root'].value,'properties':{k:v.value for k,v in compound(rd['properties']).items()},'collision':boxes(c['collision']),'selection':boxes(c['selection'])}
            signed=json.dumps(list(struct.unpack('>4i',uuid.UUID(oid).bytes)),separators=(',',':'))
            route=self.db.execute("SELECT pose,kind,reason FROM objects WHERE dim='.' AND uuid=?",(signed,)).fetchone()
            value['route']=dict(route) if route else None
            if route:
                offset=tuple(a-b for a,b in zip(pos,o['root'].value));frag=self.fragments.execute('SELECT data FROM fragments WHERE pose=? AND x=? AND y=? AND z=?',(route['pose'],*offset)).fetchone()
                value['exportedFragmentShapes']={k:json.loads(frag[0]).get(k) for k in ('collision','outline')} if frag else None
            descriptor=self.descriptor(registry)
            value['descriptorSimplePhysicalCollision']=descriptor.get('simplePhysicalCollision') if descriptor else None
            out['contributions'].append(value)
        return out

    def raw_summary(self,raw,pos):
        c=compound(raw);o=compound(c['owner']);rd=compound(c['rootData']);oid=owner_id(c['owner'])
        signed=json.dumps(list(struct.unpack('>4i',uuid.UUID(oid).bytes)),separators=(',',':'))
        route=self.db.execute("SELECT pose,kind,reason FROM objects WHERE dim='.' AND uuid=?",(signed,)).fetchone()
        value={'ownerUuid':oid,'registryId':o['id'].value,'root':list(o['root'].value),'collision':boxes(c['collision']),'selection':boxes(c['selection']),'route':dict(route) if route else None}
        _,root_be=self.cell(o['root'].value)
        resident=compound(root_be).get('resident') if root_be else None
        value['residentValid']=resident is not None and owner_id(resident)==oid
        value['rootInDeclaredSourceChunks']=[o['root'].value[0]//16,o['root'].value[2]//16] in self.plan['sourceChunks']
        offset=tuple(a-b for a,b in zip(pos,o['root'].value))
        frag=self.fragments.execute('SELECT data FROM fragments WHERE pose=? AND x=? AND y=? AND z=?',(route['pose'],*offset)).fetchone() if route else None
        value['exportedFragment']=json.loads(frag[0]) if frag else None
        descriptor=self.descriptor(o['id'].value)
        value['simplePhysicalCollision']=descriptor.get('simplePhysicalCollision',False) if descriptor else False
        if route:
            expected=json.loads(self.db.execute('SELECT data FROM poses WHERE key=?',(route['pose'],)).fetchone()[0])
            if expected['registryId']!=rd['id'].value or expected['properties']!={k:v.value for k,v in compound(rd['properties']).items()}:raise ValueError('Exact source state/pose properties mismatch: '+oid)
            payload=compound(decode_nbt(bytes(rd['payload'].value)).root)
            actual_shift=[v.value for v in payload.get('SourceShift',Tag(9,[Tag(6,0.)]*3,6)).value]
            if actual_shift!=expected.get('shift',[0,0,0]) or payload.get('MountY',Tag(6,0)).value!=expected.get('mountY',0):raise ValueError('Exact source shift/mount pose mismatch: '+oid)
        return value

def shifted_boxes(values,delta):return [[b[i]+delta[i%3] for i in range(6)] for b in values]

def exact_window(inputs,window):
    cells=[];total={'collision':[],'outline':[]}
    for pos in window['positions']:
        actual,be=inputs.cell(pos);entries,origin=inputs.actual_entries(pos);summaries=[inputs.raw_summary(raw,pos) for raw in entries]
        local={'collision':[],'outline':[]};proofs=[]
        compact=actual['properties'].get('compact')=='true'
        if compact or not entries:
            native=inputs.fragments.execute('SELECT data FROM native WHERE state=?',(state_key(actual),)).fetchone()
            if native is None:raise ValueError('Exact native physics gap at '+str(pos)+': '+state_key(actual))
            native=json.loads(native[0])
            for field in local:local[field].extend(native[field])
            proofs.append({'kind':'actual_native_state_physics_export','state':actual,'recordSha256':hashlib.sha256(canonical(native).encode()).hexdigest()})
        for c in summaries:
            if not c['residentValid']:
                if c['rootInDeclaredSourceChunks']:
                    proofs.append({'kind':'stale_owner_excluded_by_validOrDeferred','ownerUuid':c['ownerUuid'],'root':c['root']});continue
                raise ValueError('Owner validity depends on an undeclared loaded root at '+str(pos)+': '+c['ownerUuid'])
            frag=c['exportedFragment']
            if not c['simplePhysicalCollision'] or frag is None:raise ValueError('Exact worldless source outline gap at '+str(pos)+': '+c['ownerUuid']+' '+c['registryId'])
            if canonical_boxes(c['collision'])!=canonical_boxes(frag['collision']):raise ValueError('Saved collision differs from actual source export at '+str(pos)+': '+c['ownerUuid'])
            local['collision'].extend(c['collision']);local['outline'].extend(frag['outline'])
            proofs.append({'kind':'saved_valid_simple_source_collision_and_exported_exact_selection','ownerUuid':c['ownerUuid'],'root':c['root'],'pose':c['route']['pose'],'fragmentSha256':hashlib.sha256(canonical(frag).encode()).hexdigest()})
        for field in local:
            local[field]=canonical_boxes(local[field]);total[field].extend(shifted_boxes(local[field],[a-b for a,b in zip(pos,window['origin'])]))
        cells.append({'pos':pos,'state':actual,'contributionOrigin':origin,'actualSavedContributionCount':len(entries),'collision':local['collision'],'outline':local['outline'],'proofs':proofs})
    return {'key':window['key'],'origin':window['origin'],'positions':window['positions'],'collision':canonical_boxes(total['collision']),'outline':canonical_boxes(total['outline']),'cells':cells,'representation':'Exact union of listed cuboids, relative to origin; no hull, voxelization or rounding'}

def protected_cell(inputs,row):
    pos=row['pos'];actual,be=inputs.cell(pos);entries,origin=inputs.actual_entries(pos)
    selected=[raw for raw in entries if owner_id(compound(raw)['owner'])==row['protectedOwnerUuid']]
    if len(selected)!=1:raise ValueError('Exactly one selected protected owner required at '+str(pos))
    fields=compound(be) if be else {}
    outer=Tag(10,{k:v for k,v in fields.items() if k not in ('owners','OwnerDataV1','NativeGridBase')})
    be_selected=[raw for raw in owners(fields) if owner_id(compound(raw)['owner'])==row['protectedOwnerUuid']]
    runtime_outer=Tag(10,{k:v for k,v in outer.value.items() if k!='keepPacked'})
    if be:runtime_outer.value['owners']=Tag(9,[runtime_contribution(raw) for raw in be_selected],10)
    native=inputs.fragments.execute('SELECT data FROM native WHERE state=?',(state_key(actual),)).fetchone()
    if native:
        if json.loads(native[0]).get('fluid') is not None:raise ValueError('Protected nonempty fluid needs an exact FluidState string export at '+str(pos))
        fluid_proof='Exact actual native physics export has fluid=null'
    elif actual['registryId']=='bloodborne_dw:prototype_thin_window':
        fluid_proof='Immutable ThinWindowRootBlock inherits CompositeRootBlock.getFluidState; ImportedSourceLightingV127.id excludes prototype_ IDs and returns native Block.EMPTY fluid fallback'
    else:raise ValueError('Protected fluid route unproved at '+str(pos))
    reference={'state':actual,'fluid':{'registryId':'minecraft:empty','properties':{}},'nbt':nbt64(runtime_outer) if be else '', 'owners':nbt64(Tag(10,{'owners':Tag(9,[runtime_contribution(raw) for raw in selected],10)}))}
    return {'pos':pos,'protectedOwnerUuid':row['protectedOwnerUuid'],'reference':reference,'state':actual,'stateCanonicalSha256':hashlib.sha256(canonical(actual).encode()).hexdigest(),'savedOuterNBT':typed(outer) if be else None,'savedOuterNBTCanonicalSha256':hashlib.sha256(canonical(typed(outer)).encode()).hexdigest() if be else None,'savedOuterNbtKind':'SAVED_CHUNK_NBT_WITH_OWNER_ENVELOPE_SEPARATED; keepPacked is a disk loader flag, not gameplay createNbt output','runtimeNormalization':'Immutable CompositeBlockEntity.readNbt/writeNbt retains payload/resident/owners and identifying id/x/y/z; excludes keepPacked. Owner list is decoded and recanonicalized through CompositeData, then restricted to the named owner by protectedMatches.','fluidProof':fluid_proof,'selectedOwnerOrigin':origin,'selectedOwnerContributions':typed(Tag(9,selected,10)),'selectedOwnerContributionsCanonicalSha256':hashlib.sha256(canonical(typed(Tag(9,selected,10))).encode()).hexdigest(),'selectedOwnerBlockEntityContributions':typed(Tag(9,be_selected,10)),'comparisonScope':'Exact source state, outer saved BE fields, and only the named protected v12 owner; no unrelated owners are included'}

def primary_code(inputs,out):
    classes=('CompositeRuntime','CompositeRootBlock','ImportedCompactBlock','ImportedPhysicalShapes','ImportedSelectionShapes','CompositeShapes','CompositeBlockEntity','CompositeData','CompositeOwnerNbt','ShardedCompositeLedger','ThinWindowRootBlock','ImportedSourceLightingV127','mixin.CompositeShapeMixin')
    out.mkdir(parents=True,exist_ok=True);records=[]
    javap=Path(r'C:\Users\vakir\Downloads\jdk-21.0.10-full\bin\javap.exe')
    for name in classes:
        full='dev.dreamwalker.bloodbornedw.composite.'+name;entry=full.replace('.','/')+'.class'
        target=out/(name.replace('.','-')+'.txt')
        process=subprocess.run([str(javap),'-classpath',inputs.plan['productionJar'],'-c','-p',full],capture_output=True,text=True)
        if process.returncode:raise ValueError('Addressed immutable baseline javap failed: '+full+process.stderr)
        target.write_text(process.stdout,encoding='utf-8')
        records.append({'class':full,'sourceJarClassSha256':hashlib.sha256(inputs.jar.read(entry)).hexdigest(),'bytecodeFile':str(target),'bytecodeTextSha256':sha(target)})
    minecraft=Path(r'C:\Users\vakir\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged\1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2\minecraft-merged-1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2.jar')
    with zipfile.ZipFile(minecraft) as mapped:
        for name in ('net.minecraft.state.StateManager','net.minecraft.state.State','net.minecraft.block.Block','net.minecraft.fluid.Fluid','net.minecraft.block.entity.BlockEntity'):
            target=out/('mc-'+name.rsplit('.',1)[-1]+'.txt')
            process=subprocess.run([str(javap),'-classpath',str(minecraft),'-c','-p',name],capture_output=True,text=True)
            if process.returncode:raise ValueError('Primary Minecraft bytecode read failed: '+name+process.stderr)
            target.write_text(process.stdout,encoding='utf-8')
            records.append({'class':name,'mappedMinecraftJar':str(minecraft),'sourceJarClassSha256':hashlib.sha256(mapped.read(name.replace('.','/')+'.class')).hexdigest(),'bytecodeFile':str(target),'bytecodeTextSha256':sha(target)})
    return records

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--plan',type=Path,default=ROOT/'research/function-qa/baseline-reference-plan.json')
    p.add_argument('--out',type=Path,default=ROOT/'research/function-qa/offline-source-reference-inventory.json')
    p.add_argument('--reference',action='store_true',help='Produce exact finite references after coverage checks, never gameplay PASS')
    args=p.parse_args();plan=read(args.plan);inputs=Inputs(plan)
    cells=sorted({tuple(pos) for window in plan['windows'] for pos in window['positions']}|{tuple(row['pos']) for row in plan['protected']})
    result={'mode':'OFFLINE_ASSET_REFERENCE_NOT_GAMEPLAY','status':'COVERAGE_INVENTORY_ONLY','baselineGameplayStatus':'NOT_ACQUIRED_OOM','baselineFullTargetStatus':'FAIL_OOM','mapFilesRead':0,'gameProcessesStarted':0,'planSha256':sha(args.plan),'productionJarSha256':sha(plan['productionJar']),'binding':inputs.binding,'cells':[inputs.inventory_cell(pos) for pos in cells]}
    if args.reference:
        result['status']='OFFLINE_ASSET_REFERENCE_NOT_GAMEPLAY'
        result['schemaVersion']=1
        result['variant']='baseline'
        result['baselinePlanSha256']=sha(args.plan)
        result['sourceJarSha256']=result['productionJarSha256']
        result['sourceBinding']=inputs.binding
        result['windows']=[exact_window(inputs,row) for row in plan['windows']]
        result['protected']=[protected_cell(inputs,row) for row in plan['protected']]
        result['originalWitnesses']=[{'pos':row['pos'],'reference':row['reference']} for row in result['protected']]
        result.pop('cells')
        result['sourceFilesRead']={'cachedChunkDataOnly':True,'addressedOwnerShards':list(inputs.shard_provenance.values()),'originalSourceShardFilesRead':sum(row['origin']=='immutable_original_source' for row in inputs.shard_provenance.values())}
        result['mapFilesRead']=len(inputs.shard_provenance)
        result['primaryBaselineCode']=primary_code(inputs,args.out.parent/'offline-reference-bytecode')
        result['fragmentStoreMetadata']={row[0]:json.loads(row[1]) for row in inputs.fragments.execute('SELECT name,data FROM metadata')}
        export_paths=[ROOT/'research'/folder/name for folder,name in (('geometry-final','binding.json'),('geometry-final','launch-receipt.json'),('geometry-supplement','binding.json'),('geometry-supplement','launch-receipt.json'),('geometry-supplement','merge-receipt.json'),('pilot-native-physics-clean','merge-receipt.json'))]
        result['exportBindings']=[{'path':str(path),'sha256':sha(path),'data':read(path)} for path in export_paths]
        for row in result['exportBindings']:
            if row['data'].get('productionSha256',result['sourceJarSha256'])!=result['sourceJarSha256']:raise ValueError('Reference export production JAR binding differs: '+row['path'])
        result['unknownOrMissingPhysicsRoutes']=[]
        result['counts']={'windows':len(result['windows']),'windowCells':sum(len(row['positions']) for row in result['windows']),'protectedCells':len(result['protected']),'sourcePoseExports':inputs.fragments.execute('SELECT count(*) FROM poses').fetchone()[0],'nativeStateExports':inputs.fragments.execute('SELECT count(*) FROM native').fetchone()[0]}
        result['excludedAssertions']=['baseline gameplay PASS','WorldEdit compatibility PASS','Lithium compatibility PASS','full-target startup PASS','benchmark result','live owner cleanup behavior','live open/close/climbing/light/pick/break/reload actions']
        result['authoringToolSha256']=sha(__file__)
    result['cachedChunksUsed']=list(inputs.provenance.values());result['sourceCacheChunkCount']=inputs.old.execute('SELECT count(*) FROM chunk_cache').fetchone()[0]
    args.out.parent.mkdir(parents=True,exist_ok=True);args.out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':result['status'],'cells':len(cells),'cachedChunksUsed':len(inputs.chunks),'sourceCacheChunkCount':result['sourceCacheChunkCount'],'out':str(args.out)}))

if __name__=='__main__':main()
