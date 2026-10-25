package dev.dreamwalker.bloodbornedw.nativegridqa;

import com.google.gson.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

/** One opt-in authoring startup. Pure registered-state predicates only: no
 * chunk requests, world scan, cell mutations or changes to game startup. */
public final class NativeGridBridgeAudit implements ModInitializer {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    @Override public void onInitialize(){
        String request=System.getProperty("dw.nativegridqa.bridge.request");
        if(request==null)return;
        String output=System.getProperty("dw.nativegridqa.bridge.output");
        if(output==null)throw new IllegalArgumentException("Bridge audit output path is required");
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            JsonObject receipt=new JsonObject();JsonArray results=new JsonArray();receipt.add("results",results);
            receipt.addProperty("worldReads",0);receipt.addProperty("worldWrites",0);receipt.addProperty("nativeRegistryQueries",false);int checked=0;
            try{
                Path input=Path.of(request).toAbsolutePath().normalize();
                if(!Files.isRegularFile(input)||Files.isSymbolicLink(input)||Files.size(input)>16*1024*1024)throw new IllegalArgumentException("Expected a finite ordinary bridge request");
                var mod=FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow();
                var origins=mod.getOrigin().getPaths();
                if(origins.size()!=1||!Files.isRegularFile(origins.get(0)))throw new IllegalArgumentException("Expected one actual production JAR origin");
                receipt.addProperty("runtimeJar",origins.get(0).toAbsolutePath().toString());
                receipt.addProperty("runtimeSha256",sha(origins.get(0)));
                receipt.addProperty("modVersion",mod.getMetadata().getVersion().getFriendlyString());
                receipt.addProperty("lithiumLoaded",FabricLoader.getInstance().isModLoaded("lithium"));
                receipt.add("stateStorageMetrics",JSON.toJsonTree(Class.forName("dev.dreamwalker.bloodbornedw.composite.CompactStateTransitions").getMethod("metrics").invoke(null)));
                String catalogSha=sha(mod.findPath("assets/bloodborne_dw/native_grid/catalog.json").orElseThrow());
                receipt.addProperty("catalogSha256",catalogSha);receipt.addProperty("bridgePlanSha256",sha(input));
                JsonObject document=JsonParser.parseString(Files.readString(input)).getAsJsonObject();
                if(document.get("schemaVersion").getAsInt()!=1||!document.get("catalogueSha256").getAsString().equals(catalogSha))throw new IllegalArgumentException("Bridge request/catalog binding differs");
                // No new runtime class is linked when this optional authoring
                // mode is absent, including on a baseline production JAR.
                var validate=Class.forName("dev.dreamwalker.bloodbornedw.composite.NativeGridBridge").getMethod("validate",int.class,BlockState.class);
                JsonArray pairs=document.getAsJsonArray("pairs");if(pairs==null||pairs.size()==0||pairs.size()>32768)throw new IllegalArgumentException("Bridge pair budget exceeded/empty");
                boolean pass=true;
                for(JsonElement raw:pairs){
                    JsonObject pair=raw.getAsJsonObject(),row=new JsonObject();row.add("input",pair);results.add(row);
                    try{
                        JsonObject encoded=pair.getAsJsonObject("state");Identifier id=new Identifier(encoded.get("registryId").getAsString());
                        if(!Registries.BLOCK.containsId(id))throw new IllegalArgumentException("Unknown carrier registry ID");
                        BlockState state=Registries.BLOCK.get(id).getDefaultState();
                        receipt.addProperty("nativeRegistryQueries",true);
                        if(encoded.has("properties"))for(var field:encoded.getAsJsonObject("properties").entrySet()){
                            Property<?> property=state.getBlock().getStateManager().getProperty(field.getKey());
                            if(property==null)throw new IllegalArgumentException("Unknown carrier state property");
                            state=with(state,property,field.getValue().getAsString());
                        }
                        row.addProperty("actualState",state.toString());row.addProperty("opaque",state.isOpaque());row.addProperty("sidedTransparency",state.hasSidedTransparency());
                        row.addProperty("luminance",state.getLuminance());row.addProperty("fluidEmpty",state.getFluidState().isEmpty());
                        validate.invoke(null,pair.get("cell").getAsInt(),state);row.addProperty("status","PASS");
                    }catch(Throwable failure){pass=false;Throwable reason=failure instanceof InvocationTargetException invoked?invoked.getCause():failure;row.addProperty("status","FAIL");row.addProperty("error",reason.toString());}
                    checked++;
                }
                receipt.addProperty("status",pass?"PASS":"FAIL");
            }catch(Throwable failure){receipt.addProperty("status","FAIL");receipt.addProperty("error",failure.toString());}
            receipt.addProperty("pairsChecked",checked);
            try{
                Path target=Path.of(output).toAbsolutePath().normalize();Files.createDirectories(target.getParent());
                Path temporary=target.resolveSibling(target.getFileName()+".writing");Files.writeString(temporary,JSON.toJson(receipt));
                Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            }catch(IOException failure){throw new UncheckedIOException("Cannot save bridge audit receipt",failure);}
            finally{server.stop(false);}
        });
    }
    private static <T extends Comparable<T>> BlockState with(BlockState state,Property<T> property,String value){return state.with(property,property.parse(value).orElseThrow());}
    private static String sha(Path file)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream input=Files.newInputStream(file)){byte[] bytes=new byte[1<<20];for(int n;(n=input.read(bytes))!=-1;)digest.update(bytes,0,n);}
        return HexFormat.of().formatHex(digest.digest());
    }
}
