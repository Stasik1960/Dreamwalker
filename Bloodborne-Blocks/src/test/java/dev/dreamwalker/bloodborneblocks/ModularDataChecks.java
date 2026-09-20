package dev.dreamwalker.bloodborneblocks;

import java.util.*;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;

/** v2 data contract test. It starts no game client, world or server. */
public final class ModularDataChecks {
 public static void main(String[] args){
  SharedConstants.createGameVersion();Bootstrap.initialize();
  var pool=new ModularBakedModel.QuadPool();int[] face=new int[32];
  var quad=pool.intern(face,net.minecraft.util.math.Direction.UP,null);
  check(quad==pool.intern(face.clone(),net.minecraft.util.math.Direction.UP,null),"composite faces share vertex buffers");
  check(quad!=pool.intern(face.clone(),net.minecraft.util.math.Direction.DOWN,null),"opposite normals stay distinct");
  int[] shifted=face.clone();shifted[0]=Float.floatToRawIntBits(.5F);
  check(quad!=pool.intern(shifted,net.minecraft.util.math.Direction.UP,null),"different geometry stays distinct");
  check(quad!=new ModularBakedModel.QuadPool().intern(face.clone(),net.minecraft.util.math.Direction.UP,null),"resource reload does not reuse stale quads");
  BloodborneBlocks.Data all=BloodborneBlocks.loadDefinitions();
  List<BloodborneBlocks.Definition> legacy=all.blocks.stream().filter(definition->!definition.modular&&!definition.logical).toList();
  List<BloodborneBlocks.Definition> modular=all.blocks.stream().filter(definition->definition.modular).toList();
  check(legacy.size()==503,"503 legacy palette IDs retained");check(!modular.isEmpty(),"modular definitions loaded");
  Set<String> ids=new HashSet<>();for(var definition:all.blocks)check(ids.add(definition.id),"definition namespace conflict: "+definition.id);
  LegacyItemSections.load();
  String wall=LegacyItemSections.targetId("nether_brick_wall",Map.of());
  check(wall.startsWith("m_")&&ids.contains(wall),"old oversized wall item places a registered cell");
  check(LegacyItemSections.targetId("acacia_stairs",Map.of()).equals("acacia_stairs"),"large working door remains whole");
  check(LegacyItemSections.targetId("brick_stairs",Map.of()).equals("brick_stairs"),"native stairs keep placement behavior");
  for(var definition:legacy){String target=LegacyItemSections.targetId(definition.id,Map.of());check(target.isEmpty()||ids.contains(target),"old default item target exists: "+definition.id);}
  Map<String,ModularMeshData.Mesh> meshes=ModularMeshData.loadAndValidate();check(meshes.size()==modular.size(),"one mesh per modular definition");
  for(var definition:modular){
   check(definition.id.startsWith("m_"),"modular ID prefix: "+definition.id);
   check(definition.kind.equals("generic")&&definition.source.equals("minecraft:stone"),"modular carrier contract: "+definition.id);
   check(definition.properties.containsKey("facing")&&Set.of("facing","waterlogged").containsAll(definition.properties.keySet()),"modular property contract: "+definition.id);
   check(new HashSet<>(definition.properties.get("facing")).equals(Set.of("north","east","south","west")),"four facings: "+definition.id);
   Set<String> expected=new HashSet<>();for(String facing:definition.properties.get("facing")){
    if(definition.properties.containsKey("waterlogged")){
     check(new HashSet<>(definition.properties.get("waterlogged")).equals(Set.of("true","false")),"waterlogging values: "+definition.id);
     check("false".equals(definition.defaultProperties.get("waterlogged")),"dry default state: "+definition.id);
     expected.add("facing="+facing+",waterlogged=false");expected.add("facing="+facing+",waterlogged=true");
    }else expected.add("facing="+facing);
   }
   check("north".equals(definition.defaultProperties.get("facing")),"north default state: "+definition.id);
   check(definition.states.keySet().equals(expected),"complete modular states: "+definition.id);
   for(int[] state:definition.states.values())check(state!=null&&state.length==3&&state[2]>=0&&state[2]<=15,"valid luminance state: "+definition.id);
   if(definition.properties.containsKey("waterlogged"))for(String facing:definition.properties.get("facing"))
    check(Arrays.equals(definition.states.get("facing="+facing+",waterlogged=false"),definition.states.get("facing="+facing+",waterlogged=true")),"waterlogging preserves light state: "+definition.id+" "+facing);
   check(meshes.containsKey(definition.id),"mesh exists: "+definition.id);
  }
  GeometryRuntime.loadAndValidate(all);
  try{
   var field=GeometryRuntime.class.getDeclaredField("BLOCKS");field.setAccessible(true);
   @SuppressWarnings("unchecked") Map<String,GeometryRuntime.GeometryBlock> geometry=(Map<String,GeometryRuntime.GeometryBlock>)field.get(null);
   check(geometry.size()==all.blocks.size(),"one geometry block per definition");
   for(var definition:modular){
    var block=geometry.get(definition.id);check(block!=null&&block.states.keySet().equals(definition.states.keySet()),"geometry facing states: "+definition.id);
    for(var state:block.states.values()){
     check(state.parsedCells.size()==1&&state.parsedCells.containsKey(net.minecraft.util.math.BlockPos.ORIGIN),"single origin cell: "+definition.id);
     var cell=state.parsedCells.get(net.minecraft.util.math.BlockPos.ORIGIN);
     check(cell.collision.size()<=4&&cell.outline.size()==1,"bounded collision/outline cost: "+definition.id);
     if(Set.of("tree","bush","plant","floor_decoration").contains(definition.semantic))
      check(cell.collision.isEmpty(),"soft decoration is passable: "+definition.id);
    }
    if(definition.properties.containsKey("waterlogged"))for(String facing:definition.properties.get("facing"))
     check(block.states.get("facing="+facing+",waterlogged=false")==block.states.get("facing="+facing+",waterlogged=true"),"waterlogging reuses geometry profile: "+definition.id+" "+facing);
   }
  }catch(ReflectiveOperationException e){throw new AssertionError(e);}
  System.out.printf(Locale.ROOT,"MODULAR DATA CHECKS PASSED: %d legacy, %d modules, %d meshes%n",legacy.size(),modular.size(),meshes.size());
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
