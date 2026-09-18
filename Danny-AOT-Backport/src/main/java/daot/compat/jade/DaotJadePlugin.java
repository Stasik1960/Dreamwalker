package daot.compat.jade;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
@Environment(EnvType.CLIENT)
public class DaotJadePlugin implements IWailaPlugin {
   public void registerClient(IWailaClientRegistration registration) {
      registration.registerEntityComponent(new HoodedNameProvider(), PlayerEntity.class);
   }
}
