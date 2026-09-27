package dev.dreamwalker.bloodborneblocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Registry-backed creative entries; only artistic axes multiply item stacks. */
final class ArchitectureCreativeCatalog {
 static final List<String> ART_PROPERTIES=List.of("variant","visual");
 private ArchitectureCreativeCatalog() {}

 static int section(BlockItem item){
  if(!(item.getBlock() instanceof ArchitectureBlock block))return 0;
  var d=block.definition;
  if(d.logical||ReviewedWallConnections.ID.equals(d.id))return 0;
  if(d.whole_owner)return 1;
  return d.models==null?2:3;
 }

 static List<ItemStack> entries(){
  List<BlockItem> items=Registries.ITEM.stream()
   .filter(item->Registries.ITEM.getId(item).getNamespace().equals(BloodborneBlocks.ID))
   .filter(BlockItem.class::isInstance).map(BlockItem.class::cast)
   .sorted(Comparator.comparingInt(ArchitectureCreativeCatalog::section)
    .thenComparing(item->Registries.ITEM.getId(item).toString())).toList();
  List<ItemStack> result=new ArrayList<>();
  for(BlockItem item:items){
   if(!(item.getBlock() instanceof ArchitectureBlock block)){result.add(new ItemStack(item));continue;}
   List<ItemStack> choices=new ArrayList<>();choices.add(BloodborneBlocks.creativeStack(block));
   for(String name:ART_PROPERTIES){
    var property=block.getStateManager().getProperty(name);if(property==null)continue;
    String forced=block.definition.placement_properties==null?null:block.definition.placement_properties.get(name);
    List<ItemStack> expanded=new ArrayList<>();
    for(ItemStack stack:choices)for(String value:block.definition.properties.get(name)){
     // A value reset by existing placement policy is not a separately placeable item.
     if(forced!=null&&!forced.equals(value))continue;
     ItemStack choice=stack.copy();choice.getOrCreateSubNbt("BlockStateTag").putString(name,value);expanded.add(choice);
    }
    choices=expanded;
   }
   result.addAll(choices);
  }
  return result;
 }

 /** Also used by item rendering: ignore facing, open, lit and service-state NBT. */
 static String itemModelKey(ArchitectureBlock block,ItemStack stack){
  BlockState state=block.getDefaultState();var tag=stack.getSubNbt("BlockStateTag");
  if(tag!=null)for(String name:ART_PROPERTIES){
   var property=block.getStateManager().getProperty(name);
   if(property!=null&&tag.contains(name,8)&&property.parse(tag.getString(name)).isPresent())
    state=BloodborneBlocks.set(state,property,tag.getString(name));
  }
  return BloodborneBlocks.key(BloodborneBlocks.applyPlacementProperties(block.definition,state));
 }

 static void tooltip(ArchitectureBlockItem item,ItemStack stack,List<Text> lines){
  String category=switch(section(item)){case 1->"whole_owner";case 2->"native_compat";case 3->"city_section";default->"building";};
  lines.add(Text.translatable("tooltip.bloodborne_blocks.catalog."+category).formatted(Formatting.GRAY));
  var tag=stack.getSubNbt("BlockStateTag");
  if(tag!=null)for(String name:ART_PROPERTIES)if(tag.contains(name,8))
   lines.add(Text.translatable("tooltip.bloodborne_blocks.catalog."+name,tag.getString(name)).formatted(Formatting.GRAY));
  if(section(item)>=2)lines.add(Text.literal(Registries.ITEM.getId(item).toString()).formatted(Formatting.DARK_GRAY));
 }
}
