"""Report committed pilot evidence without reading any world/chunk again."""
import collections,importlib.util,json,sqlite3
from pathlib import Path
spec=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('migrate.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
m.control_check();db=sqlite3.connect('file:'+str(m.DEFAULT_PLAN/'plan.sqlite').replace('\\','/')+'?mode=ro',uri=True)
try:
    metadata={key:json.loads(value) for key,value in db.execute('SELECT key,value FROM meta')}
    m.require(metadata.get('offlineApplied'),'The offline writer has not completed')
    totals=collections.defaultdict(collections.Counter);uncommitted=[]
    for path,before,after,phase,kind,encoded in db.execute('SELECT path,before_hash,after_hash,phase,kind,stats FROM files'):
        if phase!='committed':uncommitted.append(path)
        counter=totals[kind];counter['journaledFiles']+=1;counter['byteChangedFiles']+=int(before!=after)
        for key,value in json.loads(encoded or '{}').items():
            if type(value)is int:counter[key]+=value
    m.require(not uncommitted,'A file journal remains incomplete')
    scope=metadata.get('scope');selected=dict(db.execute('SELECT kind,count(*) FROM selected_objects GROUP BY kind'))
    regions=[{'path':p,'phase':phase,'beforeSha256':before,'afterSha256':after} for p,phase,before,after in db.execute('SELECT path,phase,before_hash,after_hash FROM regions ORDER BY path')]
    bridge_hosts=[{'registryId':json.loads(host)['registryId'],'retainedResident':bool(resident),'cells':count} for host,resident,count in db.execute('SELECT host_state,retained_resident,count(*) FROM bridge_cells GROUP BY host_state,retained_resident')]
    collapsed=collections.Counter()
    for row in bridge_hosts:collapsed[(row['registryId'],row['retainedResident'])]+=row['cells']
    report={'schemaVersion':1,'status':'OFFLINE_APPLIED_RELIGHT_PENDING' if not metadata.get('relightComplete') else 'RELIGHT_COMPLETE','worldReads':0,'sourceCacheReads':0,
            'evidence':'Committed disk plan and atomic before/after file journals only; not a new geometry or gameplay pass.',
            'scope':scope,'target':metadata['target'],'runtime':metadata['runtime'],'catalog':metadata['catalog'],
            'selectedUniqueOwners':selected,'cleanupUniqueOwners':db.execute('SELECT count(*) FROM cleanup_owners').fetchone()[0],
            'committedCellPlacements':db.execute('SELECT count(*) FROM placements').fetchone()[0],'wholeRoleCells':db.execute('SELECT count(*) FROM whole_cells').fetchone()[0],
            'existingRetainedBridgeCells':db.execute('SELECT count(*) FROM bridge_cells').fetchone()[0],
            'bridgeHosts':[{'registryId':key[0],'retainedResident':key[1],'cells':value} for key,value in sorted(collapsed.items())],
            'fileJournalTotals':{kind:dict(values) for kind,values in totals.items()},'regions':regions,
            'relightByStatus':dict(db.execute('SELECT status,count(*) FROM relight GROUP BY status')),
            'localIssues':[{'kind':kind,'count':count} for kind,count in db.execute('SELECT kind,count(*) FROM issues GROUP BY kind')],
            'limitations':'removedOwners in shard journals counts shard owner-table entries, which can repeat one UUID across shards. The writer did not persist a separate per-BlockEntity deletion counter.'}
    if scope:
        xmin,xmax,zmin,zmax=scope['rootBounds'];report['preservedRootsInCoreByReason']=[{'kind':kind,'reason':reason,'objects':count} for kind,reason,count in db.execute("SELECT kind,reason,count(*) FROM objects WHERE dim=? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ? AND kind='preserve' GROUP BY kind,reason",(scope['dimension'],xmin,xmax,zmin,zmax))]
    m.atomic_json(m.DEFAULT_PLAN/'applied-journal-report.json',report);print(json.dumps(report,indent=2))
finally:db.close()
