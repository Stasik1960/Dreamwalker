package daot;

import com.mojang.blaze3d.platform.GlStateManager.DstFactor;
import com.mojang.blaze3d.platform.GlStateManager.SrcFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;

@Environment(EnvType.CLIENT)
public class FlareParticle extends SpriteBillboardParticle {
   private static final ParticleTextureSheet FLARE_ON_TOP = new ParticleTextureSheet() {
      @Override
      public void begin(BufferBuilder buffer, TextureManager textureManager) {
         RenderSystem.depthMask(true);
         RenderSystem.disableDepthTest();
         RenderSystem.setShader(GameRenderer::getParticleProgram);
         RenderSystem.setShaderTexture(0, SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);
         RenderSystem.enableBlend();
         RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE_MINUS_SRC_ALPHA);
         buffer.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);
      }

      @Override public void draw(Tessellator tessellator) { tessellator.draw(); RenderSystem.enableDepthTest(); RenderSystem.disableBlend(); }
      @Override
      public String toString() {
         return "DAOT_FLARE_ON_TOP";
      }
   };
   private final SpriteProvider sprites;
   private static final int GROW_TICKS = 15;
   private static final int PULSE_TICKS = 530;
   private static final int FADE_TICKS = 55;
   private final double driftX;
   private final double driftY;
   private final double driftZ;
   private final float pulseOffset;
   private final float pulseSpeed;

   protected FlareParticle(ClientWorld level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteProvider sprites) {
      super(level, x, y, z, xSpeed, ySpeed, zSpeed);
      this.sprites = sprites;
      this.maxAge = 600;
      this.collidesWithWorld = false;
      this.gravityStrength = 0.0F;
      this.scale = 2.0F;
      this.driftX = (this.random.nextDouble() - 0.5) * 0.02;
      this.driftY = (this.random.nextDouble() - 0.5) * 0.01;
      this.driftZ = (this.random.nextDouble() - 0.5) * 0.02;
      this.pulseOffset = this.random.nextFloat() * (float) (Math.PI * 2);
      this.pulseSpeed = 0.12F + this.random.nextFloat() * 0.06F;
      this.velocityX = 0.0;
      this.velocityY = 0.0;
      this.velocityZ = 0.0;
      this.angle = this.random.nextFloat() * (float) (Math.PI * 2);
      this.prevAngle = this.angle;
      this.setSprite(sprites.getSprite(4, 4));
   }

   @Override
   public void tick() {
      super.tick();
      this.prevAngle = this.angle;
      this.angle += 0.02F;
      int frame;
      if (this.age < 15) {
         float progress = this.age / 14.0F;
         frame = 4 - Math.round(progress * 4.0F);
         this.alpha = 1.0F;
      } else if (this.age < 545) {
         int pulseAge = this.age - 15;
         float pulseProgress = pulseAge / 530.0F;
         float pulse = (float)Math.sin(this.age * this.pulseSpeed + this.pulseOffset);
         frame = 2 + Math.round(pulse * 2.0F);
         float baseAlpha = 1.0F - pulseProgress * 0.5F;
         this.alpha = baseAlpha + pulse * 0.15F;
         this.alpha = Math.max(0.2F, Math.min(1.0F, this.alpha));
         float sizePulse = 1.0F + pulse * 0.25F;
         this.scale = 2.0F * sizePulse * (1.0F - pulseProgress * 0.3F);
         this.velocityX = this.driftX;
         this.velocityY = this.driftY;
         this.velocityZ = this.driftZ;
      } else {
         frame = 4;
         int fadeAge = this.age - 15 - 530;
         float fadeProgress = fadeAge / 55.0F;
         this.alpha = 0.5F * (1.0F - fadeProgress);
         this.scale = 2.0F * (1.0F - fadeProgress * 0.5F);
         this.velocityX = this.driftX;
         this.velocityY = this.driftY;
         this.velocityZ = this.driftZ;
      }

      frame = Math.max(0, Math.min(4, frame));
      this.setSprite(this.sprites.getSprite(frame, 4));
   }

   @Override
   public ParticleTextureSheet getType() {
      MinecraftClient mc = MinecraftClient.getInstance();
      Camera cam = mc.gameRenderer.getCamera();
      double threshold = Math.max(48.0, mc.options.getClampedViewDistance() * 16.0 - 16.0);
      return cam.getPos().squaredDistanceTo(this.x, this.y, this.z) > threshold * threshold ? FLARE_ON_TOP : ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
   }

   @Environment(EnvType.CLIENT)
   public static class Provider implements ParticleFactory<DefaultParticleType> {
      private final SpriteProvider sprites;

      public Provider(SpriteProvider sprites) {
         this.sprites = sprites;
      }

      public Particle createParticle(DefaultParticleType type, ClientWorld level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
         return new FlareParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
      }
   }
}
