"""Readonly final+2Z32x49x32city crop; refresh source/export/pose proofs."""
import collections,gzip,json
from v125_map_city import read_box,selected_apps,DEST
from v125_map_index import ROOT,MAP,PACK,BASELINE,dump,sha
from scan_original_map_models import raw_blockstates
from v125_map_resolve import Resolver,source_uuid

def main():
    box=[144,64,-1086,175,112,-1055];clip=read_box(box);raw,_,_=raw_blockstates();resolver=Resolver();owners=[];unresolved=[];passthrough=[];applications=[];origin=box[:3]
    for x,y,z,key,carrier,props in clip.pop('blocks'):
        pos=[x,y,z];relative=[pos[i]-origin[i] for i in range(3)]
        if carrier not in raw:passthrough.append({'sourcePosition':pos,'relativePosition':relative,'sourceState':key,'registryId':carrier,'properties':props});continue
        apps=selected_apps(raw,carrier,props,(x,y,z));target=resolver.resolve(apps);row={'sourcePosition':pos,'relativePosition':relative,'sourceState':key,'sourceCarrier':carrier,'sourceProperties':props,'sourceApplications':apps};applications.append(row)
        if target['resolution'].startswith('EXACT'):owners.append({**row,**target,'instanceUuid':source_uuid('minecraft:overworld',pos)})
        else:unresolved.append({**row,**target})
    data={'schemaVersion':1,'status':'READONLY_SOURCE_EXPORT_WITH_EXPLICIT_UNRESOLVED_ROWS','baselineCommit':BASELINE,'candidateId':'CITY-32-02-H49-ZPLUS2','dimension':'minecraft:overworld','mapSha256':sha(MAP),'packSha256':sha(PACK),'sourceOrigin':origin,'rawBlocks':clip,'owners':owners,'unresolvedSourceCells':unresolved,'passThroughBlocks':passthrough,'sourceApplications':applications,'entitiesPolicy':'No liveNPC/entity copy. SourceBEtypeddata is inert, commands/programs notexecuted.','normalizationPolicy':'Inverse sourcePlacementOffset thenV124alias localshift/yaw; no individualgalleryseating.','finalBoundsDecision':'Shift north/south edges+2Z equally, retain32x49x32; avoid3unsupportedprotectedV12material1cells without changing anyV12behavior.240finite source models retained versus249prior.'}
    file=DEST/'city-source-data.json.gz'
    with file.open('wb') as f:
        with gzip.GzipFile(fileobj=f,mode='wb',mtime=0) as g:g.write(json.dumps(data,ensure_ascii=False,separators=(',',':')).encode())
    import v125_map_city_finish;v125_map_city_finish.main()
    import v125_map_extra_city_poses;v125_map_extra_city_poses.main()
    import v125_map_verify_resolver;v125_map_verify_resolver.main()
    candidate=json.loads((DEST/'city-candidate.json').read_text());candidate['finalDecision']={'candidateId':data['candidateId'],'boundsInclusive':box,'finiteSelectedSourceModels':240,'affectedCarrierCells':5146,'protectedMaterial1UnsupportedCells':0,'explicitDeletedOrMissingExceptions':7,'reason':data['finalBoundsDecision'],'evidence':'nearby-bounds-source-review.json'};dump(DEST/'city-candidate.json',candidate)

if __name__=='__main__':main()
