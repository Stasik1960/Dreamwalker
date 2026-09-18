package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class BiteFirstPersonRenderer {
   private static boolean rendering = false;

   private BiteFirstPersonRenderer() {
   }

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register(BiteFirstPersonRenderer::onRender);
   }

   public static boolean isActive() {
      return rendering;
   }

   private static void onRender(WorldRenderContext context) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.world != null) {
         if (mc.options.getPerspective() == Perspective.FIRST_PERSON) {
            if (ODMAnimationHandler.isBiting(mc.player.getUuid(), mc.world.getTime())) {
               ClientPlayerEntity player = mc.player;
               EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
               Vec3d cam = context.camera().getPos();
               float partialTick = context.tickDelta();
               double px = MathHelper.lerp((double)partialTick, player.lastRenderX, player.getX()) - cam.x;
               double py = MathHelper.lerp((double)partialTick, player.lastRenderY, player.getY()) - cam.y;
               double pz = MathHelper.lerp((double)partialTick, player.lastRenderZ, player.getZ()) - cam.z;
               Immediate bufferSource = mc.getBufferBuilders().getEntityVertexConsumers();
               rendering = true;

               try {
                  dispatcher.render(
                     player, px, py, pz, player.getYaw(), partialTick, context.matrixStack(), bufferSource, dispatcher.getLight(player, partialTick)
                  );
                  bufferSource.draw();
               } catch (Throwable var17) {
               } finally {
                  rendering = false;
               }
            }
         }
      }
   }
}
