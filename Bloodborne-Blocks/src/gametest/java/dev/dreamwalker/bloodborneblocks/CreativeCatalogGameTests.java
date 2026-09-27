package dev.dreamwalker.bloodborneblocks;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.state.property.Property;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Exercises the actual registered tab collector and item placement on a dedicated server. */
public final class CreativeCatalogGameTests implements FabricGameTest {
 private static final List<String> ART=List.of("variant","visual");

 private static List<ItemStack> tab(TestContext context){
  ItemGroup group=Registries.ITEM_GROUP.get(BloodborneBlocks.id("architecture"));
  group.updateEntries(new ItemGroup.DisplayContext(FeatureFlags.VANILLA_FEATURES,false,context.getWorld().getRegistryManager()));
  return List.copyOf(group.getDisplayStacks());
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="creative_catalog")
 public void registeredTabCoversEveryPlaceableItemAndArtValue(TestContext context) throws Exception {
  List<ItemStack> entries=tab(context);Set<String> actual=new HashSet<>(),expected=new HashSet<>();
  Map<String,Integer> counts=new TreeMap<>();List<String> exclusions=new ArrayList<>();Set<Item> items=new HashSet<>();
  int lastSection=-1;String lastId="";
  for(ItemStack stack:entries){
   context.assertTrue(stack.getItem() instanceof BlockItem&&stack.getCount()==1,"tab contains one placeable item per entry");
   BlockItem item=(BlockItem)stack.getItem();String id=Registries.ITEM.getId(item).toString();int section=ArchitectureCreativeCatalog.section(item);
   context.assertTrue(section>=lastSection&&(section!=lastSection||id.compareTo(lastId)>=0),"stable category/registry-ID order");lastSection=section;lastId=id;
   items.add(item);counts.merge(Integer.toString(section),1,Integer::sum);
   context.assertTrue(actual.add(signature(stack)),"no duplicate tab entry: "+signature(stack));
   if(item.getBlock() instanceof ArchitectureBlock block){
    var tag=stack.getSubNbt("BlockStateTag");
    if(tag!=null)for(String key:tag.getKeys())context.assertTrue(ART.contains(key)||(block.definition.placement_properties!=null&&block.definition.placement_properties.containsKey(key)),"no facing/open/lit/connection/anchor product: "+id+"."+key);
    if(block.definition.models!=null){
     String model=ArchitectureCreativeCatalog.itemModelKey(block,stack);
     context.assertTrue(block.definition.models.containsKey(model),"selected inventory art resolves existing model: "+id+"["+model+"]");
     for(String name:ART){var property=block.getStateManager().getProperty(name);if(property!=null)context.assertTrue(model.contains(name+"="+value(ArchitectureBlockItem.applyStateTag(block.getDefaultState(),stack),property)),"inventory model follows selected "+name+" "+id);}
    }
   }
  }
  int registeredItems=0;
  for(Item item:Registries.ITEM){
   if(!Registries.ITEM.getId(item).getNamespace().equals(BloodborneBlocks.ID)||!(item instanceof BlockItem blockItem))continue;
   registeredItems++;context.assertTrue(items.contains(item),"all registered BlockItems visible: "+Registries.ITEM.getId(item));
   // Independently reduce the actual state manager, not the catalog's enumeration.
   for(BlockState state:blockItem.getBlock().getStateManager().getStates()){
    boolean allowed=true;
    if(blockItem.getBlock() instanceof ArchitectureBlock block&&block.definition.placement_properties!=null)
     for(String name:ART){String forced=block.definition.placement_properties.get(name);Property<?> p=block.getStateManager().getProperty(name);if(forced!=null&&p!=null&&!forced.equals(value(state,p)))allowed=false;}
    if(allowed)expected.add(signature(item,state));
   }
   if(blockItem.getBlock() instanceof ArchitectureBlock block&&block.definition.placement_properties!=null)
    for(String name:ART){String forced=block.definition.placement_properties.get(name);if(forced!=null)for(String option:block.definition.properties.get(name))if(!option.equals(forced))exclusions.add(block.definition.id+"."+name+"="+option+": placement policy forces "+forced);}
  }
  for(Block block:Registries.BLOCK)if(Registries.BLOCK.getId(block).getNamespace().equals(BloodborneBlocks.ID)&&!(block.asItem() instanceof BlockItem)){
   exclusions.add(Registries.BLOCK.getId(block)+": service block without independent BlockItem");
   context.assertTrue(entries.stream().noneMatch(stack->stack.isOf(block.asItem())),"service block has no creative item");
  }
  context.assertTrue(actual.equals(expected),"registry-derived art coverage: missing="+difference(expected,actual)+" extra="+difference(actual,expected));
  context.assertTrue(entries.stream().map(CreativeCatalogGameTests::signature).toList().equals(tab(context).stream().map(CreativeCatalogGameTests::signature).toList()),"refilling tab preserves order and entries");
  var report=new LinkedHashMap<String,Object>();report.put("registeredBlockItems",registeredItems);report.put("representedBlockItems",items.size());report.put("visualEntries",entries.size());report.put("entriesBySection",counts);report.put("excludedValuesAndServiceBlocks",exclusions);report.put("missing",List.of());report.put("coverage","PASS");report.put("graphicalClient","NOT_RUN");
  Path file=Path.of("../test-results/creative-catalog-coverage.json");Files.createDirectories(file.getParent());Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(report));
  System.out.println("CREATIVE_CATALOG_COVERAGE "+new GsonBuilder().create().toJson(report));context.complete();
 }

 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=200,batchId="zz_creative_placement")
 public void tabStacksPlaceEveryCategoryAndSelectedNonzeroArt(TestContext context){
  List<ItemStack> entries=tab(context);List<ItemStack> selected=new ArrayList<>();
  for(int category=0;category<4;category++){
   final int section=category;
   selected.add(entries.stream().filter(stack->ArchitectureCreativeCatalog.section((BlockItem)stack.getItem())==section)
    .filter(stack->small((ArchitectureBlock)((BlockItem)stack.getItem()).getBlock(),stack)).findFirst().orElseThrow().copy());
  }
  selected.add(entries.stream().filter(stack->Registries.ITEM.getId(stack.getItem()).getPath().equals("building_stone_brick_wall")).findFirst().orElseThrow().copy());
  selected.add(entries.stream().filter(stack->Registries.ITEM.getId(stack.getItem()).getPath().equals("o_c001"))
   .filter(stack->!stack.getSubNbt("BlockStateTag").getString("variant").equals(BloodborneBlocks.BLOCKS.get("o_c001").definition.defaultProperties.get("variant")))
   .filter(stack->stack.getSubNbt("BlockStateTag").getString("visual").equals("alt")).findFirst().orElseThrow().copy());
  selected.addAll(entries.stream().filter(stack->ArchitectureCreativeCatalog.section((BlockItem)stack.getItem())==3)
   .filter(stack->stack.getSubNbt("BlockStateTag")!=null&&List.of("1","3","7").contains(stack.getSubNbt("BlockStateTag").getString("variant"))).limit(3).map(ItemStack::copy).toList());
  PlayerEntity player=context.createMockSurvivalPlayer();BlockPos clicked=context.getAbsolutePos(new BlockPos(12,1,12));
  try{
   player.refreshPositionAndAngles(clicked.getX()+10,clicked.getY()+6,clicked.getZ()+10,0,0);
   for(ItemStack stack:selected){
    ArchitectureBlock block=(ArchitectureBlock)((BlockItem)stack.getItem()).getBlock();
    clear(context,clicked);context.getWorld().setBlockState(clicked,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
    ItemStack before=stack.copy();player.setStackInHand(Hand.MAIN_HAND,stack);
    var result=stack.useOnBlock(new ItemUsageContext(context.getWorld(),player,Hand.MAIN_HAND,stack,new BlockHitResult(Vec3d.ofCenter(clicked).add(0,.5,0),Direction.UP,clicked,false)));
    BlockState placed=context.getWorld().getBlockState(clicked.up());
    context.assertTrue(result.isAccepted()&&placed.isOf(block),"tab item actually places its registered ID: "+Registries.ITEM.getId(before.getItem()));
    context.assertTrue(stack.isEmpty(),"one successful item placement consumes one item");
    BlockState requested=ArchitectureBlockItem.applyStateTag(block.getDefaultState(),before);
    for(String name:ART){var property=block.getStateManager().getProperty(name);if(property!=null)context.assertTrue(placed.get(property).equals(requested.get(property)),"selected art survives actual placement: "+block.definition.id+"."+name);}
    context.getWorld().breakBlock(clicked.up(),false,player);
   }
   // A malformed/service-state item cannot select an unrelated inventory model.
   ArchitectureBlock tree=BloodborneBlocks.BLOCKS.get("o_c001");ItemStack invalid=new ItemStack(tree);var tag=invalid.getOrCreateSubNbt("BlockStateTag");tag.putString("variant","invalid");tag.putString("visual","invalid");tag.putString("facing","west");
   context.assertTrue(ArchitectureCreativeCatalog.itemModelKey(tree,invalid).equals(BloodborneBlocks.key(tree.getDefaultState())),"invalid art and service tags use default inventory model");context.complete();
  }finally{clear(context,clicked);player.discard();}
 }

 private static boolean small(ArchitectureBlock block,ItemStack stack){BlockState state=ArchitectureBlockItem.applyStateTag(block.getDefaultState(),stack);return GeometryRuntime.anchor(state,Direction.UP).equals(BlockPos.ORIGIN)&&GeometryRuntime.state(state).parsedCells.keySet().stream().allMatch(p->Math.abs(p.getX())<=2&&Math.abs(p.getZ())<=2&&p.getY()>=0&&p.getY()<=3);}
 private static void clear(TestContext context,BlockPos root){for(int x=-8;x<=8;x++)for(int y=-1;y<=15;y++)for(int z=-8;z<=8;z++)context.getWorld().removeBlock(root.add(x,y,z),false);}
 private static String signature(ItemStack stack){return signature(stack.getItem(),ArchitectureBlockItem.applyStateTag(((BlockItem)stack.getItem()).getBlock().getDefaultState(),stack));}
 private static String signature(Item item,BlockState state){StringBuilder result=new StringBuilder(Registries.ITEM.getId(item).toString());for(String name:ART){var p=state.getBlock().getStateManager().getProperty(name);if(p!=null)result.append('|').append(name).append('=').append(value(state,p));}return result.toString();}
 @SuppressWarnings({"rawtypes","unchecked"}) private static String value(BlockState state,Property property){return property.name(state.get(property));}
 private static Set<String> difference(Set<String> a,Set<String> b){Set<String> result=new TreeSet<>(a);result.removeAll(b);return result;}
}
