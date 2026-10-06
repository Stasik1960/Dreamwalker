package dev.dreamwalker.bloodbornerp.content;
import java.util.Map;
public record AssetSpec(String id, String model, String texture, String animation, float width, float height,
                        float scale, String displayName, Map<String, Clip> clips) {
 public record Clip(String name, float seconds, boolean loop) {}
 public Clip clip(String suffix) { return clips.get(suffix); }
 public String animationName(String suffix) { Clip c=clip(suffix); return c==null ? "" : c.name(); }
}
