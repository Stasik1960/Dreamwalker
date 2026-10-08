package dev.dreamwalker.bloodbornerp.mob;

import dev.dreamwalker.bloodbornerp.content.AssetSpec;
import java.util.List;
import java.util.Map;

/** Catalog-aware animation names; helper/authoring clips are deliberately never combat choices. */
public final class MobAnimationSelector {
 private static final Map<String, List<String>> ATTACKS = Map.ofEntries(
  Map.entry("cleric_beast", List.of("attack1", "attack2", "attack3")), Map.entry("vicar_amelia", List.of("attack1", "attack2")),
  Map.entry("blood_starved_beast", List.of("attack1", "attack2")), Map.entry("scourge_beast", List.of("attack1", "attack2")),
  Map.entry("executioner", List.of("attack1", "attack2", "attack3")), Map.entry("huntsman_a", List.of("attack1", "attack2")),
  Map.entry("huntsman_b", List.of("attack1", "attack2")), Map.entry("huntsman_c", List.of("attack1", "attack2")),
  Map.entry("huntsman_d", List.of("attack1", "attack2")), Map.entry("huntsman_wheelchair", List.of("attack1", "attack2")),
  Map.entry("large_huntsman", List.of("attack1", "attack2", "attack3")), Map.entry("carrion_crow", List.of("attack1", "attack2")),
  Map.entry("giant_rat", List.of("attack1", "attack2")), Map.entry("small_rat", List.of("attack1", "attack2")),
  Map.entry("rabid_dog", List.of("attack1", "attack2")), Map.entry("maneater_boar", List.of("attack1", "attack2")),
  Map.entry("brick_troll", List.of("attack1", "attack2")), Map.entry("rotted_corpse", List.of("attack1", "attack2")));
 private MobAnimationSelector() {}
 public static List<String> attacks(String id) { return ATTACKS.getOrDefault(id, List.of()); }
 public static String idle(AssetSpec spec) { return suffix(spec, "idle", "idle1", "idle2"); }
 public static String hit(AssetSpec spec) { return suffix(spec, "hit"); }
 public static String death(AssetSpec spec) { return suffix(spec, "death", "death_rifle", "death_saber"); }
 public static String movement(AssetSpec spec,boolean moving,float speed) {
  if(!moving)return idle(spec);
  // Match the source controller's locomotion loop; some source JSON clips omit loop.
  if(speed>.35f&&spec.clip("run")!=null)return "run";
  return spec.clip("walk")!=null?"walk":idle(spec);
 }
 public static software.bernie.geckolib.core.animation.Animation.LoopType loopType(AssetSpec spec,String suffix){
  if(suffix.equals("walk")||suffix.equals("run")||suffix.equals(idle(spec)))return software.bernie.geckolib.core.animation.Animation.LoopType.LOOP;
  if(suffix.equals(death(spec)))return software.bernie.geckolib.core.animation.Animation.LoopType.HOLD_ON_LAST_FRAME;
  return spec.clip(suffix).loopType();
 }
 private static String suffix(AssetSpec spec, String... options) { for (String option : options) if (spec.clip(option) != null) return option; return ""; }
}
