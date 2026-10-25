package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.state.property.IntProperty;

/** Finite shared visual-to-physics mapping, with no world positions. */
public final class NativeVisualLayout {
    private static int[] mapping, representatives;
    private static String[] layers;
    private static boolean[] ambient;
    private static String catalogSha;
    private static final Map<Integer,List<Integer>> wholeBanks=new TreeMap<>();
    private static final Map<Integer,IntProperty> properties=new HashMap<>();
    private NativeVisualLayout(){}
    public static void initialize(){
        try(var input=NativeVisualLayout.class.getResourceAsStream(NativeGridCatalog.DIRECTORY+"visual_physics.json")){
            if(input==null)throw new FileNotFoundException("Missing visual/physics layout");
            var n=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            if(n.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Visual physics schema");
            catalogSha=n.get("catalogSha256").getAsString();
            mapping=new int[n.getAsJsonArray("cellToPhysics").size()];for(int i=0;i<mapping.length;i++)mapping[i]=n.getAsJsonArray("cellToPhysics").get(i).getAsInt();
            representatives=new int[n.getAsJsonArray("representatives").size()];layers=new String[representatives.length];ambient=new boolean[representatives.length];
            for(int i=0;i<representatives.length;i++){representatives[i]=n.getAsJsonArray("representatives").get(i).getAsInt();layers[i]=n.getAsJsonArray("physicsLayers").get(i).getAsString();ambient[i]=n.getAsJsonArray("physicsAmbient").get(i).getAsBoolean();}
            n.getAsJsonObject("wholeBanks").entrySet().forEach(e->{int bank=Integer.parseInt(e.getKey());var parts=new ArrayList<Integer>();for(var p:e.getValue().getAsJsonArray())parts.add(p.getAsInt());wholeBanks.put(bank,List.copyOf(parts));int lo=Collections.min(parts),hi=Collections.max(parts);if(lo==hi){if(hi<127)hi++;else lo--;}properties.put(bank,IntProperty.of("part",lo,hi));});
            if(mapping.length!=NativeGridCatalog.cells().size())throw new IllegalArgumentException("Visual layout/catalog length");
            for(int i=0;i<mapping.length;i++)if((mapping[i]<0)!=(NativeGridCatalog.membership(i)!=null)||mapping[i]>=representatives.length)throw new IllegalArgumentException("Visual layout role/reference");
        }catch(IOException|RuntimeException failure){throw new IllegalStateException("Cannot load visual physics layout",failure);}
    }
    public static int physics(int visual){if(visual<0||visual>=mapping.length)throw new IllegalArgumentException("Visual index");return mapping[visual];}
    public static int count(){return representatives.length;}
    public static int representative(int physics){return representatives[physics];}
    public static String layer(int physics){return layers[physics];}
    public static boolean ambient(int physics){return ambient[physics];}
    public static String catalogSha(){return catalogSha;}
    public static Map<Integer,List<Integer>> wholeBanks(){return Collections.unmodifiableMap(wholeBanks);}
    public static IntProperty part(int bank){return properties.get(bank);}
}
