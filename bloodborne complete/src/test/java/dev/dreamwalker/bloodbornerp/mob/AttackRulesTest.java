package dev.dreamwalker.bloodbornerp.mob;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AttackRulesTest {
 @Test void meleeReachIncludesBoundaryAndRejectsDistanceBeyondIt() {
  double reach = Math.max(1.5D, 0.6D + 0.6D + 1.25D);
  assertTrue(AttackRules.withinMeleeReach(reach * reach, 0.6F, 0.6F));
  assertFalse(AttackRules.withinMeleeReach(reach * reach + 0.001D, 0.6F, 0.6F));
 }

 @Test void rangedReachAllowsPointBlankAndHasMaximumBoundary() {
  assertTrue(AttackRules.withinRangedReach(0.0D));
  assertTrue(AttackRules.withinRangedReach(3.99D));
  assertTrue(AttackRules.withinRangedReach(256.0D));
  assertFalse(AttackRules.withinRangedReach(256.01D));
 }
}
