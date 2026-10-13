"""Freeze-check original V12 content and justify minimal NEW-only integration hooks."""
from pathlib import Path
import hashlib,json,subprocess,os
ROOT=Path(__file__).resolve().parents[1];DOC=ROOT/'docs/v12.4-dedup'
ALLOW={
 'src/architecture/java/dev/dreamwalker/bloodbornedw/'+p for p in [
 'architecture/RemainingArchitecture.java','composite/CompositeArchitecture.java','composite/CompositeClient.java','composite/CompositeItem.java','composite/CompositeRootBlock.java','composite/CompositeRuntime.java','composite/CompositeSourceShift.java','composite/CompositeSpec.java','composite/ImportedCollisionMigration.java','composite/ImportedLadderClimbing.java','composite/mixin/CatalogueStackMigrationMixin.java','link/CompositeMechanismBridge.java','tool/ToolTarget.java','tool/ToolCommands.java']}
ALLOW|={'src/gametest/resources/fabric.mod.json','src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/RemainingTransferGameTests.java'}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    frozen=json.loads((DOC/'baseline-protected-hashes.json').read_text(encoding='utf8'));unexpected=[];hooks=[];same=0
    for path,digest in frozen['files'].items():
        if sha(ROOT/path)==digest:same+=1
        elif path in ALLOW:hooks.append(path)
        else:unexpected.append(path)
    base=json.loads((DOC/'baseline-content.json').read_text(encoding='utf8'));debug=json.loads((ROOT/'src/architecture/resources/bloodborne_dw/debug_catalogue.json').read_text(encoding='utf8'))
    entries={r['registryId']:r for r in debug['entries']};rows=all(entries[r['registryId']]==r for r in base['originalRows']);numbers=[r['temporaryId'] for r in debug['entries']]
    reserved=len(numbers)==len(set(numbers)) and numbers==[r['temporaryId'] for r in base['debugCatalogue']['entries']]
    manifest=json.loads((ROOT/'src/architecture/resources/bloodborne_dw/imported-type-aliases.json').read_text(encoding='utf8'))
    newonly=all(r['oldRegistryId'].startswith('bloodborne_dw:source_') and r['newRegistryId'].startswith('bloodborne_dw:source_') for r in manifest['mappings'])
    result={'baselineCommit':base['baselineCommit'],'unchangedProtectedFiles':same,'newOnlyIntegrationHooks':hooks,'unexpectedChangedProtectedFiles':unexpected,'originalCatalogueRowsUnchanged':rows,'numberReservationsUnchanged':reserved,'mappingScopeNewOnly':newonly,'status':'PASS' if not unexpected and rows and reserved and newonly else 'FAIL'}
    report=DOC/'preservation-audit.json';data=(json.dumps(result,ensure_ascii=False,indent=2)+'\n').encode('utf8')
    if not report.exists() or report.read_bytes()!=data:
        temporary=report.with_suffix('.atomic.tmp');temporary.write_bytes(data);os.replace(temporary,report)
    assert result['status']=='PASS',result
if __name__=='__main__':main()
