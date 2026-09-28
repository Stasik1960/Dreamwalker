"""Read-only checks of the new rc.2 repair copy, independent of its planner.

The ledger limits writes, never defines which new roots are legitimate. Root
authority comes from the original census/current contracts, pinned source
owner evidence, and exact same-art functional wall successors. A complete
whole-world registry/ownership check is separate from coverage completeness.
"""
from __future__ import annotations
import argparse
import gzip
import hashlib
import json
import tempfile
from collections import defaultdict
from pathlib import Path

from accepted_repair_coverage import Runtime, Observed, audit_coverage, read_bindings, parse, text
from convert_logical_world import World, hash_tree, safe_extract, parse_geometry
from verify_accepted_restore import audit_world, canonical_hash, resource_hashes_match
from verify_modded_preservation import verify as preservation
from world_io import TAG_STRING, Tag, compound

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT/'src/main/resources/bloodborne_blocks/logical'
SOURCE_SHA = 'f4b9ef510e2aacade80bb11f95cd82fe17eaed56e118280e8e055dd4aecd133c'
SOURCE_TREE = 'b859d1e7e9ecc7df03937ff50ad987dcb249d43c4faa5041c4fea47ddc62cbd8'
PART = 'bloodborne_blocks:architecture_part'
AIR = ('minecraft:air', ())


def read(path):
    raw = Path(path).read_bytes()
    return json.loads(gzip.decompress(raw) if str(path).endswith('.gz') else raw)


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream,'sha256').hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def state(value):
    name, props = parse(value)
    return name, tuple(sorted(props.items()))


def wall_equal(old, target, definitions, geometry):
    """Compare complete authored model/physics, not wall-name similarity."""
    if target[0] != 'bloodborne_blocks:building_stone_brick_wall':
        return False
    def signature(value):
        ident = value[0].split(':',1)[1]
        key = ','.join(k+'='+v for k,v in value[1])
        spec = geometry['blocks'][ident]['states'][key]
        return definitions[value[0]]['models'][key], geometry['profiles'].get(spec.get('ref'),spec)
    try:
        return signature(old) == signature(target)
    except KeyError:
        return False


def source_authority(runtime, oracle, bushes, resources, before):
    targets, sources = defaultdict(set), defaultdict(set)
    for row in oracle['occurrences']+bushes['occurrences']:
        for output in row['outputs']:
            target, error = runtime.current_target(row,output)
            if not error:
                targets[(row['dimension'],tuple(output['canonical_root']))].add(runtime.normalized(target))
    city = resources.parent/'city'
    mappings = read(city/'owner-runtime-mappings.json')['states']
    city_mapping = read(city/'migration.json')['states']
    wall_aliases = read(city/'reviewed-wall-family.json')['aliasStates']
    # Original recipe membership plus a COMPLETE current representation of
    # every component also authorizes plain non-occluded recipes. This path
    # reads archival state mappings, never the writer's candidates or ledger.
    from source_mapping_archive import archive
    from modded_world_adapter import compose_rule, compose_legacy_rule
    from convert_logical_world import Expected
    from types import SimpleNamespace
    frozen_source=archive()
    legacy={d['id']:d for d in frozen_source['definitions.json']['blocks']}
    defaults={'bloodborne_blocks:'+d['id']:d.get('default',{}) for d in frozen_source['v2\\definitions.json']['blocks']}
    cache={}
    def translate(value):
        if value[0].startswith('minecraft:'):
            ident=value[0].split(':',1)[1]
            if ident not in legacy:return None
            value=('bloodborne_blocks:'+ident,tuple(sorted({**legacy[ident].get('default',{}),**dict(value[1])}.items())))
        replacement=city_mapping.get(text(value))
        if replacement:value=(replacement['id'],tuple(sorted(replacement.get('properties',{}).items())))
        return runtime.normalized(text(value))
    for row in oracle['occurrences']+bushes['occurrences']:
        matched={};dim=row['dimension']
        for component in row['source_cells']:
            source=component['state'];p=tuple(component['position'])
            if source not in cache:
                raw=SimpleNamespace(number=0,target=state(source),source=Expected((0,0,0),state(source)),members=())
                alternatives=[]
                for pieces,_ in (compose_legacy_rule(raw,legacy),compose_rule(raw,frozen_source['v2\\migration.json'],defaults)):
                    if pieces:
                        values={piece.offset:translate(piece.state) for piece in pieces}
                        if all(v is not None for v in values.values()):alternatives.append(values)
                cache[source]=alternatives
            choices=[]
            for offsets in cache[source]:
                cells={tuple(p[i]+d[i] for i in range(3)):v for d,v in offsets.items()}
                if all(runtime.normalized(before.get(dim,c))==v for c,v in cells.items()):choices.append(cells)
            if not choices:break
            for choice in choices:
                for c,v in choice.items():matched.setdefault(c,set()).add(v)
        else:
            for c,values in matched.items():sources[(dim,c)].update(values)
    frozen = ROOT/'docs/composite-grid-repair/historical-owner-closures.json.gz'
    progress = read(frozen.parent/'membership-proof-progress.json')
    require(sha(frozen)==progress['evidenceSha256'],'frozen proof changed')
    evidence = read(frozen)
    require(evidence['oracleSha256']==sha(ROOT/'docs/composite-grid-repair/protected-world-oracle.json'),'frozen census changed')
    traces = list(evidence['closures'])
    manifest = read(ROOT/'docs/accepted-restore/complete-owner-evidence.json')
    for row in manifest['proofs']:
        path = ROOT/row['path']
        require(sha(path)==row['sha256'],'additional proof changed')
        traces.extend(read(path))
    positives = {'EXACT_HISTORICAL_OWNER_CLOSURE','HISTORICAL_OWNER_CLOSURE_WITH_OCCLUSION',
                 'HISTORICAL_OWNER_CLOSURE_WITH_PRESERVED_CONTEXT'}
    # Map each exact current source cell to its complete proved source owners.
    # Separate associations retain overlapping ownership, never select by XYZ
    # proximity or by whichever trace happened to be read last.
    objects, preserved = {}, set()
    for trace in traces:
        if trace.get('result') not in positives:
            continue
        require(not trace.get('errors') and trace['sourceSha256']==oracle['source_sha256'] and
            trace['moddedSha256']=='c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9', 'invalid positive proof')
        for obj in trace['objects']:
            p=tuple(obj['sourceRoot']);key=('minecraft:overworld',p)
            if key in objects:
                require(objects[key]['state']==obj['state'] and
                    {tuple(v) for v in objects[key]['cells']}=={tuple(v) for v in obj['cells']},'contradictory owner evidence')
            objects[key]=obj
            mapping=mappings.get(obj['state'])
            if mapping:
                target=runtime.normalized(text((mapping['id'],tuple(sorted(mapping['properties'].items())))))
                targets[key].add(target)
                wall=wall_aliases.get(mapping['id'].split(':',1)[1]+'|facing='+mapping['properties']['facing'])
                if wall:
                    targets[key].add(runtime.normalized('bloodborne_blocks:building_stone_brick_wall[connection='+wall['connection']+',facing='+wall['facing']+']'))
        for cell in trace['cells']:
            key=('minecraft:overworld',tuple(cell['position']))
            if cell.get('preservedOmissions'):
                preserved.add(key)
            actual=cell.get('actual')
            if actual:
                mapped=city_mapping.get(actual)
                value=(mapped['id'],tuple(sorted(mapped.get('properties',{}).items()))) if mapped else state(actual)
                sources[key].add(runtime.normalized(text(value)))
    return targets,sources,objects,preserved


def transaction_checks(report, before, after, runtime, targets, sources, objects, preserved, resources):
    errors=[]; changed_roots=0
    geometry=read(resources.parent/'city/geometry.json')
    oldbes,newbes=before.entities,after.entities
    rows={r['transaction']:r for r in report['transactions']}
    written={r['transaction'] for r in report['ledger']}
    require(len(written)==len(report['ledger']) and written=={k for k,r in rows.items() if r['result']=='ready'},'write transaction partition differs')
    for entry in report['ledger']:
        dim=entry['dimension']
        points={tuple(c['position']) for c in entry['changes']}
        outputs={tuple(o['targetRoot']):runtime.normalized(o['target']) for o in entry['outputs']}
        require(len(outputs)==len(entry['outputs']),'duplicate output root')
        allowed_roots=set()
        for p,target in outputs.items():
            old=runtime.normalized(before.get(dim,p))
            if runtime.normalized(after.get(dim,p))!=target:
                errors.append('output root differs: '+str((dim,p)));continue
            if old==target:
                allowed_roots.add(p);continue
            if target in targets.get((dim,p),()) or wall_equal(old,target,runtime.definitions,geometry):
                allowed_roots.add(p);changed_roots+=1
            else:
                errors.append('new root without independent source authority: '+str((dim,p,text(target))))
        require(set(outputs)==allowed_roots,'unproved new output roots: '+str(errors[:8]))
        for change in entry['changes']:
            p=tuple(change['position']);key=(dim,p)
            old=runtime.normalized(before.get(dim,p));new=runtime.normalized(after.get(dim,p))
            oldbe,newbe=oldbes.get((dim,*p)),newbes.get((dim,*p))
            if oldbe is not None and compound(oldbe).get('id')!=Tag(TAG_STRING,PART):
                if oldbe!=newbe:errors.append('foreign block entity changed: '+str(key))
            else:
                try:
                    oldowners=set(read_bindings(oldbe));newowners=set(read_bindings(newbe))
                    for name,root in oldowners-newowners:
                        if root not in outputs or runtime.normalized(before.get(dim,root))==outputs[root]:
                            errors.append('preserved owner binding removed: '+str((key,name,root)))
                except ValueError as error:
                    errors.append(str(error)+': '+str(key))
            if old==new:
                continue
            if key in preserved:
                errors.append('preserved source context changed: '+str(key))
            if old and old[0].startswith(('bloodborne_blocks:o_','bloodborne_blocks:owner_')) and p not in outputs:
                errors.append('foreign logical root removed: '+str(key))
            if old not in (AIR,(PART,())) and p not in outputs and old not in sources.get(key,()):
                # An actual old helper edge is independent ownership evidence;
                # only a root being replaced may retire that owned cell.
                owned = oldbe is not None and any(root in outputs and runtime.normalized(before.get(dim,root))!=outputs[root]
                    for _,root in read_bindings(oldbe))
                if not owned:errors.append('consumed source not in exact evidence: '+str((key,text(old))))
        # Every source owner replaced by a generated owner must be a complete
        # transaction, or its untouched part must have an explicit retained
        # context/already-present accepted owner. No arbitrary member deletion.
        for p,target in outputs.items():
            obj=objects.get((dim,p))
            if obj is None or runtime.normalized(before.get(dim,p))==target:
                continue
            for cell in map(tuple,obj['cells']):
                if cell in points or (dim,cell) in preserved:
                    continue
                old=runtime.normalized(before.get(dim,cell))
                definition=runtime.definitions.get(old[0],{}) if old else {}
                if old in sources.get((dim,cell),()) and old not in (AIR,(PART,())) and not (definition.get('logical') or definition.get('whole_owner')):
                    errors.append('known technical source remains outside transaction: '+str((dim,p,cell)))
    require(not errors,'transaction authority/preservation failed: '+str(errors[:20]))
    return {'result':'PASS','transactions':len(written),'changedRoots':changed_roots,'foreignBlockEntityLoss':0,'foreignRootLoss':0}


def verify(source,output,report,second=None,resources=RESOURCES,progress=False):
    source,output,resources=map(Path,(source,output,resources))
    require(report.get('subsetResult')=='APPLIED','applied repair report required')
    require(resource_hashes_match(resources,report['resources']),'runtime resource manifest differs')
    if source.is_file():require(sha(source)==SOURCE_SHA,'source is not published rc.2')
    else:require(canonical_hash(hash_tree(source))==SOURCE_TREE,'source directory is not pinned rc.2')
    with tempfile.TemporaryDirectory(prefix='complete-independent-') as temporary:
        root=Path(temporary)
        original=safe_extract(source,root/'source') if source.is_file() else source
        current=safe_extract(output,root/'output') if output.is_file() else output
        files=hash_tree(current)
        require(report['outputFiles']==files and report['outputTreeSha256']==canonical_hash(files),'output manifest differs')
        if progress:print('Loading independent before/after ownership indexes',flush=True)
        before=Observed(World(original,{}));after=Observed(World(current,{}));runtime=Runtime(resources)
        shapes=parse_geometry(resources.parent/'city',{})
        shapes.update({(name,tuple(tuple(p.split('=',1)) for p in key.split(',') if p)):mask for (name,key),mask in runtime.shapes.items()})
        shapes={value:set(mask)|{(0,0,0)} for value,mask in shapes.items()}
        full=audit_world(after.world,resources,shapes)
        require(not before.binding_errors and not after.binding_errors,'malformed owner NBT')
        oracle=read(ROOT/'docs/composite-grid-repair/protected-world-oracle.json')
        bushes=read(ROOT/'docs/accepted-restore/bush-source-census.json.gz')
        require(sha(ROOT/'docs/accepted-restore/bush-source-census.json.gz')=='809a6f426dd373a9562867e700a1167cac478d958bae7d118fde98bf3829b744','bush census changed')
        targets,sources,objects,preserved=source_authority(runtime,oracle,bushes,resources,before)
        authority=transaction_checks(report,before,after,runtime,targets,sources,objects,preserved,resources)
        pending_path=ROOT/'docs/accepted-restore/known-pending-vegetation.json.gz'
        require(sha(pending_path)=='a44ae7187cfc9284a7547b2eed10154002b22a71b02e88e5fe0e678201058011','pending vegetation census changed')
        pending=read(pending_path)
        require(pending['source_sha256']==oracle['source_sha256'],'pending vegetation source differs')
        coverage=audit_coverage(oracle,after,runtime,before=before,supplemental=bushes['occurrences']+pending['occurrences'])
        if progress:print('Checking every terrain/NBT field and non-terrain file',flush=True)
        keep=preservation(original,current,report)
        require(keep['result']=='PASS','non-target preservation failed: '+str(keep['errors'][:10]))
        repeated={'result':'NOT_RUN'}
        if second is not None:
            second=Path(second)
            repeated={'result':'PASS' if hash_tree(second)==files else 'FAIL','byteIdentical':hash_tree(second)==files}
            require(repeated['result']=='PASS' and report.get('secondPass',{}).get('transactions')==0,'repeat pass changed world')
        return {'format':'bloodborne-complete-repair-verification-v1','subsetResult':'PASS','coverageCompleteness':coverage['coverageCompleteness'],
            'releaseReady':False,'sourceSha256':SOURCE_SHA,'outputTreeSha256':canonical_hash(files),
            'authority':authority,'fullWorld':full,'preservation':keep,'secondPass':repeated,'coverage':coverage}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source',type=Path);parser.add_argument('output',type=Path);parser.add_argument('report',type=Path)
    parser.add_argument('--second',type=Path);parser.add_argument('--result',type=Path,required=True)
    args=parser.parse_args()
    try:result=verify(args.source,args.output,read(args.report),args.second,progress=True)
    except (ValueError,KeyError,TypeError,AssertionError) as error:result={'subsetResult':'FAIL','coverageCompleteness':'NOT_VERIFIED','errors':[str(error)]}
    args.result.parent.mkdir(parents=True,exist_ok=True)
    args.result.write_text(json.dumps(result,ensure_ascii=False,sort_keys=True,separators=(',',':'))+'\n',encoding='utf8')
    print(json.dumps({k:v for k,v in result.items() if k!='coverage'}),flush=True)
    raise SystemExit(result['subsetResult']!='PASS')
