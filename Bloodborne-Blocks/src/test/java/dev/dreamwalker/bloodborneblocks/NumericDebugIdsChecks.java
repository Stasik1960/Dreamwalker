package dev.dreamwalker.bloodborneblocks;

import java.util.LinkedHashSet;
import java.util.Set;

/** Resource-only checks for stable display aliases; no registry writes are needed. */
public final class NumericDebugIdsChecks {
 private NumericDebugIdsChecks() {}
 public static void main(String[] args){
  net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
  BloodborneBlocks.Data logical=BloodborneBlocks.loadDefinitions(),city=BloodborneBlocks.loadCityDefinitions();
  Set<String> expected=new LinkedHashSet<>();expected.add("architecture_part");logical.blocks.forEach(definition->expected.add(definition.id));city.blocks.forEach(definition->expected.add(definition.id));
  NumericDebugIds.loadAndValidate(expected);
  check(NumericDebugIds.all().size()==expected.size(),"every registered mod block has a numeric debug ID");
  check(NumericDebugIds.all().values().stream().allMatch(value->value.matches("[0-9]{5}")&&!value.equals("00000")),"numeric IDs are five digits from 00001");
  check(NumericDebugIds.lookup(NumericDebugIds.all().get("architecture_part")).equals("bloodborne_blocks:architecture_part"),"numeric lookup resolves the helper registry ID");
  System.out.println("NUMERIC DEBUG ID CHECKS PASSED: blocks="+expected.size());
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
