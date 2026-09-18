package daot.mixin;

import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.UnmodifiableLevelProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UnmodifiableLevelProperties.class)
public class DerivedLevelDataMixin {
   @Shadow
   @Final
   private ServerWorldProperties worldProperties;

   @Inject(method = "setTimeOfDay", at = @At("HEAD"))
   private void fixSetDayTime(long dayTime, CallbackInfo ci) {
      this.worldProperties.setTimeOfDay(dayTime);
   }
}
