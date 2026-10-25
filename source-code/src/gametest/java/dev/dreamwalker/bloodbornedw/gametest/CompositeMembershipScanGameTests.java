package dev.dreamwalker.bloodbornedw.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;

/** Only cold-reader contracts affected by the scanner change. */
public final class CompositeMembershipScanGameTests implements FabricGameTest {
 @GameTest(templateName="bloodborne_dw:window_test",tickLimit=80)
 public void dictionaryExactMasksAndNoColdRetention(TestContext t)throws Exception {new CompositeMembershipReadGameTests().dictionaryMasksMatchFullReaderWithoutRetainingOrPublishingColdShards(t);}
 @GameTest(templateName="bloodborne_dw:window_test",tickLimit=80)
 public void legacyExactMasksAndFreshSavedEdits(TestContext t)throws Exception {new CompositeMembershipReadGameTests().legacyMasksStayExactAndColdReadsObserveLaterSavedChanges(t);}
 @GameTest(templateName="bloodborne_dw:window_test",tickLimit=80)
 public void corruptReferenceAndRecoveryWithoutRootLoad(TestContext t)throws Exception {new CompositeMembershipReadGameTests().corruptRequestedMembershipStillFailsAndNeverForcesRootLoad(t);}
 @GameTest(templateName="bloodborne_dw:window_test",tickLimit=80)
 public void unrequestedOwnerBudgetStillFails(TestContext t)throws Exception {new CompositeMembershipReadGameTests().unrequestedOwnerBudgetStillValidated(t);}
}
