package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Render-only supplements keep frozen cell indices, physics and chunk hashes
 * unchanged. Generated from actual baked source models, never world edits. */
public final class NativeVisualRepairs {
    public record Group(int originalMesh,byte[] extra){}
    private record Data(List<Group> groups,Map<Integer,Integer> visuals){}
    private static final class Holder {static final Data DATA=load();}
    private NativeVisualRepairs(){}
    private static Data load(){
        try(var in=NativeVisualRepairs.class.getResourceAsStream(NativeGridCatalog.DIRECTORY+"visual-repairs.json")){
            if(in==null)return new Data(List.of(),Map.of());
            var n=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            if(n.get("schemaVersion").getAsInt()!=1||!n.get("catalogSha256").getAsString().equals(NativeVisualLayout.catalogSha()))throw new IllegalArgumentException("Visual repair catalog/schema mismatch");
            List<Group> groups=new ArrayList<>();
            for(var item:n.getAsJsonArray("groups")){
                var row=item.getAsJsonObject();int mesh=row.get("mesh").getAsInt();byte[] extra=Base64.getDecoder().decode(row.get("quads").getAsString());
                if(mesh<0||mesh>=NativeGridCatalog.quadCounts().size()||extra.length==0||extra.length%139!=0||extra.length>139*4096)throw new IllegalArgumentException("Visual repair mesh/quad bounds");
                groups.add(new Group(mesh,extra));
            }
            Map<Integer,Integer> visuals=new HashMap<>();
            for(var entry:n.getAsJsonObject("visuals").entrySet()){
                int visual=Integer.parseInt(entry.getKey()),group=entry.getValue().getAsInt();
                if(visual<0||visual>=NativeGridCatalog.cells().size()||NativeVisualLayout.physics(visual)<0||group<0||group>=groups.size()||NativeGridCatalog.cell(visual).mesh()!=groups.get(group).originalMesh())throw new IllegalArgumentException("Visual repair reference mismatch");
                visuals.put(visual,group);
            }
            return new Data(List.copyOf(groups),Map.copyOf(visuals));
        }catch(IOException|RuntimeException e){throw new IllegalStateException("Cannot read bounded native visual repairs",e);}
    }
    public static List<Group> groups(){return Holder.DATA.groups;}
    public static int mesh(int visual){int group=Holder.DATA.visuals.getOrDefault(visual,-1);return group<0?NativeGridCatalog.cell(visual).mesh():NativeGridCatalog.quadCounts().size()+group;}
    public static boolean visible(int visual){return NativeGridCatalog.quadCounts().get(NativeGridCatalog.cell(visual).mesh())>0||Holder.DATA.visuals.containsKey(visual);}
}
