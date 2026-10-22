"""Pure approved +2 native-component dependency union; no world/plan writes."""
import copy
import pilot_dependencies as p
m=p.m
base=p.PLAN/'context-extension/boundary-dependency-inventory-v2.json'
proof_path=m.ROOT/'research/native-functional-bases/original427-native-dependency-inventory.json'
tree_path=m.ROOT/'research/native-functional-bases/unknown-native-tree-footprints.json'
out=p.PLAN/'context-extension/boundary-dependency-inventory-v3.json'
def main():
    m.require(m.sha(base)=='e89c95e8debce151cc932cda34222d5fec6af914be7eca8eb91e637a8f4833b8','Approved89 inventory changed')
    m.require(m.sha(proof_path)=='350a6b1e5a4eb3fe6ac6eae11294d71f6e95b57d312360c91cef7c9b7e1f8a35','Exact original427 native proof changed')
    m.require(m.sha(tree_path)=='7de51902eb54e31e9473fec40274dfb3587f3b67a82065629c9c89b5f0cf6724','Final17 finite footprint priority proof changed')
    old=m.read(base);proof=m.read(proof_path);m.require(proof['sourceBinding']==old['sourceBinding'] and proof['baselineAllComponents']==13731 and not proof['sourceRootMismatches'],'Native source component binding changed')
    extra=proof['additionalChunksRequired'];m.require({(r['x'],r['z']) for r in extra}=={(-7,2),(11,0)} and len(extra)==2,'Only two approved additional chunks may be added')
    manifest=m.read(old['sourceBinding']['copyManifest']);new=copy.deepcopy(old)
    for row in extra:
        cached=row['cachedSourceProof'];rel=cached['region'];m.require(manifest['files'][rel]['sha256']==cached['regionSha256'],'Exact source region binding changed')
        new['chunks'].append({'dimension':'.','x':row['x'],'z':row['z'],'owners':[],'cached':True,'sourceRegion':rel,'sourceRegionSha256':cached['regionSha256'],'compression':cached['compression'],'cachedCompressedPayloadSha256':cached['cachedPayloadSha256'],'reason':'complete_original427_finite_native_component_dependencies','componentKeys':row['componentKeys']})
        shard='data/bloodborne_dw_owner_shards/owners-%d.%d.nbt'%(row['x'],row['z']);m.require(shard in manifest['files'],'Exact added source shard missing');new['shards'].append({'path':shard,'sha256':manifest['files'][shard]['sha256']})
    new['chunks'].sort(key=lambda r:(r['dimension'],r['x'],r['z']));new['shards'].sort(key=lambda r:r['path'])
    new.update(previousInventory={'path':str(base),'sha256':m.sha(base)},original427NativeDependencyProof={'path':str(proof_path),'sha256':m.sha(proof_path)},finite17FootprintProof={'path':str(tree_path),'sha256':m.sha(tree_path)},missingDependencyChunks=91,missingDependencyShards=91,expectedFinalGlobalShardCount=498,totalCopiedTerrainChunkDomain=518,sourceOnlyUnionNoNewOwnerClosure=True)
    m.require(len(new['chunks'])==91 and len({(r['dimension'],r['x'],r['z']) for r in new['chunks']})==91 and len(new['shards'])==91,'Finite union has duplicate/missing chunks')
    if out.exists():m.require(m.read(out)==new,'Published final inventory changed')
    else:m.atomic_json(out,new)
    print(m.canonical({'status':'PASS','path':str(out),'sha256':m.sha(out),'auxiliaryChunks':91,'terrainChunks':518,'sourceShards':498,'mapWrites':0,'databaseWrites':0}))
if __name__=='__main__':m.control_check();main()
