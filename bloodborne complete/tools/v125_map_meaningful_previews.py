"""Conservative whole-construction review previews; independent adjacency separate."""
from __future__ import annotations
import collections,copy,json,sqlite3
from v125_map_index import ROOT,MAP,PACK,CACHE,BASELINE,dump,sha,token
from v125_map_resolve import Resolver
DEST=ROOT/'docs/v12.5-city-review/assemblies'
DOORS=ROOT/'docs/v12.5-city-review/doors'

def main():
    resolver=Resolver();data=json.loads((DEST/'gallery-preview-platforms-compact.json').read_text());meaningful=[];doubtful=[]
    # Concrete complementary visible roles, based on actual source model/texture
    # nominations and repeated touching layouts. Genericwall+plant, independent
    #clutter/furniture/gravestones and merely touching background are excluded.
    roles={
      ('93066','93069'):'Окно с каменным орнаментом · основание и завершение',
      ('93117','93115'):'Готическое окно · прямое основание и треугольный верх',
      ('93113','93117'):'Готическое окно · нижняя рамка и промежуточная секция',
      ('93113','93116'):'Готическое окно · нижняя рамка и арочный верх',
      ('93113','93115'):'Готическое окно · нижняя рамка и треугольный верх',
      ('90179','90178'):'Каменная пилястра · основание и верхняя секция',
      ('90160','90160'):'Вертикальное деревянное украшение · составной высокий вариант',
      ('90458','90923'):'Арочная деревянная дверь · верхняя каменная арка',
      ('90143','90142'):'Металлические ворота · створка и орнаментированный верх',
      ('90031','90623'):'Деревянная дверь · нижние панели и решётчатое завершение',
      ('90301','90251'):'Арочное окно · основной проём и декоративное завершение',
      ('90586','90586'):'Приставная лестница · продолжающиеся секции',
    }
    for row in data['platforms']:
        ids=tuple(c['mainId'] for c in row['components']);offset=row['components'][-1]['relativePosition'];sourcejoin=row.get('touchOccurrences',1)>0
        if row['kind']=='USER_SPECIFIED_SOURCE_CONFIRMED_FULL_LAYOUT':meaningful.append(row);continue
        role=roles.get(ids)
        if role and sourcejoin and (offset[1]>0 or ids==('90586','90586')):
            meaningful.append({**row,'russianLabel':role,'kind':'PLAUSIBLE_COMPLEMENTARY_SOURCE_CONSTRUCTION_PREVIEW','confidence':'SOURCE_REPEAT_VISIBLE_COMPLEMENTARY_ROLES_REVIEW_REQUIRED','boundaryReason':role+'; точная совместная исходная раскладка с касанием конвертов. Не подтверждает слияние предметов или механизм двери.'})
        else:
            reason='Onlyneighbor/envelopecontact proven. Independent background/clutter/vegetation/trim placement or no established complementary visible role; no inference that the components are one finished construction.'
            if ids==('90253','90253'):reason='ActuallyviewedjoinedsourcePNGtile11 showsanordinaryL-shapedmasonrycorner, notcomplementaryfinitepilasterparts; keepasindependentarchitecturalwallsections.'
            if ids==('90304','90390'):reason='ActuallyviewedjoinedsourcePNGtile22 showstwoseparateroundwindowopeningsstackedwithagap, notinnerandouterpartsofonewindow.'
            doubtful.append({**row,'galleryDisposition':'REPORT_ONLY_NOT_A_WHOLE_OBJECT_PREVIEW','boundaryReason':reason})
    # Exact three-part stained-glass window with tiny base/end ornament. The
    # two pair nominees were only partials of this repeated complete layout.
    inventory=json.loads((DEST/'finite-application-inventory.json').read_text());byapp={r['id']:r for r in inventory['applicationCombinations']}
    rolesets=[{r['id'] for r in inventory['applicationCombinations'] if any(a['model']=='minecraft:block/'+name for a in r['sourceApplications'])} for name in ['pressure_plate/window_end_6','pressure_plate/window_bottom_4','pressure_plate/window_top_2']]
    allowed=set.union(*rolesets);positions={};con=sqlite3.connect(f'file:{CACHE}?mode=ro',uri=True)
    if allowed:
        placeholders=','.join('?' for _ in allowed)
        for dim,x,y,z,a in con.execute(f'SELECT dimension,x,y,z,applications FROM cells WHERE applications IN ({placeholders})',list(allowed)):positions[(dim,x,y,z)]=a
    triplegroups=collections.defaultdict(list)
    for pos,app in positions.items():
        if app not in rolesets[0]:continue
        ps=[pos,(pos[0],pos[1],pos[2]+2,pos[3]),(pos[0],pos[1],pos[2]+5,pos[3])]
        if not all(p in positions and positions[p] in rolesets[i] for i,p in enumerate(ps)):continue
        components=[]
        for i,p in enumerate(ps):
            info=byapp[positions[p]];target=info['target']
            if not target['resolution'].startswith('EXACT'):break
            components.append({'number':i+1,'relativePosition':[p[j]-pos[j] for j in range(1,4)],**target,'sourcePosition':list(p[1:]),'sourceApplications':info['sourceApplications'],'sourceVisualBoundsLocal':info['bounds']})
        else:
            signature=token([(c['mainId'],c['properties']['variant'],c['relativePosition'],c['sourceShift']) for c in components]);triplegroups[signature].append({'dimension':pos[0],'origin':list(pos[1:]),'components':components})
    for number,(signature,examples) in enumerate(sorted(triplegroups.items()),1):
        meaningful.append({'key':f'EXACT-THREE-PART-WINDOW-{number:02d}','russianLabel':'Большое витражное окно · нижний конец, рама и верхний вимперг','kind':'SOURCE_CONFIRMED_THREE_PART_WINDOW','confidence':'REPEATED_EXACT_SOURCE_LAYOUT_WITH_TINY_BASE_COMPONENT','occurrences':len(examples),'sourceExamples':[{'dimension':e['dimension'],'origin':e['origin']} for e in examples[:3]],'components':examples[0]['components'],'boundaryReason':'Complementaryauthoredwindow_end_6,window_bottom_4,window_top_2at+0,+2,+5Yintheactualoriginalmap; noinventedmissingfiller.'})
    # Add exact registered existing whole constructions as references. Components
    # are one existing item; source parts remain intact in the registered type.
    wholeseen=set()
    for c in resolver.whole:
        if c['open'] or c['registryId'] in wholeseen:continue
        t=resolver.target(c,0);wholeseen.add(c['registryId'])
        meaningful.append({'key':'REGISTERED-WHOLE-'+t['mainId']+'-V'+t['properties']['variant'],'russianLabel':resolver.names.get(t['registryId'],c['name']),'kind':'EXISTING_REGISTERED_WHOLE_SOURCE_REFERENCE','confidence':'SOURCE_AUTHORED_WHOLE_ALREADY_IMPORTED','occurrences':None,'sourceExamples':[],'components':[{'number':1,'relativePosition':[0,0,0],**t,'sourceApplications':c['applications']}],'registrationDecision':'Already one existing registered object, no new merge/item/state change.'})
    retirements=json.loads((ROOT/'docs/v12.5-city-review/style-retirement-confirmed.json').read_text(encoding='utf-8-sig'));retired={r['registryId'] for r in retirements['retirements']}
    kept=[]
    for r in meaningful:
        if any(c['registryId'] in retired for c in r['components']):doubtful.append({**r,'galleryDisposition':'EXPLICIT_STYLE_RETIRED_COMPONENT_PREVIEW_OMITTED','boundaryReason':'Currentstyle-retirementmetadataexcludesanofferedcomponent; sourcedatalayoutisretainedbutnoitem/objectrestored.'})
        else:kept.append(r)
    meaningful=kept
    out={'schemaVersion':1,'status':'MEANINGFUL_SOURCE_CONSTRUCTION_PREVIEW_READY_NATIVE_REVIEW_PENDING','baselineCommit':BASELINE,'mapSha256':sha(MAP),'packSha256':sha(PACK),'styleRetirementSha256':sha(ROOT/'docs/v12.5-city-review/style-retirement-confirmed.json'),'platformCount':len(meaningful),'platforms':meaningful,'reportOnlyAdjacencyCount':len(doubtful),'galleryIntegration':data['galleryIntegration'],'scope':'All highconfidence requested window stacks and plausible complementary source window/door/column/ladder/decor roles retained; generic independent adjacency stays in report. Models, registrations, originalV12 and behavior are not changed.'}
    dump(DEST/'gallery-preview-meaningful-assemblies.json',out);dump(DEST/'report-only-ambiguous-adjacency.json',{'status':'SOURCE_ADJACENCY_RETAINED_NOT_LOGICAL_OBJECTS','rows':doubtful,'count':len(doubtful)})
    review=json.loads((DOORS/'source-visible-door-review.json').read_text());platforms=[];seen=set()
    for row in review['candidateModels']:
        m=row['sourceModel'];targets=[t for t in row['canonicalTargets'] if t.get('registryId')]
        direct=resolver.resolve([{'model':m}])
        if direct['resolution'].startswith('EXACT'):targets.append(direct)
        for c in resolver.whole:
            if not c['open'] and any(a['model']==m for a in c['applications']):targets.append(resolver.target(c,0))
        if m.startswith('minecraft:block/aca_door_'):targets.append({'registryId':'bloodborne_dw:prototype_double_door','mainId':'90001','properties':{'variant':'0','rotation':'0','profile':'base','open':'false'},'mountY':0.0,'sourceShift':[0.0,0.0,0.0],'payload':{},'resolution':'PROTECTED_V12_EXISTING_DEFAULT_REFERENCE_ONLY'})
        for t in targets:
            if t['registryId'] in retired:continue
            if t['registryId'] in seen:continue
            seen.add(t['registryId']);reg=t['registryId'];name=resolver.names.get(reg,'Предполагаемая дверь/ворота')
            if t.get('sourceOldRegistryId'):
                spec=resolver.baseline['descriptors'][t['sourceOldRegistryId'].split(':',1)[1]];apps=spec['variants'][t['sourceOldVariant']].get('sourceClosedApplications',[])
            else:apps=[]
            platform={'key':'DOOR-REVIEW-'+t['mainId'],'russianLabel':name+' · просмотр двери/ворот','kind':'SOURCE_VISIBLE_DOOR_GATE_OR_CONTEXT_REVIEW','confidence':'ACTUAL_SOURCE_PROJECTION_NOMINATION_FUNCTION_NOT_DECIDED','sourceReviewNumber':row['reviewNumber'],'sourcePixelObservation':row['russianObservation'],'sourceExamples':row['sourceExamples'],'occurrences':row['actualMapApplicationCount'],'sourceUnusedFinished':row['actualMapApplicationCount']==0,'components':[{'number':1,'relativePosition':[0,0,0],**t,'sourceApplications':apps}],'preserveExistingOpenability':True,'originalV12ReferenceOnly':t['mainId']=='90001'};platforms.append(platform)
    # Multi-part suspected door/arch compositions are the same component schema.
    for row in meaningful:
        if tuple(c['mainId'] for c in row['components']) in {('90458','90923'),('90143','90142'),('90031','90623')}:
            platforms.append({**row,'key':'DOOR-'+row['key'],'kind':'SUSPECTED_DOOR_SOURCE_ASSEMBLY_PREVIEW','preserveExistingOpenability':True})
    dump(DOORS/'gallery-door-preview-platforms.json',{'schemaVersion':1,'status':'DOOR_REVIEW_PLATFORMS_READY_EXISTING_FUNCTION_UNCHANGED','baselineCommit':BASELINE,'mapSha256':sha(MAP),'packSha256':sha(PACK),'platformCount':len(platforms),'platforms':platforms,'galleryIntegration':data['galleryIntegration'],'scope':'Existing canonical IDs only, one source-backed reference pertype plus separately labelled door composition hypotheses. No openability/property/model changes. Source-unused90266 included; original90001copied for review only.'})

if __name__=='__main__':main()
