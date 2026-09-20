package dev.dreamwalker.bloodborneblocks;

import java.util.*;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

/** Standalone data/voxel test: no game client, world or server is started. */
public final class GeometryChecks {
 public static void main(String[] args) throws Exception {
  SharedConstants.createGameVersion();Bootstrap.initialize();
  long started=System.nanoTime();
  var data=BloodborneBlocks.loadDefinitions();
  GeometryRuntime.loadAndValidate(data);
  var field=GeometryRuntime.class.getDeclaredField("BLOCKS");field.setAccessible(true);
  @SuppressWarnings("unchecked") var blocks=(Map<String,GeometryRuntime.GeometryBlock>)field.get(null);
  var legacy=data.blocks.stream().filter(definition->!definition.modular&&!definition.logical).toList();
  check(legacy.size()==503,"503 palette IDs retained");
  int states=0,cells=0;Set<GeometryRuntime.GeometryState> profiles=Collections.newSetFromMap(new IdentityHashMap<>());
  for(var definition:legacy){var block=blocks.get(definition.id);for(var entry:block.states.entrySet()) {
   states++;var state=entry.getValue();if(!profiles.add(state))continue;
   for(var cell:state.parsedCells.values()){
    cells++;checkShape(cell.collisionShape);checkShape(cell.outlineShape);
    check(cell.outline.size()<=1,"one rectangular selection per cell");
    check(cell.collision.size()<=16,"bounded simple physics per cell");
   }
  }}
  check(states>=18968,"complete state coverage");
  for(String id:List.of("dead_fire_coral_fan","orange_wool","cyan_wool","pink_wool","potted_dead_bush","potted_azalea_bush"))
   for(var s:blocks.get(id).states.values())for(var c:s.parsedCells.values())check(c.collisionShape.isEmpty(),"vegetation is pass-through: "+id);
  for(var entry:blocks.get("waxed_exposed_cut_copper_stairs").states.entrySet()){
   if(!entry.getKey().contains("shape=straight"))continue;
   var s=entry.getValue();double[] bounds=bounds(s);int bottom=(int)Math.floor(bounds[1]),top=(int)Math.ceil(bounds[4]);
   for(int y=bottom;y<top;y++){final int level=y;check(s.parsedCells.keySet().stream().anyMatch(p->p.getY()==level),"continuous climb cells: "+entry.getKey());}
  }
  for(String id:List.of("acacia_stairs","birch_stairs","dark_oak_stairs")) {
   var block=blocks.get(id);Set<String> tested=new HashSet<>();
   for(var entry:block.states.entrySet()) {
    String key=entry.getKey();if(!key.contains("assembled=true")||!key.contains("half=bottom")||!key.contains("shape=straight")||!key.contains("open=false"))continue;
    var closed=entry.getValue();var open=block.states.get(key.replace("open=false","open=true"));
    check(open!=null&&open!=closed,"door has distinct open geometry: "+id);
    // Closed leaf spans the middle of the opening; an opened leaf cannot remain there.
    double[] bounds=bounds(closed);double x=(bounds[0]+bounds[3])/2,y=(bounds[1]+bounds[4])/2,z=(bounds[2]+bounds[5])/2;
    boolean northSouth=key.contains("facing=north")||key.contains("facing=south");int depthAxis=northSouth?2:0;
    boolean closedAcross=false;
    for(double depth=bounds[depthAxis]+1.0/128;depth<bounds[depthAxis+3];depth+=1.0/64){
     closedAcross|=contains(closed,northSouth?x:depth,y,northSouth?depth:z);
     for(double across=-.3;across<=.301;across+=.1)for(double height=.1;height<1.8;height+=.2)
      check(!contains(open,northSouth?x+across:depth,bounds[1]+height,northSouth?depth:z+across),"open door clears player corridor: "+id+" "+key);
    }
    check(closedAcross,"closed door seals centre corridor: "+id);tested.add(key.split("facing=")[1].split(",")[0]);
   }
   check(tested.size()==4,"tested all door orientations: "+id);
  }
  System.out.printf(Locale.ROOT,"GEOMETRY CHECKS PASSED: %d states, %d shared profiles, %d cells, %.2f s%n",states,profiles.size(),cells,(System.nanoTime()-started)/1e9);
 }
 static void checkShape(VoxelShape shape){
  if(shape.isEmpty())return;
  for(Direction.Axis axis:Direction.Axis.values())check(shape.getMin(axis)>=0&&shape.getMax(axis)<=1,"cell-local shape");
  final int[] edges={0};shape.forEachEdge((a,b,c,d,e,f)->edges[0]++);
  check(edges[0]<50000,"bounded outline edge traversal");
 }
 static double[] bounds(GeometryRuntime.GeometryState s){
  double[] result={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
  s.parsedCells.forEach((p,c)->{for(double[] b:c.collision){double[] o={p.getX(),p.getY(),p.getZ()};for(int a=0;a<3;a++){result[a]=Math.min(result[a],b[a]+o[a]);result[a+3]=Math.max(result[a+3],b[a+3]+o[a]);}}});return result;
 }
 static boolean contains(GeometryRuntime.GeometryState s,double x,double y,double z){
  for(var entry:s.parsedCells.entrySet())for(double[] b:entry.getValue().collision){var p=entry.getKey();if(x>=b[0]+p.getX()-1e-6&&x<=b[3]+p.getX()+1e-6&&y>=b[1]+p.getY()-1e-6&&y<=b[4]+p.getY()+1e-6&&z>=b[2]+p.getZ()-1e-6&&z<=b[5]+p.getZ()+1e-6)return true;}return false;
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
