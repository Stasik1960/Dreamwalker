package daot.mixin.client;

import daot.BloodmoonClientState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(BackgroundRenderer.class)
public abstract class BloodmoonFogColorMixin {
   @Shadow
   private static float red;
   @Shadow
   private static float green;
   @Shadow
   private static float blue;
   private static final RegistryKey<World> PARADIS = RegistryKey.of(RegistryKeys.WORLD, new Identifier("dannys-aot", "paradis"));

   @Inject(method = "setFogBlack()V", at = @At("HEAD"))
   private static void dannysaot_bloodmoonFog(CallbackInfo ci) {
      if (BloodmoonClientState.isActive()) {
         ClientWorld level = MinecraftClient.getInstance().world;
         if (level != null && level.getRegistryKey() == PARADIS) {
            red = 0.4F;
            green = 0.04F;
            blue = 0.04F;
         }
      }
   }
}
