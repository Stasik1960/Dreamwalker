package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

@Environment(EnvType.CLIENT)
public class ODMBootsArmorRenderer extends GeoArmorRenderer<ODMBootsItem> {
   public ODMBootsArmorRenderer() {
      super(new ODMBootsArmorModel());
   }

   public void updateModelState(LivingEntity entity, ItemStack stack) {
      boolean useTitan = "TITAN".equals(daot.compat.components.Components.get(stack, DannysAot.ODM_BOOTS_SKIN)) && DannysAot.TITAN_AUTHORIZED_UUIDS.contains(entity.getUuid());
      ((ODMBootsArmorModel)this.getGeoModel()).currentUseTitan = useTitan;
   }

   protected void applyBoneVisibilityBySlot(EquipmentSlot currentSlot) {
      this.setVisible(true);
   }
}
