package dev.dreamwalker.bloodbornedw.v123qa;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.fabricmc.loader.api.FabricLoader;

final class OutlineQaEvidence {
    private static String digest;
    static String productionSha(){
        if(digest!=null)return digest;
        try(var input=OutlineQaEvidence.class.getResourceAsStream("/v123-qa-input.json")){
            String expected=JsonParser.parseReader(new InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().get("productionJarSha256").getAsString();
            var paths=FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getOrigin().getPaths();
            if(paths.size()!=1||!expected.equals(sha(paths.get(0))))throw new IllegalStateException("Installed production artifact differs from QA build binding");
            return digest=expected;
        }catch(Exception failure){throw new IllegalStateException(failure);}
    }
    static String sha(Path file)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));}
}
