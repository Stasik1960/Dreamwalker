"""Finite retained-owner dependencies of original427; no recursive expansion."""
import collections,json,time
from pathlib import Path
import migrate as m
import context_stage as c

plan=m.ROOT/'research/native-grid-plan';db=c.attached_readonly(m,plan);started=time.monotonic();out=plan/'context-extension/boundary-dependency-inventory.json'
def progress(stage,**values):m.atomic_json(out.with_name('boundary-dependency-progress.json'),{'stage':stage,'seconds':time.monotonic()-started,'mapWrites':0,'databaseWrites':0,**values})
try:
    db.execute('CREATE TEMP TABLE fixed(cx INTEGER,cz INTEGER,PRIMARY KEY(cx,cz)) WITHOUT ROWID');db.execute('INSERT INTO fixed SELECT cx,cz FROM context_allowed_chunks')
    db.execute('CREATE TEMP TABLE hits(dim TEXT,uuid TEXT,PRIMARY KEY(dim,uuid)) WITHOUT ROWID')
    for table in ('stored_contributions','members'):
        db.execute('INSERT OR IGNORE INTO hits SELECT r.dim,r.uuid FROM old.'+table+' r CROSS JOIN fixed a ON a.cx=r.x>>4 AND a.cz=r.z>>4 WHERE r.dim=\'.\'')
        progress('INSIDE_REFERENCES_'+table,hits=db.execute('SELECT count(*) FROM hits').fetchone()[0])
    db.execute('DELETE FROM hits WHERE EXISTS(SELECT 1 FROM selected_objects s WHERE s.dim=hits.dim AND s.uuid=hits.uuid) OR EXISTS(SELECT 1 FROM stage1_selected_objects s WHERE s.dim=hits.dim AND s.uuid=hits.uuid) OR NOT EXISTS(SELECT 1 FROM old.stored_owners s WHERE s.dim=hits.dim AND s.uuid=hits.uuid)')
    db.execute('CREATE TEMP TABLE footprint(dim TEXT,uuid TEXT,cx INTEGER,cz INTEGER,PRIMARY KEY(dim,uuid,cx,cz)) WITHOUT ROWID')
    db.execute('INSERT OR IGNORE INTO footprint SELECT s.dim,s.uuid,s.x>>4,s.z>>4 FROM hits h JOIN old.stored_owners s ON s.dim=h.dim AND s.uuid=h.uuid')
    db.execute('INSERT OR IGNORE INTO footprint SELECT o.dim,o.uuid,(o.x+p.dx)>>4,(o.z+p.dz)>>4 FROM hits h JOIN objects o ON o.dim=h.dim AND o.uuid=h.uuid JOIN pose_cells p ON p.pose=o.pose')
    for table in ('stored_contributions','members'):
        db.execute('INSERT OR IGNORE INTO footprint SELECT r.dim,r.uuid,r.x>>4,r.z>>4 FROM old.'+table+' r CROSS JOIN hits h ON h.dim=r.dim AND h.uuid=r.uuid WHERE r.dim=\'.\'')
        progress('COMPLETE_FOOTPRINT_'+table,retainedOwners=db.execute('SELECT count(*) FROM hits').fetchone()[0])
    db.execute('CREATE TEMP TABLE boundary(dim TEXT,uuid TEXT,PRIMARY KEY(dim,uuid)) WITHOUT ROWID')
    db.execute('INSERT OR IGNORE INTO boundary SELECT f.dim,f.uuid FROM footprint f LEFT JOIN fixed a ON a.cx=f.cx AND a.cz=f.cz WHERE a.cx IS NULL')
    manifest=m.read(m.meta(db,'binding')['copyManifest']);owners=[];needed=collections.defaultdict(list)
    for dim,uid,rid,x,y,z in db.execute('SELECT s.dim,s.uuid,s.registry_id,s.x,s.y,s.z FROM boundary b JOIN old.stored_owners s ON s.dim=b.dim AND s.uuid=b.uuid ORDER BY s.dim,s.uuid'):
        obj=db.execute('SELECT pose,kind,reason FROM objects WHERE dim=? AND uuid=?',(dim,uid)).fetchone();chunks=[list(r) for r in db.execute('SELECT f.cx,f.cz FROM footprint f LEFT JOIN fixed a ON a.cx=f.cx AND a.cz=f.cz WHERE f.dim=? AND f.uuid=? AND a.cx IS NULL ORDER BY f.cx,f.cz',(dim,uid))]
        for cx,cz in chunks:needed[(dim,cx,cz)].append(uid)
        owners.append({'dimension':dim,'uuid':uid,'registryId':rid,'root':[x,y,z],'sourcePose':obj[0] if obj else None,'kind':obj[1] if obj else None,'classificationReason':obj[2] if obj else None,'poseFootprintAvailable':bool(obj and db.execute('SELECT 1 FROM pose_cells WHERE pose=? LIMIT 1',(obj[0],)).fetchone()),'rootOutsideCopy':db.execute('SELECT 1 FROM fixed WHERE cx=? AND cz=?',(x>>4,z>>4)).fetchone() is None,'missingFootprintChunks':chunks})
    chunks=[];shards=set();missing_cache=[]
    for (dim,cx,cz),uids in sorted(needed.items()):
        cached=db.execute('SELECT region_path,region_hash,compression,length(payload) FROM old.chunk_cache WHERE dim=? AND x=? AND z=?',(dim,cx,cz)).fetchone()
        row={'dimension':dim,'x':cx,'z':cz,'owners':uids,'cached':bool(cached)}
        if cached:
            rel,region_hash,compression,size=cached;m.require(manifest['files'][rel]['sha256']==region_hash,'Dependency cached region belongs to another immutable source')
            row.update(sourceRegion=rel,sourceRegionSha256=region_hash,compression=compression,cachedCompressedBytes=size)
        else:missing_cache.append([dim,cx,cz])
        shard='data/bloodborne_dw_owner_shards/owners-%d.%d.nbt'%(cx,cz)
        if shard in manifest['files']:shards.add(shard)
        chunks.append(row)
    report={'schemaVersion':1,'status':'READ_ONLY_FINITE_DEPENDENCY_INVENTORY','sourceBinding':m.meta(db,'binding'),'target':m.meta(db,'target'),'originalChunks':db.execute('SELECT count(*) FROM fixed').fetchone()[0],'insideReferencedRetainedRealOwners':db.execute('SELECT count(*) FROM hits').fetchone()[0],'boundaryOwners':len(owners),'rootOutsideCopyOwners':sum(v['rootOutsideCopy'] for v in owners),'missingDependencyChunks':len(chunks),'missingDependencyShards':len(shards),'missingCachedChunks':missing_cache,'ownersWithoutExportedPoseFootprint':sum(not v['poseFootprintAvailable'] for v in owners),'owners':owners,'chunks':chunks,'shards':[{'path':p,'sha256':manifest['files'][p]['sha256']} for p in sorted(shards)],'selectionDomainUnchanged':True,'newChunkOwnersRecursed':False,'mapWrites':0,'databaseWrites':0,'seconds':time.monotonic()-started}
    m.atomic_json(out,report);progress('COMPLETE',boundaryOwners=len(owners),chunks=len(chunks),shards=len(shards),missingCachedChunks=len(missing_cache));print(json.dumps({k:report[k] for k in ('status','boundaryOwners','rootOutsideCopyOwners','missingDependencyChunks','missingDependencyShards','missingCachedChunks','ownersWithoutExportedPoseFootprint','seconds')}))
finally:db.close()
