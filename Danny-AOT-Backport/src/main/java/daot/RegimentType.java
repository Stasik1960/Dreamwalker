package daot;

import net.minecraft.util.StringIdentifiable;

public enum RegimentType implements StringIdentifiable {
   GARRISON("garrison"),
   MILITARY_POLICE("military_police"),
   SCOUT("scout"),
   TRAINING("training"),
   GOLD_CLOAK("gold_cloak");

   private final String name;

   private RegimentType(String name) {
      this.name = name;
   }

   @Override
   public String asString() {
      return this.name;
   }

   public static RegimentType fromName(String name) {
      for (RegimentType type : values()) {
         if (type.name.equals(name)) {
            return type;
         }
      }

      return GARRISON;
   }
}
