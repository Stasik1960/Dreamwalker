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
}
