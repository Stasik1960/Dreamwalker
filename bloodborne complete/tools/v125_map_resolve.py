"""Pure source application -> frozen V12.4 registry/state/art-payload resolver.

Source vertical anchoring is compensated before deduplication. Unrepresented
whole/mixed multipart poses remain explicit unresolved evidence, never guessed.
"""
from __future__ import annotations
import collections, copy, functools, hashlib, json, math
from pathlib import Path
from v125_map_index import ROOT, app_clean, token

def rotation(vector, steps):
    a=math.pi*(steps%8)/4;c=math.cos(a);s=math.sin(a)
    return [round(vector[0]*c-vector[2]*s,10),float(vector[1]),round(vector[0]*s+vector[2]*c,10)]

def source_model(value):return value if ':' in value else 'minecraft:'+value

class Resolver:
    def __init__(self):
        self.baseline=json.loads((ROOT/'docs/v12.4-dedup/baseline-content.json').read_text(encoding='utf-8-sig'))
        self.plan=json.loads((ROOT/'docs/remaining-transfer/source-candidates.json').read_text(encoding='utf-8-sig'))
        self.types=json.loads((ROOT/'docs/v12.4-dedup/type-state-mapping.json').read_text(encoding='utf-8-sig'))
        cat=json.loads((ROOT/'src/architecture/resources/bloodborne_dw/debug_catalogue.json').read_text(encoding='utf-8-sig'))
        self.ids={r['registryId']:r['temporaryId'] for r in cat['entries']}
        self.names={r['registryId']:r['name'] for r in cat['entries']}
        self.maps={(r['oldRegistryId'],r['oldVariant']):r for r in self.types['mappings']}
        legacy=json.loads((ROOT/'src/architecture/resources/bloodborne_dw/imported-stair-legacy-art.json').read_text(encoding='utf-8-sig'))
        self.legacykeys={r['key'] for r in legacy['states']}
        self.candidates=collections.defaultdict(list);self.whole=[]
        self.aliases={}
        for obj in self.plan['objects']:
            for v in obj['variants']:
                for a in v.get('sourceAliases',[]):
                    if v['sourceModels']:self.aliases[a]=v['sourceModels'][0]
        for path,spec in self.baseline['descriptors'].items():
            reg='bloodborne_dw:'+path
            for vi,variant in enumerate(spec['variants']):
                for opened,pname in [(False,'closed'),(True,'open')]:
                    apps=variant.get('sourceOpenApplications' if opened else 'sourceClosedApplications',[])
                    if not apps:continue
                    candidate={'registryId':reg,'variant':vi,'open':opened,'applications':apps,'sourcePlacementOffset':variant.get('sourcePlacementOffset',[0,0,0]),'name':spec['displayName']}
                    if len(apps)>1 and any(a.get('offset') for a in apps):self.whole.append(candidate)
                    else:
                        key=tuple(sorted(self.model(a['model']) for a in apps));self.candidates[key].append(candidate)
        self.original=json.loads((ROOT/'docs/remaining-transfer/source-candidates.json').read_text(encoding='utf-8-sig'))['modelClassifications']
        self.classifications={r['sourceModel']:r for r in self.original}

    def model(self,name):
        name=source_model(name);return self.aliases.get(name,name)

    def match(self,expected,actual):
        if len(expected)!=len(actual):return None
        actual=sorted(actual,key=lambda a:(self.model(a['model']),a.get('x',0),a.get('y',0),token(a)))
        expected=sorted(expected,key=lambda a:(self.model(a['model']),a.get('x',0),a.get('y',0),token(a)))
        deltas=[]
        for a,b in zip(expected,actual):
            if self.model(a['model'])!=self.model(b['model']) or a.get('x',0)!=b.get('x',0) or bool(a.get('uvlock',False))!=bool(b.get('uvlock',False)):return None
            delta=(b.get('y',0)-a.get('y',0))%360
            if bool(a.get('uvlock')) and delta:return None
            if delta%45:return None
            deltas.append(int(delta/45))
        return deltas[0] if len(set(deltas))==1 else None

    def target(self,candidate,yaw):
        oldreg=candidate['registryId'];vi=candidate['variant'];mapping=self.maps.get((oldreg,vi))
        # Source applications were shifted to the floor during the original
        # import. The map needs the inverse of that translation, not seating.
        shift=[-float(x)/16 for x in candidate['sourcePlacementOffset']]
        payload={'ImportedTypeVersion':1}
        props={'variant':str(vi),'rotation':str(yaw),'profile':'base','open':str(candidate['open']).lower()}
        reg=oldreg
        if mapping:
            reg=mapping['newRegistryId'];props['variant']=str(mapping['newVariant']);props['rotation']=str((yaw+mapping['rotationDelta'])%8)
            props.update({k:str(v) for k,v in mapping.get('properties',{}).items()})
            inv=rotation(shift,-mapping['rotationDelta']);shift=[round(inv[i]+mapping['sourceShift'][i],10) for i in range(3)]
            selector=f'{oldreg}|{vi}'
            if selector in self.legacykeys:payload['ImportedStairLegacyArt']=selector
        if any(abs(v)>1e-10 for v in shift):payload['SourceShift']=shift
        return {'registryId':reg,'mainId':self.ids.get(reg,''),'properties':props,'mountY':0.0,'sourceShift':shift,'payload':payload,'sourceOldRegistryId':oldreg,'sourceOldVariant':vi,'sourceOldRotation':yaw,'sourceNormalizationCompensation':[round(-float(x)/16,10) for x in candidate['sourcePlacementOffset']],'resolution':'EXACT_SOURCE_APPLICATIONS_THEN_V124_ALIASES','sourceAliasesUsed':[]}

    @functools.lru_cache(maxsize=None)
    def resolve_token(self,tok):
        actual=json.loads(tok);key=tuple(sorted(self.model(a['model']) for a in actual));matches=[]
        for candidate in self.candidates.get(key,[]):
            yaw=self.match(candidate['applications'],actual)
            if yaw is not None:
                target=self.target(candidate,yaw)
                target['sourceAliasesUsed']=[a['model'] for a in actual if self.model(a['model'])!=a['model']]
                matches.append(target)
        if matches:
            matches.sort(key=lambda x:(int(x['mainId'] or '99999'),int(x['properties']['variant']),x['sourceOldRegistryId'],x['sourceOldVariant']))
            selected=matches[0];selected['equivalentSourceBackedAlternatives']=len(matches)
            return selected
        classes=[self.classifications.get(a['model'],{}) for a in actual]
        return {'resolution':'UNRESOLVED_SOURCE_APPLICATION_COMBINATION','sourceModels':[a['model'] for a in actual],'classifications':[{k:r.get(k,'') for k in ['kind','status','registryId','missing']} for r in classes],'reason':'No exact complete source-applications pose in frozen V12.3 descriptors; whole-source assembly or protected V12 special conversion must be resolved separately.'}

    def resolve(self,apps):return copy.deepcopy(self.resolve_token(token(sorted(apps,key=token))))

def source_uuid(dimension,xyz,role='owner'):
    import uuid
    return str(uuid.uuid5(uuid.UUID('86783c69-77d2-50cd-b6da-93882d539d99'),dimension+'|'+','.join(map(str,xyz))+'|'+role))
