package daot;

import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class SoldierboyChargeParticleHandler {
   private static final Vector3f YELLOW = new Vector3f(1.0F, 0.9F, 0.25F);
   private static final float DUST_SCALE = 1.4F;
   private static final double CHEST_BELOW_EYE = 0.55;

   private SoldierboyChargeParticleHandler() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(SoldierboyChargeParticleHandler::tick);
   }

   private static void tick(MinecraftClient mc) {
      ClientWorld level = mc.world;
      if (level != null) {
         ClientPlayerEntity self = mc.player;
         if (self != null && SoldierboyInputHandler.getLocalChargingAbility() >= 0) {
            spawnChargeParticles(level, self, SoldierboyInputHandler.getLocalChargeProgress());
         }

         Map<UUID, SoldierboyClientHandler.RemoteCharge> snap = SoldierboyClientHandler.snapshot();
         if (!snap.isEmpty()) {
            long now = level.getTime();

            for (Entry<UUID, SoldierboyClientHandler.RemoteCharge> entry : snap.entrySet()) {
               if ((self == null || !entry.getKey().equals(self.getUuid()))
                  && level.getPlayerByUuid(entry.getKey()) instanceof AbstractClientPlayerEntity remote) {
                  int duration = entry.getValue().ability() == 1 ? 40 : 50;
                  float progress = Math.min(1.0F, (float)(now - entry.getValue().startTick()) / duration);
                  spawnChargeParticles(level, remote, progress);
               }
            }
         }
      }
   }

   private static void spawnChargeParticles(ClientWorld level, AbstractClientPlayerEntity player, float progress) {
      if (!(progress <= 0.0F)) {
         int count = 1 + (int)(progress * 7.0F);
         Vec3d chest = player.getEyePos().subtract(0.0, 0.55, 0.0);
         DustParticleEffect dust = new DustParticleEffect(YELLOW, 1.4F);

         for (int i = 0; i < count; i++) {
            double ox = (level.getRandom().nextDouble() - 0.5) * 0.6;
            double oy = (level.getRandom().nextDouble() - 0.5) * 0.6;
            double oz = (level.getRandom().nextDouble() - 0.5) * 0.6;
            double vx = ox * 0.15;
            double vy = 0.05 + level.getRandom().nextDouble() * 0.1;
            double vz = oz * 0.15;
            level.addParticle(dust, chest.x + ox, chest.y + oy, chest.z + oz, vx, vy, vz);
         }

         if (progress >= 0.95F) {
            DustParticleEffect hotYellow = new DustParticleEffect(new Vector3f(1.0F, 1.0F, 0.55F), 1.8199999F);

            for (int i = 0; i < 6; i++) {
               double ox = (level.getRandom().nextDouble() - 0.5) * 1.2;
               double oy = (level.getRandom().nextDouble() - 0.5) * 1.2;
               double oz = (level.getRandom().nextDouble() - 0.5) * 1.2;
               level.addParticle(hotYellow, chest.x + ox, chest.y + oy, chest.z + oz, ox * 0.25, 0.1 + level.getRandom().nextDouble() * 0.15, oz * 0.25);
            }

            for (int i = 0; i < 4; i++) {
               double ox = (level.getRandom().nextDouble() - 0.5) * 0.5;
               double oy = (level.getRandom().nextDouble() - 0.5) * 0.5;
               double oz = (level.getRandom().nextDouble() - 0.5) * 0.5;
               level.addParticle(ParticleTypes.CRIT, chest.x + ox, chest.y + oy, chest.z + oz, 0.0, 0.0, 0.0);
            }
         }
      }
   }
}
