package daot.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import daot.BladeItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(HeldItemRenderer.class)
public abstract class BladeNBTBobbingFixMixin {
   @Shadow
   private ItemStack mainHand;
   @Shadow
   private ItemStack offHand;

   @WrapOperation(
      method = "updateHeldItems",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;areEqual(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z")
   )
   private boolean preventBladeBobbing(ItemStack stack1, ItemStack stack2, Operation<Boolean> original) {
      return stack1.getItem() instanceof BladeItem && stack2.getItem() instanceof BladeItem ? true : (Boolean)original.call(new Object[]{stack1, stack2});
   }
}
