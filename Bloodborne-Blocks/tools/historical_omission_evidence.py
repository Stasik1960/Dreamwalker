"""Prove a historical converter refused a specific complete source fragment.

This records a preserved context, not permission to overwrite it or world PASS.
Only applied reports, exact root states, exact textured fragments and unchanged
current protected states qualify. Other report contents remain untrusted data.
"""
import gzip
import hashlib
import json
from collections import defaultdict
from pathlib import Path
from source_mapping_archive import archive

ROOT = Path(__file__).resolve().parents[1]


class OmissionEvidence:
    def __init__(self):
        folder = ROOT/'docs/composite-grid-repair'
        raw = (folder/'historical-conversion-ledgers.json.gz').read_bytes()
        manifest = json.loads((folder/'historical-conversion-ledgers-manifest.json').read_bytes())
        if hashlib.sha256(raw).hexdigest() != manifest['bundleSha256']:
            raise ValueError('HISTORICAL_LEDGER_HASH_MISMATCH')
        bundle = json.loads(gzip.decompress(raw)); self.rows = defaultdict(list)
        self.defaults = {b['id']:b.get('default',{}) for b in archive()['definitions.json']['blocks']}
        for filename, report in bundle.items():
            if report.get('applied') is not True: continue
            for row in report.get('unresolved', []):
                if row.get('category') not in {'remaining_overlap','composition_risk_retained_legacy'}: continue
                for piece in row.get('incomingPieces', []):
                    if not all(k in piece for k in ('root','rootState','owner','id','facing')): continue
                    self.rows[(tuple(piece['root']),tuple(row['position']))].append(
                        (filename, row['protectedBlock'], piece))

    def witness(self, root, point, source_state, current_state, expected_polygons, module_polygons):
        if not source_state or not expected_polygons: return None
        name, _, suffix = source_state.partition('[')
        owner = name.removeprefix('minecraft:')
        if owner not in self.defaults: return None
        props = {**self.defaults[owner], **dict(v.split('=',1) for v in suffix.rstrip(']').split(',') if v)}
        expected_root = 'bloodborne_blocks:'+owner
        if props: expected_root += '['+','.join(k+'='+str(v) for k,v in sorted(props.items()))+']'
        for filename, protected, piece in self.rows.get((tuple(root),tuple(point)), ()):
            if current_state != protected or piece['owner'] != owner or piece['rootState'] != expected_root: continue
            polygons = module_polygons(piece['id'],piece['facing'])
            if polygons is None or set(polygons) != set(expected_polygons): continue
            return {'ledger':filename,'root':list(root),'position':list(point),
                    'sourceState':source_state,'rootState':expected_root,'preserveState':protected,
                    'incomingModule':{'id':piece['id'],'facing':piece['facing']},
                    'policy':'Proven historically unwritten fragment; preserve entire existing block/entity context. Not conversion authorization.'}
        return None
