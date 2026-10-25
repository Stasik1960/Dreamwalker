package dev.dreamwalker.bloodbornedw.nativegrid;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeSourceShift;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Exact finite future helper cells of each installed openable SourcePose. */
public final class NativeGridDoorFootprintExport {
    private NativeGridDoorFootprintExport() {}
    static void register() {
        String input=System.getProperty("nativegrid.doorFootprints.request");
        if(input==null)return;
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            Path request=Path.of(input).toAbsolutePath().normalize();
            Path output=Path.of(System.getProperty("nativegrid.doorFootprints.out",request.resolveSibling("door-footprints.json").toString()));
            JsonObject report=new JsonObject();
            try {
                Gson gson=new GsonBuilder().setPrettyPrinting().create();
                JsonObject document=JsonParser.parseString(Files.readString(request)).getAsJsonObject();
                JsonArray results=new JsonArray();
                for(JsonElement raw:document.getAsJsonArray("poses")) {
                    JsonObject row=raw.getAsJsonObject();
                    BlockState state=Registries.BLOCK.get(new Identifier(row.get("registryId").getAsString())).getDefaultState();
                    if(row.has("properties"))for(var entry:row.getAsJsonObject("properties").entrySet())state=set(state,entry.getKey(),entry.getValue().getAsString());
                    if(!(state.getBlock() instanceof CompositeRootBlock block)||!block.spec.openable)throw new IllegalStateException("Requested future footprint is not an openable source object: "+row.get("key"));
                    NbtCompound payload=new NbtCompound();NbtList shift=new NbtList();
                    for(var n:row.getAsJsonArray("shift"))shift.add(NbtDouble.of(n.getAsDouble()));
                    payload.put("SourceShift",shift);payload.putDouble("MountY",row.has("mountY")?row.get("mountY").getAsDouble():0);
                    if(row.has("sourceCarrierContextVersion"))payload.putInt("SourceCarrierContextVersion",row.get("sourceCarrierContextVersion").getAsInt());
                    java.util.Set<String> seen=new java.util.TreeSet<>();JsonArray union=new JsonArray(),states=new JsonArray();
                    for(boolean open:new boolean[]{false,true}) {
                        BlockState target=state.with(CompositeRootBlock.OPEN,open);JsonArray cells=new JsonArray();
                        for(var cell:CompositeSourceShift.footprint(block.spec,target,payload).keySet()) {
                            JsonArray xyz=new JsonArray();xyz.add(cell.x());xyz.add(cell.y());xyz.add(cell.z());cells.add(xyz);
                            if(seen.add(xyz.toString()))union.add(xyz.deepCopy());
                        }
                        JsonObject variant=new JsonObject();variant.addProperty("open",open);variant.add("cells",cells);states.add(variant);
                    }
                    JsonObject result=new JsonObject();result.addProperty("key",row.get("key").getAsString());result.add("cells",union);result.add("states",states);results.add(result);
                }
                Path loaded=FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getOrigin().getPaths().get(0);
                report.addProperty("schemaVersion",1);report.addProperty("status","PASS");report.addProperty("nativeDescriptor",true);
                report.addProperty("sourceRequestSha256",hash(request));report.addProperty("baselineModJarSha256",hash(loaded));report.add("poses",results);
                Files.createDirectories(output.toAbsolutePath().getParent());Files.writeString(output,gson.toJson(report)+"\n",StandardCharsets.UTF_8);
            } catch(Throwable error) {
                report.addProperty("status","FAIL");report.addProperty("error",error.toString());
                try{Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");}catch(Exception ignored){}
            }
            server.stop(false);
        });
    }
    private static <T extends Comparable<T>> BlockState set(BlockState state,Property<T> property,String value) {
        return state.with(property,property.parse(value).orElseThrow(()->new IllegalArgumentException("Invalid source property value")));
    }
    private static BlockState set(BlockState state,String name,String value) {
        Property<?> property=state.getBlock().getStateManager().getProperty(name);
        if(property==null)throw new IllegalArgumentException("Unknown source property: "+name);
        return set(state,property,value);
    }
    private static String hash(Path path)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var stream=Files.newInputStream(path)){byte[] buffer=new byte[1024*1024];int n;while((n=stream.read(buffer))!=-1)digest.update(buffer,0,n);}return HexFormat.of().formatHex(digest.digest());
    }
}
