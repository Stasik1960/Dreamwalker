package dev.dreamwalker.bloodborneblocks;

import net.minecraft.block.*;
import net.minecraft.block.enums.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.Set;

/** Decorative state machine: no random ticks, block entities, redstone machines or inventories. */
public final class ArchitectureBlock extends Block implements Waterloggable {
 private static final ThreadLocal<BloodborneBlocks.Definition> CONSTRUCTING=new ThreadLocal<>();
 public final BloodborneBlocks.Definition definition;
 private final Map<BlockState,BlockState> originals=new IdentityHashMap<>();
 public static ArchitectureBlock create(BloodborneBlocks.Definition d){CONSTRUCTING.set(d);try{return new ArchitectureBlock(d);}finally{CONSTRUCTING.remove();}}
 private static Settings settings(BloodborneBlocks.Definition d){
  Settings s=Settings.create().strength(d.hardness,d.resistance).sounds(d.sourceBlock.getSoundGroup(d.sourceBlock.getDefaultState())).mapColor(d.sourceBlock.getDefaultState().getMapColor(EmptyBlockView.INSTANCE,BlockPos.ORIGIN)).slipperiness(d.slipperiness).velocityMultiplier(d.velocity).jumpVelocityMultiplier(d.jump).luminance(state->d.states.get(BloodborneBlocks.key(state))[2]).pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK);
  if(!d.full_cube||d.custom_geometry)s.nonOpaque().solidBlock((state,world,pos)->false).suffocates((state,world,pos)->false).blockVision((state,world,pos)->false);
  if(!d.offset.equals("none"))s.offset(d.offset.equals("xyz")?OffsetType.XYZ:OffsetType.XZ).dynamicBounds();
  return s;
 }
 private ArchitectureBlock(BloodborneBlocks.Definition d){
  super(settings(d));definition=d;BlockState defaultState=getStateManager().getDefaultState();
  for(var e:d.defaultProperties.entrySet())defaultState=BloodborneBlocks.set(defaultState,d.propertyObjects.get(e.getKey()),e.getValue());setDefaultState(defaultState);
  for(BlockState state:getStateManager().getStates()){
   if(!d.states.containsKey(BloodborneBlocks.key(state)))throw new IllegalStateException("Unmapped state "+state);
   BlockState original=d.sourceBlock.getDefaultState();for(var e:state.getEntries().entrySet())if(original.contains(e.getKey()))original=BloodborneBlocks.set(original,e.getKey(),BloodborneBlocks.value(e.getKey(),e.getValue()));originals.put(state,original);
  }
 }
 protected void appendProperties(StateManager.Builder<Block,BlockState> builder){for(Property<?>p:CONSTRUCTING.get().propertyObjects.values())builder.add(p);}
 public BlockState original(BlockState state){return originals.getOrDefault(state,definition.sourceBlock.getDefaultState());}
 /** Keep selection bounds inside the physical shape. Authored render geometry can overhang
  * a block for decorative silhouettes; using it as an outline produced ghost lines in-world. */
 @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return GeometryRuntime.rootShape(state,true);}
 @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return GeometryRuntime.rootShape(state,false);}
 @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos){return definition.full_cube&&!definition.custom_geometry?VoxelShapes.fullCube():VoxelShapes.empty();}
 @Override public float getAmbientOcclusionLightLevel(BlockState state,BlockView world,BlockPos pos){return definition.full_cube?.2F:1F;}
 @Override public boolean isTransparent(BlockState state,BlockView world,BlockPos pos){return !definition.full_cube;}
 @Override public long getRenderingSeed(BlockState state,BlockPos pos){return definition.sourceBlock.getRenderingSeed(original(state),pos);}
 @SuppressWarnings({"rawtypes","unchecked"}) private BlockState preserveCustom(BlockState mapped,BlockState state){
  BlockState sourceDefault=definition.sourceBlock.getDefaultState();for(var entry:state.getEntries().entrySet())if(!sourceDefault.contains(entry.getKey())&&mapped.contains(entry.getKey()))mapped=mapped.with((Property)entry.getKey(),(Comparable)entry.getValue());return mapped;
 }
 @Override public BlockState rotate(BlockState state,BlockRotation rotation){BlockState mapped=preserveCustom(getStateWithProperties(definition.sourceBlock.rotate(original(state),rotation)),state);if(definition.extra_facing)return mapped.with(Properties.HORIZONTAL_FACING,rotation.rotate(state.get(Properties.HORIZONTAL_FACING)));return mapped;}
 @Override public BlockState mirror(BlockState state,BlockMirror mirror){BlockState mapped=preserveCustom(getStateWithProperties(definition.sourceBlock.mirror(original(state),mirror)),state);if(definition.extra_facing)return mapped.with(Properties.HORIZONTAL_FACING,mirror.apply(state.get(Properties.HORIZONTAL_FACING)));return mapped;}
 @Override public BlockState getPlacementState(ItemPlacementContext ctx){
  BlockState old=ctx.getWorld().getBlockState(ctx.getBlockPos());
  if(definition.kind.equals("slab")&&old.isOf(this)&&old.get(Properties.SLAB_TYPE)!=SlabType.DOUBLE)return old.with(Properties.SLAB_TYPE,SlabType.DOUBLE).with(Properties.WATERLOGGED,false);
  boolean authored=definition.kind.equals("generic")||definition.kind.equals("model_door");
  BlockState source=authored?null:definition.sourceBlock.getPlacementState(ctx);BlockState result=source==null?getDefaultState():getStateWithProperties(source);
  if(authored){
   Property<?> facing=getStateManager().getProperty("facing");if(facing!=null)result=BloodborneBlocks.set(result,facing,ctx.getHorizontalPlayerFacing().getOpposite().asString());
   Property<?> axis=getStateManager().getProperty("axis");if(axis!=null)result=BloodborneBlocks.set(result,axis,ctx.getSide().getAxis().asString());
  }
  if(definition.kind.equals("model_door")){
   if(result.contains(Properties.BLOCK_HALF))result=result.with(Properties.BLOCK_HALF,BlockHalf.BOTTOM);
   if(result.contains(Properties.STAIR_SHAPE))result=result.with(Properties.STAIR_SHAPE,StairShape.STRAIGHT);
   if(result.contains(Properties.OPEN))result=result.with(Properties.OPEN,false);
  }
  if(result.contains(BloodborneBlocks.ASSEMBLED))result=result.with(BloodborneBlocks.ASSEMBLED,true);
  if(definition.extra_facing)result=result.with(Properties.HORIZONTAL_FACING,ctx.getHorizontalPlayerFacing().getOpposite());
  if(result.contains(Properties.WATERLOGGED))result=result.with(Properties.WATERLOGGED,ctx.getWorld().getFluidState(ctx.getBlockPos()).getFluid()==Fluids.WATER);
  return connections(result,ctx.getWorld(),ctx.getBlockPos());
 }
 @Override public boolean canReplace(BlockState state,ItemPlacementContext ctx){if(definition.kind.equals("slab")&&ctx.getStack().isOf(asItem())&&state.get(Properties.SLAB_TYPE)!=SlabType.DOUBLE){boolean upper=ctx.getHitPos().y-ctx.getBlockPos().getY()>.5;return state.get(Properties.SLAB_TYPE)==SlabType.BOTTOM?(ctx.getSide()==Direction.UP||(ctx.getSide().getAxis().isHorizontal()&&upper)):(ctx.getSide()==Direction.DOWN||(ctx.getSide().getAxis().isHorizontal()&&!upper));}return false;}
 private boolean connects(BlockState other,WorldAccess world,BlockPos pos,Direction side){
  if(other.getBlock()instanceof ArchitectureBlock b&&b.definition.kind.equals(definition.kind))return true;
  if(definition.kind.equals("pane")&&(other.getBlock()instanceof PaneBlock||other.isIn(BlockTags.WALLS)))return true;
  if(definition.kind.equals("wall")&&other.isIn(BlockTags.WALLS))return true;
  if(definition.kind.equals("fence")&&(other.isIn(BlockTags.FENCES)||other.getBlock()instanceof FenceGateBlock))return true;
  return other.isSideSolidFullSquare(world,pos,side.getOpposite());
 }
 private BlockState connections(BlockState s,WorldAccess world,BlockPos pos){
  if(definition.kind.equals("stairs"))return s.with(Properties.STAIR_SHAPE,stairShape(s,world,pos));
  if(!Set.of("fence","pane","wall").contains(definition.kind))return s;
  for(Direction d:Direction.Type.HORIZONTAL){Property<?>p=getStateManager().getProperty(d.asString());if(p==null)continue;boolean connected=connects(world.getBlockState(pos.offset(d)),world,pos.offset(d),d);s=BloodborneBlocks.set(s,p,definition.kind.equals("wall")?(connected?"low":"none"):Boolean.toString(connected));}
  if(definition.kind.equals("wall")&&s.contains(Properties.UP)){
   boolean n=s.get(Properties.NORTH_WALL_SHAPE)!=WallShape.NONE,e=s.get(Properties.EAST_WALL_SHAPE)!=WallShape.NONE,ss=s.get(Properties.SOUTH_WALL_SHAPE)!=WallShape.NONE,w=s.get(Properties.WEST_WALL_SHAPE)!=WallShape.NONE;
   s=s.with(Properties.UP,!((n&&ss&&!e&&!w)||(e&&w&&!n&&!ss))||!world.getBlockState(pos.up()).isAir());
  }
  return s;
 }
 private static boolean stair(BlockState s){return s.getBlock()instanceof StairsBlock||(s.getBlock()instanceof ArchitectureBlock b&&b.definition.kind.equals("stairs"));}
 private static boolean differentStair(BlockState s,WorldAccess w,BlockPos p,Direction d){BlockState n=w.getBlockState(p.offset(d));return !stair(n)||n.get(Properties.HORIZONTAL_FACING)!=s.get(Properties.HORIZONTAL_FACING)||n.get(Properties.BLOCK_HALF)!=s.get(Properties.BLOCK_HALF);}
 private static StairShape stairShape(BlockState s,WorldAccess w,BlockPos p){
  Direction f=s.get(Properties.HORIZONTAL_FACING);BlockState front=w.getBlockState(p.offset(f));
  if(stair(front)&&front.get(Properties.BLOCK_HALF)==s.get(Properties.BLOCK_HALF)){
   Direction d=front.get(Properties.HORIZONTAL_FACING);if(d.getAxis()!=f.getAxis()&&differentStair(s,w,p,d.getOpposite()))return d==f.rotateYCounterclockwise()?StairShape.OUTER_LEFT:StairShape.OUTER_RIGHT;
  }
  BlockState back=w.getBlockState(p.offset(f.getOpposite()));
  if(stair(back)&&back.get(Properties.BLOCK_HALF)==s.get(Properties.BLOCK_HALF)){
   Direction d=back.get(Properties.HORIZONTAL_FACING);if(d.getAxis()!=f.getAxis()&&differentStair(s,w,p,d))return d==f.rotateYCounterclockwise()?StairShape.INNER_LEFT:StairShape.INNER_RIGHT;
  }return StairShape.STRAIGHT;
 }
 private void refreshEditedNeighbors(World world,BlockPos pos){
  if(world.isClient||!Set.of("stairs","fence","pane","wall").contains(definition.kind))return;
  for(Direction d:Direction.Type.HORIZONTAL){BlockPos p=pos.offset(d);BlockState s=world.getBlockState(p);if(s.getBlock()instanceof ArchitectureBlock b&&b.definition.kind.equals(definition.kind)){BlockState next=b.connections(s,world,p);if(next!=s)world.setBlockState(p,next,Block.NOTIFY_ALL);}}
 }
 @Override public BlockState getStateForNeighborUpdate(BlockState state,Direction direction,BlockState neighbor,WorldAccess world,BlockPos pos,BlockPos neighborPos){
  if(state.contains(Properties.WATERLOGGED)&&state.get(Properties.WATERLOGGED))world.scheduleFluidTick(pos,Fluids.WATER,Fluids.WATER.getTickRate(world));
  // Existing authored connections remain stable. New placement explicitly computes its own connections.
  if(definition.kind.equals("door")&&state.contains(Properties.DOUBLE_BLOCK_HALF)){
   Direction other=state.get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER?Direction.UP:Direction.DOWN;
   if(direction==other&&!neighbor.isOf(this))return Blocks.AIR.getDefaultState();
  }
  return state;
 }
 boolean canPlaceConventionalDoor(World world,BlockPos pos,BlockState state){
  if(!definition.kind.equals("door")||!state.contains(Properties.DOUBLE_BLOCK_HALF)||state.get(Properties.DOUBLE_BLOCK_HALF)!=DoubleBlockHalf.LOWER)return true;
  BlockPos upper=pos.up();if(!world.isChunkLoaded(upper))return false;BlockState old=world.getBlockState(upper);BlockState upperState=state.with(Properties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER);
  return (old.isAir()||old.isReplaceable())&&GeometryRuntime.canPlace(world,upper,upperState);
 }
 @Override public void onPlaced(World world,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
  if(world.isClient){refreshEditedNeighbors(world,pos);return;}
  if(definition.kind.equals("door")&&state.contains(Properties.DOUBLE_BLOCK_HALF)&&state.get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER){
   BlockState upper=state.with(Properties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER);world.setBlockState(pos.up(),upper,Block.NOTIFY_ALL);GeometryRuntime.rebuild(world,pos.up(),upper);
  }
  GeometryRuntime.rebuild(world,pos,state);refreshEditedNeighbors(world,pos);
 }
 @Override public void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved){
  if(!next.isOf(this)&&!world.isClient&&!GeometryRuntime.isMutating())GeometryRuntime.removeOwnedParts(world,pos,state);
  super.onStateReplaced(state,world,pos,next,moved);if(!next.isOf(this))refreshEditedNeighbors(world,pos);
 }
 @Override public ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit){
  if(!Set.of("door","trapdoor","gate","model_door").contains(definition.kind)||!state.contains(Properties.OPEN))return ActionResult.PASS;
  if(definition.kind.equals("model_door")&&state.contains(Properties.STAIR_SHAPE)&&state.get(Properties.STAIR_SHAPE)!=StairShape.STRAIGHT)return ActionResult.PASS;
  if(!world.isClient){BlockState next=state.cycle(Properties.OPEN);BlockPos other=null;BlockState sibling=null,nextSibling=null;
   if(definition.kind.equals("door")&&state.contains(Properties.DOUBLE_BLOCK_HALF)){other=state.get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER?pos.up():pos.down();sibling=world.getBlockState(other);if(sibling.isOf(this))nextSibling=sibling.with(Properties.OPEN,next.get(Properties.OPEN));}
   if(!GeometryRuntime.allCellsLoaded(world,pos,state)||(nextSibling!=null&&!GeometryRuntime.allCellsLoaded(world,other,sibling))||!GeometryRuntime.canOccupy(world,pos,next,pos)||(nextSibling!=null&&!GeometryRuntime.canOccupy(world,other,nextSibling,other)))return ActionResult.FAIL;
   GeometryRuntime.removeOwnedParts(world,pos);if(nextSibling!=null)GeometryRuntime.removeOwnedParts(world,other);
   world.setBlockState(pos,next,Block.NOTIFY_ALL);if(nextSibling!=null)world.setBlockState(other,nextSibling,Block.NOTIFY_ALL);
   GeometryRuntime.rebuild(world,pos,next);if(nextSibling!=null)GeometryRuntime.rebuild(world,other,nextSibling);
   boolean opening=next.get(Properties.OPEN);net.minecraft.sound.SoundEvent sound;
   if(definition.kind.equals("door")||definition.kind.equals("model_door"))sound=opening?SoundEvents.BLOCK_WOODEN_DOOR_OPEN:SoundEvents.BLOCK_WOODEN_DOOR_CLOSE;
   else if(definition.kind.equals("gate"))sound=opening?SoundEvents.BLOCK_FENCE_GATE_OPEN:SoundEvents.BLOCK_FENCE_GATE_CLOSE;
   else sound=opening?SoundEvents.BLOCK_WOODEN_TRAPDOOR_OPEN:SoundEvents.BLOCK_WOODEN_TRAPDOOR_CLOSE;
   world.playSound(null,pos,sound,SoundCategory.BLOCKS,.7F,1F);
  }return ActionResult.success(world.isClient);
 }
 @Override public ItemStack getPickStack(BlockView world,BlockPos pos,BlockState state){ItemStack stack=new ItemStack(this);if(!state.getEntries().isEmpty()){NbtCompound props=new NbtCompound();state.getEntries().forEach((p,v)->props.putString(p.getName(),BloodborneBlocks.value(p,v)));stack.getOrCreateNbt().put("BlockStateTag",props);}return stack;}
 @Override public FluidState getFluidState(BlockState state){return state.contains(Properties.WATERLOGGED)&&state.get(Properties.WATERLOGGED)?Fluids.WATER.getStill(false):Fluids.EMPTY.getDefaultState();}
 @Override public boolean canFillWithFluid(BlockView world,BlockPos pos,BlockState state,Fluid fluid){return state.contains(Properties.WATERLOGGED)&&Waterloggable.super.canFillWithFluid(world,pos,state,fluid);}
 @Override public boolean tryFillWithFluid(WorldAccess world,BlockPos pos,BlockState state,FluidState fluid){return state.contains(Properties.WATERLOGGED)&&Waterloggable.super.tryFillWithFluid(world,pos,state,fluid);}
 @Override public ItemStack tryDrainFluid(WorldAccess world,BlockPos pos,BlockState state){return state.contains(Properties.WATERLOGGED)?Waterloggable.super.tryDrainFluid(world,pos,state):ItemStack.EMPTY;}
}
