package daot.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(Particle.class)
public interface ParticleAccessor {
   @Accessor("x")
   double getX();

   @Accessor("y")
   double getY();

   @Accessor("z")
   double getZ();

   @Accessor("velocityX")
   double getXd();

   @Accessor("velocityY")
   double getYd();

   @Accessor("velocityZ")
   double getZd();

   @Accessor("velocityX")
   void setXd(double var1);

   @Accessor("velocityY")
   void setYd(double var1);

   @Accessor("velocityZ")
   void setZd(double var1);

   @Accessor("gravityStrength")
   float getGravity();

   @Accessor("prevPosX")
   double getXo();

   @Accessor("prevPosY")
   double getYo();

   @Accessor("prevPosZ")
   double getZo();

   @Accessor("x")
   void setX(double var1);

   @Accessor("y")
   void setY(double var1);

   @Accessor("z")
   void setZ(double var1);
}
