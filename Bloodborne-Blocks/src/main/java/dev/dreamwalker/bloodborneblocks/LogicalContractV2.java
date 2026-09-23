package dev.dreamwalker.bloodborneblocks;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Strict loader for the small authored logical-object contract (schema v2). */
final class LogicalContractV2 {
 private static final Gson GSON=new Gson();
 private static final Set<String> REQUIRED_POC_FAMILIES=Set.of("o_dead_tree_planter","o_cases_0","o_wall_deco_1","o_iron_gate","o_iron_railing");
 private static Map<String,DebugMetadata> DEBUG_METADATA=Map.of();
 static final class Data {int schemaVersion;String transform_contract;List<Family> families;}
 static final class Family {String id,placement_policy,mirror_policy,collision_policy,selection_policy,collision_justification,review_id,authority;Anchor canonical_anchor;List<Integer> rotations;Map<String,State> states;List<Pattern> migration_source_pattern,review_source_patterns;}
 static final class Anchor {int[] cell;double[] pivot;}
 static final class State {int rotation;Render render_mesh;Footprint selection_footprint,collision_footprint,interaction_footprint;List<Pattern> migration_source_pattern;}
 static final class Render {String id;double[] bounds,offset;}
 static final class Footprint {List<double[]> boxes,cells;}
 static final class Pattern {String exact_source_signature;List<Component> components;}
 static final class Component {String id;Map<String,String> properties;int[] offset;}
 record DebugMetadata(String reviewId,String canonicalAnchor,String collisionPolicy,String selectionPolicy,String sourceFamily,String sourcePatternSummary) {}
 private LogicalContractV2() {}

 static Map<String,GeometryRuntime.GeometryBlock> load(BloodborneBlocks.Data definitions){
  LogicalTransform.loadAndValidate();Data data=read();if(data.schemaVersion!=2||!"transform-v2.json".equals(data.transform_contract)||data.families==null)throw fail("schema");
  Map<String,BloodborneBlocks.Definition> known=new HashMap<>();for(BloodborneBlocks.Definition d:definitions.blocks)known.put(d.id,d);Set<String> meshes=ModularMeshData.loadLogicalAndValidate().keySet();
  Map<String,GeometryRuntime.GeometryBlock> result=new HashMap<>();Map<String,DebugMetadata> debug=new HashMap<>();Set<String> ids=new HashSet<>();
  for(Family family:data.families){
   if(family==null||family.id==null||!ids.add(family.id)||family.states==null||family.canonical_anchor==null||family.rotations==null)throw fail("family");
   BloodborneBlocks.Definition definition=known.get(family.id);if(definition==null||!definition.logical)throw fail("unknown family "+family.id);
   checkAnchor(family.canonical_anchor);if(!Set.of("FLOOR","WALL_ADJACENT").contains(family.placement_policy)||!"ROTATE_ONLY".equals(family.mirror_policy)||!family.rotations.equals(List.of(0,90,180,270)))throw fail("policy "+family.id);
   if(family.id.startsWith("o_c")&&!Arrays.equals(family.canonical_anchor.cell,new int[]{0,0,0}))throw fail("batch anchor "+family.id);
   if((family.review_id==null)!=(family.authority==null)||(family.review_id!=null&&(family.review_id.isBlank()||!"user".equals(family.authority))))throw fail("review metadata "+family.id);
   if(!family.states.keySet().equals(definition.states.keySet())||definition.models==null)throw fail("states "+family.id);
   checkOrientations(family);
   GeometryRuntime.GeometryBlock block=new GeometryRuntime.GeometryBlock();block.states=new HashMap<>();
   for(var entry:family.states.entrySet()){
    String key=entry.getKey();State state=entry.getValue();if(state==null||!family.rotations.contains(state.rotation)||state.render_mesh==null||!Objects.equals(definition.models.get(key),state.render_mesh.id)||!meshes.contains(state.render_mesh.id))throw fail("mesh "+family.id+"["+key+"]");
    checkRender(state.render_mesh);checkBoxes(state.selection_footprint,"selection",1);checkBoxes(state.collision_footprint,"collision",budget(family));checkCells(state.interaction_footprint);checkPattern(state.migration_source_pattern);
    if("FLOOR".equals(family.placement_policy)){
     if(state.render_mesh.bounds[1]+state.render_mesh.offset[1]<-1e-6)throw fail("RENDER_BELOW_SUPPORT_PLANE "+family.id+"["+key+"]");
     for(double[] cell:state.interaction_footprint.cells)if(cell[1]<0)throw fail("GROUND_OBJECT_HAS_HELPER_BELOW_ANCHOR "+family.id+"["+key+"]");
    }
    for(double[] box:state.collision_footprint.boxes)if(!covered(box,state.interaction_footprint.cells))throw fail("collision cell coverage "+family.id+"["+key+"]");
    block.states.put(key,geometry(family,state));
    debug.put(family.id+"\u0000"+key,new DebugMetadata(family.review_id,anchorText(family.canonical_anchor),family.collision_policy,family.selection_policy==null?"AUTHORED_OUTLINE":family.selection_policy,sourceFamily(family),sourcePatternSummary(family,state)));
   }
   result.put(family.id,block);
  }
  if(!result.keySet().containsAll(REQUIRED_POC_FAMILIES))throw fail("missing approved POC family");
  DEBUG_METADATA=Collections.unmodifiableMap(debug);
  return result;
 }
 static DebugMetadata debugMetadata(String familyId,String stateKey){return DEBUG_METADATA.get(familyId+"\u0000"+stateKey);}
 private static String anchorText(Anchor anchor){return anchor.cell[0]+" "+anchor.cell[1]+" "+anchor.cell[2]+" (local cell; pivot 0.5 0 0.5)";}
 private static String sourceFamily(Family family){return "catalog review "+(family.review_id==null?"noCatalogID":family.review_id)+"; placed provenance not stored";}
 private static String sourcePatternSummary(Family family,State state){LinkedHashSet<String> signatures=new LinkedHashSet<>();if(state.migration_source_pattern!=null)for(Pattern pattern:state.migration_source_pattern)if(pattern!=null&&pattern.exact_source_signature!=null&&!pattern.exact_source_signature.isBlank())signatures.add(pattern.exact_source_signature.substring(0,Math.min(12,pattern.exact_source_signature.length())));String listed=signatures.stream().limit(3).reduce((left,right)->left+","+right).orElse("none");return "catalog only; state signatures="+listed+", total="+signatures.size()+", family patterns="+count(family.review_source_patterns)+"; placed provenance not stored";}
 private static int count(List<Pattern> patterns){return patterns==null?0:patterns.size();}
 static Set<String> declaredFamilyIds(){Data data=read();if(data.schemaVersion!=2||data.families==null)throw fail("schema");Set<String> ids=new LinkedHashSet<>();for(Family family:data.families)if(family==null||family.id==null||!ids.add(family.id))throw fail("family");return Collections.unmodifiableSet(ids);}
 private static void checkOrientations(Family family){
  Map<String,Map<String,State>> oriented=new HashMap<>();
  Map<String,Map<String,State>> connections=new HashMap<>();
  for(var entry:family.states.entrySet()){
   Map<String,String> properties=parseStateKey(entry.getKey());String facing=properties.remove("facing");
   if(facing==null&&isConnectionMask(properties)){
    String group="";Map<String,String> remainder=new TreeMap<>(properties);for(String direction:List.of("north","east","south","west"))remainder.remove(direction);
    if(!remainder.isEmpty())group=remainder.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).reduce((a,b)->a+","+b).orElse("");
    if(connections.computeIfAbsent(group,ignored->new HashMap<>()).put(connectionMask(properties),entry.getValue())!=null)throw fail("duplicate connection mask "+family.id);continue;
   }
   String group=properties.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+"="+e.getValue()).reduce((a,b)->a+","+b).orElse("");
   if(facing==null){if(entry.getValue().rotation!=0)throw fail("symmetric rotation "+family.id+"["+entry.getKey()+"]");continue;}
   if(!Set.of("north","east","south","west").contains(facing))throw fail("facing "+family.id+"["+entry.getKey()+"]");
   if(oriented.computeIfAbsent(group,ignored->new HashMap<>()).put(facing,entry.getValue())!=null)throw fail("duplicate facing "+family.id+"["+entry.getKey()+"]");
  }
  for(var entry:oriented.entrySet()){
   if(!entry.getValue().keySet().equals(Set.of("north","east","south","west")))throw fail("incomplete rotations "+family.id);
   for(var expected:Map.of("north",0,"east",90,"south",180,"west",270).entrySet())if(entry.getValue().get(expected.getKey()).rotation!=expected.getValue())throw fail("rotation "+family.id+" "+expected.getKey());
  }
  for(var entry:connections.entrySet()){
   if(entry.getValue().size()!=16)throw fail("incomplete connection masks "+family.id);
   for(int mask=0;mask<16;mask++){
    State state=entry.getValue().get(Integer.toString(mask));if(state==null||state.rotation!=0)throw fail("connection rotation "+family.id);
    int rotated=((mask&1)<<1)|((mask&2)<<1)|((mask&4)<<1)|((mask&8)>>>3);if(!entry.getValue().containsKey(Integer.toString(rotated)))throw fail("connection rotation closure "+family.id);
   }
  }
 }
 private static boolean isConnectionMask(Map<String,String> properties){return properties.keySet().containsAll(Set.of("north","east","south","west"))&&properties.entrySet().stream().filter(e->Set.of("north","east","south","west").contains(e.getKey())).allMatch(e->Set.of("false","true").contains(e.getValue()));}
 private static String connectionMask(Map<String,String> properties){int mask=0;for(int index=0;index<4;index++)if("true".equals(properties.get(List.of("north","east","south","west").get(index))))mask|=1<<index;return Integer.toString(mask);}
 private static Map<String,String> parseStateKey(String key){
  if(key==null)throw fail("null state key");Map<String,String> result=new HashMap<>();if(key.isEmpty())return result;
  for(String entry:key.split(",")){String[] pair=entry.split("=",-1);if(pair.length!=2||pair[0].isBlank()||pair[1].isBlank()||result.put(pair[0],pair[1])!=null)throw fail("state key "+key);}return result;
 }
 private static Data read(){try(InputStream in=LogicalContractV2.class.getResourceAsStream("/bloodborne_blocks/logical/contracts-v2.json")){if(in==null)throw fail("missing contracts-v2.json");return GSON.fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),Data.class);}catch(IOException|RuntimeException e){throw e instanceof IllegalStateException?(IllegalStateException)e:new IllegalStateException("Cannot load contracts-v2",e);}}
 private static GeometryRuntime.GeometryState geometry(Family family,State state){
  GeometryRuntime.GeometryState geometry=new GeometryRuntime.GeometryState();geometry.anchor=family.canonical_anchor.cell.clone();geometry.rotation=state.rotation;geometry.render_offset=state.render_mesh.offset.clone();geometry.cells=new LinkedHashMap<>();
  geometry.placementPolicy=family.placement_policy;geometry.mirrorPolicy=family.mirror_policy;
  for(double[] cell:state.interaction_footprint.cells){int x=(int)cell[0],y=(int)cell[1],z=(int)cell[2];GeometryRuntime.GeometryCell part=new GeometryRuntime.GeometryCell();part.collision=clip(state.collision_footprint.boxes,x,y,z);part.outline=clip(state.selection_footprint.boxes,x,y,z);geometry.cells.put(x+","+y+","+z,part);}
  geometry.globalOutline=state.selection_footprint.boxes.get(0).clone();return geometry;
 }
 private static List<double[]> clip(List<double[]> boxes,int x,int y,int z){List<double[]> out=new ArrayList<>();for(double[] box:boxes){double[] clipped={Math.max(box[0],x)-x,Math.max(box[1],y)-y,Math.max(box[2],z)-z,Math.min(box[3],x+1)-x,Math.min(box[4],y+1)-y,Math.min(box[5],z+1)-z};if(clipped[0]<clipped[3]&&clipped[1]<clipped[4]&&clipped[2]<clipped[5])out.add(clipped);}return out;}
 private static boolean covered(double[] box,List<double[]> cells){for(int x=(int)Math.floor(box[0]);x<(int)Math.ceil(box[3]);x++)for(int y=(int)Math.floor(box[1]);y<(int)Math.ceil(box[4]);y++)for(int z=(int)Math.floor(box[2]);z<(int)Math.ceil(box[5]);z++){boolean present=false;for(double[] cell:cells)if((int)cell[0]==x&&(int)cell[1]==y&&(int)cell[2]==z)present=true;if(!present)return false;}return true;}
 private static int budget(Family f){int n=switch(f.collision_policy){case "NONE"->0;case "SIMPLE_BOX"->1;case "TWO_BOX"->2;case "THREE_BOX"->3;case "TRUNK"->2;case "POST"->1;case "FENCE","WALL"->5;case "DOOR","GATE"->2;case "STAIRS"->3;default->-1;};if(n<0||(("TRUNK".equals(f.collision_policy)||"STAIRS".equals(f.collision_policy)||"TWO_BOX".equals(f.collision_policy)||"THREE_BOX".equals(f.collision_policy))&&(f.collision_justification==null||f.collision_justification.isBlank())))throw fail("collision policy "+f.id);return n;}
 private static void checkAnchor(Anchor a){LogicalTransform.requireCell(a.cell,"anchor");for(int n:a.cell)if(Math.abs(n)>64)throw fail("anchor cell");if(a.pivot==null||a.pivot.length!=3||a.pivot[0]!=.5||a.pivot[1]!=0||a.pivot[2]!=.5)throw fail("anchor pivot");}
 private static void checkRender(Render r){if(r.id==null||r.bounds==null||r.bounds.length!=6||r.offset==null||r.offset.length!=3)throw fail("render mesh");for(double n:r.bounds)bounded(n,"render bounds");for(double n:r.offset)bounded(n,"render offset");}
 private static void checkBoxes(Footprint f,String name,int max){if(f==null||f.boxes==null||f.boxes.size()>max||("selection".equals(name)&&f.boxes.size()!=1))throw fail(name+" budget");for(double[] b:f.boxes){if(b==null||b.length!=6)throw fail(name+" box");for(double n:b)bounded(n,name);if(b[0]>=b[3]||b[1]>=b[4]||b[2]>=b[5])throw fail(name+" empty");}}
 private static void checkCells(Footprint f){if(f==null||f.cells==null||f.cells.isEmpty())throw fail("interaction cells");Set<String> seen=new HashSet<>();boolean root=false;for(double[] c:f.cells){if(c==null||c.length!=3)throw fail("interaction cell");for(double n:c)if(!Double.isFinite(n)||n!=Math.rint(n)||Math.abs(n)>64)throw fail("interaction cell");if(c[0]==0&&c[1]==0&&c[2]==0)root=true;if(!seen.add((int)c[0]+","+(int)c[1]+","+(int)c[2]))throw fail("duplicate interaction cell");}if(!root)throw fail("interaction cells omit root");}
 private static void checkPattern(List<Pattern> patterns){if(patterns==null)return;for(Pattern p:patterns){if(p==null||p.components==null)throw fail("migration pattern");for(Component c:p.components){if(c==null||c.id==null||c.offset==null)throw fail("migration component");LogicalTransform.requireCell(c.offset,"migration offset");}}}
 private static void bounded(double n,String what){if(!Double.isFinite(n)||Math.abs(n)>64)throw fail(what);}
 private static IllegalStateException fail(String message){return new IllegalStateException("Invalid logical contract v2: "+message);}
}
