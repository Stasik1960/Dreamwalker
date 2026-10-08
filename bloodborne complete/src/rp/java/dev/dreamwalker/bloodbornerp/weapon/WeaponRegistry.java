package dev.dreamwalker.bloodbornerp.weapon;

import dev.dreamwalker.bloodbornerp.BloodborneRp;
import dev.dreamwalker.bloodbornerp.RpConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

/** Server-authoritative registration and transformation endpoint for trick weapons. */
public final class WeaponRegistry {
  public static final Identifier TRANSFORM_PACKET=BloodborneRp.id("transform_weapon");
  public static final TrickWeaponItem SAW_CLEAVER=new TrickWeaponItem("sawcleaver",new WeaponProfile(4.0,-2.6),new WeaponProfile(5.5,-2.9),false);
  public static final TrickWeaponItem SAW_SPEAR=new TrickWeaponItem("sawspear",new WeaponProfile(4.0,-2.5),new WeaponProfile(5.0,-2.7),false);
  public static final TrickWeaponItem BOOM_HAMMER=new TrickWeaponItem("boomhammer",new WeaponProfile(5.5,-3.1),new WeaponProfile(7.5,-3.3),true);
  private static boolean registered;
  private static final java.util.Set<java.util.UUID> PENDING=java.util.concurrent.ConcurrentHashMap.newKeySet();
  private WeaponRegistry() {}

  public static void register() {
    if(registered) return;
    registered=true;
    Registry.register(Registries.ITEM,BloodborneRp.id("saw_cleaver"),SAW_CLEAVER);
    Registry.register(Registries.ITEM,BloodborneRp.id("saw_spear"),SAW_SPEAR);
    Registry.register(Registries.ITEM,BloodborneRp.id("boom_hammer"),BOOM_HAMMER);
    ServerPlayNetworking.registerGlobalReceiver(TRANSFORM_PACKET,(server,player,handler,buf,responseSender)-> {
      if(buf.readableBytes()!=1) return;
      byte handId=buf.readByte();
      if(handId!=0 && handId!=1) return;
      Hand hand=handId==0 ? Hand.MAIN_HAND : Hand.OFF_HAND;
      if(!PENDING.add(player.getUuid()))return;
      server.execute(()->{try{if(server.getPlayerManager().getPlayer(player.getUuid())==player)transform(player,hand);}finally{PENDING.remove(player.getUuid());}});
    });
    net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->PENDING.remove(handler.player.getUuid()));
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server->PENDING.clear());
  }

  public static boolean transform(ServerPlayerEntity player, Hand hand) {
    if(!player.isAlive() || player.isSpectator()) return false;
    if(!(player.getStackInHand(hand).getItem() instanceof TrickWeaponItem weapon)) return false;
    if(player.getItemCooldownManager().isCoolingDown(weapon)) return false;
    TrickWeaponItem.setForm(player.getStackInHand(hand),TrickWeaponItem.form(player.getStackInHand(hand)).other());
    player.getItemCooldownManager().set(weapon,RpConfig.INSTANCE.weaponTransformCooldownTicks);
    return true;
  }
}
