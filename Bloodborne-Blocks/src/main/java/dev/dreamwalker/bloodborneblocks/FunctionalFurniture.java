package dev.dreamwalker.bloodborneblocks;

import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.StairShape;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Exact opt-in behavior for the authored ladder and bench models. */
public final class FunctionalFurniture {
 private static final String LADDER="ladder";
 private static final String LARGE_LADDER="waxed_exposed_cut_copper_stairs";
 private static final String BENCH="nether_brick_stairs";
 private static final Map<LivingEntity,ClimbCache> CLIMB_CACHE=java.util.Collections.synchronizedMap(new WeakHashMap<>());

 private FunctionalFurniture(){}

 static boolean isClimbable(World world,BlockPos pos){
  if(!world.isChunkLoaded(pos))return false;BlockState state=world.getBlockState(pos);
  if(state.getBlock() instanceof ArchitectureBlock)return isLadderRoot(state);
  if(!state.isOf(BloodborneBlocks.PART_BLOCK))return false;
  ArchitecturePartBlockEntity part=GeometryRuntime.part(world,pos);if(part==null||!world.isChunkLoaded(part.rootPos()))return false;
  BlockState root=world.getBlockState(part.rootPos());return GeometryRuntime.ownsHelper(root,part.rootPos(),pos,part.ownerId())&&isLadderRoot(root);
 }

 public static BlockPos climbablePos(LivingEntity entity){
  long tick=entity.getWorld().getTime();double x=entity.getX(),y=entity.getY(),z=entity.getZ();
  ClimbCache cached=CLIMB_CACHE.get(entity);if(cached!=null&&cached.tick==tick&&cached.x==x&&cached.y==y&&cached.z==z)return cached.result;
  BlockPos result=findClimbablePos(entity);CLIMB_CACHE.put(entity,new ClimbCache(tick,x,y,z,result));return result;
 }

 private static BlockPos findClimbablePos(LivingEntity entity){
  Box body=entity.getBoundingBox();Box box=new Box(body.minX-.125,body.minY,body.minZ-.125,body.maxX+.125,body.minY+.6,body.maxZ+.125);
  for(BlockPos pos:BlockPos.iterate((int)Math.floor(box.minX),(int)Math.floor(box.minY),(int)Math.floor(box.minZ),(int)Math.floor(box.maxX),(int)Math.floor(box.maxY),(int)Math.floor(box.maxZ))){
   if(!isClimbable(entity.getWorld(),pos))continue;
   var shape=entity.getWorld().getBlockState(pos).getOutlineShape(entity.getWorld(),pos);
   if(!shape.isEmpty()&&shape.getBoundingBox().offset(pos).intersects(box))return pos.toImmutable();
  }
  return null;
 }

 private static boolean isLadderRoot(BlockState state){
  if(!(state.getBlock() instanceof ArchitectureBlock block))return false;
  if("ladder".equals(block.definition.semantic))return true;
  if(block.definition.id.equals(LADDER))return true;
  return block.definition.id.equals(LARGE_LADDER)&&state.contains(Properties.STAIR_SHAPE)&&state.get(Properties.STAIR_SHAPE)==StairShape.STRAIGHT;
 }

 static boolean isBench(BlockState state){
  if(!(state.getBlock() instanceof ArchitectureBlock block))return false;
  if(block.definition.logical)return "bench".equals(block.definition.behavior)&&"bench".equals(block.definition.semantic);
  if(!block.definition.id.equals(BENCH))return false;
  if(!state.contains(Properties.BLOCK_HALF)||state.get(Properties.BLOCK_HALF)!=BlockHalf.BOTTOM||!state.contains(Properties.STAIR_SHAPE))return false;
  StairShape shape=state.get(Properties.STAIR_SHAPE);return shape==StairShape.STRAIGHT||shape==StairShape.OUTER_LEFT||shape==StairShape.OUTER_RIGHT;
 }

 static ActionResult sit(World world,BlockPos root,BlockState state,PlayerEntity player){
  if(world.isClient)return ActionResult.SUCCESS;
  if(!(world instanceof ServerWorld server)||player.hasVehicle()||!isBench(state))return ActionResult.FAIL;
  List<ArchitectureSeatEntity> existing=server.getEntitiesByClass(ArchitectureSeatEntity.class,new Box(root).expand(8),seat->seat.rootPos().equals(root));
  for(ArchitectureSeatEntity seat:existing){if(seat.hasPassengers())return ActionResult.FAIL;seat.discard();}
  SeatPoint point=seatPoint(root,state);ArchitectureSeatEntity seat=BloodborneBlocks.SEAT_ENTITY.create(server);if(seat==null)return ActionResult.FAIL;
  Direction facing=state.contains(Properties.HORIZONTAL_FACING)?state.get(Properties.HORIZONTAL_FACING).getOpposite():Direction.SOUTH;
  // PlayerEntity already supplies a -0.35 sitting offset; do not apply it twice.
  seat.bind(root,point.x,point.y,point.z,facing.asRotation());
  if(!server.spawnEntity(seat)){seat.discard();return ActionResult.FAIL;}
  if(!player.startRiding(seat,true)){seat.discard();return ActionResult.FAIL;}
  player.setYaw(facing.asRotation());player.setHeadYaw(facing.asRotation());player.setBodyYaw(facing.asRotation());return ActionResult.CONSUME;
 }

 static void removeSeats(World world,BlockPos root){
  if(!(world instanceof ServerWorld server))return;
  for(ArchitectureSeatEntity seat:server.getEntitiesByClass(ArchitectureSeatEntity.class,new Box(root).expand(8),entity->entity.rootPos().equals(root)))seat.discard();
 }

 private static SeatPoint seatPoint(BlockPos root,BlockState state){
  GeometryRuntime.GeometryState geometry=GeometryRuntime.state(state);double[] bounds={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
  double[] best={-1,root.getY()+.5};
  if(geometry!=null)geometry.parsedCells.forEach((offset,cell)->cell.collisionShape.forEachBox((minX,minY,minZ,maxX,maxY,maxZ)->{
   double gx=offset.getX()+minX,gz=offset.getZ()+minZ,gxx=offset.getX()+maxX,gzz=offset.getZ()+maxZ;
   bounds[0]=Math.min(bounds[0],gx);bounds[1]=Math.min(bounds[1],gz);bounds[2]=Math.max(bounds[2],gxx);bounds[3]=Math.max(bounds[3],gzz);
   double area=(maxX-minX)*(maxZ-minZ),thickness=maxY-minY;if(thickness<=.25&&area>best[0]){best[0]=area;best[1]=root.getY()+offset.getY()+maxY;}
  }));
  if(!Double.isFinite(bounds[0]))return new SeatPoint(root.getX()+.5,root.getY()+.5,root.getZ()+.5);
  return new SeatPoint(root.getX()+(bounds[0]+bounds[2])*.5,best[1],root.getZ()+(bounds[1]+bounds[3])*.5);
 }

 private record SeatPoint(double x,double y,double z){}
 private record ClimbCache(long tick,double x,double y,double z,BlockPos result){}
}
