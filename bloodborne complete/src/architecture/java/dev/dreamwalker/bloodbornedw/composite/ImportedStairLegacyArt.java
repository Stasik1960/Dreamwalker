package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Identifier;

/** Immutable, reviewed NEW stair art; an item never supplies a model path. */
public final class ImportedStairLegacyArt {
    public static final String KEY="ImportedStairLegacyArt";
    private record Entry(String registry,int variant,Boolean trim,List<CompositeSpec.Part> parts,List<CompositeSpec.Part> openParts) {}
    private static final Map<String,Entry> ENTRIES=load();
    private ImportedStairLegacyArt() {}
    private static Map<String,Entry> load(){
        var input=ImportedStairLegacyArt.class.getResourceAsStream("/bloodborne_dw/imported-stair-legacy-art.json");
        if(input==null)throw new IllegalStateException("Missing reviewed NEW stair appearance table");
        try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
            var document=JsonParser.parseReader(reader).getAsJsonObject();
            if(document.get("schemaVersion").getAsInt()!=1||!document.get("scope").getAsString().equals("POST_V12_NATIVE_STAIRS_APPEARANCE_ONLY"))throw new IllegalArgumentException("Unsupported NEW stair art schema");
            Map<String,Entry> rows=new LinkedHashMap<>();
            for(var value:document.getAsJsonArray("states")){
                var row=value.getAsJsonObject();String old=row.get("oldRegistryId").getAsString(),registry=row.get("registryId").getAsString();int before=row.get("oldVariant").getAsInt(),variant=row.get("variant").getAsInt();String key=row.get("key").getAsString();
                if(!old.startsWith("bloodborne_dw:source_")||!registry.startsWith("bloodborne_dw:source_")||!ImportedStairShapes.isStairPath(new Identifier(registry).getPath())||before<0||before>15||variant<0||variant>=10||!key.equals(old+"|"+before))throw new IllegalArgumentException("Unreviewed NEW stair art key");
                Boolean trim=row.has("trim")?row.get("trim").getAsBoolean():null;
                if(trim!=null&&!registry.equals("bloodborne_dw:source_stairs_polished_deepslate_stairs_bottom"))throw new IllegalArgumentException("Unexpected native stair trim");
                List<CompositeSpec.Part> closed=parts(row.getAsJsonArray("parts"));List<CompositeSpec.Part> opened=row.has("openParts")?parts(row.getAsJsonArray("openParts")):closed;
                if(rows.putIfAbsent(key,new Entry(registry,variant,trim,closed,opened))!=null)throw new IllegalArgumentException("Repeated NEW stair art key");
            }
            if(rows.isEmpty()||rows.size()>512)throw new IllegalArgumentException("NEW stair appearance budget");
            return Collections.unmodifiableMap(rows);
        }catch(Exception failure){throw new IllegalStateException("Invalid reviewed NEW stair appearance table",failure);}
    }
    private static List<CompositeSpec.Part> parts(JsonArray values){
        if(values.size()==0||values.size()>8)throw new IllegalArgumentException("Native source part budget");
        List<CompositeSpec.Part> result=new ArrayList<>();
        for(var value:values){var row=value.getAsJsonObject();Identifier model=new Identifier(row.get("model").getAsString()),alt=row.has("altModel")?new Identifier(row.get("altModel").getAsString()):model;
            if(!model.getNamespace().equals("bloodborne_dw")||!model.getPath().startsWith("remaining/")||!alt.getNamespace().equals("bloodborne_dw")||!alt.getPath().startsWith("remaining/"))throw new IllegalArgumentException("Unreviewed native source model");
            result.add(new CompositeSpec.Part(model,alt,vector(row,"offset",new CompositeSpec.Vec(0,0,0)),number(row,"yaw",0),number(row,"pitch",0),vector(row,"pivot",new CompositeSpec.Vec(8,8,8)),number(row,"extraYaw",0),vector(row,"extraPivot",new CompositeSpec.Vec(8,8,8))));
        }
        return List.copyOf(result);
    }
    private static double number(JsonObject row,String key,double fallback){double value=row.has(key)?row.get(key).getAsDouble():fallback;if(!Double.isFinite(value)||Math.abs(value)>4096)throw new IllegalArgumentException("Native source art coordinate");return value;}
    private static CompositeSpec.Vec vector(JsonObject row,String key,CompositeSpec.Vec fallback){if(!row.has(key))return fallback;var a=row.getAsJsonArray(key);if(a.size()!=3)throw new IllegalArgumentException("Native source art vector");double[] v={a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()};for(double n:v)if(!Double.isFinite(n)||Math.abs(n)>4096)throw new IllegalArgumentException("Native source art coordinate");return new CompositeSpec.Vec(v[0],v[1],v[2]);}
    public static String keyFor(String oldRegistry,int oldVariant){String key=oldRegistry+"|"+oldVariant;return ENTRIES.containsKey(key)?key:null;}
    public static boolean validKey(String key){return key!=null&&ENTRIES.containsKey(key);}
    public static void validatePayload(NbtCompound payload){if(payload!=null&&payload.contains(KEY)&&(!payload.contains(KEY,NbtElement.STRING_TYPE)||!validKey(payload.getString(KEY))))throw new IllegalArgumentException("Unknown NEW stair appearance key");}
    public static List<CompositeSpec.Part> selectedParts(CompositeRootBlock block,BlockState state,NbtCompound payload){
        if(!(block instanceof ImportedStairBlock)||payload==null||!payload.contains(KEY))return block.spec.pose(state).parts();
        validatePayload(payload);Entry entry=ENTRIES.get(payload.getString(KEY));
        if(!entry.registry.equals(block.spec.id.toString())||entry.variant!=ImportedStairShapes.variant(state)||entry.trim!=null&&(!state.contains(ImportedStairBlock.TRIM)||state.get(ImportedStairBlock.TRIM)!=entry.trim))return block.spec.pose(state).parts();
        return state.get(CompositeRootBlock.OPEN)?entry.openParts:entry.parts;
    }
    public static Set<Identifier> models(){Set<Identifier> result=new LinkedHashSet<>();for(Entry row:ENTRIES.values())for(var pose:List.of(row.parts,row.openParts))for(var part:pose){result.add(part.model());result.add(part.altModel());}return Set.copyOf(result);}
}
