from pathlib import Path
P=Path(__file__).resolve().parents[1];T=P/'tools'
s=(T/'publish_utility_release.py').read_text(encoding='utf-8').replace('utility-release','faerie-release').replace('utility-evidence','faerie-evidence').replace('2026-10-25-utility-blocks/ASSETS.json','2026-10-25-faerie-veil/ASSETS.json').replace('Dreamwalker visual-physics.4 — служебные блоки и декоративная вода','Dreamwalker visual-physics.5 — Завеса фей')
(T/'publish_faerie_release.py').write_text(s,encoding='utf-8')
s=(T/'install_utility_desktop.py').read_text(encoding='utf-8').replace('utility-evidence','faerie-evidence').replace('utility-release','faerie-release').replace('visual-physics.4','visual-physics.5').replace('5c47f820db5b6eddb79b5a46240d35e38e4029b10f5ef161ad8a1c41830dda3e','b98bf53c37ef8244ade0b66875e1da42d8d22809c5c1ffc1104f2430861ab736')
(T/'install_faerie_desktop.py').write_text(s,encoding='utf-8')
