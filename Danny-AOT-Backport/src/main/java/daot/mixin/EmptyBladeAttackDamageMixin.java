package daot.mixin;

import daot.BladeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerEntity.class)
public abstract class EmptyBladeAttackDamageMixin {
   @ModifyVariable(method = "attack", at = @At(value = "STORE"), ordinal = 0)
   private float daot$zeroEmptyBladeBaseDamage(float baseDamage) {
      ItemStack stack = ((PlayerEntity)(Object)this).getMainHandStack();
      return stack.getItem() instanceof BladeItem && BladeItem.getBladeState(stack) == BladeItem.BladeState.EMPTY
         ? 0.0F
         : baseDamage;
   }
}
