package daot.compat.journeymap;

import daot.HoodTracker;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.event.EntityRadarUpdateEvent;
import journeymap.api.v2.client.event.EntityRadarUpdateEvent.EntityType;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

@JourneyMapPlugin(apiVersion = "2.0.0")
@Environment(EnvType.CLIENT)
public class DaotJourneyMapPlugin implements IClientPlugin {
   public String getModId() {
      return "dannys-aot";
   }

   public void initialize(IClientAPI jmClientApi) {
      ClientEventRegistry.ENTITY_RADAR_UPDATE_EVENT.subscribe("dannys-aot", this::onEntityRadarUpdate);
   }

   private void onEntityRadarUpdate(EntityRadarUpdateEvent event) {
      if (event.getType() == EntityType.PLAYER) {
         Entity entity = (Entity)event.getWrappedEntity().getEntityRef().get();
         if (entity instanceof PlayerEntity player) {
            if (HoodTracker.isHoodUpClient(player.getUuid()) || isShiftedIntoTitan(player)) {
               event.cancel();
            }
         }
      }
   }

   private static boolean isShiftedIntoTitan(PlayerEntity player) {
      return player.isInvisible() && player.hasVehicle();
   }
}
