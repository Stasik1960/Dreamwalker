"""Exact current source applications, never heuristic material/name matching."""
from __future__ import annotations
import collections,copy,json,math,re
from pathlib import Path
from v125_map_index import ROOT,token,app_clean
from v125_map_resolve import Resolver,rotation

RES=ROOT/'src/architecture/resources'
class CurrentResolver(Resolver):
    def match(self,expected,actual):
        # Multipart applications are a multiset. Sorting by the unrotated yaw
        # and zipping is incorrect when equal models exchange order on rotation.
        if len(expected)!=len(actual):return None
        def sig(a,delta=0):
            return (self.model(a['model']),a.get('x',0),(a.get('y',0)+delta*45)%360,bool(a.get('uvlock',False)))
        wanted=collections.Counter(sig(a) for a in actual)
        for delta in range(8):
            if delta and any(a.get('uvlock') for a in expected):continue
            if collections.Counter(sig(a,delta) for a in expected)==wanted:return delta
        return None
    def __init__(self):
        super().__init__()
        # Keep frozen source -> current aliases, adding only current append-only
        # source poses. Prefix variants remain exact frozen source candidates.
        self.retired={r['registryId'] for r in json.loads((RES/'bloodborne_dw/imported-style-retirements.json').read_text(encoding='utf-8-sig'))['retirements']}
        self.transparent={r['model'] for r in json.loads((ROOT/'docs/remaining-transfer/transparent-helper-validation.json').read_text(encoding='utf-8-sig'))['models'] if r['status']=='ALL_VISIBLE_FACE_PIXELS_ALPHA_ZERO'}
        inventory=json.loads((ROOT/'docs/remaining-transfer/source-inventory.json').read_text(encoding='utf-8-sig'))
        self.empty={r['model'] for r in inventory['models'] if not any(e.get('faces') for e in r['resolved'].get('elements',[]))}
        self.current={}
        self.carriers={}
        bankmeta=json.loads((RES/'bloodborne_dw/imported-full-map-v127.json').read_text(encoding='utf-8-sig'))
        self.banked={g['registryId'] for g in bankmeta.get('groups',[]) if g.get('bankMax',0)>0}
        self.banked.update(g['primaryRegistryId'] for g in json.loads((RES/'bloodborne_dw/imported-type-aliases.json').read_text(encoding='utf-8-sig'))['groups'] if g.get('kind')=='artwork' and g.get('poseCount',0)>16)
        follow=RES/'bloodborne_dw/imported-type-aliases-v127.json'
        self.follow={(r['oldRegistryId'],r['oldVariant']):r for r in json.loads(follow.read_text(encoding='utf-8-sig')).get('mappings',[])} if follow.exists() else {}
        for path in sorted((RES/'bloodborne_dw/composite').glob('*.json')):
            spec=json.loads(path.read_text(encoding='utf-8-sig'));reg=spec['id'];self.current[reg]=spec
            carrier=spec.get('source',{}).get('carrier')
            if carrier and not spec['source'].get('profileModel'):
                mapping=self.maps.get((reg,0));canonical=mapping['newRegistryId'] if mapping else reg
                follow=self.follow.get((canonical,mapping['newVariant'] if mapping else 0))
                self.carriers[carrier]=follow['newRegistryId'] if follow else canonical
            frozen=len(self.baseline['descriptors'].get(path.stem,{}).get('variants',[]))
            for vi,v in enumerate(spec['variants']):
                if vi<frozen:continue
                apps=v.get('sourceClosedApplications',[])
                if not apps:continue
                c={'registryId':reg,'variant':vi,'open':False,'applications':apps,'sourcePlacementOffset':v.get('sourcePlacementOffset',[0,0,0]),'name':spec['displayName'],'currentAppend':True}
                if len(apps)>1 and any(a.get('offset') for a in apps):self.whole.append(c)
                else:self.candidates[tuple(sorted(self.model(a['model']) for a in apps))].append(c)
        # Approved current complete constructions take priority over their old
        # source pieces. Include source OPEN recipes rather than guessing a door
        # state from its vanilla carrier properties.
        self.current_whole=[]
        for reg,spec in self.current.items():
            if not reg.startswith('bloodborne_dw:source_') or reg in self.retired:continue
            if any(old==reg and row['newRegistryId']!=reg for (old,_),row in self.maps.items()):continue
            for vi,v in enumerate(spec['variants']):
                for opened,key in [(False,'sourceClosedApplications'),(True,'sourceOpenApplications')]:
                    apps=v.get(key,[])
                    if len(apps)>1 and any(any(a.get('offset',[0,0,0])) for a in apps):
                        self.current_whole.append({'registryId':reg,'variant':vi,'open':opened,'applications':apps,'sourcePlacementOffset':v.get('sourcePlacementOffset',[0,0,0]),'currentAppend':True})
        special=ROOT/'docs/v12.7/conversion/protected-source-exact-matches.json'
        self.protected={r['sourceApplicationToken']:r['decision'] for r in json.loads(special.read_text(encoding='utf-8-sig'))['rows']} if special.exists() else {}
        if special.exists():
            for row in json.loads(special.read_text(encoding='utf-8-sig'))['rows']:
                if len(row['sourceApplications'])>1 and any(any(a.get('offset',[0,0,0])) for a in row['sourceApplications']):
                    d=row['decision'];self.current_whole.append({'registryId':d['registryId'],'variant':int(d['properties']['variant']),'open':d['properties']['open']=='true','applications':row['sourceApplications'],'protectedTarget':d})
        self.source_defects={token(r['sourceApplications']):r['sourceDefect'] for r in json.loads(special.read_text(encoding='utf-8-sig'))['unmatched'] if r.get('sourceDefect')} if special.exists() else {}
        self.resolve_token.cache_clear()

    def target(self,candidate,yaw):
        if candidate.get('protectedTarget'):
            result=copy.deepcopy(candidate['protectedTarget']);result['properties']['rotation']=str((int(result['properties']['rotation'])+yaw)%8);result['mainId']=self.ids[result['registryId']];return result
        if not candidate.get('currentAppend'):
            result=super().target(candidate,yaw)
            mapping=self.follow.get((result['registryId'],int(result['properties']['variant'])))
            if mapping:
                result['registryId']=mapping['newRegistryId'];result['mainId']=mapping['newId'];result['properties']['variant']=str(mapping['newVariant']);result['properties']['rotation']=str((int(result['properties']['rotation'])+mapping['rotationDelta'])%8)
                inv=rotation(result['sourceShift'],-mapping['rotationDelta']);shift=[round(inv[i]+mapping['sourceShift'][i],10) for i in range(3)];result['sourceShift']=shift
                if any(shift):result['payload']['SourceShift']=shift
                else:result['payload'].pop('SourceShift',None)
            if result['registryId'] in self.banked:
                logical=int(result['properties']['variant'])+16*int(result['properties'].get('art_bank',0));result['properties']['variant']=str(logical%16);result['properties']['art_bank']=str(logical//16)
            return result
        reg=candidate['registryId'];vi=candidate['variant'];shift=[-float(x)/16 for x in candidate['sourcePlacementOffset']]
        state={'variant':str(vi%16),'rotation':str(yaw),'profile':'base','open':str(candidate.get('open',False)).lower()}
        if len(self.current[reg]['variants'])>16:state['art_bank']=str(vi//16)
        payload={'ImportedTypeVersion':1}
        if any(shift):payload['SourceShift']=shift
        return {'registryId':reg,'mainId':self.ids.get(reg,''),'properties':state,'mountY':0.,'sourceShift':shift,'payload':payload,'sourceOldRegistryId':reg,'sourceOldVariant':vi,'sourceOldRotation':yaw,'resolution':'EXACT_CURRENT_APPENDED_SOURCE_APPLICATIONS'}

    def classify(self,apps,carrier,props):
        names={a['model'] for a in apps}
        if not apps:return {'action':'REMOVE','reason':'SOURCE_BLOCKSTATE_NO_APPLICABLE_BRANCH','sourceCarrier':carrier}
        if names<=self.transparent|self.empty:return {'action':'REMOVE','reason':'SOURCE_ALL_PIXEL_ALPHA_ZERO' if names&self.transparent else 'SOURCE_EMPTY_GEOMETRY','models':sorted(names)}
        if carrier=='minecraft:jungle_trapdoor':return {'action':'REMOVE','reason':'USER_REMOVED_DAMAGED_SOURCE_TYPE','models':sorted(names)}
        if carrier=='minecraft:potted_spruce_sapling':return {'action':'REMOVE','reason':'MISSING_AUTHORED_SOURCE_MODEL_OR_TEXTURE','models':sorted(names)}
        if names=={'minecraft:block/acacia_button'}:return {'action':'REMOVE','reason':'USER_REMOVED_VANILLA_MODEL_TEXTURE_FALLBACK','models':sorted(names),'evidence':'docs/v12.7/conversion/nonfinite-source-fallback-proof.json'}
        if names=={'minecraft:block/hold/wood_ladder_02'} and len(apps)==1 and not apps[0].get('x',0):
            yaw=apps[0].get('y',0)%360
            if yaw%90==0:return {'action':'OWNER','registryId':'bloodborne_dw:prototype_ladder','mainId':'90006','properties':{'facing':['north','east','south','west'][yaw//90],'variant':'0','profile':'base','waterlogged':'false','diagonal':'false','source_clone':'false','freestanding':'true'},'payload':{},'sourceShift':[0,0,0],'mountY':0.,'resolution':'EXACT_PROTECTED_NATIVE_SOURCE_LADDER_BLOCKSTATE_MODEL'}
        if token(sorted(apps,key=token)) in self.source_defects:return {'action':'REMOVE','reason':'MISSING_AUTHORED_TEXTURE_REFERENCE','sourceDefect':self.source_defects[token(sorted(apps,key=token))],'models':sorted(names)}
        if any(self.classifications.get(name,{}).get('kind')=='INTENTIONAL_EXCLUSION' for name in names):return {'action':'REMOVE','reason':'PREEXISTING_INTENTIONAL_SOURCE_VARIANT_EXCLUSION','models':sorted(names)}
        if any(name=='minecraft:block/diorite_wall_side_tall' for name in names):return {'action':'REMOVE','reason':'MISSING_AUTHORED_SOURCE_PARENT_TEMPLATE_WALL_TALL','models':sorted(names)}
        if self.carriers.get(carrier) in self.retired:return {'action':'REMOVE','reason':'USER_RETIRED_VANILLA_STYLE_CARRIER','retiredRegistryId':self.carriers[carrier],'models':sorted(names)}
        exact=self.protected.get(token(sorted(apps,key=token)))
        if exact:return {**copy.deepcopy(exact),'mainId':self.ids.get(exact['registryId'],'')}
        result=self.resolve(apps)
        if result['resolution'].startswith('EXACT'):
            if result['registryId'] in self.retired:return {'action':'REMOVE','reason':'USER_RETIRED_VANILLA_STYLE_TYPE','retiredRegistryId':result['registryId'],'models':sorted(names)}
            if result['registryId'] not in self.current and not result['registryId'].startswith(('bloodborne_dw:prototype_wall','bloodborne_dw:prototype_ladder')):return {'action':'REMOVE','reason':'EXCLUDED_UNREGISTERED_SOURCE_TYPE','excludedRegistryId':result['registryId'],'models':sorted(names)}
            return {'action':'OWNER',**result}
        if carrier=='minecraft:polished_deepslate_wall':
            mats={int(a['model'].rsplit('_',1)[1]) for a in apps if '_side_tall_' in a['model']};material=next(iter(mats),6)
            if len(mats)>1:return {'action':'REMOVE','reason':'SOURCE_WALL_MIXED_MATERIALS_NO_EXISTING_STATE','models':sorted(names)}
            if material in {1,2,3,4,5,7}:return {'action':'REMOVE','reason':'PROTECTED_V12_REMOVED_OR_GEOMETRY_INCOMPATIBLE_WALL_STATE','sourceMaterial':material,'models':sorted(names)}
            reg='bloodborne_dw:prototype_wall_skin_0' if material==0 else 'bloodborne_dw:prototype_wall'
            state={**props,'rotation':'0','profile':'base','connections':'manual'}
            if material:state['material']=str(material)
            return {'action':'OWNER','registryId':reg,'mainId':self.ids.get(reg,''),'properties':state,'payload':{},'mountY':0.,'sourceShift':[0.,0.,0.],'resolution':'EXACT_EXISTING_V12_NATIVE_WALL_STATE'}
        # Some source-authored complete multi-cell objects are resolved in the
        # second pass by explicit known whole recipes; never guess adjacency.
        for candidate in self.whole:
            if candidate.get('open'):continue
            first=[a for a in candidate['applications'] if not any(a.get('offset',[0,0,0]))]
            if self.match(first,apps) is not None:return {'action':'WHOLE_CANDIDATE','reason':'EXACT_MULTI_CELL_PRIMARY_APPS','models':sorted(names)}
        return {'action':'UNRESOLVED_VISIBLE','reason':'NO_EXACT_CURRENT_SOURCE_STATE','sourceCarrier':carrier,'models':sorted(names),'sourceApplications':apps}

def pose_proposals(resolver,unresolved):
    """Source-supported conditional unions to append only during future transfer.
    Existing descriptors/art never modified by dry-run. Missing resources and
    native pose-cap issues are concrete blockers, not invisible substitutions.
    """
    def normalized(apps):
        options=[]
        for steps in ([0] if any(a.get('uvlock') for a in apps) else [0,2,4,6]):
            values=[]
            for a in apps:
                c=app_clean(a);y=(c.get('y',0)-steps*45)%360
                if y:c['y']=y
                else:c.pop('y',None)
                values.append(c)
            values.sort(key=token);options.append((token(values),steps,values))
        return min(options,key=lambda x:x[0])
    def parts(apps,offset):
        result=[];missing=[]
        for a in apps:
            ns,local=a['model'].split(':',1);base=f'remaining/source/{ns}/{local}'
            if a.get('uvlock') and (a.get('x',0) or a.get('y',0)):base=f'remaining/source_uvlock/{ns}/{local}_x{a.get("x",0)}_y{a.get("y",0)}'
            model='bloodborne_dw:'+base;file=RES/'assets/bloodborne_dw/models'/f'{base}.json'
            if not file.exists():missing.append(str(file.relative_to(RES)))
            result.append({'model':model,'offset':offset,'yaw':a.get('y',0),'pitch':a.get('x',0)})
        return result,missing
    groups={};missing=[]
    for carrier,props,apps,count in unresolved:
        reg=resolver.carriers.get(carrier)
        if not reg or not reg.startswith('bloodborne_dw:source_') or reg in resolver.retired:
            missing.append({'sourceCarrier':carrier,'sourceProperties':props,'sourceApplications':apps,'count':count,'reason':'NO_ACTIVE_CANONICAL_TYPE'});continue
        key,yaw,canonical=normalized(apps);g=groups.setdefault(reg,{'registryId':reg,'mainId':resolver.ids.get(reg,''),'currentPoseCount':len(resolver.current[reg]['variants']),'poses':{}})
        p=g['poses'].setdefault(key,{'sourceApplications':canonical,'count':0,'sourceStates':[]});p['count']+=count;p['sourceStates'].append({'carrier':carrier,'properties':props,'yaw':yaw,'count':count})
    for g in groups.values():
        g['poses']=list(g['poses'].values());g['totalPoseCount']=g['currentPoseCount']+len(g['poses']);g['withinCurrentPoseCapacity']=g['totalPoseCount']<=(144 if g['registryId'].endswith('source_assembly_brick_wall') else 128)
        for i,p in enumerate(g['poses']):
            p['logicalPose']=g['currentPoseCount']+i;p['parts'],p['missingDependencies']=parts(p['sourceApplications'],[0,0,0])
    return {'status':'EXACT_SOURCE_APPENDING_REQUIRED_BEFORE_FULL_CONVERSION' if groups or missing else 'PASS_NO_APPEND_REQUIRED','groups':list(groups.values()),'unrepresentedVisibleSourceStates':missing,'dryRunDidNotChangeProduction':True}
