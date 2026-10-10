package dev.dreamwalker.bloodbornerp.object;

import dev.dreamwalker.bloodbornerp.BloodborneRp;
import dev.dreamwalker.bloodbornerp.content.AssetCatalog;
import dev.dreamwalker.bloodbornerp.content.AssetSpec;
import dev.dreamwalker.bloodbornerp.mob.MobRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.item.ItemStack;

public final class ObjectRegistry {
 public static final Map<String,EntityType<RpObjectEntity>> TYPES=new LinkedHashMap<>(); private static boolean registered;
 private static final Set<String> NON_OBJECTS=Set.of("sawcleaver_false","sawcleaver_true","sawspear_false","sawspear_true","boomhammer_false","boomhammer_true","bullet","blood_puddle");
 private ObjectRegistry(){}
 private static final ThreadLocal<Map<String,Object>> ITEM_DIAGNOSTIC_CONTEXT=new ThreadLocal<>();
 public static boolean withItemContext(net.minecraft.item.ItemUsageContext context,java.util.function.BooleanSupplier operation){
  if(!(context.getWorld() instanceof ServerWorld world)||!RpDiagnostics.enabled(world)||ITEM_DIAGNOSTIC_CONTEXT.get()!=null)return operation.getAsBoolean();
  ITEM_DIAGNOSTIC_CONTEXT.set(Map.of("hand",context.getHand().name(),"clickedFace",context.getSide().asString(),"placementOrigin","ordinary_item_context"));try{return operation.getAsBoolean();}finally{ITEM_DIAGNOSTIC_CONTEXT.remove();}
 }
 static net.minecraft.util.ActionResult refuseItem(net.minecraft.item.ItemUsageContext context,String id,String reason){if(context.getWorld() instanceof ServerWorld world)withItemContext(context,()->rejected(world,id,canonicalId(id),context.getBlockPos(),context.getStack(),reason));return net.minecraft.util.ActionResult.FAIL;}
 public static void register(){if(registered)return;registered=true;RpObjectIndex.initialize();for(var entry:AssetCatalog.all().entrySet()){String id=entry.getKey();if(NON_OBJECTS.contains(id)||MobRegistry.TYPES.containsKey(id))continue;AssetSpec spec=entry.getValue();EntityType<RpObjectEntity> type=Registry.register(Registries.ENTITY_TYPE,BloodborneRp.id(id),FabricEntityTypeBuilder.<RpObjectEntity>create(SpawnGroup.MISC,(t,w)->new RpObjectEntity(t,w,id)).dimensions(EntityDimensions.fixed(Math.max(.1f,spec.width()),Math.max(.1f,spec.height()))).trackRangeChunks(8).build());TYPES.put(id,type);Registry.register(Registries.ITEM,BloodborneRp.id(id+"_placer"),new PlacementObjectItem(id));}}
 public static boolean place(PlayerEntity player,String id,BlockPos pos,float yaw){return place(player,id,pos,yaw,net.minecraft.item.ItemStack.EMPTY);}
 public static String canonicalId(String id){return RpObjectCompatibility.canonicalId(id);}
 public static boolean isCanonicalPlacementItem(String id){return RpObjectCompatibility.isCanonical(id);}
 /** Construction items use the native surface behind an RP hit, without activating that RP instance. */
 public static boolean isPlacementItem(ItemStack stack){return stack!=null&&stack.getItem() instanceof PlacementObjectItem;}
 public static double placementY(String id,int y){return id.equals("chandelier_small")||id.equals("chandelier_large")?y-5.5:y;}
 public static float placementYaw(float yaw){return net.minecraft.util.math.MathHelper.floor((net.minecraft.util.math.MathHelper.wrapDegrees(yaw-180f)+22.5f)/45f)*45f;}
 public static boolean place(PlayerEntity player,String id,BlockPos pos,float yaw,net.minecraft.item.ItemStack stack){
  if(RpObjectGeometry.surfaceMounted(canonicalId(id)))return placeOnSurface(player,id,new Vec3d(pos.getX()+.5,pos.getY(),pos.getZ()+.5),Direction.UP,yaw,stack);
  return placeAt(player,id,pos,new Vec3d(pos.getX(),placementY(id,pos.getY()),pos.getZ()),yaw,stack,false,Direction.UP);
 }
 /** New items mount by a reviewed model-local base/hook; reading an old entity never runs this path. */
 public static boolean placeOnSurface(PlayerEntity player,String id,Vec3d surface,Direction face,float yaw,ItemStack stack){
  BlockPos cell=BlockPos.ofFloored(surface);
  if(!RpObjectGeometry.surfaceMounted(canonicalId(id)))return place(player,id,cell,yaw,stack);
  return placeAt(player,id,cell,surface,yaw,stack,true,face);
 }
 private static boolean placeAt(PlayerEntity player,String id,BlockPos cell,Vec3d surface,float yaw,ItemStack stack,boolean mount,Direction face){
  if(!(player.getWorld() instanceof ServerWorld world))return false;
  String canonical=canonicalId(id);
  if(!player.isCreative()&&!player.hasPermissionLevel(2))return rejected(world,id,canonical,cell,stack,"creative_or_operator_required");
  if(!Float.isFinite(yaw)||!Double.isFinite(surface.x)||!Double.isFinite(surface.y)||!Double.isFinite(surface.z))return rejected(world,id,canonical,cell,stack,"non_finite_pose");
  if(player.squaredDistanceTo(surface)>64)return rejected(world,id,canonical,cell,stack,"outside_8_block_placement_reach");
  if(!world.getWorldBorder().contains(cell))return rejected(world,id,canonical,cell,stack,"outside_world_border");
  if(!world.isChunkLoaded(cell))return rejected(world,id,canonical,cell,stack,"source_chunk_not_loaded");
  if(!world.canPlayerModifyAt(player,cell)||!player.getAbilities().allowModifyWorld)return rejected(world,id,canonical,cell,stack,"native_edit_permission_denied");
  EntityType<RpObjectEntity> type=TYPES.get(canonical);
  if(type==null){RpDiagnostics.error(world,RpDiagnostics.typeIdForAsset(canonical),null,cell,"invalid_asset_id","No registered RP placement type: "+id,null);return rejected(world,id,canonical,cell,stack,"unknown_registered_type");}
  long started=RpDiagnostics.begin(world,RpDiagnostics.typeIdForAsset(canonical),cell,"rp.placement");
  try {
  RpObjectEntity entity=type.create(world);if(entity==null){RpDiagnostics.error(world,RpDiagnostics.typeIdForAsset(canonical),null,cell,"entity_factory","Registered RP entity factory returned null",null);return rejected(world,id,canonical,cell,stack,"entity_factory_returned_null");}
  if(stack.hasCustomName())entity.setCustomName(stack.getName());
  if(stack.hasNbt()&&stack.getNbt().contains("bloodborne_rp_object",net.minecraft.nbt.NbtElement.COMPOUND_TYPE)){
   var state=stack.getNbt().getCompound("bloodborne_rp_object");if(state.contains("Open",net.minecraft.nbt.NbtElement.BYTE_TYPE))entity.setOpen(state.getBoolean("Open"));entity.setLocked(state.getBoolean("Locked"));entity.setObjectScale(state.contains("Scale",net.minecraft.nbt.NbtElement.NUMBER_TYPE)?state.getFloat("Scale"):1f);
   if(state.contains("DogsVisible",net.minecraft.nbt.NbtElement.BYTE_TYPE))entity.setDogsVisible(state.getBoolean("DogsVisible"));
  }
  float placedYaw=placementYaw(yaw);Vec3d origin=surface;
  if(mount){Vec3d anchor=RpObjectGeometry.placementAnchor(canonical,face,entity.isOpen()).multiply(entity.asset().scale()*entity.objectScale());origin=surface.subtract(RpObjectGeometry.rotate(anchor,placedYaw));}
  entity.refreshPositionAndAngles(origin.x,origin.y,origin.z,placedYaw,0);entity.refreshCollider();Box box=entity.getBoundingBox();
  if(box.minY<world.getBottomY()||box.maxY>world.getTopY())return rejected(world,id,canonical,cell,stack,"visual_bounds_outside_build_height");
  if(!world.getWorldBorder().contains(box))return rejected(world,id,canonical,cell,stack,"visual_bounds_outside_world_border");
  for(int x=net.minecraft.util.math.MathHelper.floor(box.minX)>>4;x<=net.minecraft.util.math.MathHelper.floor(box.maxX-1e-7)>>4;x++)for(int z=net.minecraft.util.math.MathHelper.floor(box.minZ)>>4;z<=net.minecraft.util.math.MathHelper.floor(box.maxZ-1e-7)>>4;z++)if(!world.isChunkLoaded(x,z))return rejected(world,id,canonical,cell,stack,"visual_chunk_not_loaded");
  String conflict=dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.entityConflict(world,entity.activePhysicalBoxes(),entity,false);
  if(conflict!=null)return rejected(world,id,canonical,cell,stack,"physical_overlap: "+conflict);
  boolean spawned=world.spawnEntity(entity);
  if(spawned&&RpDiagnostics.enabled(world))RpDiagnostics.event(entity,"place",placementRequest(id,canonical,stack),Map.of("serverTypeId",RpDiagnostics.typeId(entity),"registry",Registries.ENTITY_TYPE.getId(entity.getType()).toString(),"yaw",placedYaw,"mountFace",face.asString(),"originX",origin.x,"originY",origin.y,"originZ",origin.z),"COMMITTED","ordinary_item_placement");
  else if(!spawned)rejected(world,id,canonical,cell,stack,"spawn_entity_rejected");
  return spawned;
  }catch(RuntimeException failure){RpDiagnostics.error(world,RpDiagnostics.typeIdForAsset(canonical),null,cell,"placement_exception","RP placement failed unexpectedly",failure);throw failure;}
  finally {RpDiagnostics.finish(world,RpDiagnostics.typeIdForAsset(canonical),cell,"rp.placement",started);}
 }
 private static Map<String,Object> placementRequest(String requested,String canonical,ItemStack stack){var fields=new LinkedHashMap<String,Object>();fields.put("requestedAsset",requested);fields.put("canonicalAsset",canonical);fields.put("heldItem",Registries.ITEM.getId(stack.getItem()).toString());fields.put("heldTypeId",RpDiagnostics.typeId(Registries.ITEM.getId(stack.getItem())));fields.put("placementOrigin","provided_stack_without_item_context");var context=ITEM_DIAGNOSTIC_CONTEXT.get();if(context!=null)fields.putAll(context);return fields;}
 private static boolean rejected(ServerWorld world,String requested,String canonical,BlockPos root,ItemStack stack,String reason){if(RpDiagnostics.enabled(world))RpDiagnostics.event(world,RpDiagnostics.typeIdForAsset(canonical),null,root,"place",placementRequest(requested,canonical,stack),Map.of(),"REFUSED",reason);return false;}
 public static RpObjectEntity objectAt(PlayerEntity player){
  var start=player.getEyePos();var end=start.add(player.getRotationVec(1).multiply(6));double limit=start.squaredDistanceTo(player.raycast(6,1,false).getPos());RpObjectEntity selected=null;
  for(var entity:player.getWorld().getOtherEntities(player,new Box(start,end).expand(1),e->e instanceof RpObjectEntity&&e.canHit())){
   for(var shape:((RpObjectEntity)entity).selectionBoxes()){var box=shape.expand(entity.getTargetingMargin());var hit=box.raycast(start,end);double distance=box.contains(start)?0:hit.map(start::squaredDistanceTo).orElse(Double.POSITIVE_INFINITY);
    if(distance<=limit){limit=distance;selected=(RpObjectEntity)entity;}}
  }return selected;
 }
}
