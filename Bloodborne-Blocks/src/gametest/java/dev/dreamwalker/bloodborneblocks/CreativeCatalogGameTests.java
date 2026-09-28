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

/** Exercises both registered catalog tabs and selected item placement on a dedicated server. */
public final class CreativeCatalogGameTests implements FabricGameTest {
 private static final List<String> ART=List.of("variant","visual");
 private static List<ItemStack> tab(TestContext context,boolean main){
  ItemGroup group=Registries.ITEM_GROUP.get(BloodborneBlocks.id(main?"architecture":"architecture_technical"));group.updateEntries(new ItemGroup.DisplayContext(FeatureFlags.VANILLA_FEATURES,false,context.getWorld().getRegistryManager()));return List.copyOf(group.getDisplayStacks());
 }
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="creative_catalog")
 public void registeredTabsCoverAllPlaceableItemsAndConstructionManifest(TestContext context) throws Exception {
  List<ItemStack> main=tab(context,true),technical=tab(context,false),all=new ArrayList<>();all.addAll(main);all.addAll(technical);
  Set<String> actual=new HashSet<>(),expected=new HashSet<>(),mainIds=new TreeSet<>(),technicalIds=new TreeSet<>(),registeredIds=new TreeSet<>();Set<Item> represented=new HashSet<>();Map<String,Integer> reportCounts=new TreeMap<>();long allStates=0,allowedStates=0;
  inspect(context,main,true,actual,mainIds,represented,reportCounts);inspect(context,technical,false,actual,technicalIds,represented,reportCounts);
  for(Item item:Registries.ITEM)if(Registries.ITEM.getId(item).getNamespace().equals(BloodborneBlocks.ID)&&item instanceof BlockItem blockItem){String id=Registries.ITEM.getId(item).getPath();registeredIds.add(id);for(BlockState state:blockItem.getBlock().getStateManager().getStates()){allStates++;if(allowed(blockItem,state)){allowedStates++;expected.add(signature(item,state));}}}
  List<ItemStack> candidates=new ArrayList<>(ArchitectureCreativeCatalog.candidateEntries(true));candidates.addAll(ArchitectureCreativeCatalog.candidateEntries(false));Set<String> generated=new HashSet<>(),candidateIds=new TreeSet<>();for(ItemStack stack:candidates){context.assertTrue(generated.add(signature(stack)),"candidate occurs exactly once");candidateIds.add(Registries.ITEM.getId(stack.getItem()).getPath());}
  context.assertTrue(expected.equals(generated),"candidate generator covers every allowed art selection");context.assertTrue(candidateIds.equals(registeredIds),"every registry item has candidates");
  context.assertTrue(ArchitectureCreativeCatalog.mainIds().containsAll(mainIds),"main representatives belong to construction manifest");context.assertTrue(Collections.disjoint(mainIds,technicalIds),"tab item IDs are disjoint");
  Map<String,String> redirects=ArchitectureCreativeCatalog.redirects();Set<String> covered=new HashSet<>(actual);for(var entry:redirects.entrySet()){context.assertTrue(expected.contains(entry.getKey()),"redirect source is a candidate: "+entry.getKey());context.assertTrue(actual.contains(entry.getValue()),"redirect target is visible: "+entry);context.assertTrue(!actual.contains(entry.getKey()),"equivalent is hidden: "+entry.getKey());covered.add(entry.getKey());}
  context.assertTrue(covered.equals(expected),"visible entries and proven equivalents cover all art candidates: missing="+difference(expected,covered)+" extra="+difference(covered,expected));context.assertTrue(actual.size()+redirects.size()==expected.size(),"no arbitrary catalog omissions");
  context.assertTrue(main.stream().map(CreativeCatalogGameTests::signature).toList().equals(tab(context,true).stream().map(CreativeCatalogGameTests::signature).toList()),"main order stable");context.assertTrue(technical.stream().map(CreativeCatalogGameTests::signature).toList().equals(tab(context,false).stream().map(CreativeCatalogGameTests::signature).toList()),"technical order stable");
  var report=new LinkedHashMap<String,Object>();report.put("registeredBlockItems",registeredIds.size());report.put("artCandidates",expected.size());report.put("visibleEntries",actual.size());report.put("hiddenEquivalentEntries",redirects.size());report.put("mainVisibleBlockItems",mainIds.size());report.put("mainVisibleEntries",main.size());report.put("technicalVisibleBlockItems",technicalIds.size());report.put("technicalVisibleEntries",technical.size());report.put("entriesByType",reportCounts);report.put("registeredStates",allStates);report.put("placementExcludedStates",allStates-allowedStates);report.put("serviceStatesCollapsedIntoArtCandidates",allowedStates-expected.size());report.put("serviceStatePolicy","Only variant/visual enumerate artwork; facing/open/lit/connection/root_anchor and other service properties do not create entries. Placement-forced states are excluded.");report.put("coverage","PASS");report.put("graphicalClient","NOT_RUN");Path file=Path.of("../test-results/creative-catalog-coverage.json");Files.createDirectories(file.getParent());Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println("CREATIVE_CATALOG_COVERAGE "+new GsonBuilder().create().toJson(report));context.complete();
 }
 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=240,batchId="zz_creative_placement")
 public void selectedStacksFromEveryCatalogTypePlaceTheirChosenArt(TestContext context){
  List<ItemStack> all=new ArrayList<>(tab(context,true));all.addAll(tab(context,false));List<ItemStack> selected=new ArrayList<>();
  for(int section=0;section<4;section++){final int wanted=section;selected.add(all.stream().filter(stack->ArchitectureCreativeCatalog.section((BlockItem)stack.getItem())==wanted).filter(stack->small((ArchitectureBlock)((BlockItem)stack.getItem()).getBlock(),stack)).findFirst().orElseThrow().copy());}
  selected.add(all.stream().filter(stack->Registries.ITEM.getId(stack.getItem()).getPath().equals(ReviewedWallConnections.ID)).findFirst().orElseThrow().copy());
  selected.add(all.stream().filter(stack->Registries.ITEM.getId(stack.getItem()).getPath().equals("o_c001")).filter(stack->!tagValue(stack,"variant").equals(BloodborneBlocks.BLOCKS.get("o_c001").definition.defaultProperties.get("variant"))).findFirst().orElseThrow().copy());
  // A hidden current BASE/ALT equivalent remains a valid saved item/state.
  ItemStack retainedAlt=ArchitectureCreativeCatalog.candidateEntries(true).stream().filter(stack->Registries.ITEM.getId(stack.getItem()).getPath().equals("o_c001")&&tagValue(stack,"visual").equals("alt")).findFirst().orElseThrow().copy();context.assertTrue(ArchitectureCreativeCatalog.redirects().containsKey(signature(retainedAlt)),"current duplicate ALT has proof");selected.add(retainedAlt);
  selected.add(all.stream().filter(stack->ArchitectureCreativeCatalog.section((BlockItem)stack.getItem())==3).filter(stack->stack.getSubNbt("BlockStateTag")!=null&&!tagValue(stack,"variant").equals("0")).findFirst().orElseThrow().copy());
  PlayerEntity player=context.createMockSurvivalPlayer();BlockPos clicked=context.getAbsolutePos(new BlockPos(12,1,12));try{player.refreshPositionAndAngles(clicked.getX()+10,clicked.getY()+6,clicked.getZ()+10,0,0);for(ItemStack stack:selected)place(context,player,clicked,stack);context.complete();}finally{clear(context,clicked);player.discard();}
 }
 private static void inspect(TestContext context,List<ItemStack> entries,boolean main,Set<String> actual,Set<String> ids,Set<Item> items,Map<String,Integer> counts){int last=-1;String lastId="";for(ItemStack stack:entries){context.assertTrue(stack.getItem() instanceof BlockItem&&stack.getCount()==1,"placeable singleton entry");BlockItem item=(BlockItem)stack.getItem();String id=Registries.ITEM.getId(item).getPath();int section=ArchitectureCreativeCatalog.section(item);context.assertTrue((main?section==0:section>=1),"entry belongs to its tab: "+id);context.assertTrue(section>=last&&(section!=last||id.compareTo(lastId)>=0),"stable tab order");last=section;lastId=id;ids.add(id);items.add(item);counts.merge(main?"main":"technical."+section,1,Integer::sum);context.assertTrue(actual.add(signature(stack)),"no duplicate catalog entry");}}
 private static boolean allowed(BlockItem item,BlockState state){if(!(item.getBlock() instanceof ArchitectureBlock block)||block.definition.placement_properties==null)return true;for(var entry:block.definition.placement_properties.entrySet()){Property<?> p=block.getStateManager().getProperty(entry.getKey());if(p!=null&&!entry.getValue().equals(value(state,p)))return false;}return true;}
 private static void place(TestContext context,PlayerEntity player,BlockPos clicked,ItemStack stack){ArchitectureBlock block=(ArchitectureBlock)((BlockItem)stack.getItem()).getBlock();clear(context,clicked);context.getWorld().setBlockState(clicked,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);ItemStack before=stack.copy();player.setStackInHand(Hand.MAIN_HAND,stack);var result=stack.useOnBlock(new ItemUsageContext(context.getWorld(),player,Hand.MAIN_HAND,stack,new BlockHitResult(Vec3d.ofCenter(clicked).add(0,.5,0),Direction.UP,clicked,false)));BlockState placed=context.getWorld().getBlockState(clicked.up());context.assertTrue(result.isAccepted()&&placed.isOf(block)&&stack.isEmpty(),"catalog item places and consumes: "+Registries.ITEM.getId(before.getItem()));BlockState requested=ArchitectureBlockItem.applyStateTag(block.getDefaultState(),before);for(String name:ART){var property=block.getStateManager().getProperty(name);if(property!=null)context.assertTrue(placed.get(property).equals(requested.get(property)),"placement retains selected art: "+block.definition.id+"."+name);}context.getWorld().breakBlock(clicked.up(),false,player);}
 private static boolean small(ArchitectureBlock block,ItemStack stack){BlockState state=ArchitectureBlockItem.applyStateTag(block.getDefaultState(),stack);return GeometryRuntime.anchor(state,Direction.UP).equals(BlockPos.ORIGIN)&&GeometryRuntime.state(state).parsedCells.keySet().stream().allMatch(p->Math.abs(p.getX())<=2&&Math.abs(p.getZ())<=2&&p.getY()>=0&&p.getY()<=3);}
 private static void clear(TestContext context,BlockPos root){for(int x=-8;x<=8;x++)for(int y=-1;y<=15;y++)for(int z=-8;z<=8;z++)context.getWorld().removeBlock(root.add(x,y,z),false);}
 private static String signature(ItemStack stack){return ArchitectureCreativeCatalog.candidateKey(stack);}
 private static String signature(Item item,BlockState state){StringBuilder result=new StringBuilder(Registries.ITEM.getId(item).getPath());for(String name:ART){result.append('|').append(name).append('=');var property=state.getBlock().getStateManager().getProperty(name);if(property!=null)result.append(value(state,property));}return result.toString();}
 @SuppressWarnings({"rawtypes","unchecked"}) private static String value(BlockState state,Property property){return property.name(state.get(property));}
 private static String tagValue(ItemStack stack,String key){var tag=stack.getSubNbt("BlockStateTag");return tag==null?"":tag.getString(key);}
 private static Set<String> union(Set<String>a,Set<String>b){Set<String> result=new TreeSet<>(a);result.addAll(b);return result;}
 private static Set<String> difference(Set<String>a,Set<String>b){Set<String> result=new TreeSet<>(a);result.removeAll(b);return result;}
}
