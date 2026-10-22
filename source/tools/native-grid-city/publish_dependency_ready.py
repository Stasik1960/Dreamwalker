"""Publish exact root commit/admission handoff after semantic staging + review."""
import pilot_dependencies as p
m=p.m
def main():
    plan_path=p.REPAIR/'plan.json';file_path=p.REPAIR/'file-plan.json';plan=m.read(plan_path);files=m.read(file_path)
    m.require(plan['status']=='DRY_RUN' and files['status']=='STAGED' and files['semanticPlan']=={'path':str(plan_path),'sha256':m.sha(plan_path)},'Exact staged repair plan required')
    sources={plan['implementation']['path']:plan['implementation']['sha256'],str(p.Path(m.__file__).resolve()):plan['migrationSha256'],str(p.Path(p.c.__file__).resolve()):plan['contextSha256'],plan['semanticHelper']['path']:plan['semanticHelper']['sha256']}
    for path,sha in sources.items():m.require(m.sha(path)==sha,'Frozen implementation changed')
    for ref in (plan['inventory'],plan['audit'],plan['original427NativeDependencyCoverage']):m.require(m.sha(ref['path'])==ref['sha256'],'Frozen semantic evidence changed')
    review_path=p.REPAIR/'independent-writer-review.json';review=m.read(review_path);m.require(review['status']=='PASS_SOURCE_REVIEW_ONLY' and not review['findings'],'Focused semantic repair review required')
    repair_tests=m.ROOT/'research/pilot-dependency-fixtures.json';migration_tests=m.ROOT/'research/migration-fixtures.json'
    m.require(m.read(repair_tests)['status']=='PASS' and m.read(repair_tests)['tests']==9 and m.read(migration_tests)['status']=='PASS','Scoped fixtures must pass')
    prefix="& 'C:\\Users\\vakir\\CodexControl\\Guard.ps1' -Mode Operation -Executable 'C:\\Users\\vakir\\miniconda3\\python.exe' -Arguments "
    suffix=" -WorkingDirectory '"+str(m.ROOT/'source')+"'"
    # ROOT is the isolated project root, source files are its source child.
    prepare=prefix+"'\"tools/native-grid-city/pilot_dependencies.py\" --prepare'"+suffix
    admit=prefix+"'\"tools/native-grid-city/pilot_dependencies.py\" --admit \""+str(p.REPAIR/'file-commit-receipt.json')+"\"'"+suffix
    result={'schemaVersion':1,'status':'READY_FOR_ROOT_FILE_COMMIT','kind':plan['kind'],'finalInventory':plan['inventory'],'audit':plan['audit'],'semanticPlan':{'path':str(plan_path),'sha256':m.sha(plan_path)},'filePlan':{'path':str(file_path),'sha256':m.sha(file_path)},'sourceHashes':sources,'focusedReview':{'path':str(review_path),'sha256':m.sha(review_path)},'fixtures':[{'path':str(v),'sha256':m.sha(v)} for v in (repair_tests,migration_tests)],'originalTerrainChunks':427,'auxiliaryTerrainChunks':len(plan['auxiliaryChunks']),'sourceOwnerShardCount':plan['requiredSourceShardCount'],'filesToCommit':len(files['files']),'restorationCells':{v:w['lossesRestored'] for v,w in plan['worlds'].items()},'oldNativeSavedRowsInvalidated':8,'freshEngineReceipt':plan['lightPolicy']['freshReceipt'],'freshEngineDoneCursor':plan['lightPolicy']['freshDoneCursor'],'commands':{'prepareAlreadyExecutedStagingOnly':prepare,'admitOnlyAfterRootCommit':admit},'mapWrites':0,'databaseWrites':0}
    m.atomic_json(p.REPAIR/'READY.json',result);print(m.canonical({'path':str(p.REPAIR/'READY.json'),'sha256':m.sha(p.REPAIR/'READY.json'),'filePlanSha256':m.sha(file_path)}))
if __name__=='__main__':m.control_check();main()
