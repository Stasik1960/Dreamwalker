from pathlib import Path
P=Path(__file__).resolve().parents[1]
s=(P/'tools/run_interaction_checks.py').read_text(encoding='utf-8')
s=s.replace("'interaction-evidence'","'npc-evidence'").replace("'interaction-profiles'","'npc-profiles'").replace('visual-physics.7','visual-physics.8').replace('dw.interaction.qa.','dw.npc.qa.').replace('DW_INTERACTION_QA_HEAP','DW_NPC_QA_HEAP').replace("'interaction-profiles/server-1/world'","'npc-profiles/server-1/world'").replace('DWInteractionQA','DWNpcQA')
# Private full-pack client, not the city map: keep the minimal server's QA world.
s=s.replace("elif client:shutil.copytree(P/('interaction-profiles/preview-2/world' if fullMap else 'npc-profiles/server-1/world'),profile/'saves/interactions')","elif client:shutil.copytree(P/'npc-profiles/server-1/world',profile/'saves/interactions')")
s=s.replace("if (role in ('preview','apply')", "if (client and mid!='bloodborne_dw') or (role in ('preview','apply')")
s=s.replace("if fullMap:shutil.copytree(G/'config',profile/'config')","if client:shutil.copytree(G/'config',profile/'config')")
(P/'tools/run_npc_checks.py').write_text(s,encoding='utf-8')
s=(P/'tools/publish_interaction_checkpoint.py').read_text(encoding='utf-8')
s=s.replace("'interaction-evidence'","'npc-evidence'").replace("branch='codex/interaction-fixes-20261025'","branch='codex/npc-controls-20261026'")
a=s.index('owned=[');b=s.index('paths=[]',a)
s=s[:a]+'''owned=['gradle.properties','src/architecture/resources/fabric.mod.json','src/architecture/resources/bloodborne_dw.npc.mixins.json',
'src/architecture/java/dev/dreamwalker/bloodbornedw/compat/OptionalMixinContract.java',
'src/architecture/java/dev/dreamwalker/bloodbornedw/compat/customnpc/CustomNpcMobRendererMixin.java',
'src/architecture/java/dev/dreamwalker/bloodbornedw/tool/BbToolService.java','src/architecture/java/dev/dreamwalker/bloodbornedw/tool/BbToolRotations.java',
'src/rp/java/dev/dreamwalker/bloodbornerp/mob/RpMobEntity.java','src/rp/java/dev/dreamwalker/bloodbornerp/client/NpcMobVisualBridge.java','src/rp/java/dev/dreamwalker/bloodbornerp/client/CatalogMobRenderer.java']
owned += [p.relative_to(P).as_posix() for p in (P/'src/restorationqa').rglob('*') if p.is_file()]
owned += [p.relative_to(P).as_posix() for p in (P/'tools').glob('*npc*.py')]
''' .replace('\n+','\n')+s[b:]
s=s.replace('CURRENT-INTERACTIONS-RU.md','CURRENT-NPC-RU.md').replace("D/('HANDOFF-RU.md' if (D/'acceptance.json').exists() else 'PROGRESS-LATEST.md')","D/('HANDOFF-RU.md' if (D/'acceptance.json').exists() else 'NOTES-RU.md')")
a=s.index('readme=');b=s.index("(D/'source-files.json')",a);s=s[:a]+s[b:]
s=s.replace('diagnostics/2026-10-25-interactions','diagnostics/2026-10-26-npc').replace('Fix utility block interactions, shutter windows and RP physical bounds','Add passive NPC actors, tool controls, corpse lifecycle and Custom NPC models')
(P/'tools/publish_npc_checkpoint.py').write_text(s,encoding='utf-8')
