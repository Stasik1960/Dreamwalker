package dev.dreamwalker.bloodbornerp.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import dev.dreamwalker.bloodbornerp.content.AssetSpec;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MobAnimationSelectorTest {
 @Test void curatedAttacksExcludeAuthoringHelpers() {
  assertEquals(java.util.List.of("attack1", "attack2"), MobAnimationSelector.attacks("brick_troll"));
  assertFalse(MobAnimationSelector.attacks("huntsman_d").contains("attack_sword1"));
 }
 @Test void idleAndDeathUseKnownFallbacks() {
  AssetSpec rifle = new AssetSpec("huntsman_d", "", "", "", 1, 1, 1, "", Map.of("idle1", clip(), "death_rifle", clip()));
  assertEquals("idle1", MobAnimationSelector.idle(rifle));
  assertEquals("death_rifle", MobAnimationSelector.death(rifle));
 }
 private static AssetSpec.Clip clip() { return new AssetSpec.Clip("animation.test", 1, false); }
 @Test void sourceLocomotionLoopsEvenWhenJsonOmitsLoopAndDeathHolds(){
  var idle=new AssetSpec.Clip("animation.huntsman_a.idle",4,false,"once");
  var death=new AssetSpec.Clip("animation.rabid_dog.death",3,false,"hold_on_last_frame");
  var spec=new AssetSpec("huntsman_a","","","",1,1,1,"",Map.of("idle",idle,"death",death,"walk",clip(),"run",clip()));
  assertEquals(software.bernie.geckolib.core.animation.Animation.LoopType.LOOP,MobAnimationSelector.loopType(spec,"idle"));
  assertEquals(software.bernie.geckolib.core.animation.Animation.LoopType.HOLD_ON_LAST_FRAME,MobAnimationSelector.loopType(spec,"death"));
  assertEquals("run",MobAnimationSelector.movement(spec,true,.5f));
  assertEquals("idle",MobAnimationSelector.movement(spec,false,0));
 }
}
