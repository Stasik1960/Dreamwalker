package dev.dreamwalker.bloodborneblocks;

import com.google.gson.Gson;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server-safe, immutable runtime view of generated per-cell geometry. */
final class GeometryRuntime {
 private static final Gson GSON=new Gson();
 private static final Map<String,GeometryBlock> BLOCKS=new HashMap<>();
 private static final Map<BlockState,GeometryState> STATES=new IdentityHashMap<>();
 private static final ThreadLocal<Boolean> MUTATING=ThreadLocal.withInitial(()->false);

 static final class FileData {Map<String,GeometryState> profiles;Map<String,GeometryBlock> blocks;}
 static final class GeometryBlock {Map<String,GeometryState> states;}
 static final class GeometryState {
  Map<String,GeometryCell> cells;
  int[] anchor;
  double[] render_offset={0,0,0};
  String ref;
  transient Map<BlockPos,GeometryCell> parsedCells;
 }
 static final class GeometryCell {
  List<double[]> collision=List.of();
  List<double[]> outline=List.of();
  transient VoxelShape collisionShape;
  transient VoxelShape outlineShape;
 }
 record RepairResult(int roots,int repaired,int conflicts,int orphans) {}

 private GeometryRuntime() {}

 static void loadAndValidate(BloodborneBlocks.Data definitions) {
  FileData file;
  try(InputStream stream=GeometryRuntime.class.getResourceAsStream("/bloodborne_blocks/geometry.json")){
   if(stream==null)throw new IOException("Missing generated geometry.json");
   file=GSON.fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),FileData.class);
  }catch(IOException|RuntimeException e){throw new IllegalStateException("Cannot load Bloodborne cell geometry",e);}
  if(file==null||file.blocks==null)throw new IllegalStateException("Invalid geometry.json: missing blocks");
  BLOCKS.clear();STATES.clear();BLOCKS.putAll(file.blocks);
  Set<GeometryState> prepared=Collections.newSetFromMap(new IdentityHashMap<>());
  for(BloodborneBlocks.Definition definition:definitions.blocks){
   GeometryBlock block=BLOCKS.get(definition.id);
   if(block==null||block.states==null)throw new IllegalStateException("Missing geometry block "+definition.id);
   for(String stateKey:definition.states.keySet()){
    GeometryState state=block.states.get(stateKey);
    if(state==null)throw new IllegalStateException("Missing geometry state "+definition.id+"["+stateKey+"]");
    if(state.ref!=null){state=file.profiles==null?null:file.profiles.get(state.ref);if(state==null)throw new IllegalStateException("Missing geometry profile "+blockId(definition.id,stateKey,block.states.get(stateKey).ref));block.states.put(stateKey,state);}
    if(prepared.add(state))prepare(definition.id,stateKey,state);
   }
  }
 }

 private static String blockId(String id,String state,String ref){return ref+" referenced by "+id+"["+state+"]";}

 private static void prepare(String blockId,String stateKey,GeometryState state){
  if(state.anchor==null||state.anchor.length!=3)throw new IllegalStateException("Invalid anchor "+blockId+"["+stateKey+"]");
  if(state.render_offset==null)state.render_offset=new double[]{0,0,0};
  if(state.render_offset.length!=3||!Double.isFinite(state.render_offset[0])||!Double.isFinite(state.render_offset[1])||!Double.isFinite(state.render_offset[2]))throw new IllegalStateException("Invalid render_offset "+blockId+"["+stateKey+"]");
  state.parsedCells=new LinkedHashMap<>();
  if(state.cells==null)state.cells=Map.of();
  for(var entry:state.cells.entrySet()){
   String[] parts=entry.getKey().split(",",-1);
   if(parts.length!=3)throw new IllegalStateException("Invalid cell key "+blockId+"["+stateKey+"]: "+entry.getKey());
   BlockPos offset;
   try{offset=new BlockPos(Integer.parseInt(parts[0].trim()),Integer.parseInt(parts[1].trim()),Integer.parseInt(parts[2].trim()));}
   catch(NumberFormatException e){throw new IllegalStateException("Invalid cell key "+entry.getKey(),e);}
   GeometryCell cell=entry.getValue();if(cell==null)throw new IllegalStateException("Null cell "+entry.getKey());
   validateBoxes(blockId,stateKey,entry.getKey(),"collision",cell.collision);
   validateBoxes(blockId,stateKey,entry.getKey(),"outline",cell.outline);
   cell.collisionShape=shape(cell.collision);cell.outlineShape=sameBoxes(cell.collision,cell.outline)?cell.collisionShape:shape(cell.outline);
   state.parsedCells.put(offset.toImmutable(),cell);
  }
 }

 private static void validateBoxes(String id,String key,String cell,String type,List<double[]> boxes){
  if(boxes==null)throw new IllegalStateException("Null "+type+" boxes "+id+"["+key+"] cell "+cell);
  for(double[] box:boxes){
   if(box==null||box.length!=6)throw new IllegalStateException("Invalid "+type+" box "+id+"["+key+"] cell "+cell);
   for(double v:box)if(!Double.isFinite(v)||v<0||v>1)throw new IllegalStateException("Out-of-cell "+type+" box "+id+"["+key+"] cell "+cell);
   if(box[0]>=box[3]||box[1]>=box[4]||box[2]>=box[5])throw new IllegalStateException("Empty "+type+" box "+id+"["+key+"] cell "+cell);
  }
 }

 private static VoxelShape shape(List<double[]> boxes){
  return boxes.isEmpty()?VoxelShapes.empty():GeneratedShape.of(boxes);
 }
 private static boolean sameBoxes(List<double[]> first,List<double[]> second){
  if(first.size()!=second.size())return false;for(int i=0;i<first.size();i++)if(!Arrays.equals(first.get(i),second.get(i)))return false;return true;
 }

 static GeometryState state(BlockState state){
  GeometryState cached=STATES.get(state);if(cached!=null)return cached;
  if(!(state.getBlock() instanceof ArchitectureBlock block))return null;
  GeometryBlock geometry=BLOCKS.get(block.definition.id);
  return geometry==null?null:geometry.states.get(BloodborneBlocks.key(state));
 }

 static void bind(ArchitectureBlock block){
  GeometryBlock geometry=BLOCKS.get(block.definition.id);
  for(BlockState state:block.getStateManager().getStates())STATES.put(state,geometry.states.get(BloodborneBlocks.key(state)));
 }

 static double[] renderOffset(String blockId,String stateKey){
  GeometryBlock block=BLOCKS.get(blockId);if(block==null||block.states==null)return null;GeometryState state=block.states.get(stateKey);return state==null?null:state.render_offset;
 }

 static BlockPos anchor(BlockState state){
  GeometryState geometry=required(state);return new BlockPos(geometry.anchor[0],geometry.anchor[1],geometry.anchor[2]);
 }
 static BlockPos anchor(BlockState state,Direction side){
  GeometryState geometry=required(state);int x=geometry.anchor[0],y=geometry.anchor[1],z=geometry.anchor[2];
  if(!geometry.parsedCells.isEmpty()){
   if(side==Direction.WEST)x=geometry.parsedCells.keySet().stream().mapToInt(BlockPos::getX).max().orElse(x);
   else if(side==Direction.DOWN)y=geometry.parsedCells.keySet().stream().mapToInt(BlockPos::getY).max().orElse(y);
   else if(side==Direction.NORTH)z=geometry.parsedCells.keySet().stream().mapToInt(BlockPos::getZ).max().orElse(z);
  }
  return new BlockPos(x,y,z);
 }

 static VoxelShape rootShape(BlockState state,boolean outline){return cellShape(state,BlockPos.ORIGIN,outline);}

 static VoxelShape cellShape(BlockState rootState,BlockPos offset,boolean outline){
  if(rootState.getBlock() instanceof ArchitectureBlock block&&PaletteAliases.removed(block.definition.id))return VoxelShapes.empty();
  GeometryState geometry=state(rootState);if(geometry==null)return VoxelShapes.empty();
  GeometryCell cell=geometry.parsedCells.get(offset);if(cell==null)return VoxelShapes.empty();
  return outline?cell.outlineShape:cell.collisionShape;
 }

 private static GeometryState required(BlockState state){
  GeometryState geometry=state(state);if(geometry==null)throw new IllegalStateException("Missing runtime geometry for "+state);return geometry;
 }

 static boolean canPlace(World world,BlockPos root,BlockState state){
  return canOccupy(world,root,state,null);
 }
 static boolean canOccupy(World world,BlockPos root,BlockState state,BlockPos ownedRoot){
  if(conflict(world,root,state,ownedRoot)!=null)return false;
  GeometryState geometry=required(state);
  Set<BlockPos> offsets=new HashSet<>(geometry.parsedCells.keySet());offsets.add(BlockPos.ORIGIN);
  for(BlockPos offset:offsets){
   BlockPos target=root.add(offset);
   if(!world.isChunkLoaded(target)||!world.isInBuildLimit(target)||!world.getWorldBorder().contains(target))return false;
   VoxelShape collision=cellShape(state,offset,false);
   if(!collision.isEmpty()&&!world.doesNotIntersectEntities(null,collision.offset(target.getX(),target.getY(),target.getZ())))return false;
  }
  return true;
 }
 static boolean allCellsLoaded(World world,BlockPos root,BlockState state){
  for(BlockPos offset:required(state).parsedCells.keySet())if(!world.isChunkLoaded(root.add(offset)))return false;return world.isChunkLoaded(root);
 }

 static BlockPos conflict(World world,BlockPos root,BlockState state,BlockPos ownedRoot){
  GeometryState geometry=required(state);
  for(BlockPos offset:geometry.parsedCells.keySet()){
   if(offset.equals(BlockPos.ORIGIN)||reservedDoorSibling(state,offset))continue;
   BlockPos target=root.add(offset);
   if(target.equals(ownedRoot))continue;
   if(!world.isChunkLoaded(target)||!world.isInBuildLimit(target)||!world.getWorldBorder().contains(target))return target;
   BlockState there=world.getBlockState(target);
   if(there.isAir()||there.isReplaceable())continue;
   if(there.isOf(BloodborneBlocks.PART_BLOCK)){
    ArchitecturePartBlockEntity part=part(world,target);
    if(part!=null&&ownedRoot!=null&&part.rootPos().equals(ownedRoot))continue;
   }
   return target;
  }
  return null;
 }

 private static boolean reservedDoorSibling(BlockState state,BlockPos offset){
  if(!(state.getBlock() instanceof ArchitectureBlock block)||!block.definition.kind.equals("door")||!state.contains(Properties.DOUBLE_BLOCK_HALF))return false;
  int dy=state.get(Properties.DOUBLE_BLOCK_HALF)==net.minecraft.block.enums.DoubleBlockHalf.LOWER?1:-1;
  return offset.equals(new BlockPos(0,dy,0));
 }

 static boolean rebuild(World world,BlockPos root,BlockState state){
  if(world.isClient||MUTATING.get())return true;
  BlockPos conflict=conflict(world,root,state,root);if(conflict!=null)return false;
  MUTATING.set(true);
  try{
   Set<BlockPos> wanted=new HashSet<>();
   for(BlockPos offset:required(state).parsedCells.keySet())if(!offset.equals(BlockPos.ORIGIN)&&!reservedDoorSibling(state,offset))wanted.add(root.add(offset));
   removeOwnedParts(world,root,wanted);
   Identifier owner=RegistriesHolder.id(state.getBlock());
   for(BlockPos target:wanted){
    BlockState there=world.getBlockState(target);
    if(!there.isOf(BloodborneBlocks.PART_BLOCK))world.setBlockState(target,BloodborneBlocks.PART_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
    BlockEntity entity=world.getBlockEntity(target);
    if(entity instanceof ArchitecturePartBlockEntity part){part.bind(root,owner);world.updateListeners(target,there,BloodborneBlocks.PART_BLOCK.getDefaultState(),Block.NOTIFY_ALL);}
   }
   return true;
  }finally{MUTATING.set(false);}
 }

 static void removeOwnedParts(World world,BlockPos root){
  removeOwnedParts(world,root,world.getBlockState(root));
 }
 static void removeOwnedParts(World world,BlockPos root,BlockState geometryState){
  if(MUTATING.get()){removeOwnedParts(world,root,geometryState,Set.of());return;}
  MUTATING.set(true);try{removeOwnedParts(world,root,geometryState,Set.of());}finally{MUTATING.set(false);}
 }
 private static void removeOwnedParts(World world,BlockPos root,Set<BlockPos> keep){
  removeOwnedParts(world,root,world.getBlockState(root),keep);
 }
 private static void removeOwnedParts(World world,BlockPos root,BlockState rootState,Set<BlockPos> keep){
  GeometryState geometry=state(rootState);
  if(geometry==null)return;
  for(BlockPos offset:geometry.parsedCells.keySet()){
   BlockPos target=root.add(offset);if(target.equals(root)||keep.contains(target)||!world.isChunkLoaded(target))continue;
   if(!world.getBlockState(target).isOf(BloodborneBlocks.PART_BLOCK))continue;
   ArchitecturePartBlockEntity part=part(world,target);
   if(part!=null&&part.rootPos().equals(root))world.removeBlock(target,false);
  }
 }

 static ArchitecturePartBlockEntity part(BlockView world,BlockPos pos){BlockEntity entity=world.getBlockEntity(pos);return entity instanceof ArchitecturePartBlockEntity part?part:null;}

 static void removeHelper(World world,BlockPos pos){
  boolean previous=MUTATING.get();MUTATING.set(true);
  try{world.removeBlock(pos,false);}finally{MUTATING.set(previous);}
 }

 /** Called only after migration preflight. Never overwrites foreign cells. */
 static void replaceRoot(World world,BlockPos oldRoot,BlockState oldState,BlockPos newRoot,BlockState next){
  if(FunctionalFurniture.isBench(oldState))FunctionalFurniture.removeSeats(world,oldRoot);
  removeOwnedParts(world,oldRoot,oldState);
  MUTATING.set(true);
  try{
   if(!oldRoot.equals(newRoot)||next.isAir())world.removeBlock(oldRoot,false);
   if(!next.isAir())world.setBlockState(newRoot,next,Block.NOTIFY_ALL);
  }finally{MUTATING.set(false);}
  if(!next.isAir())rebuild(world,newRoot,next);
 }

 static RepairResult repair(ServerWorld world,BlockPos center,int radius,boolean apply){
  int roots=0,repaired=0,conflicts=0,orphans=0;
  Map<BlockPos,BlockPos> previewReservations=apply?Map.of():new HashMap<>();
  BlockPos.Mutable cursor=new BlockPos.Mutable();
  for(int x=center.getX()-radius;x<=center.getX()+radius;x++)for(int y=Math.max(world.getBottomY(),center.getY()-radius);y<=Math.min(world.getTopY()-1,center.getY()+radius);y++)for(int z=center.getZ()-radius;z<=center.getZ()+radius;z++){
   cursor.set(x,y,z);if(!world.isChunkLoaded(cursor))continue;
   BlockState state=world.getBlockState(cursor);
   if(state.getBlock() instanceof ArchitectureBlock){
    roots++;BlockPos immutable=cursor.toImmutable();boolean blocked=conflict(world,immutable,state,immutable)!=null;
    if(!blocked&&!apply){for(BlockPos target:occupiedTargets(immutable,state))if(previewReservations.containsKey(target)&&!previewReservations.get(target).equals(immutable)){blocked=true;break;}}
    if(blocked)conflicts++;else{repaired++;if(apply)rebuild(world,immutable,state);else for(BlockPos target:occupiedTargets(immutable,state))previewReservations.put(target,immutable);}
   }
   else if(state.isOf(BloodborneBlocks.PART_BLOCK)){
    ArchitecturePartBlockEntity part=part(world,cursor);boolean orphan=part==null||(world.isChunkLoaded(part.rootPos())&&!world.getBlockState(part.rootPos()).isOf(part.ownerBlock()));
    if(orphan){orphans++;if(apply)world.removeBlock(cursor,false);}
   }
  }
  return new RepairResult(roots,repaired,conflicts,orphans);
 }

 private static Set<BlockPos> occupiedTargets(BlockPos root,BlockState state){
  Set<BlockPos> targets=new HashSet<>();for(BlockPos offset:required(state).parsedCells.keySet())if(!offset.equals(BlockPos.ORIGIN)&&!reservedDoorSibling(state,offset))targets.add(root.add(offset));return targets;
 }

 static boolean isMutating(){return MUTATING.get();}

 /** Keeps registry lookup isolated so geometry parsing remains usable on a dedicated server. */
 private static final class RegistriesHolder {
  static Identifier id(Block block){return net.minecraft.registry.Registries.BLOCK.getId(block);}
 }
}
