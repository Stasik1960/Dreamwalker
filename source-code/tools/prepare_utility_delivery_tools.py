from pathlib import Path
P=Path(__file__).resolve().parents[1];T=P/'tools'
s=(T/'publish_bench_release.py').read_text(encoding='utf-8').replace('bench-release','utility-release').replace('bench-evidence','utility-evidence').replace('2026-10-25-bench-seating/ASSETS.json','2026-10-25-utility-blocks/ASSETS.json').replace('Dreamwalker visual-physics.3 — рабочие скамейки','Dreamwalker visual-physics.4 — служебные блоки и декоративная вода')
(T/'publish_utility_release.py').write_text(s,encoding='utf-8')
s=(T/'install_bench_desktop.py').read_text(encoding='utf-8').replace('bench-evidence','utility-evidence').replace('bench-release','utility-release').replace('visual-physics.3','visual-physics.4').replace('b776096762ea39f4b46fade64bb0aecc4f166d83086cd95bb6dba37846639584','5c47f820db5b6eddb79b5a46240d35e38e4029b10f5ef161ad8a1c41830dda3e')
(T/'install_utility_desktop.py').write_text(s,encoding='utf-8')
