"""Exact current source applications, never heuristic material/name matching."""
from __future__ import annotations
import collections,copy,json,math,re
from pathlib import Path
from v125_map_index import ROOT,token,app_clean
from v125_map_resolve import Resolver,rotation

RES=ROOT/'src/architecture/resources'
class CurrentResolver(Resolver):
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
        for path in sorted((RES/'bloodborne_dw/composite').glob('*.json')):
            spec=json.loads(path.read_text(encoding='utf-8-sig'));reg=spec['id'];self.current[reg]=spec
            carrier=spec.get('source',{}).get('carrier')
            if carrier and not spec['source'].get('profileModel'):self.carriers[carrier]=reg
            frozen=len(self.baseline['descriptors'].get(path.stem,{}).get('variants',[]))
            for vi,v in enumerate(spec['variants']):
                if vi<frozen:continue
                apps=v.get('sourceClosedApplications',[])
                if not apps:continue
                c={'registryId':reg,'variant':vi,'open':False,'applications':apps,'sourcePlacementOffset':v.get('sourcePlacementOffset',[0,0,0]),'name':spec['displayName'],'currentAppend':True}
                if len(apps)>1 and any(a.get('offset') for a in apps):self.whole.append(c)
                else:self.candidates[tuple(sorted(self.model(a['model']) for a in apps))].append(c)
        self.resolve_token.cache_clear()

    def target(self,candidate,yaw):
        if not candidate.get('currentAppend'):return super().target(candidate,yaw)
        reg=candidate['registryId'];vi=candidate['variant'];shift=[-float(x)/16 for x in candidate['sourcePlacementOffset']]
        state={'variant':str(vi%16),'rotation':str(yaw),'profile':'base','open':'false'}
        if len(self.current[reg]['variants'])>16:state['art_bank']=str(vi//16)
        payload={'ImportedTypeVersion':1}
        if any(shift):payload['SourceShift']=shift
        return {'registryId':reg,'mainId':self.ids.get(reg,''),'properties':state,'mountY':0.,'sourceShift':shift,'payload':payload,'sourceOldRegistryId':reg,'sourceOldVariant':vi,'sourceOldRotation':yaw,'resolution':'EXACT_CURRENT_APPENDED_SOURCE_APPLICATIONS'}

    def classify(self,apps,carrier,props):
        names={a['model'] for a in apps}
        if not apps:return {'action':'REMOVE','reason':'SOURCE_BLOCKSTATE_NO_APPLICABLE_BRANCH','sourceCarrier':carrier}
        if names<=self.transparent|self.empty:return {'action':'REMOVE','reason':'SOURCE_ALL_PIXEL_ALPHA_ZERO' if names&self.transparent else 'SOURCE_EMPTY_GEOMETRY','models':sorted(names)}
        if self.carriers.get(carrier) in self.retired:return {'action':'REMOVE','reason':'USER_RETIRED_VANILLA_STYLE_CARRIER','retiredRegistryId':self.carriers[carrier],'models':sorted(names)}
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
