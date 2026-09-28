"""Independent coverage, full-world and non-target preservation for opted-in QA."""
import argparse
import copy
import gzip
import json
import tempfile
from pathlib import Path
from accepted_repair_coverage import Runtime, Observed, audit_coverage
from accepted_objects_restore import load_shapes
from verify_accepted_restore import audit_world
from verify_modded_preservation import verify
from convert_logical_world import World, safe_extract, write_report

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources/bloodborne_blocks/logical'


def run(source,output,ledger_path,result_path):
    raw=ledger_path.read_bytes()
    ledger=json.loads(gzip.decompress(raw) if ledger_path.suffix=='.gz' else raw)
    with tempfile.TemporaryDirectory(prefix='approximate-verification-') as temp:
        before=safe_extract(source,Path(temp)/'before')
        if output.is_file():output=safe_extract(output,Path(temp)/'after')
        print('Independent whole-world registry and owner scan',flush=True)
        world=World(output,{})
        integrity=audit_world(world,RES,load_shapes(RES))
        print(json.dumps(integrity),flush=True)
        oracle=json.loads((ROOT/'docs/composite-grid-repair/protected-world-oracle.json').read_bytes())
        bushes=json.loads(gzip.decompress((ROOT/'docs/accepted-restore/bush-source-census.json.gz').read_bytes()))['occurrences']
        pending=json.loads(gzip.decompress((ROOT/'docs/accepted-restore/known-pending-vegetation.json.gz').read_bytes()))['occurrences']
        for row in pending:
            row['outputs']=[{'family':'o_grass_'+str(row['weighted_index']),'canonical_root':row['canonical_root']}]
        decisions=json.loads((ROOT/'docs/whole-models-handoff/decisions/APPROXIMATION.json').read_bytes())
        relocations={(r['family'],tuple(r['sourceRoot'])):r['offset'] for r in decisions['relocations']}
        for row in oracle['occurrences']+bushes+pending:
            for item in row['outputs']:
                old=tuple(item['canonical_root']);offset=relocations.get((item['family'],old))
                if offset:
                    item['canonical_root']=[old[i]+offset[i] for i in range(3)]
        print('Independent source census coverage',flush=True)
        coverage=audit_coverage(oracle,Observed(world),Runtime(RES),supplemental=bushes+pending)
        print(json.dumps(coverage['counts']),flush=True)
        print('Independent terrain, NBT and other-file preservation',flush=True)
        preservation=verify(before,output,ledger)
        print(json.dumps({k:v for k,v in preservation.items() if k!='errors'}),flush=True)
        report={'integrity':integrity,'coverage':coverage,'preservation':preservation,'releaseReady':False}
        write_report(result_path,report)
        return report


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('source','output','ledger','result'):p.add_argument(name,type=Path)
    a=p.parse_args();r=run(a.source,a.output,a.ledger,a.result)
    raise SystemExit(r['integrity']['result']!='PASS' or r['preservation']['result']!='PASS' or r['coverage']['knownCensusCoverage']!='PASS')
