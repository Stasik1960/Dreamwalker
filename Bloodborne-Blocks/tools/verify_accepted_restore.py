"""Independent read-only proof of the accepted RC1 bridge, not its writer ledger.

Reconstruct ownership from immutable MODDED evidence and current contracts;
prove additional RC1 dependencies through exact helper links; compare typed
NBT inside each transaction and all terrain/files outside it. The writer is
deliberately not imported.
"""
from __future__ import annotations
import argparse
import gzip
import hashlib
import json
import subprocess
import tempfile
from collections import Counter, defaultdict
from pathlib import Path
from convert_logical_world import (World, PART, add, as_tag_state, block_pos_long,
    unpack_pos_long, definition_hashes, hash_tree, parse_geometry, safe_extract)
from city_palette import helper_bindings
from logical_contract_v2 import load_contracts
from world_io import (Tag, TAG_COMPOUND, TAG_INT, TAG_LIST, TAG_LONG, TAG_STRING,
    block_state_key, compound, section_blocks)

ROOT = Path(__file__).resolve().parents[1]
RC1 = ROOT/'releases/Bloodborne-Blocks/2.1.0-rc.1/Bloodborne-City-2.1.0-rc.1.zip'
MODDED = ROOT/'reference-inputs/latest-modded-world.zip'
ACCEPTED = ROOT/'docs/catalog-city-continuation/final/first.json.gz'
RC1_SHA = '749853aeb19ba8ea823bf1f683476985b74e2fce2746143eeacc3225eb0ed0a9'
MODDED_SHA = 'c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9'
ACCEPTED_SHA = 'f69063bec68ac19642582824b0f13f5e084daebd6da9e6c6d0c5eed3a8c7352e'
RC1_COMMIT = '90a4e051a71ecc7b3156802c54dbfd8219789469'
RESOURCES = ROOT/'src/main/resources/bloodborne_blocks/logical'
AIR = ('minecraft:air', ())
HELPER = (PART, ())


def require(condition, message):
    if not condition: raise ValueError(message)


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def canonical_hash(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def state(text):
    name, _, tail = text.partition('[')
    return name, tuple(sorted(tuple(p.split('=', 1)) for p in tail.rstrip(']').split(',') if p))


def state_text(value):
    return block_state_key(as_tag_state(value)) if value is not None else None


def resource_hashes_match(resources, recorded):
    """Only Git's LF/CRLF checkout transform may change recorded JSON bytes."""
    paths={'legacy':resources.parent/'definitions.json','logical':resources/'definitions.json',
           'legacyGeometry':resources.parent/'geometry.json'}
    paths.update({n:resources/n for n in ('contracts-v2.json','transform-v2.json','physical-footprints.json') if (resources/n).is_file()})
    paths.update({'city/'+n:resources.parent/'city'/n for n in ('definitions.json','geometry.json','owner-runtime-mappings.json','owner-meshes.json.gz') if (resources.parent/'city'/n).is_file()})
    if set(paths)!=set(recorded):return False
    for key,path in paths.items():
        if not path.is_file():
            if recorded[key] is not None:return False
            continue
        data=path.read_bytes();allowed={hashlib.sha256(data).hexdigest()}
        if path.suffix=='.json':
            lf=data.replace(b'\r\n',b'\n')
            allowed.update(hashlib.sha256(value).hexdigest() for value in (lf,lf.replace(b'\n',b'\r\n')))
        if recorded[key] not in allowed:return False
    return True


def entity(point, owners):
    owners = sorted(set(owners))  # (root, ID), canonical runtime disk order
    if not owners: return None
    require(len(owners) <= 16, 'too many owners')
    rows = [Tag(TAG_COMPOUND, {'Root':Tag(TAG_LONG,block_pos_long(*p)),
            'Owner':Tag(TAG_STRING,owner)}) for p,owner in owners]
    return Tag(TAG_COMPOUND, {'id':Tag(TAG_STRING,PART),
        **{axis:Tag(TAG_INT,v) for axis,v in zip(('x','y','z'),point)},
        **compound(rows[0]), **({'Owners':Tag(TAG_LIST,rows,TAG_COMPOUND)} if len(rows)>1 else {})})


def recorded_entity(row):
    values = row.get('Owners', [row])
    tag = entity(tuple(row['position']), [(unpack_pos_long(v['Root']),v['Owner']) for v in values])
    require(tag is not None, 'empty recorded helper')
    data = compound(tag)
    require(data['Root'].value == row['Root'] and data['Owner'].value == row['Owner'], 'noncanonical recorded helper')
    return tag


def strict_bindings(tag, point):
    if tag is None: return []
    data = compound(tag)
    require(data.get('id') == Tag(TAG_STRING,PART), 'foreign block entity in transaction')
    require(not (set(data)-{'id','x','y','z','Root','Owner','Owners','keepPacked'}), 'custom helper payload')
    require(all(data.get(axis)==Tag(TAG_INT,v) for axis,v in zip(('x','y','z'),point)), 'invalid helper coordinates')
    return helper_bindings(data)


def shapes_and_definitions(resources):
    shapes = parse_geometry(resources.parent/'city', {})
    contracts, _ = load_contracts(resources)
    for family in contracts['families']:
        for key,spec in family['states'].items():
            shapes[state('bloodborne_blocks:'+family['id']+'['+key+']')] = {tuple(p) for p in spec['physical_footprint']['cells']}
    shapes = {s:set(mask)|{(0,0,0)} for s,mask in shapes.items()}
    palette={row['id'] for row in json.loads((resources/'production-palette.json').read_bytes())['objects']}
    require(canonical_hash(sorted(palette))=='992db851356b7a9d40404997e8d457d7fbacfcc7eb2aa88dfb6ac0213eaf082c',
            'accepted production palette changed')
    definitions = {'bloodborne_blocks:'+d['id']:d for d in
        json.loads((resources/'definitions.json').read_bytes())['blocks'] if d.get('logical') and d['id'] in palette}
    return shapes, definitions


def published_shapes():
    prefix = RC1_COMMIT+':Bloodborne-Blocks/src/main/resources/bloodborne_blocks/'
    def read(path):
        return json.loads(subprocess.check_output(['git','show',prefix+path],cwd=ROOT))
    physical = read('logical/physical-footprints.json')
    result = {state('bloodborne_blocks:'+ident+'['+key+']'):{tuple(p) for p in spec['cells']}|{(0,0,0)}
              for ident,states in physical['families'].items() for key,spec in states.items()}
    geometry = read('city/geometry.json')
    for ident,block in geometry['blocks'].items():
        for key,spec in block['states'].items():
            profile = geometry['profiles'].get(spec.get('ref'),spec)
            result[state('bloodborne_blocks:'+ident+'['+key+']')] = {
                tuple(map(int,p.split(','))) for p in profile['cells']}|{(0,0,0)}
    return result


def ownership(roots, points, shapes):
    occupied = defaultdict(list)
    for root,value in roots.items():
        require(value in shapes, 'unregistered accepted root: '+state_text(value))
        for offset in shapes[value]: occupied[add(root,offset)].append((root,value[0]))
    require(set(occupied)<=set(points), 'incomplete whole-owner footprint')
    desired = {p:roots.get(p, HELPER if p in occupied else AIR) for p in points}
    helpers = {}
    for p,owners in occupied.items():
        require(len(owners)<=16, 'owner capacity exceeded')
        tag = entity(p,[o for o in owners if o[0]!=p])
        if tag is not None: helpers[p]=tag
    return desired, helpers


def compose(indices, accepted, original, shapes):
    require(indices and indices==sorted(set(indices)), 'invalid accepted entry sequence')
    require(all(type(i) is int and 0<=i<len(accepted) for i in indices), 'invalid accepted entry index')
    dim = accepted[indices[0]]['dimension']
    first, final, helpers = {}, {}, {}
    for index in indices:
        entry=accepted[index]
        require(entry['dimension']==dim and entry.get('decision')=='converted' and not entry.get('conflictingCells'), 'unaccepted source transaction')
        seen=set()
        for change in entry['changes']:
            p=tuple(change['position'])
            require(len(p)==3 and all(type(v) is int for v in p) and p not in seen, 'invalid/duplicate accepted cell')
            seen.add(p)
            before,after=state(change['before']),state(change['after'])
            require(final.get(p,original.get(dim,p))==before, 'original MODDED chain mismatch: '+str((index,p)))
            first.setdefault(p,before);final[p]=after;helpers.pop(p,None)
        for row in entry['helpers']:
            p=tuple(row['position'])
            require(p in final, 'source helper outside accepted members')
            helpers[p]=recorded_entity(row)
    roots={p:v for p,v in final.items() if v not in (AIR,HELPER)}
    require(roots, 'accepted group has no roots')
    desired,expected_helpers=ownership(roots,final,shapes)
    require(final==desired and helpers==expected_helpers, 'accepted evidence differs from current whole-owner contract')
    return dim,final,roots


def existing_owners(reference, shapes, definitions, old_shapes, accepted):
    """Independent census of changed, already-logical RC1 owner contracts."""
    import numpy as np
    members={(e['dimension'],tuple(c['position'])) for e in accepted for c in e['changes']}
    result=[]
    for chunk in reference.chunks.values():
        for section in chunk.root().get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
            raw=compound(section).get('block_states')
            if raw is None:continue
            palette=compound(raw)['palette'].value
            selected={}
            for i,t in enumerate(palette):
                value=state(block_state_key(t));definition=definitions.get(value[0])
                if definition is None:continue
                target=(value[0],tuple(sorted({**definition.get('default',{}),**dict(value[1])}.items())))
                require(value in old_shapes and target in shapes,'invalid RC1 existing owner state')
                if value!=target or old_shapes[value]!=shapes[target]:selected[i]=(value,target)
            if not selected:continue
            _,indices=section_blocks(section);indices=np.asarray(indices);sy=int(compound(section)['Y'].value)
            for i,(value,target) in selected.items():
                for n in np.flatnonzero(indices==i):
                    n=int(n);p=(chunk.x*16+(n&15),sy*16+(n>>8),chunk.z*16+((n>>4)&15))
                    if (chunk.dimension,p) not in members:result.append((chunk.dimension,p,value,target))
    return [{'dimension':dim,'root':list(p),'before':state_text(value),'state':state_text(target)}
            for dim,p,value,target in sorted(result)]


def complete_partition(rows, key, count):
    indices=[i for row in rows for i in row.get(key,[])]
    require(all(type(i) is int for i in indices) and sorted(indices)==list(range(count)),
            'omitted/duplicate '+key)


def audit_world(world, resources, shapes):
    """Both directions: every required helper exists and every helper has owners.
    Registry/property validation covers every used palette state, all dimensions.
    """
    import numpy as np
    definitions={}
    for layer in (resources,resources.parent/'city'):
        definitions.update({'bloodborne_blocks:'+d['id']:d for d in json.loads((layer/'definitions.json').read_bytes())['blocks']})
    expected=defaultdict(set);parts=set();roots=set();unknown=Counter();invalid=Counter();cells=0
    for chunk in world.chunks.values():
        for section in chunk.root().get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
            raw=compound(section).get('block_states')
            if raw is None:continue
            palette=compound(raw)['palette'].value
            if not any(compound(t)['Name'].value.startswith('bloodborne_blocks:') for t in palette):continue
            _,indices=section_blocks(section);indices=np.asarray(indices);sy=int(compound(section)['Y'].value)
            for i,t in enumerate(palette):
                value=state(block_state_key(t));name=value[0]
                if not name.startswith('bloodborne_blocks:'):continue
                n=int(np.count_nonzero(indices==i))
                if not n:continue
                cells+=n
                d=definitions.get(name)
                if name!=PART:
                    if d is None:unknown[name]+=n;continue
                    normalized=(name,tuple(sorted({**d.get('default',{}),**dict(value[1])}.items())))
                    key=','.join(k+'='+v for k,v in normalized[1])
                    if key not in d.get('states',{}):invalid[state_text(value)]+=n;continue
                    if not(d.get('logical') or d.get('whole_owner')):continue
                    if normalized not in shapes:invalid[state_text(value)+' missing geometry']+=n;continue
                for flat in np.flatnonzero(indices==i):
                    flat=int(flat);p=(chunk.x*16+(flat&15),sy*16+(flat>>8),chunk.z*16+((flat>>4)&15));dim=chunk.dimension
                    if name==PART:parts.add((dim,p));continue
                    roots.add((dim,p))
                    for offset in shapes[normalized]:
                        if offset!=(0,0,0):expected[(dim,add(p,offset))].add((p,name))
    actual={};errors=[]
    for (dim,*p),tag in world.block_entities().items():
        if compound(tag).get('id')!=Tag(TAG_STRING,PART):continue
        key=(dim,tuple(p))
        try:actual[key]=set(strict_bindings(tag,tuple(p)))
        except ValueError as e:errors.append(str((key,str(e))))
    for key in expected.keys()|actual.keys()|parts:
        if key in parts and key not in actual:errors.append('part without entity: '+str(key))
        if expected.get(key)!=actual.get(key):errors.append('helper owners differ: '+str(key))
        if key in actual and key not in parts and key not in roots:errors.append('helper has unsupported carrier: '+str(key))
    result={'result':'FAIL' if unknown or invalid or errors else 'PASS','chunks':len(world.chunks),
            'modCells':cells,'ownerRoots':len(roots),'helpers':len(actual),'unknownIds':dict(unknown),
            'invalidStates':dict(invalid),'helperErrors':len(errors),'examples':errors[:20]}
    require(result['result']=='PASS','full-world palette/helper check failed: '+json.dumps(result))
    return result


def dependency_closure(dim, base, roots, reference, ref_entities, old_helpers,
                       old_shapes, shapes, definitions, approved_cells, seeds=()):
    points,examined,consumed=set(base),set(),set()
    roots=dict(roots);dependencies={}
    for root in seeds:points.update(add(root,o) for o in shapes[roots[root]])
    while points-examined:
        p=min(points-examined);examined.add(p)
        current=reference.get(dim,p)
        require(current is not None, 'missing RC1 chunk')
        connected=strict_bindings(ref_entities.get((dim,*p)),p)
        if current[0].startswith('bloodborne_blocks:o_'): connected.append((p,current[0]))
        for root,owner in connected:
            if root in consumed: continue
            before=reference.get(dim,root)
            require(before and before[0]==owner and before in old_shapes, 'unproved RC1 owner')
            expected={add(root,o) for o in old_shapes[before] if o!=(0,0,0)}
            actual=old_helpers.get((dim,root,owner),set())
            require(actual<=expected, 'RC1 helper outside old mask')
            if root in seeds:require(actual==expected,'incomplete RC1 seed owner')
            if root not in base:
                require(actual==expected, 'incomplete RC1 dependency')
                definition=definitions.get(owner)
                require(definition is not None, 'dependency is not an accepted logical object')
                normalized=(owner,tuple(sorted({**definition.get('default',{}),**dict(before[1])}.items())))
                require(normalized in shapes, 'invalid dependency state')
                dependencies[root]=(before,normalized);roots[root]=normalized
                points.update(add(root,o) for o in shapes[normalized])
            else:
                for missing in expected-actual:
                    require(ref_entities.get((dim,*missing)) is None or missing in base or (dim,missing) in approved_cells,
                            'foreign block entity in missing old mask')
                    require(missing in base or reference.get(dim,missing)==AIR or (dim,missing) in approved_cells,
                            'foreign block in missing old mask')
                    if (dim,missing) in approved_cells: points.add(missing)
            points.update(actual);consumed.add(root)
        if p not in base:
            require(current in (AIR,HELPER) or p in dependencies, 'foreign terrain in extra footprint')
    desired,helpers=ownership(roots,points,shapes)
    require(all(desired[p]==v for p,v in base.items() if v not in (AIR,HELPER)), 'dependency changed accepted root')
    return desired,helpers,roots,dependencies


def verify_group(row, compiled, reference, before, after, ref_entities, before_entities,
                 after_entities, old_helpers, old_shapes, shapes, definitions, approved_cells, ledger,
                 seeds=(), accepted_count=0):
    dim,base,accepted_roots=compiled
    require(row.get('dimension')==dim, 'report dimension differs')
    require(not row.get('preflightErrors') and not row.get('preflightDetails'), 'unresolved group preflight')
    wanted,helpers,roots,deps=dependency_closure(dim,base,accepted_roots,reference,ref_entities,
        old_helpers,old_shapes,shapes,definitions,approved_cells,seeds)
    require(row.get('rc1Dependencies')==[{'root':list(p),'before':state_text(v[0]),'state':state_text(v[1])}
            for p,v in sorted(deps.items())], 'forged/omitted dependency owner')
    require(row.get('bridgeHelperCells')==[list(p) for p in sorted(set(wanted)-set(base))], 'forged extra touched set')
    declared={tuple(v['position']):state(v['state']) for v in row['roots']}
    require(len(declared)==len(row['roots']) and declared==roots, 'forged/omitted root')
    require(row.get('origin')==list(min(base)), 'report origin differs')
    identity_indices=row['acceptedEntries']+[accepted_count+i for i in row.get('rc1OwnerEntries',[])]
    identity='accepted-rc1-'+hashlib.sha256(','.join(map(str,identity_indices)).encode()).hexdigest()[:20]
    require(row.get('transaction')==identity, 'transaction identity differs')
    already=all(before.get(dim,p)==v and before_entities.get((dim,*p))==helpers.get(p) for p,v in wanted.items())
    require(not before.ticks_at(dim,set(wanted)), 'scheduled tick in transaction')
    result='already_correct' if already else 'restore'
    require(row.get('result')==result, 'false group completion')
    if not already:
        for p in wanted:
            require(before.get(dim,p)==reference.get(dim,p) and before_entities.get((dim,*p))==ref_entities.get((dim,*p)),
                    'source cell/NBT differs from published RC1')
    for p,v in wanted.items():
        require(after.get(dim,p)==v, 'output differs from accepted object: '+str((dim,p)))
        require(after_entities.get((dim,*p))==helpers.get(p), 'output helper NBT differs: '+str((dim,p)))
    entry=ledger.get(identity)
    if already:
        require(entry is None, 'already-correct group has a write ledger')
    else:
        require(entry is not None, 'missing complete write ledger')
        require(entry.get('decision')=='converted' and not entry.get('conflictingCells') and entry.get('dimension')==dim,
                'forced/invalid write ledger')
        expected=[{'position':list(p),'before':state_text(before.get(dim,p)),'after':state_text(wanted[p])} for p in sorted(wanted)]
        require(entry['changes']==expected, 'write ledger is not actual RC1 -> complete accepted result')
        require(entry.get('source')==[list(p) for p in sorted(wanted)] and not entry.get('staleRemoved'), 'write membership differs')
        hs={tuple(v['position']):recorded_entity(v) for v in entry.get('helpers',[])}
        require(len(hs)==len(entry.get('helpers',[])) and hs==helpers, 'write ledger helper proof differs')
        outs={tuple(v['targetRoot']):state(v['target']) for v in entry.get('outputs',[])}
        require(len(outs)==len(entry.get('outputs',[])) and outs==roots, 'write ledger owners differ')
    return result,roots,set(wanted)


def verify(source, output, report, resources=RESOURCES):
    from verify_modded_preservation import verify as preserve
    source,output,resources=Path(source),Path(output),Path(resources)
    require(report.get('format')=='bloodborne-accepted-rc1-restore-v1' and report.get('sourceMode')=='accepted-rc1'
            and report.get('result')=='PASS' and report.get('phase')=='applied' and not report.get('dryRun'), 'completed restoration required')
    require(not any(k in report for k in ('cityRecovery','cityPaletteMigration','gridReconciliation','gridPreReconciliation')), 'global conversion outside addressed proof')
    inputs={'rc1Sha256':RC1_SHA,'moddedSha256':MODDED_SHA,'acceptedLedgerSha256':ACCEPTED_SHA}
    require(report.get('inputs')==inputs, 'source provenance differs')
    for path,digest in ((RC1,RC1_SHA),(MODDED,MODDED_SHA),(ACCEPTED,ACCEPTED_SHA)):
        require(sha(path)==digest, 'immutable proof input changed: '+str(path))
    require(resource_hashes_match(resources,report.get('resources',{}).get('definitionsSha256',{})), 'runtime contracts changed')
    evidence=json.loads(gzip.decompress(ACCEPTED.read_bytes()))
    require(evidence['counts']['forced']==0 and evidence['source']['hashes']['archive']==MODDED_SHA, 'untrusted accepted evidence')
    accepted=evidence['ledger'];rows=report.get('transactions',[])
    complete_partition(rows,'acceptedEntries',len(accepted))
    ledger={row['transaction']:row for row in report['ledger']}
    require(len(ledger)==len(report['ledger']), 'duplicate write transaction')
    shapes,definitions=shapes_and_definitions(resources);old_shapes=published_shapes()
    approved={(e['dimension'],tuple(c['position'])) for e in accepted for c in e['changes']}
    with tempfile.TemporaryDirectory(prefix='accepted-independent-') as temp:
        tmp=Path(temp)
        original=World(safe_extract(MODDED,tmp/'modded'),{})
        compiled=[compose(row['acceptedEntries'],accepted,original,shapes) if row['acceptedEntries'] else
                  (row['dimension'],{}, {}) for row in rows]
        del original
        reference_dir=safe_extract(RC1,tmp/'rc1');reference=World(reference_dir,{})
        seeds=existing_owners(reference,shapes,definitions,old_shapes,accepted)
        require(report.get('existingRc1Owners')==seeds,'existing RC1 owner census differs')
        complete_partition(rows,'rc1OwnerEntries',len(seeds))
        for row,group in zip(rows,compiled):
            require(row.get('rc1OwnerEntries',[])==sorted(set(row.get('rc1OwnerEntries',[]))), 'invalid RC1 seed order')
            for i in row.get('rc1OwnerEntries',[]):
                seed=seeds[i];p=tuple(seed['root']);target=state(seed['state'])
                require(seed['dimension']==group[0] and p not in group[1], 'RC1 seed overlaps original accepted member')
                group[1][p]=target;group[2][p]=target;approved.add((group[0],p))
        if source.is_file():
            require(sha(source)==RC1_SHA, 'source ZIP is not published RC1')
            require(report['source']['hashes']=={'archive':RC1_SHA} and report['source']['kind']=='zip', 'source hash differs')
            source_dir,before=reference_dir,reference
        else:
            source_dir=source
            require(report['source']['kind']=='directory' and report['source']['hashes']==hash_tree(source), 'repeat source tree differs')
            before=World(source,{})
        outdir=safe_extract(output,tmp/'output') if output.is_file() else output
        files=hash_tree(outdir)
        require(report.get('outputFiles')==files and report.get('outputTreeSha256')==canonical_hash(files), 'output manifest differs')
        after=World(outdir,{})
        refbes,beforebes,afterbes=reference.block_entities(),before.block_entities(),after.block_entities()
        old_helpers=defaultdict(set)
        for (dim,*p),tag in refbes.items():
            if compound(tag).get('id')==Tag(TAG_STRING,PART):
                for root,owner in strict_bindings(tag,tuple(p)): old_helpers[(dim,root,owner)].add(tuple(p))
        counts=Counter();families=defaultdict(Counter);touched=set()
        for row,group in zip(rows,compiled):
            result,roots,points=verify_group(row,group,reference,before,after,refbes,beforebes,afterbes,
                old_helpers,old_shapes,shapes,definitions,approved,ledger,
                {tuple(seeds[i]['root']) for i in row.get('rc1OwnerEntries',[])},len(accepted))
            counts[result]+=1
            current={(group[0],p) for p in points}
            require(not(current&touched), 'split/overlapping owner groups');touched.update(current)
            for v in roots.values():
                family=v[0].removeprefix('bloodborne_blocks:')
                families[family]['found']+=1
                families[family]['restored' if result=='restore' else 'alreadyCorrect']+=1
        require(set(ledger)=={r['transaction'] for r in rows if r['result']=='restore'}, 'foreign write transaction')
        expected_counts={'acceptedTransactions':len(accepted),'existingRc1Owners':len(seeds),'groups':len(rows),'restored':counts['restore'],
                         'alreadyCorrect':counts['already_correct'],'conflicts':0}
        require(report['counts']==expected_counts and report['families']==dict(families), 'forged summary counts')
        retired=json.loads((ROOT/'docs/city-compat/missing-model-positions.json').read_bytes())
        require(canonical_hash(retired)=='18fba494229775f0d240b1afc6f61762328274e4e311bbf6ee51de9409edf0a0', 'retirement manifest changed')
        retired_rows=[];nonair=0
        for dim,p in sorted((r.get('dimension','minecraft:overworld'),tuple(r['pos'])) for values in retired.values() for r in values):
            v=reference.get(dim,p);tag=refbes.get((dim,*p))
            require(before.get(dim,p)==after.get(dim,p)==v and beforebes.get((dim,*p))==afterbes.get((dim,*p))==tag, 'retirement position changed')
            retired_rows.append({'dimension':dim,'position':list(p),'rc1State':state_text(v)});nonair+=v!=AIR
        require(len(retired_rows)==33 and report.get('retiredPositionsPreserved')==retired_rows, 'retired proof differs')
        full_world=audit_world(after,resources,shapes)
        del reference,before,after
        preservation=preserve(source_dir,outdir,report)
        require(preservation['result']=='PASS', 'outside-target preservation failed: '+str(preservation['errors'][:5]))
    return {'result':'PASS',**expected_counts,'verifiedRoots':sum(v['found'] for v in families.values()),
        'verifiedCells':len(touched),'independentResidualFragments':0,'targetConflicts':0,
        'retiredPositionsUnchanged':33,'publishedRc1RetiredPositionsNonAir':nonair,
        'families':dict(sorted(families.items())),'preservation':preservation,'fullWorld':full_world,'inputs':inputs,
        'outputTreeSha256':report['outputTreeSha256'],
        'scope':'Complete accepted transactions, known RC1 production owners with changed accepted masks and exact linked dependencies; excludes unproved historical occurrences.'}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('source',type=Path);p.add_argument('output',type=Path);p.add_argument('report',type=Path)
    p.add_argument('--result',type=Path,required=True);a=p.parse_args()
    try:
        raw=a.report.read_bytes();report=json.loads(gzip.decompress(raw) if a.report.suffix=='.gz' else raw)
        result=verify(a.source,a.output,report)
    except (ValueError,KeyError,TypeError,AssertionError) as error:
        result={'result':'FAIL','errors':[str(error)]}
    a.result.parent.mkdir(parents=True,exist_ok=True)
    a.result.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps({k:v for k,v in result.items() if k!='families'}))
    raise SystemExit(result['result']!='PASS')


if __name__=='__main__': main()
