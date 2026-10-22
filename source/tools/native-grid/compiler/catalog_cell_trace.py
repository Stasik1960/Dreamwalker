"""Trace one already-authored cell to immutable source physics; no map reads."""
import argparse, hashlib, json, sqlite3, sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from export_cells import canonical,canonical_boxes,state_key

PROJECT=Path(__file__).resolve().parents[3]
ROOT=PROJECT.parent
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def read(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def ro(path):
    db=sqlite3.connect('file:'+Path(path).absolute().as_posix()+'?mode=ro',uri=True);db.row_factory=sqlite3.Row;return db
def digest(value):return hashlib.sha256(canonical(value).encode()).hexdigest()

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--position',type=int,nargs=3,default=[990,-59,140]);p.add_argument('--type-index',type=int,default=1713)
    p.add_argument('--out',type=Path,default=ROOT/'research/bridge-door-background-source-proof.json')
    a=p.parse_args();plan=ro(ROOT/'research/native-grid-plan/plan.sqlite');store=ro(ROOT/'research/fragments.sqlite')
    path=PROJECT/'src/architecture/resources/assets/bloodborne_dw/native_grid/catalog.json';catalog=read(path)
    placed=plan.execute("SELECT * FROM placements WHERE dim='.' AND x=? AND y=? AND z=?",a.position).fetchone()
    assert placed is not None and placed['type_index']==a.type_index,'Bound exact placement/type differs'
    composition=plan.execute('SELECT data FROM compositions WHERE key=?',(placed['composition'],)).fetchone();composition=json.loads(composition[0])
    bridge=plan.execute("SELECT * FROM bridge_cells WHERE dim='.' AND x=? AND y=? AND z=?",a.position).fetchone()
    assert bridge is not None and bridge['type_index']==a.type_index and composition.get('wrapper')=='NativeGridBase','Existing retained-owner bridge proof absent'
    sources=[];expected={'collision':[],'outline':[]}
    base=composition.get('baseState')
    assert base and base['registryId']=='minecraft:air','This exact door proof expects observed retained-carrier AIR background separation'
    native=store.execute('SELECT data FROM native WHERE state=?',(state_key(base),)).fetchone()
    assert native is not None,'Actual AIR physics export required; never substitute a guessed EMPTY_OPTICS/shape'
    native=json.loads(native[0])
    for field in expected:expected[field].extend(native[field])
    for field,kind in (('fragments','selected_static_physical_and_visual'),('substrateFragments','substrate_visual_only')):
        for item in composition.get(field,[]):
            row=store.execute('SELECT digest,data FROM fragments WHERE pose=? AND x=? AND y=? AND z=?',(item['pose'],*item['cell'])).fetchone();assert row,'Missing exact source fragment'
            data=json.loads(row['data']);pose=store.execute('SELECT source,proof FROM poses WHERE key=?',(item['pose'],)).fetchone()
            physics=[]
            for part in store.execute('SELECT x,y,z,data FROM fragments WHERE pose=?',(item['pose'],)):
                physical=json.loads(part['data'])
                if physical['collision'] or physical['outline']:physics.append({'cell':[part['x'],part['y'],part['z']],'collision':physical['collision'],'outline':physical['outline']})
            sources.append({'role':kind,'pose':item['pose'],'cell':item['cell'],'fragmentDigest':row['digest'],'sourcePose':json.loads(pose['source']),'sourcePoseProof':json.loads(pose['proof']),'quads':len(data['quads']),'collision':data['collision'],'outline':data['outline'],'recordSha256':digest(data),'posePhysicalFootprint':physics,'explanation':'This exact exported cell contains visual overhang only; the same pose has its physical footprint in the separately listed source cells' if not data['collision'] and not data['outline'] else 'Exact exported physical and visual fragment'})
            if field=='fragments':
                for shape in expected:expected[shape].extend(data[shape])
    assert not composition.get('substrateNativeFragments'),'Unexpected new native visual role in this exact single-cell diagnosis'
    for field in expected:expected[field]=canonical_boxes(expected[field])
    cell=catalog['cells'][a.type_index];actual={field:catalog['shapes'][cell[field]] for field in expected}
    match=expected==actual
    # Coordinate ownership is authoring provenance only, never catalogue input.
    owners=[dict(r) for r in plan.execute("SELECT f.uuid,f.pose,o.kind,o.reason,o.x AS rootX,o.y AS rootY,o.z AS rootZ FROM fragments f JOIN objects o ON o.dim=f.dim AND o.uuid=f.uuid WHERE f.dim='.' AND f.x=? AND f.y=? AND f.z=?",a.position)]
    retained=[dict(r) for r in plan.execute("SELECT uuid,reason FROM protected_cells WHERE dim='.' AND x=? AND y=? AND z=?",a.position)]
    report={'status':'PASS' if match else 'REAL_CATALOG_PHYSICS_MISMATCH','position':a.position,'typeIndex':a.type_index,'catalogSha256':sha(path),'meshesSha256':sha(path.parent/'meshes.bin'),'compositionKey':placed['composition'],'composition':composition,'compositionRecordSha256':digest(composition),'compiledCell':cell,'compiledShapeRefs':{field:cell[field] for field in expected},'compiledMeshQuadCount':catalog['meshes'][cell['mesh']]['quadCount'],'actualBasePhysics':{'state':base,'collision':native['collision'],'outline':native['outline'],'recordSha256':digest(native),'route':'Actual exported AIR physics; preserved carrier/door contributions intentionally remain outside NativeGridBase'},'contributingSourceFragments':sources,'selectedStaticOwnerProvenance':owners,'retainedProtectedOwnerProvenance':retained,'expectedShapeUnions':expected,'compiledShapes':actual,'emptyCollisionExact':not expected['collision'] and not actual['collision'],'emptyOutlineExact':not expected['outline'] and not actual['outline'],'preservedDoorExcludedFromBackground':bool(retained) and all(row['kind']!='preserve' for row in owners),'fragmentStoreMetadata':{r[0]:json.loads(r[1]) for r in store.execute('SELECT name,data FROM metadata')},'mapFilesRead':0,'mapWrites':0,'gameProcessesStarted':0,'scope':'One existing finite static background only; retained door physical shape is queried separately by existing CompositeBE/runtime and is not part of this catalog cell'}
    a.out.parent.mkdir(parents=True,exist_ok=True);a.out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':report['status'],'position':a.position,'typeIndex':a.type_index,'sources':len(sources),'mesh':cell['mesh'],'meshQuads':report['compiledMeshQuadCount'],'emptyCollisionExact':report['emptyCollisionExact'],'emptyOutlineExact':report['emptyOutlineExact'],'out':str(a.out)}))
    if not match:raise SystemExit(1)

if __name__=='__main__':main()
