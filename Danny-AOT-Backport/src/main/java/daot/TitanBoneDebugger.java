package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AfterEntities;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Environment(EnvType.CLIENT)
public class TitanBoneDebugger {
   private static boolean debugEnabled = false;
   private static long lastLogTime = 0L;
   private static final long LOG_INTERVAL_MS = 50L;

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register((AfterEntities)context -> {
         if (debugEnabled) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastLogTime >= 50L) {
               lastLogTime = currentTime;
               MinecraftClient mc = MinecraftClient.getInstance();
               if (mc.player != null && mc.world != null) {
                  for (Entity entity : mc.world.getEntities()) {
                     if (entity instanceof TitanEntity titan) {
                        double dist = mc.player.squaredDistanceTo(titan);
                        if (dist < 100.0) {
                           logTitanBonePositions(titan);
                        }
                     }
                  }
               }
            }
         }
      });
   }

   public static void toggleDebug() {
      debugEnabled = !debugEnabled;
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         mc.player.sendMessage(Text.literal("Titan bone debug: " + (debugEnabled ? "ENABLED" : "DISABLED")));
      }
   }

   private static void logTitanBonePositions(TitanEntity titan) {
      if (MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(titan) instanceof GeoEntityRenderer<?> geoRenderer) {
         GeoModel model = geoRenderer.getGeoModel();

         try {
            GeoBone rightHand = (GeoBone)model.getBone("rightwrist").orElse(null);
            GeoBone leftHand = (GeoBone)model.getBone("leftwrist").orElse(null);
            GeoBone head = (GeoBone)model.getBone("head").orElse(null);
            if (rightHand != null || leftHand != null || head != null) {
               StringBuilder msg = new StringBuilder();
               msg.append(String.format("[Titan Debug | Tick: %d] ", titan.age));
               if (rightHand != null) {
                  Vector3f pos = getBoneWorldOffset(rightHand, titan);
                  msg.append(String.format("RightWrist: (%.2f, %.2f, %.2f) ", pos.x, pos.y, pos.z));
               }

               if (leftHand != null) {
                  Vector3f pos = getBoneWorldOffset(leftHand, titan);
                  msg.append(String.format("LeftWrist: (%.2f, %.2f, %.2f) ", pos.x, pos.y, pos.z));
               }

               if (head != null) {
                  Vector3f pos = getBoneWorldOffset(head, titan);
                  msg.append(String.format("Head: (%.2f, %.2f, %.2f)", pos.x, pos.y, pos.z));
               }

               System.out.println(msg.toString());
            }
         } catch (Exception var9) {
            System.out.println("[TitanBoneDebugger] EXCEPTION: " + var9.getClass().getName() + ": " + var9.getMessage());
            var9.printStackTrace();
         }
      }
   }

   private static Vector3f getBoneWorldOffset(GeoBone bone, TitanEntity titan) {
      Matrix4f worldMatrix = new Matrix4f();

      for (GeoBone current = bone; current != null; current = current.getParent()) {
         Matrix4f boneMatrix = new Matrix4f();
         boneMatrix.translate(current.getPosX(), current.getPosY(), current.getPosZ());
         boneMatrix.rotateXYZ(current.getRotX(), current.getRotY(), current.getRotZ());
         boneMatrix.scale(current.getScaleX(), current.getScaleY(), current.getScaleZ());
         worldMatrix.mulLocal(boneMatrix);
      }

      Vector3f position = new Vector3f();
      worldMatrix.getTranslation(position);
      return position;
   }

   public static boolean isDebugEnabled() {
      return debugEnabled;
   }
}
