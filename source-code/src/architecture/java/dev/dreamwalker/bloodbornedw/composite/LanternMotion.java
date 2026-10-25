package dev.dreamwalker.bloodbornedw.composite;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.sound.*;
/** Contact work is bounded by moving players' occupied cells, not city size. */
public final class LanternMotion {
 public static final Identifier PACKET=new Identifier("bloodborne_dw","lantern_sway");
 private static final Map<ServerWorld,Map<Long,Long>> COOLDOWNS=new WeakHashMap<>();
 private static final Map<ServerWorld,Map<UUID,Vec3d>> POSITIONS=new WeakHashMap<>();
 public static boolean lantern(String id){return id.equals("bloodborne_dw:source_lantern")||id.equals("bloodborne_dw:source_soul_lantern");}
 public static boolean pulse(ServerWorld w,BlockPos root){
  if(!(w.getBlockEntity(root) instanceof CompositeBlockEntity e)||e.resident()==null||!lantern(e.resident().registryId()))return false;
  var times=COOLDOWNS.computeIfAbsent(w,k->new HashMap<>());long now=w.getTime();if(times.getOrDefault(root.asLong(),0L)>now)return false;times.put(root.asLong(),now+35);if(times.size()>512)times.entrySet().removeIf(v->v.getValue()<=now);
  w.playSound(null,root,SoundEvents.BLOCK_LANTERN_HIT,SoundCategory.BLOCKS,.45f,.9f+w.random.nextFloat()*.2f);
  for(var p:PlayerLookup.tracking(w,root)){var b=PacketByteBufs.create();b.writeIdentifier(w.getRegistryKey().getValue());b.writeBlockPos(root);ServerPlayNetworking.send(p,PACKET,b);}return true;
 }
 public static void initialize(){
  UseBlockCallback.EVENT.register((p,w,h,hit)->{if(h!=Hand.MAIN_HAND||p.isSneaking()||!p.getMainHandStack().isEmpty())return ActionResult.PASS;var owner=CompositeRuntime.target(w,hit.getBlockPos(),p);if(owner==null||!lantern(owner.registryId()))return ActionResult.PASS;if(w instanceof ServerWorld s)pulse(s,CompositeData.pos(owner.root()));return ActionResult.SUCCESS;});
  ServerTickEvents.END_WORLD_TICK.register(w->{for(var player:w.getPlayers()){
   var previous=POSITIONS.computeIfAbsent(w,k->new HashMap<>()).put(player.getUuid(),player.getPos());
   if(player.isSpectator()||previous!=null&&player.getPos().squaredDistanceTo(previous)<.00004&&player.getVelocity().lengthSquared()<.00004)continue;var box=player.getBoundingBox().expand(.08);Set<BlockPos> touched=new HashSet<>();
   for(BlockPos cell:BlockPos.iterate(MathHelper.floor(box.minX),MathHelper.floor(box.minY),MathHelper.floor(box.minZ),MathHelper.floor(box.maxX),MathHelper.floor(box.maxY),MathHelper.floor(box.maxZ))){if(!w.isChunkLoaded(cell))continue;
    for(var c:CompositeRuntime.contributions(w,cell))if(lantern(c.owner().registryId()))for(var b:c.shape().selection())if(box.intersects(new Box(cell.getX()+b.minX(),cell.getY()+b.minY(),cell.getZ()+b.minZ(),cell.getX()+b.maxX(),cell.getY()+b.maxY(),cell.getZ()+b.maxZ())){var root=CompositeData.pos(c.owner().root());if(touched.add(root))pulse(w,root);break;}
   }
  }});
 }
}
