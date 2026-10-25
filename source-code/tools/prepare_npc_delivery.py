from pathlib import Path
P=Path(__file__).resolve().parents[1]
s=(P/'tools/install_interaction_mod.py').read_text(encoding='utf-8').replace("'interaction-evidence'","'npc-evidence'").replace('visual-physics.7','visual-physics.8');(P/'tools/install_npc_mod.py').write_text(s,encoding='utf-8')
s=(P/'tools/publish_interaction_final.py').read_text(encoding='utf-8').replace("'interaction-evidence'","'npc-evidence'").replace('native-grid-city-interactions-20261026','native-grid-city-npc-20261026').replace('publish_interaction_checkpoint.py','publish_npc_checkpoint.py').replace('Dreamwalker-Interactions','Dreamwalker-NPC')
a=s.index('body=');b=s.index('assert release[',a)
s=s[:a]+'''body=(D/'REPORT-RU.md').read_text(encoding='utf-8')+'\\n\\nПолные исходники и инструкции восстановления приложены. Карта не менялась: используйте свою текущую bloodborne или карту стабильного релиза interactions-20261026. Предыдущие релизы сохранены.\\n'
if release is None:release=api('POST','/repos/'+REPO+'/releases',{'tag_name':TAG,'target_commitish':commit,'name':'Dreamwalker — NPC и Custom NPC, visual-physics.8','body':body,'draft':True,'prerelease':False,'make_latest':'false'})
''' .replace('\n+','\n')+s[b:];(P/'tools/publish_npc_final.py').write_text(s,encoding='utf-8')
