package dev.dreamwalker.bloodbornedw.conversion;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

/** Startup opt-in for the separately bound importer; never active in the game mod. */
public final class ConversionChunkControl {
    private static final Map<MinecraftServer,Boolean> VERIFIED = new WeakHashMap<>();
    private static final Set<MinecraftServer> BYPASSED = Collections.newSetFromMap(new WeakHashMap<>());
    private ConversionChunkControl() {}
    public static synchronized boolean active(MinecraftServer server) {
        if (!Boolean.getBoolean("dw.conversion.boundedChunks")) return false;
        if (VERIFIED.containsKey(server)) return true;
        try {
            Path input=ordinary(Path.of("dw-full-conversion-input.json"));
            JsonObject marker=json(input);
            require(marker.get("schemaVersion").getAsInt()==1 && marker.get("explicitConversionAuthorized").getAsBoolean(),"No explicit conversion authorization");
            String mode=marker.get("mode").getAsString();
            require(mode.equals("FULL_ORIGINAL_AFTER_USER_COMMAND")||mode.equals("FIXTURE_NATIVE_TEST")||mode.equals("CITY_STATIC_ARCHITECTURE_AFTER_COMPLETE"),"Unsupported conversion mode");
            require(marker.get("offlinePreparationStatus").getAsString().equals("PASS_DERIVED_COPY_PREPARED"),"Unprepared copy");
            Path target=ordinary(server.getSavePath(WorldSavePath.ROOT));
            require(target.equals(ordinary(Path.of(marker.get("targetWorldPath").getAsString()))),"Startup marker belongs to another world");
            JsonObject prepared=json(ordinary(target.resolve("dw-derived-copy.json")));
            String operation=UUID.fromString(marker.get("operationId").getAsString()).toString();
            require(prepared.get("operationId").getAsString().equals(operation),"Derived operation mismatch");
            require(prepared.get("status").getAsString().equals("PASS_DERIVED_COPY_PREPARED"),"Partial derived copy");
            require(ordinary(Path.of(prepared.get("targetRealPath").getAsString())).equals(target),"Derived target mismatch");
            String production=marker.get("productionJarSha256").getAsString();
            Path jar=ordinary(Path.of(System.getProperty("dw.conversion.productionJar","")));
            require(Files.isRegularFile(jar,LinkOption.NOFOLLOW_LINKS)&&sha(jar).equals(production),"Production JAR mismatch");
            try(InputStream stream=ConversionChunkControl.class.getResourceAsStream("/dw-conversion-binding.json")){
                require(stream!=null,"Missing converter binding");
                JsonObject binding=JsonParser.parseReader(new InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                require(binding.get("productionJarSha256").getAsString().equals(production),"Converter binding mismatch");
            }
            Path checkpoint=target.resolve(mode.equals("CITY_STATIC_ARCHITECTURE_AFTER_COMPLETE")?"dw-static-architecture-checkpoint.json":"dw-conversion-checkpoint.json");
            if(Files.exists(checkpoint,LinkOption.NOFOLLOW_LINKS)){
                JsonObject cp=json(ordinary(checkpoint));
                require(cp.get("operationId").getAsString().equals(operation)&&cp.get("productionJarSha256").getAsString().equals(production),"Checkpoint identity mismatch");
                require(ordinary(Path.of(cp.get("targetWorldPath").getAsString())).equals(target),"Checkpoint target mismatch");
            }
            VERIFIED.put(server,true);
            System.out.println("DW_CONVERSION_STARTUP bounded=true verified=true spawnTicketSuppressed=true");
            return true;
        } catch(Exception failure) { throw new IllegalStateException("Bounded conversion startup refused before spawn preload",failure); }
    }
    public static synchronized void bypassed(MinecraftServer server){require(active(server),"Not a verified importer");BYPASSED.add(server);}
    public static synchronized boolean spawnPreloadBypassed(MinecraftServer server){return BYPASSED.contains(server);}
    private static Path ordinary(Path path)throws IOException{
        Path absolute=path.toAbsolutePath().normalize(),real=absolute.toRealPath();
        require(real.equals(absolute)&&!Files.isSymbolicLink(absolute),"Aliased startup path: "+absolute);return real;
    }
    private static JsonObject json(Path path)throws IOException{
        require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&Files.size(path)<=4*1024*1024,"Invalid startup marker");
        try(Reader reader=Files.newBufferedReader(path)){return JsonParser.parseReader(reader).getAsJsonObject();}
    }
    private static String sha(Path path)throws Exception{
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(InputStream stream=Files.newInputStream(path)){byte[] block=new byte[1024*1024];for(int count;(count=stream.read(block))>=0;)hash.update(block,0,count);}
        return HexFormat.of().formatHex(hash.digest());
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}

