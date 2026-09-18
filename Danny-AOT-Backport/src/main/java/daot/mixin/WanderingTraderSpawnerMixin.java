package daot.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.WanderingTraderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WanderingTraderManager.class)
public class WanderingTraderSpawnerMixin {
   private static final Identifier PARADIS_DIMENSION = new Identifier("dannys-aot", "paradis");

   @Inject(method = "spawn", at = @At("HEAD"), cancellable = true)
   private void preventWanderingTraderSpawnsInParadis(ServerWorld level, boolean spawnMonsters, boolean spawnAnimals, CallbackInfoReturnable<Integer> cir) {
      if (level.getRegistryKey().getValue().equals(PARADIS_DIMENSION)) {
         cir.setReturnValue(0);
      }
   }
}
