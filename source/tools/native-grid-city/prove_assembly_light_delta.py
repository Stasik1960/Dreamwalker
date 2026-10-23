"""Verify the exact full-only source additions against the accepted consumer."""
import ast,hashlib
from pathlib import Path
import migrate as m

def sha_text(value):return hashlib.sha256(value.encode('utf-8')).hexdigest()
def main():
    folder=Path(__file__).parent;component=folder/'assembly_components.py';planner=folder/'migrate.py';text=component.read_text(encoding='utf-8');old=text[:text.index('def dimension_id(')]+text[text.index('def main():'):]
    m.require(sha_text(old)=='34d400fde7bfda2a733bdc564b1e31c66fad0e7612ec65626def90cb3936602a','Existing finite component/preview/apply functions changed')
    current=planner.read_text(encoding='utf-8');pieces=["    if not meta(db,'scope') and meta(db,'fullNativeFunctionGateRequired'):\n        import assembly_components\n        assembly_components.prepare_full_light(db,plan)\n","        request=meta(db,'fullAssemblyLightRequest');require(request and sha(request['path'])==request['sha256'],'Full native light requires its finite four-assembly request');light_binding.update(assemblyRequest=request,assemblyPhaseReceipt=str(fresh.with_name(fresh.name+'.assemblies.json')))\n","    if not meta(db,'scope') and meta(db,'fullNativeFunctionGateRequired'):\n        import assembly_components\n        assembly_components.accept_full_light(db,binding,receipt)\n"]
    before=current
    for piece in pieces:m.require(before.count(piece)==1,'Unexpected full-only light hook scope');before=before.replace(piece,'')
    m.require(sha_text(before)=='458ca81d711faebd9e4d4a67901bc22205d610bfd1fa78d4227da9a63a81327f','Unrelated planner functions changed during the full-only light hook')
    for path in (component,planner):ast.parse(path.read_text(encoding='utf-8'))
    sources={str(path):m.sha(path) for path in (component,planner,folder/'assembly_overrides.py',m.PROJECT/'src/nativegridrelight/java/dev/dreamwalker/bloodbornedw/nativegrid/NativeGridRelight.java',m.PROJECT/'src/nativegridrelight/java/dev/dreamwalker/bloodbornedw/nativegrid/NativeGridAssemblyRelight.java')}
    report={'status':'PASS','before':{'assemblyComponentsSha256':sha_text(old),'plannerSha256':sha_text(before)},'after':sources,'finiteConsumerFunctionsUnchanged':True,'plannerOnlyAddsThreeFullCityLightHooks':True,'sourceBuildsRun':0,'worldFilesRead':0,'activeDatabaseReads':0,'mapWrites':0};output=m.ROOT/'research/assembly-full-light-source-delta.json';m.atomic_json(output,report);print(m.canonical(report))
if __name__=='__main__':main()
