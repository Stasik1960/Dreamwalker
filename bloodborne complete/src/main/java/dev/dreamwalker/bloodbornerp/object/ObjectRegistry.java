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

public final class ObjectRegistry {
 public static final Map<String,EntityType<RpObjectEntity>> TYPES=new LinkedHashMap<>(); private static boolean registered;
 private static final Set<String> NON_OBJECTS=Set.of("sawcleaver_false","sawcleaver_true","sawspear_false","sawspear_true","boomhammer_false","boomhammer_true","bullet","blood_puddle");
 private ObjectRegistry(){}
 public static void register(){if(registered)return;registered=true;for(var entry:AssetCatalog.all().entrySet()){String id=entry.getKey();if(NON_OBJECTS.contains(id)||MobRegistry.TYPES.containsKey(id))continue;AssetSpec spec=entry.getValue();EntityType<RpObjectEntity> type=Registry.register(Registries.ENTITY_TYPE,BloodborneRp.id(id),FabricEntityTypeBuilder.<RpObjectEntity>create(SpawnGroup.MISC,(t,w)->new RpObjectEntity(t,w,id)).dimensions(EntityDimensions.fixed(Math.max(.1f,spec.width()),Math.max(.1f,spec.height()))).trackRangeChunks(8).build());TYPES.put(id,type);Registry.register(Registries.ITEM,BloodborneRp.id(id+"_placer"),new PlacementObjectItem(id));}}
 public static boolean place(PlayerEntity player,String id,BlockPos pos,float yaw){return place(player,id,pos,yaw,net.minecraft.item.ItemStack.EMPTY);}
 public static double placementY(String id,int y){return id.equals("chandelier_small")||id.equals("chandelier_large")?y-5.5:y;}
 public static float placementYaw(float yaw){return net.minecraft.util.math.MathHelper.floor((net.minecraft.util.math.MathHelper.wrapDegrees(yaw-180f)+22.5f)/45f)*45f;}
 public static boolean place(PlayerEntity player,String id,BlockPos pos,float yaw,net.minecraft.item.ItemStack stack){
  if(!(player.getWorld() instanceof ServerWorld world)||(!player.isCreative()&&!player.hasPermissionLevel(2))||!Float.isFinite(yaw)||player.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(pos))>64||!world.getWorldBorder().contains(pos)||!world.isChunkLoaded(pos)||!world.getBlockState(pos).isReplaceable())return false;
  EntityType<RpObjectEntity> type=TYPES.get(id);if(type==null)return false;RpObjectEntity entity=type.create(world);if(entity==null)return false;
  entity.refreshPositionAndAngles(pos.getX(),placementY(id,pos.getY()),pos.getZ(),placementYaw(yaw),0);entity.refreshCollider();Box box=entity.getBoundingBox();
  if(box.minY<world.getBottomY()||box.maxY>world.getTopY()||!world.getWorldBorder().contains(box))return false;
  for(int x=net.minecraft.util.math.MathHelper.floor(box.minX)>>4;x<=net.minecraft.util.math.MathHelper.floor(box.maxX-1e-7)>>4;x++)for(int z=net.minecraft.util.math.MathHelper.floor(box.minZ)>>4;z<=net.minecraft.util.math.MathHelper.floor(box.maxZ-1e-7)>>4;z++)if(!world.isChunkLoaded(x,z))return false;
  // Like the source mod, builders may embed large decorations in existing architecture.
  // Rejecting the full entity box against walls or the builder made door placement unusable.
  if(stack.hasCustomName())entity.setCustomName(stack.getName());
  if(stack.hasNbt()&&stack.getNbt().contains("bloodborne_rp_object",net.minecraft.nbt.NbtElement.COMPOUND_TYPE)){
   var state=stack.getNbt().getCompound("bloodborne_rp_object");entity.setOpen(state.getBoolean("Open"));entity.setLocked(state.getBoolean("Locked"));entity.setObjectScale(state.contains("Scale",net.minecraft.nbt.NbtElement.NUMBER_TYPE)?state.getFloat("Scale"):1f);
  }
  return world.spawnEntity(entity);
 }
 public static RpObjectEntity objectAt(PlayerEntity player){
  var start=player.getEyePos();var end=start.add(player.getRotationVec(1).multiply(6));double limit=start.squaredDistanceTo(player.raycast(6,1,false).getPos());RpObjectEntity selected=null;
  for(var entity:player.getWorld().getOtherEntities(player,new Box(start,end).expand(1),e->e instanceof RpObjectEntity&&e.canHit())){
   var box=entity.getBoundingBox().expand(entity.getTargetingMargin());var hit=box.raycast(start,end);double distance=box.contains(start)?0:hit.map(start::squaredDistanceTo).orElse(Double.POSITIVE_INFINITY);
   if(distance<=limit){limit=distance;selected=(RpObjectEntity)entity;}
  }return selected;
 }
}
