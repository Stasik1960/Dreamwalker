package daot.advancement;

import daot.DannysAot;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.util.Identifier;

public class ModCriteriaTriggers {
   public static AttackTitanShiftTrigger ATTACK_TITAN_SHIFT;
   public static ColossalTitanShiftTrigger COLOSSAL_TITAN_SHIFT;
   public static InheritedTitanPowerTrigger INHERITED_TITAN_POWER;
   public static EnteredParadisVillageTrigger ENTERED_PARADIS_VILLAGE;
   public static TitanShiftTrigger ARMORED_TITAN_SHIFT;
   public static TitanShiftTrigger FEMALE_TITAN_SHIFT;
   public static TitanShiftTrigger BEAST_TITAN_SHIFT;
   public static TitanShiftTrigger BEAST_TITAN_SHIFT_ROYAL;
   public static TitanShiftTrigger JAW_TITAN_SHIFT;
   public static TitanShiftTrigger CART_TITAN_SHIFT;

   public static void register() {
      ATTACK_TITAN_SHIFT = net.fabricmc.fabric.api.object.builder.v1.advancement.CriterionRegistry.register(new AttackTitanShiftTrigger(new Identifier("dannys-aot", "attack_titan_shift")));
      COLOSSAL_TITAN_SHIFT = net.fabricmc.fabric.api.object.builder.v1.advancement.CriterionRegistry.register(new ColossalTitanShiftTrigger(new Identifier("dannys-aot", "colossal_titan_shift")));
      INHERITED_TITAN_POWER = net.fabricmc.fabric.api.object.builder.v1.advancement.CriterionRegistry.register(new InheritedTitanPowerTrigger(new Identifier("dannys-aot", "inherited_titan_power")));
      ENTERED_PARADIS_VILLAGE = net.fabricmc.fabric.api.object.builder.v1.advancement.CriterionRegistry.register(new EnteredParadisVillageTrigger(new Identifier("dannys-aot", "entered_paradis_village")));
      ARMORED_TITAN_SHIFT = net.fabricmc.fabric.api.object.builder.v1.advancement.CriterionRegistry.register(new TitanShiftTrigger(new Identifier("dannys-aot", "armored_titan_shift")));
      FEMALE_TITAN_SHIFT = net.fabricmc.fabric.api.object.builder.v1.advancement.CriterionRegistry.register(new TitanShiftTrigger(new Identifier("dannys-aot", "female_titan_shift")));
      BEAST_TITAN_SHIFT = net.fabricmc.fabric.api.object.builder.v1.advancement.CriterionRegistry.register(new TitanShiftTrigger(new Identifier("dannys-aot", "beast_titan_shift")));
      BEAST_TITAN_SHIFT_ROYAL = net.fabricmc.fabric.api.object.builder.v1.advancement.CriterionRegistry.register(new TitanShiftTrigger(new Identifier("dannys-aot", "beast_titan_shift_royal")));
      JAW_TITAN_SHIFT = net.fabricmc.fabric.api.object.builder.v1.advancement.CriterionRegistry.register(new TitanShiftTrigger(new Identifier("dannys-aot", "jaw_titan_shift")));
      CART_TITAN_SHIFT = net.fabricmc.fabric.api.object.builder.v1.advancement.CriterionRegistry.register(new TitanShiftTrigger(new Identifier("dannys-aot", "cart_titan_shift")));
      DannysAot.LOGGER.info("Registered custom advancement criteria triggers");
   }
}
