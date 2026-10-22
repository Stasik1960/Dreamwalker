"""Addressed existing auxiliary generation fields only; no world/DB writes."""
import pilot_dependencies as p
m=p.m
def main():
    findings=[]
    for variant in ('baseline','candidate'):
        for row in m.read(p.INVENTORY)['chunks']:
            cx,cz=row['x'],row['z'];path=m.ROOT/'research/pilot'/variant/('region/r.%d.%d.mca'%(cx>>5,cz>>5));old=p.read_slot(path,cx,cz)
            if old is None:continue
            try:p.flat_before(old)
            except RuntimeError as error:
                nbt=p.chunk_decode(old[0],old[1]);root=m.compound(nbt.root);findings.append({'variant':variant,'chunk':[cx,cz],'path':str(path),'regionSha256':m.sha(path),'payloadSha256':p.hashlib.sha256(old[1]).hexdigest(),'error':str(error),'status':root.get('Status',m.Tag(8,'')).value,'fieldKeys':sorted(root),'generationFields':{key:p.c._typed(m,root[key]) for key in ('blending_data','below_zero_retrogen','UpgradeData','CarvingMasks') if key in root},'blockEntityCount':len(p.be_tags(root))})
    m.atomic_json(p.REPAIR/'auxiliary-admission-difference.json',{'status':'READ_ONLY_ACTUAL_INPUTS','findings':findings,'mapWrites':0,'databaseWrites':0})
if __name__=='__main__':m.control_check();main()
