package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.*;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.Heightmap;

/** Addressed native-state queries in a disposable empty dedicated QA server.
 * No target/source map is opened, no state is installed and no game ticker is
 * added. The regular server shutdown path completes after the durable receipt.
 */
public final class NativeGridPhysicsExport implements ModInitializer {
    private static final Gson JSON=new Gson();
    @Override public void onInitialize(){
        String input=System.getProperty("dw.nativegrid.physics.request");
        if(input==null)return;
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            Path output=Path.of(System.getProperty("dw.nativegrid.physics.output"));
            try{Files.createDirectories(output);export(Path.of(input),output);write(output.resolve("physics-result.json"),Map.of("status","PASS","worldStatesWritten",0,"actualSourceMapOpened",false));}
            catch(Throwable failure){failure.printStackTrace();try{Files.createDirectories(output);write(output.resolve("physics-result.json"),Map.of("status","FAIL","error",failure.toString(),"worldStatesWritten",0));}catch(IOException ignored){}}
            server.stop(false);
        });
    }
    private static void export(Path input,Path output)throws Exception{
        JsonElement parsed=JsonParser.parseString(Files.readString(input));
        JsonArray requested=parsed.isJsonArray()?parsed.getAsJsonArray():parsed.getAsJsonObject().has("nativeStates")?parsed.getAsJsonObject().getAsJsonArray("nativeStates"):parsed.getAsJsonObject().getAsJsonArray("states");
        Set<BlockState> seen=new HashSet<>();int count=0;
        try(BufferedWriter stream=Files.newBufferedWriter(output.resolve("native-physics.jsonl"),StandardCharsets.UTF_8)){
            for(var raw:requested){JsonObject row=raw.getAsJsonObject();BlockState state=state(row);if(!seen.add(state))continue;
                if(state.hasBlockEntity())throw new IllegalArgumentException("A real BlockEntity is not a native substrate: "+row);
                var view=EmptyBlockView.INSTANCE;var pos=BlockPos.ORIGIN;JsonObject out=new JsonObject();out.add("state",row.deepCopy());out.addProperty("renderType",state.getRenderType().name());out.addProperty("nativeVisualComplete",false);out.add("quads",new JsonArray());
                out.add("collision",boxes(state.getCollisionShape(view,pos,ShapeContext.absent())));out.add("outline",boxes(state.getOutlineShape(view,pos,ShapeContext.absent())));out.add("culling",boxes(state.getCullingShape(view,pos)));out.add("sides",boxes(state.getSidesShape(view,pos)));out.add("climbing",new JsonArray());
                JsonObject optics=new JsonObject();optics.addProperty("opacity",state.getOpacity(view,pos));optics.addProperty("luminance",state.getLuminance());optics.addProperty("ambientOcclusion",state.getAmbientOcclusionLightLevel(view,pos));optics.addProperty("transparent",state.isTransparent(view,pos));optics.addProperty("sidedTransparency",state.hasSidedTransparency());optics.addProperty("opaque",state.isOpaque());optics.addProperty("opaqueFullCube",state.isOpaqueFullCube(view,pos));optics.addProperty("solidBlock",state.isSolidBlock(view,pos));optics.addProperty("suffocates",state.shouldSuffocate(view,pos));optics.addProperty("blockVision",state.shouldBlockVision(view,pos));out.add("optics",optics);
                var fluid=state.getFluidState();if(fluid.isEmpty())out.add("fluid",JsonNull.INSTANCE);else{JsonObject value=new JsonObject();String id=Registries.FLUID.getId(fluid.getFluid()).toString();if(id.endsWith("flowing_water"))id="minecraft:water";if(id.endsWith("flowing_lava"))id="minecraft:lava";value.addProperty("id",id);value.addProperty("level",fluid.getLevel());value.addProperty("falling",fluid.contains(FlowableFluid.FALLING)&&fluid.get(FlowableFluid.FALLING));out.add("fluid",value);}
                JsonObject heights=new JsonObject();for(var type:Heightmap.Type.values())heights.addProperty(type.name(),type.getBlockPredicate().test(state));out.add("heightmapPredicates",heights);
                out.addProperty("isAir",state.isAir());out.addProperty("hasBlockEntity",false);out.addProperty("className",state.getBlock().getClass().getName());stream.write(JSON.toJson(out));stream.newLine();count++;
            }
        }
        write(output.resolve("physics-summary.json"),Map.of("schemaVersion",1,"states",count,"physicsSource","Actual blocks.2 state queries on EmptyBlockView at origin; exact VoxelShape boxes and Heightmap.Type predicates","gameMapOpened",false,"worldStatesWritten",0));
    }
    private static JsonArray boxes(VoxelShape shape){JsonArray out=new JsonArray();for(var box:shape.getBoundingBoxes())out.add(JSON.toJsonTree(new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ}));return out;}
    @SuppressWarnings({"rawtypes","unchecked"})private static BlockState state(JsonObject row){String id=row.get("registryId").getAsString();Identifier identifier=new Identifier(id);if(!Registries.BLOCK.containsId(identifier))throw new IllegalArgumentException("Unknown registry "+id);BlockState state=Registries.BLOCK.get(identifier).getDefaultState();if(row.has("properties"))for(var entry:row.getAsJsonObject("properties").entrySet()){Property property=state.getBlock().getStateManager().getProperty(entry.getKey());if(property==null)throw new IllegalArgumentException("Unknown property "+entry);state=state.with(property,(Comparable)property.parse(entry.getValue().getAsString()).orElseThrow());}return state;}
    private static void write(Path path,Object data)throws IOException{Files.writeString(path,JSON.toJson(data)+"\n",StandardCharsets.UTF_8);}
}
