package daot;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public class ModEffects {
   public static final StatusEffect POWER_DISABLE = register("power_disable", new StatusEffect(StatusEffectCategory.HARMFUL, 16724016) {});

   private static StatusEffect register(String name, StatusEffect effect) {
      return Registry.register(Registries.STATUS_EFFECT, new Identifier("dannys-aot", name), effect);
   }

   public static boolean isPowerDisabled(LivingEntity entity) {
      return entity != null && entity.hasStatusEffect(POWER_DISABLE);
   }

   public static void initialize() {
   }
}
