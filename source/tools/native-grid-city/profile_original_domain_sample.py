"""One bounded read-only timing sample; never an acceptance or world scan."""
import cProfile
import pstats
import sqlite3
import time
import migrate as m
import native_certificates as c

m.control_check()
db=sqlite3.connect((m.DEFAULT_PLAN/'plan.sqlite').as_uri()+'?mode=ro',uri=True)
try:
    db.execute('PRAGMA query_only=ON');c.attach_source_journal_original(m,db);db.execute('BEGIN')
    row=db.execute("SELECT data FROM source_local_retentions WHERE json_type(data,'$.sourceZone')='object' ORDER BY sequence LIMIT 1").fetchone()
    event=m.json.loads(row[0]);context=c.SourceVisualReservations(m,db)
    profile=cProfile.Profile();start=time.monotonic();count=0
    profile.enable()
    for domain in event['originalDomains'][:16]:
        if count and time.monotonic()-start>=20:break
        obj=domain['object']
        if domain.get('runtimeFootprintEnvelope'):context.__dict__.setdefault('_saved_envelopes',{})[obj['pose']]=domain['runtimeFootprintEnvelope']
        if domain['mechanism']=='source_original_snapshot':context.original_domain(obj['dim'],obj['uuid'])
        else:context.owner_domain(obj['dim'],obj['uuid'])
        count+=1
    profile.disable()
    m.require(db.total_changes==0,'Read-only profiling changed the plan')
    print(m.canonical({'kind':'READ_ONLY_BOUNDED_PROFILING_NOT_ACCEPTANCE','owners':count,'seconds':time.monotonic()-start,'sqliteChanges':0,'worldReads':0,'mapWrites':0}))
    pstats.Stats(profile).strip_dirs().sort_stats('cumtime').print_stats(20)
finally:db.close()
