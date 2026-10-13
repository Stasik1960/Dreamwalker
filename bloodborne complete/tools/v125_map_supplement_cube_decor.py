"""Readonly supplemental source positions for full-cube-base ornamental models.

The derived cache heuristic is intentionally not a semantic exclusion: ground
and masonry overlays stay context, while finite trim/caps get exact occurrences.
"""
import collections,json,sqlite3,zipfile,copy
import numpy as np
from v125_map_index import ROOT,OUT,CACHE,MAP,PACK,BASELINE,dump,sha,token,app_clean
from v125_map_resolve import Resolver
from v125_map_assemblies import bounds
from scan_original_map_models import branches_for,raw_blockstates,normalized_model,weighted_index
from scan_original_map_states import decode_indices,state_details
from world_io import RegionFile,compound
DEST=ROOT/'docs/v12.5-city-review/assemblies'

def main():
    index=json.loads((OUT/'original-map-index.json').read_text());meta=index['models'];resolver=Resolver();con=sqlite3.connect(f'file:{CACHE}?mode=ro',uri=True)
    contextual={'minecraft:block/grass_block','minecraft:block/bricks/grass_block_0','minecraft:block/bricks/bricks_3'}
    candidates={m for m,v in meta.items() if v['fullCubeContext'] and (v['elements']>1 or v['bounds']!=[0.,0.,0.,1.,1.,1.])}-contextual
    chunks={}
    for dim,cx,cz,data in con.execute('SELECT dimension,cx,cz,data FROM chunk_metrics'):
        if dim!='minecraft:overworld':continue
        models=json.loads(data)['modelCounts']
        if any(models.get(m,0)>0 for m in candidates):chunks[(cx,cz)]={m:models[m] for m in candidates if models.get(m,0)}
    raw,_,_=raw_blockstates();modelinventory={r['model']:r for r in json.loads((ROOT/'docs/remaining-transfer/source-inventory.json').read_text(encoding='utf-8-sig'))['models']};records=[];groups={}
    grouped=collections.defaultdict(set)
    for cx,cz in chunks:grouped[(cx//32,cz//32)].add((cx,cz))
    with zipfile.ZipFile(MAP) as archive:
        for (rx,rz),wanted in grouped.items():
            for stored in RegionFile(archive.read(f'region/r.{rx}.{rz}.mca')).chunks():
                cx,cz=rx*32+stored.x,rz*32+stored.z
                if (cx,cz) not in wanted:continue
                root=compound(stored.nbt().root)
                for section in root['sections'].value:
                    f=compound(section)
                    if 'block_states' not in f:continue
                    data=compound(f['block_states']);p=data['palette'].value;descriptors=[state_details(v) for v in p];eligible={}
                    for pi,(key,carrier,props) in enumerate(descriptors):
                        if carrier not in raw:continue
                        g=branches_for(raw[carrier],props)
                        if any(normalized_model(o[0]['model']) in candidates for _,group in g for o in group):eligible[pi]=g
                    if not eligible:continue
                    ind=decode_indices(data.get('data').value if data.get('data') else [],len(p));sy=int(f['Y'].value)
                    for i,choice in enumerate(ind):
                        if int(choice) not in eligible:continue
                        key,carrier,props=descriptors[int(choice)];pos=[cx*16+(i&15),sy*16+(i>>8),cz*16+((i>>4)&15)]
                        apps=[]
                        for _,g in eligible[int(choice)]:
                            a=g[weighted_index([o[0].get('weight',1) for o in g],tuple(pos),'multipart' in raw[carrier])][0];apps.append(app_clean({**a,'model':normalized_model(a['model'])}))
                        if not any(a['model'] in candidates for a in apps):continue
                        target=resolver.resolve(apps);records.append({'sourcePosition':pos,'sourceState':key,'sourceCarrier':carrier,'sourceProperties':props,'sourceApplications':apps,'sourceBounds':bounds(apps,modelinventory),'target':target})
    counts=collections.Counter(a['model'] for r in records for a in r['sourceApplications'] if a['model'] in candidates)
    expected=collections.Counter();[expected.update(v) for v in chunks.values()];assert counts==expected,'Supplementactualcellmodelcountsdisagreewithvalidatedsourcechunkmetrics'
    apps={i:json.loads(a) for i,a in con.execute('SELECT id,apps FROM applications')};edges=[];counter=collections.Counter();examples={}
    offsets=[(0,d,0) for d in range(-6,7) if d]+[(d,0,0) for d in range(-3,4) if d]+[(0,0,d) for d in range(-3,4) if d]
    for r in records:
        b=r['sourceBounds'];pos=r['sourcePosition']
        for off in offsets:
            q=[pos[i]+off[i] for i in range(3)];found=con.execute('SELECT state,applications FROM cells WHERE dimension=? AND x=? AND y=? AND z=?',('minecraft:overworld',*q)).fetchone()
            if not found:continue
            sid,aid=found;otherapps=apps[aid];bb=bounds(otherapps,modelinventory)
            if not bb:continue
            gap=[max(b[i]-(bb[i+3]+off[i]),bb[i]+off[i]-b[i+3],0) for i in range(3)]
            if max(gap)>.125:continue
            target=resolver.resolve(otherapps);t=r['target']
            if not all(x['resolution'].startswith('EXACT') for x in [t,target]):continue
            pairkey=token((t['mainId'],target['mainId'],off));counter[pairkey]+=1
            examples.setdefault(pairkey,{'first':r,'second':{'sourcePosition':q,'sourceApplications':otherapps,'sourceBounds':bb,'target':target},'relativeOffset':off,'gap':gap})
    for key,n in counter.most_common():
        if n<2:continue
        e=examples[key];components=[]
        for no,(row,rel) in enumerate([(e['first'],[0,0,0]),(e['second'],list(e['relativeOffset']))],1):components.append({'number':no,'relativePosition':rel,**row['target'],'sourcePosition':row['sourcePosition'],'sourceApplications':row['sourceApplications'],'sourceVisualBoundsLocal':row['sourceBounds']})
        edges.append({'key':'CUBEDECOR-'+str(len(edges)+1).zfill(3),'russianLabel':'Орнамент на кубическом основании · кандидат соединения','occurrences':n,'components':components,'sourceExamples':[{'first':e['first']['sourcePosition'],'second':e['second']['sourcePosition']}],'confidence':'REPEATED_SOURCE_JOIN_REQUIRES_VISIBLE_ROLE_REVIEW','galleryDisposition':'REPORT_ONLY_PENDING_JOINED_POLYGON_REVIEW'})
    report={'schemaVersion':1,'status':'PASS_SUPPLEMENTAL_READONLY_SOURCE_OCCURRENCES','baselineCommit':BASELINE,'mapSha256':sha(MAP),'packSha256':sha(PACK),'previousHeuristicCaveat':'Contains-a-fullcube markscontext, butdoesnotproveamodelislackingfiniteornament. Allnontrivialcaseslistedandtargetedsourcepositionsscanned; fullground/masonryoverlaysstillcontextwithoutobjectboundaryclaim.','sourceChunksRead':len(chunks),'sourceRegionEntriesRead':len(grouped),'candidateModelCounts':dict(counts),'sourceRecords':records,'sourceCountCrossCheck':'PASS','reportOnlyCandidates':edges,'groundMasonryContextModelsExplicit':sorted(contextual),'noProductionOrExistingFinalInputModification':True}
    dump(DEST/'fullcube-ornament-supplement.json',report)

if __name__=='__main__':main()
