from pathlib import Path
P=Path(__file__).resolve().parents[1];T=P/'tools'
s=(T/'run_utility_profile.py').read_text().replace('utility','faerie').replace('Utility','Faerie').replace('visual-physics.4','visual-physics.5')
old=""" try:code=p.wait(timeout=1200 if role=='client' else 600)
 except subprocess.TimeoutExpired:p.terminate();code=p.wait(timeout=30)"""
new=""" savedAt=None
 while p.poll() is None:
  now=time.time()
  if now-start>(1200 if role=='client' else 600):p.terminate();break
  if role=='server' and (D/'server-checks.json').exists():
   if 'All dimensions are saved' in (D/'console.log').read_text(encoding='utf-8',errors='replace'):
    if savedAt is None:savedAt=now
    if now-savedAt>12:
     (D/'post-save-stop.json').write_text(json.dumps({'worldSaved':True,'reason':'Own test JVM remained after all dimensions saved','pid':p.pid},indent=2));p.terminate();break
  time.sleep(1)
 code=p.wait(timeout=30)"""
assert old in s;s=s.replace(old,new);(T/'run_faerie_profile.py').write_text(s,encoding='utf-8')
s=(T/'publish_utility_checkpoint.py').read_text().replace("BR='codex/utility-blocks-20261025';D=P/'utility-evidence'","BR='codex/faerie-veil-20261025';D=P/'faerie-evidence'").replace('adac9f9060e772c7c836f72a074b6a4dade198e2','5249d169fd8e83994dadbca330681886b086a347').replace('2026-10-25-utility-blocks','2026-10-25-faerie-veil').replace('utility-full-source-local-20261025','faerie-full-source-local-20261025').replace('utility-evidence/','faerie-evidence/').replace('utility-release/','faerie-release/').replace('four utility blocks tested.','selected faerie veil tested.').replace('utility blocks .4','faerie veil .5').replace('Green nature portal: visual choice required before implementation.','Chosen variant 3: Faerie mist; no block entity or per-block ticker.').replace("if git('diff','--cached','--name-only'):git('commit','-m','Implement creative utility blocks and nonphysical decorative water' if accepted else 'Checkpoint utility block implementation and pending portal design')","if git('diff','--cached','--name-only'):git('commit','-m','Add approved faerie veil with shared wall animation' if accepted else 'Checkpoint selected faerie veil and targeted QA')")
anchor="files +=[P/n for n in ('README.md','build.gradle','gradle.properties')]"
extra="""files +=list((P/'src/architecture/java/dev/dreamwalker/bloodbornedw/architecture').glob('FaerieVeil*.java'))
files +=[p for p in (P/'src/faerieqa').rglob('*') if p.is_file()]
files +=list((P/'tools').glob('*faerie*.py'))
files +=[assets/'blockstates/dw_faerie_veil.json',assets/'models/block/dw_faerie_veil.json',assets/'models/item/dw_faerie_veil.json']
files +=[p for folder in (assets/'textures/block/faerie_veil',assets/'models/block/faerie_veil') for p in folder.rglob('*') if p.is_file()]
"""
assert anchor in s;s=s.replace(anchor,extra+anchor);(T/'publish_faerie_checkpoint.py').write_text(s,encoding='utf-8')
s=(T/'run_utility_task.py').read_text().replace('utility-evidence','faerie-evidence').replace('publish_utility','publish_faerie').replace('package_utility','package_faerie').replace('install_utility','install_faerie')
(T/'run_faerie_task.py').write_text(s,encoding='utf-8')
