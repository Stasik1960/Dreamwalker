package dev.dreamwalker.bloodborneblocks;

import com.google.gson.Gson;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.*;
import net.minecraft.entity.*;
import net.minecraft.registry.*;
import net.minecraft.state.property.Property;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.shape.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server-safe registry of static architectural blocks. Vanilla registries are only read. */
public final class BloodborneBlocks implements ModInitializer {
 public static final String ID="bloodborne_blocks";
 public static final BooleanProperty ASSEMBLED=BooleanProperty.of("assembled");
 public static final Map<String,ArchitectureBlock> BLOCKS=new LinkedHashMap<>();
 public static final ArchitecturePartBlock PART_BLOCK=new ArchitecturePartBlock();
 public static BlockEntityType<ArchitecturePartBlockEntity> PART_BLOCK_ENTITY;
 public static EntityType<ArchitectureSeatEntity> SEAT_ENTITY;
 public static Data DATA;
 public record ProductionEntry(String id,String semantic_label,List<String> source_reviews) {}
 private static final Map<String,ProductionEntry> PRODUCTION=new LinkedHashMap<>();
 private static final class ProductionPalette {int schemaVersion;List<ProductionPaletteEntry> objects;}
 private static final class ProductionPaletteEntry {String id,semantic_label,status;List<String> source_reviews;}
 public static final class Data {public List<Definition> blocks;public List<List<double[]>> shapes;public Map<String,String> emissive_textures;public Map<String,String> compat_layers;}
 public static final class Definition {
  public String id,source,layer,kind,offset,behavior,connection_family,attachment_item;
  public String semantic;
  /** Three canonical north-facing seat contact points, never inferred per tick. */
  public double[][] seat_anchors;
  public float hardness,resistance,slipperiness,velocity,jump;
  public boolean extra_facing,custom_geometry,full_cube,emissive,animated,orphan,modular,creative,logical;
  public Map<String,List<String>> properties;
  /** State values forced only while a logical item is being placed. */
  public Map<String,String> placement_properties;
  public Map<String,String> defaults;
  @com.google.gson.annotations.SerializedName("default") public Map<String,String> defaultProperties;
  public Map<String,int[]> states;
  /** Complete canonical state key to an internal logical mesh key. */
  public Map<String,String> models;
  /** Stable resource-pack paths, independent from immutable gameplay mesh IDs. */
  public Map<String,String> visual_models;
  public transient Block sourceBlock;
  public transient Map<String,Property<?>> propertyObjects=new LinkedHashMap<>();
 }
 public static Identifier id(String path){return new Identifier(ID,path);}
 @SuppressWarnings({"rawtypes","unchecked"}) public static String value(Property p,Comparable v){return p.name(v);}
 public static String key(BlockState state){List<String> entries=new ArrayList<>();state.getEntries().forEach((p,v)->entries.add(p.getName()+"="+value(p,v)));Collections.sort(entries);return String.join(",",entries);}
 @SuppressWarnings({"rawtypes","unchecked"}) public static BlockState set(BlockState state,Property property,String value){Optional<?> parsed=property.parse(value);if(parsed.isEmpty())throw new IllegalArgumentException("Invalid state value "+property+"="+value);return state.with(property,(Comparable)parsed.get());}
 static Data loadDefinitions(){
  Gson gson=new Gson();Data logical=readDefinitions(gson,"/bloodborne_blocks/logical/definitions.json");
  if(logical.blocks==null||logical.blocks.isEmpty())throw new IllegalStateException("Logical definitions must contain blocks");
  Set<String> ids=new HashSet<>();for(Definition definition:logical.blocks){
   if(!definition.logical||definition.id==null||!definition.id.startsWith("o_")||definition.behavior==null||definition.models==null)throw new IllegalStateException("Invalid logical definition "+definition.id);
   if(!ids.add(definition.id))throw new IllegalStateException("Duplicate logical definition "+definition.id);
  }
  loadProductionPalette(gson,ids);return logical;
 }
 private static Data readDefinitions(Gson gson,String path){
  try(InputStream stream=BloodborneBlocks.class.getResourceAsStream(path)){
   if(stream==null)throw new IOException("Missing generated "+path);return gson.fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Data.class);
  }catch(IOException|RuntimeException e){throw new IllegalStateException("Cannot load Bloodborne architecture "+path,e);}
 }
 private static void loadProductionPalette(Gson gson,Set<String> definitionIds){
  try(InputStream stream=BloodborneBlocks.class.getResourceAsStream("/bloodborne_blocks/logical/production-palette.json")){
   if(stream==null)throw new IOException("Missing production palette");ProductionPalette palette=gson.fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),ProductionPalette.class);
   if(palette==null||palette.schemaVersion!=1||palette.objects==null||palette.objects.isEmpty())throw new IllegalStateException("Invalid production palette schema");
   PRODUCTION.clear();Set<String> labels=new HashSet<>();
   for(ProductionPaletteEntry entry:palette.objects){
    if(entry==null||entry.id==null||!entry.id.startsWith("o_")||entry.semantic_label==null||entry.semantic_label.isBlank()||entry.source_reviews==null||entry.source_reviews.isEmpty()||(entry.status!=null&&!"PRODUCTION".equals(entry.status)))throw new IllegalStateException("Invalid production palette entry "+(entry==null?"null":entry.id));
    if(PRODUCTION.putIfAbsent(entry.id,new ProductionEntry(entry.id,entry.semantic_label,List.copyOf(entry.source_reviews)))!=null)throw new IllegalStateException("Duplicate production palette ID "+entry.id);
    if(!labels.add(entry.semantic_label))throw new IllegalStateException("Duplicate production semantic label "+entry.semantic_label);
   }
   if(!PRODUCTION.keySet().equals(definitionIds))throw new IllegalStateException("Production palette/definition membership mismatch: palette="+PRODUCTION.keySet()+" definitions="+definitionIds);
  }catch(IOException|RuntimeException e){throw new IllegalStateException("Cannot load Bloodborne production palette",e);}
 }
 static Map<String,ProductionEntry> productionPalette(){return Collections.unmodifiableMap(PRODUCTION);}
 static ProductionEntry productionEntry(String id){return PRODUCTION.get(id);}
 static void prepareDefinition(Definition d){
  Identifier source=new Identifier(d.source);if(!Registries.BLOCK.containsId(source))throw new IllegalStateException("Missing source block "+source);d.sourceBlock=Registries.BLOCK.get(source);d.propertyObjects.clear();
  for(String name:d.properties.keySet()){
   Property<?>p;
   if(name.equals("embedded")||name.equals("lantern")||name.equals("diagonal")){
    List<String> values=d.properties.get(name);
    if(!d.logical||values==null||values.size()!=2||!new HashSet<>(values).equals(Set.of("false","true")))throw new IllegalStateException("Invalid logical boolean property "+d.id+"."+name);
    p=BooleanProperty.of(name);
   }else p=d.sourceBlock.getStateManager().getProperty(name);
   if(p==null&&d.logical&&Set.of("variant","visual","hand_lantern").contains(name))p=new LogicalVariantProperty(name,d.properties.get(name));
   if(p==null&&d.logical&&name.equals("lit"))p=net.minecraft.state.property.Properties.LIT;
   if(p==null&&name.equals("facing"))p=net.minecraft.state.property.Properties.HORIZONTAL_FACING;
   if(p==null&&d.logical&&name.equals("face"))p=net.minecraft.state.property.Properties.WALL_MOUNT_LOCATION;
   if(p==null&&name.equals("open"))p=net.minecraft.state.property.Properties.OPEN;
   if(p==null&&name.equals("waterlogged"))p=net.minecraft.state.property.Properties.WATERLOGGED;
   if(p==null&&name.equals("assembled"))p=ASSEMBLED;
   if(p==null&&Set.of("north","east","south","west","up","down").contains(name))p=BooleanProperty.of(name);
   if(p==null)throw new IllegalStateException("Unknown property "+d.id+"."+name);
   d.propertyObjects.put(name,p);
  }
  if(d.placement_properties!=null)for(var entry:d.placement_properties.entrySet()){
   Property<?> property=d.propertyObjects.get(entry.getKey());
   List<String> values=d.properties.get(entry.getKey());
   if(!d.logical||property==null||entry.getValue()==null||values==null||!values.contains(entry.getValue())||property.parse(entry.getValue()).isEmpty())throw new IllegalStateException("Invalid placement property "+d.id+"."+entry.getKey()+"="+entry.getValue());
  }
 }
 @SuppressWarnings({"rawtypes","unchecked"}) static BlockState applyPlacementProperties(Definition definition,BlockState state){
  if(!definition.logical||definition.placement_properties==null)return state;
  for(var entry:definition.placement_properties.entrySet())state=set(state,(Property)definition.propertyObjects.get(entry.getKey()),entry.getValue());
  return state;
 }
 static boolean creativeVisible(Definition definition){return definition.logical&&definition.creative;}
 static ItemStack creativeStack(ArchitectureBlock block){
  ItemStack stack=new ItemStack(block);
  if(block.definition.logical&&block.definition.placement_properties!=null)block.definition.placement_properties.forEach(stack.getOrCreateSubNbt("BlockStateTag")::putString);
  return stack;
 }
 public void onInitialize(){
  DATA=loadDefinitions();
  GeometryRuntime.loadAndValidate(DATA);
  Registry.register(Registries.BLOCK,id("architecture_part"),PART_BLOCK);
  PART_BLOCK_ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,id("architecture_part"),BlockEntityType.Builder.create(ArchitecturePartBlockEntity::new,PART_BLOCK).build(null));
  ArchitecturePartBlockEntity.registerValidation();
  SEAT_ENTITY=Registry.register(Registries.ENTITY_TYPE,id("seat"),EntityType.Builder.<ArchitectureSeatEntity>create(ArchitectureSeatEntity::new,SpawnGroup.MISC).setDimensions(.01F,.01F).maxTrackingRange(8).trackingTickInterval(20).disableSaving().disableSummon().build(ID+":seat"));
  for(Definition d:DATA.blocks){
   prepareDefinition(d);
   ArchitectureBlock block=ArchitectureBlock.create(d);Registry.register(Registries.BLOCK,id(d.id),block);Registry.register(Registries.ITEM,id(d.id),new ArchitectureBlockItem(block,new Item.Settings()));BLOCKS.put(d.id,block);
  }
  LogicalAttachments.validateDefinitions(DATA.blocks);
  Registry.register(Registries.ITEM_GROUP,id("architecture"),FabricItemGroup.builder().displayName(Text.translatable("itemGroup.bloodborne_blocks.architecture")).icon(()->new ItemStack(BLOCKS.get("o_c001"))).entries((context,entries)->BLOCKS.values().stream().filter(b->creativeVisible(b.definition)).filter(b->!GeometryRuntime.state(b.getDefaultState()).parsedCells.isEmpty()).map(BloodborneBlocks::creativeStack).forEach(entries::add)).build());
  BloodborneCommands.register();
  System.out.println("BLOODBORNE_BLOCKS_REGISTERED blocks="+BLOCKS.size()+" states="+BLOCKS.values().stream().mapToInt(b->b.getStateManager().getStates().size()).sum());
 }
}
