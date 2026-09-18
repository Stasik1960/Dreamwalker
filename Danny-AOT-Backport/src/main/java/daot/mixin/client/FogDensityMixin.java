package daot.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import daot.FogClientState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.BackgroundRenderer.FogType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(BackgroundRenderer.class)
public abstract class FogDensityMixin {
   private static final RegistryKey<World> PARADIS = RegistryKey.of(RegistryKeys.WORLD, new Identifier("dannys-aot", "paradis"));
   private static final float FOG_START = 6.0F;
   private static final float FOG_END = 40.0F;

   @Inject(method = "applyFog", at = @At("TAIL"))
   private static void daot_deepFog(Camera camera, FogType fogMode, float renderDistance, boolean isFoggy, float partialTick, CallbackInfo ci) {
      if (FogClientState.isActive()) {
         ClientWorld level = MinecraftClient.getInstance().world;
         if (level != null && level.getRegistryKey() == PARADIS) {
            Entity cam = camera.getFocusedEntity();
            if (cam != null) {
               if (FogClientState.isSpawnZone(cam.getBlockX(), cam.getBlockZ())) {
                  RenderSystem.setShaderFogStart(6.0F);
                  RenderSystem.setShaderFogEnd(40.0F);
               }
            }
         }
      }
   }
}
