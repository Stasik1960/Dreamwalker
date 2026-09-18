package daot.mixin.client;

import daot.BloodmoonClientState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ClientWorld.class)
public abstract class BloodmoonSkyColorMixin {
   private static final RegistryKey<World> PARADIS = RegistryKey.of(RegistryKeys.WORLD, new Identifier("dannys-aot", "paradis"));

   @Inject(method = "getSkyColor(Lnet/minecraft/util/math/Vec3d;F)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
   private void dannysaot_bloodmoonSky(Vec3d pos, float partialTick, CallbackInfoReturnable<Vec3d> cir) {
      if (BloodmoonClientState.isActive()) {
         ClientWorld self = (ClientWorld)(Object)this;
         if (self.getRegistryKey() == PARADIS) {
            cir.setReturnValue(new Vec3d(0.55, 0.05, 0.05));
         }
      }
   }
}

