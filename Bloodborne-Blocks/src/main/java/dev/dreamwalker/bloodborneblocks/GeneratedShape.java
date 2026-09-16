package dev.dreamwalker.bloodborneblocks;
import net.minecraft.util.shape.*;
import java.util.*;
/** Exact union on the model's coordinate grid, without repeated boolean simplification. */
final class GeneratedShape extends ArrayVoxelShape {
 private final List<double[]> boxes;
 private GeneratedShape(BitSetVoxelSet voxels,double[][] points,List<double[]> boxes){super(voxels,points[0],points[1],points[2]);this.boxes=boxes;}
 static VoxelShape of(List<double[]> boxes){
  for(double[]b:boxes)for(int a=0;a<6;a++)if(b[a]==0.0)b[a]=0.0;
  if(boxes.isEmpty())return VoxelShapes.empty();
  if(boxes.size()==1){double[]b=boxes.get(0);return VoxelShapes.cuboid(b[0],b[1],b[2],b[3],b[4],b[5]);}
  double[][]points=new double[3][];
  for(int a=0;a<3;a++){TreeSet<Double> values=new TreeSet<>();for(double[]b:boxes){values.add(b[a]);values.add(b[a+3]);}points[a]=values.stream().mapToDouble(Double::doubleValue).toArray();}
  BitSetVoxelSet voxels=new BitSetVoxelSet(points[0].length-1,points[1].length-1,points[2].length-1);
  for(double[]b:boxes){int[]lo=new int[3],hi=new int[3];for(int a=0;a<3;a++){lo[a]=Arrays.binarySearch(points[a],b[a]);hi[a]=Arrays.binarySearch(points[a],b[a+3]);}
   for(int x=lo[0];x<hi[0];x++)for(int y=lo[1];y<hi[1];y++)for(int z=lo[2];z<hi[2];z++)voxels.set(x,y,z);
  }
  return new GeneratedShape(voxels,points,boxes);
 }
 @Override public VoxelShape simplify(){return VoxelShapes.combineAndSimplify(this,VoxelShapes.empty(),net.minecraft.util.function.BooleanBiFunction.OR);}
 @Override public void forEachBox(VoxelShapes.BoxConsumer consumer){for(double[]b:boxes)consumer.consume(b[0],b[1],b[2],b[3],b[4],b[5]);}
}
