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
 public static boolean place(PlayerEntity player,String id,BlockPos pos,float yaw){if(!(player.getWorld() instanceof ServerWorld world)||(!player.isCreative()&&!player.hasPermissionLevel(2))||!world.getWorldBorder().contains(pos)||!world.isChunkLoaded(pos)||!world.getBlockState(pos).isAir())return false;EntityType<RpObjectEntity> type=TYPES.get(id);if(type==null)return false;RpObjectEntity entity=new RpObjectEntity(type,world,id);float rotation=Math.round(yaw/90f)*90f;entity.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,rotation,0);entity.refreshCollider();Box box=entity.getBoundingBox();if(!world.isSpaceEmpty(entity,box)||!world.getOtherEntities(entity,box).isEmpty())return false;return world.spawnEntity(entity);}
 public static RpObjectEntity objectAt(PlayerEntity player){return player.getWorld().getOtherEntities(player,player.getBoundingBox().expand(6),e->e instanceof RpObjectEntity).stream().map(e->(RpObjectEntity)e).min(java.util.Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);}
}
