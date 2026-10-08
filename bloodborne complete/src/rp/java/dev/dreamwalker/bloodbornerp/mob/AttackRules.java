package dev.dreamwalker.bloodbornerp.mob;

/** Small, side-effect-free combat rules shared by the server attack goal and tests. */
public final class AttackRules {
 private AttackRules() {}

 public static boolean withinMeleeReach(double squaredDistance, float attackerWidth, float targetWidth) {
  double reach = Math.max(1.5D, attackerWidth + targetWidth + 1.25D);
  return squaredDistance <= reach * reach;
 }

 public static boolean withinRangedReach(double squaredDistance) {
  return squaredDistance >= 0.0D && squaredDistance <= 256.0D;
 }
}
