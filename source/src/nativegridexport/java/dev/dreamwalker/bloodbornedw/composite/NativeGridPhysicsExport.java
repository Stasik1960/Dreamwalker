package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
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
        byte[] requestBytes=Files.readAllBytes(input);String requestSha256=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(requestBytes));
        JsonElement parsed=JsonParser.parseString(new String(requestBytes,StandardCharsets.UTF_8));
        JsonObject object=parsed.isJsonObject()?parsed.getAsJsonObject():null;
        JsonArray requested=parsed.isJsonArray()?parsed.getAsJsonArray():object.has("nativeStates")?object.getAsJsonArray("nativeStates"):object.has("states")?object.getAsJsonArray("states"):new JsonArray();
        JsonObject properties=object!=null&&object.has("stateProperties")?stateProperties(object.getAsJsonArray("stateProperties"),requestSha256):null;
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
        if(!sha(input).equals(requestSha256))throw new IllegalStateException("Exact native authoring request changed during export");
        if(properties!=null)write(output.resolve("state-properties.json"),properties);
        write(output.resolve("physics-summary.json"),Map.of("schemaVersion",1,"states",count,"statePropertyRequests",properties==null?0:properties.getAsJsonArray("states").size(),"physicsSource","Actual blocks.2 state queries on EmptyBlockView at origin; exact VoxelShape boxes and Heightmap.Type predicates","gameMapOpened",false,"worldStatesWritten",0));
    }
    /** Pure state getters only: a BE-capable state is accepted without creating
     * its BE, consulting a world/shape/model, or validating a compiled bridge.
     * These observations are not a decision about retained gameplay functions.
     */
    private static JsonObject stateProperties(JsonArray requested,String requestSha256)throws Exception{
        var origins=FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getOrigin().getPaths();
        if(origins.size()!=1||!Files.isRegularFile(origins.get(0)))throw new IllegalStateException("Exact state properties require one actual production JAR origin");
        JsonObject result=new JsonObject();result.addProperty("schemaVersion",1);result.addProperty("status","PASS");result.addProperty("kind","EXACT_STATE_PROPERTIES");result.addProperty("queryScope","EXACT_STATE_GETTERS_ONLY");result.addProperty("requestSha256",requestSha256);result.addProperty("runtimeSha256",sha(origins.get(0)));result.addProperty("runtimeOrigin",origins.get(0).toAbsolutePath().toString());
        result.addProperty("worldReads",0);result.addProperty("worldWrites",0);result.addProperty("worldStatesWritten",0);result.addProperty("worldQueried",false);result.addProperty("sourceMapOpened",false);result.addProperty("blockEntitiesInstantiated",0);result.addProperty("shapeQueries",0);result.addProperty("renderQueries",0);result.addProperty("bridgeValidationInvoked",false);
        JsonArray rows=new JsonArray();
        for(var raw:requested){
            JsonObject input=raw.getAsJsonObject();BlockState actual=state(input);JsonObject sparse=new JsonObject();sparse.add("registryId",input.get("registryId").deepCopy());sparse.add("properties",input.has("properties")?input.getAsJsonObject("properties").deepCopy():new JsonObject());
            JsonObject row=new JsonObject();row.add("requestedState",sparse);row.add("state",canonicalState(actual));row.addProperty("className",actual.getBlock().getClass().getName());row.addProperty("hasBlockEntity",actual.hasBlockEntity());row.addProperty("opaque",actual.isOpaque());row.addProperty("sidedTransparency",actual.hasSidedTransparency());row.addProperty("luminance",actual.getLuminance());row.addProperty("fluidEmpty",actual.getFluidState().isEmpty());rows.add(row);
        }
        result.add("states",rows);return result;
    }
    private static JsonObject canonicalState(BlockState state){
        JsonObject result=new JsonObject();result.addProperty("registryId",Registries.BLOCK.getId(state.getBlock()).toString());Map<String,String> properties=new TreeMap<>();
        for(var entry:state.getEntries().entrySet())properties.put(entry.getKey().getName(),propertyName(entry.getKey(),entry.getValue()));
        result.add("properties",JSON.toJsonTree(properties));return result;
    }
    @SuppressWarnings({"rawtypes","unchecked"})private static String propertyName(Property property,Comparable value){return property.name(value);}
    private static String sha(Path path)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream stream=Files.newInputStream(path)){byte[] buffer=new byte[1<<16];for(int n;(n=stream.read(buffer))>=0;)digest.update(buffer,0,n);}return HexFormat.of().formatHex(digest.digest());}
    private static JsonArray boxes(VoxelShape shape){JsonArray out=new JsonArray();for(var box:shape.getBoundingBoxes())out.add(JSON.toJsonTree(new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ}));return out;}
    @SuppressWarnings({"rawtypes","unchecked"})private static BlockState state(JsonObject row){String id=row.get("registryId").getAsString();Identifier identifier=new Identifier(id);if(!Registries.BLOCK.containsId(identifier))throw new IllegalArgumentException("Unknown registry "+id);BlockState state=Registries.BLOCK.get(identifier).getDefaultState();if(row.has("properties"))for(var entry:row.getAsJsonObject("properties").entrySet()){Property property=state.getBlock().getStateManager().getProperty(entry.getKey());if(property==null)throw new IllegalArgumentException("Unknown property "+entry);state=state.with(property,(Comparable)property.parse(entry.getValue().getAsString()).orElseThrow());}return state;}
    private static void write(Path path,Object data)throws IOException{Files.writeString(path,JSON.toJson(data)+"\n",StandardCharsets.UTF_8);}
}
