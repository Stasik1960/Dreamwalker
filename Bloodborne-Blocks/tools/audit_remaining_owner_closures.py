"""Batch historical evidence only; does not promote objects or change gates."""
import argparse
import hashlib
import gzip
import json
from collections import Counter
from pathlib import Path
from trace_composite_owner_closure import trace_many
from composite_world_oracle import ROOT


def audit(gate_path, output, limit=None):
    oracle_path = ROOT/'docs/composite-grid-repair/protected-world-oracle.json'
    gate = json.loads(gate_path.read_bytes())
    wanted = {(r['rule'],tuple(r['origin'])) for r in gate['unresolved_technical_membership']}
    oracle = json.loads(oracle_path.read_bytes())
    rows = [r for r in oracle['occurrences'] if (r['rule'],tuple(r['origin'])) in wanted]
    seeds = sorted({tuple(c['position']) for r in rows for c in r['source_cells']})
    if limit is not None: seeds = seeds[:limit]
    traces = trace_many(seeds)
    by_seed = {tuple(t['seed']):i for i,t in enumerate(traces)}
    def resolved(seed):
        i = by_seed.get(tuple(seed))
        if i is None: return None
        t = traces[i]
        if t['result'] == 'REFER_TO_PROVEN_CLOSURE': i = t['closureIndex']; t = traces[i]
        return i if t['result'] != 'UNRESOLVED' else None
    memberships = []
    for row in rows:
        ids = [resolved(c['position']) for c in row['source_cells']]
        memberships.append({'rule':row['rule'],'origin':row['origin'],
                            'historicalClosureProven':bool(ids) and all(i is not None for i in ids),
                            'closureIndices':sorted({i for i in ids if i is not None})})
    result = {'scope':'Historical producer evidence only; converter, composite/world gates and release readiness unchanged',
              'oracleSha256':hashlib.sha256(oracle_path.read_bytes()).hexdigest(),
              'inputGateSha256':hashlib.sha256(gate_path.read_bytes()).hexdigest(),
              'seedLimit':limit,'seedCount':len(seeds),
              'traceCounts':dict(Counter(t['result'] for t in traces)),
              'historicalMembershipsProven':sum(r['historicalClosureProven'] for r in memberships),
              'occurrences':memberships,'closures':traces}
    raw = (json.dumps(result,separators=(',',':'))+'\n').encode('utf8')
    output.write_bytes(gzip.compress(raw,mtime=0) if output.suffix == '.gz' else raw)
    print(result['traceCounts'],'memberships',result['historicalMembershipsProven'],flush=True)


if __name__ == '__main__':
    p=argparse.ArgumentParser();p.add_argument('gate',type=Path);p.add_argument('output',type=Path)
    p.add_argument('--limit',type=int);a=p.parse_args();audit(a.gate,a.output,a.limit)
