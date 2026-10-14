package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

/** Exact consumed source-carrier shadows of two NEW whole objects. The small
 * finite table is shared; no positions or carrier data are added to owner NBT.
 * Hand placement and every original V12 instance are outside this opt-in. */
public final class ImportedWholeSourceOpticsV127 {
    public static final String CONTEXT="SourceCarrierContextVersion";
    private record Carrier(double x,double y,double z,int opacity){}
    private record Rule(String id,int variant,List<Carrier> carriers){}
    private record Key(String id,int variant,int rotation,double x,double y,double z,double mount){}
    private record Loaded(Map<String,Rule> rules,BlockState opaqueCarrier){}
    private static final Loaded TABLES=load();
    private static final Map<String,Rule> RULES=TABLES.rules;
    private static final Map<Key,Map<Cell,Integer>> CACHE=new ConcurrentHashMap<>();
    private static Loaded load(){
        var input=ImportedWholeSourceOpticsV127.class.getResourceAsStream("/bloodborne_dw/imported-whole-source-optics-v127.json");
        if(input==null)throw new IllegalStateException("Missing reviewed whole source optics");
        try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
            var document=JsonParser.parseReader(reader).getAsJsonObject();if(document.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Whole source optics schema");
            Map<String,Rule> rules=new HashMap<>();BlockState opaqueCarrier=null;
            for(var raw:document.getAsJsonArray("objects")){
                var row=raw.getAsJsonObject();String id=row.get("registryId").getAsString();int variant=row.get("variant").getAsInt();
                if(!Set.of("bloodborne_dw:source_original_tree","bloodborne_dw:source_large_grille_gate").contains(id)||variant!=0)throw new IllegalArgumentException("Unreviewed whole carrier target: "+id);
                var shift=row.getAsJsonArray("referenceSourceShift");double sx=shift.get(0).getAsDouble(),sy=shift.get(1).getAsDouble(),sz=shift.get(2).getAsDouble();
                int rotation=row.get("referenceRotation").getAsInt();double angle=-rotation*Math.PI/4,c=Math.cos(angle),s=Math.sin(angle);var carriers=new ArrayList<Carrier>();
                for(var item:row.getAsJsonArray("carriers")){
                    var source=item.getAsJsonObject();var cell=source.getAsJsonArray("relativeCell");double x=cell.get(0).getAsDouble()-sx,z=cell.get(2).getAsDouble()-sz;
                    var nativeSource=ImportedSourceLightingV127.source(source.get("sourceState").getAsString());
                    int opacity=nativeSource.getOpacity(EmptyBlockView.INSTANCE,BlockPos.ORIGIN);
                    if(opacity!=source.get("nativeOpacity").getAsInt())throw new IllegalArgumentException("Native source carrier opacity changed: "+source);
                    if(opacity==15){
                        // All reviewed opaque shadow cells are native solid cubes.
                        // This shared carrier is used only for renderer queries;
                        // helper collision/ownership remains unchanged.
                        if(!nativeSource.isOpaqueFullCube(EmptyBlockView.INSTANCE,BlockPos.ORIGIN)
                            ||nativeSource.getAmbientOcclusionLightLevel(EmptyBlockView.INSTANCE,BlockPos.ORIGIN)!=.2F
                            ||nativeSource.getLuminance()!=0||!nativeSource.getFluidState().isEmpty()
                            ||nativeSource.hasSidedTransparency())throw new IllegalArgumentException("Opaque whole source render flags require explicit contexts: "+source);
                        if(opaqueCarrier==null)opaqueCarrier=nativeSource;
                    }
                    carriers.add(new Carrier(x*c-z*s,cell.get(1).getAsDouble()+.5-sy,x*s+z*c,opacity));
                }
                if(carriers.isEmpty()||carriers.size()>32||rules.put(id,new Rule(id,variant,List.copyOf(carriers)))!=null)throw new IllegalArgumentException("Repeated/empty/excessive carrier rule");
            }
            return new Loaded(Map.copyOf(rules),opaqueCarrier);
        }catch(Exception error){throw new IllegalStateException("Invalid exact whole source optics",error);}
    }
    public static BlockState opaqueRenderCarrier(){return TABLES.opaqueCarrier;}
    private static boolean enabled(NbtCompound payload){return payload.contains(CONTEXT,NbtElement.INT_TYPE)&&payload.getInt(CONTEXT)==1;}
    public static Map<Cell,Integer> carriers(String id,int variant,int rotation,NbtCompound payload){
        if(!enabled(payload))return Map.of();var rule=RULES.get(id);if(rule==null||variant!=rule.variant)return Map.of();
        var shift=CompositeSourceShift.local(payload).rotate(rotation);double mount=payload.getDouble("MountY")+payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY);
        var key=new Key(id,variant,rotation&7,shift.x(),shift.y(),shift.z(),mount);var known=CACHE.get(key);if(known!=null)return known;
        double angle=(rotation&7)*Math.PI/4,c=Math.cos(angle),s=Math.sin(angle);Map<Cell,Integer> result=new TreeMap<>();
        for(var carrier:rule.carriers){
            // Rotate the source carrier centre about the same root cell centre
            // as model parts. Actual source cardinal poses reproduce exact cells;
            // later fractional moves retain one shadow cell per carrier centre.
            var cell=new Cell((int)Math.floor(carrier.x*c-carrier.z*s+shift.x()+.5+1e-10),(int)Math.floor(carrier.y+shift.y()+mount+1e-10),(int)Math.floor(carrier.x*s+carrier.z*c+shift.z()+.5+1e-10));
            result.merge(cell,carrier.opacity,Math::max);
        }
        var immutable=Map.copyOf(result);if(CACHE.size()<1024)CACHE.putIfAbsent(key,immutable);return immutable;
    }
    public static Map<Cell,Integer> carriers(CompositeSpec spec,BlockState state,NbtCompound payload){
        if(!enabled(payload)||!RULES.containsKey(spec.id.toString()))return Map.of();return carriers(spec.id.toString(),CompositeSpec.poseVariant(state),state.get(CompositeRootBlock.ROTATION),payload);
    }
    public static Map<Cell,Footprint> footprint(CompositeSpec spec,BlockState state,NbtCompound payload,Map<Cell,Footprint> physical){
        var carriers=carriers(spec,state,payload);if(carriers.isEmpty()||physical.keySet().containsAll(carriers.keySet()))return physical;
        Map<Cell,Footprint> result=new TreeMap<>(physical);for(var cell:carriers.keySet())result.putIfAbsent(cell,Footprint.EMPTY);return Collections.unmodifiableMap(result);
    }
    /** Called only while immutable snapshots are published, never on a light
     * worker query. Root payload decoding is finite work for two opted-in types. */
    public static int publishedOpacity(Cell cell,List<CompositeData.Contribution> entries){
        int opacity=0;
        for(var entry:entries){String id=entry.owner().registryId();if(!RULES.containsKey(id))continue;var props=entry.rootData().properties();int variant=Integer.parseInt(props.getOrDefault("variant","0"))+16*Integer.parseInt(props.getOrDefault("art_bank","0"));int rotation=Integer.parseInt(props.getOrDefault("rotation","0"));
            var relative=cell.subtract(entry.owner().root());opacity=Math.max(opacity,carriers(id,variant,rotation,CompositeData.nbt(entry.rootData().blockEntityNbt())).getOrDefault(relative,0));
        }
        return opacity;
    }
    /** Empty conversion shadow memberships must not divert ordinary hand
     * placement to an adjacent cell. Every other helper keeps its old rule. */
    public static boolean emptyReplaceable(Cell cell,List<CompositeData.Contribution> entries){
        if(entries.isEmpty())return false;
        for(var entry:entries){
            if(entry.owner().root().equals(cell)||!entry.shape().equals(Footprint.EMPTY)||!RULES.containsKey(entry.owner().registryId()))return false;
            var props=entry.rootData().properties();int variant=Integer.parseInt(props.getOrDefault("variant","0"))+16*Integer.parseInt(props.getOrDefault("art_bank","0"));int rotation=Integer.parseInt(props.getOrDefault("rotation","0"));
            if(!carriers(entry.owner().registryId(),variant,rotation,CompositeData.nbt(entry.rootData().blockEntityNbt())).containsKey(cell.subtract(entry.owner().root())))return false;
        }
        return true;
    }
    public static Map<String,Object> metrics(){return Map.of("sharedWholeSourceRules",RULES.size(),"sharedWholeSourceCarrierOffsets",RULES.values().stream().mapToInt(rule->rule.carriers.size()).sum(),"boundedTransformCache",CACHE.size(),"transformCacheLimit",1024,"perInstanceMetadata",CONTEXT+"=1 only; no repeated carrier table");}
    private ImportedWholeSourceOpticsV127(){}
}
