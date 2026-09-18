package daot;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import daot.compat.components.DataComponentTypes;
import daot.compat.components.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

@Environment(EnvType.CLIENT)
public class BladeEjectAnimationHandler {
   private static final Map<UUID, Integer> ejectingItems = new HashMap<>();
   private static final int EJECT_ANIMATION_DURATION = 18;

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null) {
            tick(client.player);
         }
      });
   }

   private static void tick(PlayerEntity player) {
      ItemStack mainHand = player.getMainHandStack();
      ItemStack offHand = player.getOffHandStack();
      checkAndTrackEjectingBlade(mainHand);
      checkAndTrackEjectingBlade(offHand);
      Iterator<Entry<UUID, Integer>> iterator = ejectingItems.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<UUID, Integer> entry = iterator.next();
         UUID itemId = entry.getKey();
         int ticksRemaining = entry.getValue();
         if (--ticksRemaining <= 0) {
            ItemStack stack = findItemById(player, itemId);
            if (stack != null) {
               clearEjectFlag(stack);
            }

            iterator.remove();
         } else {
            ejectingItems.put(itemId, ticksRemaining);
         }
      }
   }

   private static void checkAndTrackEjectingBlade(ItemStack stack) {
      if (stack.getItem() instanceof BladeItem && BladeItem.shouldEject(stack)) {
         UUID itemId = getOrCreateItemId(stack);
         if (!ejectingItems.containsKey(itemId)) {
            ejectingItems.put(itemId, 18);
         }
      }
   }

   private static UUID getOrCreateItemId(ItemStack stack) {
      NbtCompound tag = daot.compat.components.Components.getOrDefault(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
      if (tag.containsUuid("BladeItemId")) {
         return tag.getUuid("BladeItemId");
      } else {
         UUID newId = UUID.randomUUID();
         daot.compat.components.Components.apply(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, data -> {
            NbtCompound newTag = data.copyNbt();
            newTag.putUuid("BladeItemId", newId);
            return NbtComponent.of(newTag);
         });
         return newId;
      }
   }

   private static ItemStack findItemById(PlayerEntity player, UUID itemId) {
      ItemStack mainHand = player.getMainHandStack();
      if (mainHand.getItem() instanceof BladeItem) {
         NbtCompound tag = daot.compat.components.Components.getOrDefault(mainHand, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
         if (tag.containsUuid("BladeItemId") && tag.getUuid("BladeItemId").equals(itemId)) {
            return mainHand;
         }
      }

      ItemStack offHand = player.getOffHandStack();
      if (offHand.getItem() instanceof BladeItem) {
         NbtCompound tag = daot.compat.components.Components.getOrDefault(offHand, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
         if (tag.containsUuid("BladeItemId") && tag.getUuid("BladeItemId").equals(itemId)) {
            return offHand;
         }
      }

      return null;
   }

   private static void clearEjectFlag(ItemStack stack) {
      if (stack.getItem() instanceof BladeItem) {
         daot.compat.components.Components.apply(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, data -> {
            NbtCompound tag = data.copyNbt();
            tag.putBoolean("EjectAnimation", false);
            return NbtComponent.of(tag);
         });
      }
   }
}
