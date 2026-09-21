package dev.dreamwalker.bloodborneblocks;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;

/** Bootstrap-only contract test: loader validates all supplied transform vectors. */
public final class LogicalContractV2Checks {
 private LogicalContractV2Checks() {}
 public static void main(String[] args){
  SharedConstants.createGameVersion();Bootstrap.initialize();
  BloodborneBlocks.Data definitions=BloodborneBlocks.loadDefinitions();
  GeometryRuntime.loadAndValidate(definitions);
  if(LogicalTransform.masterOrigin(new int[]{4,7,9},new int[]{0,0,0},0).getX()!=4)throw new AssertionError("identity transform");
  System.out.println("LOGICAL CONTRACT V2 CHECKS PASSED");
 }
}
