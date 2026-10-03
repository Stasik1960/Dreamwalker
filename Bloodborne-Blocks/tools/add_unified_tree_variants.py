"""Plan or apply bush variants for the existing logical ``o_c001`` family.

The input JSON is a list (or ``{"entries": [...]}``) of selected historical
states.  Each entry supplies ``old_id``, ``old_state``, ``mesh`` (a mesh
object), ``geometry``, ``contract`` and ``footprint`` for its north pose.
The helper emits all four baked facings and never creates a new registry ID.
It is deliberately plan-only unless ``--apply`` is supplied.
"""
from __future__ import annotations
import argparse, copy, gzip, hashlib, json, math, zipfile
from pathlib import Path

FACINGS=("north","east","south","west")
ROOT=Path(__file__).resolve().parents[1]
LOGICAL=ROOT/"src/main/resources/bloodborne_blocks/logical"
CITY=ROOT/"src/main/resources/bloodborne_blocks/city"
HISTORICAL_JAR=ROOT.parent/"releases/Bloodborne-Blocks/bloodborne-blocks-2.0.1-mc1.20.1.jar"

def key(props): return ",".join(f"{name}={value}" for name,value in sorted(props.items()))
def read(path):
    if path.suffix==".gz":
        with gzip.open(path,"rt",encoding="utf8") as stream:return json.load(stream)
    return json.loads(path.read_text(encoding="utf8"))
def write(path,value):
    if path.suffix==".gz":path.write_bytes(gzip.compress(json.dumps(value,separators=(",",":"),ensure_ascii=False).encode(),mtime=0));return
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2,sort_keys=True)+"\n",encoding="utf8")
def parse_state(value): return dict(part.split("=",1) for part in value.split(",") if "=" in part)
def variant(entry):
    token=entry.get("variant") or hashlib.sha256((entry["old_id"]+"["+entry["old_state"]+"]").encode()).hexdigest()[:12]
    result="bush_"+token.removeprefix("bush_")
    if not result.replace("_","").isalnum():raise ValueError("invalid bush variant")
    return result
def turn_point(x,z,turns):
    for _ in range(turns%4):x,z=-z,x
    return x,z
def rotate_mesh(mesh,turns):
    result=copy.deepcopy(mesh)
    for polygon in result["polygons"]:
        for vertex in polygon["vertices"]:
            x,z=turn_point(vertex[0]-.5,vertex[2]-.5,turns);vertex[0],vertex[2]=x+.5,z+.5
    return result
def bounds(mesh):
    vertices=[vertex for polygon in mesh["polygons"] for vertex in polygon["vertices"]]
    if not vertices:raise ValueError("bush mesh has no polygons")
    return [min(vertex[i] for vertex in vertices) for i in range(3)]+[max(vertex[i] for vertex in vertices) for i in range(3)]
def rotate_cells(geometry,turns):
    result=copy.deepcopy(geometry);cells=result.get("cells",{})
    def box_turn(box):
        corners=[]
        for x in (box[0],box[3]):
            for z in (box[2],box[5]):
                rx,rz=turn_point(x-.5,z-.5,turns);corners.append((rx+.5,rz+.5))
        return [min(v[0]for v in corners),box[1],min(v[1]for v in corners),max(v[0]for v in corners),box[4],max(v[1]for v in corners)]
    rotated={}
    for name,cell in cells.items():
        x,y,z=(int(part) for part in name.split(","));x,z=turn_point(x,z,turns)
        for field in ('collision','outline','placement'):
            if field in cell:cell[field]=[box_turn(box)for box in cell[field]]
        rotated[f"{x},{y},{z}"]=cell
    if "0,0,0" not in rotated:raise ValueError("bush geometry must retain root cell 0,0,0")
    if any(len(cell.get("collision",[]))>4 for cell in rotated.values()):raise ValueError("bush geometry collision exceeds four boxes per cell")
    result["cells"]=rotated;result["anchor"]=[0,0,0];result["render_offset"]=[0,0,0]
    if 'globalOutline'in result:result['globalOutline']=box_turn(result['globalOutline'])
    return result
def world_boxes(geometry):
    boxes=[]
    for name,cell in geometry["cells"].items():
        x,y,z=(int(part) for part in name.split(","))
        boxes += [[box[0]+x,box[1]+y,box[2]+z,box[3]+x,box[4]+y,box[5]+z] for box in cell.get("collision",[])]
    return boxes
def compose_asset_e():
    """Compose reviewed asset_e fragments directly from the immutable 2.0.1 JAR."""
    archive=read(ROOT/"docs/pre-production-source-mapping.json.gz")
    parts=archive["v2\\migration.json"]["asset_e"]["states"]["assembled=false,facing=north"]
    if not HISTORICAL_JAR.is_file():raise ValueError("missing historical 2.0.1 JAR")
    needed={part["id"] for part in parts}
    with zipfile.ZipFile(HISTORICAL_JAR) as jar:
        with jar.open("bloodborne_blocks/v2/meshes.json.gz") as raw, gzip.GzipFile(fileobj=raw) as packed:
            # JSON decoder reads the archive once; immediately discard unrelated mesh entries.
            historical={ident:value for ident,value in json.load(packed).items() if ident in needed}
    if historical.keys()!=needed:raise ValueError("historical asset_e mesh payload missing")
    polygons=[];recipe=[];cells={(0,0,0)}
    for part in parts:
        mesh=rotate_mesh(historical[part["id"]],FACINGS.index(part["properties"]["facing"]));offset=part["offset"];recipe.append({"id":part["id"],"properties":part["properties"],"offset":offset})
        for polygon in mesh["polygons"]:
            clone=copy.deepcopy(polygon)
            for vertex in clone["vertices"]:vertex[0]+=offset[0];vertex[1]+=offset[1];vertex[2]+=offset[2]
            polygons.append(clone)
        # Historical art has no single current city owner; use a soft envelope only.
        for vertex in [vertex for polygon in mesh["polygons"] for vertex in polygon["vertices"]]:cells.add((math.floor(vertex[0]+offset[0]),math.floor(vertex[1]+offset[1]),math.floor(vertex[2]+offset[2])))
    # Historical asset roots were not floor anchors. New bush placement must
    # obey the existing tree family's FLOOR contract, without helpers below it.
    lift=max(0,-min(v[1] for p in polygons for v in p['vertices']))
    if lift:
        for polygon in polygons:
            for vertex in polygon['vertices']:vertex[1]+=lift
        cells={(0,0,0)}|{tuple(math.floor(vertex[i])for i in range(3))for polygon in polygons for vertex in polygon['vertices']}
    minx,miny,minz=(min(point[i] for point in cells) for i in range(3));maxx,maxy,maxz=(max(point[i] for point in cells) for i in range(3))
    # Bushes intentionally use soft no-collision cells; the outline retains the composed envelope.
    shape={"cells":{f"{x},{y},{z}":{"collision":[],"outline":[[0,0,0,1,1,1]]} for x,y,z in sorted(cells)},"globalOutline":[minx,miny,minz,maxx+1,maxy+1,maxz+1]}
    return {"old_id":"asset_e","old_state":"assembled=false,facing=north","variant":"asset_e","mesh":{"polygons":polygons},"geometry":shape,"contract":{},"footprint":{}},recipe
def build(definitions,geometry,meshes,contracts,footprints,entries):
    result=[copy.deepcopy(value) for value in (definitions,geometry,meshes,contracts,footprints)]
    defs,geo,mesh_data,contract_data,physical=result
    definition=next(block for block in defs["blocks"] if block["id"]=="o_c001")
    family=next(row for row in contract_data["families"] if row["id"]=="o_c001")
    state_geo=geo["blocks"]["o_c001"]["states"];state_physical=physical["families"]["o_c001"]
    added=[];migration={"schemaVersion":1,"rootOffset":[0,0,0],"states":{}}
    for entry in entries:
        required={"old_id","old_state","mesh","geometry","contract","footprint"}
        if missing:=required-entry.keys():raise ValueError("missing bush input fields: "+",".join(sorted(missing)))
        name=variant(entry)
        if name in definition["properties"]["variant"]:
            for state in [state for state in definition["states"] if f"variant={name}" in state]:
                definition["states"].pop(state);definition["models"].pop(state);state_geo.pop(state,None);family["states"].pop(state,None);state_physical.pop(state,None)
        else:definition["properties"]["variant"].append(name)
        source_facing=parse_state(entry["old_state"]).get("facing","north")
        if source_facing not in FACINGS:raise ValueError("invalid old facing")
        for facing in FACINGS:
            turns=(FACINGS.index(facing)-FACINGS.index(source_facing))%4
            mesh=rotate_mesh(entry["mesh"],turns);mesh_id="bush_"+hashlib.sha256(json.dumps(mesh,sort_keys=True,separators=(",",":")).encode()).hexdigest()[:16]
            mesh_data[mesh_id]=mesh;state=key({"facing":facing,"variant":name,"visual":"base"})
            shape=rotate_cells(entry["geometry"],turns);cells=[[int(p) for p in value.split(",")] for value in shape["cells"]]
            contract=copy.deepcopy(entry["contract"]);contract["rotation"]=FACINGS.index(facing)*90;contract["render_mesh"]={"id":mesh_id,"bounds":bounds(mesh),"offset":[0,0,0]};contract["collision_footprint"]={"boxes":world_boxes(shape)};contract["selection_footprint"]={"boxes":[shape.get("globalOutline",bounds(mesh))]};contract["interaction_footprint"]={"cells":cells};contract["migration_source_pattern"]=[]
            for visual in ("base","alt"):
                state=key({"facing":facing,"variant":name,"visual":visual});definition["states"][state]=[0,0,0];definition["models"][state]=mesh_id;state_geo[state]=copy.deepcopy(shape);family["states"][state]=copy.deepcopy(contract);state_physical[state]={"boxes":world_boxes(shape),"cells":cells}
                definition.setdefault('visual_models',{})[state]=f'bloodborne_blocks:block/logical/o_c001/{visual}/facing_{facing}_variant_{name}'
        migration["states"][f"{entry['old_id']}[{entry['old_state']}]"]={"id":"bloodborne_blocks:o_c001","properties":{"facing":source_facing,"variant":name,"visual":"base"},"rootOffset":[0,0,0]};added.append(name)
    used={mid for block in defs['blocks']for mid in block.get('models',{}).values()}
    for mid in list(mesh_data):
        if mid.startswith('bush_')and mid not in used:del mesh_data[mid]
    return result,migration,added
def write_assets(definition, meshes, variants):
    assets=ROOT/"src/main/resources/assets/bloodborne_blocks"; blockstate=read(assets/"blockstates/o_c001.json")
    for state,mesh_id in definition["models"].items():
        if not any(f"variant={variant}," in state or f",variant={variant}," in state for variant in variants):continue
        props=parse_state(state);path=definition['visual_models'][state];blockstate["variants"][state]={"model":path}
        textures=sorted({polygon["texture"] for polygon in meshes[mesh_id]["polygons"]});slots={texture:f"#t{index}" for index,texture in enumerate(textures)}
        model={"parent":"minecraft:block/block","bloodborne_mesh":mesh_id,"bloodborne_texture_slots":slots,"textures":{"particle":textures[0],**{f"t{index}":texture for index,texture in enumerate(textures)}}}
        if props['visual']=='alt':model={'parent':path.replace('/alt/','/base/')}
        target=assets/"models"/(path.split(":",1)[1]+".json");target.parent.mkdir(parents=True,exist_ok=True);write(target,model)
    write(assets/"blockstates/o_c001.json",blockstate)
def main():
    parser=argparse.ArgumentParser();parser.add_argument("--source",type=Path);parser.add_argument("--asset-e",action="store_true");parser.add_argument("--plan",type=Path,default=Path("build/unified-tree-variants-plan.json"));parser.add_argument("--recipe",type=Path,default=Path("build/unified-bush-recipe.json"));parser.add_argument("--apply",action="store_true");args=parser.parse_args()
    if args.asset_e==bool(args.source):parser.error("select exactly one of --source or --asset-e")
    recipe=[]
    if args.asset_e:entry,recipe=compose_asset_e();entries=[entry]
    else:
        source=read(args.source);entries=source.get("entries",source) if isinstance(source,dict) else source
    definitions=read(LOGICAL/"definitions.json");geometry=read(LOGICAL/"geometry.json");meshes=read(LOGICAL/"meshes.json.gz");contracts=read(LOGICAL/"contracts-v2.json");physical=read(LOGICAL/"physical-footprints.json")
    output,migration,added=build(definitions,geometry,meshes,contracts,physical,entries);args.plan.parent.mkdir(parents=True,exist_ok=True);write(args.plan,{"variants":added,"migration":migration});write(args.recipe,{"asset":"asset_e" if args.asset_e else "explicit","fragments":recipe})
    if args.apply:
        for name,value in zip(("definitions.json","geometry.json","meshes.json.gz","contracts-v2.json","physical-footprints.json"),output):write(LOGICAL/name,value)
        write_assets(next(block for block in output[0]["blocks"] if block["id"]=="o_c001"),output[2],added)
        from normalize_support_contracts import run
        run(LOGICAL,apply=True)
    print(json.dumps({"variants":added,"mapped":len(migration["states"])}))
if __name__=="__main__":main()
