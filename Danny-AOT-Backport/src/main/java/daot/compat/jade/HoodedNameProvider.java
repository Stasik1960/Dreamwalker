package daot.compat.jade;

import daot.HoodTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;

import snownee.jade.api.config.IPluginConfig;

@Environment(EnvType.CLIENT)
public class HoodedNameProvider implements IEntityComponentProvider {
   private static final Identifier UID = new Identifier("dannys-aot", "hooded_name");

   public Identifier getUid() {
      return UID;
   }

   public int getDefaultPriority() {
      return -10000;
   }

   public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
      if (accessor.getEntity() instanceof PlayerEntity player && HoodTracker.isHoodUpClient(player.getUuid())) {
         tooltip.remove(snownee.jade.api.Identifiers.CORE_OBJECT_NAME);
         tooltip.add(0, Text.literal("???").formatted(Formatting.WHITE), snownee.jade.api.Identifiers.CORE_OBJECT_NAME);
      }
   }
}
