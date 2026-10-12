package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;

/** Immutable defaults for changed NEW V12.6 types and poses. Offline fitted cuboids are the
 * collision, selection and saved occupied geometry; no runtime voxel fitting.
 * Original V12 objects never enter this resource or these guarded entry points.
 * This is not an authored Collision Studio replacement. */
public final class ImportedCollisionV126 {
    private record Rotation(List<ImportedCollisionMath.Box> physical, List<ImportedCollisionMath.Box> aim) {}
    private record Variant(int closedBase, int closedAlt, int openBase, int openAlt) {}
    private record Tables(Map<String,List<Variant>> objects, List<List<Rotation>> shapes) {}
    private record Key(int table, int yaw, double mount, double x, double y, double z) {}
    private static final Tables TABLES = load();
    private static final Map<Key,Map<Cell,Footprint>> CACHE = new ConcurrentHashMap<>();
    private static final int CACHE_LIMIT = 4096;
    private static final int CACHE_CELL_LIMIT = 65536, CACHE_BOX_REFERENCE_LIMIT = 262144;
    private static int cachedCells, cachedBoxReferences;
    private ImportedCollisionV126() {}

    public static boolean supports(CompositeSpec spec) {
        return spec.simplePhysicalCollision && TABLES.objects.containsKey(spec.id.toString());
    }

    private static JsonObject document(String path) {
        var input = ImportedCollisionV126.class.getResourceAsStream(path);
        if (input == null) throw new IllegalStateException("Missing NEW default collision resource: " + path);
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception failure) { throw new IllegalStateException("Invalid NEW default collision resource", failure); }
    }

    private static Tables load() {
        JsonObject document = document("/bloodborne_dw/imported-collision-v126.json");
        if (document.get("schemaVersion").getAsInt()!=1 || document.get("unitsPerBlock").getAsInt()!=16
                || document.get("registeredNewTypes").getAsInt()!=1045 || document.get("maxBoxesPerOrientation").getAsInt()!=12)
            throw new IllegalArgumentException("Unsupported NEW inner-collision defaults");
        Set<String> registered = new HashSet<>();
        for (JsonElement value : document("/bloodborne_dw/remaining-catalogue.json").getAsJsonArray("objects")) {
            String path = value.getAsJsonObject().get("path").getAsString();
            if (!path.matches("source_[a-z0-9_]+") || !registered.add("bloodborne_dw:"+path))
                throw new IllegalArgumentException("Invalid registered NEW collision cohort");
        }
        Map<List<ImportedCollisionMath.Box>,List<ImportedCollisionMath.Box>> interned = new HashMap<>();
        List<List<Rotation>> shapes = new ArrayList<>();
        JsonArray rawShapes = document.getAsJsonArray("shapes");
        if (rawShapes.size()==0 || rawShapes.size()>8192) throw new IllegalArgumentException("NEW collision table budget");
        for (JsonElement shape : rawShapes) {
            JsonArray raw = shape.getAsJsonArray();
            if (raw.size()!=8) throw new IllegalArgumentException("NEW collision requires eight orientations");
            List<Rotation> rows = new ArrayList<>();
            for (JsonElement entry : raw) {
                JsonObject row = entry.getAsJsonObject();
                List<ImportedCollisionMath.Box> physical = boxes(row.getAsJsonArray("collision"),12,interned);
                List<ImportedCollisionMath.Box> aim = boxes(row.getAsJsonArray("aim"),1,interned);
                rows.add(new Rotation(physical,aim));
            }
            shapes.add(List.copyOf(rows));
        }
        Map<String,List<Variant>> objects = new HashMap<>();
        for (var entry : document.getAsJsonObject("objects").entrySet()) {
            if (!registered.contains(entry.getKey())) throw new IllegalArgumentException("Unregistered NEW collision owner");
            JsonArray variants = entry.getValue().getAsJsonArray();
            if (variants.size()==0 || variants.size()>ImportedArtworkBlock.variantBudget(entry.getKey().substring("bloodborne_dw:".length()))) throw new IllegalArgumentException("NEW collision pose budget");
            List<Variant> values = new ArrayList<>();
            for (JsonElement value : variants) {
                JsonObject row = value.getAsJsonObject();
                int cb=index(row,"closedBase",shapes.size()),ca=index(row,"closedAlt",shapes.size());
                int ob=index(row,"openBase",shapes.size()),oa=index(row,"openAlt",shapes.size());
                values.add(new Variant(cb,ca,ob,oa));
            }
            objects.put(entry.getKey(),List.copyOf(values));
        }
        if (registered.size()!=1045 || objects.isEmpty() || !registered.containsAll(objects.keySet()))
            throw new IllegalArgumentException("NEW collision resource differs from the exact registered cohort");
        return new Tables(Map.copyOf(objects),List.copyOf(shapes));
    }

    private static int index(JsonObject row,String field,int size) {
        int result=row.get(field).getAsInt();
        if (result<0 || result>=size) throw new IllegalArgumentException("Unknown NEW collision table");
        return result;
    }

    private static List<ImportedCollisionMath.Box> boxes(JsonArray rows,int limit,Map<List<ImportedCollisionMath.Box>,List<ImportedCollisionMath.Box>> interned) {
        if (rows.size()>limit) throw new IllegalArgumentException("NEW collision cuboid budget");
        List<ImportedCollisionMath.Box> result = new ArrayList<>();
        for (JsonElement entry : rows) {
            JsonObject row=entry.getAsJsonObject();double[] lo=point(row.getAsJsonArray("from")),hi=point(row.getAsJsonArray("to"));
            result.add(new ImportedCollisionMath.Box(lo[0],lo[1],lo[2],hi[0],hi[1],hi[2]));
        }
        List<ImportedCollisionMath.Box> immutable=List.copyOf(result);
        return interned.computeIfAbsent(immutable,unused->immutable);
    }
    private static double[] point(JsonArray values) {
        if(values.size()!=3)throw new IllegalArgumentException("NEW collision coordinate count");
        double[] result=new double[3];
        for(int i=0;i<3;i++){double value=values.get(i).getAsDouble();if(!Double.isFinite(value)||Math.abs(value)>4096)throw new IllegalArgumentException("NEW collision coordinate");result[i]=value/16;}
        return result;
    }

    private static Key key(CompositeSpec spec,BlockState state,double mount,double x,double y,double z) {
        List<Variant> variants=TABLES.objects.get(spec.id.toString());
        int index=CompositeSpec.poseVariant(state);
        if(index<0 || index>=variants.size())index=0;
        Variant variant=variants.get(index);boolean open=state.get(CompositeRootBlock.OPEN),alt=state.get(CompositeRootBlock.PROFILE)==CompositeRootBlock.Profile.ALT;
        int table=open?(alt?variant.openAlt:variant.openBase):(alt?variant.closedAlt:variant.closedBase);
        return new Key(table,state.get(CompositeRootBlock.ROTATION)&7,mount,x,y,z);
    }

    public static Map<Cell,Footprint> footprint(CompositeSpec spec,BlockState state,double mount,double x,double y,double z) {
        if(!supports(spec))throw new IllegalArgumentException("Only exact-cohort NEW collision defaults");
        Key key=key(spec,state,mount,x,y,z);Map<Cell,Footprint> cached=CACHE.get(key);if(cached!=null)return cached;
        Rotation pose=TABLES.shapes.get(key.table).get(key.yaw);
        TreeMap<Cell,List<Box>> physical=new TreeMap<>(),aim=new TreeMap<>();
        for(var box:ImportedCollisionMath.transform(pose.physical,0,mount,true,x,y,z))clip(box,physical);
        if(!physical.isEmpty())aim.putAll(physical);
        else for(var box:ImportedCollisionMath.transform(pose.aim,0,mount,false,x,y,z))clip(box,aim);
        TreeSet<Cell> cells=new TreeSet<>(physical.keySet());cells.addAll(aim.keySet());cells.add(Cell.ORIGIN);
        if(cells.size()>4096)throw new IllegalArgumentException("NEW collision occupied-cell budget: "+spec.id);
        TreeMap<Cell,Footprint> result=new TreeMap<>();
        for(Cell cell:cells)result.put(cell,new Footprint(List.copyOf(physical.getOrDefault(cell,List.of())),List.copyOf(aim.getOrDefault(cell,List.of()))));
        Map<Cell,Footprint> immutable=Collections.unmodifiableMap(result);
        int references=result.values().stream().mapToInt(value->value.collision().size()+value.selection().size()).sum();
        synchronized(CACHE){
            var raced=CACHE.get(key);if(raced!=null)return raced;
            if(CACHE.size()<CACHE_LIMIT && cachedCells+result.size()<=CACHE_CELL_LIMIT && cachedBoxReferences+references<=CACHE_BOX_REFERENCE_LIMIT){
                CACHE.put(key,immutable);cachedCells+=result.size();cachedBoxReferences+=references;
            }
        }
        return immutable;
    }

    public static Map<Cell,List<Box>> physicalFootprint(CompositeSpec spec,BlockState state,double mount,double x,double y,double z) {
        TreeMap<Cell,List<Box>> result=new TreeMap<>();
        footprint(spec,state,mount,x,y,z).forEach((cell,value)->{if(!value.collision().isEmpty())result.put(cell,value.collision());});
        return Collections.unmodifiableMap(result);
    }

    private static void clip(ImportedCollisionMath.Box box,Map<Cell,List<Box>> target) {
        for(int x=(int)Math.floor(box.x0()+1e-8);x<Math.ceil(box.x1()-1e-8);x++)
            for(int y=(int)Math.floor(box.y0()+1e-8);y<Math.ceil(box.y1()-1e-8);y++)
                for(int z=(int)Math.floor(box.z0()+1e-8);z<Math.ceil(box.z1()-1e-8);z++) {
                    double a=Math.max(0,box.x0()-x),b=Math.max(0,box.y0()-y),c=Math.max(0,box.z0()-z);
                    double d=Math.min(1,box.x1()-x),e=Math.min(1,box.y1()-y),f=Math.min(1,box.z1()-z);
                    if(d>a&&e>b&&f>c)target.computeIfAbsent(new Cell(x,y,z),unused->new ArrayList<>()).add(new Box(a,b,c,d,e,f));
                }
    }
}
