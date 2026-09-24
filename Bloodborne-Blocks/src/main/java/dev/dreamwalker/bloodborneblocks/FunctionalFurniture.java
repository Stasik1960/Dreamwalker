package dev.dreamwalker.bloodborneblocks;

import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.StairShape;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
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
  return sit(world,root,state,player,null);
 }

 static ActionResult sit(World world,BlockPos root,BlockState state,PlayerEntity player,BlockHitResult hit){
  if(world.isClient)return ActionResult.SUCCESS;
  if(!(world instanceof ServerWorld server)||player.hasVehicle()||!isBench(state))return ActionResult.FAIL;
  List<ArchitectureSeatEntity> existing=server.getEntitiesByClass(ArchitectureSeatEntity.class,new Box(root).expand(8),seat->seat.rootPos().equals(root));
  if(isAuthoredBench(state))return sitAuthored(server,root,state,player,hit,existing);
  for(ArchitectureSeatEntity seat:existing){if(seat.hasPassengers())return ActionResult.FAIL;seat.discard();}
  SeatPoint point=seatPoint(root,state);ArchitectureSeatEntity seat=BloodborneBlocks.SEAT_ENTITY.create(server);if(seat==null)return ActionResult.FAIL;
  Direction facing=state.contains(Properties.HORIZONTAL_FACING)?state.get(Properties.HORIZONTAL_FACING).getOpposite():Direction.SOUTH;
  // PlayerEntity already supplies a -0.35 sitting offset; do not apply it twice.
  seat.bind(root,0,point.x,point.y,point.z,facing.asRotation());
  if(!server.spawnEntity(seat)){seat.discard();return ActionResult.FAIL;}
  if(!player.startRiding(seat,true)){seat.discard();return ActionResult.FAIL;}
  player.setYaw(facing.asRotation());player.setHeadYaw(facing.asRotation());player.setBodyYaw(facing.asRotation());return ActionResult.CONSUME;
 }

 private static boolean isAuthoredBench(BlockState state){
  return state.getBlock() instanceof ArchitectureBlock block&&block.definition.logical&&"bench".equals(block.definition.behavior)
   &&block.definition.seat_anchors!=null&&block.definition.seat_anchors.length>0;
 }

 private static boolean diagonal(BlockState state){
  var property=state.getBlock().getStateManager().getProperty("diagonal");
  return property instanceof BooleanProperty value&&state.get(value);
 }

 private static ActionResult sitAuthored(ServerWorld server,BlockPos root,BlockState state,PlayerEntity player,BlockHitResult hit,List<ArchitectureSeatEntity> existing){
  List<Vec3d> points=seatPoints(root,state);HashSet<Integer> occupied=new HashSet<>();
  for(ArchitectureSeatEntity seat:existing){
   if(!seat.hasPassengers()){seat.discard();continue;}
   if(seat.seatIndex()<0||seat.seatIndex()>=points.size())return ActionResult.FAIL;
   occupied.add(seat.seatIndex());
  }
  int selected=-1;double best=Double.POSITIVE_INFINITY;Vec3d click=hit==null?null:hit.getPos();
  for(int index=0;index<points.size();index++)if(!occupied.contains(index)){
   double distance=click==null?index:click.squaredDistanceTo(points.get(index));
   if(distance<best){best=distance;selected=index;}
  }
  if(selected<0||(click!=null&&best>9))return ActionResult.FAIL;
  ArchitectureSeatEntity seat=BloodborneBlocks.SEAT_ENTITY.create(server);if(seat==null)return ActionResult.FAIL;
  Direction facing=state.contains(Properties.HORIZONTAL_FACING)?state.get(Properties.HORIZONTAL_FACING).getOpposite():Direction.SOUTH;
  // Passenger Y is vehicle Y + mounted offset + PlayerEntity's -0.35
  // height offset. Its biped hip pivot is 12 model pixels (0.75 blocks)
  // above its feet: 1.0 + (-0.40) + (-0.35) + 0.75 = 1.0 plank Y.
  Vec3d point=points.get(selected);float yaw=facing.asRotation()+(diagonal(state)?45:0);seat.bind(root,selected,point.x,point.y,point.z,yaw,-.40);
  if(!server.spawnEntity(seat)){seat.discard();return ActionResult.FAIL;}
  if(!player.startRiding(seat,true)){seat.discard();return ActionResult.FAIL;}
  seat.updatePassengerPosition(player);
  player.setYaw(yaw);player.setHeadYaw(yaw);player.setBodyYaw(yaw);return ActionResult.CONSUME;
 }

 static void removeSeats(World world,BlockPos root){
  if(!(world instanceof ServerWorld server))return;
  for(ArchitectureSeatEntity seat:server.getEntitiesByClass(ArchitectureSeatEntity.class,new Box(root).expand(8),entity->entity.rootPos().equals(root)))seat.discard();
 }

 /** Authored north-facing local anchors, rotated about the block center. */
 static List<Vec3d> seatPoints(BlockPos root,BlockState state){
  if(!(state.getBlock() instanceof ArchitectureBlock block)||block.definition.seat_anchors==null)return List.of();
  Direction facing=state.contains(Properties.HORIZONTAL_FACING)?state.get(Properties.HORIZONTAL_FACING):Direction.NORTH;
  double angle=Math.toRadians(facing.asRotation()-180+(diagonal(state)?45:0));double cos=Math.cos(angle),sin=Math.sin(angle);
  java.util.ArrayList<Vec3d> points=new java.util.ArrayList<>();
  for(double[] anchor:block.definition.seat_anchors){
   if(anchor==null||anchor.length!=3)throw new IllegalStateException("Invalid authored seat anchor "+block.definition.id);
   double x=anchor[0]-.5,z=anchor[2]-.5;
   points.add(new Vec3d(root.getX()+.5+x*cos-z*sin,root.getY()+anchor[1],root.getZ()+.5+x*sin+z*cos));
  }
  return List.copyOf(points);
 }

 private static SeatPoint seatPoint(BlockPos root,BlockState state){
  GeometryRuntime.GeometryState geometry=GeometryRuntime.state(state);double[] bounds={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
  // Test complete authored primitives, not cell-clipped backrest caps which
  // can be thin enough to masquerade as the actual seat plank.
  if(geometry!=null&&geometry.gameplayBoxes!=null){
   double[] seat=null;double area=-1;
   for(double[] box:geometry.gameplayBoxes){double next=(box[3]-box[0])*(box[5]-box[2]);if(box[4]-box[1]<=.25&&next>area){seat=box;area=next;}}
   if(seat!=null)return new SeatPoint(root.getX()+(seat[0]+seat[3])*.5,root.getY()+seat[4],root.getZ()+(seat[2]+seat[5])*.5);
  }
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
