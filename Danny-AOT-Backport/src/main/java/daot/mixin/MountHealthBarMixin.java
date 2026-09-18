package daot.mixin;

import daot.ArmoredTitanEntity;
import daot.AttackTitanEntity;
import daot.BeastTitanEntity;
import daot.ColossalTitanEntity;
import daot.FemaleTitanEntity;
import daot.WarhammerTitanEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public class MountHealthBarMixin {
   @Inject(method = "getRiddenEntity", at = @At("RETURN"), cancellable = true)
   private void hideTitanMountHealth(CallbackInfoReturnable<LivingEntity> cir) {
      LivingEntity vehicle = (LivingEntity)cir.getReturnValue();
      if (vehicle instanceof ColossalTitanEntity
         || vehicle instanceof AttackTitanEntity
         || vehicle instanceof ArmoredTitanEntity
         || vehicle instanceof FemaleTitanEntity
         || vehicle instanceof BeastTitanEntity
         || vehicle instanceof WarhammerTitanEntity) {
         cir.setReturnValue(null);
      }
   }
}
