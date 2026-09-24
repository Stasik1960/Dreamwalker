package dev.dreamwalker.bloodborneblocks;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Kept task name for Gradle wiring; validates production logical mesh resources only. */
public final class ModularDataChecks {
 private ModularDataChecks() {}

 public static void main(String[] args){
  SharedConstants.createGameVersion();Bootstrap.initialize();
  BloodborneBlocks.Data data=BloodborneBlocks.loadDefinitions();Map<String,ModularMeshData.Mesh> meshes=ModularMeshData.loadLogicalAndValidate();Set<String> referenced=new HashSet<>();
  for(BloodborneBlocks.Definition definition:data.blocks)for(String mesh:definition.models.values()){referenced.add(mesh);check(meshes.containsKey(mesh)&&!meshes.get(mesh).polygons.isEmpty(),"production mesh: "+definition.id+" -> "+mesh);}
  System.out.println("PRODUCTION MESH CHECKS PASSED: referenced="+referenced.size()+" available="+meshes.size());
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
