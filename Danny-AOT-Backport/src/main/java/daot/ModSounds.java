package daot;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {
   public static final SoundEvent GAS_BOOST = registerFixed("gas_boost", 80.0F);
   public static final SoundEvent STEAM_POOF = registerFixed("steam_poof", 96.0F);
   public static final SoundEvent HOOK_SHOOT_1 = registerFixed("hook_shoot_1", 64.0F);
   public static final SoundEvent HOOK_SHOOT_2 = registerFixed("hook_shoot_2", 64.0F);
   public static final SoundEvent HOOK_SHOOT_APG = registerFixed("hook_shoot_apg", 64.0F);
   public static final SoundEvent GUN_SHOOT_APG = registerFixed("gun_shoot_apg", 64.0F);
   public static final SoundEvent GUN_SHOOT_APG2 = registerFixed("gun_shoot_apg2", 64.0F);
   public static final SoundEvent SHOT_IMPACT = registerFixed("shot_impact", 100.0F);
   public static final SoundEvent HOOK_IMPACT = registerFixed("hook_impact", 64.0F);
   public static final SoundEvent HOOK_RETRACT = registerFixed("hook_retract", 48.0F);
   public static final SoundEvent ODM_FLIGHT = registerFixed("odm_flight", 80.0F);
   public static final SoundEvent TITAN_STOMP = register("titan_stomp");
   public static final SoundEvent TITAN_STOMP_ARMORED_1 = register("titan_stomp_armored1");
   public static final SoundEvent TITAN_STOMP_ARMORED_2 = register("titan_stomp_armored2");
   public static final SoundEvent TITAN_STOMP_ARMORED_3 = register("titan_stomp_armored3");
   public static final SoundEvent TITAN_SHIFT = register("titan_shift");
   public static final SoundEvent ATTACK_TITAN_ROAR = register("attacktitanroar");
   public static final SoundEvent ARMORED_TITAN_ROAR = register("armoredtitanroar");
   public static final SoundEvent ARMORED_SHATTER = register("armored_shatter");
   public static final SoundEvent FEMALE_TITAN_ROAR = registerFixed("femaletitan_roar", 200.0F);
   public static final SoundEvent CHARGEUP = register("chargeup");
   public static final SoundEvent HANDBITE = register("handbite");
   public static final SoundEvent PATHS_AMBIENT = register("paths_ambient");
   public static final SoundEvent TRIGGER = registerFixed("trigger", 32.0F);
   public static final SoundEvent APG_LOAD = registerFixed("load", 32.0F);
   public static final SoundEvent APG_UNLOAD = registerFixed("unload", 32.0F);
   public static final SoundEvent BLOODMOON_1 = register("bloodmoon1");
   public static final SoundEvent BLOODMOON_2 = register("bloodmoon2");
   public static final SoundEvent BLOODMOON_3 = register("bloodmoon3");
   public static final SoundEvent BLOODMOON_4 = register("bloodmoon4");
   public static final SoundEvent SLASH1 = register("slash1");
   public static final SoundEvent SLASH2 = register("slash2");
   public static final SoundEvent SLASH3 = register("slash3");
   public static final SoundEvent FLESH_IMPACT_1 = register("flesh_impact_1");
   public static final SoundEvent FLESH_IMPACT_2 = register("flesh_impact_2");
   public static final SoundEvent FLESH_IMPACT_3 = register("flesh_impact_3");
   public static final SoundEvent FLESH_IMPACT_4 = register("flesh_impact_4");
   public static final SoundEvent FLESH_IMPACT_5 = register("flesh_impact_5");
   public static final SoundEvent FLESH_IMPACT_6 = register("flesh_impact_6");
   public static final SoundEvent FLESH_IMPACT_7 = register("flesh_impact_7");
   public static final SoundEvent BLADE_SWING = register("bladeswing");
   public static final SoundEvent ROYAL_SHOUT = register("royalshout");
   public static final SoundEvent STOP_COMMAND = register("stopcommand");
   public static final SoundEvent CONTINUE_COMMAND = register("continuecommand");
   public static final SoundEvent UNSHEATHE = register("unsheathe");
   public static final SoundEvent FLARE_SHOOT = register("flare_shoot");
   public static final SoundEvent HANDCUFF_LOCK = register("handcuff_lock");
   public static final SoundEvent HANDCUFF_UNLOCK = register("handcuff_unlock");
   public static final SoundEvent RAGA = registerFixed("raga", 100.0F);
   public static final SoundEvent SUMMON_RAGA = register("summon_raga");
   public static final SoundEvent TAME_SUMMON = register("tame_summon");
   public static final SoundEvent AURA = registerFixed("aura", 128.0F);
   public static final SoundEvent DANNY = register("danny");
   public static final SoundEvent SADTITAN1 = register("sadtitan1");
   public static final SoundEvent SADTITAN2 = register("sadtitan2");
   public static final SoundEvent SADTITAN3 = register("sadtitan3");
   public static final SoundEvent SADTITAN4 = register("sadtitan4");
   public static final SoundEvent BOOM = registerFixed("boom", 200.0F);
   public static final SoundEvent LASER = registerFixed("laser", 150.0F);
   public static final SoundEvent SPEED = registerFixed("speed", 64.0F);
   public static final SoundEvent STRWS_AIM = register("strws_aim");
   public static final SoundEvent BITE = registerFixed("bite", 64.0F);
   public static final SoundEvent IMPACT_1 = registerFixed("impact_1", 100.0F);
   public static final SoundEvent IMPACT_2 = registerFixed("impact_2", 100.0F);
   public static final SoundEvent IMPACT_3 = registerFixed("impact_3", 100.0F);
   public static final SoundEvent IMPACT_4 = registerFixed("impact_4", 100.0F);
   public static final SoundEvent IMPACT_5 = registerFixed("impact_5", 100.0F);

   private static SoundEvent register(String name) {
      Identifier id = new Identifier("dannys-aot", name);
      return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
   }

   private static SoundEvent registerFixed(String name, float range) {
      Identifier id = new Identifier("dannys-aot", name);
      return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id, range));
   }

   public static void initialize() {
   }
}
