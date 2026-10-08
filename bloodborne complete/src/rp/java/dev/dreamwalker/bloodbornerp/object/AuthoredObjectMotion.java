package dev.dreamwalker.bloodbornerp.object;

import com.google.gson.*;
import dev.dreamwalker.bloodbornerp.content.AssetCatalog;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.util.math.Vec3d;

/** Samples the supplied numeric linear keyframes; source animation resources are never rewritten. */
public final class AuthoredObjectMotion {
    private static volatile Map<String,Map<String,NavigableMap<Double,Vec3d>>> gate;
    private AuthoredObjectMotion() {}
    private static Map<String,Map<String,NavigableMap<Double,Vec3d>>> gate() {
        if(gate!=null)return gate;
        String path="/assets/bloodborne_rp/"+AssetCatalog.get("wood_gate").animation();
        try(var stream=AuthoredObjectMotion.class.getResourceAsStream(path)){
            if(stream==null)throw new IllegalStateException("Missing authored gate animation");
            JsonObject json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject clip=json.getAsJsonObject("animations").getAsJsonObject(AssetCatalog.get("wood_gate").animationName("idle"));
            if(Math.abs(clip.get("animation_length").getAsDouble()-1.6)>1e-9)throw new IllegalStateException("Unexpected gate duration");
            Map<String,Map<String,NavigableMap<Double,Vec3d>>> parsed=new LinkedHashMap<>();
            for(var bone:clip.getAsJsonObject("bones").entrySet()){
                Map<String,NavigableMap<Double,Vec3d>> channels=new LinkedHashMap<>();
                for(var channel:bone.getValue().getAsJsonObject().entrySet()){
                    if(!Set.of("position","rotation").contains(channel.getKey()))throw new IllegalStateException("Unreviewed gate channel");
                    NavigableMap<Double,Vec3d> frames=new TreeMap<>();
                    for(var frame:channel.getValue().getAsJsonObject().entrySet()){
                        JsonArray v=frame.getValue().getAsJsonArray();frames.put(Double.parseDouble(frame.getKey()),new Vec3d(v.get(0).getAsDouble(),v.get(1).getAsDouble(),v.get(2).getAsDouble()));
                    }channels.put(channel.getKey(),Collections.unmodifiableNavigableMap(frames));
                }parsed.put(bone.getKey(),Map.copyOf(channels));
            }gate=Collections.unmodifiableMap(parsed);return gate;
        }catch(Exception error){throw new IllegalStateException("Invalid reviewed gate animation",error);}
    }
    public static Set<String> gateBones(){return gate().keySet();}
    public static Vec3d gateSample(String bone,String channel,double seconds){
        var channels=gate().get(bone);if(channels==null)return Vec3d.ZERO;var frames=channels.get(channel);if(frames==null)return Vec3d.ZERO;
        var before=frames.floorEntry(seconds);var after=frames.ceilingEntry(seconds);
        if(before==null)return frames.firstEntry().getValue();if(after==null)return frames.lastEntry().getValue();
        if(before.getKey().equals(after.getKey()))return before.getValue();double t=(seconds-before.getKey())/(after.getKey()-before.getKey());return before.getValue().lerp(after.getValue(),t);
    }
}
