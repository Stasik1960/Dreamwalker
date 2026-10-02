package dev.dreamwalker.bloodborneblocks;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Registry-backed tabs: construction manifest first, historical/technical IDs second. */
final class ArchitectureCreativeCatalog {
 static final List<String> ART_PROPERTIES=List.of("variant","visual");
 private ArchitectureCreativeCatalog() {}

 static boolean main(BlockItem item){return mainIds().contains(Registries.ITEM.getId(item).getPath());}
 static int section(BlockItem item){
  if(main(item))return 0;
  if(!(item.getBlock() instanceof ArchitectureBlock block))return 2;
  var definition=block.definition;if(definition.whole_owner)return 1;
  return definition.models==null?2:3;
 }
 static List<ItemStack> mainEntries(){return entries(true);}
 static List<ItemStack> technicalEntries(){return entries(false);}
 static Map<String,String> redirects(){return Equivalents.REDIRECTS;}
 static String canonicalKey(String candidate){return redirects().getOrDefault(candidate,candidate);}
 /** Stable keys shared with the content/behavior proof generator; absent axes stay empty. */
 static String candidateKey(ItemStack stack){
  StringBuilder key=new StringBuilder(Registries.ITEM.getId(stack.getItem()).getPath());
  BlockState state=stack.getItem() instanceof BlockItem item?ArchitectureBlockItem.applyStateTag(item.getBlock().getDefaultState(),stack):null;
  for(String name:ART_PROPERTIES){key.append('|').append(name).append('=');if(state!=null){var property=state.getBlock().getStateManager().getProperty(name);if(property!=null)key.append(value(state,property));}}
  return key.toString();
 }
 @SuppressWarnings({"rawtypes","unchecked"}) private static String value(BlockState state,net.minecraft.state.property.Property property){return property.name(state.get(property));}
 private static final class Equivalents {
  static final Map<String,String> REDIRECTS=load();
  private static Map<String,String> load(){
   try(var stream=ArchitectureCreativeCatalog.class.getResourceAsStream("/bloodborne_blocks/creative-equivalence.json")){
    if(stream==null)throw new IllegalStateException("Missing creative equivalence proof");
    var document=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),com.google.gson.JsonObject.class);
    if(document.get("schemaVersion").getAsInt()!=1)throw new IllegalStateException("Unsupported creative equivalence proof");
    Map<String,String> redirects=new java.util.LinkedHashMap<>();document.getAsJsonObject("redirects").entrySet().forEach(entry->redirects.put(entry.getKey(),entry.getValue().getAsString()));
    for(var entry:redirects.entrySet())if(entry.getKey().equals(entry.getValue())||redirects.containsKey(entry.getValue()))throw new IllegalStateException("Non-canonical creative redirect: "+entry.getKey());
    return Map.copyOf(redirects);
   }catch(java.io.IOException error){throw new IllegalStateException("Cannot read creative equivalence proof",error);}
  }
 }
 static Set<String> mainIds(){
  java.util.LinkedHashSet<String> ids=new java.util.LinkedHashSet<>(BloodborneBlocks.productionPalette().keySet());ids.add(ReviewedWallConnections.ID);ids.addAll(DocumentOwnerDefinitions.ids());return Set.copyOf(ids);
 }
 private static List<ItemStack> entries(boolean mainTab){
  return candidateEntries(mainTab).stream().filter(stack->!redirects().containsKey(candidateKey(stack))).toList();
 }
 static List<ItemStack> candidateEntries(boolean mainTab){
  List<BlockItem> items=Registries.ITEM.stream().filter(BlockItem.class::isInstance).map(BlockItem.class::cast)
   .filter(item->Registries.ITEM.getId(item).getNamespace().equals(BloodborneBlocks.ID)).filter(item->main(item)==mainTab)
   .sorted(Comparator.comparingInt(ArchitectureCreativeCatalog::section).thenComparing(item->Registries.ITEM.getId(item).toString())).toList();
  List<ItemStack> result=new ArrayList<>();for(BlockItem item:items)result.addAll(artEntries(item));return result;
 }
 private static List<ItemStack> artEntries(BlockItem item){
  if(!(item.getBlock() instanceof ArchitectureBlock block))return List.of(new ItemStack(item));
  List<ItemStack> choices=new ArrayList<>();choices.add(BloodborneBlocks.creativeStack(block));
  for(String name:ART_PROPERTIES){
   var property=block.getStateManager().getProperty(name);if(property==null)continue;
   String forced=block.definition.placement_properties==null?null:block.definition.placement_properties.get(name);List<ItemStack> expanded=new ArrayList<>();
   for(ItemStack stack:choices)for(String value:block.definition.properties.get(name)){if(forced==null||forced.equals(value)){ItemStack choice=stack.copy();choice.getOrCreateSubNbt("BlockStateTag").putString(name,value);expanded.add(choice);}}
   choices=expanded;
  }
  return choices;
 }

 /** Also used by item rendering: ignore facing, open, lit and service-state NBT. */
 static String itemModelKey(ArchitectureBlock block,ItemStack stack){
  BlockState state=block.getDefaultState();var tag=stack.getSubNbt("BlockStateTag");
  if(tag!=null)for(String name:ART_PROPERTIES){var property=block.getStateManager().getProperty(name);if(property!=null&&tag.contains(name,8)&&property.parse(tag.getString(name)).isPresent())state=BloodborneBlocks.set(state,property,tag.getString(name));}
  return BloodborneBlocks.key(BloodborneBlocks.applyPlacementProperties(block.definition,state));
 }
 static void tooltip(ArchitectureBlockItem item,ItemStack stack,List<Text> lines){
  String category=switch(section(item)){case 1->"whole_owner";case 2->"native_compat";case 3->"city_section";default->"building";};lines.add(Text.translatable("tooltip.bloodborne_blocks.catalog."+category).formatted(Formatting.GRAY));
  var tag=stack.getSubNbt("BlockStateTag");if(tag!=null)for(String name:ART_PROPERTIES)if(tag.contains(name,8))lines.add(Text.translatable("tooltip.bloodborne_blocks.catalog."+name,tag.getString(name)).formatted(Formatting.GRAY));
  if(section(item)>=1)lines.add(Text.literal(Registries.ITEM.getId(item).toString()).formatted(Formatting.DARK_GRAY));
 }
}
