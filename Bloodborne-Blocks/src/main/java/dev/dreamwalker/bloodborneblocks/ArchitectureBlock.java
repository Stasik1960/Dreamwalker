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
import java.util.Objects;

/** Decorative state machine: no random ticks, block entities, redstone machines or inventories. */
public final class ArchitectureBlock extends Block implements Waterloggable {
 private static final ThreadLocal<BloodborneBlocks.Definition> CONSTRUCTING=new ThreadLocal<>();
 public final BloodborneBlocks.Definition definition;
 private final Map<BlockState,BlockState> originals=new IdentityHashMap<>();
 public static ArchitectureBlock create(BloodborneBlocks.Definition d){CONSTRUCTING.set(d);try{return new ArchitectureBlock(d);}finally{CONSTRUCTING.remove();}}
 private static Settings settings(BloodborneBlocks.Definition d){
  Block material=semanticMaterial(d);BlockState materialState=material.getDefaultState();
  Settings s=Settings.create().strength(semanticHardness(d),semanticResistance(d)).sounds(material.getSoundGroup(materialState)).mapColor(materialState.getMapColor(EmptyBlockView.INSTANCE,BlockPos.ORIGIN)).slipperiness(d.slipperiness).velocityMultiplier(d.velocity).jumpVelocityMultiplier(d.jump).luminance(state->d.states.get(BloodborneBlocks.key(state))[2]).pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK);
  if(!d.full_cube||d.custom_geometry)s.nonOpaque().solidBlock((state,world,pos)->false).suffocates((state,world,pos)->false).blockVision((state,world,pos)->false);
  if(!d.offset.equals("none"))s.offset(d.offset.equals("xyz")?OffsetType.XYZ:OffsetType.XZ).dynamicBounds();
  return s;
 }
 private static Block semanticMaterial(BloodborneBlocks.Definition d){
  if(!d.modular||d.semantic==null)return d.sourceBlock;
  return switch(d.semantic){
   case "wood","container","bench","ladder"->Blocks.OAK_PLANKS;
   case "window"->Blocks.GLASS;
   case "tree","bush","plant","floor_decoration"->Blocks.OAK_LEAVES;
   default->Blocks.STONE;
  };
 }
 private static float semanticHardness(BloodborneBlocks.Definition d){return !d.modular||d.semantic==null?d.hardness:switch(d.semantic){case "window"->.3F;case "tree","bush","plant","floor_decoration"->.2F;case "ladder"->.4F;case "wood","container","bench"->2F;default->d.hardness;};}
 private static float semanticResistance(BloodborneBlocks.Definition d){return !d.modular||d.semantic==null?d.resistance:switch(d.semantic){case "window"->.3F;case "tree","bush","plant","floor_decoration"->.2F;case "ladder"->.4F;case "wood","container","bench"->3F;default->d.resistance;};}
 private ArchitectureBlock(BloodborneBlocks.Definition d){
  super(settings(d));definition=d;BlockState defaultState=getStateManager().getDefaultState();
  for(var e:d.defaultProperties.entrySet())defaultState=BloodborneBlocks.set(defaultState,d.propertyObjects.get(e.getKey()),e.getValue());setDefaultState(defaultState);
  for(BlockState state:getStateManager().getStates()){
   if(!d.states.containsKey(BloodborneBlocks.key(state)))throw new IllegalStateException("Unmapped state "+state);
   BlockState original=d.sourceBlock.getDefaultState();for(var e:state.getEntries().entrySet())if(original.contains(e.getKey()))original=BloodborneBlocks.set(original,e.getKey(),BloodborneBlocks.value(e.getKey(),e.getValue()));originals.put(state,original);
  }
  GeometryRuntime.bind(this);
 }
 protected void appendProperties(StateManager.Builder<Block,BlockState> builder){for(Property<?>p:CONSTRUCTING.get().propertyObjects.values())builder.add(p);}
 public BlockState original(BlockState state){return originals.getOrDefault(state,definition.sourceBlock.getDefaultState());}
 /** Keep selection bounds inside the physical shape. Authored render geometry can overhang
  * a block for decorative silhouettes; using it as an outline produced ghost lines in-world. */
 @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return GeometryRuntime.rootShape(state,true);}
 @Override public BlockRenderType getRenderType(BlockState state){return BlockRenderType.MODEL;}
 @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return GeometryRuntime.rootShape(state,false);}
 @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos){return definition.full_cube&&!definition.custom_geometry?VoxelShapes.fullCube():VoxelShapes.empty();}
 @Override public float getAmbientOcclusionLightLevel(BlockState state,BlockView world,BlockPos pos){return definition.full_cube?.2F:1F;}
 @Override public boolean isTransparent(BlockState state,BlockView world,BlockPos pos){return !definition.full_cube;}
 @Override public long getRenderingSeed(BlockState state,BlockPos pos){return definition.sourceBlock.getRenderingSeed(original(state),pos);}
 @SuppressWarnings({"rawtypes","unchecked"}) private BlockState preserveCustom(BlockState mapped,BlockState state){
  BlockState sourceDefault=definition.sourceBlock.getDefaultState();for(var entry:state.getEntries().entrySet())if(!sourceDefault.contains(entry.getKey())&&mapped.contains(entry.getKey()))mapped=mapped.with((Property)entry.getKey(),(Comparable)entry.getValue());return mapped;
 }
 @Override public BlockState rotate(BlockState state,BlockRotation rotation){BlockState mapped=preserveCustom(getStateWithProperties(definition.sourceBlock.rotate(original(state),rotation)),state);if(definition.logical)return rotateLogical(mapped,state,rotation);if(definition.extra_facing)return mapped.with(Properties.HORIZONTAL_FACING,rotation.rotate(state.get(Properties.HORIZONTAL_FACING)));return mapped;}
 @Override public BlockState mirror(BlockState state,BlockMirror mirror){if(definition.logical&&GeometryRuntime.rotateOnlyMirror(state))return mirrorLogical(state,state,mirror);BlockState mapped=preserveCustom(getStateWithProperties(definition.sourceBlock.mirror(original(state),mirror)),state);if(definition.logical)return mirrorLogical(mapped,state,mirror);if(definition.extra_facing)return mapped.with(Properties.HORIZONTAL_FACING,mirror.apply(state.get(Properties.HORIZONTAL_FACING)));return mapped;}
 private BlockState rotateLogical(BlockState mapped,BlockState original,BlockRotation rotation){
  if(mapped.contains(Properties.HORIZONTAL_FACING))mapped=mapped.with(Properties.HORIZONTAL_FACING,rotation.rotate(original.get(Properties.HORIZONTAL_FACING)));
  for(Direction direction:Direction.Type.HORIZONTAL){Property<?> from=getStateManager().getProperty(direction.asString()),to=getStateManager().getProperty(rotation.rotate(direction).asString());if(from!=null&&to!=null)mapped=copy(mapped,original,from,to);}return mapped;
 }
 private BlockState mirrorLogical(BlockState mapped,BlockState original,BlockMirror mirror){
  if(mapped.contains(Properties.HORIZONTAL_FACING))mapped=mapped.with(Properties.HORIZONTAL_FACING,mirror.apply(original.get(Properties.HORIZONTAL_FACING)));
  for(Direction direction:Direction.Type.HORIZONTAL){Property<?> from=getStateManager().getProperty(direction.asString()),to=getStateManager().getProperty(mirror.apply(direction).asString());if(from!=null&&to!=null)mapped=copy(mapped,original,from,to);}return mapped;
 }
 @SuppressWarnings({"rawtypes","unchecked"}) private static BlockState copy(BlockState target,BlockState source,Property from,Property to){return target.with(to,(Comparable)source.get(from));}
 @Override public BlockState getPlacementState(ItemPlacementContext ctx){
  BlockState old=ctx.getWorld().getBlockState(ctx.getBlockPos());
  if(definition.kind.equals("slab")&&old.isOf(this)&&old.get(Properties.SLAB_TYPE)!=SlabType.DOUBLE)return old.with(Properties.SLAB_TYPE,SlabType.DOUBLE).with(Properties.WATERLOGGED,false);
  boolean authored=definition.logical||definition.kind.equals("generic")||definition.kind.equals("model_door");
  BlockState source=authored?null:definition.sourceBlock.getPlacementState(ctx);BlockState result=source==null?getDefaultState():getStateWithProperties(source);
  if(authored){
   Direction placementFacing=definition.logical&&Set.of("door","gate").contains(definition.behavior)?ctx.getHorizontalPlayerFacing():ctx.getHorizontalPlayerFacing().getOpposite();
   Property<?> facing=getStateManager().getProperty("facing");if(facing!=null)result=BloodborneBlocks.set(result,facing,placementFacing.asString());
   Property<?> axis=getStateManager().getProperty("axis");if(axis!=null)result=BloodborneBlocks.set(result,axis,ctx.getSide().getAxis().asString());
  }
  if(definition.kind.equals("model_door")){
   if(result.contains(Properties.BLOCK_HALF))result=result.with(Properties.BLOCK_HALF,BlockHalf.BOTTOM);
   if(result.contains(Properties.STAIR_SHAPE))result=result.with(Properties.STAIR_SHAPE,StairShape.STRAIGHT);
   if(result.contains(Properties.OPEN))result=result.with(Properties.OPEN,false);
  }
  if(result.contains(BloodborneBlocks.ASSEMBLED))result=result.with(BloodborneBlocks.ASSEMBLED,true);
  if(definition.extra_facing&&!definition.logical)result=result.with(Properties.HORIZONTAL_FACING,ctx.getHorizontalPlayerFacing().getOpposite());
  if(definition.logical){result=logicalMountPlacement(result,ctx.getSide(),ctx.getHorizontalPlayerFacing());result=GeometryRuntime.applyPlacementPolicy(result,ctx.getSide());}
  if(result.contains(Properties.WATERLOGGED))result=result.with(Properties.WATERLOGGED,ctx.getWorld().getFluidState(ctx.getBlockPos()).getFluid()==Fluids.WATER);
  if(definition.seat_anchors!=null&&getStateManager().getProperty("diagonal") instanceof BooleanProperty diagonal){
   int eighth=Math.floorMod(Math.round(ctx.getPlayerYaw()/45F),8);
   Direction northFrame=Direction.fromHorizontal((eighth/2+2)%4);
   result=result.with(Properties.HORIZONTAL_FACING,northFrame).with(diagonal,(eighth&1)!=0);
  }
  return BloodborneBlocks.applyPlacementProperties(definition,connections(result,ctx.getWorld(),ctx.getBlockPos()));
 }
 /** Mount states reuse the same authored model; horizontal mounts point out from the clicked face. */
 static BlockState logicalMountPlacement(BlockState state,Direction side,Direction playerFacing){
  if(!state.contains(Properties.WALL_MOUNT_LOCATION))return state;
  WallMountLocation face=side==Direction.UP?WallMountLocation.FLOOR:side==Direction.DOWN?WallMountLocation.CEILING:WallMountLocation.WALL;
  return state.with(Properties.WALL_MOUNT_LOCATION,face).with(Properties.HORIZONTAL_FACING,side.getAxis().isHorizontal()?side:playerFacing.getOpposite());
 }
 @Override public boolean canReplace(BlockState state,ItemPlacementContext ctx){if(definition.kind.equals("slab")&&ctx.getStack().isOf(asItem())&&state.get(Properties.SLAB_TYPE)!=SlabType.DOUBLE){boolean upper=ctx.getHitPos().y-ctx.getBlockPos().getY()>.5;return state.get(Properties.SLAB_TYPE)==SlabType.BOTTOM?(ctx.getSide()==Direction.UP||(ctx.getSide().getAxis().isHorizontal()&&upper)):(ctx.getSide()==Direction.DOWN||(ctx.getSide().getAxis().isHorizontal()&&!upper));}return false;}
 private boolean connects(BlockState other,WorldAccess world,BlockPos pos,Direction side){
  if(other.getBlock()instanceof ArchitectureBlock b&&b.definition.kind.equals(definition.kind))return true;
  if(definition.kind.equals("pane")&&(other.getBlock()instanceof PaneBlock||other.isIn(BlockTags.WALLS)))return true;
  if(definition.kind.equals("wall")&&other.isIn(BlockTags.WALLS))return true;
  if(definition.kind.equals("fence")&&(other.isIn(BlockTags.FENCES)||other.getBlock()instanceof FenceGateBlock))return true;
  return other.isSideSolidFullSquare(world,pos,side.getOpposite());
 }
 private boolean logicalConnects(BlockState other,WorldAccess world,BlockPos pos,Direction side){
  if(other.getBlock() instanceof ArchitectureBlock block&&block.definition.logical&&Objects.equals(block.definition.connection_family,definition.connection_family))return true;
  // Pavement follows adjacent pavement, not a wall placed beside its border.
  if("floor".equals(definition.semantic))return false;
  return other.isSideSolidFullSquare(world,pos,side.getOpposite());
 }
 private BlockState connections(BlockState s,WorldAccess world,BlockPos pos){
  if(definition.logical&&"ladder".equals(definition.behavior))return s;
  if(definition.logical&&"connected".equals(definition.behavior)){
   if(world instanceof World loaded&&!logicalNeighborsLoaded(loaded,pos))return s;
   for(Direction direction:Direction.values()){
    Property<?> property=getStateManager().getProperty(direction.asString());
    if(property!=null) s=BloodborneBlocks.set(s,property,Boolean.toString(logicalConnects(world.getBlockState(pos.offset(direction)),world,pos.offset(direction),direction)));
   }
   return s;
  }
  if(definition.kind.equals("stairs"))return s.with(Properties.STAIR_SHAPE,stairShape(s,world,pos));
  if(!Set.of("fence","pane","wall").contains(definition.kind))return s;
  for(Direction d:Direction.Type.HORIZONTAL){Property<?>p=getStateManager().getProperty(d.asString());if(p==null)continue;boolean connected=connects(world.getBlockState(pos.offset(d)),world,pos.offset(d),d);s=BloodborneBlocks.set(s,p,definition.kind.equals("wall")?(connected?"low":"none"):Boolean.toString(connected));}
  if(definition.kind.equals("wall")&&s.contains(Properties.UP)){
   boolean n=s.get(Properties.NORTH_WALL_SHAPE)!=WallShape.NONE,e=s.get(Properties.EAST_WALL_SHAPE)!=WallShape.NONE,ss=s.get(Properties.SOUTH_WALL_SHAPE)!=WallShape.NONE,w=s.get(Properties.WEST_WALL_SHAPE)!=WallShape.NONE;
   s=s.with(Properties.UP,!((n&&ss&&!e&&!w)||(e&&w&&!n&&!ss))||!world.getBlockState(pos.up()).isAir());
  }
  return s;
 }
 private static boolean logicalNeighborsLoaded(World world,BlockPos pos){for(Direction direction:Direction.values())if(!world.isChunkLoaded(pos.offset(direction)))return false;return true;}
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
  if(world.isClient||(!definition.logical&&!Set.of("stairs","fence","pane","wall").contains(definition.kind)))return;
  for(Direction d:Direction.values()){
   BlockPos p=pos.offset(d);if(definition.logical&&!world.isChunkLoaded(p))continue;BlockState s=world.getBlockState(p);
   if(s.getBlock() instanceof ArchitectureBlock b&&((definition.logical&&b.definition.logical&&"connected".equals(b.definition.behavior)&&Objects.equals(b.definition.connection_family,definition.connection_family))||(!definition.logical&&b.definition.kind.equals(definition.kind)))){
    if(b.definition.logical&&!logicalNeighborsLoaded(world,p))continue;
    BlockState next=b.connections(s,world,p);
    if(next!=s&&(!b.definition.logical||(GeometryRuntime.allCellsLoaded(world,p,next)&&GeometryRuntime.canOccupy(world,p,next,p))))world.setBlockState(p,next,Block.NOTIFY_ALL);
   }
  }
 }
 @Override public BlockState getStateForNeighborUpdate(BlockState state,Direction direction,BlockState neighbor,WorldAccess world,BlockPos pos,BlockPos neighborPos){
  if(state.contains(Properties.WATERLOGGED)&&state.get(Properties.WATERLOGGED))world.scheduleFluidTick(pos,Fluids.WATER,Fluids.WATER.getTickRate(world));
  // Existing authored connections remain stable. Logical connections are an explicit opt-in path.
  if(definition.logical&&"connected".equals(definition.behavior)&&world instanceof World loaded&&logicalNeighborsLoaded(loaded,pos)&&GeometryRuntime.allCellsLoaded(loaded,pos,state)){
   BlockState next=connections(state,world,pos);
   if(next!=state&&GeometryRuntime.allCellsLoaded(loaded,pos,next)&&GeometryRuntime.canOccupy(loaded,pos,next,pos))return next;
  }
  if(definition.kind.equals("door")&&state.contains(Properties.DOUBLE_BLOCK_HALF)){
   Direction other=state.get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER?Direction.UP:Direction.DOWN;
   if(direction==other&&!neighbor.isOf(this))return Blocks.AIR.getDefaultState();
  }
  return state;
 }
 boolean canPlaceConventionalDoor(World world,BlockPos pos,BlockState state){
  if(definition.logical||!definition.kind.equals("door")||!state.contains(Properties.DOUBLE_BLOCK_HALF)||state.get(Properties.DOUBLE_BLOCK_HALF)!=DoubleBlockHalf.LOWER)return true;
  BlockPos upper=pos.up();if(!world.isChunkLoaded(upper))return false;BlockState old=world.getBlockState(upper);BlockState upperState=state.with(Properties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER);
  return (old.isAir()||old.isReplaceable())&&GeometryRuntime.canPlace(world,upper,upperState);
 }
 @Override public void onPlaced(World world,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
  if(!GeometryRuntime.usesHelpers(this))return;
  if(world.isClient){refreshEditedNeighbors(world,pos);return;}
  if(definition.kind.equals("door")&&state.contains(Properties.DOUBLE_BLOCK_HALF)&&state.get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER){
   BlockState upper=state.with(Properties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER);world.setBlockState(pos.up(),upper,Block.NOTIFY_ALL);GeometryRuntime.rebuild(world,pos.up(),upper);
  }
  GeometryRuntime.rebuild(world,pos,state);refreshEditedNeighbors(world,pos);
 }
 @Override public void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved){
  if(!GeometryRuntime.usesHelpers(this)){super.onStateReplaced(state,world,pos,next,moved);return;}
  if(FunctionalFurniture.isBench(state)&&state!=next)FunctionalFurniture.removeSeats(world,pos);
  if(!next.isOf(this)&&!world.isClient&&!GeometryRuntime.isMutating())GeometryRuntime.removeOwnedParts(world,pos,state);
  if(next.isOf(this)&&GeometryRuntime.rebuildsHelperTransitions(this)&&!world.isClient&&!GeometryRuntime.isMutating()){
   // Direct /setblock and editor state changes do not use the interaction preflight.
   // Keep the old object intact unless the complete replacement can own every cell.
   if(!GeometryRuntime.canTransition(world,pos,state,next)){GeometryRuntime.restoreRoot(world,pos,state);return;}
   GeometryRuntime.removeOwnedParts(world,pos,state);
   if(!GeometryRuntime.rebuild(world,pos,next))GeometryRuntime.restoreRoot(world,pos,state);
  }
  super.onStateReplaced(state,world,pos,next,moved);if(!next.isOf(this))refreshEditedNeighbors(world,pos);
 }
 static boolean canReplaceLogicalState(boolean oldCellsLoaded,boolean nextCellsLoaded,boolean nextCanOccupy){return oldCellsLoaded&&nextCellsLoaded&&nextCanOccupy;}
 @Override public ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit){
  ActionResult attachment=LogicalAttachments.use(state,world,pos,player,hand);if(attachment!=null)return attachment;
  if(FunctionalFurniture.isBench(state))return FunctionalFurniture.sit(world,pos,state,player,hit);
  if(definition.logical&&"lantern".equals(definition.behavior)){
   Property<?> property=getStateManager().getProperty("lit");
   if(!(property instanceof BooleanProperty lit))return ActionResult.PASS;
   if(!world.isClient){
    BlockState next=state.cycle(lit);
    if(!GeometryRuntime.canTransition(world,pos,state,next))return ActionResult.FAIL;
    GeometryRuntime.removeOwnedParts(world,pos,state);world.setBlockState(pos,next,Block.NOTIFY_ALL);
    if(!GeometryRuntime.rebuild(world,pos,next)){GeometryRuntime.restoreRoot(world,pos,state);return ActionResult.FAIL;}
   }
   return ActionResult.success(world.isClient);
  }
  boolean interactive=definition.logical?Set.of("door","gate","shutter").contains(definition.behavior):Set.of("door","trapdoor","gate","model_door").contains(definition.kind);
  if(!interactive||!state.contains(Properties.OPEN))return ActionResult.PASS;
  if(definition.kind.equals("model_door")&&state.contains(Properties.STAIR_SHAPE)&&state.get(Properties.STAIR_SHAPE)!=StairShape.STRAIGHT)return ActionResult.PASS;
  if(!world.isClient){BlockState next=state.cycle(Properties.OPEN);BlockPos other=null;BlockState sibling=null,nextSibling=null;
   if(!definition.logical&&definition.kind.equals("door")&&state.contains(Properties.DOUBLE_BLOCK_HALF)){other=state.get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER?pos.up():pos.down();sibling=world.getBlockState(other);if(sibling.isOf(this))nextSibling=sibling.with(Properties.OPEN,next.get(Properties.OPEN));}
   if(!GeometryRuntime.canTransition(world,pos,state,next)||(nextSibling!=null&&!GeometryRuntime.canTransition(world,other,sibling,nextSibling)))return ActionResult.FAIL;
   GeometryRuntime.removeOwnedParts(world,pos);if(nextSibling!=null)GeometryRuntime.removeOwnedParts(world,other);
   world.setBlockState(pos,next,Block.NOTIFY_ALL);if(nextSibling!=null)world.setBlockState(other,nextSibling,Block.NOTIFY_ALL);
   GeometryRuntime.rebuild(world,pos,next);if(nextSibling!=null)GeometryRuntime.rebuild(world,other,nextSibling);
   boolean opening=next.get(Properties.OPEN);net.minecraft.sound.SoundEvent sound;
   if((definition.logical&&definition.behavior.equals("door"))||definition.kind.equals("door")||definition.kind.equals("model_door"))sound=opening?SoundEvents.BLOCK_WOODEN_DOOR_OPEN:SoundEvents.BLOCK_WOODEN_DOOR_CLOSE;
   else if((definition.logical&&definition.behavior.equals("gate"))||definition.kind.equals("gate"))sound=opening?SoundEvents.BLOCK_FENCE_GATE_OPEN:SoundEvents.BLOCK_FENCE_GATE_CLOSE;
   else sound=opening?SoundEvents.BLOCK_WOODEN_TRAPDOOR_OPEN:SoundEvents.BLOCK_WOODEN_TRAPDOOR_CLOSE;
   world.playSound(null,pos,sound,SoundCategory.BLOCKS,.7F,1F);
  }return ActionResult.success(world.isClient);
 }
 @Override public ItemStack getPickStack(BlockView world,BlockPos pos,BlockState state){
  return logicalPick(stackFor(state));
 }
 @Override public java.util.List<ItemStack> getDroppedStacks(BlockState state,net.minecraft.loot.context.LootContextParameterSet.Builder builder){
  java.util.List<ItemStack> drops=new java.util.ArrayList<>(super.getDroppedStacks(state,builder));
  if(definition.city_compat&&definition.models!=null)for(ItemStack drop:drops)if(drop.isOf(asItem()))copyVariant(state,drop);
  Property<?> rootAnchor=getStateManager().getProperty("root_anchor");
  if(rootAnchor!=null)for(ItemStack drop:drops)if(drop.isOf(asItem()))drop.getOrCreateSubNbt("BlockStateTag").putString("root_anchor",BloodborneBlocks.value((Property)rootAnchor,(Comparable)state.get((Property)rootAnchor)));
  ItemStack mounted=getStateManager().getProperty("hand_lantern")==null?ItemStack.EMPTY:LogicalAttachments.attachedItem(state);if(!mounted.isEmpty())drops.add(mounted);
  return drops;
 }
 private static void copyVariant(BlockState state,ItemStack stack){
  Property<?> variant=state.getBlock().getStateManager().getProperty("variant");if(variant!=null)stack.getOrCreateSubNbt("BlockStateTag").putString("variant",BloodborneBlocks.value((Property)variant,(Comparable)state.get((Property)variant)));
 }
 private static ItemStack stackFor(BlockState state){
  ItemStack stack=new ItemStack(state.getBlock());NbtCompound props=new NbtCompound();BlockState defaults=state.getBlock().getDefaultState();
  state.getEntries().forEach((p,v)->{if(!v.equals(defaults.get(p)))props.putString(p.getName(),BloodborneBlocks.value(p,v));});
  if(!props.isEmpty())stack.getOrCreateNbt().put("BlockStateTag",props);return stack;
 }
 private static ItemStack logicalPick(ItemStack stack){
  if(!(stack.getItem() instanceof ArchitectureBlockItem item)||!((ArchitectureBlock)item.getBlock()).definition.logical)return stack;
  NbtCompound properties=stack.getSubNbt("BlockStateTag");if(properties==null)return stack;
  for(String name:Set.of("facing","face","axis","open","lit","waterlogged","north","east","south","west","up","down","diagonal"))properties.remove(name);
  Map<String,String> placementProperties=((ArchitectureBlock)item.getBlock()).definition.placement_properties;
  if(placementProperties!=null)placementProperties.keySet().forEach(properties::remove);
  if(properties.isEmpty())stack.removeSubNbt("BlockStateTag");return stack;
 }
 @Override public FluidState getFluidState(BlockState state){return state.contains(Properties.WATERLOGGED)&&state.get(Properties.WATERLOGGED)?Fluids.WATER.getStill(false):Fluids.EMPTY.getDefaultState();}
 @Override public boolean canFillWithFluid(BlockView world,BlockPos pos,BlockState state,Fluid fluid){return state.contains(Properties.WATERLOGGED)&&Waterloggable.super.canFillWithFluid(world,pos,state,fluid);}
 @Override public boolean tryFillWithFluid(WorldAccess world,BlockPos pos,BlockState state,FluidState fluid){return state.contains(Properties.WATERLOGGED)&&Waterloggable.super.tryFillWithFluid(world,pos,state,fluid);}
 @Override public ItemStack tryDrainFluid(WorldAccess world,BlockPos pos,BlockState state){return state.contains(Properties.WATERLOGGED)?Waterloggable.super.tryDrainFluid(world,pos,state):ItemStack.EMPTY;}
}
