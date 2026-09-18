package daot.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DamageTracker.class)
public abstract class HoodedKillMessageMixin {
   @Shadow
   @Final
   private LivingEntity entity;

   @Inject(method = "getDeathMessage", at = @At("RETURN"), cancellable = true)
   private void hideHoodedKiller(CallbackInfoReturnable<Text> cir) {
      if (this.entity != null) {
         DamageSource lastSource = this.entity.getRecentDamageSource();
         if (lastSource != null) {
            if (lastSource.getAttacker() instanceof PlayerEntity player && player.getCommandTags().contains("hood_up")) {
               Text victimName = this.entity.getDisplayName();
               cir.setReturnValue(Text.translatable("death.attack.player", victimName, Text.literal("Unknown Player")));
            }
         }
      }
   }
}
