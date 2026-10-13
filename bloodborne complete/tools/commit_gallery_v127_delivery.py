"""Commit only the gallery tooling/reports; never stage worlds or unrelated work."""
import json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
paths=['docs/v12.7-gallery','tools/gallery-v127']
paths += ['tools/'+name for name in ('prepare_gallery_v127_input.py','build_gallery_v127_addon.py','prepare_gallery_v127_checks.py','verify_gallery_v127.py','finalize_gallery_v127_world.py','normalize_gallery_v127_manifest.py','prepare_gallery_v127_capture.py','monitor_gallery_v127_process.py','prepare_gallery_v127_delivery.py','run_gallery_v127_preflight.py','package_gallery_v127.py','publish_gallery_v127.py','record_gallery_v127_delivery_review.py','diagnose_gallery_v127_lighting.py','diagnose_gallery_v127_light_resources.py','record_gallery_v127_black_block_diagnosis.py','commit_gallery_v127_delivery.py')]
def git(*args):return subprocess.run(['git',*args],cwd=ROOT,capture_output=True,text=True,encoding='utf8',check=True).stdout
assert git('branch','--show-current').strip()=='codex/bb-v12-tool'
assert not git('diff','--cached','--name-only').strip()
assert not git('diff','--name-only','--','src','build.gradle','gradle.properties','settings.gradle').strip()
git('add','--',*paths)
staged=git('diff','--cached','--name-only').splitlines()
assert all(s.startswith(('bloodborne complete/docs/v12.7-gallery/','bloodborne complete/tools/')) for s in staged)
git('-c','core.whitespace=cr-at-eol','diff','--cached','--check')
message='Add V12.7 gallery for issue review with native WorldEdit checks'
result=git('commit','-m',message)
report={'status':'PASS_SCOPED_GALLERY_COMMIT','commit':git('rev-parse','HEAD').strip(),'paths':staged,'productionCodeChanged':False}
(ROOT/'build/v127-gallery-commit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(result);print(json.dumps(report,ensure_ascii=False))
