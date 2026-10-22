"""Prepare a separate resume repository, never touch the original DW checkout."""
from pathlib import Path
import hashlib,json,os,shutil,stat,subprocess
P=Path(__file__).resolve().parents[2];ROOT=P.parent;O=ROOT.parent/'DW-native-grid-github-transfer-20261022'
W=ROOT.parent
(O/'TASK').mkdir(exist_ok=True);(O/'handoff').mkdir(exist_ok=True)
shutil.copy2(W/'DW/audits/Dreamwalker-BB/2026-10-21-native-grid-city/START-RU.txt',O/'TASK/START-RU.txt')
for q in (ROOT/'research/transfer').glob('*-handoff-RU.txt'):shutil.copy2(q,O/'handoff'/q.name)
shutil.copy2(ROOT/'research/CONTRACT-V1.txt',O/'handoff/CONTRACT-V1.txt')
shutil.copy2(ROOT/'research/progress.json',O/'handoff/progress-at-transfer.json')
dest=O/'source';dest.mkdir(exist_ok=True)
excluded={'.git','.gradle','build','logs','crash-reports','__pycache__','.idea'}
copied=[];large=[]
for current,dirs,names in os.walk(P,followlinks=False):
    dirs[:]=[d for d in dirs if d not in excluded and not (os.lstat(Path(current)/d).st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT)]
    for name in names:
        q=Path(current)/name
        if os.lstat(q).st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT:continue
        rel=q.relative_to(P);out=dest/rel
        if q.stat().st_size>90*1024**2:large.append(str(rel));continue
        out.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(q,out);copied.append(str(rel))
(O/'.gitignore').write_text('release-assets/\npack-progress.json\n__pycache__/\n*.part\n',encoding='utf-8')
(O/'AGENTS.md').write_text('Read README.md, HANDOFF-RU.md, TASK/START-RU.txt and all handoff/*-handoff-RU.txt completely before project work. This is an unfinished resume checkpoint, not READY. Download and verify ALL release data, including both SQLite databases, immutable original source map and current pilot. Keep original evidence hashes; create a reviewed path binding on a different PC. Preserve whole assemblies/v12; exactly three named subagents; root owns builds/Minecraft/world writes. Do not restart conversion, repeat passed unrelated checks, use stale PID stop scripts, or resume paused E migration. Never upload account credentials.\n',encoding='utf-8')
receipt={'status':'PREPARED_SEPARATE_RESUME_REPOSITORY','path':str(O),'sourceFilesCopied':len(copied),'largeFilesInReleaseOnly':large,'originalGitModified':False}
(O/'git-preparation.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(receipt,ensure_ascii=False))
