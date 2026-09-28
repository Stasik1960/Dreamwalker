"""Freeze diagnostics from the completed integration scan; never release a world."""
import gzip
import hashlib
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
DOC=ROOT/'docs/composite-grid-repair'


def record():
    scan=json.loads((ROOT/'build/atomic-owner-world-verified.json').read_bytes())
    gate=json.loads((ROOT/'build/atomic-owner-world-verified-gate.json').read_bytes())
    check=json.loads((ROOT/'build/atomic-owner-verified-independent-check.log').read_text(encoding='utf-8-sig'))
    helpers=json.loads((ROOT/'build/atomic-owner-helpers.json').read_bytes())
    second=json.loads((ROOT/'build/atomic-owner-world-second.json').read_bytes())
    byte_check=json.loads((ROOT/'build/atomic-world-byte-check.json').read_bytes())
    if not check['ok'] or not byte_check['originalToVerifiedIdentical'] or not byte_check['secondPassByteIdentical']:
        raise ValueError('CHECKPOINT_VERIFICATION_FAILED')
    residual=json.loads(gzip.decompress((DOC/'atomic-world-residuals.json.gz').read_bytes()))
    archives={}
    for name,path in [('atomic-world-conversion',ROOT/'build/atomic-owner-world-verified.json'),
                      ('atomic-world-gate',ROOT/'build/atomic-owner-world-verified-gate.json')]:
        destination=DOC/(name+'.json.gz');destination.write_bytes(gzip.compress(path.read_bytes(),mtime=0))
        archives[destination.name]=hashlib.sha256(destination.read_bytes()).hexdigest()
    roots=[(g,c) for g in residual['groups'] for c in g['sharedCells'] if c['rootConflict']]
    rows=['x\ty\tz\ttransaction\towners']
    for g,c in roots:
        rows.append('\t'.join([*map(str,c['position']),g['transaction'],json.dumps(c['owners'],separators=(',',':'))]))
    (DOC/'atomic-root-conflicts.tsv').write_text('\n'.join(rows)+'\n',encoding='utf-8')
    rows=['rule\tx\ty\tz\tcategory\tsource_roots']
    for r in residual['unresolvedMemberships']:
        rows.append('\t'.join([str(r['rule']),*map(str,r['origin']),r['category'],json.dumps(r['sourceRoots'],separators=(',',':'))]))
    (DOC/'atomic-unresolved-memberships.tsv').write_text('\n'.join(rows)+'\n',encoding='utf-8')
    summary={'input':scan['source'],'resources':scan['resources'],
        'archivesSha256':archives,'convertedTransactions':scan['counts']['converted'],
        'atomicGroupsConverted':residual['convertedAtomicGroups'],
        'remainingAtomicGroups':residual['remainingAtomicGroups'],
        'atomicGroupCategories':residual['atomicGroupCategories'],
        'unresolvedMembershipCategories':residual['membershipCategories'],
        'rootConflictCells':len(roots),'rootConflictGroups':len({g['transaction'] for g,c in roots}),
        'independentChecker':check,'helpers':helpers,'secondPassChanges':second['counts']['converted'],
        'byteCheck':byte_check,'NO_COMPOSITE_FRAGMENTATION':gate['NO_COMPOSITE_FRAGMENTATION'],
        'PROTECTED_WORLD_OBJECT_PRESERVED':gate['PROTECTED_WORLD_OBJECT_PRESERVED'],
        'wholeWorld':gate['result'],'oldOrUnknownCells':scan['counts']['registryIncompatibleBlocks'],
        'clientVisualQA':'NOT_RUN','beta4Release':'NOT_CREATED','main':'NOT_MODIFIED'}
    (DOC/'atomic-integration-verification.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    progress=json.loads((DOC/'membership-proof-progress.json').read_bytes())
    progress.update(converterIntegration=f"ATOMIC_OWNER_GROUPS_INTEGRATED; {residual['convertedAtomicGroups']} converted, {residual['remainingAtomicGroups']} fail-closed",worldGate='FAIL_AFTER_INTEGRATION')
    (DOC/'membership-proof-progress.json').write_text(json.dumps(progress,indent=2)+'\n',encoding='utf-8')
    print('Recorded integration checkpoint; world remains FAIL; no release.')


if __name__=='__main__':record()
