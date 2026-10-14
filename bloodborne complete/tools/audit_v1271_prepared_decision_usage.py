"""Count prepared uint16 decisions read-only, including unused dictionary rows.

Only the already prepared section arrays are read. Original map chunks,
weighted selections, palette decoding and runtime authoring are not touched.
"""
import argparse, hashlib, json, sqlite3, time, traceback, zlib
from pathlib import Path
import numpy as np
from verify_v1271_final_readiness import ROOT,ordinary,read,sha,write

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--preparation',type=Path,default=ROOT/'build/v1271-city-preparation')
    p.add_argument('--report',type=Path,default=ROOT/'build/v1271-city-preparation/decision-frequency-proof.json');a=p.parse_args()
    out=ordinary(a.report);progress=out.with_suffix('.progress.json');plan=ordinary(a.preparation/'source-plan.sqlite');con=None
    began=time.monotonic()
    try:
        before=sha(plan);con=sqlite3.connect(f'file:{plan}?mode=ro',uri=True);con.execute('BEGIN')
        dictionary={i:json.loads(row) for i,row in con.execute('SELECT id,data FROM decisions')}
        summary=json.loads(con.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0])
        frequencies=np.zeros(max(dictionary)+1,dtype=np.int64);index_sha=hashlib.sha256();sections=0
        for dim,cx,cz,sy,packed in con.execute('SELECT dimension,cx,cz,sy,decision_indices FROM sections ORDER BY dimension,cx,cz,sy'):
            indices=np.frombuffer(zlib.decompress(packed),dtype='<u2');assert len(indices)==4096
            frequencies+=np.bincount(indices,minlength=len(frequencies));sections+=1
            # Full compressed byte stream and original section key are bound;
            # dictionary-only pose corrections do not change this index proof.
            index_sha.update(json.dumps([dim,cx,cz,sy],separators=(',',':')).encode('utf8')+b'\n')
            index_sha.update(len(packed).to_bytes(4,'little'));index_sha.update(packed)
            if sections%10000==0:write(progress,{'stage':'READ_ONLY_PREPARED_INDEX_COUNTS','sections':sections,'expectedSections':summary['sections'],'elapsedSeconds':round(time.monotonic()-began,2)})
        con.close();con=None
        assert sections==summary['sections'] and int(frequencies.sum())==summary['allPaletteCells']
        assert not [(i,int(n)) for i,n in enumerate(frequencies) if n and i not in dictionary]
        action_counts={action:sum(int(frequencies[i]) for i,d in dictionary.items() if d['action']==action) for action in summary['counts']}
        assert action_counts==summary['counts'],(action_counts,summary['counts'])
        assert sha(plan)==before,'Prepared database changed during independent count'
        result={'schema':'dreamwalker-v1271-prepared-decision-usage','status':'PASS_READ_ONLY_COMPLETE_PREPARED_DECISION_COUNTS','sourcePlanSha256AtAudit':before,
                'decisionIndexArraysSha256':index_sha.hexdigest(),'sections':sections,'paletteCells':int(frequencies.sum()),'dictionaryEntries':len(dictionary),
                'decisionCellCounts':{str(i):int(frequencies[i]) for i in dictionary},'actionCountsAtAudit':action_counts,
                'zeroCellHistoricalRemoveDecisionIds':[i for i,d in dictionary.items() if d['action']=='REMOVE' and frequencies[i]==0],
                'actualRemovedSourceCells':action_counts.get('REMOVE',0),'preparedDatabaseUnchanged':True,'originalWorldChunksRead':0,'weightedChoicesRecomputed':False,
                'jvmStarted':False,'elapsedSeconds':round(time.monotonic()-began,2)}
        write(out,result);write(progress,{'stage':'COMPLETE','sections':sections,'elapsedSeconds':result['elapsedSeconds']})
    except BaseException:
        write(out,{'schema':'dreamwalker-v1271-prepared-decision-usage','status':'FAIL','error':traceback.format_exc()});raise
    finally:
        if con is not None:con.close()

if __name__=='__main__':main()
