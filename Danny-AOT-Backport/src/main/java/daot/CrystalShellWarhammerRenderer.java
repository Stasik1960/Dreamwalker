package daot;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import daot.compat.ClientSkins.SkinTextures;
import daot.compat.ClientSkins.Model;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Environment(EnvType.CLIENT)
public class CrystalShellWarhammerRenderer extends GeoEntityRenderer<CrystalShellWarhammerEntity> {
   private PlayerEntityModel<?> widePlayerModel;
   private PlayerEntityModel<?> slimPlayerModel;
   private UUID cachedSkinUUID = null;
   private Identifier cachedSkinTexture = null;
   private boolean cachedSlim = false;
   private static final int CABLE_SEGMENTS = 30;
   private static final float CABLE_THICKNESS = 0.075F;
   private static final int CABLE_R = 255;
   private static final int CABLE_G = 255;
   private static final int CABLE_B = 255;
   private static final double SAG_PER_BLOCK = 0.03;
   private static final double MIN_SAG = 0.3;
   private static final double MAX_SAG = 6.0;
   private static final double DRAG_FACTOR = 3.0;
   private static final double DRAG_SMOOTHING = 0.12;
   private static final Identifier CABLE_TEXTURE = new Identifier("dannys-aot", "textures/entity/spike.png");
   private Vec3d smoothedDrag = Vec3d.ZERO;

   public CrystalShellWarhammerRenderer(Context context) {
      super(context, new CrystalShellWarhammerModel());
      this.shadowRadius = 1.5F;
      this.widePlayerModel = new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false);
      this.slimPlayerModel = new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER_SLIM), true);
   }

   public RenderLayer getRenderType(CrystalShellWarhammerEntity entity, Identifier texture, VertexConsumerProvider bufferSource, float partialTick) {
      return RenderLayer.getEntityTranslucent(texture);
   }

   public void render(
      CrystalShellWarhammerEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight
   ) {
      this.renderPlayerClone(entity, poseStack, bufferSource, packedLight);
      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      this.renderCable(entity, partialTick, poseStack, bufferSource);
   }

   private void renderCable(CrystalShellWarhammerEntity entity, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource) {
      int parentId = entity.getParentTitanId();
      if (parentId != -1) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world != null) {
            if (mc.world.getEntityById(parentId) instanceof WarhammerTitanEntity titan) {
               Vec3d footWorldPos = TitanBoneCache.getBonePosition(titan.getId(), "rightfeet");
               if (footWorldPos == null) {
                  footWorldPos = titan.getLerpedPos(partialTick).add(0.5, 0.0, 0.0);
               }

               Vec3d crystalWorldPos = entity.getLerpedPos(partialTick);
               Vec3d localStart = new Vec3d(0.0, 0.5, 0.0);
               Vec3d localEnd = footWorldPos.subtract(crystalWorldPos);
               double distance = localStart.distanceTo(localEnd);
               if (!(distance < 0.1)) {
                  double sag = Math.max(0.3, Math.min(6.0, distance * 0.03));
                  Vec3d titanVelocity = titan.getVelocity();
                  Vec3d targetDrag = new Vec3d(-titanVelocity.x, 0.0, -titanVelocity.z).multiply(3.0);
                  if (targetDrag.lengthSquared() > 4.0) {
                     targetDrag = targetDrag.normalize().multiply(2.0);
                  }

                  this.smoothedDrag = this.smoothedDrag.add(targetDrag.subtract(this.smoothedDrag).multiply(0.12));
                  Vec3d[] localPoints = new Vec3d[31];

                  for (int i = 0; i <= 30; i++) {
                     float t = i / 30.0F;
                     Vec3d linearPoint = localStart.add(localEnd.subtract(localStart).multiply(t));
                     double sagOffset = -sag * Math.sin(t * Math.PI);
                     double dragStrength = Math.sin(t * Math.PI);
                     Vec3d dragOffset = this.smoothedDrag.multiply(dragStrength);
                     localPoints[i] = linearPoint.add(dragOffset.x, sagOffset, dragOffset.z);
                  }

                  for (int i = 1; i < localPoints.length - 1; i++) {
                     Vec3d worldPoint = localPoints[i].add(crystalWorldPos);
                     BlockPos blockPos = BlockPos.ofFloored(worldPoint.x, worldPoint.y, worldPoint.z);

                     for (int attempts = 0; attempts < 10 && mc.world.getBlockState(blockPos).isOpaqueFullCube(mc.world, blockPos); attempts++) {
                        double newWorldY = blockPos.getY() + 1.05;
                        localPoints[i] = new Vec3d(localPoints[i].x, newWorldY - crystalWorldPos.y, localPoints[i].z);
                        worldPoint = localPoints[i].add(crystalWorldPos);
                        blockPos = BlockPos.ofFloored(worldPoint.x, worldPoint.y, worldPoint.z);
                     }
                  }

                  Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
                  Vec3d localCameraPos = cameraPos.subtract(crystalWorldPos);
                  poseStack.push();
                  VertexConsumer buffer = bufferSource.getBuffer(RenderLayer.getEntityTranslucentEmissive(CABLE_TEXTURE));
                  Matrix4f matrix = poseStack.peek().getPositionMatrix();
                  Entry pose = poseStack.peek();
                  float halfThickness = 0.0375F;

                  for (int i = 0; i < localPoints.length - 1; i++) {
                     Vec3d p1 = localPoints[i];
                     Vec3d p2 = localPoints[i + 1];
                     Vec3d lineDir = p2.subtract(p1);
                     if (!(lineDir.lengthSquared() < 1.0E-4)) {
                        lineDir = lineDir.normalize();
                        Vec3d midpoint = p1.add(p2).multiply(0.5);
                        Vec3d toCamera = localCameraPos.subtract(midpoint);
                        Vec3d perpendicular = lineDir.crossProduct(toCamera);
                        if (perpendicular.lengthSquared() < 1.0E-4) {
                           perpendicular = lineDir.crossProduct(new Vec3d(0.0, 1.0, 0.0));
                        }

                        perpendicular = perpendicular.normalize().multiply(halfThickness);
                        Vec3d v1 = p1.add(perpendicular);
                        Vec3d v2 = p1.subtract(perpendicular);
                        Vec3d v3 = p2.subtract(perpendicular);
                        Vec3d v4 = p2.add(perpendicular);
                        Vec3d normal = toCamera.lengthSquared() > 1.0E-4 ? toCamera.normalize() : new Vec3d(0.0, 1.0, 0.0);
                        float nx = (float)normal.x;
                        float ny = (float)normal.y;
                        float nz = (float)normal.z;
                        int light = 15728880;
                        float uv0 = (float)i / (localPoints.length - 1);
                        float uv1 = (float)(i + 1) / (localPoints.length - 1);
                        buffer.vertex(matrix, (float)v1.x, (float)v1.y, (float)v1.z)
                           .color(255, 255, 255, 255)
                           .texture(0.0F, uv0)
                           .overlay(OverlayTexture.DEFAULT_UV)
                           .light(light)
                           .normal(pose.getNormalMatrix(), nx, ny, nz).next();
                        buffer.vertex(matrix, (float)v2.x, (float)v2.y, (float)v2.z)
                           .color(255, 255, 255, 255)
                           .texture(1.0F, uv0)
                           .overlay(OverlayTexture.DEFAULT_UV)
                           .light(light)
                           .normal(pose.getNormalMatrix(), nx, ny, nz).next();
                        buffer.vertex(matrix, (float)v3.x, (float)v3.y, (float)v3.z)
                           .color(255, 255, 255, 255)
                           .texture(1.0F, uv1)
                           .overlay(OverlayTexture.DEFAULT_UV)
                           .light(light)
                           .normal(pose.getNormalMatrix(), nx, ny, nz).next();
                        buffer.vertex(matrix, (float)v4.x, (float)v4.y, (float)v4.z)
                           .color(255, 255, 255, 255)
                           .texture(0.0F, uv1)
                           .overlay(OverlayTexture.DEFAULT_UV)
                           .light(light)
                           .normal(pose.getNormalMatrix(), nx, ny, nz).next();
                     }
                  }

                  poseStack.pop();
               }
            }
         }
      }
   }

   private static int getLightAt(MinecraftClient mc, Vec3d pos) {
      if (mc.world == null) {
         return 15728880;
      } else {
         BlockPos blockPos = BlockPos.ofFloored(pos.x, pos.y, pos.z);
         int blockLight = mc.world.getLightLevel(LightType.BLOCK, blockPos);
         int skyLight = mc.world.getLightLevel(LightType.SKY, blockPos);
         return LightmapTextureManager.pack(blockLight, skyLight);
      }
   }

   private void renderPlayerClone(CrystalShellWarhammerEntity entity, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
      UUID shifterUUID = entity.getShifterUUID();
      if (shifterUUID != null) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player == null || !mc.player.getUuid().equals(shifterUUID) || mc.options.getPerspective() != Perspective.FIRST_PERSON) {
            Identifier skinTexture;
            boolean slim;
            SkinTextures skin = this.resolveSkin(shifterUUID);
               skinTexture = skin.texture();
               slim = skin.model() == Model.SLIM;
               this.cachedSkinUUID = shifterUUID;
               this.cachedSkinTexture = skinTexture;
               this.cachedSlim = slim;

            PlayerEntityModel<?> playerModel = slim ? this.slimPlayerModel : this.widePlayerModel;
            playerModel.child = false;
            playerModel.sneaking = false;
            playerModel.leaningPitch = 0.0F;
            resetModelPart(playerModel.head);
            resetModelPart(playerModel.hat);
            resetModelPart(playerModel.body);
            resetModelPart(playerModel.rightArm);
            resetModelPart(playerModel.leftArm);
            resetModelPart(playerModel.rightLeg);
            resetModelPart(playerModel.leftLeg);
            playerModel.hat.visible = true;
            playerModel.jacket.visible = true;
            playerModel.leftSleeve.visible = true;
            playerModel.rightSleeve.visible = true;
            playerModel.leftPants.visible = true;
            playerModel.rightPants.visible = true;
            resetModelPart(playerModel.jacket);
            resetModelPart(playerModel.leftSleeve);
            resetModelPart(playerModel.rightSleeve);
            resetModelPart(playerModel.leftPants);
            resetModelPart(playerModel.rightPants);
            poseStack.push();
            poseStack.translate(0.0, 0.01, 0.0);
            poseStack.scale(0.9375F, 0.9375F, 0.9375F);
            poseStack.translate(0.0, 1.501, 0.0);
            poseStack.scale(1.0F, -1.0F, -1.0F);
            VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderLayer.getEntityTranslucent(skinTexture));
            playerModel.render(poseStack, vertexConsumer, packedLight, OverlayTexture.DEFAULT_UV, 1F, 1F, 1F, 1F);
            poseStack.pop();
         }
      }
   }

   private static void resetModelPart(ModelPart part) {
      part.pitch = 0.0F;
      part.yaw = 0.0F;
      part.roll = 0.0F;
      part.visible = true;
   }

   private SkinTextures resolveSkin(UUID uuid) {
      return daot.compat.ClientSkins.resolve(uuid);
   }
}
