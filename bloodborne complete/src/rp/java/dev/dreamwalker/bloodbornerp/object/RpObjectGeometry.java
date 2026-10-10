package dev.dreamwalker.bloodbornerp.object;

import com.google.gson.*;
import dev.dreamwalker.bloodbornerp.content.AssetCatalog;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Source-derived selection bounds and separately reviewed working geometry. */
public final class RpObjectGeometry {
    private static final Set<String> CUSTOM = Set.of("stairs","ladder","npc_window","chandelier_small","wood_gate");
    public static boolean surfaceMounted(String id){return custom(id)||id.equals("dog_cage")||id.equals("chandelier_large");}
    private record Cube(String bone, Box bounds) {}
    private record Geometry(Box bounds,List<Cube> cubes,List<Box> motionSelection) {}
    private static final Map<String,Geometry> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.LongAdder CACHE_HITS=new java.util.concurrent.atomic.LongAdder(),CACHE_LOADS=new java.util.concurrent.atomic.LongAdder(),CACHE_FAILURES=new java.util.concurrent.atomic.LongAdder();
    private RpObjectGeometry() {}
    public static boolean custom(String id) { return CUSTOM.contains(id); }
    private static Vec3d vector(JsonObject object,String key) {
        if(!object.has(key))return Vec3d.ZERO;
        JsonArray a=object.getAsJsonArray(key);return new Vec3d(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());
    }
    private static Vec3d mirror(Vec3d v) { return new Vec3d(-v.x,v.y,v.z); }
    private static Vec3d around(Vec3d value,Vec3d pivot,Vec3d degrees) {
        Vec3d v=value.subtract(pivot);double a=Math.toRadians(-degrees.x),c=Math.cos(a),s=Math.sin(a);
        v=new Vec3d(v.x,v.y*c-v.z*s,v.y*s+v.z*c);
        a=Math.toRadians(-degrees.y);c=Math.cos(a);s=Math.sin(a);v=new Vec3d(v.x*c+v.z*s,v.y,-v.x*s+v.z*c);
        a=Math.toRadians(degrees.z);c=Math.cos(a);s=Math.sin(a);return new Vec3d(v.x*c-v.y*s,v.x*s+v.y*c,v.z).add(pivot);
    }
    private static Geometry geometry(String id) {
        Geometry cached=CACHE.get(id);if(cached!=null){CACHE_HITS.increment();return cached;}
        return CACHE.computeIfAbsent(id,key->{
            CACHE_LOADS.increment();
            String path="/assets/bloodborne_rp/"+AssetCatalog.get(key).model();
            try(var stream=RpObjectGeometry.class.getResourceAsStream(path)) {
                if(stream==null)throw new IllegalStateException("Missing reviewed geometry "+path);
                JsonObject document=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray bones=document.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
                Map<String,JsonObject> lookup=new HashMap<>();for(JsonElement e:bones){JsonObject b=e.getAsJsonObject();lookup.put(b.get("name").getAsString(),b);}
                List<Cube> values=new ArrayList<>();Box whole=null;
                for(JsonElement element:bones){JsonObject bone=element.getAsJsonObject();if(!bone.has("cubes"))continue;
                    for(JsonElement entry:bone.getAsJsonArray("cubes")){JsonObject cube=entry.getAsJsonObject();double inflate=cube.has("inflate")?cube.get("inflate").getAsDouble():0;Vec3d origin=vector(cube,"origin").subtract(inflate,inflate,inflate),size=vector(cube,"size").add(inflate*2,inflate*2,inflate*2);List<Vec3d> points=new ArrayList<>();
                        for(int corner=0;corner<8;corner++){
                            Vec3d v=mirror(origin.add((corner&1)==0?0:size.x,(corner&2)==0?0:size.y,(corner&4)==0?0:size.z));
                            v=around(v,mirror(vector(cube,"pivot")),vector(cube,"rotation"));JsonObject parent=bone;Set<String> visited=new HashSet<>();
                            while(parent!=null){if(!visited.add(parent.get("name").getAsString()))throw new IllegalStateException("Cyclic RP bones");v=around(v,mirror(vector(parent,"pivot")),vector(parent,"rotation"));parent=parent.has("parent")?lookup.get(parent.get("parent").getAsString()):null;}
                            points.add(v.multiply(1d/16));
                        }
                        Box box=bounds(points);values.add(new Cube(bone.get("name").getAsString(),box));whole=whole==null?box:whole.union(box);
                    }
                }
                if(whole==null)throw new IllegalStateException("Empty RP model "+key);
                return new Geometry(whole,List.copyOf(values),motionSelection(key,values,lookup));
            }catch(Exception error){CACHE_FAILURES.increment();RpDiagnostics.error(null,RpDiagnostics.typeIdForAsset(key),null,null,"source_geometry_cache","Cannot decode exact reviewed RP geometry "+key,error);throw new IllegalStateException("Invalid reviewed RP geometry "+key,error);}
        });
    }
    public static Map<String,Object> cacheMetrics(){return Map.of("entries",CACHE.size(),"hits",CACHE_HITS.sum(),"loadAttempts",CACHE_LOADS.sum(),"loadFailures",CACHE_FAILURES.sum(),"scope","shared JVM source-geometry cache; client/server values must not be added");}
    private static Box bounds(List<Vec3d> points) {
        double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(Vec3d v:points){double[] p={v.x,v.y,v.z};for(int a=0;a<3;a++){min[a]=Math.min(min[a],p[a]);max[a]=Math.max(max[a],p[a]);}}
        return new Box(min[0],min[1],min[2],max[0],max[1],max[2]);
    }
    public static Box localVisualBounds(String id) { return geometry(id).bounds; }
    /** Floor anchor or ceiling hook. Coordinates are model-local after GeckoLib's mirrored X. */
    public static Vec3d placementAnchor(String id,Direction face,boolean extended) {
        Box b=localVisualBounds(id);
        if(id.equals("stairs"))return new Vec3d(0,-12,-13.3125);
        if(id.equals("ladder"))return new Vec3d(0,extended?-473d/16:-245d/16,-24.75/16);
        return new Vec3d((b.minX+b.maxX)/2,face==Direction.DOWN&&id.equals("chandelier_small")?b.maxY:b.minY,(b.minZ+b.maxZ)/2);
    }
    public static Vec3d rotate(Vec3d value,float yaw) {
        double a=Math.toRadians(yaw-90),c=Math.cos(a),s=Math.sin(a);return new Vec3d(value.x*c+value.z*s,value.y,-value.x*s+value.z*c);
    }
    private static double scale(RpObjectEntity entity) { return entity.asset().scale()*entity.objectScale(); }
    public static Box visualBounds(RpObjectEntity entity) {
        Box b=localVisualBounds(entity.assetId());
        if(entity.assetId().equals("ladder")){b=null;for(Cube cube:geometry("ladder").cubes){Box part=cube.bone.equals("bottom")?cube.bounds.offset(0,entity.ladderOffsetY()/16,entity.ladderOffsetZ()/16):cube.bounds;b=b==null?part:b.union(part);}}
        if(entity.selectionMotionActive()&&!entity.assetId().equals("ladder"))for(Box motion:geometry(entity.assetId()).motionSelection)b=b.union(motion);
        return transformBounds(entity,b);
    }
    private static Box transformBounds(RpObjectEntity entity,Box b) {
        List<Vec3d> points=new ArrayList<>();double factor=scale(entity);
        double a=Math.toRadians(entity.getPitch()),c=Math.cos(a),s=Math.sin(a);
        for(int corner=0;corner<8;corner++){Vec3d p=new Vec3d((corner&1)==0?b.minX:b.maxX,(corner&2)==0?b.minY:b.maxY,(corner&4)==0?b.minZ:b.maxZ).multiply(factor);p=new Vec3d(p.x*c-p.y*s,p.x*s+p.y*c,p.z);points.add(rotate(p,entity.getYaw()).add(entity.getPos()));}
        return bounds(points);
    }
    public static List<Box> localPhysical(RpObjectEntity entity) {
        String id=entity.assetId();
        if(id.equals("chandelier_small"))return List.of();
        if(id.equals("stairs"))return geometry(id).cubes.stream().filter(c->c.bone.equals("stairs")&&positive(c.bounds)).map(Cube::bounds).toList();
        if(id.equals("npc_window"))return List.of(new Box(-16.5/16,-30d/16,12d/16,16.5/16,34.5/16,15d/16));
        // Source Forge measurement proved passability. This deliberately simple closed-leaf plane
        // includes the central decorative gap and stays closed throughout the authored idle pulse.
        if(id.equals("wood_gate"))return List.of(new Box(-119d/16,16d/16,-8d/16,119d/16,171d/16,8d/16));
        if(id.equals("ladder")){
            double bottom=(-473+entity.ladderOffsetY())/16,z=entity.ladderOffsetZ()/16;
            double top=(-192+entity.ladderOffsetY())/16;
            // Source root cube3: mirrored/rotated plane X[-11,11],Y12,Z[-24,24].
            // One pixel of support below the authored plane, independent of moving bottom.
            return List.of(new Box(-9d/16,-192d/16,-25.75/16,9d/16,14d/16,-23.75/16),new Box(-9d/16,bottom,-25.75/16+z,9d/16,top,-23.75/16+z),ladderPlatform());
        }
        return List.of();
    }
    private static boolean positive(Box box) { return box.getXLength()>1e-8&&box.getYLength()>1e-8&&box.getZLength()>1e-8; }
    public static List<Box> physicalBoxes(RpObjectEntity entity) { return worldBoxes(entity,localPhysical(entity)); }
    /** Non-colliding decorations are targeted per source part, not by the tool's motion envelope. */
    public static List<Box> ordinaryVisualBoxes(RpObjectEntity entity) {
        return geometry(entity.assetId()).cubes.stream().map(c->transformBounds(entity,entity.assetId().equals("ladder")&&c.bone.equals("bottom")?c.bounds.offset(0,entity.ladderOffsetY()/16,entity.ladderOffsetZ()/16):c.bounds)).toList();
    }
    public static List<Box> selectionBoxes(RpObjectEntity entity) {
        if(entity.assetId().equals("ladder")){List<Box> zones=new ArrayList<>(ladderZones(entity,.75,-1.8125,-1.25));zones.add(ladderPlatform());return worldBoxes(entity,zones);}
        if(custom(entity.assetId())){var physical=physicalBoxes(entity);return physical.isEmpty()?geometry(entity.assetId()).cubes.stream().map(c->transformBounds(entity,c.bounds)).toList():physical;}
        // These boxes are selection only: no model AABB is promoted to movement physics.
        List<Box> selected=new ArrayList<>();for(Cube cube:geometry(entity.assetId()).cubes)selected.add(transformBounds(entity,cube.bounds));
        if(entity.selectionMotionActive())for(Box box:geometry(entity.assetId()).motionSelection)selected.add(transformBounds(entity,box));
        return List.copyOf(selected);
    }
    public static Box ladderPlatform(){return new Box(-11d/16,11d/16,-24d/16,11d/16,12d/16,24d/16);}
    private record Motion(boolean rotation,Vec3d translation,double scale) {}
    /** Conservative per-part animation envelope, never movement geometry. Rotating parts use a pivot sphere.
     * Numeric keyframes and the source dog-idle affine sine expressions are bounded over all their times.
     * This deliberately does not claim a pixel-exact animated selection mesh. */
    private static List<Box> motionSelection(String id,List<Cube> cubes,Map<String,JsonObject> bones)throws java.io.IOException {
        if(id.equals("ladder"))return List.of(); // Its moving bottom has the existing exact sampled geometry.
        Map<String,Motion> motion=new HashMap<>();var spec=AssetCatalog.get(id);
        try(var stream=RpObjectGeometry.class.getResourceAsStream("/assets/bloodborne_rp/"+spec.animation())){
            if(stream==null)throw new IllegalStateException("Missing source animation "+id);
            JsonObject animations=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations");
            for(var declared:spec.clips().values()){
                JsonObject clip=animations.getAsJsonObject(declared.name());if(clip==null||!clip.has("bones"))continue;
                for(var entry:clip.getAsJsonObject("bones").entrySet()){
                    JsonObject channels=entry.getValue().getAsJsonObject();Motion previous=motion.getOrDefault(entry.getKey(),new Motion(false,Vec3d.ZERO,1));
                    boolean rotation=previous.rotation;Vec3d position=previous.translation;double scale=previous.scale;
                    if(channels.has("rotation"))rotation|=maximumMagnitude(channels.get("rotation"))>1e-8;
                    if(channels.has("position")){Vec3d next=maximumVector(channels.get("position")).multiply(1d/16);position=new Vec3d(Math.max(position.x,next.x),Math.max(position.y,next.y),Math.max(position.z,next.z));}
                    if(channels.has("scale"))scale=Math.max(scale,maximumMagnitude(channels.get("scale")));
                    motion.put(entry.getKey(),new Motion(rotation,position,scale));
                }
            }
        }
        if(motion.isEmpty())return List.of();List<Box> result=new ArrayList<>();
        for(Cube cube:cubes){Box box=cube.bounds;Vec3d sphereCenter=null;double sphereRadius=0;JsonObject bone=bones.get(cube.bone);boolean changed=false;Set<String> visited=new HashSet<>();
            while(bone!=null){String name=bone.get("name").getAsString();if(!visited.add(name))throw new IllegalStateException("Cyclic motion bones");Motion m=motion.get(name);
                if(m!=null&&(m.rotation||m.translation.lengthSquared()>1e-16||m.scale>1+1e-8)){
                    Vec3d pivot=mirror(vector(bone,"pivot"));JsonObject parent=bone.has("parent")?bones.get(bone.get("parent").getAsString()):null;
                    while(parent!=null){pivot=around(pivot,mirror(vector(parent,"pivot")),vector(parent,"rotation"));parent=parent.has("parent")?bones.get(parent.get("parent").getAsString()):null;}pivot=pivot.multiply(1d/16);
                    if(m.rotation||m.scale>1+1e-8){
                        // A sphere remains a sphere under rotation. Reusing its AABB
                        // corners at the next ancestor spuriously multiplies its
                        // radius by sqrt(3). The triangle inequality retains every
                        // previous point without inventing those cube corners.
                        double radius=sphereCenter==null?0:sphereCenter.distanceTo(pivot)+sphereRadius;
                        if(sphereCenter==null)for(int corner=0;corner<8;corner++)radius=Math.max(radius,new Vec3d((corner&1)==0?box.minX:box.maxX,(corner&2)==0?box.minY:box.maxY,(corner&4)==0?box.minZ:box.maxZ).distanceTo(pivot));
                        sphereCenter=pivot;sphereRadius=radius*m.scale;
                    }
                    if(m.translation.lengthSquared()>0){List<Vec3d> translations=new ArrayList<>();for(int corner=0;corner<8;corner++){Vec3d delta=new Vec3d((corner&1)==0?-m.translation.x:m.translation.x,(corner&2)==0?-m.translation.y:m.translation.y,(corner&4)==0?-m.translation.z:m.translation.z);JsonObject ancestor=bone.has("parent")?bones.get(bone.get("parent").getAsString()):null;while(ancestor!=null){delta=around(delta,Vec3d.ZERO,vector(ancestor,"rotation"));ancestor=ancestor.has("parent")?bones.get(ancestor.get("parent").getAsString()):null;}translations.add(delta);}if(sphereCenter!=null)sphereRadius+=translations.stream().mapToDouble(Vec3d::length).max().orElse(0);else{Box translation=bounds(translations);box=box.expand(Math.max(Math.abs(translation.minX),Math.abs(translation.maxX)),Math.max(Math.abs(translation.minY),Math.abs(translation.maxY)),Math.max(Math.abs(translation.minZ),Math.abs(translation.maxZ)));}}
                    if(sphereCenter!=null)box=new Box(sphereCenter.x-sphereRadius,sphereCenter.y-sphereRadius,sphereCenter.z-sphereRadius,sphereCenter.x+sphereRadius,sphereCenter.y+sphereRadius,sphereCenter.z+sphereRadius);changed=true;
                }bone=bone.has("parent")?bones.get(bone.get("parent").getAsString()):null;
            }if(changed)result.add(box);
        }return List.copyOf(result);
    }
    private static Vec3d maximumVector(JsonElement value){
        if(value.isJsonArray()){JsonArray a=value.getAsJsonArray();if(a.size()!=3)throw new IllegalStateException("Source motion vector is not3D");return new Vec3d(maximumMagnitude(a.get(0)),maximumMagnitude(a.get(1)),maximumMagnitude(a.get(2)));}
        if(value.isJsonObject()){Vec3d maximum=Vec3d.ZERO;for(var entry:value.getAsJsonObject().entrySet()){if(entry.getKey().equals("lerp_mode"))continue;Vec3d v=maximumVector(entry.getValue());maximum=new Vec3d(Math.max(maximum.x,v.x),Math.max(maximum.y,v.y),Math.max(maximum.z,v.z));}return maximum;}
        double v=maximumMagnitude(value);return new Vec3d(v,v,v);
    }
    private static double maximumMagnitude(JsonElement value){
        if(value==null||value.isJsonNull())return 0;
        if(value.isJsonArray()){double maximum=0;for(JsonElement child:value.getAsJsonArray())maximum=Math.max(maximum,maximumMagnitude(child));return maximum;}
        if(value.isJsonObject()){double maximum=0;for(var entry:value.getAsJsonObject().entrySet())if(!entry.getKey().equals("lerp_mode"))maximum=Math.max(maximum,maximumMagnitude(entry.getValue()));return maximum;}
        if(value.getAsJsonPrimitive().isNumber())return Math.abs(value.getAsDouble());
        String expression=value.getAsString().replaceAll("\\s+","");
        try{return Math.abs(Double.parseDouble(expression));}catch(NumberFormatException ignored){}
        var sine=java.util.regex.Pattern.compile("(?i)math\\.sin\\(query\\.anim_time\\*[+-]?[0-9.]+(?:[+-][0-9.]+)?\\)").matcher(expression);
        if(!sine.find())throw new IllegalStateException("Unbounded source selection expression");
        String prefix=expression.substring(0,sine.start()),suffix=expression.substring(sine.end());double constant=0,amplitude=1;
        if(!prefix.isEmpty()){char last=prefix.charAt(prefix.length()-1);if(last=='+'||last=='-'){amplitude=last=='-'?-1:1;if(prefix.length()>1)constant=Double.parseDouble(prefix.substring(0,prefix.length()-1));}else if(last=='*')amplitude=Double.parseDouble(prefix.substring(0,prefix.length()-1));else throw new IllegalStateException("Unsupported source selection prefix");}
        if(!suffix.isEmpty()){if(!suffix.startsWith("*"))throw new IllegalStateException("Unsupported source selection suffix");amplitude*=Double.parseDouble(suffix.substring(1));}
        return Math.abs(constant)+Math.abs(amplitude);
    }
    public static List<Box> climbingBoxes(RpObjectEntity entity) {
        if(!entity.assetId().equals("ladder"))return List.of();
        return worldBoxes(entity,ladderZones(entity,.625,-1.9,-1.2));
    }
    private static List<Box> ladderZones(RpObjectEntity entity,double halfWidth,double minZ,double maxZ){double y=entity.ladderOffsetY()/16,z=entity.ladderOffsetZ()/16;return List.of(new Box(-halfWidth,-192d/16,minZ,halfWidth,14d/16,maxZ),new Box(-halfWidth,-473d/16+y,minZ+z,halfWidth,-192d/16+y,maxZ+z));}
    /** Cardinal boxes remain exact; diagonal X/Z uses bounded quarter-block polygon strips, never a filled visual AABB. */
    private static List<Box> worldBoxes(RpObjectEntity entity,List<Box> local) {
        if(scale(entity)<=0)return List.of();List<Box> result=new ArrayList<>();double angle=Math.toRadians(entity.getYaw()-90),c=Math.cos(angle),s=Math.sin(angle),factor=scale(entity);
        for(Box raw:local){Box b=new Box(raw.minX*factor,raw.minY*factor,raw.minZ*factor,raw.maxX*factor,raw.maxY*factor,raw.maxZ*factor);
            if(Math.abs(entity.getPitch())>1e-7||Math.abs(c)<1e-7||Math.abs(s)<1e-7){result.add(transformBounds(entity,raw));continue;}
            double[][] polygon=new double[4][2],corners={{b.minX,b.minZ},{b.maxX,b.minZ},{b.maxX,b.maxZ},{b.minX,b.maxZ}};double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY;
            for(int i=0;i<4;i++){polygon[i][0]=corners[i][0]*c+corners[i][1]*s;polygon[i][1]=-corners[i][0]*s+corners[i][1]*c;minX=Math.min(minX,polygon[i][0]);maxX=Math.max(maxX,polygon[i][0]);}
            double step=.25;for(double left=Math.floor(minX/step)*step;left<maxX-1e-8;left+=step){double right=Math.min(maxX,left+step),first=Math.max(minX,left);List<Double> zs=new ArrayList<>();
                for(double x:new double[]{first,right})for(int i=0;i<4;i++){double[] a=polygon[i],d=polygon[(i+1)%4];if(Math.abs(d[0]-a[0])<1e-10){if(Math.abs(x-a[0])<1e-8){zs.add(a[1]);zs.add(d[1]);}}else{double t=(x-a[0])/(d[0]-a[0]);if(t>=-1e-8&&t<=1+1e-8)zs.add(a[1]+t*(d[1]-a[1]));}}
                for(double[] p:polygon)if(p[0]>=first&&p[0]<=right)zs.add(p[1]);if(zs.isEmpty())continue;double minZ=zs.stream().mapToDouble(Double::doubleValue).min().orElseThrow(),maxZ=zs.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
                if(right-first>1e-8&&maxZ-minZ>1e-8)result.add(new Box(first+entity.getX(),b.minY+entity.getY(),minZ+entity.getZ(),right+entity.getX(),b.maxY+entity.getY(),maxZ+entity.getZ()));
            }
        }return List.copyOf(result);
    }
}
