package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;

/** Authored visual parts, physical boxes, selection and structural reservations are distinct. */
public final class ReviewLegacyCompositeSpec {
    public record Vec(double x, double y, double z) {}
    public record Part(Identifier model, Identifier altModel, Vec offset, double yaw, double pitch, Vec pivot, double extraYaw, Vec extraPivot) {}
    public record Bounds(Vec from, Vec to) {}
    public record Pose(List<Part> parts, List<Bounds> collision, List<Bounds> selection) {}
    public record Variant(Pose closed, Pose open) {}
    public final Identifier id;
    public final String displayName;
    public final boolean translucent;
    public final List<Variant> variants;
    public final boolean openable, requiredSupport;
    public final Cell supportOffset;
    public final Set<Cell> essential;
    private final Map<String, Map<Cell, Footprint>> shapes = new java.util.concurrent.ConcurrentHashMap<>();
    private ReviewLegacyCompositeSpec(JsonObject document) {
        if (document.get("schemaVersion").getAsInt() != 1 || document.get("units").getAsInt() != 16) throw new IllegalArgumentException("Unsupported composite geometry schema");
        id = new Identifier(document.get("id").getAsString()); displayName = document.has("displayName") ? document.get("displayName").getAsString() : id.getPath();
        translucent = document.has("layer") && document.get("layer").getAsString().equals("translucent");
        openable = document.has("openable") && document.get("openable").getAsBoolean();
        JsonObject support = document.has("support") ? document.getAsJsonObject("support") : new JsonObject();
        requiredSupport = support.has("required") && support.get("required").getAsBoolean();
        Vec offset = support.has("offset") ? vec(support.getAsJsonArray("offset")) : new Vec(0,-1,0);
        supportOffset = new Cell((int)offset.x,(int)offset.y,(int)offset.z);
        TreeSet<Cell> reservations = new TreeSet<>(); reservations.add(Cell.ORIGIN);
        if (document.has("essentialMask")) for (JsonElement value : document.getAsJsonArray("essentialMask")) { Vec v = vec(value.getAsJsonArray()); reservations.add(new Cell((int)v.x,(int)v.y,(int)v.z)); }
        essential = Set.copyOf(reservations);
        List<Variant> parsed = new ArrayList<>();
        for (JsonElement value : document.getAsJsonArray("variants")) {
            JsonObject poses = value.getAsJsonObject().getAsJsonObject("poses"); Pose closed = pose(poses.getAsJsonObject("closed"));
            parsed.add(new Variant(closed, poses.has("open") ? pose(poses.getAsJsonObject("open")) : closed));
        }
        if (parsed.isEmpty() || parsed.size() > 16) throw new IllegalArgumentException("Composite variant budget: " + id);
        variants = List.copyOf(parsed);
    }
    public static ReviewLegacyCompositeSpec load(String path) {
        try (InputStream input = ReviewLegacyCompositeSpec.class.getResourceAsStream("/bloodborne_dw/review-v7-baseline/" + path + ".json")) {
            if (input == null) throw new IllegalStateException("Missing composite descriptor: " + path);
            return new ReviewLegacyCompositeSpec(JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException failure) { throw new IllegalStateException("Cannot load composite " + path, failure); }
    }
    public Pose pose(BlockState state) {
        int variant = state.get(CompositeRootBlock.VARIANT);
        if (variant >= variants.size()) variant = 0;
        return state.get(CompositeRootBlock.OPEN) ? variants.get(variant).open : variants.get(variant).closed;
    }
    public Map<Cell, Footprint> footprint(BlockState state, double mountY) {
        String key = state.get(CompositeRootBlock.VARIANT) + ":" + state.get(CompositeRootBlock.OPEN) + ":" + state.get(CompositeRootBlock.ROTATION) + ":" + mountY;
        return shapes.computeIfAbsent(key, unused -> {
            TreeMap<Cell, List<Box>> collision = new TreeMap<>(), selection = new TreeMap<>(); Pose pose = pose(state);
            for (Bounds bounds : pose.collision) distribute(bounds, state.get(CompositeRootBlock.ROTATION), mountY, collision);
            for (Bounds bounds : pose.selection) distribute(bounds, state.get(CompositeRootBlock.ROTATION), mountY, selection);
            TreeSet<Cell> cells = new TreeSet<>(collision.keySet()); cells.addAll(selection.keySet()); cells.add(Cell.ORIGIN);
            if (cells.size() > 4096) throw new IllegalArgumentException("Composite cell budget: " + id);
            TreeMap<Cell, Footprint> result = new TreeMap<>();
            for (Cell cell : cells) result.put(cell, new Footprint(collision.getOrDefault(cell,List.of()), selection.getOrDefault(cell,List.of())));
            return Collections.unmodifiableMap(result);
        });
    }
    private static Pose pose(JsonObject object) {
        List<Part> parts = new ArrayList<>();
        for (JsonElement value : object.getAsJsonArray("parts")) {
            JsonObject part = value.getAsJsonObject(); Identifier model = new Identifier(part.get("model").getAsString());
            parts.add(new Part(model,part.has("altModel") ? new Identifier(part.get("altModel").getAsString()) : model,
                    optionalVec(part,"offset",new Vec(0,0,0)),number(part,"yaw",0),number(part,"pitch",0),optionalVec(part,"pivot",new Vec(8,8,8)),number(part,"extraYaw",0),optionalVec(part,"extraPivot",new Vec(8,8,8))));
        }
        List<Bounds> collision = bounds(object,"collision");
        List<Bounds> selection = object.has("selection") ? bounds(object,"selection") : collision;
        return new Pose(List.copyOf(parts),collision,selection);
    }
    private static List<Bounds> bounds(JsonObject object,String field) {
        List<Bounds> result = new ArrayList<>(); if (!object.has(field)) return List.of();
        for (JsonElement value : object.getAsJsonArray(field)) {
            JsonObject box = value.getAsJsonObject(); Vec from = vec(box.getAsJsonArray("from")), to = vec(box.getAsJsonArray("to"));
            if (from.x >= to.x || from.y >= to.y || from.z >= to.z) throw new IllegalArgumentException("Degenerate " + field + " box");
            result.add(new Bounds(from,to));
        }
        return List.copyOf(result);
    }
    private static double number(JsonObject object,String field,double fallback) { return object.has(field) ? object.get(field).getAsDouble() : fallback; }
    private static Vec optionalVec(JsonObject object,String field,Vec fallback) { return object.has(field) ? vec(object.getAsJsonArray(field)) : fallback; }
    private static Vec vec(JsonArray values) {
        if (values.size()!=3) throw new IllegalArgumentException("Expected three coordinates");
        double x=values.get(0).getAsDouble(),y=values.get(1).getAsDouble(),z=values.get(2).getAsDouble();
        if (!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>4096||Math.abs(y)>4096||Math.abs(z)>4096) throw new IllegalArgumentException("Invalid composite coordinate");
        return new Vec(x,y,z);
    }
    /** Diagonal thin boxes use a 1/16-block conservative raster, never their filled AABB. */
    private static void distribute(Bounds bounds,int rotation,double mountY,Map<Cell,List<Box>> result) {
        double minY=bounds.from.y/16+mountY,maxY=bounds.to.y/16+mountY;
        double[][] polygon=new double[4][2]; double angle=Math.toRadians(rotation*45),c=Math.cos(angle),s=Math.sin(angle);
        double[][] corners={{bounds.from.x/16,bounds.from.z/16},{bounds.to.x/16,bounds.from.z/16},{bounds.to.x/16,bounds.to.z/16},{bounds.from.x/16,bounds.to.z/16}};
        double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;
        for(int i=0;i<4;i++){double x=corners[i][0]-.5,z=corners[i][1]-.5;polygon[i][0]=.5+c*x-s*z;polygon[i][1]=.5+s*x+c*z;minX=Math.min(minX,polygon[i][0]);maxX=Math.max(maxX,polygon[i][0]);minZ=Math.min(minZ,polygon[i][1]);maxZ=Math.max(maxZ,polygon[i][1]);}
        if(rotation%2==0){clip(minX,minY,minZ,maxX,maxY,maxZ,result);return;}
        int firstX=(int)Math.floor(minX*16),lastX=(int)Math.ceil(maxX*16),firstZ=(int)Math.floor(minZ*16),lastZ=(int)Math.ceil(maxZ*16);
        for(int z=firstZ;z<lastZ;z++) { int start=Integer.MIN_VALUE;
            for(int x=firstX;x<=lastX;x++) { boolean occupied=x<lastX&&intersects(polygon,x/16.0,z/16.0,(x+1)/16.0,(z+1)/16.0);
                if(occupied&&start==Integer.MIN_VALUE)start=x;
                if(!occupied&&start!=Integer.MIN_VALUE){clip(start/16.0,minY,z/16.0,x/16.0,maxY,(z+1)/16.0,result);start=Integer.MIN_VALUE;}
            }
        }
    }
    private static boolean intersects(double[][] polygon,double x0,double z0,double x1,double z1) {
        double[][] rectangle={{x0,z0},{x1,z0},{x1,z1},{x0,z1}};
        for(int edge=0;edge<6;edge++){double ax,az;if(edge<2){ax=edge==0?1:0;az=edge==0?0:1;}else{int i=edge-2,j=(i+1)%4;ax=-(polygon[j][1]-polygon[i][1]);az=polygon[j][0]-polygon[i][0];}
            double p0=Double.POSITIVE_INFINITY,p1=Double.NEGATIVE_INFINITY,q0=p0,q1=p1;
            for(double[] p:polygon){double dot=p[0]*ax+p[1]*az;p0=Math.min(p0,dot);p1=Math.max(p1,dot);}for(double[] p:rectangle){double dot=p[0]*ax+p[1]*az;q0=Math.min(q0,dot);q1=Math.max(q1,dot);}
            if(p1<=q0+1e-9||q1<=p0+1e-9)return false;
        }return true;
    }
    private static void clip(double x0,double y0,double z0,double x1,double y1,double z1,Map<Cell,List<Box>> result) {
        for(int x=(int)Math.floor(x0+1e-8);x<(int)Math.ceil(x1-1e-8);x++)for(int y=(int)Math.floor(y0+1e-8);y<(int)Math.ceil(y1-1e-8);y++)for(int z=(int)Math.floor(z0+1e-8);z<(int)Math.ceil(z1-1e-8);z++) {
            Box box=new Box(Math.max(0,x0-x),Math.max(0,y0-y),Math.max(0,z0-z),Math.min(1,x1-x),Math.min(1,y1-y),Math.min(1,z1-z));
            result.computeIfAbsent(new Cell(x,y,z),unused->new ArrayList<>()).add(box);
        }
    }
}
