"""Verify final plan annotations against every exact native-offset coordinate.

Reads only; no original world load/write and no per-cell full-world collection.
"""
import collections,gzip,json,sqlite3,zipfile,zlib
import numpy as np
from v125_map_index import ROOT,MAP,PACK,token,sha
from v127_apply_full_map_poses import write
from world_io import compound,decode_nbt
def main():
    plan=ROOT/'build/v127-full-conversion-first.sqlite';db=sqlite3.connect(f'file:{plan.resolve()}?mode=ro',uri=True);summary=json.loads(db.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0]);assert summary['status']=='PASS_COMPLETE_SOURCE_PLAN'
    decisions={i:json.loads(s) for i,s in db.execute('SELECT id,data FROM decisions')};policy=json.loads((ROOT/'docs/v12.7/conversion/source-native-model-offset-policy.json').read_text(encoding='utf-8-sig'));assert policy['ownerSourceCells']==64 and policy['sourceHashes']==summary['sourceHashes'];checked=[]
    for row in policy['rows']:
        x,y,z=row['position'];dim=row['dimension'];section=db.execute('SELECT source_palette,source_indices,decision_indices FROM sections WHERE dimension=? AND cx=? AND cz=? AND sy=?',(dim,x>>4,z>>4,y>>4)).fetchone();assert section
        palette=json.loads(section[0]);index=((y&15)<<8)|((z&15)<<4)|(x&15);source=palette[int(np.frombuffer(zlib.decompress(section[1]),dtype='<u2')[index])][0];did=int(np.frombuffer(zlib.decompress(section[2]),dtype='<u2')[index]);d=decisions[did]
        assert source==row['sourceState']==d.get('sourceNativeModelOffsetState') and d['action']=='OWNER' and d['registryId'].startswith('bloodborne_dw:source_'),row
        checked.append({'dimension':dim,'position':row['position'],'sourceState':source,'targetRegistryId':d['registryId'],'targetProperties':d['properties'],'decisionId':did})
    contexts=collections.Counter();wholeObjects=0
    for (raw,) in db.execute('SELECT data FROM whole_objects'):
        row=json.loads(raw);d=row['decision'];marked=d.get('payload',{}).get('SourceCarrierContextVersion')
        if marked:
            assert marked==1 and d['registryId'] in {'bloodborne_dw:source_original_tree','bloodborne_dw:source_large_grille_gate'} and int(d['properties']['variant'])==0
            contexts[d['registryId']]+=1
        wholeObjects+=1
    assert contexts
    ranges=[{'dimension':d,'minimumSourceChunkX':x0,'maximumSourceChunkX':x1,'minimumSourceChunkZ':z0,'maximumSourceChunkZ':z1} for d,x0,x1,z0,z1 in db.execute('SELECT dimension,MIN(cx),MAX(cx),MIN(cz),MAX(cz) FROM chunks GROUP BY dimension')]
    with zipfile.ZipFile(MAP) as source:
        names=[n for n in source.namelist() if n.endswith('level.dat')];assert len(names)==1;level=compound(compound(decode_nbt(gzip.decompress(source.read(names[0]))).root)['Data']);border={key:level[key].value for key in ['BorderCenterX','BorderCenterZ','BorderSize','BorderSizeLerpTarget','BorderSizeLerpTime'] if key in level}
    db.close();report={'status':'PASS_FINAL_SOURCE_ANNOTATIONS_AND_CONTEXT_SCOPE','sourcePlanSha256':sha(plan),'sourceHashes':summary['sourceHashes'],'nativeOffsetSourceCells':len(checked),'nativeOffsetRows':checked,'contextWholeObjects':dict(contexts),'wholeObjects':wholeObjects,'sourceChunkExtents':ranges,'sourceWorldBorder':border,'occupiedCellHeightAndTicketProof':'Separate actual native CompositeRuntime.instance all reachable OWNER/WHOLE state proof, not model approximation','originalWorldWritten':False};write(ROOT/'docs/v12.7/conversion/final-source-annotations.json',report);print(token({k:v for k,v in report.items() if k!='nativeOffsetRows'}))
if __name__=='__main__':main()
