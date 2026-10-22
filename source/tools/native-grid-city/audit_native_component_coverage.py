"""Exact consumed legacy-root/matching-part closure of the frozen plan."""
import collections,json,time
import migrate as m
import context_stage as c
started=time.monotonic();db=c.attached_readonly(m,m.DEFAULT_PLAN)
try:
    doc,overlays=m.legacy_native_catalog();offsets=collections.defaultdict(set);roots={}
    for recipe in doc['recipes']:
        for fragment in recipe['fragments']:offsets[fragment['pose']].add(tuple(-v for v in fragment['origin']))
    for dim,x,y,z,raw in db.execute("SELECT dim,x,y,z,source_state FROM placements WHERE source_state LIKE '%bloodborne_dw:static_architecture_%'"):
        state=json.loads(raw);index=int(state['registryId'].rsplit('_',1)[1])*128+int(state['properties']['part'])
        for fragment in doc['recipes'][index]['fragments']:
            if fragment['origin']==[0,0,0]:roots[(dim,x,y,z,fragment['pose'])]=state
    cache=collections.OrderedDict();gaps=[];components=[];tested=0
    def actual(dim,at):
        token=(dim,at[0]>>4,at[2]>>4)
        if token not in cache:
            source=c._original_chunk(m,db,*token);cache[token]=c._sections(m,source)
            if len(cache)>4:cache.popitem(last=False)
        return m.source_state(cache[token],at)
    for (dim,x,y,z,pose),root_state in sorted(roots.items()):
        uid=m.synthetic_uuid(dim,(x,y,z),pose);activated=bool(db.execute('SELECT 1 FROM activated_native WHERE dim=? AND uuid=?',(dim,uid)).fetchone());whole=bool(db.execute("SELECT 1 FROM selected_objects WHERE dim=? AND uuid=? AND kind='whole'",(dim,uid)).fetchone());local=[]
        for dx,dy,dz in sorted(offsets[pose]):
            at=(x+dx,y+dy,z+dz);tested+=1
            if db.execute('SELECT 1 FROM placements WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at)).fetchone():continue
            state=actual(dim,at);index=None
            if state['registryId'].startswith('bloodborne_dw:static_architecture_'):index=int(state['registryId'].rsplit('_',1)[1])*128+int(state['properties']['part'])
            elif state['registryId'] in overlays:index=overlays[state['registryId']]
            if index is not None and any(f['pose']==pose and f['origin']==[-dx,-dy,-dz] for f in doc['recipes'][index]['fragments']):local.append({'position':list(at),'sourceState':state})
        row={'dimension':dim,'root':[x,y,z],'canonicalPose':pose,'syntheticUuid':uid,'activated':activated,'selectedWhole':whole,'remainingMatchingLegacyParts':local};components.append(row)
        if local or not(activated or whole):gaps.append(row)
    report={'schemaVersion':1,'status':'PASS' if not gaps else 'BLOCKED_COMPONENT_GAPS','sourceBinding':m.meta(db,'binding'),'compositionGeneration':m.meta(db,'compositionGeneration'),'placements':db.execute('SELECT count(*) FROM placements').fetchone()[0],'rootComponentsConsumed':len(components),'finiteOffsetsChecked':tested,'components':components,'gaps':gaps,'worldFilesRead':0,'databaseWrites':0,'mapWrites':0,'seconds':time.monotonic()-started}
    path=m.DEFAULT_PLAN/'context-extension/native-component-coverage.json';m.atomic_json(path,report);print(m.canonical({'status':report['status'],'components':len(components),'gaps':len(gaps),'finiteOffsetsChecked':tested,'seconds':report['seconds']}))
finally:db.close()
