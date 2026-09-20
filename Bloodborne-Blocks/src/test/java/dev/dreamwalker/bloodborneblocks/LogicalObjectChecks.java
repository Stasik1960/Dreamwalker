package dev.dreamwalker.bloodborneblocks;

import java.util.*;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.util.math.BlockPos;

/** Resource-contract checks run without starting Minecraft or loading client classes. */
public final class LogicalObjectChecks {
 private LogicalObjectChecks() {}
 public static void main(String[] args){
 SharedConstants.createGameVersion();Bootstrap.initialize();
 BloodborneBlocks.Data data=BloodborneBlocks.loadDefinitions();
  // Same-ID editor updates retain the old root/helpers unless every new cell is loaded and ownable.
  check(ArchitectureBlock.canReplaceLogicalState(true,true,true),"logical replacement happy path");
  check(!ArchitectureBlock.canReplaceLogicalState(false,true,true),"old helper cells must be loaded");
  check(!ArchitectureBlock.canReplaceLogicalState(true,false,true),"new helper cells must be loaded");
  check(!ArchitectureBlock.canReplaceLogicalState(true,true,false),"foreign helper cells reject replacement");
  BlockPos root=BlockPos.ORIGIN,east=root.east(),west=root.west();
  check(GeometryRuntime.isCurrentHelperCell(Set.of(root,east),root,east),"current helper cell belongs to root");
  check(!GeometryRuntime.isCurrentHelperCell(Set.of(root,east),root,root),"root is never a helper");
  check(!GeometryRuntime.isCurrentHelperCell(Set.of(root,east),root,west),"rotated profile rejects stale helper");
  check(!GeometryRuntime.isCurrentHelperCell(Set.of(root,west),root,east),"recreated smaller/different profile rejects old helper");
  List<BloodborneBlocks.Definition> logical=data.blocks.stream().filter(definition->definition.logical).toList();
  check(!logical.isEmpty(),"no logical definitions");
  Set<String> ids=new HashSet<>(),meshKeys=new HashSet<>();
  for(BloodborneBlocks.Definition definition:logical){
   check(definition.id.startsWith("o_"),"logical ID: "+definition.id);
   check(Set.of("static","connected","door","gate","shutter","ladder","lantern").contains(definition.behavior),"behavior: "+definition.id);
   check(definition.properties!=null&&definition.defaultProperties!=null&&definition.states!=null&&definition.models!=null,"missing logical fields: "+definition.id);
   check(ids.add(definition.id),"duplicate logical ID: "+definition.id);
   for(String key:definition.states.keySet()){String mesh=definition.models.get(key);check(mesh!=null&&!mesh.isBlank(),"missing state mesh: "+definition.id+"["+key+"]");meshKeys.add(mesh);}
   if("connected".equals(definition.behavior))for(String direction:List.of("north","east","south","west"))check(definition.properties.containsKey(direction),"connected property: "+definition.id+"."+direction);
  }
  GeometryRuntime.loadAndValidate(data);
  Map<String,ModularMeshData.Mesh> meshes=ModularMeshData.loadLogicalAndValidate();
  check(meshes.keySet().containsAll(meshKeys),"missing logical mesh keys");
  System.out.println("LOGICAL OBJECT CHECKS PASSED: definitions="+logical.size()+" meshes="+meshKeys.size());
 }
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
