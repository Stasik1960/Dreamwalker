package dev.dreamwalker.bloodborneblocks;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.math.Direction;

/** Bootstrap-only logical registry smoke test; it starts neither a game client nor a server world. */
public final class LogicalRegistryChecks {
 private LogicalRegistryChecks() {}
 public static void main(String[] args){
  SharedConstants.createGameVersion();Bootstrap.initialize();
  BloodborneBlocks.Data data=BloodborneBlocks.loadDefinitions();GeometryRuntime.loadAndValidate(data);
  List<BloodborneBlocks.Definition> logical=data.blocks.stream().filter(definition->definition.logical).toList();check(!logical.isEmpty(),"logical definitions");
  Map<String,BloodborneBlocks.Definition> definitions=new HashMap<>();for(var definition:data.blocks)definitions.put(definition.id,definition);
  for(BloodborneBlocks.Definition definition:logical){
   ArchitectureBlock block=register(definition);
   check(BloodborneBlocks.creativeVisible(definition,Set.of()),"logical creative flag: "+definition.id);
   if(definition.properties.containsKey("facing"))check(block.getStateManager().getProperty("facing")!=null,"facing property: "+definition.id);
   if("connected".equals(definition.behavior))for(String name:List.of("north","east","south","west"))check(block.getStateManager().getProperty(name) instanceof BooleanProperty,"connected boolean: "+definition.id+"."+name);
   BlockState state=block.getDefaultState();if(state.contains(Properties.HORIZONTAL_FACING))check(block.rotate(state,BlockRotation.CLOCKWISE_90).get(Properties.HORIZONTAL_FACING).asString().equals("east"),"logical facing rotation: "+definition.id);
   if(state.contains(Properties.WALL_MOUNT_LOCATION)){
    check(block.getStateManager().getStates().size()==12,"complete mount/yaw states: "+definition.id);
    for(Direction side:Direction.values())for(Direction player:Direction.Type.HORIZONTAL){
     BlockState placed=ArchitectureBlock.logicalMountPlacement(state,side,player);
     WallMountLocation face=side==Direction.UP?WallMountLocation.FLOOR:side==Direction.DOWN?WallMountLocation.CEILING:WallMountLocation.WALL;
     check(placed.get(Properties.WALL_MOUNT_LOCATION)==face,"mount clicked face: "+definition.id+" "+side);
     check(placed.get(Properties.HORIZONTAL_FACING)==(side.getAxis().isHorizontal()?side:player.getOpposite()),"mount points outward: "+definition.id+" "+side);
     for(BlockRotation rotation:BlockRotation.values()){
      BlockState rotated=block.rotate(placed,rotation);
      check(rotated.get(Properties.WALL_MOUNT_LOCATION)==face,"yaw preserves mount face");
      check(rotated.get(Properties.HORIZONTAL_FACING)==rotation.rotate(placed.get(Properties.HORIZONTAL_FACING)),"mount yaw direction");
     }
     for(BlockMirror mirror:BlockMirror.values()){
      BlockState mirrored=block.mirror(placed,mirror);
      check(mirrored.get(Properties.WALL_MOUNT_LOCATION)==face,"mirror preserves mount face");
      check(mirrored.get(Properties.HORIZONTAL_FACING)==mirror.apply(placed.get(Properties.HORIZONTAL_FACING)),"mount mirror direction");
     }
    }
    state=state.with(Properties.WALL_MOUNT_LOCATION,WallMountLocation.CEILING);
   }
   for(String name:List.of("open","north","east","south","west","up","down")){var property=block.getStateManager().getProperty(name);if(property instanceof BooleanProperty bool)state=state.with(bool,true);}
   var picked=block.getPickStack(null,null,state).getSubNbt("BlockStateTag");
   if(picked!=null)for(String name:List.of("facing","face","open","north","east","south","west","up","down"))check(!picked.contains(name),"logical pick normalizes "+name+": "+definition.id);
  }
  JsonArray rules=rules();JsonObject open=first(rules,rule->rule.getAsJsonObject("source").getAsJsonObject("properties").has("open"));
  ArchitectureBlock source=register(definitions.get(open.getAsJsonObject("source").get("id").getAsString()));LogicalItemMigration.load();
  ItemStack exact=stack(source,open.getAsJsonObject("source").getAsJsonObject("properties"),7);exact.getOrCreateNbt().putString("RegressionMarker","kept");
  LogicalItemMigration.Result migrated=LogicalItemMigration.migrate(source,exact);JsonObject target=open.getAsJsonObject("target");
  check(migrated.matched(),"exact open-state migration");check(migrated.stack().getCount()==7&&"kept".equals(migrated.stack().getNbt().getString("RegressionMarker")),"migration preserves count/NBT");
  check(migrated.stack().isOf(BloodborneBlocks.BLOCKS.get(target.get("id").getAsString()).asItem()),"exact migration target");check(properties(migrated.stack()).equals(strings(target.getAsJsonObject("properties"))),"open-state target properties");
  ItemStack unknown=exact.copy();unknown.getOrCreateSubNbt("BlockStateTag").putString("open","unknown");check(!LogicalItemMigration.migrate(source,unknown).matched(),"unknown legacy state unchanged");
  for(String opened:List.of("false","true")){
   JsonObject crate=first(rules,rule->rule.getAsJsonObject("source").get("id").getAsString().equals("dark_oak_fence_gate")&&rule.getAsJsonObject("source").getAsJsonObject("properties").get("open").getAsString().equals(opened));
   ArchitectureBlock carrier=register(definitions.get("dark_oak_fence_gate"));LogicalItemMigration.load();
   ItemStack input=stack(carrier,crate.getAsJsonObject("source").getAsJsonObject("properties"),2);
   var result=LogicalItemMigration.migrate(carrier,input);
   check(result.matched()&&result.stack().isOf(BloodborneBlocks.BLOCKS.get(crate.getAsJsonObject("target").get("id").getAsString()).asItem()),"crate visual variant retains source open="+opened);
  }
  JsonObject unique=componentRule(rules,true);checkComponent(definitions,unique,true);
  JsonObject ambiguous=componentRule(rules,false);checkComponent(definitions,ambiguous,false);
  System.out.println("LOGICAL REGISTRY CHECKS PASSED: logical="+logical.size()+" rules="+rules.size());
 }
 private static ArchitectureBlock register(BloodborneBlocks.Definition definition){check(definition!=null,"referenced definition exists");ArchitectureBlock existing=BloodborneBlocks.BLOCKS.get(definition.id);if(existing!=null){checkBlockItem(existing);return existing;}BloodborneBlocks.prepareDefinition(definition);ArchitectureBlock block=ArchitectureBlock.create(definition);Identifier id=BloodborneBlocks.id(definition.id);Registry.register(Registries.BLOCK,id,block);ArchitectureBlockItem item=Registry.register(Registries.ITEM,id,new ArchitectureBlockItem(block,new Item.Settings()));item.appendBlocks(Item.BLOCK_ITEMS,item);BloodborneBlocks.BLOCKS.put(definition.id,block);checkBlockItem(block);return block;}
 private static void checkBlockItem(ArchitectureBlock block){Item item=block.asItem();check(item instanceof ArchitectureBlockItem,"architecture block item: "+block.definition.id);check(item!=Items.AIR,"architecture block item is not air: "+block.definition.id);check(Item.BLOCK_ITEMS.get(block)==item,"architecture block item reverse mapping: "+block.definition.id);}
 private static ItemStack stack(ArchitectureBlock block,JsonObject values,int count){ItemStack stack=new ItemStack(block,count);strings(values).forEach(stack.getOrCreateSubNbt("BlockStateTag")::putString);return stack;}
 private static JsonArray rules(){try(var stream=LogicalRegistryChecks.class.getResourceAsStream("/bloodborne_blocks/logical/migration.json")){return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("rules");}catch(Exception e){throw new AssertionError(e);}}
 private interface RuleFilter {boolean accept(JsonObject rule);}
 private static JsonObject first(JsonArray rules,RuleFilter filter){for(JsonElement element:rules)if(filter.accept(element.getAsJsonObject()))return element.getAsJsonObject();throw new AssertionError("matching migration rule");}
 private static JsonObject componentRule(JsonArray rules,boolean unique){Map<String,Set<String>> targets=new HashMap<>();Map<String,JsonObject> example=new HashMap<>();for(JsonElement element:rules){JsonObject rule=element.getAsJsonObject();if(!rule.has("components")||rule.get("components").isJsonNull())continue;for(JsonElement cell:rule.getAsJsonArray("components")){JsonObject component=cell.getAsJsonObject();String key=component.get("id").getAsString()+"|"+canonical(component.getAsJsonObject("properties"));JsonObject target=rule.getAsJsonObject("target");targets.computeIfAbsent(key,ignored->new HashSet<>()).add(target.get("id").getAsString()+"|"+withoutFacing(target.getAsJsonObject("properties")));example.putIfAbsent(key,rule);}}for(var entry:targets.entrySet())if((entry.getValue().size()==1)==unique)return example.get(entry.getKey());throw new AssertionError("component migration rule");}
 private static void checkComponent(Map<String,BloodborneBlocks.Definition> definitions,JsonObject rule,boolean expected){for(JsonElement element:rule.getAsJsonArray("components")){JsonObject component=element.getAsJsonObject();ArchitectureBlock block=register(definitions.get(component.get("id").getAsString()));ItemStack stack=stack(block,component.getAsJsonObject("properties"),3);LogicalItemMigration.Result result=LogicalItemMigration.componentPick(block,stack);if(result.matched()!=expected)continue;if(expected)check(result.stack().isOf(BloodborneBlocks.BLOCKS.get(rule.getAsJsonObject("target").get("id").getAsString()).asItem()),"component redirect target");else check(result.stack()==stack,"ambiguous component preserved");return;}throw new AssertionError("component migration ambiguity");}
 private static Map<String,String> strings(JsonObject object){Map<String,String> values=new TreeMap<>();object.entrySet().forEach(entry->values.put(entry.getKey(),entry.getValue().getAsString()));return values;}
 private static String canonical(JsonObject object){return join(strings(object));}
 private static String withoutFacing(JsonObject object){Map<String,String> values=strings(object);values.remove("facing");values.remove("face");return join(values);}
 private static String join(Map<String,String> values){return values.entrySet().stream().map(entry->entry.getKey()+"="+entry.getValue()).reduce((a,b)->a+","+b).orElse("");}
 private static Map<String,String> properties(ItemStack stack){var tag=stack.getSubNbt("BlockStateTag");if(tag==null)return Map.of();Map<String,String> values=new TreeMap<>();for(String name:tag.getKeys())values.put(name,tag.getString(name));return values;}
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
