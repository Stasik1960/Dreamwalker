package daot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class BladeBlockTracker {
   private static final Map<UUID, Boolean> blockingPlayers = new HashMap<>();
   private static final Map<UUID, Long> blockCooldowns = new HashMap<>();
   private static final long BLOCK_COOLDOWN_TICKS = 60L;
   private static final Identifier BLOCK_SPEED_ID = new Identifier("dannys-aot", "blade_block_slowdown");

   public static void setBlocking(ServerPlayerEntity player, boolean blocking) {
      UUID uuid = player.getUuid();
      if (blocking) {
         if (isOnCooldown(player)) {
            return;
         }

         blockingPlayers.put(uuid, true);
         applySlowdown(player);
      } else {
         blockingPlayers.remove(uuid);
         removeSlowdown(player);
      }
   }

   public static boolean isBlocking(ServerPlayerEntity player) {
      return blockingPlayers.getOrDefault(player.getUuid(), false);
   }

   public static boolean isOnCooldown(ServerPlayerEntity player) {
      Long cooldownEnd = blockCooldowns.get(player.getUuid());
      return cooldownEnd == null ? false : player.getServer().getTicks() < cooldownEnd;
   }

   public static void breakBlock(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      blockingPlayers.remove(uuid);
      removeSlowdown(player);
      blockCooldowns.put(uuid, player.getServer().getTicks() + 60L);
   }

   private static void applySlowdown(ServerPlayerEntity player) {
      if (player.isOnGround()) {
         EntityAttributeInstance speedAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
         if (speedAttr != null && !daot.compat.AttributeModifiers.hasModifier(speedAttr, BLOCK_SPEED_ID)) {
            speedAttr.addTemporaryModifier(daot.compat.AttributeModifiers.create(BLOCK_SPEED_ID, -0.35, Operation.MULTIPLY_TOTAL));
         }
      }
   }

   private static void removeSlowdown(ServerPlayerEntity player) {
      EntityAttributeInstance speedAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
      if (speedAttr != null) {
         daot.compat.AttributeModifiers.removeModifier(speedAttr, BLOCK_SPEED_ID);
      }
   }

   public static void tickSlowdowns(MinecraftServer server) {
      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         UUID uuid = player.getUuid();
         if (blockingPlayers.getOrDefault(uuid, false)) {
            if (player.isOnGround()) {
               applySlowdown(player);
            } else {
               removeSlowdown(player);
            }
         }
      }
   }

   public static void onPlayerDisconnect(UUID uuid) {
      blockingPlayers.remove(uuid);
      blockCooldowns.remove(uuid);
   }
}
