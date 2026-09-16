package dev.dreamwalker.bloodborneblocks;

import com.google.gson.Gson;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.shape.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server-safe registry of static architectural blocks. Vanilla registries are only read. */
public final class BloodborneBlocks implements ModInitializer {
 public static final String ID="bloodborne_blocks";
 public static final Map<String,ArchitectureBlock> BLOCKS=new LinkedHashMap<>();
 public static Data DATA;
 public static VoxelShape[] SHAPES;
 public static VoxelShape[] SELECTION_SHAPES;
 public static final class Data {public List<Definition> blocks;public List<List<double[]>> shapes;public Map<String,String> emissive_textures;public Map<String,String> compat_layers;}
 public static final class Definition {
  public String id,source,layer,kind,offset;
  public float hardness,resistance,slipperiness,velocity,jump;
  public boolean extra_facing,custom_geometry,full_cube,emissive,animated,orphan;
  public Map<String,List<String>> properties;
  public Map<String,String> defaults;
  @com.google.gson.annotations.SerializedName("default") public Map<String,String> defaultProperties;
  public Map<String,int[]> states;
  public transient Block sourceBlock;
  public transient Map<String,Property<?>> propertyObjects=new LinkedHashMap<>();
 }
 public static Identifier id(String path){return new Identifier(ID,path);}
 @SuppressWarnings({"rawtypes","unchecked"}) public static String value(Property p,Comparable v){return p.name(v);}
 public static String key(BlockState state){List<String> entries=new ArrayList<>();state.getEntries().forEach((p,v)->entries.add(p.getName()+"="+value(p,v)));Collections.sort(entries);return String.join(",",entries);}
 @SuppressWarnings({"rawtypes","unchecked"}) public static BlockState set(BlockState state,Property property,String value){Optional<?> parsed=property.parse(value);if(parsed.isEmpty())throw new IllegalArgumentException("Invalid state value "+property+"="+value);return state.with(property,(Comparable)parsed.get());}
 public void onInitialize(){
  try(InputStream stream=BloodborneBlocks.class.getResourceAsStream("/bloodborne_blocks/definitions.json")){
   if(stream==null)throw new IOException("Missing generated definitions");DATA=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Data.class);
  }catch(IOException e){throw new IllegalStateException("Cannot load Bloodborne architecture",e);}
  SHAPES=new VoxelShape[DATA.shapes.size()];
  SELECTION_SHAPES=new VoxelShape[SHAPES.length];
  for(int i=0;i<SHAPES.length;i++){
   SHAPES[i]=GeneratedShape.of(DATA.shapes.get(i));
   double[] lo={1,1,1}, hi={0,0,0};
   for(double[] box:DATA.shapes.get(i)){
    if(box[3]<=0||box[4]<=0||box[5]<=0||box[0]>=1||box[1]>=1||box[2]>=1)continue;
    for(int a=0;a<3;a++){lo[a]=Math.min(lo[a],Math.max(0,box[a]));hi[a]=Math.max(hi[a],Math.min(1,box[a+3]));}
   }
   SELECTION_SHAPES[i]=hi[0]>lo[0]&&hi[1]>lo[1]&&hi[2]>lo[2]?VoxelShapes.cuboid(lo[0],lo[1],lo[2],hi[0],hi[1],hi[2]):VoxelShapes.cuboid(.25,0,.25,.75,.125,.75);
  }
  for(Definition d:DATA.blocks){
   Identifier source=new Identifier(d.source);if(!Registries.BLOCK.containsId(source))throw new IllegalStateException("Missing source block "+source);d.sourceBlock=Registries.BLOCK.get(source);
   for(String name:d.properties.keySet()){
    Property<?>p=d.sourceBlock.getStateManager().getProperty(name);
    if(p==null&&name.equals("facing"))p=net.minecraft.state.property.Properties.HORIZONTAL_FACING;
    if(p==null)throw new IllegalStateException("Unknown property "+d.id+"."+name);
    d.propertyObjects.put(name,p);
   }
   ArchitectureBlock block=ArchitectureBlock.create(d);Registry.register(Registries.BLOCK,id(d.id),block);Registry.register(Registries.ITEM,id(d.id),new BlockItem(block,new Item.Settings()));BLOCKS.put(d.id,block);
  }
  Registry.register(Registries.ITEM_GROUP,id("architecture"),FabricItemGroup.builder().displayName(Text.translatable("itemGroup.bloodborne_blocks.architecture")).icon(()->new ItemStack(BLOCKS.get("stone_bricks"))).entries((context,entries)->BLOCKS.values().forEach(entries::add)).build());
  System.out.println("BLOODBORNE_BLOCKS_REGISTERED blocks="+BLOCKS.size()+" states="+BLOCKS.values().stream().mapToInt(b->b.getStateManager().getStates().size()).sum());
 }
}
