package daot;

import daot.network.HomelanderSonicBoomPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class SonicBoomHandler {
   private static final double STACK_MULTIPLIER = 1.5;
   private static final double MAX_MULTIPLIER = 5.0;
   private static final int COOLDOWN_TICKS = 30;
   private static final float SONIC_BOOM_FOV = 18.0F;
   private static final float SONIC_BOOM_SHAKE = 2.0F;
   private static final float CASTER_VOLUME = 1.0F;
   private static double speedMultiplier = 1.0;
   private static int cooldownTicks = 0;
   private static boolean wasQDown = false;

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(SonicBoomHandler::tick);
   }

   public static double getMultiplier() {
      return speedMultiplier;
   }

   public static void reset() {
      speedMultiplier = 1.0;
      cooldownTicks = 0;
   }

   private static void tick(MinecraftClient mc) {
      if (cooldownTicks > 0) {
         cooldownTicks--;
      }

      ClientPlayerEntity player = mc.player;
      if (player == null) {
         wasQDown = false;
      } else {
         boolean flying = HomelanderFlightHandler.isFlying(player.getUuid());
         boolean qDown = mc.currentScreen == null && mc.getWindow() != null && InputUtil.isKeyPressed(mc.getWindow().getHandle(), 81);
         boolean qPressed = qDown && !wasQDown;
         wasQDown = qDown;
         if (qPressed) {
            if (flying) {
               if (!HomelanderPlayerAnimationHandler.isInFlyStart(player.getUuid())) {
                  if (cooldownTicks <= 0) {
                     if (mc.options.sprintKey.isPressed()) {
                        if (!(player.input.movementForward <= 0.05F)) {
                           triggerBoom(mc, player);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void triggerBoom(MinecraftClient mc, ClientPlayerEntity player) {
      cooldownTicks = 30;
      speedMultiplier = Math.min(speedMultiplier * 1.5, 5.0);
      CameraShakeHandler.triggerShake(2.0F);
      BoostFovHandler.triggerSonicBoomFov();
      Vec3d vel = player.getVelocity();
      Vec3d forward = vel.lengthSquared() > 1.0E-4 ? vel.normalize() : player.getRotationVec(1.0F);
      double cx = player.getX();
      double cy = player.getY() + player.getHeight() * 0.5;
      double cz = player.getZ();
      if (mc.world != null) {
         mc.getSoundManager().play(PositionedSoundInstance.master(ModSounds.BOOM, 1.0F, 1.0F));
         spawnShockwaveDisc(mc.world, cx, cy, cz, forward);
      }

      ClientPlayNetworking.send(new HomelanderSonicBoomPayload(cx, cy, cz, forward.x, forward.y, forward.z));
   }

   private static void spawnShockwaveDisc(World level, double cx, double cy, double cz, Vec3d forward) {
      Vec3d worldUp = new Vec3d(0.0, 1.0, 0.0);
      Vec3d u = forward.crossProduct(worldUp);
      if (u.lengthSquared() < 1.0E-4) {
         u = forward.crossProduct(new Vec3d(1.0, 0.0, 0.0));
      }

      u = u.normalize();
      Vec3d v = forward.crossProduct(u).normalize();
      DustParticleEffect whiteDust = new DustParticleEffect(new Vector3f(1.0F, 1.0F, 1.0F), 1.4F);
      int count = 32;

      for (int i = 0; i < count; i++) {
         double angle = (Math.PI * 2) * i / count;
         double cs = Math.cos(angle);
         double sn = Math.sin(angle);
         double r1 = 2.2;
         level.addParticle(whiteDust, cx + (u.x * cs + v.x * sn) * r1, cy + (u.y * cs + v.y * sn) * r1, cz + (u.z * cs + v.z * sn) * r1, 0.0, 0.0, 0.0);
         double r2 = 1.4;
         level.addParticle(whiteDust, cx + (u.x * cs + v.x * sn) * r2, cy + (u.y * cs + v.y * sn) * r2, cz + (u.z * cs + v.z * sn) * r2, 0.0, 0.0, 0.0);
      }

      for (int i = 0; i < 6; i++) {
         double ox = (level.random.nextDouble() - 0.5) * 0.6;
         double oy = (level.random.nextDouble() - 0.5) * 0.6;
         double oz = (level.random.nextDouble() - 0.5) * 0.6;
         level.addParticle(whiteDust, cx + ox, cy + oy, cz + oz, 0.0, 0.0, 0.0);
      }
   }
}
