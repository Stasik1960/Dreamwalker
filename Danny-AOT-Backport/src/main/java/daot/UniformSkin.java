package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class UniformSkin {
   public static final Identifier TITAN_MODEL = new Identifier("dannys-aot", "geo/titan_uniform.geo.json");
   public static final Identifier TITAN_TEXTURE = new Identifier("dannys-aot", "textures/armor/titan_uniform.png");

   private UniformSkin() {
   }

   public static boolean useTitan(LivingEntity entity, ItemStack stack) {
      return "TITAN".equals(daot.compat.components.Components.get(stack, DannysAot.UNIFORM_SKIN)) && DannysAot.TITAN_AUTHORIZED_UUIDS.contains(entity.getUuid());
   }
}
