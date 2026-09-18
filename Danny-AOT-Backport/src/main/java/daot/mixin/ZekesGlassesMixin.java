package daot.mixin;

import daot.TargetGlowTracker;
import daot.ZekesGlassesGlowTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public class ZekesGlassesMixin {
   @Inject(method = "isGlowing", at = @At("RETURN"), cancellable = true)
   private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
      if (!(Boolean)cir.getReturnValue()) {
         Entity self = (Entity)(Object)this;
         if (ZekesGlassesGlowTracker.shouldGlow(self.getId()) || TargetGlowTracker.shouldGlow(self.getId())) {
            cir.setReturnValue(true);
         }
      }
   }
}

