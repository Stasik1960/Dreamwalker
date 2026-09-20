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
 public static final class Data {public List<Definition> blocks;public List<List<double[]>> shapes;public Map<String,String> emissive_textures;public Map<String,String> compat_layers;}
 public static final class Definition {
  public String id,source,layer,kind,offset,behavior,connection_family;
  public String semantic;
  public float hardness,resistance,slipperiness,velocity,jump;
  public boolean extra_facing,custom_geometry,full_cube,emissive,animated,orphan,modular,creative,logical;
  public Map<String,List<String>> properties;
  public Map<String,String> defaults;
  @com.google.gson.annotations.SerializedName("default") public Map<String,String> defaultProperties;
  public Map<String,int[]> states;
  /** Complete canonical state key to an internal logical mesh key. */
  public Map<String,String> models;
  public transient Block sourceBlock;
  public transient Map<String,Property<?>> propertyObjects=new LinkedHashMap<>();
 }
 public static Identifier id(String path){return new Identifier(ID,path);}
 @SuppressWarnings({"rawtypes","unchecked"}) public static String value(Property p,Comparable v){return p.name(v);}
 public static String key(BlockState state){List<String> entries=new ArrayList<>();state.getEntries().forEach((p,v)->entries.add(p.getName()+"="+value(p,v)));Collections.sort(entries);return String.join(",",entries);}
 @SuppressWarnings({"rawtypes","unchecked"}) public static BlockState set(BlockState state,Property property,String value){Optional<?> parsed=property.parse(value);if(parsed.isEmpty())throw new IllegalArgumentException("Invalid state value "+property+"="+value);return state.with(property,(Comparable)parsed.get());}
 static Data loadDefinitions(){
  Gson gson=new Gson();Data legacy=readDefinitions(gson,"/bloodborne_blocks/definitions.json");
  Data modular=readDefinitions(gson,"/bloodborne_blocks/v2/definitions.json");
  Data logical=readDefinitions(gson,"/bloodborne_blocks/logical/definitions.json");
  if(legacy.blocks==null||modular.blocks==null||logical.blocks==null)throw new IllegalStateException("Definitions must contain blocks");
  Set<String> ids=new HashSet<>();for(Definition definition:legacy.blocks)if(!ids.add(definition.id))throw new IllegalStateException("Duplicate legacy block "+definition.id);
  for(Definition definition:modular.blocks){
   if(!definition.modular||definition.id==null||!definition.id.startsWith("m_"))throw new IllegalStateException("Invalid modular definition "+definition.id);
   if(!ids.add(definition.id))throw new IllegalStateException("Legacy/modular ID conflict "+definition.id);
  }
  for(Definition definition:logical.blocks){
   if(!definition.logical||definition.id==null||!definition.id.startsWith("o_")||definition.behavior==null||definition.models==null)throw new IllegalStateException("Invalid logical definition "+definition.id);
   if(!ids.add(definition.id))throw new IllegalStateException("Logical ID conflict "+definition.id);
  }
  legacy.blocks=new ArrayList<>(legacy.blocks);legacy.blocks.addAll(modular.blocks);legacy.blocks.addAll(logical.blocks);return legacy;
 }
 private static Data readDefinitions(Gson gson,String path){
  try(InputStream stream=BloodborneBlocks.class.getResourceAsStream(path)){
   if(stream==null)throw new IOException("Missing generated "+path);return gson.fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Data.class);
  }catch(IOException|RuntimeException e){throw new IllegalStateException("Cannot load Bloodborne architecture "+path,e);}
 }
 private static Set<String> loadLegacyCreative(){
  try(InputStream stream=BloodborneBlocks.class.getResourceAsStream("/bloodborne_blocks/v2/legacy-creative.json")){
   if(stream==null)throw new IOException("Missing legacy creative allow-list");String[] ids=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),String[].class);return Set.of(ids);
  }catch(IOException|RuntimeException e){throw new IllegalStateException("Cannot load legacy creative allow-list",e);}
 }
 static void prepareDefinition(Definition d){
  Identifier source=new Identifier(d.source);if(!Registries.BLOCK.containsId(source))throw new IllegalStateException("Missing source block "+source);d.sourceBlock=Registries.BLOCK.get(source);d.propertyObjects.clear();
  for(String name:d.properties.keySet()){
   Property<?>p=d.sourceBlock.getStateManager().getProperty(name);
   if(p==null&&name.equals("facing"))p=net.minecraft.state.property.Properties.HORIZONTAL_FACING;
   if(p==null&&d.logical&&name.equals("face"))p=net.minecraft.state.property.Properties.WALL_MOUNT_LOCATION;
   if(p==null&&name.equals("open"))p=net.minecraft.state.property.Properties.OPEN;
   if(p==null&&name.equals("waterlogged"))p=net.minecraft.state.property.Properties.WATERLOGGED;
   if(p==null&&name.equals("assembled"))p=ASSEMBLED;
   if(p==null&&Set.of("north","east","south","west","up","down").contains(name))p=BooleanProperty.of(name);
   if(p==null)throw new IllegalStateException("Unknown property "+d.id+"."+name);
   d.propertyObjects.put(name,p);
  }
 }
 static boolean creativeVisible(Definition definition,Set<String> legacyCreative){return definition.logical||definition.modular&&definition.creative||legacyCreative.contains(definition.id);}
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
  LegacyItemSections.load();
  LogicalItemMigration.load();
  Set<String> legacyCreative=loadLegacyCreative();
  Registry.register(Registries.ITEM_GROUP,id("architecture"),FabricItemGroup.builder().displayName(Text.translatable("itemGroup.bloodborne_blocks.architecture")).icon(()->new ItemStack(BLOCKS.get("stone_bricks"))).entries((context,entries)->BLOCKS.values().stream().filter(b->creativeVisible(b.definition,legacyCreative)).filter(b->!PaletteAliases.hidden(b.definition.id)&&!GeometryRuntime.state(b.getDefaultState()).parsedCells.isEmpty()).forEach(entries::add)).build());
  BloodborneCommands.register();
  System.out.println("BLOODBORNE_BLOCKS_REGISTERED blocks="+BLOCKS.size()+" states="+BLOCKS.values().stream().mapToInt(b->b.getStateManager().getStates().size()).sum());
 }
}
