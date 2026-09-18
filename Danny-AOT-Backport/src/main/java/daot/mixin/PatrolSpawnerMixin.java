package daot.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.spawner.PatrolSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PatrolSpawner.class)
public class PatrolSpawnerMixin {
   private static final Identifier PARADIS_DIMENSION = new Identifier("dannys-aot", "paradis");

   @Inject(method = "spawn", at = @At("HEAD"), cancellable = true)
   private void preventPatrolSpawnsInParadis(ServerWorld level, boolean spawnMonsters, boolean spawnAnimals, CallbackInfoReturnable<Integer> cir) {
      if (level.getRegistryKey().getValue().equals(PARADIS_DIMENSION)) {
         cir.setReturnValue(0);
      }
   }
}
