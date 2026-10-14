"""Apply the reviewed v12.7 read-only disk verifier to the new gallery schema.

Checks remain unchanged: saved UUID/NBT/geometry, exact platform bounds,
catalogue identity, retained original V12, and production-only reopen equality.
Hidden style primaries are explicitly excluded from main display platforms.
"""
from pathlib import Path

path=Path(__file__).with_name('verify_gallery_v127.py')
source=path.read_text(encoding='utf8').replace('dreamwalker-flat-gallery-v127','dreamwalker-flat-gallery-v1271')
needle="retired.update(assemblies['hiddenPartRedirects'])"
assert source.count(needle)==1
source=source.replace(needle,needle+"\n        retired.update(r['registryId'] for r in retirement['retirements'])\n        retired.update('bloodborne_dw:'+r['path'] for r in remaining['objects'] if r.get('conversionOnly'))")
def checked_replace(old,new):
    global source
    assert source.count(old)==1,old
    source=source.replace(old,new)
checked_replace("    before={str(f.relative_to(a.world)):sha(f) for f in base.saved_files(a.world)}", "    from gallery_v1271_alias_contract import AliasContract\n    with zipfile.ZipFile(a.production_jar) as alias_jar: alias_contract=AliasContract(alias_jar)\n    before={str(f.relative_to(a.world)):sha(f) for f in base.saved_files(a.world)}")
checked_replace("    mainrows=m['exhibits'];groups=m['reviewPlatforms'];city=m['city'];parts=list(mainrows)", "    navigation_functions=a.world/'datapacks/dw_gallery_entry/data/dw_gallery/functions'\n    for mode in ('alt','base'):\n        lines=[line.strip() for line in (navigation_functions/(mode+'.mcfunction')).read_text(encoding='utf8').splitlines() if line.strip() and not line.lstrip().startswith('#')]\n        assert len(lines)==1 and lines[0].startswith('tellraw @s '),'Player-only bb command cannot be parsed directly by datapack loader'\n        message=json.loads(lines[0][len('tellraw @s '):]);assert message['clickEvent']=={'action':'run_command','value':'/bb '+mode+' 300'}\n    for function_file in navigation_functions.rglob('*.mcfunction'):\n        assert not any(re.search(r'(?:^|\\brun )bb(?:\\s|$)',line.strip()) for line in function_file.read_text(encoding='utf8').splitlines() if line.strip() and not line.lstrip().startswith('#')),('Unparseable player-only bb function',str(function_file))\n    mainrows=m['exhibits'];groups=m['reviewPlatforms'];city=m['city'];parts=list(mainrows)")
checked_replace("'typedSha256':typed_hash(tag),'nbt':base.plain(tag)}", "'typedSha256':typed_hash(tag),'payloadTypedSha256':typed_hash(raw.get('payload',Tag(10,{}))),'nbt':base.plain(tag)}")
checked_replace("        actual=residents[uuid];assert actual['position']==row['root'] and actual['owner']['registryId']==row['registry']", "        actual=residents[uuid];canonical_registry=alias_contract.verify(row,actual,city['translation']);assert actual['position']==row['root'] and actual['owner']['registryId']==canonical_registry")
checked_replace("        assert states[tuple(row['root'])]['registry']==row['registry'] and states[tuple(row['root'])]['properties']==row['placedState']", "        assert states[tuple(row['root'])]['registry']==canonical_registry and states[tuple(row['root'])]['properties']==row['placedState']")
checked_replace("        for c in owned:assert c['value']['owner']['registryId']==row['registry'] and c['value']['owner']['root']==row['root']", "        for c in owned:\n            assert c['value']['owner']['registryId']==canonical_registry and c['value']['owner']['root']==row['root']\n            assert c['value']['rootData']['properties']==row['placedState'],('Stale cached root state',row['key'],c['position'])\n            assert c['value']['rootData']['typedPayloadSha256']==actual['payloadTypedSha256'],('Stale cached typed root payload',row['key'],c['position'])")
checked_replace("            saved={'registryId':row['registry'],'properties':row['placedState'],'relativePosition':row['root'],", "            saved={'registryId':alias_contract.expected_registry(row),'properties':row['placedState'],'relativePosition':row['root'],")
checked_replace("'visualAcceptance':'NOT_RUN_BY_DISK_PROOF'}", "'visualAcceptance':'NOT_RUN_BY_DISK_PROOF','compatibilityAliasNormalization':alias_contract.report(),'allCachedRootStatesAndTypedPayloadsEqualResidents':True,'playerOnlyBbShortcutsUseVanillaTellrawChatCommands':True}")
checked_replace("print(json.dumps({k:v for k,v in result.items() if k!='stableExhibits'},ensure_ascii=False))", "console={k:v for k,v in result.items() if k not in ('stableExhibits','compatibilityAliasNormalization')};console['compatibilityAliasNormalization']={k:v for k,v in alias_contract.report().items() if k!='rows'};print(json.dumps(console,ensure_ascii=False))")
exec(compile(source,str(path),'exec'),{'__file__':__file__,'__name__':'__main__'})
