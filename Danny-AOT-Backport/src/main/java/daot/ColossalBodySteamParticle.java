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
public class ColossalBodySteamParticle extends SpriteBillboardParticle {
   private static final int FADE_IN_TICKS = 5;
   private final SpriteProvider sprites;
   private final float maxAlpha;
   private final float baseSize;

   protected ColossalBodySteamParticle(ClientWorld level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteProvider sprites) {
      super(level, x, y, z, xSpeed, ySpeed, zSpeed);
      this.sprites = sprites;
      this.collidesWithWorld = false;
      this.baseSize = 2.4F + this.random.nextFloat() * 1.6F;
      this.maxAge = 60 + this.random.nextInt(50);
      this.maxAlpha = 0.32F;
      this.velocityMultiplier = 0.98F;
      this.red = 1.0F;
      this.green = 1.0F;
      this.blue = 1.0F;
      this.velocityX = xSpeed;
      this.velocityY = ySpeed;
      this.velocityZ = zSpeed;
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
         float lifeT = (float)this.age / this.maxAge;
         this.velocityY += 0.012;
         this.move(this.velocityX, this.velocityY, this.velocityZ);
         this.velocityX = this.velocityX * this.velocityMultiplier;
         this.velocityY = this.velocityY * this.velocityMultiplier;
         this.velocityZ = this.velocityZ * this.velocityMultiplier;
         this.angle += 0.004F;
         this.scale = this.baseSize * (1.0F + lifeT * 0.5F);
         if (this.age < 5) {
            this.alpha = this.maxAlpha * (this.age / 5.0F);
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
   public static class Provider implements ParticleFactory<DefaultParticleType> {
      private final SpriteProvider sprites;

      public Provider(SpriteProvider sprites) {
         this.sprites = sprites;
      }

      public Particle createParticle(DefaultParticleType type, ClientWorld level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
         return new ColossalBodySteamParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
      }
   }
}
