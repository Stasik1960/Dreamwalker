package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

@Environment(EnvType.CLIENT)
public class ODMGearRenderer extends GeoArmorRenderer<ODMGearItem> {
   public ODMGearRenderer() {
      super(new ODMGearModel());
   }

   public void updateModelState(LivingEntity entity, ItemStack stack) {
      boolean useHyper = "HYPER".equals(daot.compat.components.Components.get(stack, DannysAot.ODM_GEAR_SKIN)) && DannysAot.HYPER_AUTHORIZED_UUIDS.contains(entity.getUuid());
      ((ODMGearModel)this.getGeoModel()).currentUseHyper = useHyper;
   }

   protected void applyBoneVisibilityBySlot(EquipmentSlot currentSlot) {
      this.setVisible(true);
   }
}
