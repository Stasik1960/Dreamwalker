package dev.dreamwalker.bloodbornedw.v124qa;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.*;
import net.minecraft.util.Identifier;
import dev.dreamwalker.bloodbornedw.composite.*;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/** Isolated evidence utilities; never packaged in the production mod. */
final class V124QaEvidence {
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static JsonObject input, mapping;
    private static String production;
    private static final Map<String, JsonObject> MODELS = new HashMap<>();
    private static Set<String> legacyStairRegistries;
    static JsonObject input() {
        if (input == null) input = resource("/v124-qa-input.json"); return input;
    }
    static JsonObject mapping() {
        if (mapping == null) mapping = resource("/v124-type-state-mapping.json"); return mapping;
    }
    private static JsonObject resource(String name) {
        try (var stream = V124QaEvidence.class.getResourceAsStream(name)) {
            require(stream != null, "Missing explicit QA resource " + name);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    static String baselineSha() {
        return input().has("baselineJarSha") ? input().get("baselineJarSha").getAsString() : input().get("baselineJarSha256").getAsString();
    }
    static String candidateSha() {
        return input().has("productionJarSha") ? input().get("productionJarSha").getAsString() : input().get("productionJarSha256").getAsString();
    }
    static String productionSha() {
        if (production != null) return production;
        try {
            var paths = FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getOrigin().getPaths();
            require(paths.size() == 1, "Exactly one production artifact required");
            String actual = sha(paths.get(0));
            require(actual.equals(baselineSha()) || actual.equals(candidateSha()), "Installed production artifact is outside this QA binding");
            return production = actual;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    static boolean mode(String expected) {
        Path path = Path.of("v124-native-mode.json");
        try {
            require(Files.isRegularFile(path), "Explicit isolated v124-native-mode.json required");
            JsonObject mode = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            return mode.get("mode").getAsString().equals(expected);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    static String sha(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
    static JsonObject state(BlockState state) {
        JsonObject result = new JsonObject(); state.getEntries().forEach((key, value) -> result.addProperty(key.getName(), name(key, value))); return result;
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static String name(net.minecraft.state.property.Property property, Comparable value) { return property.name(value); }
    static JsonObject expectedState(BlockState old, JsonObject row) {
        JsonObject state = state(old); state.addProperty("rotation", Integer.toString((old.get(CompositeRootBlock.ROTATION) + row.get("rotationDelta").getAsInt()) & 7));
        state.addProperty("variant", row.get("newVariant").getAsString());
        row.getAsJsonObject("properties").entrySet().forEach(entry -> state.addProperty(entry.getKey(), entry.getValue().getAsString())); return state;
    }
    static NbtCompound expectedPayload(NbtCompound old, JsonObject row) {
        NbtCompound result = old.copy(); CompositeSourceShift.Shift prior = CompositeSourceShift.local(old).rotate(-row.get("rotationDelta").getAsInt());
        JsonArray shift = row.getAsJsonArray("sourceShift");
        double x = prior.x() + shift.get(0).getAsDouble(), y = prior.y() + shift.get(1).getAsDouble(), z = prior.z() + shift.get(2).getAsDouble();
        if (x == 0 && y == 0 && z == 0) result.remove("SourceShift");
        else { NbtList vector = new NbtList(); vector.add(NbtDouble.of(x)); vector.add(NbtDouble.of(y)); vector.add(NbtDouble.of(z)); result.put("SourceShift", vector); }
        if (historicalStair(row.get("oldRegistryId").getAsString())) result.putString("ImportedStairLegacyArt", row.get("oldRegistryId").getAsString() + "|" + row.get("oldVariant").getAsInt());
        result.putInt("ImportedTypeVersion", 1); return result;
    }
    /** Independent expectation from the reviewed mapping group, not production conversion. */
    static boolean historicalStair(String registry) {
        if (legacyStairRegistries == null) {
            Set<String> types = new HashSet<>();
            for (JsonElement raw : mapping().getAsJsonArray("groups")) {
                JsonObject group = raw.getAsJsonObject();
                if (group.get("kind").getAsString().equals("stairs")) for (JsonElement member : group.getAsJsonArray("members")) types.add(member.getAsString());
            }
            legacyStairRegistries = Set.copyOf(types);
        }
        return legacyStairRegistries.contains(registry);
    }
    static JsonArray array(double... values) { JsonArray result = new JsonArray(); for (double value : values) result.add(value); return result; }
    static void write(Path path, JsonObject value) {
        try { Files.writeString(path, JSON.toJson(value) + "\n", StandardCharsets.UTF_8); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    static void require(boolean pass, String reason) { if (!pass) throw new AssertionError(reason); }

    /** Server-side vertex bounds use the actual source JSON faces and renderer transforms.
     * Generated/builtin item meshes are explicitly unavailable here, never fabricated.
     */
    static Optional<double[]> renderBounds(CompositeRootBlock block, BlockState state, NbtCompound payload) {
        double[] result = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        CompositeSourceShift.Shift shift = CompositeSourceShift.world(state, payload);
        Matrix4d outer = new Matrix4d().translation(shift.x(), payload.getDouble("MountY") + payload.getDouble("VerticalOffset") + shift.y(), shift.z());
        rotate(outer, .5, .5, .5, -state.get(CompositeRootBlock.ROTATION) * 45, false);
        boolean found = false;
        List<CompositeSpec.Part> parts=block.spec.pose(state).parts();
        // The genuine baseline has no selector/API. Reflection is entered only
        // for the candidate's validated art payload, retaining the old-JAR author.
        if(payload.contains("ImportedStairLegacyArt",NbtElement.STRING_TYPE))try{
            var helper=Class.forName("dev.dreamwalker.bloodbornedw.composite.ImportedStairLegacyArt");
            @SuppressWarnings("unchecked") var selected=(List<CompositeSpec.Part>)helper.getMethod("selectedParts",CompositeRootBlock.class,BlockState.class,NbtCompound.class).invoke(null,block,state,payload);
            parts=selected;
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Candidate legacy stair art cannot resolve its reviewed original parts",failure);}
        for (var part : parts) {
            Matrix4d matrix = new Matrix4d(outer).translate(part.offset().x()/16, part.offset().y()/16, part.offset().z()/16);
            rotate(matrix, part.pivot().x()/16, part.pivot().y()/16, part.pivot().z()/16, -part.yaw(), false);
            rotate(matrix, part.pivot().x()/16, part.pivot().y()/16, part.pivot().z()/16, -part.pitch(), true);
            rotate(matrix, part.extraPivot().x()/16, part.extraPivot().y()/16, part.extraPivot().z()/16, part.extraYaw(), false);
            Identifier id = state.get(CompositeRootBlock.PROFILE) == CompositeRootBlock.Profile.ALT ? part.altModel() : part.model();
            JsonArray elements = elements(id.toString(), new HashSet<>()); if (elements == null) return Optional.empty();
            for (JsonElement raw : elements) {
                JsonObject element = raw.getAsJsonObject(); if (!element.has("faces") || element.getAsJsonObject("faces").size() == 0) continue;
                JsonArray from = element.getAsJsonArray("from"), to = element.getAsJsonArray("to");
                Matrix4d elementMatrix = new Matrix4d(matrix);
                if (element.has("rotation")) {
                    JsonObject rotation = element.getAsJsonObject("rotation"); JsonArray origin = rotation.getAsJsonArray("origin");
                    double x=origin.get(0).getAsDouble()/16, y=origin.get(1).getAsDouble()/16, z=origin.get(2).getAsDouble()/16;
                    double angle=Math.toRadians(rotation.get("angle").getAsDouble()); String axis=rotation.get("axis").getAsString();
                    elementMatrix.translate(x,y,z);
                    switch(axis) {case "x" -> elementMatrix.rotateX(angle); case "y" -> elementMatrix.rotateY(angle); case "z" -> elementMatrix.rotateZ(angle); default -> throw new IllegalArgumentException("Invalid element rotation axis");}
                    if(rotation.has("rescale") && rotation.get("rescale").getAsBoolean()) {
                        double scale=1/Math.cos(angle); elementMatrix.scale(axis.equals("x")?1:scale,axis.equals("y")?1:scale,axis.equals("z")?1:scale);
                    }
                    elementMatrix.translate(-x,-y,-z);
                }
                for (String face : element.getAsJsonObject("faces").keySet()) for (int a=0;a<2;a++) for(int b=0;b<2;b++) {
                    double x,y,z;
                    switch(face) {
                        case "west", "east" -> {x=(face.equals("west")?from:to).get(0).getAsDouble()/16;y=(a==0?from:to).get(1).getAsDouble()/16;z=(b==0?from:to).get(2).getAsDouble()/16;}
                        case "down", "up" -> {x=(a==0?from:to).get(0).getAsDouble()/16;y=(face.equals("down")?from:to).get(1).getAsDouble()/16;z=(b==0?from:to).get(2).getAsDouble()/16;}
                        case "north", "south" -> {x=(a==0?from:to).get(0).getAsDouble()/16;y=(b==0?from:to).get(1).getAsDouble()/16;z=(face.equals("north")?from:to).get(2).getAsDouble()/16;}
                        default -> throw new IllegalArgumentException("Unknown source face " + face);
                    }
                    Vector3d point=elementMatrix.transformPosition(new Vector3d(x,y,z)); found=true;
                    result[0]=Math.min(result[0],point.x);result[1]=Math.min(result[1],point.y);result[2]=Math.min(result[2],point.z);
                    result[3]=Math.max(result[3],point.x);result[4]=Math.max(result[4],point.y);result[5]=Math.max(result[5],point.z);
                }
            }
        }
        return found ? Optional.of(result) : Optional.empty();
    }
    private static void rotate(Matrix4d matrix,double x,double y,double z,double degrees,boolean pitch) {
        matrix.translate(x,y,z);if(pitch)matrix.rotateX(Math.toRadians(degrees));else matrix.rotateY(Math.toRadians(degrees));matrix.translate(-x,-y,-z);
    }
    private static JsonArray elements(String registry, Set<String> visiting) {
        if (registry.startsWith("builtin/") || registry.startsWith("minecraft:builtin/")) return null;
        require(visiting.add(registry),"Cyclic source model parent " + registry);
        JsonObject model=MODELS.get(registry);
        if(model==null){Identifier id=new Identifier(registry);String name="/assets/"+id.getNamespace()+"/models/"+id.getPath()+".json";
            try(var stream=V124QaEvidence.class.getResourceAsStream(name)){if(stream==null)return null;model=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();MODELS.put(registry,model);}catch(Exception e){throw new IllegalStateException(e);}}
        if(model.has("elements"))return model.getAsJsonArray("elements");
        return model.has("parent")?elements(model.get("parent").getAsString(),visiting):null;
    }
}
