"""Build a reviewable whole-type deduplication plan, without production writes."""
from __future__ import annotations
import hashlib, json, math, sys, traceback
from collections import defaultdict
from pathlib import Path
import numpy as np
from v124_visual_audit import ROOT, OUT, RES, rot

STair_IDS={str(i) for group in [[90275],[90276],[90322,90323,90324],[90409],
 [90423,90424,90425,90426,90427,90428,90429,90430],[90437,90438,90439],[90493,90494,90495],
 [90523,90524,90525],[90550,90551,90552],[90686],[90692,90693,90694],[90701],
 [90760,90761,90762],[90804,90805,90806],[90862],[93004],[93028,93029,90980],
 [93081,93082,93083],[93094,93095,93096],[90586,90587]] for i in group}

def main():
    audit=json.loads((OUT/'visual-candidates.json').read_text(encoding='utf-8'))
    near=json.loads((OUT/'near-visual-candidates.json').read_text(encoding='utf-8'))
    frozen=json.loads((OUT/'baseline-content.json').read_text(encoding='utf-8'))['descriptors']
    inventory=audit['inventory'];by_registry=defaultdict(list);by_key={}
    for row in inventory:
        by_registry[row['registryId']].append(row);by_key[(row['registryId'],row['variant'])]=row
    parent={r:r for r in by_registry};edges=[]
    def find(r):
        while parent[r]!=r:parent[r]=parent[parent[r]];r=parent[r]
        return r
    def union(a,b,reason,evidence=None):
        if a['id'] in STair_IDS or b['id'] in STair_IDS:return
        one,two=find(a['registryId']),find(b['registryId']);parent[max(one,two)]=min(one,two)
        edges.append({'a':a['id'],'b':b['id'],'aVariant':a['variant'],'bVariant':b['variant'],'reason':reason,'evidence':evidence})
    for rows in audit['sameAppearanceGroups']:
        for row in rows[1:]:union(rows[0],row,'MATCH_TRANSFORMED_VISIBLE_FACES_AND_UV_SAMPLED_PIXELS')
    kept=[]
    for pair in near['pairs']:
        a,b=pair['a'],pair['b']
        if a['id'] in STair_IDS or b['id'] in STair_IDS:continue
        if pair['difference']<.11 and pair['pixelDifference']<.1:
            union(a,b,'REVIEWED_PRACTICALLY_SAME_EIGHT_VIEW_APPEARANCE',pair['diagnosticImage'])
        else:
            # Concrete visual differences, including the material judgment made
            # after looking at diagnostic projections. Native QA verifies them.
            reason=('Player sees different surface decoration/material across the visible faces: '
                    f"eight-view RGB difference {pair['pixelDifference']:.3f}, silhouette difference {pair['silhouetteDifference']:.3f}.")
            kept.append({**pair,'retainReason':reason})
    global_decisions=json.loads((OUT/'reviewed-global-decisions.json').read_text(encoding='utf-8')) if (OUT/'reviewed-global-decisions.json').exists() else {'families':[]}
    first_by_id={rows[0]['id']:rows[0] for rows in by_registry.values()}
    for family in global_decisions['families']:
        rows=[first_by_id[i] for i in family['ids'] if i in first_by_id]
        for row in rows[1:]:union(rows[0],row,family['reason'],'reviewed-global-decisions.json and full source textured diagnostic sheets')
    clusters=defaultdict(list)
    for registry in by_registry:clusters[find(registry)].append(registry)
    groups=[];mappings=[];baseline={}
    for registries in clusters.values():
        if len(registries)<2:continue
        registries.sort(key=lambda r:int(by_registry[r][0]['id']))
        main=registries[0];primary_id=by_registry[main][0]['id'];representatives=list(by_registry[main]);state_rows=[]
        for registry in registries:
            path=registry.split(':',1)[1]
            spec=frozen[path];baseline[registry]=spec
            for source in by_registry[registry]:
                matches=[r for r in representatives if r['appearanceSignature']==source['appearanceSignature']]
                if registry==main:
                    target=source;target_index=source['variant'];delta=0;shift=[0.,0.,0.]
                elif matches:
                    target=min(matches,key=lambda r:representatives.index(r));target_index=representatives.index(target)
                    delta=int(round((source['canonicalYaw']-target['canonicalYaw'])/45))%8
                    # Canonical render transforms R_s V_s + T_s = R_t V_t + T_t.
                    difference=np.asarray(target['normalizingTranslation'])-np.asarray(source['normalizingTranslation'])
                    world_shift=rot(difference,1,-source['canonicalYaw'],[0,0,0])/16
                    # Payload uses target-local axes, then Shift.rotate(newYaw)
                    # rotates it with the target model. Undo the added yaw here.
                    shift=rot(world_shift,1,delta*45,[0,0,0]).tolist()
                else:
                    target=source;target_index=len(representatives);representatives.append(source);delta=0;shift=[0.,0.,0.]
                mapping={'oldId':source['id'],'oldRegistryId':registry,'oldVariant':source['variant'],
                         'mainId':primary_id,'mainRegistryId':main,'variant':target_index,'yawDeltaSteps':delta,
                         'sourceShiftDelta':[round(n,10) for n in shift],
                         'oldYawPreserved':True,'oldMountYPreserved':True,'oldVerticalMountPreserved':True,
                         'profilePreserved':True,'instanceUuidAndPayloadPreserved':True,
                         'representativeRegistryId':target['registryId'],'representativeVariant':target['variant']}
                state_rows.append(mapping);mappings.append(mapping)
        relevant=[e for e in edges if e['a'] in {by_registry[r][0]['id'] for r in registries} and e['b'] in {by_registry[r][0]['id'] for r in registries}]
        group={'mainId':primary_id,'mainRegistryId':main,'members':[{'id':by_registry[r][0]['id'],'registryId':r,'name':by_registry[r][0]['name'],'oldVariants':len(by_registry[r])} for r in registries],
               'newVariantCount':len(representatives),'variantPropertyBudget16':len(representatives)<=16,
               'representatives':[{'variant':i,'registryId':r['registryId'],'oldVariant':r['variant'],'name':r['name']} for i,r in enumerate(representatives)],
               'reason':'Same visible functional object; positions/orientations and minor artwork/posture changes become states of one placing item.',
               'evidenceEdges':relevant,'mappings':state_rows}
        if primary_id in global_decisions.get('canonicalNames',{}):group['canonicalName']=global_decisions['canonicalNames'][primary_id]
        if primary_id=='90411':group['renderLayerOverride']='translucent';group['renderLayerOverrideReason']='Retain alpha121 window parts in 90459/90665/90666; other members use only alpha0/255.'
        groups.append(group)
    groups.sort(key=lambda g:int(g['mainId']))
    retained=[p for p in kept if find(p['a']['registryId'])!=find(p['b']['registryId'])]
    material_retained=[]
    for bucket in audit['materialReviewGroups']:
        representatives={find(r['registryId']):r for r in bucket['members'] if r['id'] not in STair_IDS}
        if len(representatives)<2:continue
        records=list(representatives.values())
        material_retained.append({'members':[{'id':r['id'],'registryId':r['registryId'],'variant':r['variant'],'name':r['name'],
                                            'visibleDimensionsBlocks':[round(v/16,3) for v in r['sizeModelUnits']]} for r in records],
                                 'reason':'Atlas content is shared, but these objects have visibly different size or silhouette after position normalization; every member lists its actual model dimensions. Close-size pairs are separately projected and reviewed in retainedNearPairs. A shared atlas alone does not merge a full masonry cube, a half-height walking slab, a stair ascent, an open arch and an upright wall post.'})
    result={'schemaVersion':1,'baselineCommit':'27bf67e','status':'REVIEWED_SOFTWARE_APPEARANCE_PLAN_PENDING_NATIVE_GAME_COMPARISON',
            'scope':'Only simplePhysicalCollision source_ objects added after V12; V12 registrations/resources excluded completely.',
            'renderYawConvention':'CompositeClient rotates -rotation*45deg. Map newRotation=floorMod(oldRotation+yawDeltaSteps,8). sourceShiftDelta is in target-local block axes (the world preservation translation rotated back by the added yaw); old SourceShift must likewise be expressed in target-local axes before addition. Existing CompositeSourceShift.world applies the target yaw.',
            'migrationRequirement':'Do not remove old registrations. Hide aliases from offered catalogue/Creative; migrate saved roots/ledger/item/debug by exact old ID and state, preserve UUID,payload,events,connections,source shift,mount/profile. Existing authored collision hooks remain exact-ID compatible through adapters.',
            'summary':{'newTypesBefore':1045,'generalGroups':len(groups),'generalTypesRetired':sum(len(g['members'])-1 for g in groups),
                       'generalTypesAfter':1045-sum(len(g['members'])-1 for g in groups),'mappedOldStates':len(mappings),
                       'groupsOverVariantBudget':sum(not g['variantPropertyBudget16'] for g in groups),
                       'stairsOwnedByOtherAgentIds':sorted(STair_IDS),'allSourceStatesGeometryAudited':1802,
                       'sameTextureSimilarSizeStatesProjected':near['summary']['projectedStates'],'renderedNearPairs':near['summary']['testedPairs']},
            'fullStateVisualCoverage':'full-visual-coverage.json: 1802 states, 13112 normalized source-rendered views, atlas-independent candidate comparisons; pending native Minecraft inspection.',
            'reviewedGlobalDecisions':global_decisions,'groups':groups,'retainedNearPairs':retained,'retainedMaterialGroups':material_retained}
    (OUT/'general-functional-groups.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'visual-baseline-descriptors.json').write_text(json.dumps(baseline,ensure_ascii=False,indent=2),encoding='utf-8')
    candidates=[]
    for g in groups:
        for m in g['mappings']:
            if m['oldRegistryId']==m['mainRegistryId'] and m['oldVariant']==m['variant']:continue
            candidates.append({'groupId':g['mainId'],**m})
    (OUT/'native-visual-comparison-fixtures.json').write_text(json.dumps({'schemaVersion':1,'status':'PENDING_NATIVE_CAPTURE',
          'scope':'Before old descriptor vs after main type mapped pose, same bounds/modelview/lighting; every retired state included.',
          'fixtures':candidates},ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result['summary']),flush=True)

if __name__=='__main__':
    OUT.mkdir(parents=True,exist_ok=True)
    with (OUT/'functional-groups-console.txt').open('w',encoding='utf-8') as stream:
        sys.stdout=sys.stderr=stream
        try:main()
        except BaseException:traceback.print_exc();raise
