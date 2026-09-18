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
public class NapeSteamParticle extends SpriteBillboardParticle {
   private static final int FADE_IN_TICKS = 3;
   private static final int FADE_OUT_TICKS = 10;
   private static final float BASE_MAX_ALPHA = 0.85F;
   private final SpriteProvider sprites;
   private final float maxAlpha;

   protected NapeSteamParticle(
      ClientWorld level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteProvider sprites, float sizeMul, float alphaMul
   ) {
      super(level, x, y, z, xSpeed, ySpeed, zSpeed);
      this.sprites = sprites;
      this.maxAlpha = 0.85F * alphaMul;
      boolean isStream = xSpeed * xSpeed + zSpeed * zSpeed > 0.02;
      this.maxAge = isStream ? 23 + this.random.nextInt(12) : 35 + this.random.nextInt(18);
      this.gravityStrength = 0.0F;
      this.collidesWithWorld = false;
      this.velocityMultiplier = 0.96F;
      double upBase = isStream ? 0.045 : 0.03;
      double upJitter = isStream ? 0.03 : 0.02;
      this.velocityX = xSpeed + (this.random.nextDouble() - 0.5) * 0.01;
      this.velocityY = ySpeed + upBase + this.random.nextDouble() * upJitter;
      this.velocityZ = zSpeed + (this.random.nextDouble() - 0.5) * 0.01;
      this.scale = (0.275F + this.random.nextFloat() * 0.175F) * sizeMul;
      this.scale(1.0F);
      this.red = 1.0F;
      this.green = 1.0F;
      this.blue = 1.0F;
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
         this.velocityY += 8.0E-4;
         this.move(this.velocityX, this.velocityY, this.velocityZ);
         this.velocityX = this.velocityX * this.velocityMultiplier;
         this.velocityY = this.velocityY * this.velocityMultiplier;
         this.velocityZ = this.velocityZ * this.velocityMultiplier;
         this.angle += 0.005F;
         int ticksLeft = this.maxAge - this.age;
         if (this.age < 3) {
            float t = this.age / 3.0F;
            this.alpha = this.maxAlpha * t;
         } else if (ticksLeft < 10) {
            float t = ticksLeft / 10.0F;
            this.alpha = this.maxAlpha * t;
            this.scale *= 0.97F + 0.03F * t;
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
   public static class Provider implements ParticleFactory<DefaultParticleType> {
      private final SpriteProvider sprites;
      private final float sizeMul;
      private final float alphaMul;

      public Provider(SpriteProvider sprites) {
         this(sprites, 1.0F, 1.0F);
      }

      public Provider(SpriteProvider sprites, float sizeMul, float alphaMul) {
         this.sprites = sprites;
         this.sizeMul = sizeMul;
         this.alphaMul = alphaMul;
      }

      public Particle createParticle(DefaultParticleType type, ClientWorld level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
         return new NapeSteamParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites, this.sizeMul, this.alphaMul);
      }
   }
}
