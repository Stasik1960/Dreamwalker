package dev.dreamwalker.bloodbornedw.review;

import com.google.gson.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.fabricmc.loader.api.FabricLoader;

/** QA-only exact-artifact binding. No account/configuration files are read. */
public final class ReviewV10ProofSupport {
    private ReviewV10ProofSupport() {}
    public static String artifact(JsonObject input,JsonObject output)throws Exception{
        String expected=input.has("productionJarSha256")?input.get("productionJarSha256").getAsString():input.get("artifactSha256").getAsString();
        if(!expected.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Exact current artifact SHA256 required");
        boolean matched=false;JsonArray rows=new JsonArray();
        for(Path origin:FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getOrigin().getPaths()){
            JsonObject row=new JsonObject();row.addProperty("path",origin.toString());
            if(Files.isRegularFile(origin)){String actual=sha(Files.readAllBytes(origin));row.addProperty("sha256",actual);matched|=actual.equals(expected);}
            rows.add(row);
        }
        output.add("productionArtifactOrigins",rows);output.addProperty("productionJarSha256",expected);
        if(!matched)throw new IllegalStateException("Loaded ordinary production artifact differs from explicit V10 marker");return expected;
    }
    public static String sha(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    public static void write(Path output,JsonObject report)throws Exception{Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");}
}
