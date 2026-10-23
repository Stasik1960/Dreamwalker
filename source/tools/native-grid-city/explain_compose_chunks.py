"""One bounded EXPLAIN-only check of current compose chunk lookups.

No SELECT data query, chunk/world read, ANALYZE, index creation or DB write.
"""
import argparse,hashlib,json,sqlite3
from pathlib import Path

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--plan',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    database=args.plan.resolve()/'plan.sqlite';output=args.out.resolve()
    if output.exists():raise RuntimeError('Keep existing diagnostics; choose a fresh --out')
    source=Path(__file__).resolve().parent
    queries=[
      ('native_function_conflicts_chunk','context_stage.py',235,'SELECT DISTINCT x,y,z FROM fragments WHERE dim=? AND x>>4=? AND z>>4=?',('.',57,4),'fragment_native_chunk'),
      ('preflight_fragment_chunk_inventory','migrate.py',356,'SELECT DISTINCT dim,x>>4,z>>4 FROM fragments ORDER BY dim,x>>4,z>>4',(), 'fragment_native_chunk'),
      ('compose_fragment_chunk_inventory','migrate.py',478,'SELECT DISTINCT dim,x>>4,z>>4 FROM fragments',(),'fragment_native_chunk'),
      ('bridge_per_chunk','migrate.py',779,'SELECT x,y,z,host_state,type_index FROM bridge_cells WHERE dim=? AND x>>4=? AND z>>4=?',('.',57,4),'bridge_chunk'),
      ('native_visual_pending','migrate.py',517,'SELECT dim,x,y,z FROM cell_queue WHERE processed=0 ORDER BY dim,(x>>4),(z>>4),y,x,z LIMIT 1024',(),'queue_work'),
      ('final_queue_iteration','migrate.py',555,'SELECT dim,x,y,z FROM cell_queue q WHERE NOT EXISTS(SELECT 1 FROM whole_cells w WHERE w.dim=q.dim AND w.x=q.x AND w.y=q.y AND w.z=q.z) ORDER BY dim,(x>>4),(z>>4),y,x,z',(),None),
      ('fragment_cell_point','migrate.py',560,'SELECT pose,dx,dy,dz FROM fragments WHERE dim=? AND x=? AND y=? AND z=? ORDER BY pose,dx,dy,dz',('.',912,64,64),None),
      ('bridge_cell_point','migrate.py',561,'SELECT 1 FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',('.',912,64,64),None),
    ]
    db=sqlite3.connect('file:'+database.as_posix()+'?mode=ro',uri=True)
    try:
        db.execute('PRAGMA query_only=ON');db.execute('PRAGMA cache_size=-2048');db.execute('PRAGMA temp_store=FILE');db.execute('BEGIN')
        schema=[{'type':t,'name':n,'table':table,'sql':sql} for t,n,table,sql in db.execute("SELECT type,name,tbl_name,sql FROM sqlite_master WHERE tbl_name IN ('fragments','bridge_cells','cell_queue','whole_cells') ORDER BY type,name")]
        indexes={row['name'] for row in schema if row['type']=='index'};rows=[]
        for name,filename,line,sql,params,index in queries:
            if sql not in (source/filename).read_text(encoding='utf-8'):raise RuntimeError('Query no longer matches its actual source: '+name)
            plans=[list(row) for row in db.execute('EXPLAIN QUERY PLAN '+sql,params)]
            item={'name':name,'source':filename,'line':line,'sql':sql,'parameters':params,'plan':plans}
            if index:
                if index not in indexes:raise RuntimeError('Required current index is absent: '+index)
                table={'fragment_native_chunk':'fragments','bridge_chunk':'bridge_cells','queue_work':'cell_queue'}[index]
                hinted=sql.replace('FROM '+table,'FROM '+table+' INDEXED BY '+index,1)
                item.update(hintedSql=hinted,hintedPlan=[list(row) for row in db.execute('EXPLAIN QUERY PLAN '+hinted,params)])
            if name in ('native_function_conflicts_chunk','bridge_per_chunk'):
                item['dimensionOnlySearch']=any('SEARCH 'in row[3] and '(dim=?)'in row[3] for row in plans)
            rows.append(item)
        report={'schemaVersion':1,'status':'EXPLAIN_ONLY_COMPLETED','database':str(database),'sqliteVersion':sqlite3.sqlite_version,'sourceHashes':{str(source/name):sha(source/name) for name in ('migrate.py','context_stage.py')},'schema':schema,'queries':rows,'badPerChunkChoices':[row['name'] for row in rows if row.get('dimensionOnlySearch')],'actualDataSelectsExecuted':0,'worldFilesRead':0,'persistentDatabaseWrites':0,'caveat':'Current promoted derived tables may be empty; no population or statistics were changed. Plans refer to the actual current schema and query predicates.'}
    finally:db.close()
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps({'status':report['status'],'badPerChunkChoices':report['badPerChunkChoices'],'out':str(output),'sha256':sha(output)},ensure_ascii=False))
if __name__=='__main__':main()
