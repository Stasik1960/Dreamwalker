package daot.mixin;

import daot.DannysAot;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemStack.class)
public abstract class TitanBloodlineOdmDurabilityMixin {
   private static final float DURABILITY_DIVISOR = 12.0F;
   private static final Map<String, Float> wearAccumulator = new ConcurrentHashMap<>();

   @ModifyVariable(
      method = "damage(ILnet/minecraft/entity/LivingEntity;Ljava/util/function/Consumer;)V",
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private int daot$titanBloodlineOdmWear(int amount, int originalAmount, LivingEntity entity, Consumer<LivingEntity> onBreak) {
      if (amount > 0 && entity instanceof ServerPlayerEntity player) {
         if (!player.getCommandTags().contains("titan_bloodline")) {
            return amount;
         } else {
            ItemStack self = (ItemStack)(Object)this;
            if (!isOdmEquipment(self.getItem())) {
               return amount;
            } else {
               UUID id = player.getUuid();
               String key = id + "|" + System.identityHashCode(self.getItem());
               float carried = wearAccumulator.getOrDefault(key, 0.0F) + amount / 12.0F;
               int whole = (int)carried;
               wearAccumulator.put(key, carried - whole);
               return whole;
            }
         }
      } else {
         return amount;
      }
   }

   private static boolean isOdmEquipment(Item item) {
      return DannysAot.isODMGear(item) || item == DannysAot.ODM_BOOTS || item == DannysAot.APG_SUIT;
   }
}

