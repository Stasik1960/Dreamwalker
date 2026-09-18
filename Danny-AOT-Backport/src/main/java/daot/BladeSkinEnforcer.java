package daot;

import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public final class BladeSkinEnforcer {
   private static final int SWEEP_INTERVAL = 20;
   private static int tickCounter;

   private BladeSkinEnforcer() {
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         if (++tickCounter >= 20) {
            tickCounter = 0;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
               enforce(player);
            }
         }
      });
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> server.execute(() -> enforce(handler.getPlayer())));
   }

   public static void enforce(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      PlayerInventory inv = player.getInventory();

      for (int i = 0; i < inv.size(); i++) {
         stripIfUnauthorized(inv.getStack(i), uuid);
      }
   }

   private static void stripIfUnauthorized(ItemStack stack, UUID holderUuid) {
      if (!stack.isEmpty() && stack.getItem() instanceof BladeItem) {
         String skin = daot.compat.components.Components.get(stack, DannysAot.BLADE_SKIN);
         if (skin != null) {
            Set<UUID> authorized = authorizedFor(skin);
            if (!authorized.contains(holderUuid)) {
               daot.compat.components.Components.remove(stack, DannysAot.BLADE_SKIN);
            }
         }
      }
   }

   private static Set<UUID> authorizedFor(String skinTag) {
      return switch (skinTag) {
         case "KIRITO" -> DannysAot.KIRITO_AUTHORIZED_UUIDS;
         case "NEEDLE" -> DannysAot.NEEDLE_AUTHORIZED_UUIDS;
         case "HYPER" -> DannysAot.HYPER_AUTHORIZED_UUIDS;
         default -> Set.of();
      };
   }
}
