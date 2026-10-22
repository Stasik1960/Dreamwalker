"""One failed dimension ledger's exact immutable/committed/actual typed diff."""
import context_stage as c
import migrate as m
from pathlib import Path
def main():
    plan=m.ROOT/'research/native-grid-plan';out=plan/'context-extension/dimension-ledger-difference.json';db=c.attached_readonly(m,plan)
    try:
        binding=m.meta(db,'binding');manifest=m.read(binding['copyManifest']);rel='DIM-1/data/bloodborne_dw_composite_owners.dat';source=Path(binding['source'])/rel;actual=Path(m.meta(db,'target'))/rel;history=Path(c.receipt(m,db)['history']);old=m.read(history/'meta.json');row=db.execute('SELECT before_hash,after_hash,phase FROM stage1_files WHERE path=?',(rel,)).fetchone();source_hash=m.sha(source);m.require(source_hash==manifest['files'][rel]['sha256'],'Addressed immutable ledger source changed')
        expected=m.read_nbt(source);value=m.read_nbt(actual);before=m.compound(expected.root);after=m.compound(value.root);diff=[]
        for key in sorted(set(before)|set(after)):
            a=c._typed(m,before[key]) if key in before else None;b=c._typed(m,after[key]) if key in after else None
            if a!=b:diff.append({'key':key,'expected':a,'actual':b})
        result={'schemaVersion':1,'status':'READ_ONLY_EXACT_TYPED_DIFF','path':rel,'source':str(source),'sourceSha256':source_hash,'sourceManifestSha256':manifest['files'][rel]['sha256'],'actual':str(actual),'actualSha256':m.sha(actual),'stage1FileRow':list(row) if row else None,'oldPilotMetadataBinding':old.get('pilotMetadataBinding'),'expectedTyped':c._typed(m,expected.root),'actualTyped':c._typed(m,value.root),'differences':diff,'typedEquivalent':c._typed(m,expected.root)==c._typed(m,value.root),'sourceBinding':binding,'mapWrites':0,'databaseWrites':0};m.atomic_json(out,result);print(m.canonical({'path':str(out),'sha256':m.sha(out),'typedEquivalent':result['typedEquivalent']}))
    finally:db.close()
if __name__=='__main__':m.control_check();main()
