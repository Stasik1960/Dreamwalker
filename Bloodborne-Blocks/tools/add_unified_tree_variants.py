"""Plan or apply the dry bush as a logical construction separate from ``o_c001``.

The input JSON is a list (or ``{"entries": [...]}``) of selected historical
states.  Each entry supplies ``old_id``, ``old_state``, ``mesh`` (a mesh
object), ``geometry``, ``contract`` and ``footprint`` for its north pose.
The helper emits all four baked facings for ``o_dry_bush``.  ``o_c001`` keeps
only tree variants, so selecting a construction never selects a tree or bush
through the same variant property.
It is deliberately plan-only unless ``--apply`` is supplied.
"""
from __future__ import annotations
import argparse, copy, gzip, hashlib, json, math, zipfile
from pathlib import Path

FACINGS=("north","east","south","west")
TREE_ID="o_c001"
BUSH_ID="o_dry_bush"
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
def namespaced(ident): return ident if ":" in ident else f"bloodborne_blocks:{ident}"
def is_bush_state(state):
    return parse_state(state).get("variant","").startswith("bush_")

def bush_definition(tree):
    result=copy.deepcopy(tree)
    result["id"]=BUSH_ID
    result["semantic"]="bush"
    result["properties"]={name:copy.deepcopy(values) for name,values in tree["properties"].items() if name!="variant"}
    result["default"]={name:value for name,value in tree.get("default",{}).items() if name!="variant"}
    result["default"].update({"facing":"north","visual":"base"})
    if "placement_properties" in result:
        result["placement_properties"]={name:value for name,value in result["placement_properties"].items() if name!="variant"}
    result["states"]={}
    result["models"]={}
    result["visual_models"]={}
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
    return {"old_id":"asset_e","old_state":"assembled=false,facing=north","mesh":{"polygons":polygons},"geometry":shape,"contract":{},"footprint":{}},recipe
def build(definitions,geometry,meshes,contracts,footprints,entries):
    result=[copy.deepcopy(value) for value in (definitions,geometry,meshes,contracts,footprints)]
    defs,geo,mesh_data,contract_data,physical=result
    tree=next(block for block in defs["blocks"] if block["id"]==TREE_ID)
    tree_family=next(row for row in contract_data["families"] if row["id"]==TREE_ID)
    tree_geo=geo["blocks"][TREE_ID]["states"];tree_physical=physical["families"][TREE_ID]
    # Rebuild this generated ID on every run, but keep all non-bush tree data.
    defs["blocks"][:]=[block for block in defs["blocks"] if block["id"]!=BUSH_ID]
    geo["blocks"].pop(BUSH_ID,None);physical["families"].pop(BUSH_ID,None)
    contract_data["families"][:]=[row for row in contract_data["families"] if row["id"]!=BUSH_ID]
    bush=bush_definition(tree);defs["blocks"].append(bush)
    bush_geo={"states":{}};geo["blocks"][BUSH_ID]=bush_geo
    bush_family={name:copy.deepcopy(value) for name,value in tree_family.items() if name not in {"states","compiler_owner","review_source_patterns","support_groups","review_ids"}}
    bush_family.update(id=BUSH_ID,states={},review_id="DRY_BUSH",migration_disabled=True,
        collision_policy="NONE",selection_policy="SIMPLE_BOX",
        collision_justification="Dry bush foliage has no physical collision; selection retains its own outline.")
    contract_data["families"].append(bush_family)
    bush_physical={};physical["families"][BUSH_ID]=bush_physical
    removed_states=[];migration={"schemaVersion":1,"rootOffset":[0,0,0],"states":{}}
    for state in list(tree["states"]):
        if not is_bush_state(state):continue
        props=parse_state(state);target=key({"facing":props["facing"],"visual":props["visual"]})
        bush["states"][target]=tree["states"].pop(state)
        bush["models"][target]=tree["models"].pop(state)
        bush_geo["states"][target]=tree_geo.pop(state)
        bush_family["states"][target]=tree_family["states"].pop(state)
        bush_physical[target]=tree_physical.pop(state)
        visual=tree.get("visual_models",{}).pop(state,None)
        bush["visual_models"][target]=f'bloodborne_blocks:block/logical/{BUSH_ID}/{props["visual"]}/facing_{props["facing"]}'
        migration["states"][f"{namespaced(TREE_ID)}[{state}]"]={"id":f"bloodborne_blocks:{BUSH_ID}","properties":{"facing":props["facing"],"visual":props["visual"]},"rootOffset":[0,0,0]}
        removed_states.append((state,visual))
    tree["properties"]["variant"]=[name for name in tree["properties"].get("variant",[]) if not name.startswith("bush_")]
    if not tree.get("visual_models"):tree.pop("visual_models",None)
    canonical_tree=tree.get("default",{}).get("variant")
    if canonical_tree:
        tree.setdefault("placement_properties",{})["variant"]=canonical_tree
    for entry in entries:
        required={"old_id","old_state","mesh","geometry","contract","footprint"}
        if missing:=required-entry.keys():raise ValueError("missing bush input fields: "+",".join(sorted(missing)))
        source_facing=parse_state(entry["old_state"]).get("facing","north")
        if source_facing not in FACINGS:raise ValueError("invalid old facing")
        # A frozen mixed state already has reviewed mesh, geometry, contract
        # and physical data.  Move it intact instead of replacing city art
        # with a freshly composed source entry.
        if bush["states"]:
            migration["states"][f"{namespaced(entry['old_id'])}[{entry['old_state']}]"]={"id":f"bloodborne_blocks:{BUSH_ID}","properties":{"facing":source_facing,"visual":"base"},"rootOffset":[0,0,0]}
            continue
        for facing in FACINGS:
            turns=(FACINGS.index(facing)-FACINGS.index(source_facing))%4
            mesh=rotate_mesh(entry["mesh"],turns);mesh_id="bush_"+hashlib.sha256(json.dumps(mesh,sort_keys=True,separators=(",",":")).encode()).hexdigest()[:16]
            mesh_data[mesh_id]=mesh
            shape=rotate_cells(entry["geometry"],turns);cells=[[int(p) for p in value.split(",")] for value in shape["cells"]]
            contract=copy.deepcopy(entry["contract"]);contract["rotation"]=FACINGS.index(facing)*90;contract["render_mesh"]={"id":mesh_id,"bounds":bounds(mesh),"offset":[0,0,0]};contract["collision_footprint"]={"boxes":world_boxes(shape)};contract["selection_footprint"]={"boxes":[shape.get("globalOutline",bounds(mesh))]};contract["interaction_footprint"]={"cells":cells};contract["migration_source_pattern"]=[]
            for visual in ("base","alt"):
                state=key({"facing":facing,"visual":visual});bush["states"][state]=[0,0,0];bush["models"][state]=mesh_id;bush_geo["states"][state]=copy.deepcopy(shape);bush_family["states"][state]=copy.deepcopy(contract);bush_physical[state]={"boxes":world_boxes(shape),"cells":cells}
                bush["visual_models"][state]=f'bloodborne_blocks:block/logical/{BUSH_ID}/{visual}/facing_{facing}'
        migration["states"][f"{namespaced(entry['old_id'])}[{entry['old_state']}]"]={"id":f"bloodborne_blocks:{BUSH_ID}","properties":{"facing":source_facing,"visual":"base"},"rootOffset":[0,0,0]}
    used={mid for block in defs['blocks']for mid in block.get('models',{}).values()}
    for mid in list(mesh_data):
        if mid.startswith('bush_')and mid not in used:del mesh_data[mid]
    return result,migration,[BUSH_ID],removed_states

def write_metadata(tree,bush):
    """Add the new public construction without rewriting frozen review evidence."""
    assets=ROOT/"src/main/resources/assets/bloodborne_blocks"
    state=key(bush["default"])
    item=copy.deepcopy(read(assets/f"models/item/{TREE_ID}.json"))
    item["parent"]=bush["visual_models"][state]
    item["display"]["gui"].update(scale=[0.7,0.7,0.7],translation=[0,0,0])
    write(assets/f"models/item/{BUSH_ID}.json",item)
    loot=ROOT/f"src/main/resources/data/bloodborne_blocks/loot_tables/blocks/{BUSH_ID}.json"
    write(loot,{"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":f"bloodborne_blocks:{BUSH_ID}"}],"conditions":[{"condition":"minecraft:survives_explosion"}]}]})
    labels={"en_us":"Dry bush [o_dry_bush]","ru_ru":"Сухой куст [o_dry_bush]"}
    for locale,label in labels.items():
        path=assets/f"lang/{locale}.json";language=read(path);language[f"block.bloodborne_blocks.{BUSH_ID}"]=label;write(path,language)
    palette_path=LOGICAL/"production-palette.json";palette=read(palette_path)
    if not any(row["id"]==BUSH_ID for row in palette["objects"]):
        palette["objects"].append({"id":BUSH_ID,"semantic_label":labels["en_us"],"source_reviews":["DRY_BUSH"]})
    write(palette_path,palette)
    doc_path=ROOT/"docs/production-logical-palette.json";doc=read(doc_path)
    doc["objects"]=[row for row in doc["objects"] if row["id"]!=BUSH_ID]
    template=copy.deepcopy(next(row for row in doc["objects"] if row["id"]==TREE_ID))
    template.update(id=BUSH_ID,semantic_label=labels["en_us"],source_reviews=["DRY_BUSH"],
        provenance=["User clarification: dry tree and dry bush are distinct constructions", "Historical asset_e assembled from immutable source meshes"],
        source_patterns=[],compiled_source_patterns=[],source_occurrences=0,migration_target=BUSH_ID,
        migration_redirect=None,model_variant_relationship=bush["properties"],collision_policy="NONE",
        migration_policy="Explicit mixed-release state migration; historical city fragments are not guessed")
    doc["objects"].append(template);write(doc_path,doc)
def write_assets(tree, bush, meshes, removed_states):
    assets=ROOT/"src/main/resources/assets/bloodborne_blocks"; tree_state=read(assets/f"blockstates/{TREE_ID}.json")
    bush_state={"variants":{}}
    for state,visual in removed_states:
        tree_state["variants"].pop(state,None)
        if visual:
            target=assets/"models"/(visual.split(":",1)[1]+".json")
            if target.is_file():target.unlink()
    for state,mesh_id in bush["models"].items():
        props=parse_state(state);path=bush['visual_models'][state];bush_state["variants"][state]={"model":path}
        textures=sorted({polygon["texture"] for polygon in meshes[mesh_id]["polygons"]});slots={texture:f"#t{index}" for index,texture in enumerate(textures)}
        model={"parent":"minecraft:block/block","bloodborne_mesh":mesh_id,"bloodborne_texture_slots":slots,"textures":{"particle":textures[0],**{f"t{index}":texture for index,texture in enumerate(textures)}}}
        if props['visual']=='alt':model={'parent':path.replace('/alt/','/base/')}
        target=assets/"models"/(path.split(":",1)[1]+".json");target.parent.mkdir(parents=True,exist_ok=True);write(target,model)
    write(assets/f"blockstates/{TREE_ID}.json",tree_state)
    write(assets/f"blockstates/{BUSH_ID}.json",bush_state)
def main():
    parser=argparse.ArgumentParser();parser.add_argument("--source",type=Path);parser.add_argument("--asset-e",action="store_true");parser.add_argument("--plan",type=Path,default=Path("build/unified-tree-variants-plan.json"));parser.add_argument("--recipe",type=Path,default=Path("build/unified-bush-recipe.json"));parser.add_argument("--apply",action="store_true");args=parser.parse_args()
    if args.asset_e==bool(args.source):parser.error("select exactly one of --source or --asset-e")
    recipe=[]
    if args.asset_e:entry,recipe=compose_asset_e();entries=[entry]
    else:
        source=read(args.source);entries=source.get("entries",source) if isinstance(source,dict) else source
    definitions=read(LOGICAL/"definitions.json");geometry=read(LOGICAL/"geometry.json");meshes=read(LOGICAL/"meshes.json.gz");contracts=read(LOGICAL/"contracts-v2.json");physical=read(LOGICAL/"physical-footprints.json")
    output,migration,added,removed_states=build(definitions,geometry,meshes,contracts,physical,entries);args.plan.parent.mkdir(parents=True,exist_ok=True);write(args.plan,{"constructions":added,"migration":migration});write(args.recipe,{"asset":"asset_e" if args.asset_e else "explicit","fragments":recipe})
    if args.apply:
        for name,value in zip(("definitions.json","geometry.json","meshes.json.gz","contracts-v2.json","physical-footprints.json"),output):write(LOGICAL/name,value)
        write_assets(next(block for block in output[0]["blocks"] if block["id"]==TREE_ID),next(block for block in output[0]["blocks"] if block["id"]==BUSH_ID),output[2],removed_states)
        write_metadata(next(block for block in output[0]["blocks"] if block["id"]==TREE_ID),next(block for block in output[0]["blocks"] if block["id"]==BUSH_ID))
        # Persist every state of the withdrawn mixed release for offline migration,
        # including when this generator starts from the older frozen 49-ID source.
        for facing in FACINGS:
            for visual in ("base","alt"):
                migration["states"][f"{namespaced(TREE_ID)}[facing={facing},variant=bush_asset_e,visual={visual}]"]={"id":f"bloodborne_blocks:{BUSH_ID}","properties":{"facing":facing,"visual":visual},"rootOffset":[0,0,0]}
        write(LOGICAL/"tree-bush-split-migration.json",migration)
        from normalize_support_contracts import run
        run(LOGICAL,apply=True)
    print(json.dumps({"constructions":added,"mapped":len(migration["states"])}))
if __name__=="__main__":main()
