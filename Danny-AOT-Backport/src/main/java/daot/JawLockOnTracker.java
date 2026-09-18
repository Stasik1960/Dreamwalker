package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class JawLockOnTracker {
   private static final double LOCK_RANGE = 160.0;
   private static final double LOCK_CONE_DEGREES = 14.0;
   private static final int LOCK_GRACE_TICKS = 8;
   private static final int ACQUIRE_TICKS = 4;
   private static int targetId = -1;
   private static int graceLeft = 0;
   private static int acquireTicks = 0;
   private static float animTick = 0.0F;
   private static boolean aiming = false;
   private static final int COLOR_RING = -49088;
   private static final int COLOR_CORE = -58854;

   private JawLockOnTracker() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> clientTick());
      WorldRenderEvents.BEFORE_DEBUG_RENDER.register(JawLockOnTracker::render);
   }

   public static int getTargetId() {
      return targetId;
   }

   public static boolean hasTarget() {
      return targetId != -1;
   }

   public static boolean isAiming() {
      return aiming;
   }

   public static void clear() {
      targetId = -1;
      graceLeft = 0;
      acquireTicks = 0;
   }

   public static Vec3d aimDirection(TestShifterTitanEntity jaw) {
      float yawRad = (float)Math.toRadians(jaw.getYaw());
      Vec3d facing = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
      Entity target = currentTarget();
      if (target == null) {
         return facing;
      } else {
         Vec3d flat = new Vec3d(target.getX() - jaw.getX(), 0.0, target.getZ() - jaw.getZ());
         return flat.lengthSquared() < 0.01 ? facing : flat.normalize();
      }
   }

   private static Entity currentTarget() {
      if (targetId == -1) {
         return null;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world == null) {
            return null;
         } else {
            Entity e = mc.world.getEntityById(targetId);
            return e != null && e.isAlive() ? e : null;
         }
      }
   }

   private static void clientTick() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      animTick++;
      if (player != null && mc.world != null && player.getVehicle() instanceof TestShifterTitanEntity jaw && !(jaw instanceof CartShifterTitanEntity)) {
         aiming = mc.options.useKey.isPressed();
         if (!aiming) {
            clear();
         } else {
            Entity found = pickTarget(mc, player, jaw);
            if (found != null) {
               if (found.getId() != targetId) {
                  acquireTicks = 0;
               }

               targetId = found.getId();
               graceLeft = 8;
            } else if (graceLeft > 0) {
               graceLeft--;
               if (currentTarget() == null) {
                  clear();
               }
            } else {
               clear();
            }

            if (targetId != -1 && acquireTicks < 4) {
               acquireTicks++;
            }
         }
      } else {
         aiming = false;
         clear();
      }
   }

   private static Entity pickTarget(MinecraftClient mc, ClientPlayerEntity player, TestShifterTitanEntity jaw) {
      Camera camera = mc.gameRenderer.getCamera();
      Vec3d eye = camera.isThirdPerson() ? camera.getPos() : player.getEyePos();
      Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).normalize();
      double cosLimit = Math.cos(Math.toRadians(14.0));
      Box searchBox = jaw.getBoundingBox().expand(160.0);
      Entity best = null;
      double bestDot = cosLimit;

      for (Entity candidate : mc.world.getOtherEntities(jaw, searchBox, e -> isLockable(e, player, jaw))) {
         Vec3d center = reticleAnchor(candidate, 1.0F);
         Vec3d toTarget = center.subtract(eye);
         double dist = toTarget.length();
         if (!(dist < 0.5) && !(dist > 160.0)) {
            double dot = toTarget.multiply(1.0 / dist).dotProduct(look);
            if (dot > bestDot) {
               bestDot = dot;
               best = candidate;
            }
         }
      }

      return best == null ? null : preferNape(mc, best);
   }

   private static boolean isLockable(Entity e, ClientPlayerEntity player, TestShifterTitanEntity jaw) {
      if (e != player && e != jaw) {
         if (e.getVehicle() == jaw) {
            return false;
         } else if (!(e instanceof LivingEntity living && living.isAlive())) {
            return false;
         } else if (e instanceof PlayerEntity p && p.isSpectator()) {
            return false;
         } else if (e instanceof AttackTitanNapeEntity nape && nape.getParentTitan() == jaw) {
            return false;
         } else {
            return e instanceof AttackTitanEyeEntity eye && eye.getParentTitan() == jaw ? false : !isNonWeakpointHitbox(e);
         }
      } else {
         return false;
      }
   }

   private static boolean isNonWeakpointHitbox(Entity e) {
      String name = e.getClass().getSimpleName();
      return name.endsWith("LegEntity") || name.endsWith("HandEntity") || name.endsWith("GrabEntity");
   }

   private static Entity preferNape(MinecraftClient mc, Entity picked) {
      if (!(picked instanceof GrabbingTitan) && !(picked instanceof ShifterTitan)) {
         return picked;
      } else {
         Vec3d bodyCenter = picked.getPos().add(0.0, picked.getHeight() * 0.5, 0.0);
         Entity bestNape = null;
         double bestDistSq = Double.MAX_VALUE;

         for (Entity e : mc.world.getOtherEntities(picked, picked.getBoundingBox().expand(2.0), ex -> ex.getClass().getSimpleName().endsWith("NapeEntity"))) {
            double distSq = e.getPos().add(0.0, e.getHeight() * 0.5, 0.0).squaredDistanceTo(bodyCenter);
            if (distSq < bestDistSq) {
               bestDistSq = distSq;
               bestNape = e;
            }
         }

         return bestNape != null ? bestNape : picked;
      }
   }

   private static Vec3d reticleAnchor(Entity e, float partialTick) {
      return e.getLerpedPos(partialTick).add(0.0, e.getHeight() * 0.5, 0.0);
   }

   private static void render(WorldRenderContext context) {
      if (aiming) {
         Entity target = currentTarget();
         if (target != null) {
            MatrixStack poseStack = context.matrixStack();
            if (poseStack != null) {
               MinecraftClient mc = MinecraftClient.getInstance();
               float partialTick = context.tickDelta();
               Vec3d cameraPos = context.camera().getPos();
               Vec3d anchor = reticleAnchor(target, partialTick);
               poseStack.push();
               poseStack.translate(anchor.x - cameraPos.x, anchor.y - cameraPos.y, anchor.z - cameraPos.z);
               poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-context.camera().getYaw()));
               poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(context.camera().getPitch()));
               double dist = anchor.distanceTo(cameraPos);
               float scale = (float)Math.max(0.25, dist * 0.045);
               poseStack.scale(scale, scale, scale);
               float anim = animTick + partialTick;
               float acquire = Math.min(1.0F, (acquireTicks + partialTick) / 4.0F);
               float ease = acquire * acquire * (3.0F - 2.0F * acquire);
               poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45.0F + anim * 1.5F * (1.0F - ease)));
               Immediate buffers = mc.getBufferBuilders().getEntityVertexConsumers();
               VertexConsumer buffer = buffers.getBuffer(LockOnRenderTypes.RETICLE);
               Matrix4f matrix = poseStack.peek().getPositionMatrix();
               float half = 0.55F - 0.15F * ease;
               float thick = 0.075F;
               drawSquareOutline(buffer, matrix, half, thick, -49088);
               float pulse = 0.85F + 0.15F * (float)Math.sin(anim * 0.3F);
               drawRect(buffer, matrix, -0.09F * pulse, -0.09F * pulse, 0.09F * pulse, 0.09F * pulse, -58854);
               buffers.draw(LockOnRenderTypes.RETICLE);
               poseStack.pop();
            }
         }
      }
   }

   private static void drawSquareOutline(VertexConsumer buffer, Matrix4f matrix, float half, float thick, int color) {
      drawRect(buffer, matrix, -half, half - thick, half, half, color);
      drawRect(buffer, matrix, -half, -half, half, -half + thick, color);
      drawRect(buffer, matrix, -half, -half, -half + thick, half, color);
      drawRect(buffer, matrix, half - thick, -half, half, half, color);
   }

   private static void drawRect(VertexConsumer buffer, Matrix4f matrix, float x0, float y0, float x1, float y1, int color) {
      int a = color >>> 24 & 0xFF;
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      buffer.vertex(matrix, x0, y0, 0.0F).color(r, g, b, a).next();
      buffer.vertex(matrix, x1, y0, 0.0F).color(r, g, b, a).next();
      buffer.vertex(matrix, x1, y1, 0.0F).color(r, g, b, a).next();
      buffer.vertex(matrix, x0, y1, 0.0F).color(r, g, b, a).next();
   }
}
