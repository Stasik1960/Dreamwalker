package dev.dreamwalker.bloodbornerp.lamp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class LampPolicyTest {
 @Test void rejectsInvalidNamesAndBoundedRoutes(){assertFalse(LampPolicy.validName(""));assertFalse(LampPolicy.validName("x".repeat(65)));assertTrue(LampPolicy.validName("Hunter's Dream"));assertTrue(LampPolicy.validRoute(15,16,false));assertFalse(LampPolicy.validRoute(16,16,false));assertFalse(LampPolicy.validRoute(3,16,true));}
 @Test void rejectsReplayAndExpiredContexts(){assertTrue(LampPolicy.validContext(9,9,100,100));assertFalse(LampPolicy.validContext(9,10,100,99));assertFalse(LampPolicy.validContext(9,9,99,100));}
}
