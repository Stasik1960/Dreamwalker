package dev.dreamwalker.bloodborneblocks;

import com.google.gson.JsonParser;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/** Small adapter for one proved multipart family; old owner states remain loadable. */
final class ReviewedWallConnections {
 static final String ID="building_stone_brick_wall";
 private static final com.google.gson.JsonObject MANIFEST=loadManifest();
 private static final Set<String> ALIASES=Set.copyOf(MANIFEST.getAsJsonObject("aliases").keySet());
 private static final Direction[] SIDES={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
 private static com.google.gson.JsonObject loadManifest(){
  try(var stream=ReviewedWallConnections.class.getResourceAsStream("/bloodborne_blocks/city/reviewed-wall-family.json")){
   if(stream==null)throw new IllegalStateException("Missing reviewed wall mapping");
   return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
  }catch(java.io.IOException e){throw new IllegalStateException(e);}
 }
 static boolean building(ArchitectureBlock block){return ID.equals(block.definition.id);}
 static String retainedSuccessor(ArchitectureBlock block,Direction facing){
  var row=MANIFEST.getAsJsonObject("aliasStates").getAsJsonObject(block.definition.id+"|facing="+facing.asString());
  if(row==null)throw new IllegalArgumentException("Missing reviewed wall successor for "+block.definition.id+"/"+facing);
  return row.get("connection").getAsString();
 }
 private static Set<String> strings(com.google.gson.JsonArray values){var result=new LinkedHashSet<String>();for(var value:values)result.add(value.getAsString());return Set.copyOf(result);}
 private static Set<String> canonicalConnections(){var result=new LinkedHashSet<String>();for(String level:java.util.List.of("low","tall"))for(int mask=0;mask<16;mask++)result.add(level+"_"+mask);return Set.copyOf(result);}
 static void validate(BloodborneBlocks.Definition d,BloodborneBlocks.Data city){
  if(!ID.equals(d.id)||!d.whole_owner||!d.modular||!d.extra_facing||!"generic".equals(d.kind)||!"minecraft:stone_brick_wall".equals(d.source)||!"reviewed_wall".equals(d.behavior)||!d.properties.keySet().equals(Set.of("facing","connection"))||!Set.copyOf(d.properties.get("facing")).equals(Set.of("north","east","south","west"))||!d.states.keySet().equals(d.models.keySet()))throw new IllegalStateException("Invalid reviewed wall building definition");
  var definitions=new java.util.HashMap<String,BloodborneBlocks.Definition>();for(var entry:city.blocks)definitions.put(entry.id,entry);
  var proof=MANIFEST.getAsJsonObject("connections");
  Set<String> canonical=canonicalConnections(),retained=strings(MANIFEST.getAsJsonArray("retainedConnections")),connections=Set.copyOf(proof.keySet()),expectedConnections=new LinkedHashSet<>(canonical);expectedConnections.addAll(retained);
  if(!canonical.equals(strings(MANIFEST.getAsJsonArray("canonicalConnections")))||!java.util.Collections.disjoint(canonical,retained)||!connections.equals(expectedConnections)||!Set.copyOf(d.properties.get("connection")).equals(connections)||d.states.size()!=connections.size()*SIDES.length)throw new IllegalStateException("Invalid reviewed wall connections");
  for(String connection:connections){
   var row=proof.getAsJsonObject(connection);String owner=row.get("owner").getAsString();
   var original=definitions.get(owner);if(original==null||!ALIASES.contains(owner)||!original.whole_owner||!"minecraft:stone_brick_wall".equals(original.source))throw new IllegalStateException("Unproved wall owner "+owner);
   if(!MANIFEST.getAsJsonObject("aliases").get(owner).getAsString().equals(row.get("source").getAsString()))throw new IllegalStateException("Reviewed wall source mismatch "+connection);
   int base=java.util.List.of("north","east","south","west").indexOf(row.get("facing").getAsString());
   if(base<0)throw new IllegalStateException("Reviewed wall facing "+connection);
   for(int turn=0;turn<4;turn++){
    String key="connection="+connection+",facing="+SIDES[turn].asString(),old="facing="+SIDES[(base+turn)%4].asString();
    if(!java.util.Objects.equals(d.models.get(key),original.models.get(old))||!java.util.Arrays.equals(d.states.get(key),original.states.get(old)))throw new IllegalStateException("Reviewed wall must reuse exact existing state/model "+key);
   }
  }
  var successors=MANIFEST.getAsJsonObject("aliasStates");var expected=new LinkedHashSet<String>();
  for(String owner:ALIASES)for(Direction facing:SIDES){String old="facing="+facing.asString(),alias=owner+"|"+old;expected.add(alias);var next=successors.getAsJsonObject(alias);if(next==null)throw new IllegalStateException("Missing reviewed wall alias successor "+alias);String connection=next.get("connection").getAsString(),targetFacing=next.get("facing").getAsString(),key="connection="+connection+",facing="+targetFacing;if(!connections.contains(connection)||!java.util.List.of("north","east","south","west").contains(targetFacing))throw new IllegalStateException("Invalid reviewed wall alias successor "+alias);var original=definitions.get(owner);if(!java.util.Objects.equals(d.models.get(key),original.models.get(old))||!java.util.Arrays.equals(d.states.get(key),original.states.get(old)))throw new IllegalStateException("Reviewed wall alias must retain exact state/model "+alias);}
  if(!expected.equals(successors.keySet()))throw new IllegalStateException("Unexpected reviewed wall alias successor");
 }
 static boolean family(BlockState state){return state.getBlock() instanceof ArchitectureBlock block&&(building(block)||ALIASES.contains(block.definition.id));}
 static ItemStack item(ArchitectureBlock block){
  if(!building(block)&&!ALIASES.contains(block.definition.id))return null;
  ArchitectureBlock target=BloodborneBlocks.CITY_BLOCKS.get(ID);
  return target==null?null:new ItemStack(target);
 }
 static BlockState update(BlockState state,WorldAccess world,BlockPos pos){
  ArchitectureBlock block=(ArchitectureBlock)state.getBlock();
  if(!building(block))return state;
  Direction facing=state.get(Properties.HORIZONTAL_FACING);
  int turn=0;while(SIDES[turn]!=facing)turn++;
  int mask=0;
  for(int local=0;local<4;local++){
   Direction side=SIDES[(local+turn)%4];BlockPos other=pos.offset(side);BlockState neighbor=world.getBlockState(other);
   if(family(neighbor)||neighbor.isSideSolidFullSquare(world,other,side.getOpposite()))mask|=1<<local;
  }
  BlockPos above=pos.up();BlockState roof=world.getBlockState(above);
  String level=roof.isSideSolidFullSquare(world,above,Direction.DOWN)?"tall":"low";
  return BloodborneBlocks.set(state,block.getStateManager().getProperty("connection"),level+"_"+mask);
 }
 private ReviewedWallConnections(){}
}
