"""Bind the unchanged accepted427 audit to an approved dependency-only superset."""
import copy
import pilot_dependencies as p
m=p.m
def main():
    path=p.REPAIR/'audit.json';saved=p.REPAIR/'audit-before-final-native-union.json';proof_path=m.ROOT/'research/native-functional-bases/original427-native-dependency-inventory-v3.json'
    m.require(m.sha(proof_path)=='cad025355b2f8d182471e596b01732711214caaeb93a1abf69d1905bff9538f2','Final native purecoverage proof changed')
    m.require(m.sha(p.INVENTORY)==p.INVENTORY_SHA,'Approved91 inventory changed');proof=m.read(proof_path);new=m.read(p.INVENTORY)
    if saved.exists():m.require(m.sha(saved)=='4d8fee075bd2bbe0de8223607d5981e6d8cfed4593073ce5c9411cc9143bd4c1','Frozen accepted audit changed');report=m.read(saved)
    else:
        m.require(m.sha(path)=='4d8fee075bd2bbe0de8223607d5981e6d8cfed4593073ce5c9411cc9143bd4c1','Accepted427 audit changed');p.sync_copy(path,saved);report=m.read(saved)
    m.require(report['status']=='PASS' and not report['errors'] and proof['status']=='PASS' and proof['depsComplete'] and not proof['additionalChunksRequired'] and proof['sourceBinding']==report['sourceBinding']==new['sourceBinding'],'Final source native dependencies incomplete')
    old=m.read(report['inventory']['path']);m.require(m.sha(report['inventory']['path'])==report['inventory']['sha256'] and old['owners']==new['owners'],'Union changed real-owner selection')
    oldchunks={(r['dimension'],r['x'],r['z']):r for r in old['chunks']};newchunks={(r['dimension'],r['x'],r['z']):r for r in new['chunks']}
    m.require(all(newchunks.get(k)==v for k,v in oldchunks.items()) and set(newchunks)-set(oldchunks)=={('.',-7,2),('.',11,0)},'Final dependency union changed existing slots')
    current=copy.deepcopy(report);current['previousAcceptedAudit']={'path':str(saved),'sha256':m.sha(saved)};current['inventory']={'path':str(p.INVENTORY),'sha256':p.INVENTORY_SHA};current['original427NativeDependencyCoverage']={'path':str(proof_path),'sha256':m.sha(proof_path)};current['dependencyOnlyRebind']={'old427ChunkProofsByteEquivalent':True,'originalWorldsProofSha256':m.digest(report['worlds']),'noWorldOrCacheReads':True,'newOwnerClosure':False}
    m.require(m.digest(current['worlds'])==m.digest(report['worlds']),'Accepted perworld427 proof changed');m.atomic_json(path,current);print(m.canonical({'status':'PASS','auditSha256':m.sha(path),'inventorySha256':p.INVENTORY_SHA,'original427Rescanned':False,'mapWrites':0,'databaseWrites':0}))
if __name__=='__main__':m.control_check();main()
