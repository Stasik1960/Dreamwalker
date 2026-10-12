"""Compact source-backed review platforms; exact requested full window stacks."""
from __future__ import annotations
import collections,json,sqlite3
from v125_map_index import ROOT,OUT,CACHE,MAP,PACK,BASELINE,dump,sha,token
from v125_map_resolve import Resolver
DEST=ROOT/'docs/v12.5-city-review/assemblies'

def main():
    resolver=Resolver();con=sqlite3.connect(f'file:{CACHE}?mode=ro',uri=True)
    info=json.loads((DEST/'finite-application-inventory.json').read_text());byapp={r['id']:r for r in info['applicationCombinations']}
    positions={};upperids={};lowerids={}
    for aid,r in byapp.items():
        if r['target'].get('mainId')=='93116':upperids[aid]=r
        if r['target'].get('mainId')=='93117':lowerids[aid]=r
    allids=set(upperids)|set(lowerids)
    for dim,x,y,z,s,a in con.execute('SELECT dimension,x,y,z,state,applications FROM cells'):
        if a in allids:positions[(dim,x,y,z)]=(s,a)
    full=collections.defaultdict(list)
    for pos,(state,aid) in positions.items():
        if aid not in upperids:continue
        upper=upperids[aid];near=(pos[0],pos[1],pos[2]-2,pos[3]);lower=positions.get(near)
        if not lower or lower[1] not in lowerids:continue
        orientation=upper['target']['properties']['rotation']
        if lowerids[lower[1]]['target']['properties']['rotation']!=orientation:continue
        stack=[(near,lower)];at=near
        for _ in range(24):
            nextpos=(at[0],at[1],at[2]-1,at[3]);other=positions.get(nextpos)
            if other and other[1] in lowerids and lowerids[other[1]]['target']['properties']['rotation']==orientation:stack.append((nextpos,other));at=nextpos
            else:break
        stack.reverse();stack.append((pos,(state,aid)));origin=stack[0][0]
        components=[]
        for number,(p,(_,app)) in enumerate(stack,1):
            r=byapp[app];components.append({'number':number,'relativePosition':[p[i]-origin[i] for i in range(1,4)],**r['target'],'sourceApplications':r['sourceApplications'],'sourcePosition':list(p[1:]),'sourceVisualBoundsLocal':r['bounds']})
        # Orientation is a preview state, not a separate logical construction.
        sig=token([(c['mainId'],c['relativePosition'],c['properties'].get('variant'),c['sourceShift']) for c in components])
        full[sig].append({'dimension':pos[0],'origin':list(origin[1:]),'upperPosition':list(pos[1:]),'components':components})
    windows=[]
    for no,(sig,examples) in enumerate(sorted(full.items(),key=lambda p:(len(p[1][0]['components']),-len(p[1]))),1):
        row=examples[0];windows.append({'key':f'SOURCE-WINDOW-STACK-{no:03d}','russianLabel':f'Готическое окно93117 +93116 · нижних секций{len(row["components"])-1}','kind':'USER_SPECIFIED_SOURCE_CONFIRMED_FULL_LAYOUT','confidence':'HIGH_SOURCE_REPEAT_AND_JOIN','occurrences':len(examples),'sourceExamples':[{'dimension':r['dimension'],'origin':r['origin'],'upperPosition':r['upperPosition']} for r in examples[:3]],'components':row['components'],'orientationVariants':sorted({r['components'][0]['properties']['rotation'] for r in examples}),'objectBoundaryNote':'Continuous same-orientation lower93117 cells beneath a top93116 with proven nearest+2Y; original source cells, never guessed missing filler.'})
    pairs=json.loads((DEST/'gallery-preview-assemblies.json').read_text())['assemblies'];buckets=collections.defaultdict(list)
    for row in pairs:
        if row['touchOccurrences']==0:continue
        ids=tuple(c['mainId'] for c in row['components'])
        if ids==('93117','93116'):continue
        buckets[ids].append(row)
    compact=[]
    for ids,rows in buckets.items():
        rows.sort(key=lambda r:(-r['touchOccurrences'],-r['occurrences'],sum(abs(v) for v in r['components'][1]['relativePosition']),r['key']))
        r=rows[0]
        compact.append({**r,'groupMainIds':list(ids),'alternativeSourceLayouts':len(rows),'allAlternativeCandidateKeys':[x['key'] for x in rows],'sourceLayoutGroupPolicy':'One representative actual source layout per canonical component ID group. Full alternative typed poses and offsets retained in gallery-preview-assemblies.json for review; no model/registration merge.'})
    compact.sort(key=lambda r:(not r['confidence'].startswith('USER'),-r['touchOccurrences'],tuple(r['groupMainIds'])))
    platforms=windows+compact
    result={'schemaVersion':1,'status':'PREVIEW_READY_SOURCE_LAYOUTS_REQUIRE_USER_REVIEW','baselineCommit':BASELINE,'mapSha256':sha(MAP),'packSha256':sha(PACK),'canonicalMappingSha256':sha(ROOT/'docs/v12.4-dedup/type-state-mapping.json'),'platformCount':len(platforms),'sourceConfirmedWindowStackTypes':len(windows),'pairGroupRepresentatives':len(compact),'platforms':platforms,'galleryIntegration':{'preserveSourceRelativePositions':True,'oneSharedVerticalSeatingTranslation':True,'noIndividualReseating':True,'marginBlocks':2,'betweenPlatformsBlocks':2,'labelOnlyNoFunctionalMerge':True},'sourceSemanticBoundaryLimit':'360-ish finite adjacency groups are source-backed review proposals, not approval that every neighbor is one finished authored object. Native previews can expose these uncertainties; source graph has no stored logical-object boundaries.'}
    dump(DEST/'gallery-preview-platforms-compact.json',result)
    lines=['Составные конструкции: исходные раскладки для просмотра','Основа653ae307; оригинальные v15/v16 и V12 не изменялись.',f'Платформ: {len(platforms)}. Целых вариантов окна: {len(windows)}.', '93117 +93116: верхняя ячейка на+2Y; ниже могут повторяться93117.', '93066 +93069: подтверждено19совместных установок на+2Y.', 'Компоненты использовать с указанными SourceShift и rotation; сдвиг всей конструкции поY разрешён только единый.', 'Соседние детали без доказанного назначения помечены кандидатом. Отдельные регистрации/предметы/открываемость не изменялись.', '', 'Группы:']
    for p in platforms:lines.append(f'{p["key"]}: {p["russianLabel"]}; встречено{p["occurrences"]}; компонентов{len(p["components"])}; {p["confidence"]}')
    (DEST/'PREVIEW-GROUPS-RU.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8-sig')

if __name__=='__main__':main()
