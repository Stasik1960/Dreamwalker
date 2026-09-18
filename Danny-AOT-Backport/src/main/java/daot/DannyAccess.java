package daot;

import daot.mixin.CommandSourceStackAccessor;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public final class DannyAccess {
   private static final UUID SPEAKOR_UUID = UUID.fromString("2b80f8d6-33d8-4409-bcdf-77fd4d273b59");
   private static final String SPEAKOR_NAME = "Speakor";
   private static final UUID INRUPT_UUID = UUID.fromString("e478779b-e3c4-4a28-9b93-1d07072df930");
   private static final String INRUPT_NAME = "Inrupt";
   private static final UUID LODBROK_UUID = UUID.fromString("658f3922-881f-436c-9010-5c679b1324b2");
   private static final String LODBROK_NAME = "Lodbrok03";
   private static final UUID THEODOARD_UUID = UUID.fromString("6949ce40-3471-468e-a1c7-a879cce4a596");
   private static final Set<UUID> DANNY_POWER_HOLDERS = Set.of(SPEAKOR_UUID, INRUPT_UUID, LODBROK_UUID, THEODOARD_UUID);

   private DannyAccess() {
   }

   public static boolean mayHoldDannyPower(UUID playerUUID) {
      return DANNY_POWER_HOLDERS.isEmpty() ? true : playerUUID != null && DANNY_POWER_HOLDERS.contains(playerUUID);
   }

   public static boolean isPowerHolderListConfigured() {
      return !DANNY_POWER_HOLDERS.isEmpty();
   }

   private static boolean matches(ServerPlayerEntity player, UUID pinnedUuid, String name) {
      if (player == null) {
         return false;
      } else {
         return pinnedUuid != null ? pinnedUuid.equals(player.getUuid()) : name.equals(player.getGameProfile().getName());
      }
   }

   public static boolean isFullAccess(ServerPlayerEntity player) {
      return matches(player, SPEAKOR_UUID, "Speakor") || matches(player, INRUPT_UUID, "Inrupt");
   }

   public static boolean isLimitedAccess(ServerPlayerEntity player) {
      return matches(player, LODBROK_UUID, "Lodbrok03");
   }

   public static boolean isSubsetAccess(ServerPlayerEntity player) {
      return isFullAccess(player) || isLimitedAccess(player);
   }

   public static boolean mayHoldDiscreetShifter(ServerPlayerEntity player) {
      return isSubsetAccess(player);
   }

   public static boolean isTitanCommandAccess(ServerPlayerEntity player) {
      return isFullAccess(player) || player != null && THEODOARD_UUID.equals(player.getUuid());
   }

   public static boolean isDirectInvocation(ServerCommandSource stack) {
      if (stack == null) {
         return false;
      } else {
         Entity entity = stack.getEntity();
         if (entity == null) {
            return false;
         } else {
            try {
               CommandOutput raw = ((CommandSourceStackAccessor)stack).daot$getSource();
               return raw == entity;
            } catch (Throwable var3) {
               return true;
            }
         }
      }
   }

   private static ServerPlayerEntity directPlayer(ServerCommandSource stack) {
      if (stack != null && stack.getEntity() instanceof ServerPlayerEntity player) {
         return isDirectInvocation(stack) ? player : null;
      } else {
         return null;
      }
   }

   public static boolean hasFullDannyAccess(ServerCommandSource stack) {
      return isFullAccess(directPlayer(stack));
   }

   public static boolean hasLimitedDannyAccess(ServerCommandSource stack) {
      return isLimitedAccess(directPlayer(stack));
   }

   public static boolean canUseDannySubset(ServerCommandSource stack) {
      return isSubsetAccess(directPlayer(stack));
   }

   public static boolean hasTitanCommandAccess(ServerCommandSource stack) {
      return isTitanCommandAccess(directPlayer(stack));
   }

   public static boolean canTraverseDanny(ServerCommandSource stack) {
      ServerPlayerEntity player = directPlayer(stack);
      return isSubsetAccess(player) || isTitanCommandAccess(player);
   }

   public static boolean mayHoldTag(ServerPlayerEntity player, String tag) {
      if (player == null || tag == null) {
         return false;
      } else if ("ogre_shifter".equals(tag)) {
         return isSubsetAccess(player);
      } else {
         return "titan_bloodline".equals(tag) ? isTitanCommandAccess(player) : isFullAccess(player);
      }
   }
}
