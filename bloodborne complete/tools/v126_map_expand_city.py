"""Readonly V16 city expansion containing the accepted V12.5 source rectangle.

The source ZIP and existing SQLite index are opened read-only. All output is a
derived V12.6 candidate; no production resources or older reports are written.
"""
from __future__ import annotations
import argparse, collections, copy, gzip, hashlib, json, sqlite3
from pathlib import Path
from v125_map_index import ROOT, MAP, PACK, CACHE, token, dump, sha
from v125_map_city import read_box, selected_apps
from v125_map_resolve import Resolver, rotation, source_uuid
from scan_original_map_models import raw_blockstates

OUT = ROOT / 'docs/v12.6-city-review/map/citycandidate'
OLD_BOX = [144, 64, -1086, 175, 112, -1055]
RES = ROOT / 'src/architecture/resources'


class CurrentResolver(Resolver):
    """Keep established conversion choices, then consider appended source poses."""
    def __init__(self):
        super().__init__()
        self.current_candidates = collections.defaultdict(list)
        aliases = json.loads((RES/'bloodborne_dw/imported-type-aliases.json').read_text(encoding='utf-8-sig'))
        self.canonical_regs = {g['primaryRegistryId'] for g in aliases['groups']}
        for file in sorted((RES/'bloodborne_dw/composite').glob('source_*.json')):
            spec = json.loads(file.read_text(encoding='utf-8-sig'))
            reg = 'bloodborne_dw:' + file.stem
            if reg not in self.canonical_regs:
                # Unmapped singleton finished types remain live and have no alias group.
                mapped = {r['oldRegistryId'] for r in self.types['mappings'] if r['oldRegistryId'] != r['newRegistryId']}
                if reg in mapped:
                    continue
            for vi, variant in enumerate(spec['variants']):
                apps = variant.get('sourceClosedApplications', [])
                if not apps or any(any(a.get('offset', [0, 0, 0])) for a in apps):
                    continue
                key = tuple(sorted(self.model(a['model']) for a in apps))
                self.current_candidates[key].append({'registryId':reg, 'variant':vi,
                    'applications':apps, 'sourcePlacementOffset':variant.get('sourcePlacementOffset',[0,0,0]),
                    'poseCount':len(spec['variants'])})

    def current_target(self, candidate, yaw):
        reg = candidate['registryId']; logical = candidate['variant']
        props = {'variant':str(logical % 16 if candidate['poseCount'] > 16 else logical),
                 'rotation':str(yaw), 'profile':'base', 'open':'false'}
        if candidate['poseCount'] > 16:
            props['art_bank'] = str(logical // 16)
        shift = [round(-float(v)/16, 10) for v in candidate['sourcePlacementOffset']]
        payload = {'ImportedTypeVersion':1}
        if any(shift):
            payload['SourceShift'] = shift
        return {'registryId':reg, 'mainId':self.ids.get(reg,''), 'properties':props,
                'mountY':0., 'sourceShift':shift, 'payload':payload,
                'sourceLogicalPoseIndex':logical, 'sourceOldRegistryId':reg,
                'sourceOldVariant':logical, 'sourceOldRotation':yaw,
                'sourceNormalizationCompensation':shift,
                'resolution':'EXACT_SOURCE_CURRENT_APPEND_ONLY_POSE', 'sourceAliasesUsed':[]}

    def resolve(self, apps):
        established = super().resolve(apps)
        if established['resolution'].startswith('EXACT'):
            return established
        key = tuple(sorted(self.model(a['model']) for a in apps)); matches=[]
        for candidate in self.current_candidates.get(key, []):
            yaw = self.match(candidate['applications'], apps)
            if yaw is not None:
                matches.append(self.current_target(candidate, yaw))
        if matches:
            matches.sort(key=lambda r:(int(r['mainId'] or '99999'), r['sourceLogicalPoseIndex']))
            return matches[0]
        return established


def rank_candidates():
    index = json.loads((ROOT/'docs/v12.5-city-review/map/original-map-index.json').read_text())
    assert index['status'] == 'PASS'
    finite = {m for m,r in index['models'].items() if r['hasGeometry'] and not r['fullCubeContext']}
    con=sqlite3.connect(f'file:{CACHE}?mode=ro',uri=True)
    rows=[]
    apps={i:json.loads(a) for i,a in con.execute('SELECT id,apps FROM applications')}
    states={i:(state,name,json.loads(props)) for i,state,name,props in con.execute('SELECT id,state,name,properties FROM states')}
    cells=list(con.execute('SELECT x,y,z,state,applications FROM cells WHERE dimension=? AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? AND z BETWEEN ? AND ?',
        ('minecraft:overworld',112,207,64,112,-1118,-1023)))
    resolver=CurrentResolver()
    unresolved_app_ids={i for i,a in apps.items() if not resolver.resolve(a)['resolution'].startswith('EXACT')}
    for x0 in range(112,145,8):
        for z0 in range(-1118,-1085,8):
            chosen=[r for r in cells if x0<=r[0]<=x0+63 and z0<=r[2]<=z0+63]
            models={a['model'] for _,_,_,_,ap in chosen for a in apps[ap]} & finite
            source_states={s for _,_,_,s,_ in chosen}
            pending=[r for r in chosen if r[4] in unresolved_app_ids]
            wall_materials=collections.Counter()
            for _,_,_,state,ap in pending:
                if states[state][1]=='minecraft:polished_deepslate_wall':
                    materials={int(a['model'].rsplit('_',1)[1]) for a in apps[ap] if '_side_tall_' in a['model']}
                    wall_materials[next(iter(materials),6)]+=1
            rows.append({'sourceBoundsInclusive':[x0,64,z0,x0+63,112,z0+63],
                         'sizeXYZ':[64,49,64], 'finiteIndexedCells':len(chosen),
                         'finiteSelectedModels':len(models), 'finiteSourceStates':len(source_states),
                         'containedPreviousBox':OLD_BOX, 'horizontalAreaRatioToV125':4.0,
                         'denseVolumeRatioToV125':4.0,
                         'coarseUnresolvedFiniteCells':len(pending),
                         'coarseUnresolvedApplicationCombinations':len({r[4] for r in pending}),
                         'protectedV12WallMaterialCounts':dict(sorted(wall_materials.items())),
                         'protectedV12UnsupportedMaterial1Cells':wall_materials[1],
                         'protectedV12DeletedMaterialCells':sum(wall_materials[m] for m in [2,3,4,5,7])})
    con.close()
    rows.sort(key=lambda r:(-r['finiteSelectedModels'],-r['finiteSourceStates'],-r['finiteIndexedCells'],r['sourceBoundsInclusive']))
    return rows


def attach_wholes_and_services(data, resolver):
    rows={tuple(r['sourcePosition']):r for r in data['sourceApplications']}
    unresolved={tuple(r['sourcePosition']):r for r in data['unresolvedSourceCells']}
    consumed=set(); whole=[]
    for pos,row in sorted(unresolved.items()):
        if pos in consumed:continue
        for candidate in resolver.whole:
            if not candidate['applications'] or candidate['open']:continue
            first=[a for a in candidate['applications'] if not any(a.get('offset',[0,0,0]))]
            yaw=resolver.match(first,row['sourceApplications'])
            if yaw is None:continue
            expected=collections.defaultdict(list)
            for a in candidate['applications']:
                off=rotation([v/16 for v in a.get('offset',[0,0,0])],yaw)
                if any(abs(v-round(v))>1e-8 for v in off):break
                worldpos=tuple(pos[i]+round(off[i]) for i in range(3)); app={k:v for k,v in a.items() if k!='offset'}
                app['y']=(app.get('y',0)+yaw*45)%360;expected[worldpos].append(app)
            else:
                if all(p in unresolved and p not in consumed and resolver.match(expected[p],rows[p]['sourceApplications'])==0 for p in expected):
                    target=resolver.target(candidate,yaw)
                    whole.append({**row,**target,'instanceUuid':source_uuid('minecraft:overworld',pos,'whole'),
                        'sourceComponentPositions':[list(p) for p in expected],
                        'sourceComponentApplications':[rows[p] for p in expected],
                        'resolution':'EXACT_MULTI_CELL_SOURCE_WHOLE_THEN_V124_ALIASES'})
                    consumed.update(expected);break
    helper=json.loads((ROOT/'docs/remaining-transfer/transparent-helper-validation.json').read_text())
    assert helper['status']=='PASS' and helper['packSha256']==sha(PACK)
    transparent={r['model'] for r in helper['models'] if r['status']=='ALL_VISIBLE_FACE_PIXELS_ALPHA_ZERO'}
    inventory=json.loads((ROOT/'docs/remaining-transfer/source-inventory.json').read_text(encoding='utf-8-sig'))
    empty={r['model'] for r in inventory['models'] if not any(e.get('faces') for e in r['resolved'].get('elements',[]))}
    service=[];still=[]
    for pos,row in unresolved.items():
        if pos in consumed:continue
        names={a['model'] for a in row['sourceApplications']}
        if names and names<=transparent|empty:
            service.append({**row,'resolution':'SOURCE_ALL_ALPHA_ZERO_INVISIBLE_SERVICE' if names&transparent else 'SOURCE_EMPTY_GEOMETRY_SERVICE',
                'nativeConversion':'minecraft:air','status':'EXPLICIT_INVISIBLE_SERVICE_AIR',
                'sourceTransparencyProof':'docs/remaining-transfer/transparent-helper-validation.json'})
        else:still.append(row)
    data['owners'].extend(whole); data['wholeSourceOwners']=whole
    data['sourceInvisibleServices']=service;data['unresolvedSourceCells']=still
    return data


def main():
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,default=OUT)
    p.add_argument('--top',type=int,default=1);p.add_argument('--rank-only',action='store_true')
    p.add_argument('--box',nargs=6,type=int);p.add_argument('--number-offset',type=int,default=0)
    args=p.parse_args();args.output.mkdir(parents=True,exist_ok=True)
    before={p.name:sha(p) for p in [MAP,PACK]};ranked=rank_candidates()
    dump(args.output/'expansion-ranked-candidates.json',{'status':'PASS_READONLY_INDEX_SELECTION',
        'previousBox':OLD_BOX,'ranking':'All 25 offset-eight source windows containing entire approved 32x49x32 crop; rank actual finite source models, then source states, then finite cells.',
        'candidates':ranked,'sourceHashes':before})
    if args.rank_only:
        print(json.dumps({'status':'PASS_READONLY_RANKING','candidates':len(ranked)}));return
    if args.box:
        selected=next((r for r in ranked if r['sourceBoundsInclusive']==args.box),None)
        assert selected, 'Explicit candidate must be among equally sized windows containing approved crop'
        ranked=[selected]
    raw,_,_=raw_blockstates();resolver=CurrentResolver();reports=[]
    for number,candidate in enumerate(ranked[:args.top],1+args.number_offset):
        clip=read_box(candidate['sourceBoundsInclusive']); origin=candidate['sourceBoundsInclusive'][:3]
        owners=[];unresolved=[];passthrough=[];applications=[];modelcounts=collections.Counter()
        for x,y,z,key,carrier,props in clip.pop('blocks'):
            xyz=[x,y,z];relative=[xyz[i]-origin[i] for i in range(3)]
            if carrier not in raw:
                passthrough.append({'sourcePosition':xyz,'relativePosition':relative,'sourceState':key,'registryId':carrier,'properties':props});continue
            apps=selected_apps(raw,carrier,props,xyz); modelcounts.update(a['model'] for a in apps)
            base={'sourcePosition':xyz,'relativePosition':relative,'sourceState':key,'sourceCarrier':carrier,'sourceProperties':props,'sourceApplications':apps}
            result=resolver.resolve(apps); applications.append(base)
            if result['resolution'].startswith('EXACT'):
                owners.append({**base,**result,'instanceUuid':source_uuid('minecraft:overworld',xyz)})
            else:unresolved.append({**base,**result})
        data={'schemaVersion':1,'status':'READONLY_EXPANDED_SOURCE_EXPORT_WITH_EXPLICIT_UNRESOLVED',
            'candidateId':f'V126-CITY64-{number:02d}','dimension':'minecraft:overworld',
            'mapSha256':before[MAP.name],'packSha256':before[PACK.name],
            'sourceOrigin':origin,'rawBlocks':clip,'owners':owners,'unresolvedSourceCells':unresolved,
            'passThroughBlocks':passthrough,'sourceApplications':applications,
            'entitiesPolicy':'No live NPC/entity copy; typed block entities are inert evidence and must not be executed/copied.',
            'normalizationPolicy':'Preserve established V12.4 alias and SourceShift conversions; exact V12.5 appended single-cell poses considered only for formerly unresolved rows. No per-cell floor seating.',
            'previousApprovedSourceBounds':OLD_BOX,'horizontalAreaRatioToV125':4.0,'denseVolumeRatioToV125':4.0}
        attach_wholes_and_services(data,resolver)
        file=args.output/f'city-source-data-{number:02d}.json.gz'
        with file.open('wb') as f:
            with gzip.GzipFile(fileobj=f,mode='wb',mtime=0) as g:g.write(json.dumps(data,ensure_ascii=False,separators=(',',':')).encode())
        report={**candidate,'candidateId':data['candidateId'],'owners':len(data['owners']),
            'affectedSourceCells':len(applications),'wholeSourceOwners':len(data['wholeSourceOwners']),
            'invisibleServiceCells':len(data['sourceInvisibleServices']),
            'unresolvedSourceCells':len(data['unresolvedSourceCells']),
            'unresolvedCarriers':dict(collections.Counter(r['sourceCarrier'] for r in data['unresolvedSourceCells'])),
            'passThroughBlocks':len(passthrough),'passThroughCarriers':dict(collections.Counter(r['registryId'] for r in passthrough)),
            'sourceBlockEntities':len(clip['blockEntities']),'sourceChunks':clip['chunks'],
            'rawPaletteStates':len(clip['palette']),'rawDenseCells':len(clip['denseIndices']),
            'selectedSourceModels':len(modelcounts),'ownerCountsByRegistry':dict(collections.Counter(r['registryId'] for r in data['owners'])),
            'sourceModelCounts':dict(modelcounts),'dataFile':str(file),'dataSha256':sha(file),'dataBytes':file.stat().st_size}
        reports.append(report);dump(args.output/f'city-source-summary-{number:02d}.json',report)
        dump(args.output/f'visible-unresolved-{number:02d}.json',{'status':'SOURCE_APPLICATION_POSES_REQUIRE_EXACT_DISPOSITION','rows':data['unresolvedSourceCells']})
    after={p.name:sha(p) for p in [MAP,PACK]};assert after==before
    dump(args.output/'expansion-summary.json',{'status':'PASS_READONLY_CANDIDATE_EXPORT_NATIVE_NOT_RUN',
        'chosenCandidate':reports[0],'candidates':reports,'sourceHashesBefore':before,'sourceHashesAfter':after,
        'originalWorldOrPackModified':False,'productionCodeModified':False,'nativeValidation':'NOT_RUN'})
    print(json.dumps({k:reports[0][k] for k in ['sourceBoundsInclusive','owners','affectedSourceCells','unresolvedSourceCells','unresolvedCarriers','passThroughBlocks','sourceBlockEntities']}))

if __name__=='__main__':main()
