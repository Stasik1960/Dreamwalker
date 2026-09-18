package daot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class OgreHealAbility {
   public static final int COOLDOWN_TICKS = 20;
   private static final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

   private OgreHealAbility() {
   }

   public static boolean tryHeal(ServerPlayerEntity player, LivingEntity titan) {
      if (player == null) {
         return false;
      } else {
         long now = player.getServerWorld().getTime();
         Long last = lastUse.get(player.getUuid());
         if (last != null && now - last < 20L) {
            return false;
         } else {
            lastUse.put(player.getUuid(), now);
            player.setHealth(player.getMaxHealth());
            if (titan != null && titan.isAlive()) {
               titan.setHealth(titan.getMaxHealth());
            }

            player.sendMessage(Text.literal(titan != null ? "Healed — titan restored to full" : "Healed to full").formatted(Formatting.LIGHT_PURPLE), true);
            LivingEntity effectSource = (LivingEntity)(titan != null && titan.isAlive() ? titan : player);
            player.getWorld()
               .playSound(
                  null, effectSource.getX(), effectSource.getY(), effectSource.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5F, 1.4F
               );
            if (player.getWorld() instanceof ServerWorld level) {
               level.spawnParticles(
                  ParticleTypes.HEART,
                  effectSource.getX(),
                  effectSource.getY() + effectSource.getHeight() * 0.7,
                  effectSource.getZ(),
                  12,
                  effectSource.getWidth() * 0.5,
                  0.4,
                  effectSource.getWidth() * 0.5,
                  0.05
               );
            }

            return true;
         }
      }
   }

   public static void onPlayerDisconnect(UUID uuid) {
      lastUse.remove(uuid);
   }
}
