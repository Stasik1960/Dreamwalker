package dev.dreamwalker.bloodbornedw.blockassetsqa;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.*;
import net.minecraft.registry.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

/** Asset-only harness: exact production IDs/state schema and exact JAR assets,
 * without booting the unrelated architecture/RP catalog for a model bake. */
public final class SlabAssetFactory implements ModInitializer {
 public static final List<SlabBlock> BLOCKS=new ArrayList<>();
 public static final Set<SlabBlock> DECORATED=new HashSet<>();
 @Override public void onInitialize(){
  try(var in=SlabAssetFactory.class.getResourceAsStream("/bloodborne_dw/restored-material-slabs.json")){
   var rows=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("materials");
   for(var raw:rows){var row=raw.getAsJsonObject();var block=Registry.register(Registries.BLOCK,new Identifier(row.get("id").getAsString()),new AssetSlab());BLOCKS.add(block);if(row.has("decorated")&&row.get("decorated").getAsBoolean())DECORATED.add(block);}
  }catch(Exception e){throw new IllegalStateException(e);}
 }
 private static final class AssetSlab extends SlabBlock {
  AssetSlab(){super(Settings.copy(Blocks.STONE_SLAB).strength(.8F).dynamicBounds());setDefaultState(getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.NORTH));}
  @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){super.appendProperties(b);b.add(Properties.HORIZONTAL_FACING);}
 }
}
