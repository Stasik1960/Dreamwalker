"""Choose one actual stored sculpture from the saved discovery, no world scan."""
import collections,importlib.util,json
from pathlib import Path
spec=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('migrate.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
m.control_check();db=m.connect(m.DEFAULT_PLAN);checked=0;reasons=collections.Counter();winner=None;cache=collections.OrderedDict()
rows=db.execute("SELECT o.dim,o.uuid,o.pose,o.x,o.y,o.z FROM objects o JOIN old.stored_owners s ON s.dim=o.dim AND s.uuid=o.uuid WHERE o.kind='whole' ORDER BY abs(o.x-960)+abs(o.z-110),o.dim,o.x,o.y,o.z").fetchall()
db.execute('CREATE TEMP TABLE whole_actual_cells(dim TEXT,uuid TEXT,x INTEGER,y INTEGER,z INTEGER,PRIMARY KEY(dim,uuid,x,y,z)) WITHOUT ROWID')
for table in ('stored_contributions','members'):
    # Old tables have position-first indexes and no UUID index. Scan them once
    # with their rows outermost, never once per candidate sculpture.
    db.execute('INSERT OR IGNORE INTO whole_actual_cells SELECT c.dim,c.uuid,c.x,c.y,c.z FROM old.'+table+" c CROSS JOIN objects o ON o.dim=c.dim AND o.uuid=c.uuid WHERE o.kind='whole'")
for dim,uid,pose,x,y,z in rows:
    checked+=1;cells={(x+dx,y+dy,z+dz) for dx,dy,dz in db.execute('SELECT dx,dy,dz FROM pose_cells WHERE pose=?',(pose,))}
    cells.update(db.execute('SELECT x,y,z FROM whole_actual_cells WHERE dim=? AND uuid=?',(dim,uid)))
    if not cells:reasons['missing_footprint']+=1;continue
    foreign=False
    for at in cells:
        if any(db.execute('SELECT 1 FROM old.'+table+' WHERE dim=? AND x=? AND y=? AND z=? AND uuid!=? LIMIT 1',(dim,*at,uid)).fetchone() for table in ('stored_contributions','members')):foreign=True;break
    if foreign:reasons['independent_owner_in_actual_footprint']+=1;continue
    # Inspect only the already cached chunks of this finite potential witness.
    for at in cells:
        token=(dim,at[0]>>4,at[2]>>4)
        if token not in cache:
            nbt,_,_=m.get_source_chunk(db,*token);root=m.compound(nbt.root)
            cache[token]=({m.compound(s)['Y'].value:m.section_blocks(s) for s in root.get('sections',m.Tag(9,[],10)).value},
                          {tuple(m.compound(t)[key].value for key in ('x','y','z')):m.compound(t) for t in root.get('block_entities',m.Tag(9,[],10)).value})
            if len(cache)>4:cache.popitem(last=False)
        sections,bes=cache[token];state=m.source_state(sections,at);be=bes.get(at)
        if state['registryId'] in ('minecraft:air','minecraft:cave_air','minecraft:void_air'):continue
        if be is None or not m.be_owners(be) or any(m.owner_uuid(m.compound(item)['owner'])!=uid for item in m.be_owners(be)):
            foreign=True;break
        if 'resident' in be and m.owner_uuid(be['resident'])!=uid:foreign=True;break
    if foreign:reasons['independent_native_backing_or_foreign_be']+=1;continue
    pdata=json.loads(db.execute('SELECT data FROM poses WHERE key=?',(pose,)).fetchone()[0])
    winner={'dimension':dim,'uuid':uid,'root':[x,y,z],'pose':pose,'registryId':pdata['registryId'],'kind':'whole','cells':[list(c) for c in sorted(cells)],'chunks':[{'dimension':dim,'x':cx,'z':cz} for cx,cz in sorted({(a>>4,c>>4) for a,b,c in cells})]};break
out=m.ROOT/'research'/'function-qa'/'isolated-whole-selection.json';m.atomic_json(out,{'schemaVersion':1,'status':'PASS' if winner else 'NO_ISOLATED_ACTUAL_INSTANCE','actualStoredWholeCandidates':len(rows),'checked':checked,'rejectedByReason':dict(reasons),'winner':winner,'worldReads':0,'graphClosure':False});print(out)
