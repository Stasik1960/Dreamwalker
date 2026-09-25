package dev.dreamwalker.bloodborneblocks;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/** Standalone production geometry gate: every generated logical state is cell-local and bounded. */
public final class GeometryChecks {
 private GeometryChecks() {}

 public static void main(String[] args) throws Exception {
  SharedConstants.createGameVersion();Bootstrap.initialize();
  BloodborneBlocks.Data data=BloodborneBlocks.loadDefinitions();GeometryRuntime.loadAndValidate(data);
  var field=GeometryRuntime.class.getDeclaredField("BLOCKS");field.setAccessible(true);
  @SuppressWarnings("unchecked") Map<String,GeometryRuntime.GeometryBlock> blocks=(Map<String,GeometryRuntime.GeometryBlock>)field.get(null);
  int states=0,cells=0;var unique=Collections.newSetFromMap(new IdentityHashMap<GeometryRuntime.GeometryState,Boolean>());
  for(BloodborneBlocks.Definition definition:data.blocks){
   GeometryRuntime.GeometryBlock block=blocks.get(definition.id);check(block!=null&&block.states.keySet().equals(definition.states.keySet()),"production geometry states: "+definition.id);
   for(var entry:block.states.entrySet()){
    states++;GeometryRuntime.GeometryState state=entry.getValue();check(state!=null&&state.parsedCells!=null&&!state.parsedCells.isEmpty(),"prepared geometry: "+definition.id+"["+entry.getKey()+"]");
    if(!unique.add(state))continue;
    for(GeometryRuntime.GeometryCell cell:state.parsedCells.values()){cells++;checkShape(cell.collisionShape);checkShape(cell.outlineShape);check(cell.outline.size()<=1,"one selection box per cell");check(cell.collision.size()<=5,"bounded collision boxes per cell");}
   }
  }
  check(blocks.keySet().containsAll(BloodborneBlocks.productionPalette().keySet()),"geometry includes every production object");
  System.out.println("GEOMETRY CHECKS PASSED: states="+states+" unique="+unique.size()+" cells="+cells);
 }
 private static void checkShape(VoxelShape shape){if(shape.isEmpty())return;for(Direction.Axis axis:Direction.Axis.values())check(shape.getMin(axis)>=-1.0e-6&&shape.getMax(axis)<=1+1.0e-6,"COLLISION_OUTSIDE_OWNED_CELLS");}
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
