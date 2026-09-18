package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;

@Environment(EnvType.CLIENT)
public class NukeCloudParticle extends SpriteBillboardParticle {
   private static final int FADE_IN_TICKS = 4;
   private final SpriteProvider sprites;
   private final NukeCloudParticle.Mode mode;
   private final float maxAlpha;
   private final float baseSize;

   protected NukeCloudParticle(
      ClientWorld level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteProvider sprites, NukeCloudParticle.Mode mode
   ) {
      super(level, x, y, z, xSpeed, ySpeed, zSpeed);
      this.sprites = sprites;
      this.mode = mode;
      this.collidesWithWorld = false;
      switch (mode) {
         case FIRE:
            this.baseSize = 7.0F + this.random.nextFloat() * 7.0F;
            this.maxAge = 78 + this.random.nextInt(54);
            this.gravityStrength = -0.03F;
            this.velocityMultiplier = 0.9F;
            this.maxAlpha = 1.0F;
            this.red = 1.0F;
            this.green = 0.6F;
            this.blue = 0.16F;
            break;
         case DUST:
            this.baseSize = 8.0F + this.random.nextFloat() * 8.0F;
            this.maxAge = 330 + this.random.nextInt(210);
            this.gravityStrength = 0.008F;
            this.velocityMultiplier = 0.93F;
            this.maxAlpha = 0.8F;
            this.red = 0.52F;
            this.green = 0.43F;
            this.blue = 0.31F;
            break;
         case STEAM:
            this.baseSize = 2.0F + this.random.nextFloat() * 1.2F;
            this.maxAge = 30 + this.random.nextInt(20);
            this.gravityStrength = -0.02F;
            this.velocityMultiplier = 0.96F;
            this.maxAlpha = 0.5F;
            this.red = 1.0F;
            this.green = 1.0F;
            this.blue = 1.0F;
            break;
         default:
            this.baseSize = 11.0F + this.random.nextFloat() * 11.0F;
            this.maxAge = 390 + this.random.nextInt(330);
            this.gravityStrength = -0.018F;
            this.velocityMultiplier = 0.94F;
            this.maxAlpha = 0.9F;
            this.red = 0.16F;
            this.green = 0.15F;
            this.blue = 0.14F;
      }

      if (mode == NukeCloudParticle.Mode.STEAM) {
         this.velocityX = xSpeed;
         this.velocityY = ySpeed + 0.04;
         this.velocityZ = zSpeed;
      } else {
         this.velocityX = xSpeed * 0.5;
         this.velocityY = ySpeed * 0.5 + (mode == NukeCloudParticle.Mode.DUST ? 0.0 : 0.02);
         this.velocityZ = zSpeed * 0.5;
      }

      this.scale = this.baseSize;
      this.alpha = 0.0F;
      this.angle = this.random.nextFloat() * (float) (Math.PI * 2);
      this.prevAngle = this.angle;
      this.setSpriteForAge(sprites);
   }

   @Override
   public void tick() {
      this.prevPosX = this.x;
      this.prevPosY = this.y;
      this.prevPosZ = this.z;
      this.prevAngle = this.angle;
      if (this.age++ >= this.maxAge) {
         this.markDead();
      } else {
         this.velocityY = this.velocityY - 0.04 * this.gravityStrength;
         this.move(this.velocityX, this.velocityY, this.velocityZ);
         this.velocityX = this.velocityX * this.velocityMultiplier;
         this.velocityY = this.velocityY * this.velocityMultiplier;
         this.velocityZ = this.velocityZ * this.velocityMultiplier;
         this.angle = this.angle + (this.mode == NukeCloudParticle.Mode.FIRE ? 0.02F : 0.006F);
         float lifeT = (float)this.age / this.maxAge;
         if (this.mode == NukeCloudParticle.Mode.FIRE) {
            this.green = 0.6F * (1.0F - lifeT * 0.85F);
            this.blue = 0.16F * (1.0F - lifeT);
            this.red = 1.0F - lifeT * 0.65F;
         }

         if (this.mode == NukeCloudParticle.Mode.FIRE) {
            this.scale = this.baseSize * (1.0F - lifeT * 0.4F);
         } else {
            this.scale = this.baseSize * (1.0F + lifeT * 0.6F);
         }

         if (this.age < 4) {
            this.alpha = this.maxAlpha * (this.age / 4.0F);
         } else if (lifeT > 0.66F) {
            this.alpha = this.maxAlpha * (1.0F - (lifeT - 0.66F) / 0.34F);
         } else {
            this.alpha = this.maxAlpha;
         }

         this.setSpriteForAge(this.sprites);
      }
   }

   @Override
   public ParticleTextureSheet getType() {
      return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
   }

   @Environment(EnvType.CLIENT)
   public static enum Mode {
      SMOKE,
      FIRE,
      DUST,
      STEAM;
   }

   @Environment(EnvType.CLIENT)
   public static class Provider implements ParticleFactory<DefaultParticleType> {
      private final SpriteProvider sprites;
      private final NukeCloudParticle.Mode mode;

      public Provider(SpriteProvider sprites, NukeCloudParticle.Mode mode) {
         this.sprites = sprites;
         this.mode = mode;
      }

      public Particle createParticle(DefaultParticleType type, ClientWorld level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
         return new NukeCloudParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites, this.mode);
      }
   }
}
