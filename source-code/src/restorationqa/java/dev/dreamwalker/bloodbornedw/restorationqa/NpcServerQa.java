package dev.dreamwalker.bloodbornedw.restorationqa;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornerp.mob.*;
import dev.dreamwalker.bloodbornedw.tool.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.server.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

/** Actual server actors, saved state, safe damage, tool selection and navigation. */
public final class NpcServerQa implements ModInitializer {
 public static final BlockPos BASE=new BlockPos(15200,200,15200);
 private final JsonArray checks=new JsonArray();private final List<RpMobEntity> moving=new ArrayList<>();private final List<Float> sounds=new ArrayList<>();private ServerPlayerEntity actor;private int ticks,phase;private boolean done;
 private void check(boolean value,String text){if(!value)throw new AssertionError(text);checks.add(text);}
 @Override public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(s->{if(done)return;try{if(++ticks<30)return;if(phase==0){setup(s);phase=1;ticks=0;return;}if(phase==1&&ticks>=240){finish(s);save("PASS",null);done=true;s.stop(false);}}catch(Throwable error){error.printStackTrace();save("FAIL",error);done=true;s.stop(false);}});}
 private ServerPlayerEntity actor(MinecraftServer s,ServerWorld w){
  var p=new ServerPlayerEntity(s,w,new GameProfile(UUID.randomUUID(),"NpcPuppetQA")){@Override public boolean hasPermissionLevel(int level){return true;}@Override public void sendMessage(Text text,boolean overlay){}};
  new ServerPlayNetworkHandler(s,new ClientConnection(NetworkSide.SERVERBOUND),p){@Override public void sendPacket(Packet<?> packet){if(packet instanceof PlaySoundS2CPacket sound)sounds.add(sound.getVolume());}};
  p.changeGameMode(GameMode.CREATIVE);p.setPosition(BASE.getX(),BASE.getY(),BASE.getZ());s.getPlayerManager().getPlayerList().add(p);return p;
 }
 private void setup(MinecraftServer s){
  var w=s.getOverworld();w.getGameRules().get(net.minecraft.world.GameRules.DO_MOB_SPAWNING).set(false,s);w.setSpawnPos(BASE,0);actor=actor(s,w);
  for(int x=-8;x<24;x++)for(int z=-8;z<190;z++)w.setBlockState(BASE.add(x,-1,z),Blocks.STONE.getDefaultState());
  var held=new ItemStack(BbToolService.ITEM);held.getOrCreateNbt().putInt("BBMode",BbToolItem.Mode.SPECIAL.ordinal());actor.getInventory().setStack(0,held);
  int lane=0;
  for(var entry:MobRegistry.TYPES.entrySet()){
   String id=entry.getKey();var mob=entry.getValue().create(w);mob.refreshPositionAndAngles(BASE.getX()+2.5,BASE.getY(),BASE.getZ()+lane*10+.5,0,0);w.setChunkForced(mob.getChunkPos().x,mob.getChunkPos().z,true);w.spawnEntity(mob);
   check(mob.getMaxHealth()==2&&mob.getHealth()==2,"one heart "+id);
   mob.setTarget(actor);check(mob.getTarget()==null&&!mob.tryAttack(actor),"no targeting or actual combat "+id);
   for(var mode:List.of(GameMode.SURVIVAL,GameMode.ADVENTURE)){actor.changeGameMode(mode);check(!mob.damage(w.getDamageSources().playerAttack(actor),1000)&&mob.getHealth()==2,"protected from "+mode+" "+id);}
   check(!mob.damage(w.getDamageSources().generic(),1000)&&mob.getHealth()==2,"environment cannot damage "+id);
   actor.changeGameMode(GameMode.CREATIVE);
   if(lane==0){int n=sounds.size();for(int k=0;k<4;k++)mob.playAmbientSound();check(sounds.size()==n+2,"half of ambient attempts play");check(Math.abs(sounds.get(n)-1f/3)<.0001,"actual sound packet volume divided by three");}
   check(mob.damage(w.getDamageSources().playerAttack(actor),1000)&&mob.isCorpse()&&mob.getHealth()==1&&!mob.isRemoved(),"first arbitrary-strength creative hit leaves corpse "+id);
   var saved=new NbtCompound();mob.writeNbt(saved);saved.putString("NpcQaProvenance","preserved");var restored=entry.getValue().create(w);restored.readNbt(saved);check(restored.isCorpse()&&restored.getHealth()==1&&restored.getMaxHealth()==2,"corpse round trip "+id);restored.discard();
   BbToolService.apply(actor,ToolTarget.entity(mob));check(!mob.isCorpse()&&mob.getHealth()==2,"tool revives and selects "+id);
   check(BbToolService.special(actor,ToolTarget.entity(mob)),"selected actor accepts cosmetic attack "+id);
   String clip=mob.currentAnimationSuffix();check(MobAnimationSelector.attacks(id).contains(clip),"valid attack animation "+id);
   check(BbToolRotations.rotate(actor,ToolTarget.entity(mob))&&mob.getYaw()==45&&mob.bodyYaw==45&&mob.headYaw==45,"tool rotates all facing angles45 "+id);
   check(BbToolService.commandSelectedMob(actor,BASE.add(8,-1,lane*10)),"vanilla target cell path accepted "+id);
   check(mob.orderedDestination().isPresent(),"destination recorded "+id);moving.add(mob);lane++;
  }
  check(MobRegistry.TYPES.size()==18,"all18 mob types covered");
 }
 private void finish(MinecraftServer s){var w=s.getOverworld();int lane=0;
  for(var mob:moving){double x=BASE.getX()+8.5,z=BASE.getZ()+lane*10+.5;check(Math.hypot(mob.getX()-x,mob.getZ()-z)<.35&&mob.orderedDestination().isEmpty(),"walked to target and stopped "+mob.assetId());check(mob.getTarget()==null&&mob.getHealth()==2,"still passive and healthy "+mob.assetId());
   check(mob.damage(w.getDamageSources().playerAttack(actor),.0001f)&&mob.getHealth()==1,"small creative hit still removes half heart "+mob.assetId());check(mob.damage(w.getDamageSources().playerAttack(actor),.0001f)&&mob.isRemoved(),"second creative hit removes corpse "+mob.assetId());lane++;}
  s.getPlayerManager().getPlayerList().remove(actor);
 }
 private void save(String status,Throwable error){try{var j=new JsonObject();j.addProperty("status",status);j.addProperty("phase",phase);j.addProperty("ticks",ticks);j.add("checks",checks);if(error!=null)j.addProperty("error",error.toString());Files.writeString(Path.of(System.getProperty("dw.npc.qa.output")).resolve("server-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(j));}catch(Exception error2){throw new RuntimeException(error2);}}
}
