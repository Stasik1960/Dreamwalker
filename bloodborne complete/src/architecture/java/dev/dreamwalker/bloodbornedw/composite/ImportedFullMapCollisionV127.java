package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;

/** Sparse exact appended-state shapes; old V125/V126 tables are never changed
 * and an unlisted pose always reaches the previous provider. */
public final class ImportedFullMapCollisionV127 {
    private record Pose(String registry,int variant) {}
    private record Rotation(List<ImportedCollisionMath.Box> collision,List<ImportedCollisionMath.Box> aim) {}
    private record Key(Pose pose,int yaw,double mount,double x,double y,double z) {}
    private static final Map<Pose,List<Rotation>> TABLES=load();
    private static final Map<Key,Map<Cell,Footprint>> CACHE=new ConcurrentHashMap<>();
    private ImportedFullMapCollisionV127() {}
    private static List<ImportedCollisionMath.Box> boxes(JsonArray values,int limit) {
        if(values.size()>limit)throw new IllegalArgumentException("Full-source simple box limit");
        List<ImportedCollisionMath.Box> result=new ArrayList<>();
        for(var raw:values){var box=raw.getAsJsonObject();JsonArray a=box.getAsJsonArray("from"),b=box.getAsJsonArray("to");result.add(new ImportedCollisionMath.Box(a.get(0).getAsDouble()/16,a.get(1).getAsDouble()/16,a.get(2).getAsDouble()/16,b.get(0).getAsDouble()/16,b.get(1).getAsDouble()/16,b.get(2).getAsDouble()/16));}
        return List.copyOf(result);
    }
    private static Map<Pose,List<Rotation>> load() {
        var input=ImportedFullMapCollisionV127.class.getResourceAsStream("/bloodborne_dw/imported-full-map-v127.json");if(input==null)return Map.of();
        try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
            var doc=JsonParser.parseReader(reader).getAsJsonObject();if(doc.get("schemaVersion").getAsInt()!=1||doc.get("unitsPerBlock").getAsInt()!=16)throw new IllegalArgumentException("Full-source collision schema");
            Map<Pose,List<Rotation>> result=new HashMap<>();
            for(var raw:doc.getAsJsonArray("objects")){var row=raw.getAsJsonObject();String registry=row.get("registryId").getAsString();int variant=row.get("variant").getAsInt();if(!registry.startsWith("bloodborne_dw:source_")||variant<0||variant>=ImportedArtworkBlock.variantBudget(registry.substring("bloodborne_dw:".length())))throw new IllegalArgumentException("Original V12 cannot use full-source collision metadata");List<Rotation> rotations=new ArrayList<>();for(var r:row.getAsJsonArray("rotations")){var v=r.getAsJsonObject();rotations.add(new Rotation(boxes(v.getAsJsonArray("collision"),12),boxes(v.getAsJsonArray("aim"),1)));}if(rotations.size()!=8||result.putIfAbsent(new Pose(registry,variant),List.copyOf(rotations))!=null)throw new IllegalArgumentException("Duplicate or incomplete full-source pose");}
            return Map.copyOf(result);
        }catch(IOException failure){throw new IllegalStateException("Cannot read full-source collision metadata",failure);}
    }
    public static boolean supports(CompositeSpec spec,BlockState state){return spec.simplePhysicalCollision&&!spec.functionalDoorPhysics&&TABLES.containsKey(new Pose(spec.id.toString(),CompositeSpec.poseVariant(state)));}
    public static Map<Cell,Footprint> footprint(CompositeSpec spec,BlockState state,double mount,double x,double y,double z) {
        Key key=new Key(new Pose(spec.id.toString(),CompositeSpec.poseVariant(state)),state.get(CompositeRootBlock.ROTATION),mount,x,y,z);var known=CACHE.get(key);if(known!=null)return known;
        Rotation rotation=TABLES.get(key.pose).get(key.yaw);TreeMap<Cell,List<Box>> physical=new TreeMap<>(),aim=new TreeMap<>();
        for(var box:ImportedCollisionMath.transform(rotation.collision,0,mount,true,x,y,z))clip(box,physical);
        if(!physical.isEmpty())aim.putAll(physical);else for(var box:ImportedCollisionMath.transform(rotation.aim,0,mount,false,x,y,z))clip(box,aim);
        TreeSet<Cell> cells=new TreeSet<>(physical.keySet());cells.addAll(aim.keySet());cells.add(Cell.ORIGIN);if(cells.size()>4096)throw new IllegalArgumentException("Full-source occupied-cell budget");
        TreeMap<Cell,Footprint> result=new TreeMap<>();for(Cell cell:cells)result.put(cell,new Footprint(List.copyOf(physical.getOrDefault(cell,List.of())),List.copyOf(aim.getOrDefault(cell,List.of()))));var immutable=Collections.unmodifiableMap(result);if(CACHE.size()<1024&&cells.size()<256)CACHE.putIfAbsent(key,immutable);return immutable;
    }
    public static Map<Cell,List<Box>> physicalFootprint(CompositeSpec spec,BlockState state,double mount,double x,double y,double z){TreeMap<Cell,List<Box>> result=new TreeMap<>();footprint(spec,state,mount,x,y,z).forEach((cell,shape)->{if(!shape.collision().isEmpty())result.put(cell,shape.collision());});return Collections.unmodifiableMap(result);}
    private static void clip(ImportedCollisionMath.Box box,Map<Cell,List<Box>> result){for(int x=(int)Math.floor(box.x0()+1e-8);x<Math.ceil(box.x1()-1e-8);x++)for(int y=(int)Math.floor(box.y0()+1e-8);y<Math.ceil(box.y1()-1e-8);y++)for(int z=(int)Math.floor(box.z0()+1e-8);z<Math.ceil(box.z1()-1e-8);z++){double a=Math.max(0,box.x0()-x),b=Math.max(0,box.y0()-y),c=Math.max(0,box.z0()-z),d=Math.min(1,box.x1()-x),e=Math.min(1,box.y1()-y),f=Math.min(1,box.z1()-z);if(d>a&&e>b&&f>c)result.computeIfAbsent(new Cell(x,y,z),unused->new ArrayList<>()).add(new Box(a,b,c,d,e,f));}}
}
