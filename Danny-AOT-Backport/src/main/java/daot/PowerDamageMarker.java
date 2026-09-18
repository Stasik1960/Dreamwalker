package daot;

public final class PowerDamageMarker {
   public static final ThreadLocal<Boolean> APPLYING_ABILITY = ThreadLocal.withInitial(() -> Boolean.FALSE);

   private PowerDamageMarker() {
   }

   public static boolean isApplyingAbility() {
      return APPLYING_ABILITY.get();
   }
}
