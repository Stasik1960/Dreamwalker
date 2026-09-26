package dev.dreamwalker.bloodborneblocks;

import com.mojang.brigadier.context.CommandContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.literal;

/** Ephemeral, server-side visual variant editor for already loaded logical roots. */
final class LogicalVisualCommands {
 private static final int MAX_VOLUME=32768;
 private static final Map<UUID,Selection> SELECTIONS=new HashMap<>();
 static record Selection(ServerWorld world,BlockPos first,BlockPos second) {}
 private LogicalVisualCommands() {}

 static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> command(){
  return literal("visual")
   .then(literal("get").executes(context->target(context,"get")))
   .then(literal("base").executes(context->target(context,"base")))
   .then(literal("alt").executes(context->target(context,"alt")))
   .then(literal("toggle").executes(context->target(context,"toggle")))
   .then(literal("pos1").executes(context->position(context,true)))
   .then(literal("pos2").executes(context->position(context,false)))
   .then(literal("region")
    .then(literal("base").executes(context->region(context,"base")))
    .then(literal("alt").executes(context->region(context,"alt")))
    .then(literal("toggle").executes(context->region(context,"toggle"))));
 }

 static void registerLifecycle(){
  ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->SELECTIONS.remove(handler.player.getUuid()));
  ServerLifecycleEvents.SERVER_STOPPING.register(server->SELECTIONS.clear());
 }

 private static ServerPlayerEntity player(ServerCommandSource source){
  return source.getEntity() instanceof ServerPlayerEntity player?player:null;
 }

 private static int position(CommandContext<ServerCommandSource> context,boolean first){
  ServerPlayerEntity player=player(context.getSource());
  if(player==null){context.getSource().sendError(Text.literal("Bloodborne visual: player required."));return 0;}
  Selection old=SELECTIONS.get(player.getUuid());BlockPos pos=player.getBlockPos();
  Selection next=updatedSelection(old,player.getServerWorld(),pos,first);
  SELECTIONS.put(player.getUuid(),next);context.getSource().sendFeedback(()->Text.literal("Bloodborne visual: "+(first?"pos1":"pos2")+" set to "+coordinates(pos)+"."),false);return 1;
 }

 static Selection updatedSelection(Selection old,ServerWorld world,BlockPos pos,boolean first){
  // A position from another dimension must never become the opposite endpoint.
  if(old!=null&&old.world!=world)old=null;
  return first?new Selection(world,pos,old==null?null:old.second):new Selection(world,old==null?null:old.first,pos);
 }

 private static int target(CommandContext<ServerCommandSource> context,String requested){
  ServerPlayerEntity player=player(context.getSource());
  if(player==null){context.getSource().sendError(Text.literal("Bloodborne visual: player required."));return 0;}
  ServerWorld world=player.getServerWorld();BlockHitResult hit=LogicalTargetDebug.raycastLoaded(world,player);
  if(hit==null||hit.getType()!=HitResult.Type.BLOCK){context.getSource().sendError(Text.literal("Bloodborne visual: no loaded target."));return 0;}
  BlockPos root=resolveMaster(world,hit.getBlockPos());
  if(root==null){context.getSource().sendError(Text.literal("Bloodborne visual: target is not a valid loaded logical master."));return 0;}
  return change(context.getSource(),world,root,requested)?1:0;
 }

 private static int region(CommandContext<ServerCommandSource> context,String requested){
  ServerPlayerEntity player=player(context.getSource());
  if(player==null){context.getSource().sendError(Text.literal("Bloodborne visual: player required."));return 0;}
  Selection selection=SELECTIONS.get(player.getUuid());
  if(selection==null||selection.first==null||selection.second==null||selection.world!=player.getServerWorld()){
   context.getSource().sendError(Text.literal("Bloodborne visual: set pos1 and pos2 in this dimension first."));return 0;
  }
  int minX=Math.min(selection.first.getX(),selection.second.getX()),maxX=Math.max(selection.first.getX(),selection.second.getX());
  int minY=Math.min(selection.first.getY(),selection.second.getY()),maxY=Math.max(selection.first.getY(),selection.second.getY());
  int minZ=Math.min(selection.first.getZ(),selection.second.getZ()),maxZ=Math.max(selection.first.getZ(),selection.second.getZ());
  long width=(long)maxX-minX+1,height=(long)maxY-minY+1,depth=(long)maxZ-minZ+1;
  if(width>MAX_VOLUME||height>MAX_VOLUME||depth>MAX_VOLUME||width*height>MAX_VOLUME||width*height*depth>MAX_VOLUME){context.getSource().sendError(Text.literal("Bloodborne visual: region exceeds "+MAX_VOLUME+" blocks."));return 0;}
  ServerWorld world=selection.world;
  for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(!world.isChunkLoaded(new BlockPos(x,world.getBottomY(),z))){context.getSource().sendError(Text.literal("Bloodborne visual: region has unloaded chunks."));return 0;}
  Set<BlockPos> roots=new HashSet<>();int scanned=0,skipped=0;
  for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++){
   scanned++;BlockPos root=resolveMaster(world,new BlockPos(x,y,z));if(root==null){skipped++;continue;}
   BlockState state=world.getBlockState(root);if(!GeometryRuntime.allCellsLoaded(world,root,state)||!roots.add(root)){skipped++;continue;}
  }
  int changed=0;for(BlockPos root:roots)if(change(null,world,root,requested))changed++;else skipped++;
  int totalScanned=scanned,totalMasters=roots.size(),totalChanged=changed,totalSkipped=skipped;
  context.getSource().sendFeedback(()->Text.literal("Bloodborne visual: cells scanned="+totalScanned+", masters scanned="+totalMasters+", masters changed="+totalChanged+", skipped/non-logical="+totalSkipped+"."),false);
  return changed;
 }

 static BlockPos resolveMaster(ServerWorld world,BlockPos target){
  if(!world.isChunkLoaded(target))return null;BlockState state=world.getBlockState(target);
  if(state.getBlock() instanceof ArchitectureBlock block&&block.definition.logical)return target;
  if(!state.isOf(BloodborneBlocks.PART_BLOCK))return null;
  ArchitecturePartBlockEntity part=GeometryRuntime.part(world,target);if(part==null||part.bindings().size()!=1||!world.isChunkLoaded(part.rootPos()))return null;
  BlockState root=world.getBlockState(part.rootPos());return GeometryRuntime.ownsHelper(root,part.rootPos(),target,part.ownerId())?part.rootPos():null;
 }

 @SuppressWarnings("unchecked")
 static boolean change(ServerCommandSource source,ServerWorld world,BlockPos root,String requested){
  BlockState state=world.getBlockState(root);Property<?> raw=state.getBlock().getStateManager().getProperty("visual");
  if(!(state.getBlock() instanceof ArchitectureBlock block)||!block.definition.logical||raw==null){if(source!=null)source.sendError(Text.literal("Bloodborne visual: target has no visual variant."));return false;}
  Property<String> property=(Property<String>)raw;String current=state.get(property);
  if("get".equals(requested)){if(source!=null)source.sendFeedback(()->Text.literal("Bloodborne visual: "+current+" at "+coordinates(root)+"."),false);return true;}
  String value="toggle".equals(requested)?("base".equals(current)?"alt":"base"):requested;
  if(!property.parse(value).isPresent()){if(source!=null)source.sendError(Text.literal("Bloodborne visual: variant "+value+" is unavailable."));return false;}
  if(!GeometryRuntime.allCellsLoaded(world,root,state)){if(source!=null)source.sendError(Text.literal("Bloodborne visual: logical footprint is not fully loaded."));return false;}
  if(value.equals(current)){if(source!=null)source.sendFeedback(()->Text.literal("Bloodborne visual: "+value+" already set at "+coordinates(root)+"."),false);return false;}
  if(!GeometryRuntime.setVisualState(world,root,state.with(property,value))){
   if(source!=null)source.sendError(Text.literal("Bloodborne visual: world rejected the state update."));return false;
  }
  if(source!=null)source.sendFeedback(()->Text.literal("Bloodborne visual: "+value+" at "+coordinates(root)+"."),false);return true;
 }
 private static String coordinates(BlockPos pos){return pos.getX()+" "+pos.getY()+" "+pos.getZ();}
}
