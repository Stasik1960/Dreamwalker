"""Reconcile the exhaustive derived section cache with final current resources.

No source ZIP rescan or world write. Every original cell is retained in the
compressed plan; current whole recipes, hidden source parts and compact modes
are applied as an append-only decision dictionary. Exact removal coordinates
are stored as lossless section-linear runs (X fastest, then Z, then Y).
"""
import argparse,collections,copy,gzip,json,os,sqlite3,time,uuid,zlib
from pathlib import Path
import numpy as np
from v125_map_index import ROOT,MAP,PACK,CACHE,token,sha
from v127_apply_full_map_poses import write as dump
from v127_prepare_full_conversion import whole_pass
from v127_full_map_resolver import CurrentResolver
O=ROOT/'docs/v12.7/conversion';R=ROOT/'src/architecture/resources'
def runs(indices):
    values=np.asarray(indices,dtype=np.int64)
    if not len(values):return []
    split=np.flatnonzero(np.diff(values)>1)+1
    return [[int(a[0]),len(a)] for a in np.split(values,split)]
def props(state):
    carrier,_,tail=state.partition('[');return carrier,dict(part.split('=',1) for part in tail.rstrip(']').split(',') if part)
def main(path,orphan):
    path=path.resolve();r=CurrentResolver();source=sqlite3.connect(f'file:{CACHE}?mode=ro',uri=True);out=sqlite3.connect(path);out.execute('PRAGMA synchronous=OFF')
    oldconsumed={(d,x,y,z):wid for d,x,y,z,wid in out.execute('SELECT * FROM consumed')};out.execute('DELETE FROM consumed');out.execute('DELETE FROM whole_objects');out.commit();consumed,whole,attempts=whole_pass(r,source,out)
    compact={t['registryId']:set(t['poses']) for t in json.loads((R/'bloodborne_dw/imported-compact-v127.json').read_text(encoding='utf-8-sig'))['types']};dictionary={i:json.loads(d) for i,d in out.execute('SELECT id,data FROM decisions')};tokens={token(d):i for i,d in dictionary.items()};last=max(dictionary)+1
    def did(d):
        nonlocal last
        key=token(d)
        if key not in tokens:tokens[key]=last;dictionary[last]=d;out.execute('INSERT INTO decisions VALUES(?,?)',(last,key));last+=1
        return tokens[key]
    def classify(apps,state):
        carrier,properties=props(state);d=r.classify(apps,carrier,properties)
        if d['action']=='OWNER':
            p=d['properties'];reg=d['registryId'];pose=int(p.get('variant',0))+16*int(p.get('art_bank',0))
            if reg in compact and pose in compact[reg] and not d.get('sourceNativeModelOffsetState') and not any(d.get('sourceShift',[0,0,0])) and int(p.get('rotation',0))%2==0 and not d.get('payload',{}).get('ImportedStairLegacyArt'):
                d['action']='COMPACT';p['compact']='true';d['payload']={}
            elif reg in compact:p['compact']='false'
        if d['action'] in {'UNRESOLVED_VISIBLE','WHOLE_CANDIDATE'}:
            names={a['model'] for a in apps}
            if names<= {'minecraft:block/aca_door_1','minecraft:block/aca_door_2'}:
                d={'action':'REMOVE' if orphan=='remove' else 'ORPHAN_ACACIA_REVIEW','reason':'ORPHAN_PROTECTED_90001_SOURCE_FRAGMENT_NO_COMPLETE_PAIR','sourceModels':sorted(names),'policy':'Protected90001notaltered; removed90479notrestored; exactsourcefragmentwouldrequireuserexception'}
            elif names=={'minecraft:block/addon/iron_door_top'}:
                d={'action':'REMOVE','reason':'SOURCE_BROKEN_WHOLE_MISSING_AUTHORED_IRON_DOOR_COMPONENTS','missingModels':['addon/iron_door_bottom','addon/iron_door_open_bottom','addon/iron_door_open_top'],'sourceModels':sorted(names)}
        return {**d,'sourceState':state,'sourceApplications':apps}
    remap={}
    for i,d in list(dictionary.items()):
        if 'sourceApplications' in d and 'sourceState' in d:remap[i]=did(classify(d['sourceApplications'],d['sourceState']))
        else:remap[i]=i
    lut=np.arange(last,dtype=np.uint16)
    for a,b in remap.items():lut[a]=b
    changes=collections.defaultdict(dict)
    for p in set(oldconsumed)|set(consumed):
        dim,x,y,z=p;section=(dim,x>>4,z>>4,y>>4);index=((y&15)<<8)|((z&15)<<4)|(x&15)
        if p in consumed:changes[section][index]=did({'action':'CONSUMED','wholeObject':consumed[p],'reason':'EXACT_COMPLETE_SOURCE_CONSTRUCTION'})
        else:
            row=source.execute('SELECT a.apps,s.state FROM cells c JOIN applications a ON c.applications=a.id JOIN states s ON c.state=s.id WHERE dimension=? AND x=? AND y=? AND z=?',p).fetchone();assert row,p;changes[section][index]=did(classify(json.loads(row[0]),row[1]))
    lut=np.pad(lut,(0,last-len(lut)),mode='constant');lut[len(remap):]=np.arange(len(remap),last,dtype=np.uint16)
    fillerFile=ROOT/'build/v127-enclosed-fillers.sqlite';filler=sqlite3.connect(f'file:{fillerFile}?mode=ro',uri=True) if fillerFile.exists() and r.preserveRetired else None;fillerProof=json.loads((O/'enclosed-vanilla-filler-proof.json').read_text(encoding='utf-8-sig')) if filler else None
    if fillerProof:assert fillerProof['status']=='PASS_READ_ONLY_OPAQUE_SOURCE_ENCLOSED_FILLER_PROOF' and fillerProof['sourceHashes']=={'map':sha(MAP),'pack':sha(PACK)};approvedFillerSources={(row['sourceState'],token(row['sourceApplications'])) for row in fillerProof['opaqueSourceDecisionProofs'] if row['retiredCandidate'] and row['candidateEntireGeometryContainedInUnitCell']}
    fillerCount=0;counts=collections.Counter();reasoncounts=collections.Counter();targetcounts=collections.Counter();sourcecounts=collections.Counter();gaps=[];orphanrows=[];sectioncount=0;start=time.monotonic();temp=ROOT/'build'/('v127-removal-runs-'+uuid.uuid4().hex+'.jsonl.gz')
    with gzip.open(temp,'wt',encoding='utf8') as removal:
        for dim,cx,cz,sy,sp,si,di in out.execute('SELECT * FROM sections ORDER BY dimension,cx,cz,sy'):
            sourceindex=np.frombuffer(zlib.decompress(si),dtype='<u2');sourcepalette=json.loads(sp);old=np.frombuffer(zlib.decompress(di),dtype='<u2');index=lut[old].copy();section=(dim,cx,cz,sy)
            for pi,(state,registry,properties) in enumerate(sourcepalette):
                if not registry.startswith('minecraft:'):
                    index[sourceindex==pi]=did({'action':'REMOVE','reason':'UNKNOWN_SOURCE_REGISTRY_OR_SYSTEM_SERVICE','sourceState':state,'sourceRegistryId':registry,'sourceApplications':[],'policy':'User requested unknown/system cleanup; original ZIP remains immutable'})
            for i,d in changes.get(section,{}).items():index[i]=d
            if filler:
                row=filler.execute('SELECT replacement_bits,replacements FROM sections WHERE dimension=? AND cx=? AND cz=? AND sy=?',section).fetchone()
                if row:
                    mask=np.unpackbits(np.frombuffer(zlib.decompress(row[0]),dtype=np.uint8),bitorder='little')[:4096].astype(bool);assert int(np.count_nonzero(mask))==row[1]
                    for i in np.unique(index[mask]):
                        d=dictionary[int(i)];assert (d.get('sourceState'),token(d.get('sourceApplications',[]))) in approvedFillerSources,('Filler source signature changed',section,i,d)
                        at=mask&(index==i);index[at]=did({'action':'KEEP','reason':'USER_APPROVED_ENCLOSED_VANILLA_SOURCE_FILLER','sourceState':d['sourceState'],'sourceApplications':d['sourceApplications'],'evidence':'enclosed-vanilla-filler-proof.json','policy':'Exact source geometry entirely within unitcube and six proven opaque source neighbors; ordinary vanilla source carrier material permitted by user'})
                    fillerCount+=row[1]
            if not np.array_equal(old,index):out.execute('UPDATE sections SET decision_indices=? WHERE dimension=? AND cx=? AND cz=? AND sy=?',(zlib.compress(index.astype('<u2').tobytes(),1),*section))
            for pi,n in enumerate(np.bincount(sourceindex,minlength=len(sourcepalette))):
                if n:sourcecounts[sourcepalette[pi][0]]+=int(n)
            for i,n in zip(*np.unique(index,return_counts=True)):
                d=dictionary[int(i)];action=d['action'];counts[action]+=int(n);reason=d.get('reason',d.get('resolution',''));reasoncounts[reason]+=int(n)
                if action in {'OWNER','COMPACT'}:targetcounts[d['registryId']]+=int(n)
                if action in {'REMOVE','UNRESOLVED_VISIBLE','WHOLE_CANDIDATE','ORPHAN_ACACIA_REVIEW'}:
                    at=np.flatnonzero(index==i);row={'dimension':dim,'chunk':[cx,cz],'sectionY':sy,'linearRuns':runs(at),'sourceState':d.get('sourceState'),'sourceApplications':d.get('sourceApplications'),'reason':reason,'count':int(n)}
                    if action=='REMOVE':removal.write(token(row)+'\n')
                    else:
                        for v in at:
                            p=[cx*16+(int(v)&15),sy*16+(int(v)>>8),cz*16+((int(v)>>4)&15)];item={'dimension':dim,'position':p,'sourceState':d.get('sourceState'),'sourceApplications':d.get('sourceApplications'),'reason':reason}
                            (orphanrows if action=='ORPHAN_ACACIA_REVIEW' else gaps).append(item)
            sectioncount+=1
            if sectioncount%10000==0:out.commit();print(token({'sections':sectioncount,'counts':dict(counts),'elapsed':round(time.monotonic()-start,2)}),flush=True)
    os.replace(temp,O/'source-removal-coordinates.jsonl.gz');out.commit();chunks=out.execute('SELECT count(*) FROM chunks').fetchone()[0]
    if filler:assert fillerCount==fillerProof['provenEnclosedVanillaFillerCells'];filler.close()
    summary={'schemaVersion':1,'status':'PASS_COMPLETE_SOURCE_PLAN' if not gaps and not orphanrows else 'REVIEW_ORPHAN_ACACIA_ONLY' if not gaps else 'FAIL_KNOWN_SOURCE_GAPS','sourceHashes':{'map':sha(MAP),'pack':sha(PACK)},'chunks':chunks,'sections':sectioncount,'allPaletteCells':sum(sourcecounts.values()),'counts':dict(counts),'reasonCounts':dict(reasoncounts),'targetTypeCounts':dict(targetcounts),'wholeObjects':len(whole),'consumedSourceCells':len(consumed),'knownVisibleGaps':len(gaps),'orphanAcaciaCells':len(orphanrows),'gapSamples':gaps[:50],'orphanSamples':orphanrows[:8],'orphanAction':orphan,'removalCoordinateFile':'source-removal-coordinates.jsonl.gz','removalCoordinateFormat':'section linearRuns; index=(y&15)*256+(z&15)*16+(x&15), each[start,length] expands exact coordinates','removalCoordinateSha256':sha(O/'source-removal-coordinates.jsonl.gz'),'decisionDictionaryCount':len(dictionary),'sourceObservedStates':len(sourcecounts),'planFile':str(path.relative_to(ROOT)),'planBytes':path.stat().st_size,'sourceArchivesModified':False,'fullWorldConverted':False,'nativeValidation':'NOT_RUN_FULL_ORIGINAL_BY_USER_REQUEST','initialOverlapPrivilege':'Only incoming deterministicsourceUUID in nonserializedSourceConversionScope; no saved/item permission','RAMPolicy':'Exact native compact palettes; compressed per-section uint16; lazy chunk-sharded remaining owners; no global fullmapowner list','runtimeExecution':'Deferred until explicit user command','elapsedSeconds':round(time.monotonic()-start,2)}
    policy=O/'source-style-policy.json'
    if policy.exists():summary['sourceStylePolicy']=json.loads(policy.read_text(encoding='utf-8-sig'))['status'];summary['sourceStylePolicySha256']=sha(policy)
    summary['enclosedVanillaFillerCells']=fillerCount
    if summary.get('sourceStylePolicy')=='PENDING_USER_DECISION':summary['runtimeExecution']='BLOCKED_PENDING_SOURCE_STYLE_POLICY_AND_EXPLICIT_FULL_CONVERSION_COMMAND'
    out.execute('INSERT OR REPLACE INTO metadata VALUES(?,?)',('summary',token(summary)));out.commit();out.close();source.close();dump(O/'full-conversion-plan.json',summary);dump(O/'full-conversion-known-gaps.json',{'status':'PASS_NO_KNOWN_GAPS' if not gaps else 'FAIL','rows':gaps});dump(O/'orphan-acacia-source-fragments.json',{'status':'USER_REVIEW_REQUIRED' if orphanrows else 'PASS','count':len(orphanrows),'rows':orphanrows});print(token(summary))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--plan',type=Path,default=ROOT/'build/v127-full-conversion-first.sqlite');p.add_argument('--orphan-acacia',choices=['defer','remove'],default='defer');a=p.parse_args();main(a.plan,a.orphan_acacia)
