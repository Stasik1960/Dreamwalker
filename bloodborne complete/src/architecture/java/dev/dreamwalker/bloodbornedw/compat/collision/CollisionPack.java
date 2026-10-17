package dev.dreamwalker.bloodbornedw.compat.collision;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Data-only adapter for Collision Studio's replacement export, not ModelPacks' new objects.
 * Reading never installs a pack. Coordinates are model-local blocks; rotations retain ZYX degrees.
 * Consumers must apply instance scale/mount/rotation and player-only OBB physics explicitly;
 * this adapter never converts a rotated collider into an enlarged axis-aligned obstacle.
 */
public final class CollisionPack {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final int MAX_STATES = 32768;
    private final Map<Key, Replacement> states;

    public record Vector(double x, double y, double z) {
        public Vector {
            for (double n : new double[]{x,y,z})
                if (!Double.isFinite(n) || Math.abs(n)>10000000) throw invalid("Vector range");
        }
    }
    public record Collider(String id, Vector center, Vector size, Vector rotation) {
        public Collider {
            text(id,"collider ID"); Objects.requireNonNull(center); Objects.requireNonNull(rotation);
            if (size==null || size.x<=0 || size.y<=0 || size.z<=0) throw invalid("Nonpositive collider size");
        }
    }
    public record Seat(String id, Vector surfaceCenter, Vector rotation) {
        public Seat { text(id,"seat ID"); Objects.requireNonNull(surfaceCenter); Objects.requireNonNull(rotation); }
        public Vector size() { return new Vector(.6,.1,.6); }
    }
    public record Replacement(String stateKey, List<Collider> colliders, List<Seat> seats) {
        public Replacement {
            text(stateKey,"state key"); colliders=List.copyOf(colliders); seats=List.copyOf(seats);
        }
    }
    private record Key(String registryId, Map<String,JsonPrimitive> selector) {}
    private CollisionPack(Map<Key,Replacement> states) { this.states=Map.copyOf(states); }

    public int stateCount() { return states.size(); }
    /** Exact selector equality, including profile. An absent state means preserve existing physics.
     * A present replacement with empty lists means an explicitly reviewed empty player state.
     */
    public Optional<Replacement> replacement(String registryId, JsonObject selector) {
        return Optional.ofNullable(states.get(key(registryId,selector)));
    }

    /** Accept the Studio JSON or its ZIP. Other files, models and embedded instructions are ignored. */
    public static CollisionPack read(Path path) throws IOException {
        if (Files.size(path)>64L*1024*1024) throw new IOException("Collision pack exceeds 64 MiB");
        if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            try (ZipFile zip=new ZipFile(path.toFile(),StandardCharsets.UTF_8)) {
                var entries=zip.entries(); java.util.zip.ZipEntry selected=null; int count=0;
                while (entries.hasMoreElements()) {
                    var entry=entries.nextElement();
                    if (++count>4096) throw new IOException("Too many collision ZIP entries");
                    if (!entry.getName().equals("dreamwalker-collisions.json")) continue;
                    if (selected!=null || entry.isDirectory()) throw new IOException("Duplicate collision JSON");
                    selected=entry;
                }
                if (selected==null) throw new IOException("Missing dreamwalker-collisions.json; game pack.json is a different format");
                try (InputStream input=zip.getInputStream(selected)) { return readBytes(input); }
            }
        }
        try (InputStream input=Files.newInputStream(path)) { return readBytes(input); }
    }
    private static CollisionPack readBytes(InputStream input) throws IOException {
        byte[] bytes=input.readNBytes(MAX_BYTES+1);
        if (bytes.length>MAX_BYTES) throw new IOException("Collision JSON exceeds 16 MiB");
        return read(new StringReader(new String(bytes,StandardCharsets.UTF_8)));
    }
    public static CollisionPack read(Reader reader) throws IOException {
        StringBuilder document=new StringBuilder(); char[] buffer=new char[8192]; int count;
        while ((count=reader.read(buffer))!=-1) {
            if (document.length()+count>MAX_BYTES) throw new IOException("Collision JSON exceeds 16 MiB");
            document.append(buffer,0,count);
        }
        try { return parse(JsonParser.parseString(document.toString()).getAsJsonObject()); }
        catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException failure) {
            throw new IOException("Invalid collision replacement project: "+failure.getMessage(),failure);
        }
    }
    private static CollisionPack parse(JsonObject root) {
        require(root,"schema","dreamwalker.player-collision-project");
        require(root,"schemaVersion",1); require(root,"unitsPerBlock",16);
        JsonObject coordinates=object(root,"coordinates"),policy=object(root,"policy"),source=object(root,"source");
        require(coordinates,"rotationOrder","ZYX");
        require(policy,"scope","players_only"); require(policy,"operation","replace_per_exact_state");
        if (policy.has("unlistedStates")) require(policy,"unlistedStates","preserve");
        for (String name:List.of("repository","commit","release")) text(string(source,name),"source "+name);
        seatSize(root,"seatSize");
        Map<Key,Replacement> result=new LinkedHashMap<>(); Set<String> models=new HashSet<>();
        JsonArray values=array(root,"models");
        if (values.size()>512) throw invalid("Model limit");
        for (JsonElement value:values) {
            JsonObject model=value.getAsJsonObject(); String registryId=string(model,"registryId");
            if (!models.add(registryId)) throw invalid("Duplicate model "+registryId);
            Set<String> stateKeys=new HashSet<>();
            for (JsonElement entry:array(model,"states")) {
                if (result.size()>=MAX_STATES) throw invalid("State limit");
                JsonObject state=entry.getAsJsonObject(); String stateKey=string(state,"key");
                if (!stateKeys.add(stateKey)) throw invalid("Duplicate state key");
                require(state,"replace",true);
                Key key=key(registryId,object(state,"selector"));
                List<Collider> colliders=new ArrayList<>(); Set<String> colliderIds=new HashSet<>();
                for (JsonElement element:array(state,"colliders")) {
                    if (colliders.size()>=2048) throw invalid("Collider limit");
                    JsonObject box=element.getAsJsonObject(); String id=string(box,"id");
                    if (!colliderIds.add(id)) throw invalid("Duplicate collider ID");
                    colliders.add(new Collider(id,vector(box,"center",16),vector(box,"size",16),vector(box,"rotation",1)));
                }
                List<Seat> seats=new ArrayList<>(); Set<String> seatIds=new HashSet<>();
                for (JsonElement element:array(state,"seats")) {
                    if (seats.size()>=64) throw invalid("Seat limit");
                    JsonObject seat=element.getAsJsonObject(); String id=string(seat,"id");
                    if (!seatIds.add(id)) throw invalid("Duplicate seat ID");
                    require(seat,"capacity",1); require(seat,"trigger","right_click_surface"); seatSize(seat,"size");
                    seats.add(new Seat(id,vector(seat,"surfaceCenter",16),vector(seat,"rotation",1)));
                }
                if (colliders.isEmpty() && seats.isEmpty()) require(state,"reviewed",true);
                if (result.putIfAbsent(key,new Replacement(stateKey,colliders,seats))!=null)
                    throw invalid("Duplicate exact selector for "+registryId);
            }
        }
        return new CollisionPack(result);
    }
    private static Key key(String registryId,JsonObject selector) {
        if (registryId==null || !registryId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw invalid("Registry ID");
        Objects.requireNonNull(selector); Map<String,JsonPrimitive> exact=new TreeMap<>();
        if (selector.size()>64) throw invalid("Selector field limit");
        for (var field:selector.entrySet()) {
            text(field.getKey(),"selector field");
            if (!field.getValue().isJsonPrimitive()) throw invalid("Selector values must be scalar");
            JsonPrimitive primitive=field.getValue().getAsJsonPrimitive();
            if (primitive.isNumber()) {
                if (!Double.isFinite(primitive.getAsDouble())) throw invalid("Selector number");
                // Gson numeric equality can span Java number types whose hashes differ.
                // Normalize parsed JSON and runtime-created property values identically.
                primitive=new JsonPrimitive(new java.math.BigDecimal(primitive.getAsString()).stripTrailingZeros());
            }
            exact.put(field.getKey(),primitive);
        }
        return new Key(registryId,Map.copyOf(exact));
    }
    private static Vector vector(JsonObject object,String name,double units) {
        JsonArray values=array(object,name);
        if (values.size()!=3) throw invalid("Vector "+name);
        double[] vector=new double[3];
        for (int i=0;i<3;i++) {
            JsonPrimitive value=values.get(i).getAsJsonPrimitive();
            if (!value.isNumber()) throw invalid("Numeric vector "+name);
            vector[i]=value.getAsDouble();
            if (!Double.isFinite(vector[i]) || Math.abs(vector[i])>10000000) throw invalid("Vector range");
        }
        return new Vector(vector[0]/units,vector[1]/units,vector[2]/units);
    }
    private static void seatSize(JsonObject object,String name) {
        if (!vector(object,name,1).equals(new Vector(9.6,1.6,9.6))) throw invalid("Fixed seat size");
    }
    private static JsonObject object(JsonObject root,String name) {
        if (!root.has(name) || !root.get(name).isJsonObject()) throw invalid("Object "+name);
        return root.getAsJsonObject(name);
    }
    private static JsonArray array(JsonObject root,String name) {
        if (!root.has(name) || !root.get(name).isJsonArray()) throw invalid("Array "+name);
        return root.getAsJsonArray(name);
    }
    private static String string(JsonObject root,String name) {
        if (!root.has(name) || !root.get(name).isJsonPrimitive() || !root.getAsJsonPrimitive(name).isString()) throw invalid("String "+name);
        String value=root.get(name).getAsString(); text(value,name); return value;
    }
    private static void require(JsonObject root,String name,Object value) {
        JsonPrimitive expected=value instanceof Number n?new JsonPrimitive(n):value instanceof Boolean b?new JsonPrimitive(b):new JsonPrimitive(value.toString());
        if (!root.has(name) || !expected.equals(root.get(name))) throw invalid("Expected "+name+"="+value);
    }
    private static void text(String value,String name) {
        if (value==null || value.isEmpty() || value.length()>512) throw invalid("Invalid "+name);
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
