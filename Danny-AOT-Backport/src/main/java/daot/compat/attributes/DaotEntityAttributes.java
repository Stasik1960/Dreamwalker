package daot.compat.attributes;

import net.minecraft.entity.attribute.ClampedEntityAttribute;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * The LivingEntity attributes added by vanilla after 1.20.1 which Danny's AoT
 * uses. Their vanilla 1.21 identifiers, defaults, ranges and tracking flags are
 * retained so serialized modifiers and attribute sync keep the same contract.
 */
public final class DaotEntityAttributes {
   public static final EntityAttribute EXPLOSION_KNOCKBACK_RESISTANCE = register(
      "generic.explosion_knockback_resistance", 0.0, 0.0, 1.0
   );
   public static final EntityAttribute GRAVITY = register("generic.gravity", 0.08, -1.0, 1.0);
   public static final EntityAttribute JUMP_STRENGTH = register("generic.jump_strength", 0.41999998688697815, 0.0, 32.0);
   public static final EntityAttribute SAFE_FALL_DISTANCE = register("generic.safe_fall_distance", 3.0, -1024.0, 1024.0);
   public static final EntityAttribute SCALE = register("generic.scale", 1.0, 0.0625, 16.0);
   public static final EntityAttribute STEP_HEIGHT = register("generic.step_height", 0.6, 0.0, 10.0);
   public static final EntityAttribute WATER_MOVEMENT_EFFICIENCY = register(
      "generic.water_movement_efficiency", 0.0, 0.0, 1.0
   );

   private DaotEntityAttributes() {
   }

   public static void initialize() {
      // Loading this class performs registration. This explicit hook documents
      // that it must run before default entity attribute builders are created.
   }

   private static EntityAttribute register(String path, double defaultValue, double min, double max) {
      Identifier id = new Identifier("minecraft", path);
      EntityAttribute existing = Registries.ATTRIBUTE.get(id);
      if (existing != null) {
         return existing;
      }

      return Registry.register(
         Registries.ATTRIBUTE,
         id,
         new ClampedEntityAttribute("attribute.name." + path, defaultValue, min, max).setTracked(true)
      );
   }
}
