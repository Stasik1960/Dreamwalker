package daot.mixin;

import daot.VanishManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public class VanishSoundMixin {
   @Inject(method = "playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;FFJ)V", at = @At("HEAD"), cancellable = true)
   private void suppressVanishedSound(
      PlayerEntity player,
      double x,
      double y,
      double z,
      RegistryEntry<SoundEvent> sound,
      SoundCategory source,
      float volume,
      float pitch,
      long seed,
      CallbackInfo ci
   ) {
      if (player != null && VanishManager.isVanished(player.getUuid())) {
         ci.cancel();
      }
   }
}
