package daot;

import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Environment(EnvType.CLIENT)
public class WarhammerSpikeRenderer extends GeoEntityRenderer<WarhammerSpikeEntity> {
   private static final Identifier PROCEDURAL_TEXTURE = new Identifier("dannys-aot", "textures/entity/spike.png");
   private static final int SIDES = 4;
   private static final int HEIGHT_SEGMENTS = 8;
   private static final float Y_OFFSET = -1.0F;
   private static final int BASE_R = 185;
   private static final int BASE_G = 185;
   private static final int BASE_B = 200;
   private static final int TIP_R = 248;
   private static final int TIP_G = 248;
   private static final int TIP_B = 255;
   private static final Set<Integer> playedEmergeEffects = new HashSet<>();
   private static final boolean USE_GEO_FOR_PIERCING_THORNS = true;
   private static final float GEO_MODEL_HEIGHT = 10.511875F;
   private static final float GEO_MODEL_WIDTH = 1.56375F;
   private static final float BIG_GEO_MODEL_HEIGHT = 31.0625F;
   private static final float BIG_GEO_MODEL_WIDTH = 6.09375F;
   private static final float SMALL_GEO_MODEL_HEIGHT = 1.5F;
   private static final float SMALL_GEO_MODEL_WIDTH = 1.0F;

   public WarhammerSpikeRenderer(Context context) {
      super(context, new WarhammerSpikeGeoModel());
      this.shadowRadius = 0.0F;
   }

   public void render(
      WarhammerSpikeEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight
   ) {
      int spikeAge = entity.getSpikeAge();
      if (spikeAge >= 0) {
         if (!entity.isSpikeField() && !entity.isImpaleSpike()) {
            this.renderGeoSpike(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
         } else {
            this.renderGeoSpike(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
         }
      }
   }

   private void renderGeoSpike(
      WarhammerSpikeEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight
   ) {
      int spikeAge = entity.getSpikeAge();
      if (entity.isImpaleSpike() && spikeAge <= 1 && playedEmergeEffects.add(entity.getId())) {
         ShiftParticleHelper.spawnSpikeEmergeParticle(entity.getWorld(), entity.getX(), entity.getY(), entity.getZ(), 1.0F);
      }

      float height = entity.getSpikeHeight();
      float width = entity.getSpikeWidth();
      float animScale = this.computeHeightScale(entity, spikeAge, partialTick);
      if (!(height * animScale < 0.01F)) {
         poseStack.push();
         poseStack.translate(0.0, -1.0, 0.0);
         poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(entity.getTiltX()));
         poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(entity.getTiltZ()));
         if (entity.isImpaleSpike()) {
            poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(entity.getRotation()));
         }

         float geoHeight;
         float geoWidth;
         if (entity.isImpaleSpike()) {
            geoHeight = 31.0625F;
            geoWidth = 6.09375F;
         } else if (entity.isSpikeField()) {
            geoHeight = 1.5F;
            geoWidth = 1.0F;
         } else {
            geoHeight = 10.511875F;
            geoWidth = 1.56375F;
         }

         float scaleY = height / geoHeight * animScale;
         float scaleXZ = width / geoWidth;
         poseStack.scale(scaleXZ, scaleY, scaleXZ);
         super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
         poseStack.pop();
      }
   }

   private float computeHeightScale(WarhammerSpikeEntity entity, int spikeAge, float partialTick) {
      boolean isImpale = entity.isImpaleSpike();
      int customLife = entity.getCustomLifetime();
      int maxAge = customLife > 0 ? customLife : (isImpale ? 270 : 320);
      int shrinkStart = customLife > 0 ? (int)(maxAge * 0.875) : (isImpale ? 230 : 280);
      int growTicks = isImpale ? 35 : 12;
      float age = spikeAge + partialTick;
      float heightScale;
      if (age < growTicks) {
         float t = age / growTicks;
         heightScale = (float)(1.0 - Math.exp(-4.0 * t) * Math.cos(t * Math.PI * 1.5));
      } else if (age > shrinkStart) {
         float t = (age - shrinkStart) / (maxAge - shrinkStart);
         heightScale = Math.max(0.0F, 1.0F - t);
      } else {
         heightScale = 1.0F;
      }

      return Math.max(0.0F, Math.min(1.3F, heightScale));
   }

   private void renderProceduralSpike(
      WarhammerSpikeEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight
   ) {
      int spikeAge = entity.getSpikeAge();
      if (entity.isImpaleSpike() && spikeAge <= 1 && playedEmergeEffects.add(entity.getId())) {
         ShiftParticleHelper.spawnSpikeEmergeParticle(entity.getWorld(), entity.getX(), entity.getY(), entity.getZ(), 1.0F);
      }

      float height = entity.getSpikeHeight();
      float baseHalfW = entity.getSpikeWidth() / 2.0F;
      float topHalfW = baseHalfW * 0.06F;
      float tiltX = entity.getTiltX();
      float tiltZ = entity.getTiltZ();
      float heightScale = this.computeHeightScale(entity, spikeAge, partialTick);
      float renderHeight = height * heightScale;
      if (!(renderHeight < 0.01F)) {
         poseStack.push();
         poseStack.translate(0.0F, -1.0F, 0.0F);
         poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tiltX));
         poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(tiltZ));
         if (entity.isImpaleSpike()) {
            poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(entity.getRotation()));
         }

         VertexConsumer buffer = bufferSource.getBuffer(RenderLayer.getEntitySolid(PROCEDURAL_TEXTURE));
         Matrix4f matrix = poseStack.peek().getPositionMatrix();
         Entry pose = poseStack.peek();
         boolean isSpikeField = entity.isSpikeField();
         boolean isImpale = entity.isImpaleSpike();
         int sides;
         int heightSegs;
         if (isSpikeField) {
            sides = 12;
            heightSegs = 2;
         } else if (isImpale) {
            sides = 8;
            heightSegs = 16;
         } else {
            sides = 4;
            heightSegs = 8;
         }

         float[] cosA = new float[sides];
         float[] sinA = new float[sides];

         for (int i = 0; i < sides; i++) {
            float angle = (float)((Math.PI * 2) * i / sides);
            cosA[i] = (float)Math.cos(angle);
            sinA[i] = (float)Math.sin(angle);
         }

         float taperSlope = (baseHalfW - topHalfW) / renderHeight;
         float normalLen = (float)Math.sqrt(1.0 + taperSlope * taperSlope);
         float normalHorizontal = 1.0F / normalLen;
         float normalY = taperSlope / normalLen;

         for (int ring = 0; ring < heightSegs; ring++) {
            float t0 = (float)ring / heightSegs;
            float t1 = (float)(ring + 1) / heightSegs;
            float y0 = renderHeight * t0;
            float y1 = renderHeight * t1;
            float r0 = lerp(baseHalfW, topHalfW, t0);
            float r1 = lerp(baseHalfW, topHalfW, t1);
            int cr0 = lerpColor(185, 248, t0);
            int cg0 = lerpColor(185, 248, t0);
            int cb0 = lerpColor(200, 255, t0);
            int cr1 = lerpColor(185, 248, t1);
            int cg1 = lerpColor(185, 248, t1);
            int cb1 = lerpColor(200, 255, t1);
            float vBottom = t0;
            float vTop = t1;

            for (int i = 0; i < sides; i++) {
               int next = (i + 1) % sides;
               float midAngle = (float)((Math.PI * 2) * (i + 0.5) / sides);
               float nx = (float)Math.cos(midAngle) * normalHorizontal;
               float nz = (float)Math.sin(midAngle) * normalHorizontal;
               float uLeft = (float)i / sides;
               float uRight = (float)(i + 1) / sides;
               this.litVertex(buffer, matrix, pose, cosA[i] * r0, y0, sinA[i] * r0, uLeft, vBottom, nx, normalY, nz, packedLight, cr0, cg0, cb0);
               this.litVertex(buffer, matrix, pose, cosA[i] * r1, y1, sinA[i] * r1, uLeft, vTop, nx, normalY, nz, packedLight, cr1, cg1, cb1);
               this.litVertex(buffer, matrix, pose, cosA[next] * r1, y1, sinA[next] * r1, uRight, vTop, nx, normalY, nz, packedLight, cr1, cg1, cb1);
               this.litVertex(buffer, matrix, pose, cosA[next] * r0, y0, sinA[next] * r0, uRight, vBottom, nx, normalY, nz, packedLight, cr0, cg0, cb0);
            }
         }

         for (int i = 0; i < sides; i++) {
            int next = (i + 1) % sides;
            this.litVertex(buffer, matrix, pose, 0.0F, renderHeight, 0.0F, 0.5F, 0.5F, 0.0F, 1.0F, 0.0F, packedLight, 248, 248, 255);
            this.litVertex(
               buffer, matrix, pose, cosA[next] * topHalfW, renderHeight, sinA[next] * topHalfW, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F, packedLight, 248, 248, 255
            );
            this.litVertex(buffer, matrix, pose, cosA[i] * topHalfW, renderHeight, sinA[i] * topHalfW, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, packedLight, 248, 248, 255);
            this.litVertex(buffer, matrix, pose, cosA[i] * topHalfW, renderHeight, sinA[i] * topHalfW, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, packedLight, 248, 248, 255);
         }

         poseStack.pop();
      }
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   private static int lerpColor(int a, int b, float t) {
      return (int)(a + (b - a) * t);
   }

   private void litVertex(
      VertexConsumer buffer,
      Matrix4f matrix,
      Entry pose,
      float x,
      float y,
      float z,
      float u,
      float v,
      float nx,
      float ny,
      float nz,
      int packedLight,
      int r,
      int g,
      int b
   ) {
      buffer.vertex(matrix, x, y, z).color(r, g, b, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(pose.getNormalMatrix(), nx, ny, nz).next();
   }
}
