"""Reuse the established full dependency walker with V12.6 scoped allowances."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
    s=(ROOT/'tools/audit_v125_resources.py').read_text(encoding='utf8')
    s=s.replace('import check_v125_preservation as preservation','import check_v126_preservation as preservation').replace('v125-independent','v126-independent')
    s=s.replace("BASELINE_JAR=ROOT.parent/'releases/Dreamwalker-BB/v12.4-dedup/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.4-dedup.jar'","BASELINE_JAR=ROOT.parent/'releases/Dreamwalker-BB/v12.5-city-review/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.5-city-review.jar'")
    s=s.replace("BASELINE_SHA='4c9e202cff6ae5acd17386a30547726948a0d0d9babf4e529c84ec5c51b0c640'","BASELINE_SHA='76690ecea281ecd5ac59d9b00493546af1978b0819bb2923da977b176f16b845'")
    s=s.replace("NEW_METADATA={'bloodborne_dw/imported-style-retirements.json','bloodborne_dw/imported-collision-v125.json'}","NEW_METADATA={p.relative_to(ROOT/'src/architecture/resources').as_posix() for p in (ROOT/'src/architecture/resources/bloodborne_dw').glob('*v126*.json')}")
    s=s.replace("NEW_CLASSES={'dev/dreamwalker/bloodbornedw/composite/ImportedStyleRetirements','dev/dreamwalker/bloodbornedw/composite/ImportedCollisionV125'}","NEW_CLASSES={p.relative_to(ROOT/'src/architecture/java').as_posix()[:-5] for p in (ROOT/'src/architecture/java').rglob('*V126*.java')}")
    old="  proposal=read(DOC/'citycandidate/extra-canonical-city-poses.json')\n  appended={f\"bloodborne_dw/composite/{g['primaryRegistryId'].split(':')[1]}.json\" for g in proposal['groups']}"
    new="  proposal=read(DOC/'applied-content.json')\n  changed_ids={g['registryId'] for g in proposal['groups']}|{d['registryId'] for d in read(ROOT/'build/v126-door-plan/door-manifest.json')['doors']}\n  appended={f\"bloodborne_dw/composite/{registry.split(':')[1]}.json\" for registry in changed_ids}"
    assert old in s;s=s.replace(old,new)
    s=s.replace("metadata_changed={'bloodborne_dw/remaining-catalogue.json','bloodborne_dw/imported-type-aliases.json'}","metadata_changed={'bloodborne_dw/remaining-catalogue.json','bloodborne_dw/imported-type-aliases.json','bloodborne_dw/debug_catalogue.json'}")
    s=s.replace("   if path in NEW_METADATA:counts['approvedNewMetadata']+=1","   if path in NEW_METADATA:counts['approvedNewMetadata']+=1\n   elif path.startswith('assets/bloodborne_dw/models/v126/doors/') and path.endswith('.json'):counts['approvedNewDoorModels']+=1")
    s=s.replace("  check(final[debug_path]==old[debug_path],'NUMBER_RESERVATIONS_OR_DEBUG_CATALOGUE_CHANGED')","  check([(r['temporaryId'],r['registryId']) for r in json.loads(final[debug_path])['entries']]==[(r['temporaryId'],r['registryId']) for r in json.loads(old[debug_path])['entries']],'NUMBER_RESERVATIONS_OR_REGISTRY_BINDING_CHANGED')")
    s=s.replace("len(primaries-retired)==551","len(primaries-retired)==551") # assembly helper hides seven additional parts independently.
    s=s.replace("  counts.update({'registeredNewIds'", "  assembly=read(ROOT/'build/v126-assembly-plan/assembly-manifest.json');hidden_numbers={n for g in assembly['assemblies'] for n in g['hideIndependentPartIds']}|{'90479'}\n  hidden={r['registryId'] for r in json.loads(final[debug_path])['entries'] if r['temporaryId'] in hidden_numbers}\n  counts['workingNewTypesAfterAssemblies']=len(primaries-retired-hidden)\n  check(counts['workingNewTypesAfterAssemblies']==544,'WORKING_FINISHED_ASSEMBLY_COUNT')\n  counts.update({'registeredNewIds'")
    (ROOT/'tools/audit_v126_resources.py').write_text(s,encoding='utf8',newline='\n')
    print('Prepared final JAR resource/dependency audit; no audit PASS claimed')
if __name__=='__main__':main()
