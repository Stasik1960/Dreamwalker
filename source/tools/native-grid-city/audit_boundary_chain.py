"""Read-only evidence for the two exact foreign-root satellite helpers."""
import json
from pathlib import Path
import migrate as m
import context_stage as c
plan=m.ROOT/'research/native-grid-plan';db=c.attached_readonly(m,plan)
try:
    dim='.';uid=m.canonical([-1920773844,24597428,-2084089085,-1717829492]);root=(254,36,-882)
    source=db.execute('SELECT pose,x,y,z,kind,reason FROM objects WHERE dim=? AND uuid=?',(dim,uid)).fetchone();m.require(source is not None,'Exact original foreign owner missing from global source input')
    pose,x,y,z,kind,reason=source;m.require((x,y,z)==root,'Exact foreign root moved in source inputs')
    allowed=set(db.execute('SELECT cx,cz FROM context_allowed_chunks'));target=Path(m.meta(db,'target'));region=target/'region/r.0.-2.mca'
    _,_,compression,payload=next(c._region_chunks(m,region,{(15,-56)}))
    current_root=None
    if compression is not None:
        value=m.compound(m.decode_nbt(payload,compressed={1:'gzip',2:'zlib',3:None}[compression]).root)
        current_root={'state':m.source_state(c._sections(m,value),root),'blockEntityAtRoot':[c._be_value(m,v) for v in value.get('block_entities',m.Tag(9,[],10)).value if tuple(m.compound(v)[key].value for key in ('x','y','z'))==root],'status':value.get('Status',m.Tag(8,'')).value,'dataVersion':value.get('DataVersion',m.Tag(3,0)).value}
    points=[]
    for dx,dy,dz in db.execute('SELECT dx,dy,dz FROM pose_cells WHERE pose=? ORDER BY dx,dy,dz',(pose,)):
        at=(x+dx,y+dy,z+dz)
        points.append({'position':list(at),'chunk':[at[0]>>4,at[2]>>4],'insideCopy':(at[0]>>4,at[2]>>4) in allowed,'storedContributions':[r[0] for r in db.execute('SELECT uuid FROM old.stored_contributions WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at))],'members':[dict(zip(('uuid','pose','ox','oy','oz'),r)) for r in db.execute('SELECT uuid,pose,ox,oy,oz FROM old.members WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at))]})
    report={'schemaVersion':1,'status':'READ_ONLY_BOUNDARY_EVIDENCE','sourceBinding':m.meta(db,'binding'),'owner':{'uuid':uid,'root':list(root),'pose':pose,'kind':kind,'reason':reason,'selectedCurrent':db.execute('SELECT count(*) FROM selected_objects WHERE dim=? AND uuid=?',(dim,uid)).fetchone()[0],'selectedStage1':db.execute('SELECT count(*) FROM stage1_selected_objects WHERE dim=? AND uuid=?',(dim,uid)).fetchone()[0],'storedOwner':db.execute('SELECT registry_id,x,y,z FROM old.stored_owners WHERE dim=? AND uuid=?',(dim,uid)).fetchone()},'rootChunkInsideCopy':(15,-56) in allowed,'actualRootChunkSlotAbsent':compression is None and payload is None,'actualRoot':current_root,'actualRootRegion':str(region),'actualRootRegionSha256':m.sha(region),'completeExportedFootprint':points,'helpers':[[256,36,-882],[256,36,-881]],'helperCurrentPlacements':[list(r) for r in db.execute('SELECT x,y,z,type_index FROM placements WHERE dim=? AND x=256 AND y=36 AND z IN (-882,-881)',(dim,))],'helperStage1Placements':[list(r) for r in db.execute('SELECT x,y,z,type_index FROM stage1_placements WHERE dim=? AND x=256 AND y=36 AND z IN (-882,-881)',(dim,))],'mapWrites':0,'databaseWrites':0}
    out=plan/'context-extension/boundary-chain-evidence.json';m.atomic_json(out,report);print(json.dumps(report))
finally:db.close()
