"""Verify historical refusal records against all remaining protected sources."""
import functools
import gzip
import hashlib
import json
from collections import defaultdict
from pathlib import Path
from historical_omission_evidence import OmissionEvidence
from inspect_historical_wall_meshes import historical_art, carrier_cells, ROOT
from composite_world_oracle import EvidenceReader, SOURCE_SHA
from audit_remaining_membership_meshes import MODDED_SHA
from source_mapping_archive import archive
from build_city_compat import rotated_mesh
from verify_composite_overlap_evidence import signature


def audit():
    proof = json.loads(gzip.decompress((ROOT/'docs/composite-grid-repair/historical-owner-closures.json.gz').read_bytes()))
    wanted = {(r['rule'],tuple(r['origin'])) for r in proof['occurrences'] if not r['historicalClosureProven']}
    oracle = json.loads((ROOT/'docs/composite-grid-repair/protected-world-oracle.json').read_bytes())
    parts = {tuple(c['position']):c['state'] for r in oracle['occurrences']
             if (r['rule'],tuple(r['origin'])) in wanted for c in r['source_cells']}
    paths = [ROOT/'reference-inputs/source-world.zip',ROOT/'reference-inputs/latest-modded-world.zip']
    for path,sha in zip(paths,(SOURCE_SHA,MODDED_SHA)):
        if hashlib.sha256(path.read_bytes()).hexdigest()!=sha: raise ValueError('IMMUTABLE_INPUT_HASH_MISMATCH')
    source,current = (EvidenceReader(p) for p in paths)
    evidence = OmissionEvidence(); migration = archive()['v2\\migration.json']; approved=[]; rejected=[]
    try:
        with historical_art() as jar:
            meshes=json.loads(gzip.decompress(jar.read('bloodborne_blocks/v2/meshes.json.gz')))
            @functools.lru_cache(None)
            def module(ident,facing):
                if ident not in meshes:return None
                return signature(rotated_mesh(meshes[ident],('north','east','south','west').index(facing))['polygons'])
            @functools.lru_cache(None)
            def template(state):
                name,_,suffix=state.partition('[');ident=name.removeprefix('minecraft:')
                if ident not in migration:return {}
                props={**migration[ident]['default'],**dict(p.split('=',1) for p in suffix.rstrip(']').split(',') if p)}
                value=migration[ident]['states'].get(','.join(k+'='+v for k,v in sorted(props.items())))
                if isinstance(value,list):
                    cells=defaultdict(set)
                    for part in value:
                        faces=module(part['id'],part.get('properties',{}).get('facing','north'))
                        if faces is None:return {}
                        cells[tuple(part['offset'])].update(faces)
                    return cells
                try:return {p:signature(v) for p,v in carrier_cells(jar,ident,props).items()}
                except (KeyError,ValueError):return {}
            for (root,point),ledger_rows in sorted(evidence.rows.items()):
                if root not in parts:continue
                state=parts[root]
                if source.state('eh_s2:yharnam',root)[1]!=state:raise ValueError('SOURCE_ORACLE_STATE_CHANGED')
                offset=tuple(point[i]-root[i] for i in range(3));faces=template(state).get(offset)
                actual=current.state('minecraft:overworld',point)[1]
                witness=evidence.witness(root,point,state,actual,faces,module)
                if witness:approved.append(witness)
                else:rejected.append({'root':list(root),'position':list(point),'current':actual,
                                     'reason':'ledger/source/fragment/current-context agreement not proved'})
    finally:source.close();current.close()
    result={'scope':'Historical omission witnesses only; no membership/world gate bypass or conversion authorization',
            'sourceSha256':SOURCE_SHA,'moddedSha256':MODDED_SHA,
            'approvedFragmentContexts':len(approved),'affectedSourceRoots':len({tuple(r['root']) for r in approved}),
            'approved':approved,'rejected':rejected}
    destination=ROOT/'docs/composite-grid-repair/historical-omission-witnesses.json.gz'
    destination.write_bytes(gzip.compress((json.dumps(result,separators=(',',':'))+'\n').encode(),mtime=0))
    print('Verified omission contexts',len(approved),'source roots',result['affectedSourceRoots'],'rejected',len(rejected),flush=True)


if __name__=='__main__':audit()
