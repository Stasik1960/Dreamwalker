package dev.dreamwalker.bloodbornerp.content;
import java.util.Map;
public record AssetSpec(String id, String model, String texture, String animation, float width, float height,
                        float scale, String displayName, Map<String, Clip> clips) {
 public record Clip(String name, float seconds, boolean loop, String loopMode) {
  public Clip(String name,float seconds,boolean loop){this(name,seconds,loop,null);}
  public software.bernie.geckolib.core.animation.Animation.LoopType loopType(){
   if("hold_on_last_frame".equals(loopMode))return software.bernie.geckolib.core.animation.Animation.LoopType.HOLD_ON_LAST_FRAME;
   return (loop||"loop".equals(loopMode))?software.bernie.geckolib.core.animation.Animation.LoopType.LOOP:software.bernie.geckolib.core.animation.Animation.LoopType.PLAY_ONCE;
  }
 }
 public Clip clip(String suffix) { return clips.get(suffix); }
 public String animationName(String suffix) { Clip c=clip(suffix); return c==null ? "" : c.name(); }
}
