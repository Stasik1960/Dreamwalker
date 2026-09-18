package daot;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class HookLineRenderer {
   private static final int SEGMENTS = 30;
   private static final double BASE_SAG = 0.2;
   private static final double VELOCITY_BEND_FACTOR = 0.5;
   private static final double MAX_BEND = 0.1;
   private static final float LINE_THICKNESS = 0.03F;
   private static final int[] COLOR_DEFAULT = new int[]{0, 0, 0};
   private static final double REFERENCE_DISTANCE = 15.0;
   private static final double ZIGZAG_AMPLITUDE = 0.3;
   private static final int ZIGZAG_FREQUENCY = 4;
   private static final double MAX_NORMAL_DISTANCE = 65.0;
   private static Vec3d smoothedLeftBend = Vec3d.ZERO;
   private static Vec3d smoothedRightBend = Vec3d.ZERO;
   private static final double SMOOTHING_FACTOR = 0.15;

   public static void register() {
      WorldRenderEvents.BEFORE_DEBUG_RENDER.register(HookLineRenderer::renderHookLines);
   }

   private static void renderHookLines(WorldRenderContext context) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null) {
         HookPoint leftHook = ODMTickHandler.getLeftHook(player.getUuid());
         HookPoint rightHook = ODMTickHandler.getRightHook(player.getUuid());
         MatrixStack poseStack = context.matrixStack();
         Immediate bufferSource = mc.getBufferBuilders().getEntityVertexConsumers();
         RenderSystem.enableDepthTest();
         RenderSystem.depthMask(true);
         Vec3d cameraPos = context.camera().getPos();
         float partialTick = context.tickDelta();
         Vec3d playerPos = player.getLerpedPos(partialTick);
         float yaw = (float)Math.toRadians(player.getYaw());
         double legOffset = 0.3;
         Vec3d leftLegPos = playerPos.add(Math.cos(yaw) * legOffset, 0.6, Math.sin(yaw) * legOffset);
         Vec3d rightLegPos = playerPos.add(-Math.cos(yaw) * legOffset, 0.6, -Math.sin(yaw) * legOffset);
         if (mc.options.getPerspective().isFirstPerson() && player.getEquippedStack(EquipmentSlot.LEGS).getItem() == DannysAot.ODM_APG) {
            boolean mainIsRight = player.getMainArm() == Arm.RIGHT;
            boolean mainIsApg = player.getMainHandStack().getItem() == DannysAot.APG_GUN;
            boolean offIsApg = player.getOffHandStack().getItem() == DannysAot.APG_GUN;
            Vec3d mainOffset = BladeArmRenderer.apgFirstPersonHandOffsetMain;
            Vec3d offOffset = BladeArmRenderer.apgFirstPersonHandOffsetOff;
            Vec3d rightSideOffset = mainIsRight ? (mainIsApg ? mainOffset : null) : (offIsApg ? offOffset : null);
            Vec3d leftSideOffset = mainIsRight ? (offIsApg ? offOffset : null) : (mainIsApg ? mainOffset : null);
            Camera cam = context.camera();
            if (leftSideOffset != null) {
               leftLegPos = cameraLocalToWorld(leftSideOffset, cam, cameraPos);
            }

            if (rightSideOffset != null) {
               rightLegPos = cameraLocalToWorld(rightSideOffset, cam, cameraPos);
            }
         }

         int[] wireColor = COLOR_DEFAULT;
         if (leftHook != null && leftHook.active) {
            Vec3d hookEnd = leftHook.getCurrentPosition();
            smoothedLeftBend = smoothBend(smoothedLeftBend, leftHook);
            if (leftHook.isExtending) {
               renderZigzagLine(poseStack, bufferSource, leftLegPos, hookEnd, cameraPos, leftHook.getExtensionProgress(), wireColor);
            } else if (leftHook.isRetracting) {
               renderZigzagLine(poseStack, bufferSource, leftLegPos, hookEnd, cameraPos, leftHook.getRetractionProgress(), wireColor);
            } else {
               renderCurvedLine(poseStack, bufferSource, leftLegPos, hookEnd, cameraPos, smoothedLeftBend, wireColor);
            }
         } else {
            smoothedLeftBend = Vec3d.ZERO;
         }

         if (rightHook != null && rightHook.active) {
            Vec3d hookEnd = rightHook.getCurrentPosition();
            smoothedRightBend = smoothBend(smoothedRightBend, rightHook);
            if (rightHook.isExtending) {
               renderZigzagLine(poseStack, bufferSource, rightLegPos, hookEnd, cameraPos, rightHook.getExtensionProgress(), wireColor);
            } else if (rightHook.isRetracting) {
               renderZigzagLine(poseStack, bufferSource, rightLegPos, hookEnd, cameraPos, rightHook.getRetractionProgress(), wireColor);
            } else {
               renderCurvedLine(poseStack, bufferSource, rightLegPos, hookEnd, cameraPos, smoothedRightBend, wireColor);
            }
         } else {
            smoothedRightBend = Vec3d.ZERO;
         }

         renderRemotePlayerHooks(poseStack, bufferSource, cameraPos, partialTick, mc);
         bufferSource.draw();
      }
   }

   private static void renderRemotePlayerHooks(MatrixStack poseStack, Immediate bufferSource, Vec3d cameraPos, float partialTick, MinecraftClient mc) {
      if (mc.world != null) {
         for (Entry<Integer, RemoteHookTracker.RemoteHookData> entry : RemoteHookTracker.getAllHooks().entrySet()) {
            int playerId = entry.getKey();
            RemoteHookTracker.RemoteHookData hookData = entry.getValue();
            if (hookData.leftActive || hookData.rightActive) {
               long now = System.currentTimeMillis();
               boolean leftStale = hookData.leftActive && !hookData.leftExtending && !hookData.leftRetracting && now - hookData.leftLastSyncTime > 500L;
               boolean rightStale = hookData.rightActive && !hookData.rightExtending && !hookData.rightRetracting && now - hookData.rightLastSyncTime > 500L;
               if (leftStale) {
                  hookData.leftActive = false;
               }

               if (rightStale) {
                  hookData.rightActive = false;
               }

               if ((hookData.leftActive || hookData.rightActive)
                  && mc.world.getEntityById(playerId) instanceof PlayerEntity remotePlayer
                  && remotePlayer != mc.player) {
                  Vec3d remotePlayerPos = remotePlayer.getLerpedPos(partialTick);
                  float remoteYaw = (float)Math.toRadians(remotePlayer.getYaw());
                  double legOffset = 0.3;
                  Vec3d remoteLeftLegPos = remotePlayerPos.add(Math.cos(remoteYaw) * legOffset, 0.6, Math.sin(remoteYaw) * legOffset);
                  Vec3d remoteRightLegPos = remotePlayerPos.add(-Math.cos(remoteYaw) * legOffset, 0.6, -Math.sin(remoteYaw) * legOffset);
                  int[] remoteWireColor = COLOR_DEFAULT;
                  if (hookData.leftActive) {
                     Vec3d hookEnd = hookData.getLeftCurrentPosition();
                     if (hookData.leftExtending) {
                        renderZigzagLine(poseStack, bufferSource, remoteLeftLegPos, hookEnd, cameraPos, hookData.getLeftExtensionProgress(), remoteWireColor);
                     } else if (hookData.leftRetracting) {
                        renderZigzagLine(poseStack, bufferSource, remoteLeftLegPos, hookEnd, cameraPos, hookData.getLeftRetractionProgress(), remoteWireColor);
                     } else {
                        renderCurvedLine(poseStack, bufferSource, remoteLeftLegPos, hookEnd, cameraPos, Vec3d.ZERO, remoteWireColor);
                     }
                  }

                  if (hookData.rightActive) {
                     Vec3d hookEnd = hookData.getRightCurrentPosition();
                     if (hookData.rightExtending) {
                        renderZigzagLine(poseStack, bufferSource, remoteRightLegPos, hookEnd, cameraPos, hookData.getRightExtensionProgress(), remoteWireColor);
                     } else if (hookData.rightRetracting) {
                        renderZigzagLine(poseStack, bufferSource, remoteRightLegPos, hookEnd, cameraPos, hookData.getRightRetractionProgress(), remoteWireColor);
                     } else {
                        renderCurvedLine(poseStack, bufferSource, remoteRightLegPos, hookEnd, cameraPos, Vec3d.ZERO, remoteWireColor);
                     }
                  }
               }
            }
         }
      }
   }

   private static Vec3d cameraLocalToWorld(Vec3d localOffset, Camera cam, Vec3d cameraPos) {
      Vector3f p = new Vector3f((float)localOffset.x, (float)localOffset.y, (float)localOffset.z);
      cam.getRotation().transform(p);
      return cameraPos.add(p.x, p.y, p.z);
   }

   private static Vec3d smoothBend(Vec3d currentSmoothed, HookPoint hook) {
      Vec3d velocity = hook.playerVelocity;
      Vec3d targetBend = velocity.multiply(-0.5);
      double bendMagnitude = targetBend.length();
      if (bendMagnitude > 0.1) {
         targetBend = targetBend.normalize().multiply(0.1);
      }

      return currentSmoothed.lerp(targetBend, 0.15);
   }

   private static void renderZigzagLine(MatrixStack poseStack, Immediate bufferSource, Vec3d start, Vec3d end, Vec3d cameraPos, float progress, int[] color) {
      poseStack.push();
      VertexConsumer buffer = bufferSource.getBuffer(RenderLayer.getDebugQuads());
      Matrix4f matrix = poseStack.peek().getPositionMatrix();
      Vec3d relativeStart = start.subtract(cameraPos);
      Vec3d relativeEnd = end.subtract(cameraPos);
      double distance = start.distanceTo(end);
      if (distance < 0.1) {
         poseStack.pop();
      } else {
         Vec3d direction = end.subtract(start).normalize();
         Vec3d up = new Vec3d(0.0, 1.0, 0.0);
         Vec3d perpendicular = direction.crossProduct(up).normalize();
         if (perpendicular.lengthSquared() < 0.01) {
            perpendicular = direction.crossProduct(new Vec3d(1.0, 0.0, 0.0)).normalize();
         }

         double effectiveFrequency;
         if (distance <= 65.0) {
            effectiveFrequency = 4.0 * (distance / 15.0);
         } else {
            double baseFrequency = 17.333333333333332;
            int halvings = (int)Math.ceil((distance - 65.0) / 65.0);
            effectiveFrequency = baseFrequency / Math.pow(2.0, halvings);
         }

         Vec3d[] points = new Vec3d[31];
         points[0] = relativeStart;

         for (int i = 1; i <= 30; i++) {
            float t = i / 30.0F;
            Vec3d linearPoint = relativeStart.add(relativeEnd.subtract(relativeStart).multiply(t));
            double wavePhase = t * Math.PI * effectiveFrequency * 2.0;
            double waveOffset = Math.sin(wavePhase) * 0.3;
            double zigzagIntensity = 1.0 - progress;
            waveOffset *= zigzagIntensity;
            Vec3d offset = perpendicular.multiply(waveOffset);
            points[i] = linearPoint.add(offset);
         }

         renderRibbon(buffer, matrix, points, cameraPos, 0.03F, color);
         poseStack.pop();
      }
   }

   private static void renderCurvedLine(MatrixStack poseStack, Immediate bufferSource, Vec3d start, Vec3d end, Vec3d cameraPos, Vec3d bendOffset, int[] color) {
      poseStack.push();
      VertexConsumer buffer = bufferSource.getBuffer(RenderLayer.getDebugQuads());
      Matrix4f matrix = poseStack.peek().getPositionMatrix();
      Vec3d relativeStart = start.subtract(cameraPos);
      Vec3d relativeEnd = end.subtract(cameraPos);
      Vec3d[] points = new Vec3d[31];
      points[0] = relativeStart;

      for (int i = 1; i <= 30; i++) {
         float t = i / 30.0F;
         Vec3d linearPoint = relativeStart.add(relativeEnd.subtract(relativeStart).multiply(t));
         points[i] = linearPoint;
      }

      renderRibbon(buffer, matrix, points, cameraPos, 0.03F, color);
      poseStack.pop();
   }

   private static void renderRibbon(VertexConsumer buffer, Matrix4f matrix, Vec3d[] points, Vec3d cameraPos, float thickness, int[] color) {
      if (points.length >= 2) {
         float halfThickness = thickness / 2.0F;

         for (int i = 0; i < points.length - 1; i++) {
            Vec3d p1 = points[i];
            Vec3d p2 = points[i + 1];
            Vec3d lineDir = p2.subtract(p1);
            if (!(lineDir.lengthSquared() < 1.0E-4)) {
               lineDir = lineDir.normalize();
               Vec3d midpoint = p1.add(p2).multiply(0.5);
               Vec3d toCamera = midpoint.multiply(-1.0);
               Vec3d perpendicular = lineDir.crossProduct(toCamera);
               if (perpendicular.lengthSquared() < 1.0E-4) {
                  perpendicular = lineDir.crossProduct(new Vec3d(0.0, 1.0, 0.0));
               }

               perpendicular = perpendicular.normalize().multiply(halfThickness);
               Vec3d v1 = p1.add(perpendicular);
               Vec3d v2 = p1.subtract(perpendicular);
               Vec3d v3 = p2.subtract(perpendicular);
               Vec3d v4 = p2.add(perpendicular);
               buffer.vertex(matrix, (float)v1.x, (float)v1.y, (float)v1.z).color(color[0], color[1], color[2], 255).next();
               buffer.vertex(matrix, (float)v2.x, (float)v2.y, (float)v2.z).color(color[0], color[1], color[2], 255).next();
               buffer.vertex(matrix, (float)v3.x, (float)v3.y, (float)v3.z).color(color[0], color[1], color[2], 255).next();
               buffer.vertex(matrix, (float)v4.x, (float)v4.y, (float)v4.z).color(color[0], color[1], color[2], 255).next();
            }
         }
      }
   }
}
