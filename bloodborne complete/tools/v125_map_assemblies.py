"""Readonly finite construction candidates, including tiny original helpers.

Exports supported layouts for a review gallery; never merges types/models or
declares adjacency alone to be a finished authored object.
"""
from __future__ import annotations
import collections, json, math, sqlite3, time
from pathlib import Path
from v125_map_index import ROOT,MAP,PACK,CACHE,OUT,BASELINE,dump,sha,token,transformed_corners
from v125_map_resolve import Resolver,rotation

DEST=ROOT/'docs/v12.5-city-review/assemblies'
DOORS=ROOT/'docs/v12.5-city-review/doors'

def bounds(apps,models):
    pts=[p for a in apps for e in models.get(a['model'],{}).get('resolved',{}).get('elements',[]) if e.get('faces') for p in transformed_corners(e,a)]
    return [round(min(p[i] for p in pts)/16,8) for i in range(3)]+[round(max(p[i] for p in pts)/16,8) for i in range(3)] if pts else None
def category(apps,info,target):
    text=' '.join(a['model'] for a in apps).lower();b=info['bounds']
    if not b:return 'empty'
    if info['multipart']:return 'connected_context'
    if 'ImportedStairLegacyArt' in target.get('payload',{}):return 'functional_stair_context'
    size=[b[i+3]-b[i] for i in range(3)]
    # File labels are hints only. Every row records actual geometry / textures
    # and source map placement; no logical boundary comes from a carrier name.
    for label,words in [('window',('window','warped_trapdoor','wimperge')),('door_gate',('bir_door','aca_door','dark_door','addon/door','addon/gate','oak_door','iron_door')),('column',('column','pilaster','pillar','spire','chimney')),('statue',('statue','tombstone','gargoyle','warped_door','darkoak_door')),('lamp',('lamp','lantern','candel','candle')),('tree',('oak_log','spruce_log','birch_log','jungle_log','acacia_log')),('furniture',('wood_obj','bench','book','cases','wooden_box','barrel','bag_'))]:
        if any(w in text for w in words):return label
    if max(size)<=1.125 or min(size)<=.125:return 'tiny_or_thin_helper'
    if size[1]>=1.5 and min(size[0],size[2])<=max(size)*.25:return 'tall_panel_candidate'
    return 'finite_other'

def main():
    DEST.mkdir(parents=True,exist_ok=True);DOORS.mkdir(parents=True,exist_ok=True)
    index=json.loads((OUT/'original-map-index.json').read_text());assert index['status']=='PASS'
    con=sqlite3.connect(f'file:{CACHE}?mode=ro',uri=True);resolver=Resolver();inventory=json.loads((ROOT/'docs/remaining-transfer/source-inventory.json').read_text(encoding='utf-8-sig'));models={m['model']:m for m in inventory['models']}
    appsinfo={};allstates={i:(s,n,json.loads(p)) for i,s,n,p in con.execute('SELECT id,state,name,properties FROM states')}
    for aid,tok,finite,multipart in con.execute('SELECT id,apps,finite,multipart FROM applications'):
        apps=json.loads(tok);target=resolver.resolve(apps);b=bounds(apps,models)
        row={'id':aid,'sourceApplications':apps,'bounds':b,'multipart':bool(multipart),'target':target};row['category']=category(apps,row,target);appsinfo[aid]=row
    frequencies=dict(con.execute('SELECT applications,count(*) FROM cells GROUP BY applications'))
    samplebyapp={};statecounts=collections.Counter()
    for aid in appsinfo:
        row=con.execute('SELECT dimension,x,y,z,state FROM cells WHERE applications=? LIMIT1'.replace('LIMIT1','LIMIT 1'),(aid,)).fetchone()
        if row:samplebyapp[aid]={'dimension':row[0],'position':list(row[1:4]),'sourceState':allstates[row[4]][0]}
    dump(DEST/'finite-application-inventory.json',{'schemaVersion':1,'status':'PASS_READONLY_INVENTORY','baselineCommit':BASELINE,'mapSha256':sha(MAP),'packSha256':sha(PACK),'applicationCombinations':[dict(v,occurrences=frequencies.get(k,0),sample=samplebyapp.get(k)) for k,v in appsinfo.items()],'classificationPolicy':'Source-name hints nominate category only; no merge/openability. Finite geometry includes tiny helpers. Connected background and functional stairs remain inventoried separately.'})
    # Exhaustive same-cell multipart is not an object boundary across cells.
    # Every finite pattern is inventoried; bounded joins propose constructions.
    joinable={k for k,r in appsinfo.items() if r['category'] not in {'empty','connected_context','functional_stair_context'}}
    interesting={k for k in joinable if appsinfo[k]['category'] not in {'finite_other','tiny_or_thin_helper'} or appsinfo[k]['target'].get('mainId') in {'93066','93069','93116','93117'}}
    helpers=joinable-interesting
    positions={}
    for dim,x,y,z,s,a in con.execute('SELECT dimension,x,y,z,state,applications FROM cells'):
        if a in joinable:positions[(dim,x,y,z)]=(s,a)
    pairs=collections.Counter();examples={};touching=collections.Counter();paircats={};start=time.monotonic()
    offsets=[(0,d,0) for d in range(1,7)]+[(d,0,0) for d in range(1,5)]+[(0,0,d) for d in range(1,5)]
    for pos,(sid,aid) in positions.items():
        if aid not in interesting:continue
        ar=appsinfo[aid];b=ar['bounds']
        for dx,dy,dz in offsets:
            other=(pos[0],pos[1]+dx,pos[2]+dy,pos[3]+dz);row=positions.get(other)
            if row is None:continue
            os,oa=row;br=appsinfo[oa]
            if oa==aid:continue
            same=ar['category']==br['category'];helper=oa in helpers
            if not(same or helper or {ar['category'],br['category']}<={'window','door_gate','tall_panel_candidate','column'}):continue
            q=br['bounds'];gap=[max(b[i]-(q[i+3]+[dx,dy,dz][i]),q[i]+[dx,dy,dz][i]-b[i+3],0) for i in range(3)]
            # Real envelopes must meet/overlap along all axes. A half-block
            # tolerance captures authored trim joints without joining furniture
            # across streets. Every edge remains a review proposal, not authority.
            near=max(gap)<=.5
            key=(aid,oa,dx,dy,dz);pairs[key]+=1;paircats[key]=(same,helper)
            if near:touching[key]+=1
            ex=examples.setdefault(key,[])
            if len(ex)<3:ex.append({'dimension':pos[0],'first':list(pos[1:]),'second':list(other[1:]),'firstSourceState':allstates[sid][0],'secondSourceState':allstates[os][0],'envelopeGapBlocks':gap,'envelopesWithinHalfBlock':near})
    evidence=[];preview=[];known=[('93117','93116','Готическое окно: нижняя часть93117 + верхняя93116'),('93066','93069','Составная оконная деталь: нижняя93066 + верхняя93069')]
    for key,count in pairs.most_common():
        aid,oa,dx,dy,dz=key;a,b=appsinfo[aid],appsinfo[oa];ta,tb=a['target'],b['target'];same,helper=paircats[key]
        supported=touching[key]>0 and count>=2
        explicit=any(ta.get('mainId')==x and tb.get('mainId')==y and dy>0 for x,y,_ in known)
        row={'candidateKey':f'{aid}:{oa}:{dx},{dy},{dz}','firstApplications':aid,'secondApplications':oa,'relativeCellOffset':[dx,dy,dz],'occurrences':count,'envelopeTouchOccurrences':touching[key],'examples':examples[key],'categories':[a['category'],b['category']],'tinyHelperIncluded':helper,'status':'USER_SPECIFIED_PAIR_SOURCE_OBSERVED' if explicit else 'REPEATED_GEOMETRIC_JOIN_CANDIDATE' if supported else 'ADJACENCY_ONLY_NOT_CONFIRMED_OBJECT','confidence':'USER_SPECIFIED_PLUS_SOURCE_LAYOUT' if explicit else 'SOURCE_REPEATED_GEOMETRIC_JOIN_REVIEW_REQUIRED' if supported else 'LOW_ADJACENCY_ONLY'}
        evidence.append(row)
        if not(supported or explicit) or not all(t['resolution'].startswith('EXACT') for t in [ta,tb]):continue
        # Compact gallery proposals deduplicate orientation/application aliases by
        # canonical typed art state plus exact relative placement; no registry merge.
        label=next((label for x,y,label in known if ta.get('mainId')==x and tb.get('mainId')==y and dy>0), f'Составная конструкция · {ta.get("mainId","")} + {tb.get("mainId","")}')
        components=[]
        for number,(info,t,rel) in enumerate([(a,ta,[0,0,0]),(b,tb,[dx,dy,dz])],1):
            components.append({'number':number,'relativePosition':rel,**t,'sourceApplications':info['sourceApplications'],'sourceVisualBoundsLocal':info['bounds']})
        preview.append({'key':row['candidateKey'],'russianLabel':label,'kind':'COMPOSITION_PREVIEW_ONLY','status':'READY_FOR_SOURCE_LAYOUT_PREVIEW','confidence':row['confidence'],'occurrences':count,'touchOccurrences':touching[key],'sourceExamples':examples[key],'components':components,'wholeItemDecision':'NOT_MADE','minimumSpacingBetweenPlatforms':2,'platformRule':'Union of actual component visual envelopes + two block margin; preserve relative source cell positions. One vertical translation for whole assembly permitted to seat its lowest visual point; no per-part reseating.'})
    # The named pairs must be investigated even if no envelope touch or source
    # occurrence was found; unsupported requested combinations are explicit.
    requested=[]
    for lower,upper,label in known:
        rows=[r for r in evidence if appsinfo[r['firstApplications']]['target'].get('mainId')==lower and appsinfo[r['secondApplications']]['target'].get('mainId')==upper and r['relativeCellOffset'][1]>0]
        requested.append({'lowerId':lower,'upperId':upper,'russianLabel':label,'status':'SOURCE_PAIR_FOUND' if rows else 'NO_PAIR_WITHIN_VERTICAL_SCAN6_BLOCKS','occurrences':sum(r['occurrences'] for r in rows),'candidates':rows})
    dump(DEST/'adjacency-evidence.json',{'schemaVersion':1,'status':'PASS_SOURCE_SCAN_REVIEW_REQUIRED','baselineCommit':BASELINE,'mapSha256':sha(MAP),'packSha256':sha(PACK),'finiteCellsConsidered':len(positions),'allFiniteApplicationCombinations':len(appsinfo),'joinableCombinations':len(joinable),'candidateOffsets':offsets,'boundsToleranceBlocks':.5,'noClaimOfSemanticExhaustiveness':'Every finite cell/combination is inventoried, but map adjacency does not uniquely encode authored logical-object boundaries. Zero-observation/long-distance/diagonal/connected structural compositions require manual source review; none are silently declared nonexistent.','requestedPairs':requested,'edges':evidence,'elapsedSeconds':round(time.monotonic()-start,2)})
    # One example per canonical combination/relative source layout, keep every
    # unique assembly as data; platforms can paginate the candidate list.
    compact={}
    for r in preview:
        sig=token([{k:c[k] for k in ['registryId','properties','relativePosition','sourceShift','mountY','payload']} for c in r['components']])
        compact.setdefault(sig,r)
    previews=list(compact.values());previews.sort(key=lambda r:(not r['confidence'].startswith('USER'),-r['touchOccurrences'],-r['occurrences'],r['key']))
    dump(DEST/'gallery-preview-assemblies.json',{'schemaVersion':1,'status':'SOURCE_BACKED_REVIEW_PREVIEWS_NOT_APPROVED_MERGES','baselineCommit':BASELINE,'mapSha256':sha(MAP),'packSha256':sha(PACK),'canonicalMappingSha256':sha(ROOT/'docs/v12.4-dedup/type-state-mapping.json'),'previewCount':len(previews),'assemblies':previews,'requestedPairStatus':requested,'scope':'Preview composition only; all registrations/models/originalV12 remain unchanged.'})
    # All visible tall/flat authored sections and all door-labelled source models
    # are nominated. Names alone never prove that a sculpture is a door.
    doorrows=[]
    for m,row in models.items():
        b=index['models'].get(m,{}).get('bounds');text=m.lower()
        if not b:continue
        size=[b[i+3]-b[i] for i in range(3)];namehint=any(w in text for w in ['door','gate'])
        flat=size[1]>=1.5 and min(size[0],size[2])<=max(size[0],size[2])*.28 and max(size[0],size[2])>=.5
        if not(namehint or flat):continue
        approws=[r for r in appsinfo.values() if any(a['model']==m for a in r['sourceApplications'])]
        targets={token(r['target']):r['target'] for r in approws};mapcount=sum(frequencies.get(r['id'],0) for r in approws)
        visible_reason='Tall thin visible panel, potentially a leaf/door or window/facade; source texture/model review required' if flat else 'Door/gate-labelled source mesh nominated, label does not imply visible function'
        classification='PANEL_DOOR_WINDOW_REVIEW_REQUIRED'
        if any(w in text for w in ['darkoak_door','warped_door']):classification='SOURCE_SCULPTURE_NOT_DOOR_CONFIRMED_PRIOR_IMPORT'
        if any(w in text for w in ['window','glass','trapdoor','wimperge']):classification='WINDOW_TRIM_OR_PANEL_REVIEW_REQUIRED'
        if any(w in text for w in ['bir_door','aca_door','dark_door','addon/gate_','addon/oak_door','addon/iron_door','block/door_']):classification='DOOR_OR_GATE_CANDIDATE_VISIBLE_AUTHORED_LEAF'
        doorrows.append({'sourceModel':m,'sourceSha256':row['sha256'],'sourceVisibleBounds':b,'dimensionsBlocks':size,'sourceTextures':row.get('textures',[]),'sourceElements':len(row['resolved'].get('elements',[])),'classification':classification,'visibleCandidateReason':visible_reason,'actualMapApplicationCount':mapcount,'sourceExamples':[samplebyapp[r['id']] for r in approws[:3] if r['id'] in samplebyapp],'canonicalTargets':list(targets.values()),'knownWholeObjects':[{'registryId':c['registryId'],'mainId':resolver.ids.get(c['registryId']),'variant':c['variant'],'openPoseAuthored':c['open']} for c in resolver.whole if any(a['model']==m for a in c['applications'])],'openabilityDecision':'NOT_CHANGED_NOT_INFERRED','nativeVisualReview':'NOT_RUN_SOURCE_GEOMETRY_AND_MAP_EVIDENCE_ONLY'})
    dump(DOORS/'all-door-candidates.json',{'schemaVersion':1,'status':'COMPLETE_SOURCE_NOMINATION_REQUIRES_PLAYER_VISIBLE_REVIEW','baselineCommit':BASELINE,'mapSha256':sha(MAP),'packSha256':sha(PACK),'sourceNominationCoverage':'All source inventory models with door/gate hint OR tall thin visible envelope; carrier door name is not enough. Includes source-unused candidates and sculpture exclusions.','candidates':doorrows})

if __name__=='__main__':
    try:main()
    except BaseException:
        import traceback
        DEST.mkdir(parents=True,exist_ok=True);(DEST/'assembly-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
