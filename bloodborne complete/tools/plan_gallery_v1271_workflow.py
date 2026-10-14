"""Write the sequential final gallery/prepare-delivery workflow; run no step."""
import argparse
from pathlib import Path
from verify_v1271_final_readiness import ROOT,ordinary,write

PYTHON=Path(r'C:\Users\vakir\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe')
EULA=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\runtime-server-v127-new-gallery-author2\eula.txt')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--production-jar',type=Path,required=True)
    p.add_argument('--output',type=Path,default=ROOT/'build/v1271-gallery/workflow.json');p.add_argument('--suffix',default='final')
    p.add_argument('--author-suffix');p.add_argument('--prepared-suffix')
    p.add_argument('--native-resume-proof',type=Path,default=ROOT/'build/v1271-conversion-resume-native5d/dw-conversion-resume-proof.json')
    p.add_argument('--preparation-evidence',type=Path,action='append',default=[]);a=p.parse_args()
    author_suffix=a.author_suffix or a.suffix;prepared_suffix=a.prepared_suffix or a.suffix
    assert all(v.replace('-','').isalnum() for v in (a.suffix,author_suffix,prepared_suffix)),'Safe run suffix required'
    jar=ordinary(a.production_jar);work=ROOT/'build/v1271-gallery';marker=work/f'input-{prepared_suffix}.json';addon=ROOT/f'build/v1271-gallery-addon-{prepared_suffix}'
    authored=ROOT/f'build/runtime-server-v1271-gallery-author-{author_suffix}';reopened=ROOT/f'build/runtime-server-v1271-gallery-reopen-{a.suffix}'
    delivery=ROOT/f'build/v1271-gallery-delivery-{a.suffix}/World';ready=work/f'readiness-{prepared_suffix}.json'
    steps=[]
    def add(name,script,arguments,jvm=False):steps.append({'name':name,'requiresJvm':jvm,'command':[str(PYTHON),str(ROOT/'tools'/script),*[str(v) for v in arguments]]})
    add('Independently count final prepared decision arrays','audit_v1271_prepared_decision_usage.py',[])
    add('Verify final SQL/runtime/native resources bound to actual JAR','verify_v1271_final_readiness.py',['--production-jar',jar,'--report',ready])
    add('Reuse reviewed gallery with current exact source decisions','prepare_gallery_v1271_input.py',['--production-jar',jar,'--output',marker])
    add('Build isolated reviewed author addon','build_gallery_v1271_addon.py',['--production-jar',jar,'--output',addon],True)
    add('Author isolated gallery using cached bootstrap and previously accepted EULA','run_gallery_v1271_server_offline.py',['--jar',jar,'--accepted-eula-file',EULA,'--run-name',f'v1271-gallery-author-{author_suffix}','--report',work/f'author-server-{author_suffix}.json','--extra-mod',ROOT/'build/libs/dreamwalker-v1271-gallery-author-1.2.7.1.jar','--gallery-input',marker,'--gallery-timeout',300,'--startup-timeout',600],True)
    manifest=authored/'catalogue-gallery-output.json';world=authored/'isolated-smoke-world'
    add('Finalize current navigation, utility and empty ALT instructions','finalize_gallery_v1271_world.py',['--world',world,'--manifest',manifest])
    add('Read-only proof of authored world','verify_gallery_v1271.py',['--world',world,'--manifest',manifest,'--production-jar',jar,'--report',work/f'authored-disk-{a.suffix}.json'])
    add('Create clean derived delivery copy','prepare_gallery_v1271_delivery.py',['--world',world,'--output',delivery])
    add('Read-only proof before production reopen','verify_gallery_v1271.py',['--world',delivery,'--manifest',delivery/'gallery-manifest.json','--production-jar',jar,'--report',work/f'delivery-before-{a.suffix}.json'])
    add('Reopen actual derived world using production only and cached bootstrap','run_gallery_v1271_server_offline.py',['--jar',jar,'--accepted-eula-file',EULA,'--run-name',f'v1271-gallery-reopen-{a.suffix}','--report',work/f'reopen-server-{a.suffix}.json','--world-copy',delivery,'--startup-timeout',600],True)
    final_world=reopened/'isolated-smoke-world'
    add('Read-only saved equality after production reopen','verify_gallery_v1271.py',['--world',final_world,'--manifest',final_world/'gallery-manifest.json','--production-jar',jar,'--report',work/f'delivery-reopened-{a.suffix}.json','--compare',work/f'delivery-before-{a.suffix}.json'])
    add('Package verified local gallery and tooling','package_gallery_v1271.py',['--world',final_world,'--production-jar',jar,'--before-report',work/f'delivery-before-{a.suffix}.json','--reopen-report',work/f'delivery-reopened-{a.suffix}.json','--server-report',work/f'reopen-server-{a.suffix}.json','--preparation-readiness',ready,'--addon-source',addon/'java/dev/dreamwalker/bloodbornedw/gallery/FlatGalleryV1271Author.java','--evidence',marker.with_suffix('.preparation.json')])
    add('Package bound preparation without converting original world','package_v1271_preparation.py',['--production-jar',jar,'--readiness',ready,'--native-resume-proof',a.native_resume_proof,*[value for path in a.preparation_evidence for value in ('--evidence',path)]])
    write(ordinary(a.output),{'status':'PREPARED_REQUIRES_FINAL_CITY_AND_JAR_FREEZE_THEN_SEQUENTIAL_NATIVE_EXECUTION',
          'storageGuard':'Run each process through C:/Users/vakir/CodexControl/Guard.ps1 -Mode Operation. Exactly one JVM at a time.',
          'nativeReleaseAcceptance':'Parent gold pixel/lighting/performance and GameTests remain separate requirements. No workflow step is executed by this planner.',
          'productionJar':str(jar),'steps':steps})

if __name__=='__main__':main()
