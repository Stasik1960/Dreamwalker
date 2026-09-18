package daot;

public enum TitanPowerType {
   ATTACK,
   COLOSSAL,
   ARMORED,
   BEAST,
   FEMALE,
   WARHAMMER,
   JAW;

   public String getTagName() {
      return switch (this) {
         case ATTACK -> "attack";
         case COLOSSAL -> "colossal";
         case ARMORED -> "armored";
         case BEAST -> "beast";
         case FEMALE -> "female";
         case WARHAMMER -> "warhammer";
         case JAW -> "jaw";
      };
   }

   public String getDisplayName() {
      return switch (this) {
         case ATTACK -> "Attack Titan";
         case COLOSSAL -> "Colossal Titan";
         case ARMORED -> "Armored Titan";
         case BEAST -> "Beast Titan";
         case FEMALE -> "Female Titan";
         case WARHAMMER -> "Warhammer Titan";
         case JAW -> "Jaw Titan";
      };
   }

   public static TitanPowerType fromOrdinal(int ordinal) {
      return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
   }
}
