package dev.dreamwalker.bloodborneblocks;

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Read-only, server-side target inspection for authored logical objects. */
final class LogicalTargetDebug {
 private static final double RANGE=16.0;
 private LogicalTargetDebug() {}

 static String inspect(ServerPlayerEntity player){
  ServerWorld world=player.getServerWorld();BlockHitResult hit=raycastLoaded(world,player);
  if(hit==null||hit.getType()!=HitResult.Type.BLOCK)return "Bloodborne debug: no loaded outline target within 16 blocks.";
  return inspectTarget(world,hit.getBlockPos());
 }

 static String inspectTarget(ServerWorld world,BlockPos target){
  if(!world.isChunkLoaded(target))return "Bloodborne debug: target chunk is not loaded.";
  BlockState targetState=world.getBlockState(target);
  if(targetState.isOf(BloodborneBlocks.PART_BLOCK))return helper(world,target,targetState);
  return rootOrOrdinary(target,targetState,"MASTER",null);
 }

 static BlockHitResult raycastLoaded(ServerWorld world,ServerPlayerEntity player){
  Vec3d start=player.getCameraPosVec(1.0F),end=start.add(player.getRotationVec(1.0F).multiply(RANGE));
  if(!rayChunksLoaded(world,start,end))return null;
  BlockHitResult hit=world.raycast(new RaycastContext(start,end,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
  return hit.getType()==HitResult.Type.BLOCK?hit:null;
 }

 /** Check the small ray bounding rectangle before World#raycast so no chunk may be requested. */
 private static boolean rayChunksLoaded(ServerWorld world,Vec3d start,Vec3d end){
  int minX=Math.floorDiv(Math.min(BlockPos.ofFloored(start).getX(),BlockPos.ofFloored(end).getX()),16);
  int maxX=Math.floorDiv(Math.max(BlockPos.ofFloored(start).getX(),BlockPos.ofFloored(end).getX()),16);
  int minZ=Math.floorDiv(Math.min(BlockPos.ofFloored(start).getZ(),BlockPos.ofFloored(end).getZ()),16);
  int maxZ=Math.floorDiv(Math.max(BlockPos.ofFloored(start).getZ(),BlockPos.ofFloored(end).getZ()),16);
  for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(!world.isChunkLoaded(new BlockPos(x<<4,world.getBottomY(),z<<4)))return false;
  return true;
 }

 private static String helper(ServerWorld world,BlockPos helper,BlockState helperState){
  ArchitecturePartBlockEntity part=GeometryRuntime.part(world,helper);
  if(part==null)return ordinary(helper,helperState,"invalid architecture_part (missing ownership)");
  if(part.bindings().size()!=1)return ordinary(helper,helperState,"shared architecture_part ("+part.bindings().size()+" owners; target a root explicitly)");
  BlockPos root=part.rootPos();
  if(!world.isChunkLoaded(root))return ordinary(helper,helperState,"invalid architecture_part (root chunk unloaded)");
  BlockState rootState=world.getBlockState(root);
  if(!GeometryRuntime.ownsHelper(rootState,root,helper,part.ownerId()))return ordinary(helper,helperState,"invalid architecture_part (stale ownership)");
  return rootOrOrdinary(root,rootState,"HELPER",helper.subtract(root));
 }

 private static String rootOrOrdinary(BlockPos root,BlockState state,String target,BlockPos helperOffset){
  if(!(state.getBlock() instanceof ArchitectureBlock block)||!block.definition.logical)return ordinary(root,state,null);
  BloodborneBlocks.ProductionEntry production=BloodborneBlocks.productionEntry(block.definition.id);
  if(production==null)return ordinary(root,state,"production palette metadata unavailable");
  String stateKey=BloodborneBlocks.key(state);
  String facing=state.contains(Properties.HORIZONTAL_FACING)?state.get(Properties.HORIZONTAL_FACING).asString():"none";
  StringBuilder report=new StringBuilder("Source Review: ").append(String.join(", ",production.source_reviews()))
   .append("\nLogical/Object ID: ").append(BloodborneBlocks.id(block.definition.id)).append("\nSemantic part: ").append(production.semantic_label())
   .append("\nMaster: ").append(coordinates(root))
   .append("\nTarget: ").append(target);
  if(helperOffset!=null)report.append("\nHelper offset: ").append(coordinates(helperOffset));
  return report.append("\nFacing: ").append(facing).append("\nState: ").append(stateKey)
   .append("\nProduction palette schema: v1").toString();
 }

 private static String ordinary(BlockPos pos,BlockState state,String detail){
  String result="Registry ID: "+Registries.BLOCK.getId(state.getBlock())+"\nPosition: "+coordinates(pos)+"\nTarget: NON-LOGICAL";
  return detail==null?result:result+"\n"+detail;
 }
 private static String coordinates(BlockPos pos){return pos.getX()+" "+pos.getY()+" "+pos.getZ();}
}
