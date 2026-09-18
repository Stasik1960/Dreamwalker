package daot.mixin;

import daot.DannysAot;
import daot.LacedFoodData;
import daot.VillagerTransformTracker;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LacedFoodEatMixin {
   @Inject(method = "consumeItem", at = @At("HEAD"))
   private void dannysaot$onFinishLacedConsume(CallbackInfo ci) {
      LivingEntity self = (LivingEntity)(Object)this;
      if (self instanceof ServerPlayerEntity player) {
         ItemStack useItem = player.getActiveItem();
         if (!useItem.isEmpty()) {
            LacedFoodData laced = daot.compat.components.Components.get(useItem, DannysAot.LACED_FOOD_DATA);
            if (laced != null) {
               if (!VillagerTransformTracker.isPlayerInjected(player)) {
                  VillagerTransformTracker.injectPlayer(player, laced.sourceName(), laced.isRoyal());
               }
            }
         }
      }
   }
}

