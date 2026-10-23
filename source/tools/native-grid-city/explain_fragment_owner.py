"""EXPLAIN only two actual fragment-owner statements during the root writer."""
import argparse,hashlib,json,sqlite3
from pathlib import Path

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--plan',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    source=Path(__file__).resolve().parent/'migrate.py';sqls=['SELECT dim,x,y,z,uuid,\'functional_guest\' FROM fragments WHERE dim=? AND uuid=?','DELETE FROM fragments WHERE dim=? AND uuid=?'];text=source.read_text(encoding='utf-8')
    if any(sql not in text for sql in sqls):raise RuntimeError('Exact owner query differs from the current source')
    output=args.out.resolve()
    if output.exists():raise RuntimeError('Preserve previous evidence; choose fresh output')
    database=args.plan.resolve()/'plan.sqlite';db=sqlite3.connect('file:'+database.as_posix()+'?mode=ro',uri=True)
    try:
        db.execute('PRAGMA query_only=ON');db.execute('PRAGMA cache_size=-1024');db.execute('BEGIN')
        schema=[{'type':kind,'name':name,'sql':sql} for kind,name,sql in db.execute("SELECT type,name,sql FROM sqlite_master WHERE tbl_name='fragments' ORDER BY type,name")]
        rows=[{'sql':sql,'plan':[list(row) for row in db.execute('EXPLAIN QUERY PLAN '+sql,('.','[-1,-2,-3,-4]'))]} for sql in sqls]
        report={'schemaVersion':1,'status':'EXPLAIN_ONLY_COMPLETED','source':{'path':str(source),'sha256':sha(source)},'database':str(database),'schema':schema,'queries':rows,'dimensionOnlySearch':any('(dim=?)'in row[3] for result in rows for row in result['plan']),'actualDataSelectsExecuted':0,'deleteStatementsExecuted':0,'persistentDatabaseWrites':0,'worldFilesRead':0}
    finally:db.close()
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps({'status':report['status'],'dimensionOnlySearch':report['dimensionOnlySearch'],'out':str(output),'sha256':sha(output)}))
if __name__=='__main__':main()
