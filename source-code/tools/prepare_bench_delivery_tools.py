"""Adapt proven delivery helpers, with distinct paths/tag and pinned old JAR."""
from pathlib import Path
P=Path(__file__).resolve().parents[1];T=P/'tools'
s=(T/'publish_fix_release.py').read_text(encoding='utf-8').replace("B=P/'fix-release'","B=P/'bench-release'").replace("P/'fix-evidence/acceptance.json'","P/'bench-evidence/acceptance.json'")
s=s.replace("P/'build-tools/checkpoint-publication.json'","P/'bench-evidence/publication.json'").replace("'PUSHED_VERIFIED_READY_RELEASE'","'READY'").replace('diagnostics/2026-10-25-fixes/ASSETS.json','diagnostics/2026-10-25-bench-seating/ASSETS.json')
start="'name':'Dreamwalker visual-physics.2"
i=s.index(start);end=s.index(",'body':notes",i);s=s[:i]+"'name':'Dreamwalker visual-physics.3 — рабочие скамейки'"+s[end:]
(T/'publish_bench_release.py').write_text(s,encoding='utf-8')
s=(T/'install_fix_desktop.py').read_text(encoding='utf-8').replace("D=P/'fix-evidence';B=P/'fix-release'","D=P/'bench-evidence';B=P/'bench-release'").replace('visual-physics.2','visual-physics.3').replace('337ad3113ea02fd9e59b0646167d1ed51c769f048277e3a0858b4c89d2cfceb5','b776096762ea39f4b46fade64bb0aecc4f166d83086cd95bb6dba37846639584')
(T/'install_bench_desktop.py').write_text(s,encoding='utf-8')
print('Prepared isolated bench delivery helpers')
